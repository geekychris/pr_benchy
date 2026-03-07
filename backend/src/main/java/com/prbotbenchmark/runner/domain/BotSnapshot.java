package com.prbotbenchmark.runner.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prbotbenchmark.bot.domain.Bot;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "bot_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BotSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    @JsonIgnoreProperties("snapshots")
    private BenchmarkRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id")
    private Bot bot;

    private String name;

    @Column(name = "workflow_file_name")
    private String workflowFileName;

    @Column(name = "workflow_content", columnDefinition = "text")
    private String workflowContent;

    @Column(name = "wait_strategy")
    private String waitStrategy;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "snapshotted_at")
    private Instant snapshottedAt;

    @PrePersist
    protected void onCreate() {
        if (snapshottedAt == null) {
            snapshottedAt = Instant.now();
        }
    }
}
