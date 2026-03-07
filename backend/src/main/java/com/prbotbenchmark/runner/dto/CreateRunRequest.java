package com.prbotbenchmark.runner.dto;

import java.util.List;

public record CreateRunRequest(
        Long suiteId,
        String name,
        List<Long> botIds,
        Integer concurrency,
        Boolean goldenDatasetEnabled
) {}
