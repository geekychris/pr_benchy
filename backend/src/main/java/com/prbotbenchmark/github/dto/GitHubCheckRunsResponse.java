package com.prbotbenchmark.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GitHubCheckRunsResponse(
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("check_runs") List<GitHubCheckRun> checkRuns
) {
}
