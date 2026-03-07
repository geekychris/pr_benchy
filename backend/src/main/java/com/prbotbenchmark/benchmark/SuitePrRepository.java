package com.prbotbenchmark.benchmark;

import com.prbotbenchmark.benchmark.domain.SuitePr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SuitePrRepository extends JpaRepository<SuitePr, Long> {

    List<SuitePr> findBySuiteId(Long suiteId);

    Optional<SuitePr> findBySuiteIdAndOriginalPrNumber(Long suiteId, Integer prNumber);

    @Modifying
    @Transactional
    void deleteBySuiteIdAndId(Long suiteId, Long id);
}
