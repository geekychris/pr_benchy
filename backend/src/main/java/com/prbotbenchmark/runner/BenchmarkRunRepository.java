package com.prbotbenchmark.runner;

import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {

    List<BenchmarkRun> findBySuiteId(Long suiteId);

    List<BenchmarkRun> findByStatus(RunStatus status);
}
