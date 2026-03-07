package com.prbotbenchmark.grading.dto;

import java.util.List;

public record BulkGradeResult(
        int successCount,
        int skippedCount,
        List<String> errors
) {}
