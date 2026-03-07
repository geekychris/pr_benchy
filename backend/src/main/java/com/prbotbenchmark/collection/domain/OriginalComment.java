package com.prbotbenchmark.collection.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prbotbenchmark.benchmark.domain.SuitePr;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "original_comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OriginalComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_pr_id")
    private SuitePr suitePr;

    @Column(name = "github_comment_id")
    private String githubCommentId;

    private String author;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_type")
    private CommentType commentType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "diff_hunk", columnDefinition = "text")
    private String diffHunk;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "body_normalized", columnDefinition = "text")
    private String bodyNormalized;

    @Column(name = "fingerprint_hash")
    private String fingerprintHash;

    @Column(name = "github_created_at")
    private Instant githubCreatedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
