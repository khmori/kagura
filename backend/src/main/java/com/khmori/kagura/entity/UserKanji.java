package com.khmori.kagura.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Table(
    name = "user_kanji",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "kanji_id"})
)
@Data
public class UserKanji {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "kanji_id", nullable = false)
    private Kanji kanji;

    @Column(name = "proficiency_score", nullable = false)
    private Double proficiencyScore;

    @Column(nullable = false)
    private Boolean known;

    @Column(name = "last_computed_at", nullable = false)
    private Instant lastComputedAt = Instant.now();
}