package com.prbotbenchmark.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WinnowingHashStrategyTest {

    private WinnowingHashStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new WinnowingHashStrategy(5, 4, 0.8);
    }

    @Test
    void getName_returnsWinnowing() {
        assertThat(strategy.getName()).isEqualTo("WINNOWING");
    }

    @Test
    void fingerprint_emptyText_returnsEmptySet() {
        Set<Long> result = strategy.fingerprint("");
        assertThat(result).isEmpty();
    }

    @Test
    void fingerprint_nullText_returnsEmptySet() {
        Set<Long> result = strategy.fingerprint(null);
        assertThat(result).isEmpty();
    }

    @Test
    void fingerprint_shortText_doesNotThrow() {
        // Text shorter than k should not throw
        assertThat(strategy.fingerprint("hi")).isEmpty();
    }

    @Test
    void fingerprint_identicalTexts_produceSameFingerprint() {
        String text = "possible null pointer dereference in this method";
        Set<Long> fp1 = strategy.fingerprint(text);
        Set<Long> fp2 = strategy.fingerprint(text);
        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void fingerprint_deterministicAcrossMultipleCalls() {
        String text = "this variable is never initialized before use";
        for (int i = 0; i < 5; i++) {
            assertThat(strategy.fingerprint(text)).isEqualTo(strategy.fingerprint(text));
        }
    }

    @Test
    void compute_identicalTexts_scoreIsOne() {
        String text = "null pointer dereference possible here";
        SimilarityStrategy.SimilarityResult result = strategy.compute(text, text, null, null, null, null);
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    void compute_completelyDifferentTexts_scoreLow() {
        String a = "null pointer dereference in authentication module";
        String b = "missing semicolon after return statement";
        SimilarityStrategy.SimilarityResult result = strategy.compute(a, b, null, null, null, null);
        assertThat(result.score()).isLessThan(0.5);
    }

    @Test
    void compute_nearDuplicateTexts_scoreHigh() {
        String a = "possible null pointer dereference at line 42 in UserService";
        String b = "possible null pointer dereference at line 43 in UserService";
        SimilarityStrategy.SimilarityResult result = strategy.compute(a, b, null, null, null, null);
        // Minor line number difference should still result in high similarity
        assertThat(result.score()).isGreaterThan(0.5);
    }

    @Test
    void compute_emptyStrings_returnsZeroScore() {
        SimilarityStrategy.SimilarityResult result = strategy.compute("", "", null, null, null, null);
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    void computeFingerprint_nonEmpty_returnsHexString() {
        String fp = strategy.computeFingerprint("some meaningful code review comment about null safety");
        assertThat(fp).isNotBlank();
        assertThat(fp).matches("[0-9a-f]+");
    }

    @Test
    void computeFingerprint_emptyText_returnsEmptyOrZero() {
        String fp = strategy.computeFingerprint("");
        assertThat(fp).isNotNull();
    }

    @Test
    void jaccardSimilarity_correctForKnownOverlap() {
        // If A and B have the same text, Jaccard = 1.0
        String text = "resource leak detected in finally block consider closing streams";
        Set<Long> fpA = strategy.fingerprint(text);
        Set<Long> fpB = strategy.fingerprint(text);

        long intersection = fpA.stream().filter(fpB::contains).count();
        long union = fpA.size() + fpB.size() - intersection;
        double expected = union == 0 ? 0.0 : (double) intersection / union;

        assertThat(expected).isEqualTo(1.0);
    }
}
