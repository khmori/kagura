import { anki, type CardInfo, type FieldMap, type NoteInfo } from "./anki";
import type { FieldMapping } from "./api";

// payload sent to the backend — mirrors com.khmori.kagura.dto.SyncRequest / IncomingNote
export interface SyncRequest {
  provider: string;
  providerUserId: string;
  notes: IncomingNote[];
}

export interface IncomingNote {
  ankiNoteId: number;
  ankiModelName: string;
  fields: FieldMap;
  cards: CardInfo[];
}

function extractCardScheduling(c: CardInfo): CardInfo {
  const {
    cardId,
    modelName,
    interval,
    lapses,
    reps,
    factor,
    queue,
    type,
    due,
  } = c;
  return {
    cardId,
    modelName,
    interval,
    lapses,
    reps,
    factor,
    queue,
    type,
    due,
  };
}

// AnkiConnect returns lots of extra data per card (rendered HTML, CSS, templates)
// and per note (Yomitan HTML soup, media). Keep only the fields the user mapped
// to a canonical slot for this note type — backend reads nothing else.
function extractNoteFields(
  fields: FieldMap,
  modelMapping: Record<string, string> | undefined,
): FieldMap {
  if (!modelMapping) return {};
  const out: FieldMap = {};
  for (const ankiFieldName of Object.values(modelMapping)) {
    if (fields[ankiFieldName]) out[ankiFieldName] = fields[ankiFieldName];
  }
  return out;
}

export async function buildSyncRequest(
  deckName: string,
  provider: string,
  providerUserId: string,
  fieldMapping: FieldMapping,
): Promise<SyncRequest> {
  // 1. find note IDs in the deck
  const noteIds = await anki<number[]>("findNotes", {
    query: `deck:"${deckName}"`,
  });

  // 2. fetch full note info (fields, modelName, card IDs)
  const notesInfo = await anki<NoteInfo[]>("notesInfo", { notes: noteIds });

  // 3. fetch all cards in one batch, then index by cardId for fast lookup
  const allCardIds = notesInfo.flatMap((n) => n.cards);
  const cardsInfo = await anki<CardInfo[]>("cardsInfo", { cards: allCardIds });
  const cardsById = new Map<number, CardInfo>(
    cardsInfo.map((c) => [c.cardId, c]),
  );

  // 4. shape into IncomingNote[] — backend's expected format. Notes whose
  // modelName has no mapping go through with empty fields; backend skips them.
  const notes: IncomingNote[] = notesInfo.map((n) => ({
    ankiNoteId: n.noteId,
    ankiModelName: n.modelName,
    fields: extractNoteFields(n.fields, fieldMapping[n.modelName]),
    cards: n.cards
      .map((id) => cardsById.get(id))
      .filter((c): c is CardInfo => c !== undefined)
      .map(extractCardScheduling),
  }));

  return { provider, providerUserId, notes };
}

export async function sync(deckName: string, fieldMapping: FieldMapping) {
  const syncRequest = await buildSyncRequest(
    deckName,
    "manual",
    "test-1",
    fieldMapping,
  );

  const body = JSON.stringify(syncRequest);
  console.log(
    `posting payload: ${syncRequest.notes.length} notes, ${(body.length / 1024 / 1024).toFixed(2)} MB`,
  );

  const response = await fetch("http://localhost:8080/api/sync", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
  });
  if (!response.ok) {
    throw new Error(`sync failed: ${response.status} ${await response.text()}`);
  }
  console.log("sync ok:", await response.json());
}
