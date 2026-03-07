package com.prbotbenchmark.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubCheckRun(
        long id,
        String name,
        String status,
        String conclusion,
        @JsonProperty("html_url") String htmlUrl
) {
}
