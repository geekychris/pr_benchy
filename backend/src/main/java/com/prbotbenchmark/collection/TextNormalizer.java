package com.prbotbenchmark.collection;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes comment text for similarity comparison.
 */
@Component
public class TextNormalizer {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```", Pattern.MULTILINE);
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]*)`");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]*)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*([^*]*)\\*");
    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("_([^_]*)_");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern FIRST_CODE_BLOCK_PATTERN = Pattern.compile("```(?:[a-zA-Z0-9]*)\\r?\\n?([\\s\\S]*?)```");

    /**
     * Normalizes comment text for similarity comparison.
     * Returns empty string for null/blank input.
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text;

        // Strip markdown code blocks
        result = CODE_BLOCK_PATTERN.matcher(result).replaceAll(" ");

        // Strip inline code markers, keep content
        result = INLINE_CODE_PATTERN.matcher(result).replaceAll("$1");

        // Strip bold markers, keep content
        result = BOLD_PATTERN.matcher(result).replaceAll("$1");

        // Strip italic markers, keep content
        result = ITALIC_PATTERN.matcher(result).replaceAll("$1");

        // Strip underline markers, keep content
        result = UNDERLINE_PATTERN.matcher(result).replaceAll("$1");

        // Remove URLs
        result = URL_PATTERN.matcher(result).replaceAll(" ");

        // Lowercase
        result = result.toLowerCase();

        // Remove punctuation except spaces
        result = PUNCTUATION_PATTERN.matcher(result).replaceAll(" ");

        // Collapse multiple whitespace to single space
        result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ");

        return result.trim();
    }

    /**
     * Extracts the content from the first code block (``` ... ```) if present.
     * Returns empty string if no code block is found.
     */
    public String extractCodeSnippet(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        Matcher matcher = FIRST_CODE_BLOCK_PATTERN.matcher(body);
        if (matcher.find()) {
            String content = matcher.group(1);
            return content != null ? content.trim() : "";
        }
        return "";
    }
}
