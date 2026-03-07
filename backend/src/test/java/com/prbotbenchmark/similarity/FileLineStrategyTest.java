package com.prbotbenchmark.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileLineStrategyTest {

    private FileLineStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FileLineStrategy();
    }

    @Test
    void getName_returnsFileLine() {
        assertThat(strategy.getName()).isEqualTo("FILE_LINE");
    }

    @Test
    void compute_sameFileAndLine_scoreOneAndMatch() {
        var result = strategy.compute("text", "text", "src/Foo.java", 42, "src/Foo.java", 42);
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    void compute_sameFileOnlyNoLine_scoreHalfNoMatch() {
        var result = strategy.compute("a", "b", "src/Foo.java", null, "src/Foo.java", null);
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    void compute_sameFileDifferentLine_scoreHalfNoMatch() {
        var result = strategy.compute("a", "b", "src/Foo.java", 10, "src/Foo.java", 20);
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    void compute_differentFile_scoreZeroNoMatch() {
        var result = strategy.compute("a", "b", "src/Foo.java", 10, "src/Bar.java", 10);
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    void compute_bothFilesNull_scoreZeroNoMatch() {
        var result = strategy.compute("a", "b", null, null, null, null);
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    void compute_oneFileNull_scoreZeroNoMatch() {
        var result = strategy.compute("a", "b", "src/Foo.java", 10, null, 10);
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.isMatch()).isFalse();
    }
}
