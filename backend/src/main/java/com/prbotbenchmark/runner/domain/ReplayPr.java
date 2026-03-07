package com.prbotbenchmark.runner.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.bot.domain.Bot;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "replay_prs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ReplayPr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private BenchmarkRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_pr_id")
    private SuitePr suitePr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id")
    private Bot bot;

    @Column(name = "mirror_pr_number")
    private Integer mirrorPrNumber;

    @Column(name = "mirror_pr_url")
    private String mirrorPrUrl;

    @Column(name = "base_branch")
    private String baseBranch;

    @Column(name = "head_branch")
    private String headBranch;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ReplayStatus status = ReplayStatus.PENDING;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
