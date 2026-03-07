package com.prbotbenchmark.grading.dto;

public record UpdateGoldenEntryRequest(
        String issueType,
        String description,
        String canonicalBody,
        Boolean active,
        Boolean includedByDefault
) {}
