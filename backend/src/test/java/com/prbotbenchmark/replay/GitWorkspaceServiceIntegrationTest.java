package com.prbotbenchmark.replay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for git operations used by GitWorkspaceService.
 * Uses real git processes against local bare repos — no GitHub connectivity required.
 *
 * These tests verify that the core git interactions (clone, branch creation at SHA,
 * push specific commit as branch) work correctly before relying on them with live GitHub.
 */
class GitWorkspaceServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private Path bareRepoDir;
    private Path workspaceDir;
    private String initialCommitSha;
    private String secondCommitSha;

    @BeforeEach
    void setUpLocalRepos() throws IOException, InterruptedException {
        // Create a "bare" source repo (simulates the exemplar)
        bareRepoDir = tempDir.resolve("source.git");
        Files.createDirectories(bareRepoDir);
        runGit(bareRepoDir, "init", "--bare");

        // Create a working clone to make commits
        Path workClone = tempDir.resolve("work-clone");
        runGit(tempDir, "clone", bareRepoDir.toString(), workClone.toString());

        // Configure git identity for the work clone
        runGit(workClone, "config", "user.email", "test@test.com");
        runGit(workClone, "config", "user.name", "Test User");

        // First commit
        Path file = workClone.resolve("README.md");
        Files.writeString(file, "# Initial content\n");
        runGit(workClone, "add", ".");
        runGit(workClone, "commit", "-m", "Initial commit");
        runGit(workClone, "branch", "-M", "main");  // ensure branch is named "main" regardless of system default
        runGit(workClone, "push", "origin", "main");
        runGit(bareRepoDir, "symbolic-ref", "HEAD", "refs/heads/main");  // set bare repo default branch
        initialCommitSha = getHeadSha(workClone);

        // Second commit (simulates the PR head)
        Files.writeString(file, "# Updated content\nWith more text\n");
        Files.writeString(workClone.resolve("NewFile.java"), "public class NewFile {}");
        runGit(workClone, "add", ".");
        runGit(workClone, "commit", "-m", "Add new file");
        runGit(workClone, "push", "origin", "main");
        secondCommitSha = getHeadSha(workClone);

        // Workspace is a fresh clone of the bare repo
        workspaceDir = tempDir.resolve("workspace");
        runGit(tempDir, "clone", "--no-single-branch", bareRepoDir.toString(), workspaceDir.toString());
        runGit(workspaceDir, "config", "user.email", "test@test.com");
        runGit(workspaceDir, "config", "user.name", "Test User");
    }

    @Test
    void initialCommitSha_isShorterShaOrFull() {
        assertThat(initialCommitSha).isNotBlank();
        assertThat(initialCommitSha).hasSize(40); // full SHA
    }

    @Test
    void createBranchAtSpecificSha_createsCorrectBranch() throws IOException, InterruptedException {
        // Simulate: create a "base" branch at the initial commit (PR's base)
        String branchName = "benchmark/run-1/pr-42/base";
        runGit(workspaceDir, "checkout", "-b", branchName, initialCommitSha);

        // Verify HEAD is at the expected SHA
        String headSha = getHeadSha(workspaceDir);
        assertThat(headSha).isEqualTo(initialCommitSha);
    }

    @Test
    void addWorkflowFileAndCommit_workflowFileExistsOnBranch() throws IOException, InterruptedException {
        String branchName = "benchmark/run-1/pr-42/base";
        runGit(workspaceDir, "checkout", "-b", branchName, initialCommitSha);

        // Inject a workflow file (simulates GitWorkspaceService adding the bot workflow)
        Path workflowDir = workspaceDir.resolve(".github/workflows");
        Files.createDirectories(workflowDir);
        Files.writeString(workflowDir.resolve("bot.yml"), "name: test-bot\non: [pull_request]\njobs:\n  review:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo hello\n");
        runGit(workspaceDir, "add", ".github/workflows/bot.yml");
        runGit(workspaceDir, "commit", "-m", "chore: add benchmark bot workflow");

        // Workflow file should be present on this branch
        assertThat(workflowDir.resolve("bot.yml")).exists();

        // The original commit should not have had this file
        runGit(workspaceDir, "checkout", initialCommitSha);
        assertThat(workspaceDir.resolve(".github/workflows/bot.yml")).doesNotExist();
    }

    @Test
    void pushSpecificShaAsBranch_branchExistsInRemote() throws IOException, InterruptedException {
        // Simulate pushing the PR's head commit as the "head" branch
        // This is what GitWorkspaceService does for the head branch
        String headBranch = "benchmark/run-1/pr-42/head";

        // Create a "mirror" bare repo (simulates the GitHub mirror)
        Path mirrorRepo = tempDir.resolve("mirror.git");
        Files.createDirectories(mirrorRepo);
        runGit(mirrorRepo, "init", "--bare");

        // First push all branches to mirror
        runGit(workspaceDir, "remote", "add", "mirror", mirrorRepo.toString());
        runGit(workspaceDir, "push", "mirror", "--all");

        // Now push a specific SHA as a new branch (core replay operation)
        runGit(workspaceDir, "push", "mirror",
               secondCommitSha + ":refs/heads/" + headBranch);

        // Verify the branch exists in mirror
        String branches = runGitOutput(mirrorRepo, "branch", "--list");
        assertThat(branches).contains(headBranch);
    }

    @Test
    void createReplayBranches_baseAndHeadAtDifferentShas() throws IOException, InterruptedException {
        // End-to-end: create base and head branches mimicking a PR replay
        Path mirrorRepo = tempDir.resolve("mirror2.git");
        Files.createDirectories(mirrorRepo);
        runGit(mirrorRepo, "init", "--bare");
        runGit(workspaceDir, "remote", "add", "mirror2", mirrorRepo.toString());
        runGit(workspaceDir, "push", "mirror2", "--all");

        String baseBranch = "benchmark/run-2/pr-7/base";
        String headBranch = "benchmark/run-2/pr-7/head";

        // Create base branch at first commit + workflow file
        runGit(workspaceDir, "checkout", "-b", baseBranch, initialCommitSha);
        Path workflowDir = workspaceDir.resolve(".github/workflows");
        Files.createDirectories(workflowDir);
        Files.writeString(workflowDir.resolve("bot.yml"), "name: bot\non: [pull_request]\n");
        runGit(workspaceDir, "add", ".");
        runGit(workspaceDir, "commit", "-m", "chore: add workflow");
        runGit(workspaceDir, "push", "mirror2", baseBranch + ":refs/heads/" + baseBranch);

        // Push head branch at second commit
        runGit(workspaceDir, "push", "mirror2",
               secondCommitSha + ":refs/heads/" + headBranch);

        // Verify both branches exist in mirror
        String branches = runGitOutput(mirrorRepo, "branch", "--list");
        assertThat(branches).contains(baseBranch);
        assertThat(branches).contains(headBranch);

        // Verify base and head point to different commits
        String baseSha = runGitOutput(mirrorRepo, "rev-parse", baseBranch).trim();
        String headSha = runGitOutput(mirrorRepo, "rev-parse", headBranch).trim();
        assertThat(baseSha).isNotEqualTo(headSha);
        assertThat(headSha).isEqualTo(secondCommitSha);
    }

    @Test
    void deleteBranch_branchNoLongerExists() throws IOException, InterruptedException {
        Path mirrorRepo = tempDir.resolve("mirror3.git");
        Files.createDirectories(mirrorRepo);
        runGit(mirrorRepo, "init", "--bare");
        runGit(workspaceDir, "remote", "add", "mirror3", mirrorRepo.toString());
        runGit(workspaceDir, "push", "mirror3", "--all");

        String branch = "benchmark/run-1/pr-99/head";
        runGit(workspaceDir, "push", "mirror3",
               secondCommitSha + ":refs/heads/" + branch);

        // Verify it exists
        assertThat(runGitOutput(mirrorRepo, "branch", "--list")).contains(branch);

        // Delete it (simulate cleanup)
        runGit(workspaceDir, "push", "mirror3", ":refs/heads/" + branch);

        // Verify it's gone
        assertThat(runGitOutput(mirrorRepo, "branch", "--list")).doesNotContain(branch);
    }

    @Test
    void gitProcess_invalidCommand_throwsException() {
        assertThatThrownBy(() -> runGit(workspaceDir, "not-a-real-command"))
                .isInstanceOf(RuntimeException.class);
    }

    // ---- Helpers ----

    private void runGit(Path workDir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workDir.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("git " + String.join(" ", args) + " failed (exit " + exitCode + "): " + output);
        }
    }

    private String runGitOutput(Path workDir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workDir.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        return output;
    }

    private String getHeadSha(Path repoDir) throws IOException, InterruptedException {
        return runGitOutput(repoDir, "rev-parse", "HEAD").trim();
    }
}
