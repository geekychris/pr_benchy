package com.prbotbenchmark.collection;

import com.prbotbenchmark.collection.domain.OriginalComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OriginalCommentRepository extends JpaRepository<OriginalComment, Long> {

    List<OriginalComment> findBySuitePrId(Long suitePrId);
}
