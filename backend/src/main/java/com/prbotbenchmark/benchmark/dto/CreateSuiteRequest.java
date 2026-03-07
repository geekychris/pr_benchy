package com.prbotbenchmark.benchmark.dto;

public record CreateSuiteRequest(
        Long exemplarRepoId,
        String name,
        String description
) {}
