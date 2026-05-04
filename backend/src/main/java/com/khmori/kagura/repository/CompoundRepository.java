package com.khmori.kagura.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.khmori.kagura.entity.Compound;

public interface CompoundRepository extends JpaRepository<Compound, Integer> {

    /**
     * Returns IDs of compounds containing at least one of the given kanji characters.
     * Use with {@link #findAllWithKanjiByIdIn} to load full compounds with both kanji.
     */
    @Query("SELECT DISTINCT c.id FROM Compound c JOIN c.kanji k WHERE k.kanji IN :chars")
    List<Integer> findIdsContainingAny(@Param("chars") Collection<String> chars);

    /**
     * Loads compounds by id with their full kanji set eagerly fetched.
     * Combine with {@link #findIdsContainingAny} to avoid the JOIN FETCH + WHERE pitfall
     * where filtering trims the loaded collection to only matched kanji.
     */
    @Query("SELECT DISTINCT c FROM Compound c JOIN FETCH c.kanji WHERE c.id IN :ids")
    List<Compound> findAllWithKanjiByIdIn(@Param("ids") Collection<Integer> ids);

    /**
     * Returns IDs of 2-kanji compounds whose every constituent kanji is in {@code known}.
     * Pair with {@link #findAllWithKanjiByIdIn} to load the full Compound entities.
     */
    @Query("""
        SELECT c.id FROM Compound c JOIN c.kanji k
        GROUP BY c.id
        HAVING COUNT(k) = 2
           AND COUNT(k) = SUM(CASE WHEN k.kanji IN :known THEN 1 ELSE 0 END)
        """)
    List<Integer> findIdsAllKanjiKnown2Char(@Param("known") Collection<String> known);
}