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

### Slot-based field mapping (load-bearing design)

Anki field names are user-defined per note type, so backend code must never hardcode them. Kagura defines its own **canonical slot names** and the user maps each slot to a field name in their note type via the Settings UI. The Yomitan pattern, applied to Anki.

**Canonical slots (MVP):** `expression` (join key against `compounds.compound`, required), `reading`, `meaning`, `sentence`, `expressionAudio`, `sentenceAudio`, `image`. Single field name per slot; absent if the user's note type doesn't have one.

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

**Mixed-type decks are supported natively** — the mapping is keyed by note type, not deck. A single sync call resolves each note's fields independently via `mapping.forModel(note.ankiModelName)`. Unknown / unconfigured note types are skipped + logged, not errored.

**Backend never says** `note.fields.get("Sentence")`. It says `resolveSlot("sentence", note)`. Only the Settings UI (and the seeder, for the test user) ever names a real Anki field. `SyncService` and downstream consumers operate on slot names.

**Per-slot fill state lives in one BOOLEAN column per slot on `user_vocab`** (`sentence_filled`, `expression_audio_filled`, `sentence_audio_filled`, `image_filled`, etc.). Keep: the lumped `audio_filled` doesn't distinguish "missing word audio" from "missing sentence audio," which Nadeshiko needs. Reject: a single `fill_state JSONB` — the slot list is small and stable, so JSONB's flexibility is dressing up booleans in a map costume. Real columns give type safety, simpler queries, simpler Lombok entity. Adding a new slot is one line in `schema.sql` + one field in `UserVocab`.

## Card enrichment (Nadeshiko)

Planned feature: for cards missing a sentence or audio field, let the user fetch examples from the Nadeshiko API (anime screenshots + audio + example sentences indexed by Japanese word). This lives primarily in the **Browse view** (enriching existing cards), not Discover (which is for words not in the deck).

Flow, same browser-as-bridge pattern as sync:
- Frontend calls Nadeshiko directly if CORS allows; otherwise add a thin backend proxy (`GET /api/nadeshiko/search?q=…`) that forwards + caches.
- User picks a result → frontend calls AnkiConnect `updateNoteFields` (and `storeMediaFile` for audio/images) directly.
- Backend learns about the new field state on the next `/api/sync`. No special write endpoint.

**Schema implication for `user_vocab`:** per-slot fill state lives in `user_vocab.fill_state` JSONB (see [Slot-based field mapping](#slot-based-field-mapping-load-bearing-design)). Enrichment reads `fill_state["sentenceAudio"] == false`, then writes to whatever the user mapped that slot to (e.g. `"SentenceAudio"`) via `updateNoteFields`. The slot abstraction is what makes Nadeshiko work across users with different note types.

Before building: verify Nadeshiko's API terms re: rate limits and redistribution of media into user decks.

## Planned migrations / future work

- **Rename `Compound` → `Word` (and `compounds` table → `words`).** The 2-kanji-only (`isNiji`) filter in `DataSeeder` was a holdover from the kanji-graph design — for the Anki-sync dashboard we want to match any JMDICT entry (single-kanji words, 3+ kanji compounds, kana-only words, etc.) since `UserVocab.expression` can be anything the user has in their deck. Drop the `isNiji` filter, rename the entity/table/column/repository/foreign keys, and update `UserVocab.compound` → `UserVocab.word`. Re-seed required.

- **Split `audio_filled` into per-audio-slot columns.** `audio_filled` collapses information Nadeshiko enrichment needs (which specific audio field is missing). Drop `audio_filled`; keep `sentence_filled`; add `expression_audio_filled`, `sentence_audio_filled`, `image_filled` (all `BOOLEAN NOT NULL DEFAULT FALSE`). `UserVocab` entity gains matching fields. `SyncService` computes them at upsert time by resolving each slot via the user's `field_mapping`. Do this together with the slot-based service refactor below — both depend on the same `FieldMapping` helper.

- **Slot-based service refactor (replaces hardcoded field names in `SyncService`).** Currently `SyncService.upsertVocab` hardcodes `fieldValue(note, "front" | "sentence" | "audio")` — wrong for any note type that doesn't use those exact field names. Build a `FieldMapping` helper in `service/` that wraps `user.fieldMapping` and exposes `Optional<String> resolveSlot(modelName, slot)`. `SyncService` then resolves expression/sentence/audio/etc. via slot names, never raw field names. Seeder writes a hardcoded `Mining-JP` mapping for the test user so end-to-end works without a Settings UI. Skip-and-log notes whose `modelName` has no mapping. Pairs with the `fill_state` migration above.

- **Proficiency engine (Pass 2).** Currently `SyncService.upsertVocab` hardcodes `retention_status = NEW` and nothing populates `user_kanji`. Two pieces:
  1. **Derive `retention_status` from `cards`** in `upsertVocab`. Walk the cards list: `queue == -1` → suspended (skip); `interval > 21 && lapses < 3` → KNOWN; any live card otherwise → SHAKY; all-suspended → SUSPENDED; no cards → NEW. Anki queue values: `-1` suspended, `0` new, `1` learning, `2` review, `3` day-learning.
  2. **Recompute `user_kanji` at the end of `sync(...)`.** Per-user `INSERT ... ON CONFLICT DO UPDATE` from `kanji_compounds ⨝ compounds (common=TRUE) LEFT JOIN user_vocab`, grouped by `kanji_id`. `proficiency_score = known_count / common_compound_count`, `known = score >= 0.5`. SQL-first via a `@Modifying @Query` on `UserKanjiRepository`; do not load into Java and recompute per-row. Threshold (0.5) and weighting of SHAKY (currently zero) are intentionally simple — tune after seeing real data.

  Order of work: (1) close the real frontend → `/api/sync` round-trip first so the engine has real data to consume; (2) implement `deriveStatus`; (3) implement the recompute query; (4) call it from `SyncService.sync` after the upsert loop.

## Frontend

Not yet scaffolded on this branch. Target stack: React + TypeScript. Two top-level routes planned: `/` (Dashboard: summary stats + filterable browse table) and `/discover` (recommendations + search + Add to Anki). Tag color system is consistent across views: green=known, yellow=shaky, gray=new/not in deck, red=missing field.

**API boundary typing.** `response.json()` returns `Promise<any>` — using `as SomeType` is a compile-time-only assertion with no runtime check, so a malformed response silently produces `undefined`/`NaN` bugs downstream. Plan to adopt **zod** for runtime validation at every fetch boundary (`zod.parse(await res.json())`) and derive TS types via `z.infer<typeof Schema>` so one schema drives both. `as` is acceptable during prototyping (own backend, simple shapes) but switch to zod before any third-party API integration (Nadeshiko) and before any UI renders nested response data directly (Dashboard, Discover). Shared schemas live in `frontend/src/types/` (or `lib/schemas/`) and mirror the backend DTOs in `com.khmori.kagura.dto`.

## Conventions

- Package root: `com.khmori.kagura`.
- Lombok is enabled (`@Data` on entities) — annotation processing is configured in `pom.xml`.
- Spring Boot 4.0.4, Java 17.
- Dictionary files live in `dicts/` at repo root and are referenced with relative paths from the backend working dir (`../dicts/...`); `make run` `cd`s into `backend/` first, so that path resolves correctly. Don't move the dicts or change the working dir without updating `DataSeeder`.
