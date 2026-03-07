package com.prbotbenchmark.replay;

import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.bot.domain.Bot;
import com.prbotbenchmark.github.GitHubApiClient;
import com.prbotbenchmark.github.GitHubAppAuthService;
import com.prbotbenchmark.github.GitHubAppProperties;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitWorkspaceService {

    private final GitHubApiClient gitHubApiClient;
    private final GitHubAppAuthService authService;
    private final GitHubAppProperties gitHubAppProperties;

    @Value("${app.workspace.base-path}")
    private String workspaceBasePath;

    private Path getWorkspacePath(ExemplarRepo repo) {
        return Path.of(workspaceBasePath, repo.getOwner() + "-" + repo.getRepoName());
    }

    public void ensureMirrorRepoExists(ExemplarRepo exemplarRepo, String mirrorOwner, String mirrorRepoName) throws IOException, InterruptedException {
        try {
            gitHubApiClient.getRepository(mirrorOwner, mirrorRepoName);
            log.debug("Mirror repo {}/{} already exists", mirrorOwner, mirrorRepoName);
        } catch (Exception e) {
            // Check if it's a 404 (repo doesn't exist)
            String message = e.getMessage();
            if (message != null && message.contains("404")) {
                log.info("Mirror repo {}/{} not found, creating it", mirrorOwner, mirrorRepoName);
                Map<String, Object> body = Map.of(
                        "name", mirrorRepoName,
                        "private", true,
                        "has_issues", false,
                        "has_projects", false,
                        "has_wiki", false,
                        "auto_init", false
                );
                gitHubApiClient.createRepository(mirrorOwner, body);
                log.info("Created mirror repo {}/{}", mirrorOwner, mirrorRepoName);
            } else {
                throw new RuntimeException("Failed to check/create mirror repo " + mirrorOwner + "/" + mirrorRepoName, e);
            }
        }
    }

    public void initializeWorkspace(ExemplarRepo repo) {
        Path workspacePath = getWorkspacePath(repo);

        if (Files.exists(workspacePath.resolve(".git"))) {
            log.debug("Workspace already initialized at {}", workspacePath);
            return;
        }

        log.info("Initializing workspace for {}/{} at {}", repo.getOwner(), repo.getRepoName(), workspacePath);

        try {
            Files.createDirectories(workspacePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create workspace parent directory: " + workspacePath.getParent(), e);
        }

        String authUrl = buildAuthUrl(repo.getOwner(), repo.getRepoName());
        runGit(workspacePath.getParent(),
                "clone", "--no-single-branch", authUrl, workspacePath.getFileName().toString());

        log.info("Workspace initialized at {}", workspacePath);
    }

    public void syncWorkspace(ExemplarRepo repo, String mirrorOwner, String mirrorRepoName) {
        initializeWorkspace(repo);

        Path workspacePath = getWorkspacePath(repo);
        log.info("Syncing workspace for {}/{} to mirror {}/{}", repo.getOwner(), repo.getRepoName(), mirrorOwner, mirrorRepoName);

        runGit(workspacePath, "fetch", "--all", "--prune");

        String mirrorAuthUrl = buildAuthUrl(mirrorOwner, mirrorRepoName);
        runGit(workspacePath, "push", "--mirror", mirrorAuthUrl);

        log.info("Sync complete for {}/{} -> {}/{}", repo.getOwner(), repo.getRepoName(), mirrorOwner, mirrorRepoName);
    }

    public void createReplayBranches(
            ExemplarRepo exemplarRepo,
            String mirrorOwner,
            String mirrorRepoName,
            SuitePr suitePr,
            Bot bot,
            String baseBranchName,
            String headBranchName) throws IOException, InterruptedException {

        log.info("Creating replay branches for suitePr={} in mirror {}/{}",
                suitePr.getOriginalPrNumber(), mirrorOwner, mirrorRepoName);

        // Step 1: Ensure workspace is initialized
        initializeWorkspace(exemplarRepo);

        Path workspacePath = getWorkspacePath(exemplarRepo);

        // Step 2: Fetch latest from exemplar
        log.debug("Fetching latest from exemplar {}/{}", exemplarRepo.getOwner(), exemplarRepo.getRepoName());
        runGit(workspacePath, "fetch", "--all", "--prune");

        // Step 3: Create base branch at suitePr.baseCommitSha via GitHub Tree API
        // a. Get the commit to find tree SHA
        log.debug("Getting commit {} from mirror for tree SHA", suitePr.getBaseCommitSha());
        var baseCommitNode = gitHubApiClient.getCommit(mirrorOwner, mirrorRepoName, suitePr.getBaseCommitSha());
        String baseTreeSha = baseCommitNode.get("tree").get("sha").asText();

        // b. Create blob for workflow file content
        log.debug("Creating workflow blob for bot={}", bot.getName());
        var blobNode = gitHubApiClient.createBlob(mirrorOwner, mirrorRepoName, bot.getWorkflowContent());
        String blobSha = blobNode.get("sha").asText();

        // c. Create new tree with workflow file added
        String workflowFilePath = ".github/workflows/" + bot.getWorkflowFileName();
        List<Map<String, Object>> treeItems = List.of(Map.of(
                "path", workflowFilePath,
                "mode", "100644",
                "type", "blob",
                "sha", blobSha
        ));
        log.debug("Creating tree with workflow file at {}", workflowFilePath);
        var newTreeNode = gitHubApiClient.createTree(mirrorOwner, mirrorRepoName, baseTreeSha, treeItems);
        String newTreeSha = newTreeNode.get("sha").asText();

        // d. Create commit on top of baseCommitSha
        String commitMessage = "Add benchmark workflow for bot: " + bot.getName();
        var newCommitNode = gitHubApiClient.createCommit(
                mirrorOwner, mirrorRepoName, commitMessage, newTreeSha,
                List.of(suitePr.getBaseCommitSha()));
        String newCommitSha = newCommitNode.get("sha").asText();

        // e. Create ref for base branch pointing to new commit
        log.info("Creating base branch '{}' at commit {}", baseBranchName, newCommitSha);
        gitHubApiClient.createRef(mirrorOwner, mirrorRepoName, "refs/heads/" + baseBranchName, newCommitSha);

        // Step 4: Push head commit to mirror as head branch using git push
        // This handles the case where headCommitSha may not exist in the mirror yet
        log.info("Pushing head commit {} as branch '{}'", suitePr.getHeadCommitSha(), headBranchName);
        String mirrorAuthUrl = buildAuthUrl(mirrorOwner, mirrorRepoName);
        runGit(workspacePath,
                "push", mirrorAuthUrl,
                suitePr.getHeadCommitSha() + ":refs/heads/" + headBranchName);

        log.info("Replay branches created: base='{}', head='{}'", baseBranchName, headBranchName);
    }

    private void runGit(Path workDir, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }

        // Redact auth tokens from logs
        String logCommand = command.stream()
                .map(s -> s.replaceAll("x-access-token:[^@]+@", "x-access-token:***@"))
                .collect(Collectors.joining(" "));
        log.debug("Running git command in {}: {}", workDir, logCommand);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start git process: " + logCommand, e);
        }

        // Capture stdout and stderr concurrently to avoid blocking
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();

        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutBuilder.append(line).append("\n");
                }
            } catch (IOException e) {
                log.warn("Error reading git stdout: {}", e.getMessage());
            }
        });

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrBuilder.append(line).append("\n");
                }
            } catch (IOException e) {
                log.warn("Error reading git stderr: {}", e.getMessage());
            }
        });

        stdoutThread.start();
        stderrThread.start();

        boolean finished;
        try {
            finished = process.waitFor(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new RuntimeException("Interrupted waiting for git command: " + logCommand, e);
        }

        try {
            stdoutThread.join(5000);
            stderrThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Git command timed out after 120s: " + logCommand);
        }

        int exitCode = process.exitValue();
        String stdout = stdoutBuilder.toString();
        String stderr = stderrBuilder.toString();

        if (exitCode != 0) {
            // Redact tokens from output logs too
            String redactedStdout = stdout.replaceAll("x-access-token:[^@]+@", "x-access-token:***@");
            String redactedStderr = stderr.replaceAll("x-access-token:[^@]+@", "x-access-token:***@");
            log.error("Git command failed (exit {}): {}\nstdout: {}\nstderr: {}",
                    exitCode, logCommand, redactedStdout, redactedStderr);
            throw new RuntimeException("Git command failed with exit code " + exitCode + ": " + logCommand
                    + "\nstderr: " + redactedStderr.trim());
        }

        if (!stdout.isBlank()) {
            log.debug("git stdout: {}", stdout.trim());
        }
    }

    private String buildAuthUrl(String owner, String repo) {
        String token = authService.getInstallationToken();
        return "https://x-access-token:" + token + "@github.com/" + owner + "/" + repo + ".git";
    }
}
