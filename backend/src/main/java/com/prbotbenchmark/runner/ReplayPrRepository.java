package com.prbotbenchmark.runner;

import com.prbotbenchmark.runner.domain.ReplayPr;
import com.prbotbenchmark.runner.domain.ReplayStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReplayPrRepository extends JpaRepository<ReplayPr, Long> {

    List<ReplayPr> findByRunId(Long runId);

    List<ReplayPr> findByRunIdAndStatus(Long runId, ReplayStatus status);

    long countByRunId(Long runId);

    long countByRunIdAndStatus(Long runId, ReplayStatus status);
}
