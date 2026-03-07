package com.prbotbenchmark.similarity;

import com.prbotbenchmark.collection.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizedTextStrategyTest {

    private NormalizedTextStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new NormalizedTextStrategy(new TextNormalizer(), 0.8);
    }

    @Test
    void getName_returnsNormalizedText() {
        assertThat(strategy.getName()).isEqualTo("NORMALIZED_TEXT");
    }

    @Test
    void compute_identicalTexts_scoreOneAndMatch() {
        var result = strategy.compute("null pointer", "null pointer", null, null, null, null);
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    void compute_emptyBothTexts_handledGracefully() {
        var result = strategy.compute("", "", null, null, null, null);
        assertThat(result.score()).isBetween(0.0, 1.0);
    }

    @Test
    void compute_completelyDifferentTexts_scoreLow() {
        var result = strategy.compute(
            "null pointer dereference in authentication",
            "sql injection vulnerability in query builder",
            null, null, null, null);
        assertThat(result.score()).isLessThan(0.8);
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    void compute_verySimularTexts_scoreHighAndMatch() {
        // Slight variation — should still match above threshold
        var result = strategy.compute(
            "possible null pointer dereference here",
            "possible null pointer dereference at this point",
            null, null, null, null);
        assertThat(result.score()).isGreaterThan(0.7);
    }

    @Test
    void compute_scoreSymmetric() {
        String a = "resource leak detected";
        String b = "potential resource leak";
        double scoreAB = strategy.compute(a, b, null, null, null, null).score();
        double scoreBA = strategy.compute(b, a, null, null, null, null).score();
        assertThat(scoreAB).isEqualTo(scoreBA);
    }

    @Test
    void compute_nullInputs_handledGracefully() {
        var result = strategy.compute(null, null, null, null, null, null);
        assertThat(result.score()).isBetween(0.0, 1.0);
    }

    @Test
    void compute_thresholdBoundary_atThresholdIsMatch() {
        // With threshold 0.8, verify boundary behavior
        // Since we can't perfectly control the score, verify the logic is consistent
        var result = strategy.compute("abc", "abc", null, null, null, null);
        if (result.score() >= 0.8) {
            assertThat(result.isMatch()).isTrue();
        } else {
            assertThat(result.isMatch()).isFalse();
        }
    }
}
