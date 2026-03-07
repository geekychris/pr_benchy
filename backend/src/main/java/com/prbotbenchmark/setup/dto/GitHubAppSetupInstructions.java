package com.prbotbenchmark.setup.dto;

import java.util.List;

public record GitHubAppSetupInstructions(
        List<String> requiredPermissions,
        List<String> requiredEvents,
        List<String> setupSteps
) {}
