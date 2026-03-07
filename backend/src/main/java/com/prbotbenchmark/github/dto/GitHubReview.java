package com.prbotbenchmark.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GitHubReview(
        long id,
        String body,
        String state,
        GitHubUser user,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("html_url") String htmlUrl
) {
}
