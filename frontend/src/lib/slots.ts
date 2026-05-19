// Kagura's canonical slot names. The user maps each one to a real Anki field
// name (per note type) so backend code never has to know about field names.
export const SLOTS = [
  "expression",
  "reading",
  "meaning",
  "sentence",
  "expressionAudio",
  "sentenceAudio",
  "image",
] as const;

export type Slot = (typeof SLOTS)[number];

// `expression` is the join key against `words.word`. Without it, a note type
// can't be synced. All other slots are optional.
export const REQUIRED_SLOTS: Slot[] = ["expression"];
