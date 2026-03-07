package com.prbotbenchmark.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.prbotbenchmark.setup.dto.GitHubAppSetupInstructions;
import com.prbotbenchmark.setup.dto.SetupStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
@Slf4j
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    public ResponseEntity<SetupStatusDto> getStatus() {
        SetupStatusDto status = setupService.getStatus();
        return ResponseEntity.ok(status);
    }

    @GetMapping("/installations")
    public ResponseEntity<JsonNode> listInstallations() throws Exception {
        JsonNode installations = setupService.listInstallations();
        return ResponseEntity.ok(installations);
    }

    @GetMapping("/instructions")
    public ResponseEntity<GitHubAppSetupInstructions> getSetupInstructions() {
        GitHubAppSetupInstructions instructions = setupService.getSetupInstructions();
        return ResponseEntity.ok(instructions);
    }
}
