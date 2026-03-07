package com.prbotbenchmark.similarity.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "comment_similarities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CommentSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_a_id")
    private Long commentAId;

    @Column(name = "comment_a_type")
    private String commentAType;

    @Column(name = "comment_b_id")
    private Long commentBId;

    @Column(name = "comment_b_type")
    private String commentBType;

    private String strategy;

    private Double score;

    @Column(name = "is_match")
    private Boolean isMatch;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
