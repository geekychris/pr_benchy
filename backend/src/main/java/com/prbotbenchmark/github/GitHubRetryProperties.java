package com.prbotbenchmark.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.github.retry")
public record GitHubRetryProperties(
        int maxAttempts,
        long initialBackoffMs,
        double backoffMultiplier,
        int rateLimitRetryAfterSeconds
) {
    public GitHubRetryProperties {
        if (maxAttempts == 0) maxAttempts = 3;
        if (initialBackoffMs == 0) initialBackoffMs = 1000;
        if (backoffMultiplier == 0.0) backoffMultiplier = 2.0;
        if (rateLimitRetryAfterSeconds == 0) rateLimitRetryAfterSeconds = 60;
    }
}
