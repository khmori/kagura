package com.khmori.kagura.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.khmori.kagura.entity.UserKanji;

public interface UserKanjiRepository extends JpaRepository<UserKanji, Integer> {
    List<UserKanji> findByUserId(Integer userId);
}