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
