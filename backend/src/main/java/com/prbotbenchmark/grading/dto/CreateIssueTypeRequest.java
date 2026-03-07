package com.prbotbenchmark.grading.dto;

public record CreateIssueTypeRequest(
        String code,
        String name,
        String description,
        String category,
        String severityHint
) {}
