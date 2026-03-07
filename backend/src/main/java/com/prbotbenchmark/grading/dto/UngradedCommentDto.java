package com.prbotbenchmark.grading.dto;

public record UngradedCommentDto(
        Long commentId,
        String commentTableType,  // REVIEW or ORIGINAL
        Long replayPrId,          // null if ORIGINAL
        Long suitePrId,           // always present
        Integer originalPrNumber,
        String prTitle,
        String botName,           // null if ORIGINAL
        String commentType,       // REVIEW_COMMENT, PR_COMMENT, REVIEW
        String filePath,
        Integer lineNumber,
        String body,
        Integer stars,            // current grading if any partial grading exists
        Boolean flagged           // current grading if any partial grading exists
) {}
