package com.prbotbenchmark.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.prbotbenchmark.github.GitHubAppAuthService;
import com.prbotbenchmark.github.GitHubAppProperties;
import com.prbotbenchmark.setup.dto.GitHubAppSetupInstructions;
import com.prbotbenchmark.setup.dto.SetupStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SetupService {

    private final GitHubAppAuthService authService;
    private final GitHubAppProperties props;

    public SetupStatusDto getStatus() {
        boolean jwtOk = false;
        boolean tokenOk = false;
        String errorMessage = null;

        try {
            authService.generateAppJwt();
            jwtOk = true;
        } catch (Exception e) {
            log.warn("JWT generation failed during setup status check: {}", e.getMessage());
            errorMessage = "JWT generation failed: " + e.getMessage();
        }

        if (jwtOk) {
            try {
                authService.getInstallationToken();
                tokenOk = true;
            } catch (Exception e) {
                log.warn("Installation token retrieval failed during setup status check: {}", e.getMessage());
                errorMessage = (errorMessage != null ? errorMessage + "; " : "") +
                        "Installation token failed: " + e.getMessage();
            }
        }

        return new SetupStatusDto(
                props.appId(),
                props.installationId(),
                jwtOk,
                tokenOk,
                props.defaultMirrorOrg(),
                errorMessage
        );
    }

    public JsonNode listInstallations() throws Exception {
        return authService.listInstallations();
    }

    public GitHubAppSetupInstructions getSetupInstructions() {
        List<String> requiredPermissions = List.of(
                "Contents: Read & Write",
                "Pull requests: Read & Write",
                "Checks: Read",
                "Workflows: Read & Write",
                "Metadata: Read (required)"
        );

        List<String> requiredEvents = List.of(
                "Pull request",
                "Pull request review",
                "Check run",
                "Check suite"
        );

        List<String> setupSteps = List.of(
                "1. Go to GitHub Settings > Developer settings > GitHub Apps > New GitHub App",
                "2. Set Homepage URL to any valid URL",
                "3. Set Webhook URL to your server URL (or use smee.io for local dev)",
                "4. Configure permissions as listed",
                "5. Subscribe to events as listed",
                "6. Generate a private key and download the PEM file",
                "7. Install the App on your organization",
                "8. Note the App ID and Installation ID",
                "9. Set GITHUB_APP_ID, GITHUB_APP_PRIVATE_KEY_PATH, GITHUB_APP_INSTALLATION_ID in your environment"
        );

        return new GitHubAppSetupInstructions(requiredPermissions, requiredEvents, setupSteps);
    }
}
