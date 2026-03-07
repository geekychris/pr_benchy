package com.prbotbenchmark.grading;

import com.prbotbenchmark.grading.domain.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueTypeRepository extends JpaRepository<IssueType, Long> {

    List<IssueType> findByActiveTrue();

    Optional<IssueType> findByCode(String code);

    List<IssueType> findByCategory(String category);
}
