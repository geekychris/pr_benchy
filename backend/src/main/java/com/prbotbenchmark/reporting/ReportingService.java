package com.prbotbenchmark.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prbotbenchmark.benchmark.SuitePrRepository;
import com.prbotbenchmark.bot.domain.Bot;
import com.prbotbenchmark.collection.ReviewCommentRepository;
import com.prbotbenchmark.collection.domain.CommentType;
import com.prbotbenchmark.collection.domain.ReviewComment;
import com.prbotbenchmark.grading.GoldenDatasetRepository;
import com.prbotbenchmark.grading.GradingRepository;
import com.prbotbenchmark.grading.domain.GoldenDatasetEntry;
import com.prbotbenchmark.grading.domain.Grading;
import com.prbotbenchmark.reporting.dto.BotComparisonDto;
import com.prbotbenchmark.reporting.dto.BotStatsDto;
import com.prbotbenchmark.reporting.dto.BotTrendDto;
import com.prbotbenchmark.reporting.dto.ComparisonReportDto;
import com.prbotbenchmark.reporting.dto.RunReportDto;
import com.prbotbenchmark.reporting.dto.SignificanceTestDto;
import com.prbotbenchmark.reporting.dto.TrendDataPointDto;
import com.prbotbenchmark.runner.domain.RunStatus;
import com.prbotbenchmark.runner.BenchmarkRunRepository;
import com.prbotbenchmark.runner.ReplayPrRepository;
import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.ReplayPr;
import com.prbotbenchmark.runner.domain.ReplayStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class ReportingService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final ReplayPrRepository replayPrRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final GradingRepository gradingRepository;
    private final GoldenDatasetRepository goldenDatasetRepository;
    private final SuitePrRepository suitePrRepository;
    private final ObjectMapper objectMapper;

    public RunReportDto generateRunReport(Long runId) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found with id: " + runId));

        List<ReplayPr> replayPrs = replayPrRepository.findByRunId(runId);
        int totalReplayPrs = replayPrs.size();
        int completedReplayPrs = (int) replayPrs.stream()
                .filter(rp -> rp.getStatus() == ReplayStatus.COMPLETED)
                .count();
        int failedReplayPrs = (int) replayPrs.stream()
                .filter(rp -> rp.getStatus() == ReplayStatus.FAILED)
                .count();

        // Load all review comments for this run
        List<ReviewComment> allComments = reviewCommentRepository.findByReplayPrRunId(runId);

        // Load all gradings for these comments (keyed by commentId)
        // We use REVIEW type since these are ReviewComments
        Map<Long, List<Grading>> gradingsByCommentId = allComments.stream()
                .collect(Collectors.groupingBy(
                        ReviewComment::getId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                comments -> gradingRepository.findByCommentIdAndCommentTableType(
                                        comments.get(0).getId(), "REVIEW")
                        )
                ));

        // Actually, rebuild gradingsByCommentId properly
        List<Grading> allGradings = gradingRepository.findByGraderType("MACHINE");
        // Load all gradings for review comments in this run
        Map<Long, List<Grading>> reviewGradings = allComments.stream()
                .collect(Collectors.toMap(
                        ReviewComment::getId,
                        c -> gradingRepository.findByCommentIdAndCommentTableType(c.getId(), "REVIEW")
                ));

        List<Bot> bots = run.getBots();
        List<BotStatsDto> botStats = new ArrayList<>();

        for (Bot bot : bots) {
            List<ReviewComment> botComments = allComments.stream()
                    .filter(c -> c.getBot() != null && c.getBot().getId().equals(bot.getId()))
                    .toList();

            int totalComments = botComments.size();
            int reviewCommentCount = (int) botComments.stream()
                    .filter(c -> c.getCommentType() == CommentType.REVIEW_COMMENT)
                    .count();
            int prCommentCount = (int) botComments.stream()
                    .filter(c -> c.getCommentType() == CommentType.PR_COMMENT)
                    .count();
            int reviewCount = (int) botComments.stream()
                    .filter(c -> c.getCommentType() == CommentType.REVIEW)
                    .count();

            int validCount = 0;
            int invalidCount = 0;
            int duplicateCount = 0;
            int needsReviewCount = 0;
            int ungradedCount = 0;

            for (ReviewComment comment : botComments) {
                List<Grading> gradings = reviewGradings.getOrDefault(comment.getId(), List.of());
                if (gradings.isEmpty()) {
                    ungradedCount++;
                } else {
                    // Use the most recent grading
                    Grading latest = gradings.get(gradings.size() - 1);
                    switch (latest.getVerdict() != null ? latest.getVerdict() : "") {
                        case "VALID" -> validCount++;
                        case "INVALID" -> invalidCount++;
                        case "DUPLICATE" -> duplicateCount++;
                        case "NEEDS_REVIEW" -> needsReviewCount++;
                        default -> ungradedCount++;
                    }
                }
            }

            botStats.add(new BotStatsDto(
                    bot.getId(),
                    bot.getName(),
                    totalComments,
                    reviewCommentCount,
                    prCommentCount,
                    reviewCount,
                    validCount,
                    invalidCount,
                    duplicateCount,
                    needsReviewCount,
                    ungradedCount
            ));
        }

        return new RunReportDto(
                run.getId(),
                run.getName(),
                run.getStatus() != null ? run.getStatus().name() : null,
                run.getStartedAt(),
                run.getCompletedAt(),
                totalReplayPrs,
                completedReplayPrs,
                failedReplayPrs,
                botStats
        );
    }

    public ComparisonReportDto compareWithGoldenDataset(Long runId) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found with id: " + runId));

        Long suiteId = run.getSuite().getId();

        // Get all active golden entries for this suite
        List<GoldenDatasetEntry> goldenEntries = goldenDatasetRepository.findBySuitePrSuiteId(suiteId)
                .stream()
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .toList();

        int goldenEntryCount = goldenEntries.size();

        // Load all review comments for this run
        List<ReviewComment> allComments = reviewCommentRepository.findByReplayPrRunId(runId);

        List<Bot> bots = run.getBots();
        List<BotComparisonDto> botComparisons = new ArrayList<>();

        for (Bot bot : bots) {
            List<ReviewComment> botComments = allComments.stream()
                    .filter(c -> c.getBot() != null && c.getBot().getId().equals(bot.getId()))
                    .toList();

            int truePositives = 0;
            int falseNegatives = 0;
            int falsePositives = 0;

            // Track which golden entries were matched
            boolean[] matched = new boolean[goldenEntries.size()];

            // For each bot comment, check if it matches any golden entry
            List<ReviewComment> unmatchedComments = new ArrayList<>();
            for (ReviewComment comment : botComments) {
                boolean foundMatch = false;
                for (int i = 0; i < goldenEntries.size(); i++) {
                    GoldenDatasetEntry golden = goldenEntries.get(i);
                    if (commentMatchesGoldenEntry(comment, golden)) {
                        matched[i] = true;
                        foundMatch = true;
                        break;
                    }
                }
                if (!foundMatch) {
                    unmatchedComments.add(comment);
                }
            }

            for (int i = 0; i < goldenEntries.size(); i++) {
                if (matched[i]) {
                    truePositives++;
                } else {
                    falseNegatives++;
                }
            }
            falsePositives = unmatchedComments.size();

            double precision = (truePositives + falsePositives) > 0
                    ? (double) truePositives / (truePositives + falsePositives)
                    : 0.0;
            double recall = (truePositives + falseNegatives) > 0
                    ? (double) truePositives / (truePositives + falseNegatives)
                    : 0.0;
            double f1Score = (precision + recall) > 0
                    ? 2.0 * precision * recall / (precision + recall)
                    : 0.0;

            botComparisons.add(new BotComparisonDto(
                    bot.getId(),
                    bot.getName(),
                    truePositives,
                    falseNegatives,
                    falsePositives,
                    precision,
                    recall,
                    f1Score
            ));
        }

        return new ComparisonReportDto(runId, goldenEntryCount, botComparisons);
    }

    /**
     * A review comment matches a golden entry if:
     * - same filePath AND lineNumber (both non-null), OR
     * - the comment body contains the golden entry's canonicalBody as a substring (after normalization)
     */
    private boolean commentMatchesGoldenEntry(ReviewComment comment, GoldenDatasetEntry golden) {
        // File + line match
        if (comment.getFilePath() != null && golden.getFilePath() != null
                && comment.getFilePath().equals(golden.getFilePath())
                && comment.getLineNumber() != null && golden.getLineNumber() != null
                && comment.getLineNumber().equals(golden.getLineNumber())) {
            return true;
        }

        // Normalized body substring match
        if (golden.getCanonicalBody() != null && !golden.getCanonicalBody().isBlank()) {
            String normalizedCanonical = normalize(golden.getCanonicalBody());
            String normalizedCommentBody = comment.getBodyNormalized() != null
                    ? comment.getBodyNormalized()
                    : normalize(comment.getBody());
            if (normalizedCommentBody != null && normalizedCommentBody.contains(normalizedCanonical)) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String text) {
        if (text == null) return null;
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    @Transactional(readOnly = true)
    public BotTrendDto getBotTrend(Long botId, Long suiteId, int limit) {
        // Fetch all COMPLETED runs for the suite, ordered by startedAt DESC, then limit
        List<BenchmarkRun> completedRuns = benchmarkRunRepository.findBySuiteId(suiteId)
                .stream()
                .filter(r -> r.getStatus() == RunStatus.COMPLETED)
                .filter(r -> r.getBots() != null && r.getBots().stream().anyMatch(b -> b.getId().equals(botId)))
                .sorted((a, b) -> {
                    if (a.getStartedAt() == null && b.getStartedAt() == null) return 0;
                    if (a.getStartedAt() == null) return 1;
                    if (b.getStartedAt() == null) return -1;
                    return b.getStartedAt().compareTo(a.getStartedAt()); // DESC
                })
                .limit(limit)
                .collect(Collectors.toList());

        // Reverse to chronological order for the trend
        Collections.reverse(completedRuns);

        // Determine bot name from the first run that has the bot
        String botName = completedRuns.stream()
                .flatMap(r -> r.getBots().stream())
                .filter(b -> b.getId().equals(botId))
                .map(b -> b.getName())
                .findFirst()
                .orElse("Unknown");

        List<TrendDataPointDto> dataPoints = new ArrayList<>();
        for (BenchmarkRun run : completedRuns) {
            ComparisonReportDto comparison = compareWithGoldenDataset(run.getId());
            comparison.botComparisons().stream()
                    .filter(bc -> bc.botId().equals(botId))
                    .findFirst()
                    .ifPresent(bc -> {
                        int totalComments = bc.truePositives() + bc.falsePositives() + bc.falseNegatives();
                        dataPoints.add(new TrendDataPointDto(
                                run.getId(),
                                run.getName(),
                                run.getStartedAt(),
                                bc.precision(),
                                bc.recall(),
                                bc.f1Score(),
                                totalComments,
                                bc.truePositives(),
                                bc.falsePositives(),
                                bc.falseNegatives()
                        ));
                    });
        }

        return new BotTrendDto(botId, botName, dataPoints);
    }

    @Transactional(readOnly = true)
    public SignificanceTestDto testSignificance(Long runAId, Long runBId) {
        BenchmarkRun runA = benchmarkRunRepository.findById(runAId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found with id: " + runAId));
        BenchmarkRun runB = benchmarkRunRepository.findById(runBId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found with id: " + runBId));

        ComparisonReportDto reportA = compareWithGoldenDataset(runAId);
        ComparisonReportDto reportB = compareWithGoldenDataset(runBId);

        // Aggregate per-comment binary outcomes across all bots:
        // correct = TP counts as one correct prediction per entry,
        // incorrect = FP + FN each count as an incorrect prediction.
        // For McNemar's, we compare: correct in A but not B (b), correct in B but not A (c).
        // We model this at the golden-entry level across all bots combined:
        //   totalTP_A = sum of TPs for all bots in run A
        //   totalTP_B = sum of TPs for all bots in run B
        //   total golden = goldenEntryCount (same suite assumed)
        // b = entries correct in A but not B  (approximated as max(0, TP_A - TP_B) shared part)
        // Since we cannot align individual golden entries across runs without more complex joins,
        // we use the aggregate TP/FN/FP counts to compute the contingency table:
        //   Both correct    = min(TP_A, TP_B)  (conservative lower bound)
        //   A correct only  = TP_A - min(TP_A, TP_B)
        //   B correct only  = TP_B - min(TP_A, TP_B)
        //   Neither correct = total - TP_A - TP_B + min(TP_A, TP_B)
        int totalTPa = reportA.botComparisons().stream().mapToInt(BotComparisonDto::truePositives).sum();
        int totalTPb = reportB.botComparisons().stream().mapToInt(BotComparisonDto::truePositives).sum();
        double runAF1 = reportA.botComparisons().stream()
                .mapToDouble(BotComparisonDto::f1Score).average().orElse(0.0);
        double runBF1 = reportB.botComparisons().stream()
                .mapToDouble(BotComparisonDto::f1Score).average().orElse(0.0);

        int minTP = Math.min(totalTPa, totalTPb);
        int b = totalTPa - minTP; // correct in A but not B
        int c = totalTPb - minTP; // correct in B but not A

        if (b + c == 0) {
            return new SignificanceTestDto(
                    runAId, runA.getName(),
                    runBId, runB.getName(),
                    runAF1, runBF1,
                    1.0, false,
                    "Insufficient discordant pairs for McNemar's test"
            );
        }

        double chiSquared = (double) (b - c) * (b - c) / (double) (b + c);
        double pValue = chiSquaredPValue1df(chiSquared);

        log.debug("McNemar's test: runA={} runB={} b={} c={} chiSquared={} pValue={}",
                runAId, runBId, b, c, chiSquared, pValue);

        boolean significant = pValue < 0.05;
        String interpretation;
        if (significant) {
            String better = runBF1 >= runAF1 ? "Run B" : "Run A";
            interpretation = String.format(
                    "%s shows statistically significant improvement (p=%.3f < 0.05)", better, pValue);
        } else {
            interpretation = String.format(
                    "No statistically significant difference detected (p=%.3f)", pValue);
        }

        return new SignificanceTestDto(
                runAId, runA.getName(),
                runBId, runB.getName(),
                runAF1, runBF1,
                pValue, significant, interpretation
        );
    }

    /**
     * Approximates the p-value for a chi-squared statistic with 1 degree of freedom.
     * Uses the relationship: p = erfc(sqrt(chiSquared / 2) / sqrt(2))
     * erfc is approximated using the Abramowitz & Stegun formula (eq. 7.1.26).
     */
    private double chiSquaredPValue1df(double chiSquared) {
        if (chiSquared <= 0.0) return 1.0;
        double x = Math.sqrt(chiSquared / 2.0);
        return erfc(x / Math.sqrt(2.0));
    }

    /**
     * Complementary error function approximation (Abramowitz & Stegun 7.1.26).
     * Maximum error: |epsilon(x)| <= 1.5e-7
     */
    private double erfc(double x) {
        if (x < 0) return 2.0 - erfc(-x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double poly = t * (0.254829592
                + t * (-0.284496736
                + t * (1.421413741
                + t * (-1.453152027
                + t * 1.061405429))));
        return poly * Math.exp(-x * x);
    }
}
