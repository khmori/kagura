package com.khmori.kagura.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "compounds")
@Data
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
}