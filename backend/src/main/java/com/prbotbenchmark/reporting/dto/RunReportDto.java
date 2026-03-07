package com.prbotbenchmark.reporting.dto;

import java.time.Instant;
import java.util.List;

public record RunReportDto(
        Long runId,
        String runName,
        String status,
        Instant startedAt,
        Instant completedAt,
        int totalReplayPrs,
        int completedReplayPrs,
        int failedReplayPrs,
        List<BotStatsDto> botStats
) {}
