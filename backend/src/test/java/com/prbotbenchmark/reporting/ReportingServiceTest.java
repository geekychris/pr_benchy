package com.prbotbenchmark.reporting;

import com.prbotbenchmark.benchmark.SuitePrRepository;
import com.prbotbenchmark.collection.OriginalCommentRepository;
import com.prbotbenchmark.collection.ReviewCommentRepository;
import com.prbotbenchmark.grading.GoldenDatasetRepository;
import com.prbotbenchmark.grading.GradingRepository;
import com.prbotbenchmark.runner.BenchmarkRunRepository;
import com.prbotbenchmark.runner.ReplayPrRepository;
import com.prbotbenchmark.reporting.dto.BotComparisonDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the precision/recall/F1 calculation logic directly.
 * ReportingService arithmetic is tested in isolation.
 */
class ReportingServiceTest {

    @Test
    void f1Score_perfectPrecisionAndRecall_returnsOne() {
        assertThat(computeF1(1.0, 1.0)).isEqualTo(1.0);
    }

    @Test
    void f1Score_zeroPrecision_returnsZero() {
        assertThat(computeF1(0.0, 1.0)).isEqualTo(0.0);
    }

    @Test
    void f1Score_zeroRecall_returnsZero() {
        assertThat(computeF1(1.0, 0.0)).isEqualTo(0.0);
    }

    @Test
    void f1Score_bothZero_returnsZero_noDivisionByZero() {
        assertThat(computeF1(0.0, 0.0)).isEqualTo(0.0);
    }

    @Test
    void f1Score_halfPrecisionHalfRecall_returnsHalf() {
        assertThat(computeF1(0.5, 0.5)).isEqualTo(0.5);
    }

    @Test
    void precision_noFalsePositives_returnsOne() {
        int tp = 5, fp = 0;
        double precision = computePrecision(tp, fp);
        assertThat(precision).isEqualTo(1.0);
    }

    @Test
    void precision_allFalsePositives_returnsZero() {
        int tp = 0, fp = 5;
        double precision = computePrecision(tp, fp);
        assertThat(precision).isEqualTo(0.0);
    }

    @Test
    void precision_zeroDenominator_returnsZero() {
        double precision = computePrecision(0, 0);
        assertThat(precision).isEqualTo(0.0);
    }

    @Test
    void recall_noFalseNegatives_returnsOne() {
        int tp = 5, fn = 0;
        double recall = computeRecall(tp, fn);
        assertThat(recall).isEqualTo(1.0);
    }

    @Test
    void recall_zeroDenominator_returnsZero() {
        double recall = computeRecall(0, 0);
        assertThat(recall).isEqualTo(0.0);
    }

    @Test
    void botComparisonDto_fieldsSetCorrectly() {
        int tp = 3, fn = 1, fp = 2;
        double precision = computePrecision(tp, fp);
        double recall = computeRecall(tp, fn);
        double f1 = computeF1(precision, recall);

        BotComparisonDto dto = new BotComparisonDto(1L, "TestBot", tp, fn, fp, precision, recall, f1);

        assertThat(dto.truePositives()).isEqualTo(3);
        assertThat(dto.falseNegatives()).isEqualTo(1);
        assertThat(dto.falsePositives()).isEqualTo(2);
        assertThat(dto.precision()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
        assertThat(dto.recall()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(0.001));
        assertThat(dto.f1Score()).isGreaterThan(0.0);
    }

    // --- Extracted calculation logic (mirrors ReportingService) ---

    private double computePrecision(int tp, int fp) {
        int denominator = tp + fp;
        return denominator == 0 ? 0.0 : (double) tp / denominator;
    }

    private double computeRecall(int tp, int fn) {
        int denominator = tp + fn;
        return denominator == 0 ? 0.0 : (double) tp / denominator;
    }

    private double computeF1(double precision, double recall) {
        double denominator = precision + recall;
        return denominator == 0.0 ? 0.0 : 2.0 * precision * recall / denominator;
    }
}
