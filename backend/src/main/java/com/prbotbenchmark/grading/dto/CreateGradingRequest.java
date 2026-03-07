package com.prbotbenchmark.grading.dto;

public record CreateGradingRequest(
        Long commentId,
        String commentTableType,
        String graderType,
        String graderId,
        String verdict,
        String severity,
        Integer stars,
        Boolean flagged,
        String notes
) {}
