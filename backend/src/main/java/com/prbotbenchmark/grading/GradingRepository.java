package com.prbotbenchmark.grading;

import com.prbotbenchmark.grading.domain.Grading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradingRepository extends JpaRepository<Grading, Long> {

    List<Grading> findByCommentIdAndCommentTableType(Long commentId, String commentTableType);

    List<Grading> findByGraderType(String graderType);
}
