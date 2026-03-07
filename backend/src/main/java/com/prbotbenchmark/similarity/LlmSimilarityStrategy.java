package com.prbotbenchmark.similarity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses a local LLM (Ollama-compatible) to judge comment similarity.
 */
@Component
@ConditionalOnProperty(name = "app.llm.enabled", havingValue = "true")
@Slf4j
public class LlmSimilarityStrategy implements SimilarityStrategy {

    private static final Pattern SCORE_PATTERN = Pattern.compile("\"score\"\\s*:\\s*([0-9]*\\.?[0-9]+)");

    private final String baseUrl;
    private final String model;
    private final double threshold;
    private final HttpClient httpClient;

    public LlmSimilarityStrategy(
            @Value("${app.llm.base-url}") String baseUrl,
            @Value("${app.llm.model}") String model,
            @Value("${app.llm.similarity-threshold:0.85}") double threshold) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.threshold = threshold;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String getName() {
        return "LLM";
    }

    @Override
    public SimilarityResult compute(String textA, String textB, String fileA, Integer lineA, String fileB, Integer lineB) {
        if (textA == null || textB == null || textA.isBlank() || textB.isBlank()) {
            return new SimilarityResult(0.0, false);
        }

        try {
            String prompt = buildPrompt(textA, textB);
            String requestBody = buildRequestBody(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.warn("LLM API returned status {} for similarity request", response.statusCode());
                return new SimilarityResult(0.0, false);
            }

            double score = parseScore(response.body());
            return new SimilarityResult(score, score >= threshold);

        } catch (Exception e) {
            log.warn("LLM similarity computation failed: {}", e.getMessage());
            return new SimilarityResult(0.0, false);
        }
    }

    private String buildPrompt(String textA, String textB) {
        return "Are these two code review comments addressing the same issue? " +
               "Reply with a JSON object: {\"score\": 0.0-1.0, \"reasoning\": \"...\"}\n\n" +
               "Comment A: " + textA + "\n\n" +
               "Comment B: " + textB;
    }

    private String buildRequestBody(String prompt) {
        // Escape the prompt for JSON embedding
        String escaped = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "{\"model\": \"" + model + "\", \"prompt\": \"" + escaped + "\", \"stream\": false}";
    }

    private double parseScore(String responseBody) {
        // The Ollama /api/generate response has a "response" field with the generated text
        // Extract it, then look for the score
        Pattern responseFieldPattern = Pattern.compile("\"response\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher responseFieldMatcher = responseFieldPattern.matcher(responseBody);

        String generatedText = responseBody;
        if (responseFieldMatcher.find()) {
            // Unescape basic JSON escapes
            generatedText = responseFieldMatcher.group(1)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        Matcher scoreMatcher = SCORE_PATTERN.matcher(generatedText);
        if (scoreMatcher.find()) {
            try {
                double score = Double.parseDouble(scoreMatcher.group(1));
                // Clamp to [0.0, 1.0]
                return Math.max(0.0, Math.min(1.0, score));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse LLM score from response: {}", generatedText);
            }
        } else {
            log.warn("No score found in LLM response: {}", generatedText);
        }
        return 0.0;
    }
}
