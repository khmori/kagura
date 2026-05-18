--------------- static reference data (seeded from JMDICT and KANIDIC) ---------------
CREATE TABLE IF NOT EXISTS kanji (
    id SERIAL PRIMARY KEY,
    kanji TEXT UNIQUE NOT NULL,
    on_reading TEXT[],
    kun_reading TEXT[],
    meaning TEXT[],
    grade INT,
    jlpt_level INT,
    stroke_count INT,
    frequency INT,
    radical_classical INT,
    radical_nelson INT,
    metadata JSONB          -- extensible: strokes SVGs, media usage, etc.
);

CREATE TABLE IF NOT EXISTS words (
    id SERIAL PRIMARY KEY,
    word TEXT UNIQUE NOT NULL,
    reading TEXT[],
    meaning TEXT[],
    common BOOLEAN DEFAULT FALSE,
    jlpt INT
);

CREATE TABLE IF NOT EXISTS kanji_words (
    kanji_id INT REFERENCES kanji(id),
    word_id INT REFERENCES words(id),
    PRIMARY KEY (kanji_id, word_id)
);

------------------------------ user data ------------------------------
CREATE TABLE IF NOT EXISTS users (
    id               SERIAL PRIMARY KEY,
    email            TEXT NOT NULL,
    provider         TEXT NOT NULL,                  -- 'google' | 'discord' | 'github'
    provider_user_id TEXT NOT NULL,                  -- stable id from the OAuth provider
    -- Per-model field mapping populated via the Settings UI.
    -- { "<modelName>": { "expression": "front", "sentence": "Sentence", "audio": "Audio" }, ... }
    field_mapping    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

-- one row per Anki note the user has synced
-- Cards (the scheduling units) collapse into this row — usually 1:1, sometimes N:1.
CREATE TABLE IF NOT EXISTS user_vocab (
    id               SERIAL PRIMARY KEY,
    user_id          INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    anki_note_id     BIGINT NOT NULL,
    anki_model_name  TEXT NOT NULL,                  -- needed to pick the right field mapping

    -- extracted via the user's field_mapping at sync time
    expression              TEXT NOT NULL,                  -- join key against words.word
    sentence_filled         BOOLEAN NOT NULL DEFAULT FALSE,
    expression_audio_filled BOOLEAN NOT NULL DEFAULT FALSE,
    sentence_audio_filled   BOOLEAN NOT NULL DEFAULT FALSE,
    image_filled            BOOLEAN NOT NULL DEFAULT FALSE,

    -- reference words table for JMDICT readings/meanings/etc.
    word_id          INT REFERENCES words(id) ON DELETE SET NULL,

    -- per-note proficiency status, derived from the note's card(s) by the proficiency engine
    -- KNOWN | SHAKY | NEW | SUSPENDED
    retention_status TEXT NOT NULL,

    -- raw card scheduling data, one entry per card
    -- [{ cardId, interval, lapses, reps, factor, queue, type, due }, ...]
    cards            JSONB NOT NULL,

    -- all field values, copied verbatim
    -- lets us re-derive fields when user changes field mapping
    fields           JSONB NOT NULL,

    last_synced_at   TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (user_id, anki_note_id)
);

CREATE INDEX IF NOT EXISTS user_vocab_user_status     ON user_vocab(user_id, retention_status);
CREATE INDEX IF NOT EXISTS user_vocab_user_word       ON user_vocab(user_id, word_id);
CREATE INDEX IF NOT EXISTS user_vocab_user_expression ON user_vocab(user_id, expression);

-- per-kanji proficiency
CREATE TABLE IF NOT EXISTS user_kanji (
    id                SERIAL PRIMARY KEY,
    user_id           INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kanji_id          INT NOT NULL REFERENCES kanji(id) ON DELETE CASCADE,

    proficiency_score DOUBLE PRECISION NOT NULL,
    known             BOOLEAN NOT NULL,

    last_computed_at  TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (user_id, kanji_id)
);

CREATE INDEX IF NOT EXISTS user_kanji_user_known ON user_kanji(user_id, known);
