# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Kagura is a Japanese learner's dashboard that syncs with Anki via AnkiConnect, computes kanji/vocab proficiency from SRS data, estimates JLPT/Kanken level, and lets users browse their knowledge inventory and discover new words. See the project spec (in conversation context / docs) for the full MVP plan.

**Status:** mid-pivot. The repo originally implemented a kanji graph visualization (see commits `2f8a415`, `6fecd84`). The frontend is being rewritten and the schema is being extended for the Anki-sync dashboard. Existing kanji/word ingestion is being kept; graph edges/scoring and the old DTO/recommendation-service work is parked.

## Commands (run from repo root)

- `make run` — start Spring Boot dev server (`./mvnw spring-boot:run` in `backend/`)
- `make test` — run all backend tests
- `make test-one CLASS=SomeTest` — run a single test class
- `make build` — package jar, skip tests
- `make compile` / `make clean`

The backend requires `DB_URL`, `DB_USER`, `DB_PASSWORD` env vars (Postgres). Schema is auto-applied from `backend/src/main/resources/schema.sql` (`spring.sql.init.mode=always`).

Frontend: `cd frontend && npm run dev` (Vite on `:5173`). Backend CORS allows that origin only.

## Architecture

### Browser-as-bridge

The React frontend is the bridge between AnkiConnect (`localhost:8765`, user's machine) and the deployed Spring Boot backend. The backend never calls AnkiConnect directly. Sync flow: frontend pulls notes/cards from AnkiConnect → trims to the fields the backend reads → POSTs as `SyncRequest` to `/api/sync` → backend persists and (planned) computes proficiency → returns dashboard data. "Add to Anki" calls `addNote` directly from the browser.

### Backend layout (`backend/src/main/java/com/khmori/kagura/`)

- `entity/` — JPA entities. `Kanji` ↔ `Word` is a `@ManyToMany` joined via `kanji_words`. Postgres-specific column types are used (`TEXT[]`, `jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`); avoid changing these without keeping `schema.sql` in sync.
- `repository/` — Spring Data JPA interfaces.
- `dto/` — `SyncRequest` / `IncomingNote` / `SyncResponse`. Public fields, no getters/setters (Jackson binds them directly).
- `controller/SyncController.java` — `POST /api/sync`. `@CrossOrigin(origins = "http://localhost:5173")`.
- `service/SyncService.java` — upserts one `UserVocab` row per `IncomingNote`. Resolves fields by slot via `FieldMapping`; skips notes whose `modelName` has no mapping.
- `service/FieldMapping.java` — thin wrapper over `user.field_mapping`. `resolveSlot(modelName, slot)` is the only way backend code should look up field values.
- `seed/DataSeeder.java` — `CommandLineRunner` that ingests `dicts/kanjidic2-en-3.6.2.json` (kanji metadata) and `dicts/jmdict-eng-3.6.2.json` (all surface forms — every kanji form per entry, plus kana-only entries — deduped by text) on first boot. Also seeds a test user (`provider=manual`, `providerUserId=test-1`) with a hardcoded `Mining-JP` field mapping. Idempotent: skips kanji/word seed if `kanji` table already has rows; skips test user if it exists. The kanji↔word join pass loads all kanji into a `HashMap` once, accumulates `kanji.words.add(word)` in memory, and bulk-saves at the end.
- `KaguraApplication.java` — entry point.

Schema source of truth is `backend/src/main/resources/schema.sql`, applied alongside Hibernate's entity mapping. When adding tables (e.g. `user_kanji` is wired but unpopulated), update both.

### Static vs user data

- **Static (bundled, seeded once):** `kanji`, `words`, `kanji_words`. Sourced from KANJIDIC + JMDICT in `dicts/`.
- **User data:** `users` (OAuth identity + `field_mapping` JSONB), `user_vocab` (one row per synced Anki note: expression, per-slot fill booleans, raw fields/cards JSONB, retention status), `user_kanji` (proficiency score derived from known words — table + entity exist, not yet populated).

### Proficiency model (planned, not yet implemented)

- Card retention: `KNOWN` = interval > 21d AND lapses < 3; `SHAKY` = card exists but below threshold; `NEW` = unseen; `SUSPENDED` = all cards suspended. Enum exists at `entity/RetentionStatus.java`; `SyncService.upsertVocab` currently hardcodes `NEW`.
- Kanji proficiency = fraction of common words containing that kanji that the user knows.
- Estimated JLPT/Kanken level = highest level where user knows ≥80% of that level's kanji/vocab.

### Slot-based field mapping (load-bearing design)

Anki field names are user-defined per note type, so backend code must never hardcode them. Kagura defines its own **canonical slot names** and the user maps each slot to a field name in their note type (eventually via the Settings UI; for now the seeder hardcodes one mapping for the test user). The Yomitan pattern, applied to Anki.

**Canonical slots (MVP):** `expression` (join key against `words.word`, required), `reading`, `meaning`, `sentence`, `expressionAudio`, `sentenceAudio`, `image`. Single field name per slot; absent if the user's note type doesn't have one.

**Storage:** `users.field_mapping` is a JSONB column keyed by `modelName`:
```jsonc
{
  "Mining-JP": {
    "expression":      "front",
    "reading":         "Reading",
    "sentence":        "Sentence",
    "expressionAudio": "ExpressionAudio",
    "sentenceAudio":   "SentenceAudio"
  },
  "Core 2k":   { "expression": "Expression", "sentence": "Example", ... }
}
```

**Mixed-type decks are supported natively** — the mapping is keyed by note type, not deck. A single sync call resolves each note's fields independently via `FieldMapping.resolveSlot(modelName, slot)`. Unknown / unconfigured note types are skipped + logged, not errored.

**Backend never says** `note.fields.get("Sentence")`. It says `mapping.resolveSlot(modelName, "sentence")`. Only the Settings UI (and the seeder, for the test user) ever names a real Anki field. `SyncService` and downstream consumers operate on slot names.

**Per-slot fill state lives in one BOOLEAN column per slot on `user_vocab`** (`sentence_filled`, `expression_audio_filled`, `sentence_audio_filled`, `image_filled`). A single lumped `audio_filled` would lose the distinction between "missing word audio" and "missing sentence audio" that Nadeshiko enrichment needs. JSONB would have worked too but the slot list is small and stable, so real columns win on type safety, query simplicity, and entity clarity. Adding a new slot is one line in `schema.sql` + one field in `UserVocab`.

## Card enrichment (Nadeshiko)

Planned feature: for cards missing a sentence or audio field, let the user fetch examples from the Nadeshiko API (anime screenshots + audio + example sentences indexed by Japanese word). This lives primarily in the **Browse view** (enriching existing cards), not Discover (which is for words not in the deck).

Flow, same browser-as-bridge pattern as sync:
- Frontend calls Nadeshiko directly if CORS allows; otherwise add a thin backend proxy (`GET /api/nadeshiko/search?q=…`) that forwards + caches.
- User picks a result → frontend calls AnkiConnect `updateNoteFields` (and `storeMediaFile` for audio/images) directly.
- Backend learns about the new field state on the next `/api/sync`. No special write endpoint.

Enrichment reads e.g. `user_vocab.sentence_audio_filled == false`, then writes to whatever the user mapped that slot to (e.g. `"SentenceAudio"`) via `updateNoteFields`. The slot abstraction is what makes Nadeshiko work across users with different note types.

Before building: verify Nadeshiko's API terms re: rate limits and redistribution of media into user decks.

## Roadmap

Rough priority order from current state. Estimates are solo-dev "focused day" units — halve for easy, double for messy.

**P0 — unblock**
1. Fix `KEPT_NOTE_FIELDS` in `frontend/src/App.tsx` (5 min). Currently hardcodes `["front", "Sentence", "ExpressionAudio", "SentenceAudio"]` — matches the test user's `Mining-JP` mapping but will silently drop fields for any other note type. Temporary; goes away with Settings UI (derive from the user's `field_mapping`).

**P1 — close the sync loop**
2. `GET /api/users/me/field-mapping` endpoint (30 min). Trivial; useful once Settings UI exists.
3. Settings UI for field mapping (1–2 days). Fetches `modelNames` + `modelFieldNames` from AnkiConnect; user picks a field per canonical slot per note type; saves via `PUT /api/users/me/field-mapping`. Kill the hardcoded mapping in the seeder once this works.
4. Proficiency engine (1–2 days). `deriveStatus` from cards (~1 hr); `user_kanji` recompute SQL (most of the time, debug against hand-counted truth). See "Planned migrations" below for the algorithm.
5. Minimal dashboard UI (1–2 days). Table of `user_vocab` with retention colors, kanji grid colored by `known`. Ugly is fine.

**P2 — pay down**
6. Gzip the sync payload (30 min). Unfiltered notes hit the 50MB ceiling; gzip → ~5MB. `server.compression.enabled=true` on Spring.
7. Flyway/Liquibase (2–4 hrs). Already hit the `CREATE TABLE IF NOT EXISTS` trap once; do this before more schema changes.
8. Unit tests for `FieldMapping` + `SyncService` (half day). Cheap insurance on load-bearing code.

**Weakest estimates:** Settings UI (#3) can balloon if you start designing instead of building. Proficiency engine SQL (#4) could be a half-day if it clicks, 3 days if you fight JPQL — drop to native `@Query` early.

## Planned migrations / future work

- **Proficiency engine (Pass 2).** Currently `SyncService.upsertVocab` hardcodes `retention_status = NEW` and nothing populates `user_kanji`. Two pieces:
  1. **Derive `retention_status` from `cards`** in `upsertVocab`. Walk the cards list: `queue == -1` → suspended (skip); `interval > 21 && lapses < 3` → KNOWN; any live card otherwise → SHAKY; all-suspended → SUSPENDED; no cards → NEW. Anki queue values: `-1` suspended, `0` new, `1` learning, `2` review, `3` day-learning.
  2. **Recompute `user_kanji` at the end of `sync(...)`.** Per-user `INSERT ... ON CONFLICT DO UPDATE` from `kanji_words ⨝ words (common=TRUE) LEFT JOIN user_vocab`, grouped by `kanji_id`. `proficiency_score = known_count / common_word_count`, `known = score >= 0.5`. SQL-first via a `@Modifying @Query` on `UserKanjiRepository`; do not load into Java and recompute per-row. Threshold (0.5) and weighting of SHAKY (currently zero) are intentionally simple — tune after seeing real data.

  Order of work: (1) the real frontend → `/api/sync` round-trip already runs end-to-end with the test user; (2) implement `deriveStatus`; (3) implement the recompute query; (4) call it from `SyncService.sync` after the upsert loop.

## Frontend (`frontend/`)

Vite + React 19 + TypeScript. Single `App.tsx` with two buttons (`probe`, `sync`) — no routing, no UI scaffold yet. Target stack stays the same; planned routes: `/` (Dashboard: summary stats + filterable browse table) and `/discover` (recommendations + search + Add to Anki). Tag color system planned to be consistent across views: green=known, yellow=shaky, gray=new/not in deck, red=missing field.

The `anki()` helper in `App.tsx` wraps AnkiConnect's `{action, version, params}` JSON-RPC. `buildSyncRequest` is the canonical example of the bridge: pull from AnkiConnect, trim via `KEPT_NOTE_FIELDS` + `extractCardScheduling`, POST to backend.

**API boundary typing.** `response.json()` returns `Promise<any>` — using `as SomeType` is a compile-time-only assertion with no runtime check, so a malformed response silently produces `undefined`/`NaN` bugs downstream. Plan to adopt **zod** for runtime validation at every fetch boundary (`zod.parse(await res.json())`) and derive TS types via `z.infer<typeof Schema>` so one schema drives both. `as` is acceptable during prototyping (own backend, simple shapes — current state) but switch to zod before any third-party API integration (Nadeshiko) and before any UI renders nested response data directly (Dashboard, Discover). Shared schemas live in `frontend/src/types/` (or `lib/schemas/`) and mirror the backend DTOs in `com.khmori.kagura.dto`.

## Conventions

- Package root: `com.khmori.kagura`.
- Lombok is enabled (`@Data` on entities) — annotation processing is configured in `pom.xml`.
- Spring Boot 4.0.4, Java 17.
- Dictionary files live in `dicts/` at repo root and are referenced with relative paths from the backend working dir (`../dicts/...`); `make run` `cd`s into `backend/` first, so that path resolves correctly. Don't move the dicts or change the working dir without updating `DataSeeder`.
