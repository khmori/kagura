package com.khmori.kagura.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.khmori.kagura.dto.ExampleSentenceDto;

@Service
public class JitenService {
    private static final Logger log = LoggerFactory.getLogger(JitenService.class);
    // 1=Anime, 4=Novel, 7=VisualNovel, 6=VideoGame, 5=NonFiction, 8=WebNovel
    private static final int[] MEDIA_TYPES = {1, 4, 7, 6, 5, 8};

    private final RestClient restClient;
    private final ConcurrentHashMap<String, List<ExampleSentenceDto>> cache = new ConcurrentHashMap<>();

    public JitenService(
            @Value("${jiten.api.base-url}") String baseUrl,
            @Value("${jiten.api.key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    public List<ExampleSentenceDto> fetchSentences(String word) {
        List<ExampleSentenceDto> cached = cache.get(word);
        if (cached != null) return cached;

        List<ExampleSentenceDto> result = lookupAndFetchSentences(word);
        cache.put(word, result);
        return result;
    }

    private List<ExampleSentenceDto> lookupAndFetchSentences(String word) {
        try {
            int[] wordRef = resolveJitenWordId(word);
            if (wordRef == null) return List.of();

            return fetchJitenSentences(wordRef[0], wordRef[1]);
        } catch (Exception e) {
            log.warn("jiten.moe lookup failed for '{}': {}", word, e.getMessage());
            return List.of();
        }
    }

    /** @return [wordId, readingIndex] or null if not found */
    private int[] resolveJitenWordId(String word) {
        Map<String, Object> response = restClient.get()
                .uri("/api/vocabulary/search?query={q}&limit=1", word)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) return null;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) return null;

        Map<String, Object> first = results.get(0);
        int wordId = ((Number) first.get("wordId")).intValue();
        int readingIndex = ((Number) first.get("readingIndex")).intValue();
        return new int[]{wordId, readingIndex};
    }

    private List<ExampleSentenceDto> fetchJitenSentences(int wordId, int readingIndex) {
        List<Map<String, Object>> rawSentences = null;
        for (int mediaType : MEDIA_TYPES) {
            rawSentences = restClient.post()
                    .uri("/api/vocabulary/{wordId}/{readingIndex}/random-example-sentences/{mediaType}",
                            wordId, readingIndex, mediaType)
                    .body(List.of())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (rawSentences != null && !rawSentences.isEmpty()) break;
        }

        if (rawSentences == null || rawSentences.isEmpty()) return List.of();

        List<ExampleSentenceDto> result = new ArrayList<>();
        for (Map<String, Object> raw : rawSentences) {
            ExampleSentenceDto dto = new ExampleSentenceDto();
            dto.sentence = (String) raw.get("text");
            dto.wordPosition = raw.get("wordPosition") != null
                    ? ((Number) raw.get("wordPosition")).intValue() : 0;
            dto.wordLength = raw.get("wordLength") != null
                    ? ((Number) raw.get("wordLength")).intValue() : 0;
            dto.difficulty = raw.get("difficulty") != null
                    ? ((Number) raw.get("difficulty")).floatValue()
                    : 0f;

            @SuppressWarnings("unchecked")
            Map<String, Object> parentDeck = (Map<String, Object>) raw.get("sourceDeckParent");
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceDeck = (Map<String, Object>) raw.get("sourceDeck");
            Map<String, Object> deckForTitle = parentDeck != null ? parentDeck : sourceDeck;
            if (deckForTitle != null) {
                String title = (String) deckForTitle.get("englishTitle");
                if (title == null) title = (String) deckForTitle.get("originalTitle");
                dto.source = title;
            }

            result.add(dto);
        }
        return result;
    }
}
