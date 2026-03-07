package com.prbotbenchmark.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages GitHub App authentication.
 * Generates short-lived JWTs (used to call /app endpoints)
 * and exchanges them for installation access tokens (used for repo operations).
 * Tokens are cached and refreshed automatically.
 */
@Service
@Slf4j
public class GitHubAppAuthService {

    private final GitHubAppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private String cachedToken;
    private Instant tokenExpiresAt;

    public GitHubAppAuthService(GitHubAppProperties props,
                                 @Qualifier("githubObjectMapper") ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Returns a valid installation access token, refreshing if needed.
     * Thread-safe.
     */
    public String getInstallationToken() {
        tokenLock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
                return cachedToken;
            }
            refreshToken();
            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }

    private void refreshToken() {
        try {
            String jwt = generateAppJwt();
            String token = fetchInstallationToken(jwt, props.installationId());
            cachedToken = token;
            // Installation tokens last 1 hour
            tokenExpiresAt = Instant.now().plusSeconds(3600);
            log.debug("Refreshed GitHub App installation token");
        } catch (Exception e) {
            throw new GitHubAuthException("Failed to refresh GitHub App installation token", e);
        }
    }

    /**
     * Generates a short-lived JWT signed with the app's RSA private key.
     * Valid for 10 minutes as per GitHub's requirements.
     */
    public String generateAppJwt() throws Exception {
        PrivateKey privateKey = loadPrivateKey();
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.appId())
                .issuedAt(Date.from(now.minusSeconds(60))) // backdate 60s for clock skew
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(privateKey)
                .compact();
    }

    private String fetchInstallationToken(String jwt, String installationId) throws Exception {
        String url = props.apiUrl() + "/app/installations/" + installationId + "/access_tokens";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new GitHubAuthException("Failed to get installation token: HTTP " + response.statusCode() + " - " + response.body());
        }
        JsonNode node = objectMapper.readTree(response.body());
        return node.get("token").asText();
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String keyContent = Files.readString(Path.of(props.privateKeyPath()));
        keyContent = keyContent
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    /**
     * Lists all installations for this GitHub App (useful for setup).
     */
    public JsonNode listInstallations() throws Exception {
        String jwt = generateAppJwt();
        String url = props.apiUrl() + "/app/installations";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }
}
