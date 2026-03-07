package com.prbotbenchmark.runner.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prbotbenchmark.benchmark.domain.BenchmarkSuite;
import com.prbotbenchmark.bot.domain.Bot;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "benchmark_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BenchmarkRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id")
    private BenchmarkSuite suite;

    private String name;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private RunStatus status = RunStatus.PENDING;

    @Builder.Default
    private Integer concurrency = 2;

    @Builder.Default
    @Column(name = "golden_dataset_enabled")
    private Boolean goldenDatasetEnabled = true;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToMany
    @JoinTable(
        name = "run_bots",
        joinColumns = @JoinColumn(name = "run_id"),
        inverseJoinColumns = @JoinColumn(name = "bot_id")
    )
    private List<Bot> bots;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("run")
    @lombok.Builder.Default
    private List<BotSnapshot> snapshots = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
