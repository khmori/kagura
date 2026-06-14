package com.khmori.kagura.seed;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.khmori.kagura.entity.Kanji;
import com.khmori.kagura.entity.User;
import com.khmori.kagura.entity.Word;
import com.khmori.kagura.repository.KanjiRepository;
import com.khmori.kagura.repository.UserRepository;
import com.khmori.kagura.repository.WordRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;

@Component
public class DataSeeder implements CommandLineRunner {
    private static final String KANJIDIC_PATH = "../dicts/kanjidic2-en-3.6.2.json";
    private static final String JMDICT_PATH = "../dicts/jmdict-eng-3.6.2.json";
    private static final String JLPT_LEVEL_PATH = "../dicts/jlpt_level.json";
    private static final String KANKEN_LEVEL_PATH = "../dicts/kanji_kentei_level.json";
    private static final String INNOCENT_CORPUS_DIR = "../dicts/innocent_corpus";

    private final KanjiRepository kanjiRepository;
    private final WordRepository wordRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataSeeder(KanjiRepository kanjiRepository, WordRepository wordRepository, UserRepository userRepository, EntityManager entityManager) {
        this.kanjiRepository = kanjiRepository;
        this.wordRepository = wordRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        seedTestUser();

        if (kanjiRepository.count() > 0) {
            System.out.println("Kanji table already seeded.");
        } else {
            kanjiRepository.deleteAll();
            kanjiRepository.flush();
            wordRepository.deleteAll();
            wordRepository.flush();
            seedKanji();
            seedWords();
            seedKanjiWords();
        }

        seedLevels();
        seedFrequency();
    }

    private void seedKanji() throws Exception {
        System.out.println("Seeding kanji table...");

        JsonNode root = mapper.readTree(new File(KANJIDIC_PATH));
        JsonNode characters = root.get("characters");
        int kanjiCount = 0;

        for (JsonNode character : characters) {
            Kanji kanji = new Kanji();

            // Kanji
            String literal = character.get("literal").asText();
            kanji.setKanji(literal);

            // Readings and meanings
            JsonNode groups = character.path("readingMeaning").path("groups");
            JsonNode firstGroup = groups.get(0);

            List<String> onReadings = new ArrayList<>();
            List<String> kunReadings = new ArrayList<>();

            for (JsonNode reading : firstGroup.path("readings")) {
                String type = reading.get("type").asText();
                String value = reading.get("value").asText();

                if (type.equals("ja_on"))
                    onReadings.add(value);
                else if (type.equals("ja_kun"))
                    kunReadings.add(value);
            }
            kanji.setOnReading(onReadings.toArray(new String[0]));
            kanji.setKunReading(kunReadings.toArray(new String[0]));

            List<String> meanings = new ArrayList<>();
            for (JsonNode meaning : firstGroup.path("meanings")) {
                meanings.add(meaning.get("value").asText());
            }
            kanji.setMeaning(meanings.toArray(new String[0]));

            // Grade
            JsonNode misc = character.path("misc");

            JsonNode gradeNode = misc.path("grade");
            if (!gradeNode.isNull() && !gradeNode.isMissingNode()) {
                kanji.setGrade(gradeNode.asInt());
            }

            // JLPT level (old)
            JsonNode jlptNode = misc.path("jlptLevel");
            if (!jlptNode.isNull() && !jlptNode.isMissingNode()) {
                kanji.setJlptLevel(jlptNode.asInt());
            }

            // Stroke count
            kanji.setStrokeCount(misc.path("strokeCounts").get(0).asInt());

            // Frequency
            kanji.setFrequency(misc.path("frequency").asInt());

            // Radicals
            for (JsonNode radical : character.path("radicals")) {
                String type = radical.get("type").asText();
                int value = radical.get("value").asInt();

                if (type.equals("classical"))
                    kanji.setRadicalClassical(value);
                else if (type.equals("nelson_c"))
                    kanji.setRadicalNelson(value);
            }

            entityManager.persist(kanji);
            if (++kanjiCount % 500 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();

        System.out.println("Kanji table seeded.");
    }

    private void seedWords() throws Exception {
        System.out.println("Seeding word table...");

        JsonNode root = mapper.readTree(new File(JMDICT_PATH));
        JsonNode jmdictWords = root.path("words");
        Set<String> seen = new HashSet<>();
        int entryCount = 0;
        int savedCount = 0;

        for (JsonNode entry : jmdictWords) {
            if (++entryCount % 20000 == 0) {
                System.out.printf("  ...processed %d entries, %d unique words%n", entryCount, seen.size());
            }
            List<String> meanings = new ArrayList<>();
            for (JsonNode sense : entry.get("sense")) {
                for (JsonNode gloss : sense.get("gloss")) {
                    meanings.add(gloss.get("text").asText());
                }
            }
            String[] meaningArr = meanings.toArray(new String[0]);

            JsonNode kanjiForms = entry.get("kanji");
            JsonNode kanaForms = entry.get("kana");

            if (kanjiForms != null && kanjiForms.size() > 0) {
                for (JsonNode kanjiForm : kanjiForms) {
                    String text = kanjiForm.get("text").asText();
                    if (!seen.add(text)) continue;

                    List<String> readings = new ArrayList<>();
                    for (JsonNode kana : kanaForms) {
                        for (JsonNode target : kana.get("appliesToKanji")) {
                            String t = target.asText();
                            if (t.equals("*") || t.equals(text)) {
                                readings.add(kana.get("text").asText());
                                break;
                            }
                        }
                    }

                    Word word = new Word();
                    word.setWord(text);
                    word.setReading(readings.toArray(new String[0]));
                    word.setMeaning(meaningArr);
                    word.setCommon(kanjiForm.get("common").asBoolean());
                    entityManager.persist(word);
                    if (++savedCount % 1000 == 0) {
                        entityManager.flush();
                        entityManager.clear();
                    }
                }
            } else if (kanaForms != null && kanaForms.size() > 0) {
                // Kana-only entry — the kana text IS the word.
                for (JsonNode kanaForm : kanaForms) {
                    String text = kanaForm.get("text").asText();
                    if (!seen.add(text)) continue;

                    Word word = new Word();
                    word.setWord(text);
                    word.setReading(new String[] { text });
                    word.setMeaning(meaningArr);
                    word.setCommon(kanaForm.get("common").asBoolean());
                    entityManager.persist(word);
                    if (++savedCount % 1000 == 0) {
                        entityManager.flush();
                        entityManager.clear();
                    }
                }
            }
        }

        entityManager.flush();
        entityManager.clear();
        System.out.println("Word table seeded.");
    }

    private void seedKanjiWords() {
        System.out.println("Seeding kanji-words join table...");

        Map<String, Kanji> kanjiByChar = new HashMap<>();
        for (Kanji k : kanjiRepository.findAll()) {
            kanjiByChar.put(k.getKanji(), k);
        }

        List<Word> words = wordRepository.findAll();
        int total = words.size();
        int processed = 0;

        for (Word word : words) {
            String text = word.getWord();
            for (int i = 0; i < text.length(); i++) {
                Kanji kanji = kanjiByChar.get(String.valueOf(text.charAt(i)));
                if (kanji != null) {
                    kanji.getWords().add(word);
                }
            }
            if (++processed % 5000 == 0) {
                System.out.printf("  ...joined %d / %d words%n", processed, total);
            }
        }

        System.out.println("Flushing kanji-words join rows...");
        kanjiRepository.saveAll(kanjiByChar.values());
        System.out.println("Kanji-words seeded.");
    }

    // Assigns JLPT (N5–N1) and Kanken (10–1) levels to each kanji in the DB.
    // Source: JSON files that map level names to character lists.
    // KANJIDIC only has old 4-level JLPT data, so we wipe it and re-seed with
    // the modern 5-level system. Kanken levels are new (no prior data to wipe).
    // Runs once — skips on subsequent boots if kanken_level is already populated.
    private void seedLevels() throws Exception {
        if (kanjiRepository.countByKankenLevelIsNotNull() > 0) {
            System.out.println("Kanji levels already seeded.");
            return;
        }

        System.out.println("Seeding kanji levels...");

        // Load all kanji into a lookup map (kanji string -> kanji DB entity), wipe stale KANJIDIC JLPT values
        Map<String, Kanji> kanjiByChar = new HashMap<>();
        for (Kanji k : kanjiRepository.findAll()) {
            k.setJlptLevel(null);
            kanjiByChar.put(k.getKanji(), k);
        }

        JsonNode jlptRoot = mapper.readTree(new File(JLPT_LEVEL_PATH));
        for (JsonNode group : jlptRoot.get("groups")) {
            String name = group.get("name").asText();
            int level = Integer.parseInt(name.replaceAll("\\D+", ""));
            String chars = group.get("characters").asText();
            for (int i = 0; i < chars.length(); i++) {
                Kanji k = kanjiByChar.get(String.valueOf(chars.charAt(i)));
                if (k != null) k.setJlptLevel(level);
            }
        }

        JsonNode kankenRoot = mapper.readTree(new File(KANKEN_LEVEL_PATH));
        for (JsonNode group : kankenRoot.get("groups")) {
            String name = group.get("name").asText();
            double level = parseKankenLevel(name);
            String chars = group.get("characters").asText();
            for (int i = 0; i < chars.length(); i++) {
                Kanji k = kanjiByChar.get(String.valueOf(chars.charAt(i)));
                if (k != null) k.setKankenLevel(level);
            }
        }

        kanjiRepository.saveAll(kanjiByChar.values());
        System.out.println("Kanji levels seeded.");
    }

    // "Level 準2 Kanji" → 2.5, "Level 準1 Kanji" → 1.5, "Level 10 Kanji" → 10.0
    private double parseKankenLevel(String groupName) {
        if (groupName.contains("準2")) return 2.5;
        if (groupName.contains("準1")) return 1.5;
        return Double.parseDouble(groupName.replaceAll("\\D+", ""));
    }

    private void seedFrequency() throws Exception {
        if (wordRepository.countByFrequencyRankIsNotNull() > 0) {
            System.out.println("Word frequency already seeded.");
            return;
        }

        System.out.println("Seeding word frequency from Innocent Corpus...");

        File dir = new File(INNOCENT_CORPUS_DIR);
        File[] bankFiles = dir.listFiles((d, name) -> name.startsWith("term_meta_bank_") && name.endsWith(".json"));
        if (bankFiles == null || bankFiles.length == 0) {
            System.out.println("No Innocent Corpus bank files found, skipping frequency seed.");
            return;
        }

        // Collect (word, occurrenceCount) from all bank files
        List<Object[]> entries = new ArrayList<>();
        for (File f : bankFiles) {
            JsonNode bank = mapper.readTree(f);
            for (JsonNode entry : bank) {
                String word = entry.get(0).asText();
                long count = entry.get(2).asLong();
                entries.add(new Object[] { word, count });
            }
        }

        // Sort by occurrence count descending, assign rank
        entries.sort((a, b) -> Long.compare((long) b[1], (long) a[1]));

        Map<String, Integer> wordToRank = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            String word = (String) entries.get(i)[0];
            wordToRank.putIfAbsent(word, i + 1);
        }

        int matched = 0;
        List<Word> allWords = wordRepository.findAll();
        for (Word w : allWords) {
            Integer rank = wordToRank.get(w.getWord());
            if (rank != null) {
                w.setFrequencyRank(rank);
                matched++;
            }
        }

        wordRepository.saveAll(allWords);
        System.out.printf("Word frequency seeded: %d/%d corpus entries matched a word row.%n", matched, wordToRank.size());
    }

    // Test user for Pass 1 dev. Idempotent — re-uses existing row on subsequent boots.
    // Frontend hardcodes userId=1, which only holds on a freshly-created DB.
    private void seedTestUser() {
        String provider = "manual";
        String providerUserId = "test-1";
        boolean exists = userRepository.findAll().stream()
                .anyMatch(u -> provider.equals(u.getProvider())
                            && providerUserId.equals(u.getProviderUserId()));
        if (exists) {
            System.out.println("Test user already present.");
            return;
        }
        User u = new User();
        u.setEmail("test@example.com");
        u.setProvider(provider);
        u.setProviderUserId(providerUserId);
        u.setFieldMapping(Map.of());
        userRepository.save(u);
        System.out.println("Seeded test user with id=" + u.getId());
    }
}
