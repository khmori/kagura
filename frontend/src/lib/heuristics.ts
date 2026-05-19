import type { Slot } from "./slots";

// Field-name patterns for each slot, case-insensitive. Earlier patterns within
// a slot win over later ones. Slots are processed in the order below so that
// "SentenceAudio" gets claimed by `sentenceAudio` before `sentence` can take it.
const SLOT_ORDER: Slot[] = [
  "expression",
  "sentenceAudio",
  "expressionAudio",
  "sentence",
  "reading",
  "meaning",
  "image",
];

const PATTERNS: Record<Slot, RegExp[]> = {
  expression: [
    /^expression$/i,
    /^word$/i,
    /^vocab(ulary)?$/i,
    /^term$/i,
    /^target$/i,
    /^front$/i,
  ],
  sentenceAudio: [/sentence.*audio|example.*audio|audio.*sentence|audio.*example/i],
  expressionAudio: [
    /expression.*audio|word.*audio|vocab.*audio|audio.*expression|audio.*word|audio.*vocab/i,
    /^audio$/i,
  ],
  sentence: [/sentence|example|context/i],
  reading: [/reading|kana|furigana|yomi/i],
  meaning: [/meaning|definition|gloss|english|translation|^back$/i],
  image: [/image|picture|screenshot|photo/i],
};

// Given a model's field names, guess a sensible slot → field mapping.
// Each field is claimed by at most one slot.
export function guessFieldMapping(
  fieldNames: string[],
): Partial<Record<Slot, string>> {
  const guess: Partial<Record<Slot, string>> = {};
  const claimed = new Set<string>();

  for (const slot of SLOT_ORDER) {
    for (const pattern of PATTERNS[slot]) {
      const match = fieldNames.find((f) => !claimed.has(f) && pattern.test(f));
      if (match) {
        guess[slot] = match;
        claimed.add(match);
        break;
      }
    }
  }
  return guess;
}
