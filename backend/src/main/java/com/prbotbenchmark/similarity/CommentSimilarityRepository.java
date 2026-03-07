package com.prbotbenchmark.similarity;

import com.prbotbenchmark.similarity.domain.CommentSimilarity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentSimilarityRepository extends JpaRepository<CommentSimilarity, Long> {

    List<CommentSimilarity> findByCommentAIdAndCommentAType(Long id, String type);

    List<CommentSimilarity> findByCommentBIdAndCommentBType(Long id, String type);

    Optional<CommentSimilarity> findByCommentAIdAndCommentATypeAndCommentBIdAndCommentBTypeAndStrategy(
            Long aId, String aType, Long bId, String bType, String strategy);
}
