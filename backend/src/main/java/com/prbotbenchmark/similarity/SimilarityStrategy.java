package com.prbotbenchmark.similarity;

/**
 * Strategy interface for computing similarity between two comments.
 */
public interface SimilarityStrategy {

    String getName();

    SimilarityResult compute(String textA, String textB, String fileA, Integer lineA, String fileB, Integer lineB);

    record SimilarityResult(double score, boolean isMatch) {}
}
