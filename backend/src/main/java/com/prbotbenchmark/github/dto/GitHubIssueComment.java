package com.prbotbenchmark.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GitHubIssueComment(
        long id,
        String body,
        GitHubUser user,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("html_url") String htmlUrl
) {
}
