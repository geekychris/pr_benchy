package com.prbotbenchmark.reporting.dto;

import java.time.Instant;

public record TrendDataPointDto(
        Long runId,
        String runName,
        Instant startedAt,
        double precision,
        double recall,
        double f1Score,
        int totalComments,
        int truePositives,
        int falsePositives,
        int falseNegatives
) {}
