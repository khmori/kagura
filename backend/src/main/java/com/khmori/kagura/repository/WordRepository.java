package com.khmori.kagura.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.khmori.kagura.entity.Word;

public interface WordRepository extends JpaRepository<Word, Integer> {
    Optional<Word> findByWord(String word);
    long countByFrequencyRankIsNotNull();

    /**
     * Finds the highest-value words for a user to learn next.
     *
     * Steps:
     * 1. Start with all words that have a frequency rank AND contain at least one kanji
     *    (join through kanji_words to exclude kana-only words).
     * 2. Exclude words the user already has in their Anki deck (user_vocab).
     * 3. Split each word into individual characters and look up the user's proficiency
     *    for each kanji via user_kanji. Unknown kanji default to 0.
     * 4. Average the per-kanji proficiency scores to get "kanji familiarity" for the word.
     * 5. Compute a final score blending frequency and kanji familiarity:
     *
     *    score = (1 / ln(frequency_rank + 2)) * (0.3 + 0.7 * avg_kanji_proficiency)
     *
     *    - Frequency component: 1/ln(rank+2) gives a logarithmic curve where common words
     *      score much higher, but the gap between rare words is small.
     *    - Kanji familiarity: 70% weight. Words whose kanji the user already knows are
     *      easier to acquire, so they rank higher.
     *    - 30% base: ensures very common words still surface even if all their kanji are
     *      unfamiliar to the user.
     *
     * Returns Object[] rows: [id, word, reading, meaning, frequency_rank, score].
     */
    @Query(nativeQuery = true, value = """
        SELECT w.id, w.word, w.reading, w.meaning, w.frequency_rank,
               (1.0 / LN(w.frequency_rank + 2)) * (0.3 + 0.7 * COALESCE(AVG(uk.proficiency_score), 0)) AS score
        FROM words w
        JOIN kanji_words kw ON kw.word_id = w.id
        CROSS JOIN LATERAL regexp_split_to_table(w.word, '') AS ch
        LEFT JOIN kanji k ON k.kanji = ch
        LEFT JOIN user_kanji uk ON uk.kanji_id = k.id AND uk.user_id = :userId
        WHERE w.frequency_rank IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM user_vocab uv
              WHERE uv.user_id = :userId AND uv.word_id = w.id
          )
        GROUP BY w.id
        ORDER BY score DESC
        LIMIT :lim
        """)
    List<Object[]> findRecommendedWords(@Param("userId") Integer userId, @Param("lim") int lim);
}
