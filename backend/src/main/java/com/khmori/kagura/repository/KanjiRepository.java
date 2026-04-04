package com.khmori.kagura.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.khmori.kagura.entity.Kanji;

public interface KanjiRepository extends JpaRepository<Kanji, Integer> {
}