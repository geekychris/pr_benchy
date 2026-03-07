package com.prbotbenchmark.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prbotbenchmark.github.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GitHub REST API client using the GitHub App installation token for auth.
 * Handles pagination automatically for list endpoints.
 */
@Service
@Slf4j
public class GitHubApiClient {

    private static final int PAGE_SIZE = 100;

    private final GitHubAppAuthService authService;
    private final GitHubAppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final GitHubRetryProperties retryProps;

    private volatile RateLimitState rateLimitState = new RateLimitState(5000, 5000, Instant.now());

    public record RateLimitState(int limit, int remaining, Instant resetAt) {}

    public GitHubApiClient(GitHubAppAuthService authService,
                           GitHubAppProperties props,
                           @Qualifier("githubObjectMapper") ObjectMapper mapper,
                           GitHubRetryProperties retryProps) {
        this.authService = authService;
        this.props = props;
        this.mapper = mapper;
        this.retryProps = retryProps;
        this.httpClient = HttpClient.newHttpClient();
    }

    public RateLimitState getRateLimitState() { return rateLimitState; }

    // ---- Repository operations ----

    public JsonNode getRepository(String owner, String repo) throws IOException, InterruptedException {
        return getJson("/repos/" + owner + "/" + repo);
    }

    public JsonNode createRepository(String org, Map<String, Object> body) throws IOException, InterruptedException {
        return postJson("/orgs/" + org + "/repos", body);
    }

    // ---- PR listing and detail ----

    public List<GitHubPr> listPullRequests(String owner, String repo, String state) throws IOException, InterruptedException {
        return getPaginated("/repos/" + owner + "/" + repo + "/pulls?state=" + state + "&per_page=" + PAGE_SIZE,
                new TypeReference<>() {});
    }

    public GitHubPr getPullRequest(String owner, String repo, int prNumber) throws IOException, InterruptedException {
        String json = get("/repos/" + owner + "/" + repo + "/pulls/" + prNumber);
        return mapper.readValue(json, GitHubPr.class);
    }

    // ---- PR comments ----

    public List<GitHubPrComment> listPrReviewComments(String owner, String repo, int prNumber) throws IOException, InterruptedException {
        return getPaginated("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/comments?per_page=" + PAGE_SIZE,
                new TypeReference<>() {});
    }

    public List<GitHubReview> listPrReviews(String owner, String repo, int prNumber) throws IOException, InterruptedException {
        return getPaginated("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews?per_page=" + PAGE_SIZE,
                new TypeReference<>() {});
    }

    public List<GitHubIssueComment> listIssueComments(String owner, String repo, int issueNumber) throws IOException, InterruptedException {
        return getPaginated("/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/comments?per_page=" + PAGE_SIZE,
                new TypeReference<>() {});
    }

    // ---- Check runs (for waiting on bots) ----

    public GitHubCheckRunsResponse listCheckRuns(String owner, String repo, String ref) throws IOException, InterruptedException {
        String json = get("/repos/" + owner + "/" + repo + "/commits/" + ref + "/check-runs?per_page=100");
        return mapper.readValue(json, GitHubCheckRunsResponse.class);
    }

    // ---- PR creation ----

    public JsonNode createPullRequest(String owner, String repo, String title, String body,
                                      String head, String base) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of(
                "title", title,
                "body", body != null ? body : "",
                "head", head,
                "base", base,
                "draft", false
        );
        return postJson("/repos/" + owner + "/" + repo + "/pulls", payload);
    }

    public JsonNode closePullRequest(String owner, String repo, int prNumber) throws IOException, InterruptedException {
        return patchJson("/repos/" + owner + "/" + repo + "/pulls/" + prNumber, Map.of("state", "closed"));
    }

    // ---- Branch/ref operations ----

    public JsonNode getRef(String owner, String repo, String ref) throws IOException, InterruptedException {
        return getJson("/repos/" + owner + "/" + repo + "/git/refs/heads/" + ref);
    }

    public JsonNode createRef(String owner, String repo, String refName, String sha) throws IOException, InterruptedException {
        return postJson("/repos/" + owner + "/" + repo + "/git/refs",
                Map.of("ref", "refs/heads/" + refName, "sha", sha));
    }

    public void deleteRef(String owner, String repo, String refName) throws IOException, InterruptedException {
        delete("/repos/" + owner + "/" + repo + "/git/refs/heads/" + refName);
    }

    // ---- Commit/tree operations (for workflow file injection) ----

    public JsonNode getCommit(String owner, String repo, String sha) throws IOException, InterruptedException {
        return getJson("/repos/" + owner + "/" + repo + "/git/commits/" + sha);
    }

    public JsonNode createBlob(String owner, String repo, String content) throws IOException, InterruptedException {
        return postJson("/repos/" + owner + "/" + repo + "/git/blobs",
                Map.of("content", content, "encoding", "utf-8"));
    }

    public JsonNode createTree(String owner, String repo, String baseTreeSha, List<Map<String, Object>> treeItems) throws IOException, InterruptedException {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("base_tree", baseTreeSha);
        payload.put("tree", treeItems);
        return postJson("/repos/" + owner + "/" + repo + "/git/trees", payload);
    }

    public JsonNode createCommit(String owner, String repo, String message, String treeSha, List<String> parentShas) throws IOException, InterruptedException {
        return postJson("/repos/" + owner + "/" + repo + "/git/commits",
                Map.of("message", message, "tree", treeSha, "parents", parentShas));
    }

    public JsonNode updateRef(String owner, String repo, String refName, String sha) throws IOException, InterruptedException {
        return patchJson("/repos/" + owner + "/" + repo + "/git/refs/heads/" + refName,
                Map.of("sha", sha, "force", false));
    }

    // ---- Setup helpers ----

    public JsonNode getAppInstallations() throws Exception {
        String jwt = authService.generateAppJwt();
        String url = props.apiUrl() + "/app/installations";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    // ---- Private helpers ----

    private <T> List<T> getPaginated(String path, TypeReference<List<T>> typeRef) throws IOException, InterruptedException {
        List<T> all = new ArrayList<>();
        int page = 1;
        while (true) {
            String sep = path.contains("?") ? "&" : "?";
            String json = get(path + sep + "page=" + page);
            List<T> page_items = mapper.readValue(json, typeRef);
            if (page_items.isEmpty()) break;
            all.addAll(page_items);
            if (page_items.size() < PAGE_SIZE) break;
            page++;
        }
        return all;
    }

    private String get(String path) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path).GET().build();
        return send(request);
    }

    private JsonNode getJson(String path) throws IOException, InterruptedException {
        return mapper.readTree(get(path));
    }

    private JsonNode postJson(String path, Object body) throws IOException, InterruptedException {
        String bodyJson = mapper.writeValueAsString(body);
        HttpRequest request = buildRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .header("Content-Type", "application/json")
                .build();
        return mapper.readTree(send(request));
    }

    private JsonNode patchJson(String path, Object body) throws IOException, InterruptedException {
        String bodyJson = mapper.writeValueAsString(body);
        HttpRequest request = buildRequest(path)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(bodyJson))
                .header("Content-Type", "application/json")
                .build();
        return mapper.readTree(send(request));
    }

    private void delete(String path) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path).DELETE().build();
        send(request);
    }

    private HttpRequest.Builder buildRequest(String path) {
        String url = path.startsWith("http") ? path : props.apiUrl() + path;
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + authService.getInstallationToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        if (rateLimitState.remaining() <= 10) {
            log.warn("GitHub API rate limit low: {}/{} remaining, resets at {}",
                    rateLimitState.remaining(), rateLimitState.limit(), rateLimitState.resetAt());
        }

        int maxAttempts = retryProps.maxAttempts();
        long backoffMs = retryProps.initialBackoffMs();
        IOException lastIoException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpResponse<String> response = null;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                lastIoException = e;
                if (attempt < maxAttempts) {
                    log.warn("GitHub API {} {} -> IOException on attempt {}/{}, retrying in {}ms: {}",
                            request.method(), request.uri(), attempt, maxAttempts, backoffMs, e.getMessage());
                    sleep(backoffMs);
                    backoffMs = (long) (backoffMs * retryProps.backoffMultiplier());
                    continue;
                }
                throw e;
            }

            int status = response.statusCode();
            log.debug("GitHub API {} {} -> {}", request.method(), request.uri(), status);

            // Update rate limit state from response headers
            try {
                String limitHeader = response.headers().firstValue("X-RateLimit-Limit").orElse(null);
                String remainingHeader = response.headers().firstValue("X-RateLimit-Remaining").orElse(null);
                String resetHeader = response.headers().firstValue("X-RateLimit-Reset").orElse(null);
                if (limitHeader != null && remainingHeader != null && resetHeader != null) {
                    rateLimitState = new RateLimitState(
                            Integer.parseInt(limitHeader),
                            Integer.parseInt(remainingHeader),
                            Instant.ofEpochSecond(Long.parseLong(resetHeader))
                    );
                }
            } catch (NumberFormatException e) {
                log.debug("Failed to parse rate limit headers", e);
            }

            if (status == 429) {
                long retryAfterMs;
                String retryAfterHeader = response.headers().firstValue("Retry-After").orElse(null);
                if (retryAfterHeader != null) {
                    try {
                        retryAfterMs = Long.parseLong(retryAfterHeader) * 1000L;
                    } catch (NumberFormatException e) {
                        retryAfterMs = retryProps.rateLimitRetryAfterSeconds() * 1000L;
                    }
                } else {
                    retryAfterMs = retryProps.rateLimitRetryAfterSeconds() * 1000L;
                }
                if (attempt < maxAttempts) {
                    log.warn("GitHub API {} {} -> 429 rate limited on attempt {}/{}, retrying after {}ms",
                            request.method(), request.uri(), attempt, maxAttempts, retryAfterMs);
                    sleep(retryAfterMs);
                    continue;
                }
                throw new GitHubApiException("GitHub API error 429 for " +
                        request.method() + " " + request.uri() + ": " + response.body());
            }

            if (status >= 500) {
                if (attempt < maxAttempts) {
                    log.warn("GitHub API {} {} -> {} on attempt {}/{}, retrying in {}ms",
                            request.method(), request.uri(), status, attempt, maxAttempts, backoffMs);
                    sleep(backoffMs);
                    backoffMs = (long) (backoffMs * retryProps.backoffMultiplier());
                    continue;
                }
                throw new GitHubApiException("GitHub API error " + status + " for " +
                        request.method() + " " + request.uri() + ": " + response.body());
            }

            if (status >= 400) {
                throw new GitHubApiException("GitHub API error " + status + " for " +
                        request.method() + " " + request.uri() + ": " + response.body());
            }

            return response.body() != null ? response.body() : "{}";
        }

        // Should not be reachable, but satisfy the compiler
        if (lastIoException != null) throw lastIoException;
        throw new GitHubApiException("GitHub API request failed after " + maxAttempts + " attempts for " +
                request.method() + " " + request.uri());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
