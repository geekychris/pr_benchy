package com.prbotbenchmark.repository.dto;

public record UpdateRepoRequest(
        String name,
        String mirrorOrg,
        String mirrorRepoName,
        String defaultBranch
) {}
