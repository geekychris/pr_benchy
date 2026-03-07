package com.prbotbenchmark.grading.dto;

public record UpdateIssueTypeRequest(
        String name,
        String description,
        String category,
        String severityHint,
        Boolean active
) {}
