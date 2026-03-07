package com.prbotbenchmark.collection;

import com.prbotbenchmark.benchmark.SuitePrRepository;
import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.collection.domain.OriginalComment;
import com.prbotbenchmark.collection.domain.ReviewComment;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import com.prbotbenchmark.similarity.CommentSimilarityRepository;
import com.prbotbenchmark.similarity.SimilarityService;
import com.prbotbenchmark.similarity.domain.CommentSimilarity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for comment collection and similarity analysis.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final ReviewCommentRepository reviewCommentRepository;
    private final OriginalCommentRepository originalCommentRepository;
    private final CommentCollector commentCollector;
    private final SimilarityService similarityService;
    private final SuitePrRepository suitePrRepository;

    /**
     * GET /api/replay-prs/{replayPrId}/comments
     * Lists all review comments for a replay PR.
     */
    @GetMapping("/api/replay-prs/{replayPrId}/comments")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReviewComment>> listReplayPrComments(@PathVariable Long replayPrId) {
        List<ReviewComment> comments = reviewCommentRepository.findByReplayPrId(replayPrId);
        return ResponseEntity.ok(comments);
    }

    /**
     * GET /api/suite-prs/{suitePrId}/original-comments
     * Lists all original (human) comments for a suite PR.
     */
    @GetMapping("/api/suite-prs/{suitePrId}/original-comments")
    @Transactional(readOnly = true)
    public ResponseEntity<List<OriginalComment>> listOriginalComments(@PathVariable Long suitePrId) {
        List<OriginalComment> comments = originalCommentRepository.findBySuitePrId(suitePrId);
        return ResponseEntity.ok(comments);
    }

    /**
     * POST /api/suite-prs/{suitePrId}/collect-original-comments
     * Triggers collection of original (human) comments for a suite PR from the exemplar repo.
     */
    @PostMapping("/api/suite-prs/{suitePrId}/collect-original-comments")
    public ResponseEntity<Void> collectOriginalComments(@PathVariable Long suitePrId) {
        SuitePr suitePr = suitePrRepository.findById(suitePrId)
                .orElseThrow(() -> new IllegalArgumentException("SuitePr not found: " + suitePrId));

        ExemplarRepo exemplarRepo = suitePr.getSuite().getExemplarRepo();
        String owner = exemplarRepo.getOwner();
        String repoName = exemplarRepo.getRepoName();

        log.info("Triggering original comment collection for suite PR {} in {}/{}", suitePrId, owner, repoName);
        commentCollector.collectOriginalComments(suitePr, owner, repoName);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/runs/{runId}/similarities
     * Analyzes and returns all similarity records for a benchmark run.
     */
    @GetMapping("/api/runs/{runId}/similarities")
    public ResponseEntity<List<CommentSimilarity>> analyzeRunSimilarities(@PathVariable Long runId) {
        similarityService.analyzeRunSimilarities(runId);

        // Gather all review comments for this run and collect all their similarities
        List<ReviewComment> comments = reviewCommentRepository.findByReplayPrRunId(runId);
        List<CommentSimilarity> similarities = comments.stream()
                .flatMap(c -> similarityService.findSimilarities(c.getId(), "ReviewComment").stream())
                .distinct()
                .toList();

        return ResponseEntity.ok(similarities);
    }
}
