package com.khmori.kagura.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AnkiDeckParser {

    /**
     * Anki has shipped three collection filenames over time. The real data lives
     * in the newest one present:
     *   collection.anki21b  — Anki 2.1.50+, zstd-compressed package (NOT YET SUPPORTED)
     *   collection.anki21   — Anki 2.1+, plain SQLite
     *   collection.anki2    — legacy plain SQLite (often a 51KB empty stub in modern exports)
     * Prefer .anki21 over .anki2 since modern exports leave .anki2 as a stub.
     */
    private static final String[] COLLECTION_CANDIDATES = {
        "collection.anki21",
        "collection.anki2"
    };

    /**
     * Pull every reviewed card (type > 0; excludes new). The score curve naturally
     * weights low-interval cards toward 0, so we don't need a hard cutoff.
     * Front field only ({@code flds.split("")[0]}) — the prompt side, where
     * mastery is actually being tested.
     */
    private static final String SCORING_QUERY = """
        SELECT n.flds, c.ivl
        FROM cards c JOIN notes n ON c.nid = n.id
        WHERE c.type > 0
        """;

    /** Minimum cards containing a kanji before we trust the average interval. */
    private static final int MIN_CARDS_FOR_SCORE = 3;
    /** Interval normalization (days). 360-day avg ⇒ x=1.0 ⇒ score=0.75. */
    private static final double IVL_NORMALIZE_DAYS = 360.0;
    /** Field separator inside Anki notes. */
    private static final char FIELD_DELIMITER = '';

    /**
     * Returns kanji → mastery score in [0.0, 1.0]. Kanji appearing in fewer than
     * {@value #MIN_CARDS_FOR_SCORE} cards are omitted entirely.
     */
    public Map<String, Double> extractKanjiScores(MultipartFile apkg) throws IOException, SQLException {
        Path tempDb = Files.createTempFile("kagura-anki-", ".sqlite");
        try {
            extractCollectionDb(apkg, tempDb);
            return computeScores(tempDb);
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    private void extractCollectionDb(MultipartFile apkg, Path dest) throws IOException {
        // Two-pass: scan the zip once to learn what's present, then re-open and
        // extract the most-preferred candidate. ZipInputStream is forward-only.
        Set<String> entries = listEntries(apkg);
        String chosen = null;
        for (String candidate : COLLECTION_CANDIDATES) {
            if (entries.contains(candidate)) { chosen = candidate; break; }
        }
        if (chosen == null) {
            throw new IOException("No supported collection file found in .apkg "
                + "(looked for: " + String.join(", ", COLLECTION_CANDIDATES) + "). "
                + "Found: " + entries);
        }
        try (ZipInputStream zip = new ZipInputStream(apkg.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (chosen.equals(entry.getName())) {
                    Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException(chosen + " disappeared between scan and extract");
    }

    private Set<String> listEntries(MultipartFile apkg) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(apkg.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private Map<String, Double> computeScores(Path dbPath) throws SQLException {
        Map<String, List<Integer>> kanjiIvls = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(SCORING_QUERY);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String flds = rs.getString(1);
                int ivl = rs.getInt(2);
                if (flds == null) continue;
                String front = frontField(flds);
                for (String kanji : extractKanji(front)) {
                    kanjiIvls.computeIfAbsent(kanji, k -> new ArrayList<>()).add(ivl);
                }
            }
        }
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, List<Integer>> e : kanjiIvls.entrySet()) {
            List<Integer> ivls = e.getValue();
            if (ivls.size() < MIN_CARDS_FOR_SCORE) continue;
            double avg = ivls.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            double x = avg / IVL_NORMALIZE_DAYS;
            scores.put(e.getKey(), 1.0 - 1.0 / Math.pow(x + 1.0, 2));
        }
        return scores;
    }

    private String frontField(String flds) {
        int idx = flds.indexOf(FIELD_DELIMITER);
        return idx < 0 ? flds : flds.substring(0, idx);
    }

    private List<String> extractKanji(String text) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                out.add(String.valueOf(c));
            }
        }
        return out;
    }
}
