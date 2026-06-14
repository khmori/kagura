import { useEffect, useState } from "react";
import { Callout } from "@/components/Callout";
import { KanjiDetailsPanel } from "@/components/KanjiDetailsPanel";
import { KanjiGrid, GroupedKanjiGrid } from "@/components/KanjiGrid";
import { Button } from "@/components/ui/button";
import { getUserKanji, type UserKanjiDto } from "@/lib/api";
import { useConfig } from "@/lib/config-context";
import { probe } from "@/lib/anki";
import { sync } from "@/lib/sync";

export default function Home() {
  const { config } = useConfig();
  const [grid, setGrid] = useState<UserKanjiDto[]>([]);
  const [syncing, setSyncing] = useState(false);
  const [selectedKanji, setSelectedKanji] = useState<string | null>(null);

  useEffect(() => {
    getUserKanji().then(setGrid);
  }, []);

  async function handleSync() {
    setSyncing(true);
    try {
      await sync(config.selectedDeck!, config.fieldMapping);
      setGrid(await getUserKanji());
    } finally {
      setSyncing(false);
    }
  }

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">Home</h1>
        <p className="mt-1 text-sm text-muted-foreground">Your Japanese learning dashboard.</p>
      </header>

      {!config.selectedDeck && (
        <Callout variant="news">
          Pick a deck and confirm your field mappings in <span className="font-semibold">Settings</span> to start
          syncing.
        </Callout>
      )}

      <section className="flex flex-wrap gap-2">
        <Button onClick={handleSync} disabled={!config.selectedDeck || syncing}>
          {syncing ? "Syncing…" : "Sync with AnkiConnect"}
        </Button>
        <Button variant="outline" onClick={probe}>
          Probe
        </Button>
      </section>

      {grid.length > 0 && config.studyMode !== "none" ? (
        <GroupedKanjiGrid entries={grid} mode={config.studyMode} onSelectKanji={setSelectedKanji} />
      ) : grid.length > 0 ? (
        <KanjiGrid entries={grid} onSelectKanji={setSelectedKanji} />
      ) : null}

      {selectedKanji && <KanjiDetailsPanel kanji={selectedKanji} onClose={() => setSelectedKanji(null)} />}
    </div>
  );
}
