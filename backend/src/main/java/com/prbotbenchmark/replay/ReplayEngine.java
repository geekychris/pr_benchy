package com.prbotbenchmark.replay;

import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.bot.domain.Bot;
import com.prbotbenchmark.github.GitHubApiClient;
import com.prbotbenchmark.github.GitHubAppProperties;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import com.prbotbenchmark.runner.ReplayPrRepository;
import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.ReplayPr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayEngine {

    private final GitHubApiClient gitHubApiClient;
    private final GitWorkspaceService gitWorkspaceService;
    private final GitHubAppProperties gitHubAppProperties;
    private final ReplayPrRepository replayPrRepository;

    @Value("${app.workspace.base-path}")
    private String workspaceBasePath;

    @Transactional
    public void createReplayPr(ReplayPr replayPr) throws java.io.IOException, InterruptedException {
        SuitePr suitePr = replayPr.getSuitePr();
        Bot bot = replayPr.getBot();
        BenchmarkRun run = replayPr.getRun();
        ExemplarRepo exemplarRepo = run.getSuite().getExemplarRepo();

        String mirrorOwner = exemplarRepo.getMirrorOrg() != null
                ? exemplarRepo.getMirrorOrg()
                : exemplarRepo.getOwner();
        String mirrorRepoName = exemplarRepo.getMirrorRepoName() != null
                ? exemplarRepo.getMirrorRepoName()
                : exemplarRepo.getRepoName() + "-benchmark";

        log.info("Creating replay PR for ReplayPr id={}, suitePr={}, bot={}, mirror={}/{}",
                replayPr.getId(), suitePr.getOriginalPrNumber(), bot.getName(), mirrorOwner, mirrorRepoName);

        // Ensure mirror repo exists
        gitWorkspaceService.ensureMirrorRepoExists(exemplarRepo, mirrorOwner, mirrorRepoName);

        // Build branch names scoped to run + PR number to avoid collisions
        String baseBranchName = "benchmark/" + run.getId() + "/" + suitePr.getOriginalPrNumber() + "/base";
        String headBranchName = "benchmark/" + run.getId() + "/" + suitePr.getOriginalPrNumber() + "/head";

        // Persist branch names before doing remote operations so we can clean up on failure
        replayPr.setBaseBranch(baseBranchName);
        replayPr.setHeadBranch(headBranchName);
        replayPrRepository.save(replayPr);

        // Create branches in the mirror repo
        gitWorkspaceService.createReplayBranches(
                exemplarRepo,
                mirrorOwner,
                mirrorRepoName,
                suitePr,
                bot,
                baseBranchName,
                headBranchName
        );

        // Open the PR in the mirror repo
        String prTitle = "[Benchmark] PR #" + suitePr.getOriginalPrNumber()
                + (suitePr.getTitle() != null ? " - " + suitePr.getTitle() : "");
        String prBody = "Benchmark replay PR for original PR #" + suitePr.getOriginalPrNumber()
                + "\n\nBot: " + bot.getName()
                + "\nRun ID: " + run.getId()
                + "\nOriginal author: " + suitePr.getAuthor()
                + "\nBase commit: " + suitePr.getBaseCommitSha()
                + "\nHead commit: " + suitePr.getHeadCommitSha();

        var prResponse = gitHubApiClient.createPullRequest(
                mirrorOwner, mirrorRepoName, prTitle, prBody, headBranchName, baseBranchName);

        int mirrorPrNumber = prResponse.get("number").asInt();
        String mirrorPrUrl = prResponse.get("html_url").asText();

        replayPr.setMirrorPrNumber(mirrorPrNumber);
        replayPr.setMirrorPrUrl(mirrorPrUrl);
        replayPrRepository.save(replayPr);

        log.info("ReplayPr id={}: created mirror PR #{} at {}", replayPr.getId(), mirrorPrNumber, mirrorPrUrl);
    }
}
