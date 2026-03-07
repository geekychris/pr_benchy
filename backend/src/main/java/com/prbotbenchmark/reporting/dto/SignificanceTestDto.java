package com.prbotbenchmark.reporting.dto;

public record SignificanceTestDto(
        Long runAId,
        String runAName,
        Long runBId,
        String runBName,
        double runAF1,
        double runBF1,
        double pValue,
        boolean significant,
        String interpretation
) {}
