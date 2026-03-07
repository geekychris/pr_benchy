package com.prbotbenchmark.grading.dto;

import java.util.List;

public record BulkGradeRequest(
        List<Long> commentIds,
        String commentTableType,  // all must be same type
        String verdict,
        String severity,
        Integer stars,
        Boolean flagged,
        String notes,
        String graderId           // nullable; defaults to "bulk-human" if not provided
) {}
