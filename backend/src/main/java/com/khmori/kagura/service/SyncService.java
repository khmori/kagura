package com.khmori.kagura.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.khmori.kagura.dto.IncomingNote;
import com.khmori.kagura.dto.SyncRequest;
import com.khmori.kagura.dto.SyncResponse;
import com.khmori.kagura.dto.UserKanjiDto;
import com.khmori.kagura.entity.RetentionStatus;
import com.khmori.kagura.entity.User;
import com.khmori.kagura.entity.UserVocab;
import com.khmori.kagura.repository.UserKanjiRepository;
import com.khmori.kagura.repository.UserRepository;
import com.khmori.kagura.repository.UserVocabRepository;
import com.khmori.kagura.repository.WordRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final UserRepository userRepo;
    private final UserVocabRepository userVocabRepo;
    private final UserKanjiRepository kanjiRepo;
    private final WordRepository wordRepo;

    @Transactional
    public SyncResponse sync(SyncRequest req) {
        User user = userRepo.findByProviderAndProviderUserId(req.provider, req.providerUserId).orElseThrow();
        FieldMapping mapping = new FieldMapping(user.getFieldMapping());

        for (IncomingNote note : req.notes) {
            if (!mapping.hasModel(note.ankiModelName)) {
                log.warn("no field mapping for model '{}'; skipping note {}", note.ankiModelName, note.ankiNoteId);
                continue;
            }
            upsertVocab(user, note, mapping); // update (or insert) the user_vocab table for the given note, for the given user
        }
        kanjiRepo.computeScoresForUser(user.getId());
        return new SyncResponse();
    }

    public List<UserKanjiDto> getUserKanji() {
        User user = userRepo.findByProviderAndProviderUserId("manual", "test-1").orElseThrow();
        return kanjiRepo.findByUserIdOrderByProficiencyScoreDesc(user.getId()).stream().map(uk -> {
            UserKanjiDto dto = new UserKanjiDto();
            dto.kanji = uk.getKanji().getKanji();
            dto.proficiencyScore = uk.getProficiencyScore();
            dto.known = uk.getKnown();
            return dto;
        }).toList();
    }

    private void upsertVocab(User user, IncomingNote note, FieldMapping mapping) {
        UserVocab userVocab = userVocabRepo.findByUserIdAndAnkiNoteId(user.getId(), note.ankiNoteId).orElseGet(UserVocab::new);
        String expression = getSlotValue(note, mapping, "expression");

        userVocab.setUser(user);
        userVocab.setAnkiNoteId(note.ankiNoteId);
        userVocab.setAnkiModelName(note.ankiModelName);
        userVocab.setExpression(expression);
        userVocab.setSentenceFilled(!getSlotValue(note, mapping, "sentence").isEmpty());
        userVocab.setExpressionAudioFilled(!getSlotValue(note, mapping, "expressionAudio").isEmpty());
        userVocab.setSentenceAudioFilled(!getSlotValue(note, mapping, "sentenceAudio").isEmpty());
        userVocab.setImageFilled(!getSlotValue(note, mapping, "image").isEmpty());
        userVocab.setWord(wordRepo.findByWord(expression).orElse(null));
        userVocab.setRetentionStatus(deriveStatus(note.cards));
        userVocab.setAvgInterval(computeAvgInterval(note.cards));
        userVocab.setCards(note.cards);
        userVocab.setFields(note.fields);
        userVocab.setLastSyncedAt(Instant.now());
        userVocabRepo.save(userVocab);
    }

    /**
     * Coarse per-word label for each vocab (note), derived from the note's cards (best status wins).
     * NEW: no cards or only new cards
     * SUSPENDED: all cards suspended
     * KNOWN: any "mature" (interval > 21 and lapses < 3) cards
     * SHAKY: anything else
     */
    private static RetentionStatus deriveStatus(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) return RetentionStatus.NEW;

        boolean anyActive = false;
        boolean anyStudied = false;

        for (Map<String, Object> card : cards) {
            if ((Integer) card.get("queue") == -1) continue; // suspended
            anyActive = true;
            if ((Integer) card.get("type") > 0) {
                anyStudied = true;
                if ((Integer) card.get("interval") > 21 && (Integer) card.get("lapses") < 3) {
                    return RetentionStatus.KNOWN; // mature
                }
            }
        }

        if (!anyActive) return RetentionStatus.SUSPENDED;
        if (anyStudied) return RetentionStatus.SHAKY;
        return RetentionStatus.NEW;
    }

    /**
     * Mean card.interval over seen, non-suspended cards (queue != -1 && type > 0).
     * If no qualifying cards, return null.
     */
    private static Double computeAvgInterval(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) return null;

        long sum = 0;
        int count = 0;
        for (Map<String, Object> card : cards) {
            if ((Integer) card.get("queue") != -1 && (Integer) card.get("type") > 0) {
                sum += (Integer) card.get("interval");
                count++;
            }
        }
        return count == 0 ? null : (double) sum / count;
    }

    // Get the value for the given slot name for the given user note (and field mapping)
    private static String getSlotValue(IncomingNote note, FieldMapping mapping, String slot) {
        return mapping.resolveSlot(note.ankiModelName, slot)
                .map(fieldName -> fieldValue(note, fieldName))
                .orElse("");
    }

    /** Anki's notesInfo returns each field as { value: "...", order: N }. */
    private static String fieldValue(IncomingNote note, String fieldName) {
        if (note.fields == null) return "";
        Object raw = note.fields.get(fieldName);
        if (!(raw instanceof Map<?, ?> field)) return "";
        Object value = field.get("value");
        return value instanceof String s ? s : "";
    }
}
