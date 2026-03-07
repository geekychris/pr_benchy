package com.prbotbenchmark.benchmark.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "suite_prs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SuitePr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id")
    private BenchmarkSuite suite;

    @Column(name = "original_pr_number")
    private Integer originalPrNumber;

    private String title;

    private String description;

    @Column(name = "base_commit_sha")
    private String baseCommitSha;

    @Column(name = "head_commit_sha")
    private String headCommitSha;

    @Column(name = "base_branch")
    private String baseBranch;

    @Column(name = "head_branch")
    private String headBranch;

    private String author;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "files_changed")
    private Integer filesChanged;

    private Integer additions;

    private Integer deletions;

    @Column(name = "labels_json")
    private String labelsJson;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
