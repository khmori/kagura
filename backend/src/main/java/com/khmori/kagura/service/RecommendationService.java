package com.khmori.kagura.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.khmori.kagura.dto.RecommendedWordDto;
import com.khmori.kagura.entity.UserKanji;
import com.khmori.kagura.repository.UserKanjiRepository;
import com.khmori.kagura.repository.WordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final WordRepository wordRepository;
    private final UserKanjiRepository userKanjiRepository;

    /**
     * Returns the highest-value words for the user to learn next.
     *
     * Delegates to {@link WordRepository#findRecommendedWords} for the scored ranking
     * (frequency × kanji familiarity), then enriches each result with a "reinforces"
     * list: the specific kanji in the word that the user currently knows at a shaky
     * level (proficiency 0.3–0.6). This tells the frontend which kanji benefit from
     * learning this word.
     */
    public List<RecommendedWordDto> getRecommendedWords(Integer userId, int limit) {
        List<Object[]> scoredWords = wordRepository.findRecommendedWords(userId, limit);

        List<UserKanji> shakyKanji = userKanjiRepository.findByUserIdOrderByProficiencyScoreDesc(userId)
                .stream()
                .filter(userKanji -> userKanji.getProficiencyScore() >= 0.3 && userKanji.getProficiencyScore() <= 0.6)
                .toList();

        List<RecommendedWordDto> recommendations = new ArrayList<>();
        for (Object[] wordRow : scoredWords) {
            RecommendedWordDto dto = new RecommendedWordDto();
            dto.id = (Integer) wordRow[0];
            dto.word = (String) wordRow[1];
            dto.reading = parseTextArray(wordRow[2]);
            dto.meaning = parseTextArray(wordRow[3]);
            dto.frequencyRank = (Integer) wordRow[4];
            dto.score = ((Number) wordRow[5]).doubleValue();

            List<String> reinforcedKanji = new ArrayList<>();
            for (UserKanji userKanji : shakyKanji) {
                if (dto.word.contains(userKanji.getKanji().getKanji())) {
                    reinforcedKanji.add(userKanji.getKanji().getKanji());
                }
            }
            dto.reinforces = reinforcedKanji.toArray(new String[0]);

            recommendations.add(dto);
        }
        return recommendations;
    }

    private String[] parseTextArray(Object raw) {
        if (raw == null) return new String[0];
        if (raw instanceof String[] arr) return arr;
        // Postgres TEXT[] comes back as {a,b,c} from native queries
        String s = raw.toString();
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length() - 1);
            if (s.isEmpty()) return new String[0];
            return s.split(",");
        }
        return new String[] { s };
    }
}
