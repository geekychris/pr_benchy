package com.prbotbenchmark.github;

/**
 * Spring application event published when a GitHub webhook indicates a bot has completed
 * work on a PR (e.g., a check_run completed or a pull_request_review submitted).
 * RunOrchestrator listens for this event to short-circuit the polling loop.
 */
public record BotCompletionEvent(String repoFullName, int prNumber, String eventType) {}
