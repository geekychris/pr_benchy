package com.prbotbenchmark.grading.dto;

public record UpdateGradingRequest(
        String verdict,
        String severity,
        Integer stars,
        Boolean flagged,
        String notes
) {}
