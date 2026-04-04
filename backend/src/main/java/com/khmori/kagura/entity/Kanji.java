package com.khmori.kagura.entity;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "kanji")
@Data
public class Kanji {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false)
    private String kanji;

    @Column(name = "on_reading", columnDefinition = "TEXT[]")
    private String[] onReading;

    @Column(name = "kun_reading", columnDefinition = "TEXT[]")
    private String[] kunReading;

    @Column(columnDefinition = "TEXT[]")
    private String[] meaning;

    private Integer grade;

    private Integer jlpt;

    private Integer strokeCount;
    
    private Integer frequency;

    @Column(name = "radical_classical")
    private Integer radicalClassical;

    @Column(name = "radical_nelson")
    private Integer radicalNelson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}