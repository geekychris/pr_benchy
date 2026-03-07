package com.prbotbenchmark.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubRepo(
        @JsonProperty("full_name") String fullName,
        String name,
        @JsonProperty("clone_url") String cloneUrl,
        @JsonProperty("default_branch") String defaultBranch
) {
}
