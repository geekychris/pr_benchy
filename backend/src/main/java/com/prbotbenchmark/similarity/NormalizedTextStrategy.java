package com.prbotbenchmark.similarity;

import com.prbotbenchmark.collection.TextNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Normalized text similarity using Jaro-Winkler similarity.
 */
@Component
public class NormalizedTextStrategy implements SimilarityStrategy {

    private final TextNormalizer textNormalizer;
    private final double threshold;

    public NormalizedTextStrategy(
            TextNormalizer textNormalizer,
            @Value("${app.similarity.text-similarity-threshold:0.8}") double threshold) {
        this.textNormalizer = textNormalizer;
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "NORMALIZED_TEXT";
    }

    @Override
    public SimilarityResult compute(String textA, String textB, String fileA, Integer lineA, String fileB, Integer lineB) {
        String normA = textNormalizer.normalize(textA);
        String normB = textNormalizer.normalize(textB);

        if (normA.isEmpty() && normB.isEmpty()) {
            return new SimilarityResult(1.0, true);
        }
        if (normA.isEmpty() || normB.isEmpty()) {
            return new SimilarityResult(0.0, false);
        }

        double score = jaroWinkler(normA, normB);
        return new SimilarityResult(score, score >= threshold);
    }

    /**
     * Computes the Jaro-Winkler similarity between two strings.
     * Returns a value between 0.0 (no match) and 1.0 (exact match).
     */
    private double jaroWinkler(String s1, String s2) {
        double jaro = jaro(s1, s2);

        // Compute common prefix length (up to 4 characters)
        int prefixLen = 0;
        int maxPrefix = Math.min(4, Math.min(s1.length(), s2.length()));
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLen++;
            } else {
                break;
            }
        }

        // Winkler scaling factor p = 0.1
        return jaro + prefixLen * 0.1 * (1.0 - jaro);
    }

    /**
     * Computes the Jaro similarity between two strings.
     */
    private double jaro(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        int matchDistance = Math.max(len1, len2) / 2 - 1;
        if (matchDistance < 0) {
            matchDistance = 0;
        }

        boolean[] s1Matched = new boolean[len1];
        boolean[] s2Matched = new boolean[len2];

        int matches = 0;
        int transpositions = 0;

        // Find matches
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);

            for (int j = start; j < end; j++) {
                if (s2Matched[j] || s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                s1Matched[i] = true;
                s2Matched[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) {
            return 0.0;
        }

        // Count transpositions
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matched[i]) {
                continue;
            }
            while (!s2Matched[k]) {
                k++;
            }
            if (s1.charAt(i) != s2.charAt(k)) {
                transpositions++;
            }
            k++;
        }

        double m = matches;
        return (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0;
    }
}
