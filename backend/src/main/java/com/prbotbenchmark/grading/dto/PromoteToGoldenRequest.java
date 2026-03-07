package com.prbotbenchmark.grading.dto;

public record PromoteToGoldenRequest(
        Long commentId,
        String commentTableType,
        String issueType,
        String description
) {}
