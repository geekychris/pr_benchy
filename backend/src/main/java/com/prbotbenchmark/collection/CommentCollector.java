package com.prbotbenchmark.collection;

import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.collection.domain.CommentSource;
import com.prbotbenchmark.collection.domain.CommentType;
import com.prbotbenchmark.collection.domain.OriginalComment;
import com.prbotbenchmark.collection.domain.ReviewComment;
import com.prbotbenchmark.github.GitHubApiClient;
import com.prbotbenchmark.github.dto.GitHubIssueComment;
import com.prbotbenchmark.github.dto.GitHubPrComment;
import com.prbotbenchmark.github.dto.GitHubReview;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import com.prbotbenchmark.runner.domain.ReplayPr;
import com.prbotbenchmark.similarity.WinnowingHashStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Collects and persists all comments from a replay PR or an original suite PR.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class CommentCollector {

    private final ReviewCommentRepository reviewCommentRepository;
    private final OriginalCommentRepository originalCommentRepository;
    private final GitHubApiClient gitHubApiClient;
    private final TextNormalizer textNormalizer;
    private final WinnowingHashStrategy winnowingHashStrategy;

    /**
     * Collects and persists all comments from a replay PR (bot comments on the mirror repo).
     */
    public void collectComments(ReplayPr replayPr) {
        ExemplarRepo exemplarRepo = replayPr.getRun().getSuite().getExemplarRepo();
        String mirrorOwner = resolveMirrorOwner(exemplarRepo);
        String mirrorRepoName = resolveMirrorRepoName(exemplarRepo);
        int prNumber = replayPr.getMirrorPrNumber();

        log.info("Collecting comments for replay PR #{} in {}/{}", prNumber, mirrorOwner, mirrorRepoName);

        // 1. Collect inline review comments
        try {
            List<GitHubPrComment> reviewComments = gitHubApiClient.listPrReviewComments(mirrorOwner, mirrorRepoName, prNumber);
            for (GitHubPrComment ghComment : reviewComments) {
                if (ghComment.body() == null || ghComment.body().isBlank()) {
                    continue;
                }
                String normalized = textNormalizer.normalize(ghComment.body());
                String fingerprint = winnowingHashStrategy.computeFingerprint(normalized);

                Integer lineNumber = ghComment.line() != null ? ghComment.line() : ghComment.originalLine();

                ReviewComment comment = ReviewComment.builder()
                        .replayPr(replayPr)
                        .source(CommentSource.BOT)
                        .bot(replayPr.getBot())
                        .githubCommentId(String.valueOf(ghComment.id()))
                        .commentType(CommentType.REVIEW_COMMENT)
                        .filePath(ghComment.path())
                        .lineNumber(lineNumber)
                        .diffHunk(ghComment.diffHunk())
                        .body(ghComment.body())
                        .bodyNormalized(normalized)
                        .fingerprintHash(fingerprint)
                        .githubCreatedAt(ghComment.createdAt())
                        .build();
                reviewCommentRepository.save(comment);
            }
            log.debug("Collected {} inline review comments for replay PR #{}", reviewComments.size(), prNumber);
        } catch (Exception e) {
            log.warn("Failed to collect inline review comments for replay PR #{}: {}", prNumber, e.getMessage());
        }

        // 2. Collect PR reviews with body
        try {
            List<GitHubReview> reviews = gitHubApiClient.listPrReviews(mirrorOwner, mirrorRepoName, prNumber);
            int savedReviews = 0;
            for (GitHubReview ghReview : reviews) {
                if (ghReview.body() == null || ghReview.body().isBlank()) {
                    continue;
                }
                String normalized = textNormalizer.normalize(ghReview.body());
                String fingerprint = winnowingHashStrategy.computeFingerprint(normalized);

                ReviewComment comment = ReviewComment.builder()
                        .replayPr(replayPr)
                        .source(CommentSource.BOT)
                        .bot(replayPr.getBot())
                        .githubCommentId(String.valueOf(ghReview.id()))
                        .commentType(CommentType.REVIEW)
                        .body(ghReview.body())
                        .bodyNormalized(normalized)
                        .fingerprintHash(fingerprint)
                        .githubCreatedAt(ghReview.submittedAt())
                        .build();
                reviewCommentRepository.save(comment);
                savedReviews++;
            }
            log.debug("Collected {} PR reviews with body for replay PR #{}", savedReviews, prNumber);
        } catch (Exception e) {
            log.warn("Failed to collect PR reviews for replay PR #{}: {}", prNumber, e.getMessage());
        }

        // 3. Collect general PR comments (issue comments)
        try {
            List<GitHubIssueComment> issueComments = gitHubApiClient.listIssueComments(mirrorOwner, mirrorRepoName, prNumber);
            for (GitHubIssueComment ghComment : issueComments) {
                if (ghComment.body() == null || ghComment.body().isBlank()) {
                    continue;
                }
                String normalized = textNormalizer.normalize(ghComment.body());
                String fingerprint = winnowingHashStrategy.computeFingerprint(normalized);

                ReviewComment comment = ReviewComment.builder()
                        .replayPr(replayPr)
                        .source(CommentSource.BOT)
                        .bot(replayPr.getBot())
                        .githubCommentId(String.valueOf(ghComment.id()))
                        .commentType(CommentType.PR_COMMENT)
                        .body(ghComment.body())
                        .bodyNormalized(normalized)
                        .fingerprintHash(fingerprint)
                        .githubCreatedAt(ghComment.createdAt())
                        .build();
                reviewCommentRepository.save(comment);
            }
            log.debug("Collected {} general PR comments for replay PR #{}", issueComments.size(), prNumber);
        } catch (Exception e) {
            log.warn("Failed to collect general PR comments for replay PR #{}: {}", prNumber, e.getMessage());
        }
    }

    /**
     * Collects and persists all human comments from an original PR in the exemplar repo.
     * Called once per SuitePr (not per bot).
     */
    public void collectOriginalComments(SuitePr suitePr, String exemplarOwner, String exemplarRepo) {
        int prNumber = suitePr.getOriginalPrNumber();
        log.info("Collecting original comments for suite PR #{} in {}/{}", prNumber, exemplarOwner, exemplarRepo);

        // Collect inline review comments
        try {
            List<GitHubPrComment> reviewComments = gitHubApiClient.listPrReviewComments(exemplarOwner, exemplarRepo, prNumber);
            for (GitHubPrComment ghComment : reviewComments) {
                if (ghComment.body() == null || ghComment.body().isBlank()) {
                    continue;
                }
                String normalized = textNormalizer.normalize(ghComment.body());
                String fingerprint = winnowingHashStrategy.computeFingerprint(normalized);

                Integer lineNumber = ghComment.line() != null ? ghComment.line() : ghComment.originalLine();
                String author = ghComment.user() != null ? ghComment.user().login() : null;

                OriginalComment comment = OriginalComment.builder()
                        .suitePr(suitePr)
                        .githubCommentId(String.valueOf(ghComment.id()))
                        .author(author)
                        .commentType(CommentType.REVIEW_COMMENT)
                        .filePath(ghComment.path())
                        .lineNumber(lineNumber)
                        .diffHunk(ghComment.diffHunk())
                        .body(ghComment.body())
                        .bodyNormalized(normalized)
                        .fingerprintHash(fingerprint)
                        .githubCreatedAt(ghComment.createdAt())
                        .build();
                originalCommentRepository.save(comment);
            }
            log.debug("Collected {} original inline review comments for suite PR #{}", reviewComments.size(), prNumber);
        } catch (Exception e) {
            log.warn("Failed to collect original inline review comments for suite PR #{}: {}", prNumber, e.getMessage());
        }

        // Collect general PR comments (issue comments)
        try {
            List<GitHubIssueComment> issueComments = gitHubApiClient.listIssueComments(exemplarOwner, exemplarRepo, prNumber);
            for (GitHubIssueComment ghComment : issueComments) {
                if (ghComment.body() == null || ghComment.body().isBlank()) {
                    continue;
                }
                String normalized = textNormalizer.normalize(ghComment.body());
                String fingerprint = winnowingHashStrategy.computeFingerprint(normalized);

                String author = ghComment.user() != null ? ghComment.user().login() : null;

                OriginalComment comment = OriginalComment.builder()
                        .suitePr(suitePr)
                        .githubCommentId(String.valueOf(ghComment.id()))
                        .author(author)
                        .commentType(CommentType.PR_COMMENT)
                        .body(ghComment.body())
                        .bodyNormalized(normalized)
                        .fingerprintHash(fingerprint)
                        .githubCreatedAt(ghComment.createdAt())
                        .build();
                originalCommentRepository.save(comment);
            }
            log.debug("Collected {} original general PR comments for suite PR #{}", issueComments.size(), prNumber);
        } catch (Exception e) {
            log.warn("Failed to collect original general PR comments for suite PR #{}: {}", prNumber, e.getMessage());
        }
    }

    // ---- Helpers ----

    private String resolveMirrorOwner(ExemplarRepo exemplarRepo) {
        String mirrorOrg = exemplarRepo.getMirrorOrg();
        return (mirrorOrg != null && !mirrorOrg.isBlank()) ? mirrorOrg : exemplarRepo.getOwner();
    }

    private String resolveMirrorRepoName(ExemplarRepo exemplarRepo) {
        String mirrorRepoName = exemplarRepo.getMirrorRepoName();
        if (mirrorRepoName != null && !mirrorRepoName.isBlank()) {
            return mirrorRepoName;
        }
        return exemplarRepo.getRepoName() + "-benchmark";
    }
}
