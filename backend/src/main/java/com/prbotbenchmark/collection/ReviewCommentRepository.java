package com.prbotbenchmark.collection;

import com.prbotbenchmark.collection.domain.CommentSource;
import com.prbotbenchmark.collection.domain.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    List<ReviewComment> findByReplayPrId(Long replayPrId);

    List<ReviewComment> findByReplayPrIdAndSource(Long replayPrId, CommentSource source);

    List<ReviewComment> findByReplayPrRunId(Long runId);
}
