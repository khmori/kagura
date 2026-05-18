package com.khmori.kagura.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "user_vocab",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "anki_note_id"})
)
@Data
public class UserVocab {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "anki_note_id", nullable = false)
    private Long ankiNoteId;

    @Column(name = "anki_model_name", nullable = false)
    private String ankiModelName;

    @Column(nullable = false)
    private String expression;

    @Column(name = "sentence_filled", nullable = false)
    private Boolean sentenceFilled = false;

    @Column(name = "expression_audio_filled", nullable = false)
    private Boolean expressionAudioFilled = false;

    @Column(name = "sentence_audio_filled", nullable = false)
    private Boolean sentenceAudioFilled = false;

    @Column(name = "image_filled", nullable = false)
    private Boolean imageFilled = false;

    // Nullable: exact-match lookup against JMDICT will miss inflected forms, proper nouns, etc.
    @ManyToOne
    @JoinColumn(name = "word_id")
    private Word word;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_status", nullable = false)
    private RetentionStatus retentionStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> cards = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> fields = new HashMap<>();

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();
}