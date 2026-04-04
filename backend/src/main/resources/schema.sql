CREATE TABLE kanji (
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

CREATE TABLE compounds (
    id SERIAL PRIMARY KEY,
    compound TEXT UNIQUE NOT NULL,
    reading TEXT[],
    meaning TEXT[],
    common BOOLEAN DEFAULT FALSE,
    jlpt INT
);

CREATE TABLE kanji_compounds (
    kanji_id INT REFERENCES kanji(id), 
    compound_id INT REFERENCES compounds(id),
    PRIMARY KEY (kanji_id, compound_id)
);

CREATE TABLE users (
    id SERIAL PRIMARY KEY
);

CREATE TABLE user_stats (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id),
    kanji_id INT REFERENCES kanji(id),
    mastery DOUBLE PRECISION,
    UNIQUE (user_id, kanji_id)
);