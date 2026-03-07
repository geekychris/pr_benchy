package com.prbotbenchmark.similarity;

import org.springframework.stereotype.Component;

/**
 * Exact string match after normalization.
 */
@Component
public class ExactMatchStrategy implements SimilarityStrategy {

    @Override
    public String getName() {
        return "EXACT";
    }

    @Override
    public SimilarityResult compute(String textA, String textB, String fileA, Integer lineA, String fileB, Integer lineB) {
        if (textA == null && textB == null) {
            return new SimilarityResult(1.0, true);
        }
        if (textA == null || textB == null) {
            return new SimilarityResult(0.0, false);
        }
        boolean equal = textA.equals(textB);
        return new SimilarityResult(equal ? 1.0 : 0.0, equal);
    }
}
