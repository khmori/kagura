package com.khmori.kagura.seed;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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
    public void run(String... args) throws Exception {
        kanjiRepository.deleteAll();
        if (kanjiRepository.count() > 0) {
            System.out.println("Database already seeded.");
            return;
        }

        seedKanji();
        seedCompounds();
        seedKanjiCompounds();
    }

    private void seedKanji() throws Exception {
        JsonNode root = mapper.readTree(new File(KANJIDIC_PATH));
        JsonNode characters = root.get("characters");

        int counter = 0;
        for (JsonNode character : characters) {
            Kanji kanji = new Kanji();
            if (counter > 0) return;

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

                if (type.equals("ja_on")) onReadings.add(value);
                else if (type.equals("ja_kun")) kunReadings.add(value);
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

                if (type.equals("classical")) kanji.setRadicalClassical(value);
                else if (type.equals("nelson_c")) kanji.setRadicalNelson(value);
            }

            kanjiRepository.save(kanji);
        }
    }

    private void seedCompounds() throws Exception {
        JsonNode root = mapper.readTree(new File(JMDICT_PATH));
    }

    private void seedKanjiCompounds() {

    }

    private void printNode(JsonNode node) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            System.out.println("Property Name: " + fieldName);
        }

    }
}