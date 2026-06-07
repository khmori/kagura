# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Kagura is a Japanese learner's dashboard that syncs with Anki via AnkiConnect, computes kanji/vocab proficiency from SRS data, estimates JLPT/Kanken level, and lets users browse their knowledge inventory and discover new words. See the project spec (in conversation context / docs) for the full MVP plan.

**Status:** mid-pivot. The repo originally implemented a kanji graph visualization (see commits `2f8a415`, `6fecd84`). The frontend is being rewritten and the schema is being extended for the Anki-sync dashboard. Existing kanji/word ingestion is being kept; graph edges/scoring and the old DTO/recommendation-service work is parked.

## Core product flow (north star)

The one loop Kagura is built around. Everything else is supporting cast.

1. **Sync the deck → kanji grid + estimated level.** Read the user's Anki cards, compute per-kanji proficiency, render a grid colored by `known` (green/yellow/gray). Estimated JLPT/Kanken level sits on top.
2. **Click a kanji → detail panel.** Kanji metadata (stroke count, readings, meaning from KANJIDIC) plus the compound words that use it.
3. **Compound words = JMDICT-wide, frequency-ranked, colored by deck status.** Not just the words in the user's deck — *all* JMDICT words containing the kanji, sorted most-common-first, each tagged KNOWN / SHAKY / NEW by joining against `user_vocab`. The list shows both what the user knows and the common words they're missing.
4. **Per word → fetch an example (Nadeshiko et al.).** At the leaf, pull an example sentence + image for a word and add/enrich it in Anki. See "Card enrichment (Nadeshiko)" below.

**The one idea:** the grid is a *study planner*, not a report card. Weak kanji → the common words using it → the ones the user is missing → learn/enrich them. That loop is the product; the level estimate and grid are how you navigate into it.

**Build order follows the data dependency.** Steps 1–3 run entirely on local seeded data (KANJIDIC + JMDICT + `user_vocab`) — no external calls, so build these first; they're also the safe-to-demo core. Step 4 needs an external API → build last, keep it at the edge so a flaky/rate-limited Nadeshiko never breaks the grid.

**This fuses Browse and Discover.** The earlier split (`/` Browse = your deck, `/discover` = new words) collapses into one drill-down: the kanji detail panel shows known *and* unknown words together. A separate Discover page may still exist for free search, but the primary discovery surface is now the per-kanji word list.

Two data costs to know before committing:
- **Stroke order is not in KANJIDIC** — it only has stroke *count* (an int). Animated stroke-order diagrams need **KanjiVG** (separate SVG dataset, new seed pass + frontend render). Stroke count + readings + radical is enough to make the detail panel substantial without it; scope KanjiVG as its own task.
- **JMDICT frequency is coarse** — no clean frequency float, just tags (`news1/2`, `ichi1/2`, `spec1/2`, `nf01`–`nf48`). Pick a heuristic to collapse those into one sort key; the ranking will be chunky, not smooth.

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

Two separate signals, computed from the same card data:

**Per-word retention (`user_vocab.retention_status`).** A bucket for the Browse UI's color coding, derived per note from its cards:
- Drop suspended cards (`queue == -1`).
- Any remaining card with `interval > 21 && lapses < 3` → **KNOWN**.
- Any remaining card otherwise → **SHAKY**.
- Had cards but all suspended → **SUSPENDED**.
- No cards → **NEW**.

Best-status-wins (if any card is mature, the word is KNOWN) — generous on purpose: a word you know in recognition is "known" even if production is shaky. `SyncService.upsertVocab` currently hardcodes `NEW`; the enum is in `entity/RetentionStatus.java`.

**Per-kanji proficiency (`user_kanji.proficiency_score`).** Kanjigrid-style continuous score, not a binary KNOWN-fraction. For each kanji `K`:
```
avg_interval = mean(card.interval for word in user_vocab containing K
                                  for card in word.cards
                                  where card.queue != -1 AND card.type > 0)
raw          = avg_interval / 21
score        = 1 - 1 / (raw + 1)**2    # maps [0,∞) → [0,1), 0.75 at avg=21d
known        = score >= 0.5            # ≈ avg_interval ≥ 9d, tunable
```
No JMDICT commonness filter — the user's deck *is* the inventory. If they study it, it counts. Filtering by `words.common` would erase signal for the mining/immersion audience that lives on rare vocabulary. Threshold of 0.5 is a guess — eyeball results, tune later.

Implementation note: precompute `user_vocab.avg_interval` (Double, nullable) during `upsertVocab` so the `user_kanji` recompute is a single GROUP BY over the join, no JSONB scanning at query time.

**Estimated JLPT/Kanken level** = highest level where user `known` covers ≥80% of that level's kanji/vocab (later).

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

**Done (recent):** P0 `KEPT_NOTE_FIELDS` removed; `GET/PUT /api/users/me/config` endpoint; Settings UI for deck + field mapping; seeder no longer hardcodes a mapping.

**P1 — close the sync loop**
1. Proficiency engine Pass A (½ day). `deriveStatus` + `avg_interval` in `upsertVocab`. See "Planned migrations" below.
2. Proficiency engine Pass B (½–1 day). `user_kanji` recompute via native `@Query`. Debug against hand-counted kanji.
3. Minimal dashboard UI (1–2 days). Table of `user_vocab` with retention colors, kanji grid colored by `known`. Ugly is fine.

**P2 — pay down**
4. Sync performance (1–2 hrs). Hibernate can't batch INSERTs with `IDENTITY` generation. Switch `user_vocab` to `SEQUENCE` strategy or bypass Hibernate with a native bulk upsert via JDBC.
5. Gzip the sync payload (30 min). Unfiltered notes hit the 50MB ceiling; gzip → ~5MB. `server.compression.enabled=true` on Spring.
6. Flyway/Liquibase (2–4 hrs). Already hit the `CREATE TABLE IF NOT EXISTS` trap once; do this before more schema changes.
7. Unit tests for `FieldMapping` + `SyncService` (half day). Cheap insurance on load-bearing code.
8. Word/kanji frequency data. JMDICT only has coarse tags (`news1/2`, `ichi1/2`, `nf01`–`nf48`), and the `words.common` boolean is binary. Collapse these into a numeric frequency score for proper sort ordering in the detail panel word list. Consider an external frequency list (e.g. JPDB, Innocent Corpus) for finer granularity.

**Weakest estimates:** Pass B SQL (#2) could be a half-day if it clicks, 3 days if you fight JPQL — go native from the start.

## Planned migrations / future work

- **Proficiency engine (Pass 2).** Currently `SyncService.upsertVocab` hardcodes `retention_status = NEW`, doesn't compute `avg_interval`, and nothing populates `user_kanji`. Three pieces, shipped in two passes:

  *Pass A — per-word state in `upsertVocab`:*
  1. **Derive `retention_status`** from the cards list (best-status-wins rules in "Proficiency model" above). Anki queue values: `-1` suspended, `0` new, `1` learning, `2` review, `3` day-learning. Card `type`: 0 new, 1 learning, 2 review, 3 relearning.
  2. **Compute `avg_interval`** = mean of `card.interval` over cards with `queue != -1 && type > 0` (i.e. seen, non-suspended). Store as nullable Double on `user_vocab`; null when no qualifying cards. Add column to schema + entity.

  Validate against psql before moving on: sync the test deck, eyeball a handful of `user_vocab` rows — known mature words should be KNOWN with high `avg_interval`; brand-new words should be NEW with null.

  *Pass B — kanji aggregation:*
  3. **Recompute `user_kanji` at end of `SyncService.sync`.** Per-user `INSERT ... ON CONFLICT (user_id, kanji_id) DO UPDATE` from `kanji_words ⨝ user_vocab` (no commonness filter), grouped by `kanji_id`. `avg_interval` aggregated across the user's vocab containing each kanji; `proficiency_score = 1 - 1/(avg/21 + 1)^2`; `known = score >= 0.5`. SQL-first via a `@Modifying @Query` on `UserKanjiRepository` — do not load into Java and aggregate per-row. Use native SQL, not JPQL.

  Open question for Pass B: weight `user_vocab.avg_interval` rows equally (per-word) or by card count (per-card, matches kanjigrid more closely). Start equal-weight; revisit if scores look wrong on real data.

## Frontend (`frontend/`)

Vite + React 19 + TypeScript. Single `App.tsx` with two buttons (`probe`, `sync`) — no routing, no UI scaffold yet. Target stack stays the same; planned routes: `/` (Dashboard: summary stats + filterable browse table) and `/discover` (recommendations + search + Add to Anki). Tag color system planned to be consistent across views: green=known, yellow=shaky, gray=new/not in deck, red=missing field.

The `anki()` helper in `App.tsx` wraps AnkiConnect's `{action, version, params}` JSON-RPC. `buildSyncRequest` is the canonical example of the bridge: pull from AnkiConnect, trim via `KEPT_NOTE_FIELDS` + `extractCardScheduling`, POST to backend.

**API boundary typing.** `response.json()` returns `Promise<any>` — using `as SomeType` is a compile-time-only assertion with no runtime check, so a malformed response silently produces `undefined`/`NaN` bugs downstream. Plan to adopt **zod** for runtime validation at every fetch boundary (`zod.parse(await res.json())`) and derive TS types via `z.infer<typeof Schema>` so one schema drives both. `as` is acceptable during prototyping (own backend, simple shapes — current state) but switch to zod before any third-party API integration (Nadeshiko) and before any UI renders nested response data directly (Dashboard, Discover). Shared schemas live in `frontend/src/types/` (or `lib/schemas/`) and mirror the backend DTOs in `com.khmori.kagura.dto`.

## Settings UI placement (open question)

Currently Settings is a full page reached via the left nav rail (Home / Settings). Considering switching to a **gear icon top-right that opens `<Settings />` in a native `<dialog>`**. Rationale: Kagura settings will probably top out at 3-4 tabs (Sync, Account, Appearance, maybe Discover prefs) — dialog-weight, not full-page-weight. The badge (yellow `!` when setup is incomplete) moves to the gear icon. Same `<Settings />` component either way; only the container changes.

References worth looking at before deciding: Linear's settings dialog, Raycast Preferences, mobbin.com (filter "Settings"). Decision criteria is roughly: if Settings stays small, dialog wins; if it grows past ~5 tabs with deep config, the current full-page wins.

Either way, **no forced redirects to Settings**. Boot always lands on Home. Unmapped slots surface via the badge + per-dropdown yellow highlights in Settings, never by teleporting the user.

## Typography (planned)

Two-font system: **IBM Plex Sans** for UI chrome and Latin text, **Klee One** as the default for Japanese text (kanji/kana in the inventory, example sentences, headings). Klee One is a textbook-style kaisho — feels appropriate for a learning tool and uses the kanji forms learners are trained on.

The Japanese font should be **user-configurable in Settings**. Offer a small curated list (Klee One default, Noto Serif JP, Shippori Mincho, Zen Old Mincho, Noto Sans JP, M PLUS 1) — not a free-text field. Persist on the user (likely a `users.preferences` JSONB column or similar; design alongside other future user prefs). Apply via a CSS custom property (`--font-jp`) on `:root` so all Japanese-rendering components pick it up without prop drilling.

Load fonts via Google Fonts `<link>` in `index.html` for the curated set. Latin font stays fixed (IBM Plex Sans).

## Conventions

- Package root: `com.khmori.kagura`.
- Lombok is enabled (`@Data` on entities) — annotation processing is configured in `pom.xml`.
- Spring Boot 4.0.4, Java 17.
- Dictionary files live in `dicts/` at repo root and are referenced with relative paths from the backend working dir (`../dicts/...`); `make run` `cd`s into `backend/` first, so that path resolves correctly. Don't move the dicts or change the working dir without updating `DataSeeder`.
