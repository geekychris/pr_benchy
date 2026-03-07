package com.prbotbenchmark.reporting.dto;

public record BotComparisonDto(
        Long botId,
        String botName,
        int truePositives,
        int falseNegatives,
        int falsePositives,
        double precision,
        double recall,
        double f1Score
) {}
