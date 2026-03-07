package com.prbotbenchmark.runner.dto;

public record RunProgressDto(
        Long runId,
        String status,
        int total,
        int pending,
        int creating,
        int waiting,
        int collecting,
        int completed,
        int failed
) {}
