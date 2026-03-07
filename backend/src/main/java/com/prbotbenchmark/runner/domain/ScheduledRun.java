package com.prbotbenchmark.runner.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prbotbenchmark.benchmark.domain.BenchmarkSuite;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "scheduled_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ScheduledRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id")
    private BenchmarkSuite suite;

    @Column(name = "bot_ids")
    private String botIds;

    @Column(name = "cron_expression")
    private String cronExpression;

    private Integer concurrency;

    @Column(name = "golden_dataset_enabled")
    private Boolean goldenDatasetEnabled;

    private Boolean enabled;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_id")
    private Long lastRunId;

    @Column(name = "last_run_status")
    private String lastRunStatus;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

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
