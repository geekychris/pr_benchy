package com.prbotbenchmark.setup.dto;

public record SetupStatusDto(
        String appId,
        String installationId,
        boolean jwtOk,
        boolean tokenOk,
        String defaultMirrorOrg,
        String errorMessage
) {}
