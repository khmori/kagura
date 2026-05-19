export type FieldMap = Record<string, { value: string; order: number }>;

// raw shapes returned by AnkiConnect — partial; only fields we use
export interface NoteInfo {
  noteId: number;
  modelName: string;
  tags: string[];
  fields: FieldMap;
  cards: number[]; // card IDs, not card objects
}

export interface CardInfo {
  cardId: number;
  modelName: string;
  interval: number;
  lapses: number;
  reps: number;
  factor: number;
  queue: number;
  type: number;
  due: number;
}

export async function anki<T>(action: string, params: object = {}): Promise<T> {
  const response = await fetch("http://localhost:8765", {
    method: "POST",
    body: JSON.stringify({ action, version: 6, params }),
  });
  const { result, error } = await response.json();
  if (error) throw new Error(error);
  return result as T;
}

export async function pingAnki(): Promise<boolean> {
  try {
    const res = await fetch("http://localhost:8765", {
      method: "POST",
      body: JSON.stringify({ action: "version", version: 6 }),
    });

    if (!res.ok) return false;
    const data = await res.json();
    if (!data) return false;
    return data.error == null && typeof data.result === "number";
  } catch {
    return false;
  }
}

export async function probe() {
  // fetch deck names
  const decks = await anki<string[]>("deckNames");
  console.log("decks: ", decks);

  // fetch notes
  const notes = await anki<number[]>("findNotes", {
    query: 'deck:"reading mine"',
  });
  console.log("note ids: ", notes);

  const notesInfo = await anki<NoteInfo[]>("notesInfo", {
    notes: notes.slice(0, 10),
  });
  console.log("first 10 notes: ", notesInfo);
  console.log(Object.keys(notesInfo[0].fields));

  console.log(
    "fronts: ",
    notesInfo.map((note) => note.fields.front.value),
  );
  console.log(
    "readings: ",
    notesInfo.map((note) => note.fields.Reading.value),
  );
  console.log(
    "meanings: ",
    notesInfo.map((note) => note.fields.MainDefinition.value),
  );
  console.log(
    "sentences: ",
    notesInfo.map((note) => note.fields.Sentence.value),
  );

  // extract card IDs from first 10 notes
  const cards = notesInfo.map((note) => note.cards[0]);
  console.log("card ids: ", cards);

  const cardsInfo = await anki<CardInfo[]>("cardsInfo", {
    cards: cards.slice(0, 10),
  });
  console.log("first 10 cards: ", cardsInfo);

  console.log(Object.keys(cardsInfo[0]));
  console.log(
    "model names: ",
    cardsInfo.map((card) => card.modelName),
  );
  console.log(
    "factors: ",
    cardsInfo.map((card) => card.factor),
  );
  console.log(
    "intervals: ",
    cardsInfo.map((card) => card.interval),
  );
  console.log(
    "types: ",
    cardsInfo.map((card) => card.type),
  );
  console.log(
    "due: ",
    cardsInfo.map((card) => card.due),
  );
  console.log(
    "reps: ",
    cardsInfo.map((card) => card.reps),
  );
  console.log(
    "lapses: ",
    cardsInfo.map((card) => card.lapses),
  );
}
