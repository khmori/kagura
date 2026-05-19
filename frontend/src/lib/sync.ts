import { anki, type CardInfo, type FieldMap, type NoteInfo } from "./anki";

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

// AnkiConnect returns lots of extra data per card (rendered HTML, CSS, templates)
// and per note (Yomitan HTML soup, media). Drop everything backend doesn't read.
const KEPT_NOTE_FIELDS = [
  "front",
  "Sentence",
  "ExpressionAudio",
  "SentenceAudio",
];

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

function extractNoteFields(fields: FieldMap): FieldMap {
  const out: FieldMap = {};
  for (const key of KEPT_NOTE_FIELDS) {
    if (fields[key]) out[key] = fields[key];
  }
  return out;
}

export async function buildSyncRequest(
  deckName: string,
  provider: string,
  providerUserId: string,
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

  // 4. shape into IncomingNote[] — backend's expected format
  const notes: IncomingNote[] = notesInfo.map((n) => ({
    ankiNoteId: n.noteId,
    ankiModelName: n.modelName,
    fields: extractNoteFields(n.fields),
    // fields: n.fields,
    cards: n.cards
      .map((id) => cardsById.get(id))
      .filter((c): c is CardInfo => c !== undefined)
      .map(extractCardScheduling),
  }));

  return { provider, providerUserId, notes };
}

export async function sync() {
  // make calls to AnkiConnect API for current user and chosen deck
  const syncRequest = await buildSyncRequest(
    "reading mine",
    "manual",
    "test-1",
  );

  // determine distinct note types in current deck
  const distinctModelNames = new Set<string>();
  syncRequest.notes.map((n) => distinctModelNames.add(n.ankiModelName));
  console.log(distinctModelNames);

  syncRequest.notes.map((n) => console.log(n.ankiModelName));

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
