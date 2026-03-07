package com.prbotbenchmark.grading;

import com.prbotbenchmark.grading.domain.GoldenDatasetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoldenDatasetRepository extends JpaRepository<GoldenDatasetEntry, Long> {

    List<GoldenDatasetEntry> findBySuitePrId(Long suitePrId);

    List<GoldenDatasetEntry> findByActiveTrue();

    List<GoldenDatasetEntry> findByActiveTrueAndIncludedByDefaultTrue();

    List<GoldenDatasetEntry> findBySuitePrSuiteId(Long suiteId);
}
