package com.khmori.kagura.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.khmori.kagura.dto.IncomingNote;
import com.khmori.kagura.dto.SyncRequest;
import com.khmori.kagura.dto.SyncResponse;
import com.khmori.kagura.entity.RetentionStatus;
import com.khmori.kagura.entity.User;
import com.khmori.kagura.entity.UserVocab;
import com.khmori.kagura.repository.CompoundRepository;
import com.khmori.kagura.repository.UserKanjiRepository;
import com.khmori.kagura.repository.UserRepository;
import com.khmori.kagura.repository.UserVocabRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SyncService {
    private final UserRepository userRepo;
    private final UserVocabRepository vocabRepo;
    private final UserKanjiRepository kanjiRepo;
    private final CompoundRepository compoundRepo;

    @Transactional
    public SyncResponse sync(SyncRequest req) {
        User user = userRepo.findById(req.userId).orElseThrow();
        for (IncomingNote note : req.notes) {
            upsertVocab(user, note);
        }
        return new SyncResponse();
    }

    private void upsertVocab(User user, IncomingNote note) {
        UserVocab vocab = vocabRepo
            .findByUserIdAndAnkiNoteId(user.getId(), note.ankiNoteId)
            .orElseGet(UserVocab::new);
        vocab.setUser(user);
        vocab.setAnkiNoteId(note.ankiNoteId);
        vocab.setAnkiModelName(note.ankiModelName);
        String expression = fieldValue(note, "front");
        vocab.setExpression(expression);
        vocab.setSentenceFilled(!fieldValue(note, "sentence").isEmpty());
        vocab.setAudioFilled(!fieldValue(note, "audio").isEmpty());
        vocab.setCompound(compoundRepo.findByCompound(expression).orElse(null));
        vocab.setRetentionStatus(RetentionStatus.NEW);
        vocab.setCards(note.cards);
        vocab.setFields(note.fields);
        vocab.setLastSyncedAt(Instant.now());
        vocabRepo.save(vocab);
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