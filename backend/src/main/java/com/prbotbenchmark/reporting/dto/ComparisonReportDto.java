package com.prbotbenchmark.reporting.dto;

import java.util.List;

public record ComparisonReportDto(
        Long runId,
        int goldenEntryCount,
        List<BotComparisonDto> botComparisons
) {}
