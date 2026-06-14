import { useState } from "react";
import type { UserKanjiDto } from "@/lib/api";
import { ChevronDownIcon, ChevronRightIcon } from "lucide-react";

function scoreToColor(score: number): string {
  if (score === 0) return "hsl(0 0% 85%)";
  const hue = score * 120;
  return `hsl(${hue} 70% 85%)`;
}

function KanjiTile({
  entry,
  onSelect,
}: {
  entry: UserKanjiDto;
  onSelect: (kanji: string) => void;
}) {
  return (
    <button
      onClick={() => onSelect(entry.kanji)}
      className="inline-flex items-center justify-center w-9 h-9 text-sm rounded-sm border border-border/50 cursor-pointer hover:ring-2 hover:ring-ring transition-shadow"
      style={{ backgroundColor: scoreToColor(entry.proficiencyScore) }}
      title={`${entry.kanji}: ${(entry.proficiencyScore * 100).toFixed(0)}%`}
    >
      {entry.kanji}
    </button>
  );
}

interface KanjiGridProps {
  entries: UserKanjiDto[];
  onSelectKanji: (kanji: string) => void;
}

export function KanjiGrid({ entries, onSelectKanji }: KanjiGridProps) {
  const knownCount = entries.filter((e) => e.known).length;

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        {knownCount} / {entries.length} kanji known
      </p>
      <div className="flex flex-wrap gap-1">
        {entries.map((entry) => (
          <KanjiTile key={entry.kanji} entry={entry} onSelect={onSelectKanji} />
        ))}
      </div>
    </div>
  );
}

function formatKankenLevel(level: number): string {
  if (level === 2.5) return "準2級";
  if (level === 1.5) return "準1級";
  return `${level}級`;
}

interface LevelGroup {
  label: string;
  entries: UserKanjiDto[];
}

function groupByLevel(
  entries: UserKanjiDto[],
  mode: "jlpt" | "kanken",
): LevelGroup[] {
  const groups = new Map<number, UserKanjiDto[]>();

  for (const entry of entries) {
    const level = mode === "jlpt" ? entry.jlptLevel : entry.kankenLevel;
    if (level == null) continue;
    let group = groups.get(level);
    if (!group) {
      group = [];
      groups.set(level, group);
    }
    group.push(entry);
  }

  for (const group of groups.values()) {
    group.sort((a, b) => b.proficiencyScore - a.proficiencyScore);
  }

  const sorted = [...groups.entries()].sort((a, b) => b[0] - a[0]);

  return sorted.map(([level, group]) => ({
    label: mode === "jlpt" ? `N${level}` : formatKankenLevel(level),
    entries: group,
  }));
}

function LevelSection({
  group,
  defaultOpen,
  onSelectKanji,
}: {
  group: LevelGroup;
  defaultOpen: boolean;
  onSelectKanji: (kanji: string) => void;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const knownCount = group.entries.filter((e) => e.known).length;

  return (
    <div className="border border-border/50 rounded-md">
      <button
        onClick={() => setOpen(!open)}
        className="flex w-full items-center gap-2 px-3 py-2 text-sm font-medium cursor-pointer hover:bg-muted/50 transition-colors"
      >
        {open ? (
          <ChevronDownIcon className="size-4 text-muted-foreground" />
        ) : (
          <ChevronRightIcon className="size-4 text-muted-foreground" />
        )}
        <span>{group.label}</span>
        <span className="text-muted-foreground font-normal">
          {knownCount} / {group.entries.length}
        </span>
      </button>
      {open && (
        <div className="flex flex-wrap gap-1 px-3 pb-3">
          {group.entries.map((entry) => (
            <KanjiTile key={entry.kanji} entry={entry} onSelect={onSelectKanji} />
          ))}
        </div>
      )}
    </div>
  );
}

interface GroupedKanjiGridProps {
  entries: UserKanjiDto[];
  mode: "jlpt" | "kanken";
  onSelectKanji: (kanji: string) => void;
}

export function GroupedKanjiGrid({ entries, mode, onSelectKanji }: GroupedKanjiGridProps) {
  const groups = groupByLevel(entries, mode);
  const totalKnown = entries.filter((e) => e.known).length;

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        {totalKnown} / {entries.length} kanji known
      </p>
      <div className="space-y-2">
        {groups.map((group) => (
          <LevelSection
            key={group.label}
            group={group}
            defaultOpen={mode === "jlpt"}
            onSelectKanji={onSelectKanji}
          />
        ))}
      </div>
    </div>
  );
}
