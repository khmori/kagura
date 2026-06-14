package com.khmori.kagura.dto;

import java.util.Map;

// User-editable config: deck choice + per-model field mapping.
// Returned by GET /api/users/me/config and accepted by PUT /api/users/me/config.
public class UserConfig {
    public String selectedDeck;
    public Map<String, Map<String, String>> fieldMapping;
    public String studyMode;
}
