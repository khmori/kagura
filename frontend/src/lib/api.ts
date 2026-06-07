// modelName -> slotName -> Anki field name
export type FieldMapping = Record<string, Record<string, string>>;

export interface UserConfig {
  selectedDeck: string | null;
  fieldMapping: FieldMapping;
}

export const API_BASE = "http://localhost:8080";

export interface UserKanjiDto {
  kanji: string;
  proficiencyScore: number;
  known: boolean;
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
  strokeCount: number | null;
  words: WordEntry[];
}

export interface WordEntry {
  word: string;
  reading: string[];
  meaning: string[];
  common: boolean;
  retentionStatus: string | null;
}

export async function getKanjiDetails(character: string): Promise<KanjiDetails> {
  const res = await fetch(`${API_BASE}/api/kanji/${encodeURIComponent(character)}`);
  if (!res.ok) throw new Error(`kanji details fetch failed: ${res.status}`);
  return (await res.json()) as KanjiDetails;
}
