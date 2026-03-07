package com.prbotbenchmark.reporting.dto;

public record BotStatsDto(
        Long botId,
        String botName,
        int totalComments,
        int reviewComments,
        int prComments,
        int reviews,
        int validCount,
        int invalidCount,
        int duplicateCount,
        int needsReviewCount,
        int ungradedCount
) {}
