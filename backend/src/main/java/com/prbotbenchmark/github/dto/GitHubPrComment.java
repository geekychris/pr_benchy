package com.prbotbenchmark.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GitHubPrComment(
        long id,
        String body,
        String path,
        Integer line,
        @JsonProperty("original_line") Integer originalLine,
        @JsonProperty("diff_hunk") String diffHunk,
        GitHubUser user,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("html_url") String htmlUrl
) {
}
