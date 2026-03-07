package com.prbotbenchmark.repository.dto;

public record RegisterRepoRequest(
        String name,
        String githubUrl,
        String mirrorOrg,
        String mirrorRepoName
) {}
