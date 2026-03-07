package com.prbotbenchmark.similarity;

import org.springframework.stereotype.Component;

/**
 * Match based on same file path and line number.
 */
@Component
public class FileLineStrategy implements SimilarityStrategy {

    @Override
    public String getName() {
        return "FILE_LINE";
    }

    @Override
    public SimilarityResult compute(String textA, String textB, String fileA, Integer lineA, String fileB, Integer lineB) {
        boolean sameFile = fileA != null && fileA.equals(fileB);
        boolean sameLine = lineA != null && lineA.equals(lineB);

        if (sameFile && sameLine) {
            return new SimilarityResult(1.0, true);
        } else if (sameFile) {
            return new SimilarityResult(0.5, false);
        } else {
            return new SimilarityResult(0.0, false);
        }
    }
}
