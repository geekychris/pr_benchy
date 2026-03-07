package com.prbotbenchmark.repository.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "exemplar_repos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ExemplarRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "github_url")
    private String githubUrl;

    private String owner;

    @Column(name = "repo_name")
    private String repoName;

    @Column(name = "mirror_org")
    private String mirrorOrg;

    @Column(name = "mirror_repo_name")
    private String mirrorRepoName;

    @Column(name = "mirror_repo_url")
    private String mirrorRepoUrl;

    @Builder.Default
    @Column(name = "default_branch")
    private String defaultBranch = "main";

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Builder.Default
    @Column(name = "sync_status")
    private String syncStatus = "PENDING";

    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson;

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
