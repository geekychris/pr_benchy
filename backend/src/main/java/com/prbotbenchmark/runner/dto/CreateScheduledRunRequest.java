package com.prbotbenchmark.runner.dto;

import java.util.List;

public record CreateScheduledRunRequest(
        String name,
        Long suiteId,
        List<Long> botIds,
        String cronExpression,
        Integer concurrency,
        Boolean goldenDatasetEnabled
) {}
