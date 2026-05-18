package com.khmori.kagura.service;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final UserRepository userRepo;
    private final UserVocabRepository vocabRepo;
    private final UserKanjiRepository kanjiRepo;
    private final CompoundRepository compoundRepo;

    @Transactional
    public SyncResponse sync(SyncRequest req) {
        User user = userRepo
            .findByProviderAndProviderUserId(req.provider, req.providerUserId)
            .orElseThrow();
        FieldMapping mapping = new FieldMapping(user.getFieldMapping());

        for (IncomingNote note : req.notes) {
            if (!mapping.hasModel(note.ankiModelName)) {
                log.warn("no field mapping for model '{}'; skipping note {}",
                        note.ankiModelName, note.ankiNoteId);
                continue;
            }
            upsertVocab(user, note, mapping);
        }
        return new SyncResponse();
    }

    private void upsertVocab(User user, IncomingNote note, FieldMapping mapping) {
        UserVocab vocab = vocabRepo
            .findByUserIdAndAnkiNoteId(user.getId(), note.ankiNoteId)
            .orElseGet(UserVocab::new);

        String expression = slotValue(note, mapping, "expression");

        vocab.setUser(user);
        vocab.setAnkiNoteId(note.ankiNoteId);
        vocab.setAnkiModelName(note.ankiModelName);
        vocab.setExpression(expression);
        vocab.setSentenceFilled(slotFilled(note, mapping, "sentence"));
        vocab.setExpressionAudioFilled(slotFilled(note, mapping, "expressionAudio"));
        vocab.setSentenceAudioFilled(slotFilled(note, mapping, "sentenceAudio"));
        vocab.setImageFilled(slotFilled(note, mapping, "image"));
        vocab.setCompound(compoundRepo.findByCompound(expression).orElse(null));
        vocab.setRetentionStatus(RetentionStatus.NEW);
        vocab.setCards(note.cards);
        vocab.setFields(note.fields);
        vocab.setLastSyncedAt(Instant.now());
        vocabRepo.save(vocab);
    }

    private static String slotValue(IncomingNote note, FieldMapping mapping, String slot) {
        return mapping.resolveSlot(note.ankiModelName, slot)
                .map(fieldName -> fieldValue(note, fieldName))
                .orElse("");
    }

    // answers "is this slot missing from the user's anki card?"
    private static boolean slotFilled(IncomingNote note, FieldMapping mapping, String slot) {
        return !slotValue(note, mapping, slot).isEmpty();
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
