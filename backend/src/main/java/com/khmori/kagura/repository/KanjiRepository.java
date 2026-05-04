package com.khmori.kagura.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.khmori.kagura.entity.Kanji;

public interface KanjiRepository extends JpaRepository<Kanji, Integer> {
    Optional<Kanji> findByKanji(String kanji);
    List<Kanji> findByKanjiIn(Collection<String> chars);
}