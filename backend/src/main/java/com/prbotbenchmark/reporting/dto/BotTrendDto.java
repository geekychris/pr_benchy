package com.prbotbenchmark.reporting.dto;

import java.util.List;

public record BotTrendDto(
        Long botId,
        String botName,
        List<TrendDataPointDto> dataPoints
) {}
