package com.prbotbenchmark.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExactMatchStrategyTest {

    private ExactMatchStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ExactMatchStrategy();
    }

    @Test
    void getName_returnsExact() {
        assertThat(strategy.getName()).isEqualTo("EXACT");
    }

    @Test
    void compute_identicalTexts_scoreOneAndMatch() {
        var result = strategy.compute("foo bar", "foo bar", null, null, null, null);
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    void compute_differentTexts_scoreZeroNoMatch() {
        var result = strategy.compute("foo", "bar", null, null, null, null);
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    void compute_nullBoth_scoreOneAndMatch() {
        var result = strategy.compute(null, null, null, null, null, null);
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    void compute_oneNull_scoreZeroNoMatch() {
        var result = strategy.compute("foo", null, null, null, null, null);
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    void compute_caseDifference_scoreZeroNoMatch() {
        // Exact match is case-sensitive (normalization is handled separately)
        var result = strategy.compute("Foo", "foo", null, null, null, null);
        assertThat(result.score()).isEqualTo(0.0);
    }
}
