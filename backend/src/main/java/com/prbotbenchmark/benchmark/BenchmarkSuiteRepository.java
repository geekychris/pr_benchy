package com.prbotbenchmark.benchmark;

import com.prbotbenchmark.benchmark.domain.BenchmarkSuite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenchmarkSuiteRepository extends JpaRepository<BenchmarkSuite, Long> {

    List<BenchmarkSuite> findByExemplarRepoId(Long repoId);
}
