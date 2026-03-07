package com.prbotbenchmark.github;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies GitHub webhook payloads using HMAC-SHA256 signatures.
 *
 * Requires app.github.webhook-secret to be set in application configuration
 * (matches the webhook secret configured in the GitHub App settings).
 * If left blank, signature verification is skipped with a one-time warning.
 */
@Slf4j
@Service
public class WebhookVerificationService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    @Value("${app.github.webhook-secret:}")
    private String webhookSecret;

    private volatile boolean warningLogged = false;

    @PostConstruct
    void init() {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("app.github.webhook-secret is not configured — GitHub webhook signature verification is DISABLED. " +
                    "Set this property to the webhook secret from your GitHub App settings to enable verification.");
            warningLogged = true;
        }
    }

    /**
     * Verifies the HMAC-SHA256 signature of an incoming webhook payload.
     *
     * @param payload         the raw request body as a UTF-8 string
     * @param signatureHeader the value of the X-Hub-Signature-256 header (format: "sha256={hex}")
     * @return true if the signature is valid (or if webhook verification is disabled)
     */
    public boolean verify(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            if (!warningLogged) {
                log.warn("app.github.webhook-secret is not configured — skipping signature verification.");
                warningLogged = true;
            }
            return true;
        }

        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Missing or malformed X-Hub-Signature-256 header");
            return false;
        }

        String receivedHex = signatureHeader.substring(SIGNATURE_PREFIX.length());

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] computedBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] receivedBytes = HexFormat.of().parseHex(receivedHex);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(computedBytes, receivedBytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA256 for webhook verification", e);
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid hex encoding in X-Hub-Signature-256 header: {}", receivedHex);
            return false;
        }
    }
}
