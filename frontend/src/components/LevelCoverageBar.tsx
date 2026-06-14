import {
  Progress,
  ProgressLabel,
  ProgressValue,
} from "@/components/ui/progress";
import { formatKankenLevel } from "@/components/KanjiGrid";
import type { UserKanjiDto } from "@/lib/api";

interface LevelCoverageBarProps {
  entries: UserKanjiDto[];
  studyMode: "jlpt" | "kanken";
  targetLevel: string;
}

export function LevelCoverageBar({
  entries,
  studyMode,
  targetLevel,
}: LevelCoverageBarProps) {
  const numericLevel = parseFloat(targetLevel);

  const targetEntries = entries.filter((e) => {
    const level = studyMode === "jlpt" ? e.jlptLevel : e.kankenLevel;
    return level === numericLevel;
  });

  const total = targetEntries.length;
  const known = targetEntries.filter((e) => e.known).length;
  const pct = total === 0 ? 0 : Math.round((known / total) * 100);

  const levelLabel =
    studyMode === "jlpt"
      ? `N${targetLevel}`
      : formatKankenLevel(numericLevel);

  if (total === 0) return null;

  return (
    <Progress value={pct}>
      <ProgressLabel>{levelLabel} Coverage</ProgressLabel>
      <ProgressValue>
        {() => `${known} / ${total} kanji known — ${pct}%`}
      </ProgressValue>
    </Progress>
  );
}
