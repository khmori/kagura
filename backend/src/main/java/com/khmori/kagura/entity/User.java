package com.khmori.kagura.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
)
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    // { "<modelName>": { "<slot>": "<anki field name>", ... }, ... }
    // Slot names are Kagura's vocabulary (expression, sentence, sentenceAudio, ...).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_mapping", columnDefinition = "jsonb", nullable = false)
    private Map<String, Map<String, String>> fieldMapping = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
