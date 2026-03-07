package com.prbotbenchmark.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.github")
public record GitHubAppProperties(
        String appId,
        String privateKeyPath,
        String installationId,
        String defaultMirrorOrg,
        String apiUrl,
        int pollIntervalSeconds,
        int defaultBotTimeoutSeconds
) {
}
