package com.prbotbenchmark.bot.dto;

public record UpdateBotRequest(
        String name,
        String description,
        String workflowFileName,
        String workflowContent,
        String waitStrategy,
        Integer timeoutSeconds
) {}
