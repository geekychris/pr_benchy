package com.prbotbenchmark.collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TextNormalizer();
    }

    @Test
    void normalize_nullInput_returnsEmpty() {
        assertThat(normalizer.normalize(null)).isEmpty();
    }

    @Test
    void normalize_blankInput_returnsEmpty() {
        assertThat(normalizer.normalize("   ")).isEmpty();
    }

    @Test
    void normalize_stripsFencedCodeBlock() {
        String input = "This is bad:\n```java\nint x = 1;\n```\nFix it.";
        String result = normalizer.normalize(input);
        assertThat(result).doesNotContain("```");
        assertThat(result).doesNotContain("int x = 1");
        assertThat(result).contains("this is bad");
        assertThat(result).contains("fix it");
    }

    @Test
    void normalize_stripsInlineCode() {
        String input = "The `foo()` method is wrong.";
        String result = normalizer.normalize(input);
        assertThat(result).doesNotContain("`");
        assertThat(result).contains("foo");
    }

    @Test
    void normalize_stripsBoldAndItalic() {
        String input = "This is **important** and *critical*.";
        String result = normalizer.normalize(input);
        assertThat(result).doesNotContain("**");
        assertThat(result).doesNotContain("*");
        assertThat(result).contains("important");
        assertThat(result).contains("critical");
    }

    @Test
    void normalize_stripsUrls() {
        String input = "See https://github.com/foo/bar for details.";
        String result = normalizer.normalize(input);
        assertThat(result).doesNotContain("https://");
        assertThat(result).contains("see");
        assertThat(result).contains("for details");
    }

    @Test
    void normalize_lowercasesText() {
        String input = "NullPointerException in FooBar";
        String result = normalizer.normalize(input);
        assertThat(result).isEqualTo(result.toLowerCase());
    }

    @Test
    void normalize_collapsesWhitespace() {
        String input = "foo   bar\t\nbaz";
        String result = normalizer.normalize(input);
        assertThat(result).doesNotContain("  ");
        assertThat(result).doesNotContain("\t");
        assertThat(result).doesNotContain("\n");
    }

    @Test
    void normalize_trims() {
        String input = "  some text  ";
        assertThat(normalizer.normalize(input)).isEqualTo("some text");
    }

    @ParameterizedTest
    @CsvSource({
        "'Possible null dereference here', 'possible null dereference here'",
        "'Use `Objects.requireNonNull()`', 'use objects requirenonnull'",
    })
    void normalize_examples(String input, String expected) {
        assertThat(normalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void normalize_sameCommentProducesSameResult() {
        String comment = "This **variable** might be `null`. See https://example.com.";
        assertThat(normalizer.normalize(comment)).isEqualTo(normalizer.normalize(comment));
    }
}
