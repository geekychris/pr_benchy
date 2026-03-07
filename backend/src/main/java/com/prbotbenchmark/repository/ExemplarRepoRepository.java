package com.prbotbenchmark.repository;

import com.prbotbenchmark.repository.domain.ExemplarRepo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExemplarRepoRepository extends JpaRepository<ExemplarRepo, Long> {

    Optional<ExemplarRepo> findByOwnerAndRepoName(String owner, String repoName);
}
