package com.prbotbenchmark.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prbotbenchmark.runner.WebhookEventRepository;
import com.prbotbenchmark.runner.domain.WebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Receives GitHub webhook events at POST /api/webhooks/github.
 *
 * Required configuration property:
 *   app.github.webhook-secret — the webhook secret configured in the GitHub App settings
 *   (optional; if blank, signature verification is skipped with a warning)
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final WebhookVerificationService webhookVerificationService;
    private final WebhookEventRepository webhookEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Qualifier("githubObjectMapper")
    private final ObjectMapper githubObjectMapper;

    /**
     * Handles incoming GitHub webhook events.
     *
     * Uses {@code @RequestBody byte[]} to receive the raw bytes so HMAC-SHA256
     * verification is performed over the exact bytes GitHub signed, before any
     * charset conversion happens.
     */
    @PostMapping("/github")
    @Transactional
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        String payload = new String(body, StandardCharsets.UTF_8);

        // 1. Verify signature
        if (!webhookVerificationService.verify(payload, signature)) {
            log.warn("Webhook signature verification failed for delivery={}", deliveryId);
            return ResponseEntity.status(401).body("Invalid signature");
        }

        // 2. Idempotency — ignore duplicate deliveries
        if (deliveryId != null && webhookEventRepository.existsByDeliveryId(deliveryId)) {
            log.debug("Duplicate webhook delivery ignored: deliveryId={}", deliveryId);
            return ResponseEntity.ok("Already processed");
        }

        if (eventType == null) {
            log.warn("Webhook received with no X-GitHub-Event header, deliveryId={}", deliveryId);
            return ResponseEntity.badRequest().body("Missing X-GitHub-Event header");
        }

        // 3. Parse payload
        JsonNode root;
        try {
            root = githubObjectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook payload for deliveryId={}: {}", deliveryId, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON payload");
        }

        String action = root.path("action").asText(null);
        String repoFullName = root.path("repository").path("full_name").asText(null);
        Integer prNumber = extractPrNumber(root, eventType);

        log.info("Webhook received: event={}, action={}, repo={}, pr={}, deliveryId={}",
                eventType, action, repoFullName, prNumber, deliveryId);

        // 4. Persist for idempotency and audit
        WebhookEvent event = WebhookEvent.builder()
                .deliveryId(deliveryId)
                .eventType(eventType)
                .action(action)
                .repoFullName(repoFullName)
                .prNumber(prNumber)
                .payload(payload)
                .processedAt(Instant.now())
                .build();
        webhookEventRepository.save(event);

        // 5. Publish application event to short-circuit polling in RunOrchestrator
        if (repoFullName != null && prNumber != null) {
            if ("check_run".equals(eventType) && "completed".equals(action)) {
                log.info("Publishing BotCompletionEvent for check_run completed: repo={}, pr={}", repoFullName, prNumber);
                eventPublisher.publishEvent(new BotCompletionEvent(repoFullName, prNumber, eventType));
            } else if ("pull_request_review".equals(eventType) && "submitted".equals(action)) {
                log.info("Publishing BotCompletionEvent for pull_request_review submitted: repo={}, pr={}", repoFullName, prNumber);
                eventPublisher.publishEvent(new BotCompletionEvent(repoFullName, prNumber, eventType));
            }
        }

        return ResponseEntity.ok("OK");
    }

    /**
     * Extracts the PR number from the webhook payload depending on the event type.
     * <ul>
     *   <li>{@code check_run} — taken from {@code check_run.pull_requests[0].number}</li>
     *   <li>{@code pull_request_review} — taken from {@code pull_request.number}</li>
     *   <li>All other events — taken from {@code pull_request.number} as a best-effort fallback</li>
     * </ul>
     */
    private Integer extractPrNumber(JsonNode root, String eventType) {
        if ("check_run".equals(eventType)) {
            JsonNode pullRequests = root.path("check_run").path("pull_requests");
            if (pullRequests.isArray() && !pullRequests.isEmpty()) {
                JsonNode number = pullRequests.get(0).path("number");
                if (!number.isMissingNode()) {
                    return number.asInt();
                }
            }
            return null;
        }

        // pull_request_review and general fallback
        JsonNode number = root.path("pull_request").path("number");
        if (!number.isMissingNode()) {
            return number.asInt();
        }
        return null;
    }
}
