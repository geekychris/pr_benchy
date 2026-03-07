package com.prbotbenchmark.grading.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prbotbenchmark.benchmark.domain.SuitePr;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "golden_dataset_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class GoldenDatasetEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_pr_id")
    private SuitePr suitePr;

    @Column(name = "source_comment_id")
    private Long sourceCommentId;

    @Column(name = "source_comment_type")
    private String sourceCommentType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "issue_type")
    private String issueType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "canonical_body", columnDefinition = "text")
    private String canonicalBody;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    @Column(name = "included_by_default")
    private Boolean includedByDefault = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
