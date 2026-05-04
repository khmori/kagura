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
import java.util.HashSet;
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
     * Mature-card definition: in the review queue (queue >= 2) AND interval >= 21 days.
     * No deck filter — every deck in the uploaded .apkg contributes.
     */
    private static final String MATURE_QUERY = """
        SELECT DISTINCT n.flds
        FROM cards c JOIN notes n ON c.nid = n.id
        WHERE c.queue >= 2 AND c.ivl >= 21
        """;

    public Set<String> extractKnownKanji(MultipartFile apkg) throws IOException, SQLException {
        Path tempDb = Files.createTempFile("kagura-anki-", ".sqlite");
        try {
            extractCollectionDb(apkg, tempDb);
            return queryKnownKanji(tempDb);
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

    private Set<String> queryKnownKanji(Path dbPath) throws SQLException {
        Set<String> known = new HashSet<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            PreparedStatement ps = conn.prepareStatement(MATURE_QUERY);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String flds = rs.getString(1);
                if (flds != null) extractKanji(flds, known);
            }
        }
        return known;
    }

    private void extractKanji(String text, Set<String> sink) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                sink.add(String.valueOf(c));
            }
        }
    }
}
