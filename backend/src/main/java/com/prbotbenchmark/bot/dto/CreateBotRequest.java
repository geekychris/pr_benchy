package com.prbotbenchmark.bot.dto;

public record CreateBotRequest(
        String name,
        String description,
        String workflowFileName,
        String workflowContent,
        String waitStrategy,
        Integer timeoutSeconds
) {}
