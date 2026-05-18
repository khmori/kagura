package com.khmori.kagura.service;

import java.util.Map;
import java.util.Optional;

/**
 * A wrapper class around the user.field_mapping JSONB blob.
 *
 *   { "<modelName>": { "<slot>": "<anki field name>", ... }, ... }
 *
 * Example:
 *   { "Mining-JP": { "expression": "front", "sentence": "Sentence", ... },
 *     "Core 2k":   { "expression": "Expression", "sentence": "Example", ... } }
 * 
 */
public class FieldMapping {
    // { "<modelName>": { "<slot>": "<anki field name>", ... }, ... }
    private final Map<String, Map<String, String>> byModel;

    public FieldMapping(Map<String, Map<String, String>> raw) {
        this.byModel = raw == null ? Map.of() : raw;
    }

    // answers "what field name in this user's note type corresponds to this slot"
    public Optional<String> resolveSlot(String modelName, String slot) {
        Map<String, String> slots = byModel.get(modelName);
        if (slots == null) return Optional.empty();
        return Optional.ofNullable(slots.get(slot));
    }

    // answers "does this user have any field-slot mapping for this note type?"
    public boolean hasModel(String modelName) {
        return byModel.containsKey(modelName);
    }
}
