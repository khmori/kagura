package com.khmori.kagura.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "compounds")
@Getter
@Setter
public class Compound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false)
    private String compound;

    @Column(columnDefinition = "TEXT[]")
    private String[] reading;

    @Column(columnDefinition = "TEXT[]")
    private String[] meaning;

    @Column
    private Boolean common;

    @Column
    private Integer jlpt;

    @ManyToMany(mappedBy = "compounds")
    private Set<Kanji> kanji= new HashSet<>();
}