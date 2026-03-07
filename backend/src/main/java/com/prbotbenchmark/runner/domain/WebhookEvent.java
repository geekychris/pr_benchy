package com.prbotbenchmark.runner.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", unique = true, length = 100)
    private String deliveryId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "repo_full_name", length = 255)
    private String repoFullName;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }
}
