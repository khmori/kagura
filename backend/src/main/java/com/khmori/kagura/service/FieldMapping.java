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
    private final Map<String, Map<String, String>> modelFieldMappings;

    public FieldMapping(Map<String, Map<String, String>> raw) {
        this.modelFieldMappings = raw == null ? Map.of() : raw;
    }

    // answers "what field name in this user's note type corresponds to this slot"

    // Get the field name in the user's note type that corresponds to the given canonical slot
    // e.g. given canonical slot name "expression", return field name "front"
    public Optional<String> resolveSlot(String modelName, String slot) {
        Map<String, String> slots = modelFieldMappings.get(modelName);
        if (slots == null) return Optional.empty();
        return Optional.ofNullable(slots.get(slot));
    }

    // Return true if there exists a slot-field mapping for the given note type
    public boolean hasModel(String modelName) {
        return modelFieldMappings.containsKey(modelName);
    }
}
