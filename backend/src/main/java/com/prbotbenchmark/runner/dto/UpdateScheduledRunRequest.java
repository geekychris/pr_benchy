package com.prbotbenchmark.runner.dto;

import java.util.List;

public record UpdateScheduledRunRequest(
        String name,
        List<Long> botIds,
        String cronExpression,
        Integer concurrency,
        Boolean goldenDatasetEnabled,
        Boolean enabled
) {}
