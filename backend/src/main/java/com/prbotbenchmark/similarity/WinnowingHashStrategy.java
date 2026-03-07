package com.prbotbenchmark.similarity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Winnowing fingerprinting for near-duplicate detection.
 * Based on: "Winnowing: Local Algorithms for Document Fingerprinting" (Schleimer et al.)
 */
@Component
public class WinnowingHashStrategy implements SimilarityStrategy {

    private static final long BASE = 31L;
    private static final long MOD = 1_000_000_007L;

    private final int k;
    private final int w;
    private final double threshold;

    public WinnowingHashStrategy(
            @Value("${app.similarity.winnowing-k:5}") int k,
            @Value("${app.similarity.winnowing-w:4}") int w,
            @Value("${app.similarity.text-similarity-threshold:0.8}") double threshold) {
        this.k = k;
        this.w = w;
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "WINNOWING";
    }

    @Override
    public SimilarityResult compute(String textA, String textB, String fileA, Integer lineA, String fileB, Integer lineB) {
        Set<Long> fpA = fingerprint(textA);
        Set<Long> fpB = fingerprint(textB);

        if (fpA.isEmpty() && fpB.isEmpty()) {
            return new SimilarityResult(0.0, false);
        }
        if (fpA.isEmpty() || fpB.isEmpty()) {
            return new SimilarityResult(0.0, false);
        }

        // Jaccard similarity: |A ∩ B| / |A ∪ B|
        Set<Long> intersection = new HashSet<>(fpA);
        intersection.retainAll(fpB);

        Set<Long> union = new HashSet<>(fpA);
        union.addAll(fpB);

        double score = (double) intersection.size() / union.size();
        return new SimilarityResult(score, score >= threshold);
    }

    /**
     * Computes the winnowing fingerprint as a set of selected hashes.
     */
    public Set<Long> fingerprint(String text) {
        if (text == null || text.isBlank()) {
            return new HashSet<>();
        }

        // Step 1: Normalize - lowercase, remove non-alphanumeric except spaces, collapse spaces
        String normalized = text.toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();

        // Remove spaces to form the character stream for k-gram generation
        String charStream = normalized.replace(" ", "");

        if (charStream.length() < k) {
            // Text too short for k-grams; return empty fingerprint
            return new HashSet<>();
        }

        // Step 2: Generate k-grams and hash them
        int numKgrams = charStream.length() - k + 1;
        long[] hashes = new long[numKgrams];
        for (int i = 0; i < numKgrams; i++) {
            hashes[i] = rollingHash(charStream.substring(i, i + k));
        }

        // Step 3: Apply winnowing - sliding window of size w, select minimum hash in each window
        Set<Long> selected = new HashSet<>();
        if (hashes.length < w) {
            // Window larger than hash array - just take the minimum of all hashes
            long min = hashes[0];
            for (long h : hashes) {
                if (h < min) min = h;
            }
            selected.add(min);
            return selected;
        }

        long prevMin = Long.MAX_VALUE;
        int prevMinIdx = -1;

        for (int i = 0; i <= hashes.length - w; i++) {
            // Find minimum in window [i, i+w)
            long windowMin = Long.MAX_VALUE;
            int windowMinIdx = -1;
            for (int j = i; j < i + w; j++) {
                if (hashes[j] < windowMin) {
                    windowMin = hashes[j];
                    windowMinIdx = j;
                }
            }
            // Select if the minimum changed or we moved to a new position
            if (windowMinIdx != prevMinIdx) {
                selected.add(windowMin);
                prevMin = windowMin;
                prevMinIdx = windowMinIdx;
            }
        }

        return selected;
    }

    /**
     * Computes a hex fingerprint string (XOR of all selected hashes) suitable for DB storage.
     */
    public String computeFingerprint(String text) {
        Set<Long> hashes = fingerprint(text);
        if (hashes.isEmpty()) {
            return "0";
        }
        long xor = 0L;
        for (long h : hashes) {
            xor ^= h;
        }
        return Long.toHexString(xor);
    }

    /**
     * Polynomial rolling hash for a string.
     * h = sum(char[i] * BASE^i) mod MOD
     */
    private long rollingHash(String s) {
        long hash = 0L;
        long power = 1L;
        for (int i = 0; i < s.length(); i++) {
            hash = (hash + (s.charAt(i) * power) % MOD) % MOD;
            power = (power * BASE) % MOD;
        }
        return hash;
    }
}
