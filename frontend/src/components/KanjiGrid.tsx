import type { UserKanjiDto } from "@/lib/api";

function scoreToColor(score: number): string {
  if (score === 0) return "hsl(0 0% 85%)";
  const hue = score * 120;
  return `hsl(${hue} 70% 85%)`;
}

export function KanjiGrid({ entries }: { entries: UserKanjiDto[] }) {
  const knownCount = entries.filter((e) => e.known).length;

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        {knownCount} / {entries.length} kanji known
      </p>
      <div className="flex flex-wrap gap-1">
        {entries.map((entry) => (
          <button
            key={entry.kanji}
            className="inline-flex items-center justify-center w-9 h-9 text-sm rounded-sm border border-border/50 cursor-pointer hover:ring-2 hover:ring-ring transition-shadow"
            style={{ backgroundColor: scoreToColor(entry.proficiencyScore) }}
            title={`${entry.kanji}: ${(entry.proficiencyScore * 100).toFixed(0)}%`}
          >
            {entry.kanji}
          </button>
        ))}
      </div>
    </div>
  );
}
