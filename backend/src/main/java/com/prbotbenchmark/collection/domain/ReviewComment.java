package com.prbotbenchmark.collection.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prbotbenchmark.bot.domain.Bot;
import com.prbotbenchmark.runner.domain.ReplayPr;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "review_comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replay_pr_id")
    private ReplayPr replayPr;

    @Enumerated(EnumType.STRING)
    private CommentSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id", nullable = true)
    private Bot bot;

    @Column(name = "github_comment_id")
    private String githubCommentId;

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
