package com.khmori.kagura.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.khmori.kagura.entity.UserKanji;

public interface UserKanjiRepository extends JpaRepository<UserKanji, Integer> {
    List<UserKanji> findByUserIdOrderByProficiencyScoreDesc(Integer userId);

    // Extract kanji characters from each vocab expression (by joining with `kanji` table),
    // use average avg_interval per kanji (=avg), then calculate proficiency score in (0, 1].
    // Proficiency score formula = 1 - 1/(avg/180 + 1)^2.
    // Set known attribute based on if proficiency score >= 0.5.
    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO user_kanji (user_id, kanji_id, proficiency_score, known, last_computed_at)
        SELECT
            uv.user_id,
            k.id,
            1.0 - 1.0 / (POWER(AVG(uv.avg_interval) / 180.0 + 1.0), 2),
            (1.0 - 1.0 / (POWER(AVG(uv.avg_interval) / 180.0 + 1.0), 2)) >= 0.5,
            NOW()
        FROM user_vocab uv
        CROSS JOIN LATERAL regexp_split_to_table(uv.expression, '') AS ch
        JOIN kanji k ON k.kanji = ch
        WHERE uv.user_id = :userId
          AND uv.avg_interval IS NOT NULL
        GROUP BY uv.user_id, k.id
        ON CONFLICT (user_id, kanji_id) DO UPDATE SET
            proficiency_score = EXCLUDED.proficiency_score,
            known             = EXCLUDED.known,
            last_computed_at  = EXCLUDED.last_computed_at
        """)
    void computeScoresForUser(@Param("userId") Integer userId);
}