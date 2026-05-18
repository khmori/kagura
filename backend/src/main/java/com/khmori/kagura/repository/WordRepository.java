package com.khmori.kagura.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.khmori.kagura.entity.Word;

public interface WordRepository extends JpaRepository<Word, Integer> {
    Optional<Word> findByWord(String word);
}
