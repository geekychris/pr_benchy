package com.prbotbenchmark.setup;

import com.prbotbenchmark.github.GitHubApiClient;
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
public class RateLimitController {

    private final GitHubApiClient gitHubApiClient;

    @GetMapping("/rate-limit")
    public ResponseEntity<GitHubApiClient.RateLimitState> getRateLimit() {
        return ResponseEntity.ok(gitHubApiClient.getRateLimitState());
    }
}
