import { useEffect, useState } from "react";
import { Callout } from "@/components/Callout";
import { KanjiDetailsPanel } from "@/components/KanjiDetailsPanel";
import { KanjiGrid, GroupedKanjiGrid } from "@/components/KanjiGrid";
import { RecommendedWords } from "@/components/RecommendedWords";
import { WordDetailView } from "@/components/WordDetailView";
import { Button } from "@/components/ui/button";
import { getUserKanji, type UserKanjiDto, type WordEntry } from "@/lib/api";
import { useConfig } from "@/lib/config-context";
import { probe } from "@/lib/anki";
import { sync } from "@/lib/sync";

type PanelState =
  | { type: "kanji"; kanji: string }
  | { type: "word"; wordEntry: WordEntry; backLabel: string; back: PanelState | null }
  | null;

export default function Home() {
  const { config } = useConfig();
  const [grid, setGrid] = useState<UserKanjiDto[]>([]);
  const [syncing, setSyncing] = useState(false);
  const [panel, setPanel] = useState<PanelState>(null);

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

  function openKanji(kanji: string) {
    setPanel({ type: "kanji", kanji });
  }

  function openWordFromRecommendations(wordEntry: WordEntry) {
    setPanel({ type: "word", wordEntry, backLabel: "Back to recommendations", back: null });
  }

  function closePanel() {
    setPanel(null);
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

      {grid.length > 0 && <RecommendedWords onSelectWord={openWordFromRecommendations} />}

      {grid.length > 0 && config.studyMode !== "none" ? (
        <GroupedKanjiGrid entries={grid} mode={config.studyMode} onSelectKanji={openKanji} />
      ) : grid.length > 0 ? (
        <KanjiGrid entries={grid} onSelectKanji={openKanji} />
      ) : null}

      {panel && (
        <aside className="fixed right-0 top-0 h-full w-96 border-l bg-background shadow-lg overflow-y-auto z-50">
          <div className="p-6 space-y-6">
            {panel.type === "kanji" ? (
              <KanjiDetailsPanel
                kanji={panel.kanji}
                onSelectKanji={openKanji}
                onClose={closePanel}
              />
            ) : (
              <WordDetailView
                wordEntry={panel.wordEntry}
                backLabel={panel.backLabel}
                onBack={() => setPanel(panel.back)}
                onClose={closePanel}
                onSelectKanji={openKanji}
              />
            )}
          </div>
        </aside>
      )}
    </div>
  );
}
