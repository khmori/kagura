// modelName -> slotName -> Anki field name
export type FieldMapping = Record<string, Record<string, string>>;

export interface UserConfig {
  selectedDeck: string | null;
  fieldMapping: FieldMapping;
  studyMode: "none" | "jlpt" | "kanken";
}

export const API_BASE = "http://localhost:8080";

export interface UserKanjiDto {
  kanji: string;
  proficiencyScore: number;
  known: boolean;
  jlptLevel: number | null;
  kankenLevel: number | null;
}

export async function getUserConfig(): Promise<UserConfig> {
  const res = await fetch(`${API_BASE}/api/users/me/config`);
  if (!res.ok) throw new Error(`config fetch failed: ${res.status}`);
  return (await res.json()) as UserConfig;
}

export async function putUserConfig(config: UserConfig): Promise<void> {
  const res = await fetch(`${API_BASE}/api/users/me/config`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(config),
  });
  if (!res.ok) throw new Error(`config save failed: ${res.status}`);
}

export async function getUserKanji(): Promise<UserKanjiDto[]> {
  const res = await fetch(`${API_BASE}/api/user-kanji`);
  if (!res.ok) throw new Error(`user kanji fetch failed: ${res.status}`);
  return (await res.json()) as UserKanjiDto[];
}

export interface KanjiDetails {
  kanji: string;
  onReading: string[];
  kunReading: string[];
  meaning: string[];
  grade: number | null;
  jlptLevel: number | null;
  kankenLevel: number | null;
  strokeCount: number | null;
  words: WordEntry[];
}

export interface WordEntry {
  word: string;
  reading: string[];
  meaning: string[];
  common: boolean;
  frequencyRank: number | null;
  retentionStatus: string | null;
}

export async function getKanjiDetails(character: string): Promise<KanjiDetails> {
  const res = await fetch(`${API_BASE}/api/kanji/${encodeURIComponent(character)}`);
  if (!res.ok) throw new Error(`kanji details fetch failed: ${res.status}`);
  return (await res.json()) as KanjiDetails;
}

export interface ExampleSentence {
  sentence: string;
  wordPosition: number;
  wordLength: number;
  source: string;
  difficulty: number;
}

export async function getExampleSentences(word: string): Promise<ExampleSentence[]> {
  const res = await fetch(`${API_BASE}/api/sentences?word=${encodeURIComponent(word)}`);
  if (!res.ok) return [];
  return (await res.json()) as ExampleSentence[];
}

export interface RecommendedWord {
  id: number;
  word: string;
  reading: string[];
  meaning: string[];
  frequencyRank: number;
  score: number;
  reinforces: string[];
}

export async function getRecommendedWords(limit = 20): Promise<RecommendedWord[]> {
  const res = await fetch(`${API_BASE}/api/recommended-words?limit=${limit}`);
  if (!res.ok) return [];
  return (await res.json()) as RecommendedWord[];
}
