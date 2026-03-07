package com.prbotbenchmark.grading.dto;

public record GradingProgressDto(
        Long runId,
        Long suiteId,
        int totalComments,
        int gradedComments,
        int ungradedComments,
        double percentComplete,
        int validCount,
        int invalidCount,
        int duplicateCount,
        int needsReviewCount,
        int flaggedCount
) {}
