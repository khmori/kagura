package com.khmori.kagura.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.khmori.kagura.entity.Kanji;

public interface KanjiRepository extends JpaRepository<Kanji, Integer> {
    Optional<Kanji> findByKanji(String kanji);
    long countByKankenLevelIsNotNull();

    @Query(nativeQuery = true, value = """
        SELECT k.kanji, COALESCE(uk.proficiency_score, 0), COALESCE(uk.known, false),
               k.jlpt_level, k.kanken_level
        FROM kanji k
        LEFT JOIN user_kanji uk ON uk.kanji_id = k.id AND uk.user_id = :userId
        WHERE k.jlpt_level IS NOT NULL
        ORDER BY k.jlpt_level DESC
        """)
    List<Object[]> findAllByJlptWithProficiency(@Param("userId") Integer userId);

    @Query(nativeQuery = true, value = """
        SELECT k.kanji, COALESCE(uk.proficiency_score, 0), COALESCE(uk.known, false),
               k.jlpt_level, k.kanken_level
        FROM kanji k
        LEFT JOIN user_kanji uk ON uk.kanji_id = k.id AND uk.user_id = :userId
        WHERE k.kanken_level IS NOT NULL
        ORDER BY k.kanken_level DESC
        """)
    List<Object[]> findAllByKankenWithProficiency(@Param("userId") Integer userId);
}