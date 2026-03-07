package com.prbotbenchmark.similarity;

import com.prbotbenchmark.collection.OriginalCommentRepository;
import com.prbotbenchmark.collection.ReviewCommentRepository;
import com.prbotbenchmark.collection.TextNormalizer;
import com.prbotbenchmark.collection.domain.ReviewComment;
import com.prbotbenchmark.similarity.domain.CommentSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for computing and retrieving similarity scores between comments.
 */
@Service
@Transactional
@Slf4j
public class SimilarityService {

    private final List<SimilarityStrategy> strategies;
    private final CommentSimilarityRepository commentSimilarityRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final OriginalCommentRepository originalCommentRepository;
    private final TextNormalizer textNormalizer;
    private final String defaultStrategies;

    public SimilarityService(
            List<SimilarityStrategy> strategies,
            CommentSimilarityRepository commentSimilarityRepository,
            ReviewCommentRepository reviewCommentRepository,
            OriginalCommentRepository originalCommentRepository,
            TextNormalizer textNormalizer,
            @Value("${app.similarity.default-strategies:EXACT,NORMALIZED_TEXT,WINNOWING}") String defaultStrategies) {
        this.strategies = strategies;
        this.commentSimilarityRepository = commentSimilarityRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.originalCommentRepository = originalCommentRepository;
        this.textNormalizer = textNormalizer;
        this.defaultStrategies = defaultStrategies;
    }

    /**
     * Analyzes similarities for all review comments in a benchmark run.
     * Compares each pair of comments using all configured strategies.
     */
    public void analyzeRunSimilarities(Long runId) {
        List<ReviewComment> comments = reviewCommentRepository.findByReplayPrRunId(runId);
        log.info("Analyzing similarities for run {} with {} comments", runId, comments.size());

        List<SimilarityStrategy> configuredStrategies = getConfiguredStrategies();
        if (configuredStrategies.isEmpty()) {
            log.warn("No configured strategies found for run {} similarity analysis. Configured: {}", runId, defaultStrategies);
            return;
        }

        int pairs = 0;
        for (int i = 0; i < comments.size(); i++) {
            ReviewComment commentA = comments.get(i);
            for (int j = i + 1; j < comments.size(); j++) {
                ReviewComment commentB = comments.get(j);
                computeAndSaveStrategies(
                        commentA.getId(), "ReviewComment", commentA.getBodyNormalized(),
                        commentA.getFilePath(), commentA.getLineNumber(),
                        commentB.getId(), "ReviewComment", commentB.getBodyNormalized(),
                        commentB.getFilePath(), commentB.getLineNumber(),
                        configuredStrategies
                );
                pairs++;
            }
        }
        log.info("Completed similarity analysis for run {}: {} pairs across {} strategies", runId, pairs, configuredStrategies.size());
    }

    /**
     * Returns all similarity records where the given comment is either commentA or commentB.
     */
    @Transactional(readOnly = true)
    public List<CommentSimilarity> findSimilarities(Long commentId, String commentType) {
        List<CommentSimilarity> result = new ArrayList<>();
        result.addAll(commentSimilarityRepository.findByCommentAIdAndCommentAType(commentId, commentType));
        result.addAll(commentSimilarityRepository.findByCommentBIdAndCommentBType(commentId, commentType));
        return result;
    }

    // ---- Private helpers ----

    private void computeAndSaveStrategies(
            Long idA, String typeA, String textA, String fileA, Integer lineA,
            Long idB, String typeB, String textB, String fileB, Integer lineB,
            List<SimilarityStrategy> strategyList) {

        for (SimilarityStrategy strategy : strategyList) {
            try {
                // Skip if already computed
                Optional<CommentSimilarity> existing = commentSimilarityRepository
                        .findByCommentAIdAndCommentATypeAndCommentBIdAndCommentBTypeAndStrategy(
                                idA, typeA, idB, typeB, strategy.getName());
                if (existing.isPresent()) {
                    continue;
                }

                SimilarityStrategy.SimilarityResult result = strategy.compute(textA, textB, fileA, lineA, fileB, lineB);

                CommentSimilarity similarity = CommentSimilarity.builder()
                        .commentAId(idA)
                        .commentAType(typeA)
                        .commentBId(idB)
                        .commentBType(typeB)
                        .strategy(strategy.getName())
                        .score(result.score())
                        .isMatch(result.isMatch())
                        .build();
                commentSimilarityRepository.save(similarity);

            } catch (Exception e) {
                log.warn("Strategy {} failed for comments {}/{} vs {}/{}: {}",
                        strategy.getName(), typeA, idA, typeB, idB, e.getMessage());
            }
        }
    }

    private List<SimilarityStrategy> getConfiguredStrategies() {
        if (defaultStrategies == null || defaultStrategies.isBlank()) {
            return strategies;
        }

        Set<String> configured = Arrays.stream(defaultStrategies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        return strategies.stream()
                .filter(s -> configured.contains(s.getName()))
                .collect(Collectors.toList());
    }
}
