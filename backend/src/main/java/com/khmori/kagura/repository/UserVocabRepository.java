package com.khmori.kagura.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.khmori.kagura.entity.UserVocab;

public interface UserVocabRepository extends JpaRepository<UserVocab, Integer> {
    Optional<UserVocab> findByUserIdAndAnkiNoteId(Integer userId, Long ankiNoteId);
    List<UserVocab> findByUserId(Integer userId);
}