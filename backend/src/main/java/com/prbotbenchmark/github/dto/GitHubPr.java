package com.prbotbenchmark.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record GitHubPr(
        int number,
        String title,
        String body,
        String state,
        @JsonProperty("html_url") String htmlUrl,
        GitHubUser user,
        GitHubBranch head,
        GitHubBranch base,
        @JsonProperty("merged_at") Instant mergedAt,
        @JsonProperty("changed_files") int changedFiles,
        int additions,
        int deletions,
        List<GitHubLabel> labels
) {
}
