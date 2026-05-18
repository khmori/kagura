package com.khmori.kagura.dto;

import java.util.List;
import java.util.Map;

public class IncomingNote {
    public Long ankiNoteId;
    public String ankiModelName;
    public Map<String, Object> fields;       // raw fields from Anki
    public List<Map<String, Object>> cards;  // [{ cardId, interval. lapses, ...}, ...]
}
