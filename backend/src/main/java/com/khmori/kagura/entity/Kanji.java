package com.khmori.kagura.entity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "kanji")
@Data
@EqualsAndHashCode(exclude = "words")
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

    @Column(name = "jlpt_level") 
    private Integer jlptLevel;

    @Column(name = "stroke_count")
    private Integer strokeCount;
    
    private Integer frequency;

    @Column(name = "radical_classical")
    private Integer radicalClassical;

    @Column(name = "radical_nelson")
    private Integer radicalNelson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "kanji_words",
        joinColumns = @JoinColumn(name = "kanji_id"),
        inverseJoinColumns = @JoinColumn(name = "word_id")
    )
    private Set<Word> words = new HashSet<>();
}