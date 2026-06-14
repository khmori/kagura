package com.khmori.kagura.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.khmori.kagura.dto.ExampleSentenceDto;
import com.khmori.kagura.dto.KanjiDetailsDto;
import com.khmori.kagura.dto.RecommendedWordDto;
import com.khmori.kagura.dto.SyncRequest;
import com.khmori.kagura.dto.SyncResponse;
import com.khmori.kagura.dto.UserKanjiDto;
import com.khmori.kagura.entity.User;
import com.khmori.kagura.repository.UserRepository;
import com.khmori.kagura.service.JitenService;
import com.khmori.kagura.service.RecommendationService;
import com.khmori.kagura.service.SyncService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SyncController {
    private final SyncService syncService;
    private final JitenService jitenService;
    private final RecommendationService recommendationService;
    private final UserRepository userRepository;

    @PostMapping("/sync")
    public SyncResponse sync(@RequestBody SyncRequest req) {
        return syncService.sync(req);
    }

    @GetMapping("/user-kanji")
    public List<UserKanjiDto> getUserKanji() {
        return syncService.getUserKanji();
    }

    @GetMapping("/kanji/{character}")
    public KanjiDetailsDto getKanjiDetails(@PathVariable String character) {
        return syncService.getKanjiDetails(character);
    }

    @GetMapping("/sentences")
    public List<ExampleSentenceDto> getSentences(@RequestParam String word) {
        return jitenService.fetchSentences(word);
    }

    @GetMapping("/recommended-words")
    public List<RecommendedWordDto> getRecommendedWords(@RequestParam(defaultValue = "20") int limit) {
        User user = userRepository.findByProviderAndProviderUserId("manual", "test-1").orElseThrow();
        return recommendationService.getRecommendedWords(user.getId(), user.getStudyMode(), user.getTargetLevel(), limit);
    }
}
