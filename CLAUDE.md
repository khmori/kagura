# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Kagura is a Japanese learner's dashboard that syncs with Anki via AnkiConnect, computes kanji/vocab proficiency from SRS data, estimates JLPT/Kanken level, and lets users browse their knowledge inventory and discover new words. See the project spec (in conversation context / docs) for the full MVP plan.

**Status:** mid-pivot. The repo originally implemented a kanji graph visualization (see commits `2f8a415`, `6fecd84`). The frontend is being rewritten and the schema is being extended for the Anki-sync dashboard. Existing kanji/compound ingestion is being kept; graph edges/scoring and the old DTO/recommendation-service work is parked.

## Commands (run from repo root)

- `make run` — start Spring Boot dev server (`./mvnw spring-boot:run` in `backend/`)
- `make test` — run all backend tests
- `make test-one CLASS=SomeTest` — run a single test class
- `make build` — package jar, skip tests
- `make compile` / `make clean`

The backend requires `DB_URL`, `DB_USER`, `DB_PASSWORD` env vars (Postgres). Schema is auto-applied from `backend/src/main/resources/schema.sql` (`spring.sql.init.mode=always`).

## Architecture

### Browser-as-bridge

The React frontend is the bridge between AnkiConnect (`localhost:8765`, user's machine) and the deployed Spring Boot backend. The backend never calls AnkiConnect directly. Sync flow: frontend pulls notes/cards/reviews from AnkiConnect → POSTs a processed snapshot to `/api/sync` → backend persists and computes proficiency → returns dashboard data. "Add to Anki" calls `addNote` directly from the browser.

### Backend layout (`backend/src/main/java/com/khmori/kagura/`)

- `entity/` — JPA entities. `Kanji` ↔ `Compound` is a `@ManyToMany` joined via `kanji_compounds`. Postgres-specific column types are used (`TEXT[]`, `jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`); avoid changing these without keeping `schema.sql` in sync.
- `repository/` — Spring Data JPA interfaces.
- `seed/DataSeeder.java` — `CommandLineRunner` that ingests `dicts/kanjidic2-en-3.6.2.json` (kanji metadata) and `dicts/jmdict-eng-3.6.2.json` (compounds, currently 2-kanji-only via `isNiji`) on first boot. Idempotent: skips if `kanji` table already has rows. The kanji↔compound join pass is per-compound and known-slow (TODO in the file).
- `KaguraApplication.java` — entry point.

Schema source of truth is `backend/src/main/resources/schema.sql`, applied alongside Hibernate's entity mapping. When adding tables (e.g. `users`, `user_vocab`, `user_kanji` per the MVP plan), update both.

### Static vs user data

- **Static (bundled, seeded once):** `kanji`, `compounds`, `kanji_compounds`. Sourced from KANJIDIC + JMDICT in `dicts/`.
- **User data (to be built):** `users`, `user_vocab` (Anki card state: interval, lapses, reps, factor, queue, retention status), `user_kanji` (proficiency score derived from known compounds). The existing `users` / `user_stats` tables in `schema.sql` are placeholders from the prior design and will be replaced.

### Proficiency model (planned)

- Card retention: `KNOWN` = interval > 21d AND lapses < 3; `SHAKY` = card exists but below threshold; `NEW` = unseen.
- Kanji proficiency = fraction of common compounds containing that kanji that the user knows.
- Estimated JLPT/Kanken level = highest level where user knows ≥80% of that level's kanji/vocab.

## Card enrichment (Nadeshiko)

Planned feature: for cards missing a sentence or audio field, let the user fetch examples from the Nadeshiko API (anime screenshots + audio + example sentences indexed by Japanese word). This lives primarily in the **Browse view** (enriching existing cards), not Discover (which is for words not in the deck).

Flow, same browser-as-bridge pattern as sync:
- Frontend calls Nadeshiko directly if CORS allows; otherwise add a thin backend proxy (`GET /api/nadeshiko/search?q=…`) that forwards + caches.
- User picks a result → frontend calls AnkiConnect `updateNoteFields` (and `storeMediaFile` for audio/images) directly.
- Backend learns about the new field state on the next `/api/sync`. No special write endpoint.

**Schema implication for `user_vocab` (do this when designing the table, not later):** store the per-field empty/non-empty state as a map keyed by Anki field name, not just two booleans (`has_sentence_field`, `has_audio_field`). The spec's "fields likely named sentence/example/audio" heuristic will be wrong for some users' note types, so users will eventually need a settings UI to map their own fields. Building this in from day one avoids a re-sync migration later.

Before building: verify Nadeshiko's API terms re: rate limits and redistribution of media into user decks.

## Frontend

Not yet scaffolded on this branch. Target stack: React + TypeScript. Two top-level routes planned: `/` (Dashboard: summary stats + filterable browse table) and `/discover` (recommendations + search + Add to Anki). Tag color system is consistent across views: green=known, yellow=shaky, gray=new/not in deck, red=missing field.

## Conventions

- Package root: `com.khmori.kagura`.
- Lombok is enabled (`@Data` on entities) — annotation processing is configured in `pom.xml`.
- Spring Boot 4.0.4, Java 17.
- Dictionary files live in `dicts/` at repo root and are referenced with relative paths from the backend working dir (`../dicts/...`); `make run` `cd`s into `backend/` first, so that path resolves correctly. Don't move the dicts or change the working dir without updating `DataSeeder`.
