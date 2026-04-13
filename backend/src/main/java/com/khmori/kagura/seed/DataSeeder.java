package com.khmori.kagura.seed;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.khmori.kagura.entity.Compound;
import com.khmori.kagura.entity.Kanji;
import com.khmori.kagura.repository.CompoundRepository;
import com.khmori.kagura.repository.KanjiRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class DataSeeder implements CommandLineRunner {
    private static final String KANJIDIC_PATH = "../dicts/kanjidic2-en-3.6.2.json";
    private static final String JMDICT_PATH = "../dicts/jmdict-eng-3.6.2.json";

    private final KanjiRepository kanjiRepository;
    private final CompoundRepository compoundRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataSeeder(KanjiRepository kanjiRepository, CompoundRepository compoundRepository) {
        this.kanjiRepository = kanjiRepository;
        this.compoundRepository = compoundRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (kanjiRepository.count() > 0) {
            System.out.println("Kanji table already seeded.");
            return;
        }
        kanjiRepository.deleteAll();
        kanjiRepository.flush();
        compoundRepository.deleteAll();
        compoundRepository.flush();
        seedKanji();
        seedCompounds();
        seedKanjiCompounds();
    }

    private void seedKanji() throws Exception {
        System.out.println("Seeding kanji table...");

        JsonNode root = mapper.readTree(new File(KANJIDIC_PATH));
        JsonNode characters = root.get("characters");

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

            kanjiRepository.save(kanji);
        }

        System.out.println("Kanji table seeded.");
    }

    private void seedCompounds() throws Exception {
        System.out.println("Seeding compound table...");

        JsonNode root = mapper.readTree(new File(JMDICT_PATH));
        JsonNode words = root.path("words");
        Set<String> seen = new HashSet<>();

        for (JsonNode word : words) {
            JsonNode kanjiEntry = extractNiji(word.get("kanji"));
            if (kanjiEntry == null) continue;

            String kanjiText = kanjiEntry.get("text").asText();
            if (!seen.add(kanjiText)) continue;

            Compound compound = new Compound();

            // Compound
            compound.setCompound(kanjiText);

            // Readings
            List<String> readings = new ArrayList<>();
            for (JsonNode kana : word.get("kana")) {
                JsonNode appliesTo = kana.get("appliesToKanji");
                for (JsonNode target : appliesTo) {
                    if (target.asText().equals("*") || target.asText().equals(kanjiText)) {
                        readings.add(kana.get("text").asText());
                    }
                }
            }
            compound.setReading(readings.toArray(new String[0]));

            // Meanings
            List<String> meanings = new ArrayList<>();
            for (JsonNode sense : word.get("sense")) {
                for (JsonNode gloss : sense.get("gloss")) {
                    meanings.add(gloss.get("text").asText());
                }
            }
            compound.setMeaning(meanings.toArray(new String[0]));

            // Common
            compound.setCommon(kanjiEntry.get("common").asBoolean());

            compoundRepository.save(compound);
        }

        System.out.println("Compound table seeded.");
    }

    // TODO: optimize (map kanji : compounds for less DB queries)
    private void seedKanjiCompounds() {
        System.out.println("Seeding kanji-compounds join table...");
    
        List<Compound> compounds = compoundRepository.findAll();

        int counter = 0;

        for (Compound compound : compounds) {
            String text = compound.getCompound();

            for (int i = 0; i < text.length(); i++) {
                String kanjiChar = String.valueOf(text.charAt(i));
                
                Optional<Kanji> opt = kanjiRepository.findByKanji(kanjiChar);
                if (!opt.isPresent()) {
                    continue;
                }

                Kanji kanji = opt.get();
                kanji.getCompounds().add(compound);
                kanjiRepository.save(kanji);

                System.out.println(compound.getCompound() + ": " + kanji.getKanji());
            }

        }

        System.out.println("Kanji-compounds seeded.");
    }

    private JsonNode extractNiji(JsonNode kanjiEntries) {
        for (JsonNode entry : kanjiEntries) {
            if (isNiji(entry.get("text").asText()))
                return entry;
        }
        return null;
    }

    private boolean isNiji(String text) {
        return text.length() == 2
                && Character.UnicodeBlock.of(text.charAt(0)) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                && Character.UnicodeBlock.of(text.charAt(1)) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS;
    }
}