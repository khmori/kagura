package com.khmori.kagura.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "words")
@Data
public class Word {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false)
    private String word;

    @Column(columnDefinition = "TEXT[]")
    private String[] reading;

    @Column(columnDefinition = "TEXT[]")
    private String[] meaning;

    @Column
    private Boolean common;

    @Column
    private Integer jlpt;

    @ManyToMany(mappedBy = "words")
    private Set<Kanji> kanji = new HashSet<>();
}
