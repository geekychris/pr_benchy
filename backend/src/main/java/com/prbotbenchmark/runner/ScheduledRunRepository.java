package com.prbotbenchmark.runner;

import com.prbotbenchmark.runner.domain.ScheduledRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledRunRepository extends JpaRepository<ScheduledRun, Long> {

    List<ScheduledRun> findByEnabledTrue();

    List<ScheduledRun> findBySuiteId(Long suiteId);
}
