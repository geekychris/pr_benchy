package com.prbotbenchmark.runner;

import com.prbotbenchmark.benchmark.SuitePrRepository;
import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.bot.domain.Bot;
import com.prbotbenchmark.collection.CommentCollector;
import com.prbotbenchmark.github.BotCompletionEvent;
import com.prbotbenchmark.github.GitHubApiClient;
import com.prbotbenchmark.github.GitHubAppProperties;
import com.prbotbenchmark.replay.ReplayEngine;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.ReplayPr;
import com.prbotbenchmark.runner.domain.ReplayStatus;
import com.prbotbenchmark.runner.domain.RunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunOrchestrator {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final ReplayPrRepository replayPrRepository;
    private final SuitePrRepository suitePrRepository;
    private final ReplayEngine replayEngine;
    private final CommentCollector commentCollector;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubAppProperties gitHubAppProperties;

    @Qualifier("replayExecutor")
    private final ThreadPoolTaskExecutor replayExecutor;

    /**
     * Tracks in-flight PR waits keyed by "repoFullName/prNumber".
     * When a BotCompletionEvent arrives via webhook, the corresponding future is
     * completed immediately so waitForBots() can exit without waiting for the next
     * poll cycle.
     */
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pendingPrCompletions = new ConcurrentHashMap<>();

    @EventListener
    public void onBotCompletion(BotCompletionEvent event) {
        String key = event.repoFullName() + "/" + event.prNumber();
        CompletableFuture<Void> future = pendingPrCompletions.get(key);
        if (future != null) {
            log.info("Webhook triggered completion for PR {}", key);
            future.complete(null);
        }
    }

    @Async("runExecutor")
    @Transactional
    public void startRunAsync(BenchmarkRun run) {
        log.info("Starting BenchmarkRun id={} name='{}'", run.getId(), run.getName());

        // Set to RUNNING
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        benchmarkRunRepository.save(run);

        List<SuitePr> suitePrs = suitePrRepository.findBySuiteId(run.getSuite().getId());
        List<Bot> bots = run.getBots();

        log.info("Run id={}: {} suite PRs x {} bots = {} replay PRs",
                run.getId(), suitePrs.size(), bots.size(), suitePrs.size() * bots.size());

        // Create all ReplayPr entities upfront
        List<ReplayPr> replayPrs = new ArrayList<>();
        for (SuitePr suitePr : suitePrs) {
            for (Bot bot : bots) {
                ReplayPr replayPr = new ReplayPr();
                replayPr.setRun(run);
                replayPr.setSuitePr(suitePr);
                replayPr.setBot(bot);
                replayPr.setStatus(ReplayStatus.PENDING);
                replayPr.setCreatedAt(Instant.now());
                replayPrs.add(replayPr);
            }
        }
        replayPrRepository.saveAll(replayPrs);
        log.info("Run id={}: saved {} ReplayPr records", run.getId(), replayPrs.size());

        // Semaphore for concurrency control
        int concurrency = run.getConcurrency() != null && run.getConcurrency() > 0 ? run.getConcurrency() : 1;
        Semaphore semaphore = new Semaphore(concurrency);

        // Submit all replay PRs as async tasks
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ReplayPr replayPr : replayPrs) {
            final Long replayPrId = replayPr.getId();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Run id={}: interrupted while acquiring semaphore for ReplayPr id={}", run.getId(), replayPrId);
                    return;
                }
                try {
                    // Re-fetch from DB inside the async thread to avoid detached entity issues
                    ReplayPr freshReplayPr = replayPrRepository.findById(replayPrId)
                            .orElseThrow(() -> new IllegalStateException("ReplayPr not found: " + replayPrId));
                    processReplayPr(freshReplayPr);
                } finally {
                    semaphore.release();
                }
            }, replayExecutor);
            futures.add(future);
        }

        // Wait for all to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("Run id={}: error waiting for futures to complete", run.getId(), e);
        }

        // Determine final status
        long failedCount = replayPrRepository.countByRunIdAndStatus(run.getId(), ReplayStatus.FAILED);
        RunStatus finalStatus = failedCount > 0 ? RunStatus.FAILED : RunStatus.COMPLETED;

        run.setStatus(finalStatus);
        run.setCompletedAt(Instant.now());
        if (failedCount > 0) {
            run.setErrorMessage(failedCount + " replay PR(s) failed");
        }
        benchmarkRunRepository.save(run);

        log.info("Run id={} finished with status={}, {} failed out of {}",
                run.getId(), finalStatus, failedCount, replayPrs.size());
    }

    @Transactional
    protected void processReplayPr(ReplayPr replayPr) {
        log.info("Processing ReplayPr id={} suitePr={} bot={}",
                replayPr.getId(),
                replayPr.getSuitePr().getOriginalPrNumber(),
                replayPr.getBot().getName());
        try {
            replayPr.setStatus(ReplayStatus.CREATING_BRANCHES);
            replayPrRepository.save(replayPr);

            replayEngine.createReplayPr(replayPr);

            replayPr.setStatus(ReplayStatus.WAITING_FOR_BOTS);
            replayPrRepository.save(replayPr);

            waitForBots(replayPr);

            replayPr.setStatus(ReplayStatus.COLLECTING_COMMENTS);
            replayPrRepository.save(replayPr);

            commentCollector.collectComments(replayPr);

            replayPr.setStatus(ReplayStatus.COMPLETED);
            replayPr.setCompletedAt(Instant.now());
            replayPrRepository.save(replayPr);

            log.info("ReplayPr id={} completed successfully", replayPr.getId());

        } catch (Exception e) {
            log.error("ReplayPr id={} failed: {}", replayPr.getId(), e.getMessage(), e);
            replayPr.setStatus(ReplayStatus.FAILED);
            replayPr.setErrorMessage(e.getMessage());
            replayPr.setCompletedAt(Instant.now());
            replayPrRepository.save(replayPr);
        }
    }

    private void waitForBots(ReplayPr replayPr) {
        Bot bot = replayPr.getBot();
        BenchmarkRun run = replayPr.getRun();
        ExemplarRepo exemplarRepo = run.getSuite().getExemplarRepo();

        String mirrorOwner = exemplarRepo.getMirrorOrg() != null
                ? exemplarRepo.getMirrorOrg()
                : exemplarRepo.getOwner();
        String mirrorRepoName = exemplarRepo.getMirrorRepoName() != null
                ? exemplarRepo.getMirrorRepoName()
                : exemplarRepo.getRepoName() + "-benchmark";

        int timeoutSeconds = bot.getTimeoutSeconds() != null
                ? bot.getTimeoutSeconds()
                : gitHubAppProperties.defaultBotTimeoutSeconds();
        int pollIntervalSeconds = gitHubAppProperties.pollIntervalSeconds();
        String waitStrategy = bot.getWaitStrategy() != null ? bot.getWaitStrategy() : "CHECKS";

        int mirrorPrNumber = replayPr.getMirrorPrNumber();
        String headBranch = replayPr.getHeadBranch();

        log.info("ReplayPr id={}: waiting for bots (strategy={}, timeout={}s, poll={}s)",
                replayPr.getId(), waitStrategy, timeoutSeconds, pollIntervalSeconds);

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        // Register a CompletableFuture so that an incoming webhook can short-circuit the poll loop.
        String webhookKey = mirrorOwner + "/" + mirrorRepoName + "/" + mirrorPrNumber;
        CompletableFuture<Void> webhookFuture = new CompletableFuture<>();
        pendingPrCompletions.put(webhookKey, webhookFuture);

        try {
            while (true) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= timeoutMs) {
                    log.warn("ReplayPr id={}: bot wait timeout exceeded ({}s) - continuing with comment collection",
                            replayPr.getId(), timeoutSeconds);
                    break;
                }

                // Short-circuit: webhook has already signalled completion
                if (webhookFuture.isDone()) {
                    log.info("ReplayPr id={}: webhook signalled bot completion, skipping remaining poll cycles",
                            replayPr.getId());
                    break;
                }

                boolean checksComplete = false;
                boolean reviewsComplete = false;

                try {
                    if ("CHECKS".equals(waitStrategy) || "BOTH".equals(waitStrategy)) {
                        checksComplete = areChecksComplete(mirrorOwner, mirrorRepoName, headBranch, replayPr.getId());
                    } else {
                        checksComplete = true; // Not required by strategy
                    }

                    if ("REVIEWS".equals(waitStrategy) || "BOTH".equals(waitStrategy)) {
                        reviewsComplete = hasReview(mirrorOwner, mirrorRepoName, mirrorPrNumber, replayPr.getId());
                    } else {
                        reviewsComplete = true; // Not required by strategy
                    }
                } catch (Exception e) {
                    log.warn("ReplayPr id={}: error polling bot status, will retry: {}", replayPr.getId(), e.getMessage());
                }

                if (checksComplete && reviewsComplete) {
                    log.info("ReplayPr id={}: bots completed (checksComplete={}, reviewsComplete={})",
                            replayPr.getId(), checksComplete, reviewsComplete);
                    break;
                }

                log.debug("ReplayPr id={}: still waiting (checksComplete={}, reviewsComplete={}, elapsed={}s)",
                        replayPr.getId(), checksComplete, reviewsComplete, elapsed / 1000);

                try {
                    Thread.sleep(pollIntervalSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("ReplayPr id={}: interrupted during bot wait", replayPr.getId());
                    break;
                }
            }
        } finally {
            pendingPrCompletions.remove(webhookKey);
        }
    }

    private boolean areChecksComplete(String owner, String repo, String ref, Long replayPrId) {
        try {
            var checkRunsResponse = gitHubApiClient.listCheckRuns(owner, repo, ref);
            if (checkRunsResponse == null || checkRunsResponse.checkRuns() == null
                    || checkRunsResponse.checkRuns().isEmpty()) {
                log.debug("ReplayPr id={}: no check runs found yet for ref={}", replayPrId, ref);
                return false;
            }
            boolean allCompleted = checkRunsResponse.checkRuns().stream()
                    .allMatch(cr -> "completed".equalsIgnoreCase(cr.status()));
            log.debug("ReplayPr id={}: {} check runs, allCompleted={}",
                    replayPrId, checkRunsResponse.checkRuns().size(), allCompleted);
            return allCompleted;
        } catch (Exception e) {
            log.warn("ReplayPr id={}: error listing check runs: {}", replayPrId, e.getMessage());
            return false;
        }
    }

    private boolean hasReview(String owner, String repo, int prNumber, Long replayPrId) {
        try {
            var reviews = gitHubApiClient.listPrReviews(owner, repo, prNumber);
            boolean hasReviews = reviews != null && !reviews.isEmpty();
            log.debug("ReplayPr id={}: {} reviews found", replayPrId, reviews != null ? reviews.size() : 0);
            return hasReviews;
        } catch (Exception e) {
            log.warn("ReplayPr id={}: error listing PR reviews: {}", replayPrId, e.getMessage());
            return false;
        }
    }

    public void cleanupRun(Long runId) {
        log.info("Cleaning up run id={}", runId);
        List<ReplayPr> replayPrs = replayPrRepository.findByRunId(runId);

        for (ReplayPr replayPr : replayPrs) {
            if (replayPr.getMirrorPrNumber() == null) {
                continue;
            }

            BenchmarkRun run = replayPr.getRun();
            ExemplarRepo exemplarRepo = run.getSuite().getExemplarRepo();

            String mirrorOwner = exemplarRepo.getMirrorOrg() != null
                    ? exemplarRepo.getMirrorOrg()
                    : exemplarRepo.getOwner();
            String mirrorRepoName = exemplarRepo.getMirrorRepoName() != null
                    ? exemplarRepo.getMirrorRepoName()
                    : exemplarRepo.getRepoName() + "-benchmark";

            try {
                log.info("Closing mirror PR #{} for ReplayPr id={}", replayPr.getMirrorPrNumber(), replayPr.getId());
                gitHubApiClient.closePullRequest(mirrorOwner, mirrorRepoName, replayPr.getMirrorPrNumber());
            } catch (Exception e) {
                log.error("Failed to close mirror PR #{} for ReplayPr id={}: {}",
                        replayPr.getMirrorPrNumber(), replayPr.getId(), e.getMessage());
            }

            if (replayPr.getBaseBranch() != null) {
                try {
                    log.info("Deleting base branch '{}' for ReplayPr id={}", replayPr.getBaseBranch(), replayPr.getId());
                    gitHubApiClient.deleteRef(mirrorOwner, mirrorRepoName, "refs/heads/" + replayPr.getBaseBranch());
                } catch (Exception e) {
                    log.error("Failed to delete base branch '{}' for ReplayPr id={}: {}",
                            replayPr.getBaseBranch(), replayPr.getId(), e.getMessage());
                }
            }

            if (replayPr.getHeadBranch() != null) {
                try {
                    log.info("Deleting head branch '{}' for ReplayPr id={}", replayPr.getHeadBranch(), replayPr.getId());
                    gitHubApiClient.deleteRef(mirrorOwner, mirrorRepoName, "refs/heads/" + replayPr.getHeadBranch());
                } catch (Exception e) {
                    log.error("Failed to delete head branch '{}' for ReplayPr id={}: {}",
                            replayPr.getHeadBranch(), replayPr.getId(), e.getMessage());
                }
            }
        }

        log.info("Cleanup complete for run id={}", runId);
    }
}
