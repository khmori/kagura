import { useEffect, useState } from "react";
import {
  getExampleSentences,
  getKanjiDetails,
  type ExampleSentence,
  type KanjiDetails,
  type WordEntry,
} from "@/lib/api";
import { useConfig } from "@/lib/config-context";
import { anki } from "@/lib/anki";

const STATUS_COLORS: Record<string, string> = {
  KNOWN: "bg-green-100 text-green-800",
  SHAKY: "bg-yellow-100 text-yellow-800",
  NEW: "bg-gray-100 text-gray-600",
  SUSPENDED: "bg-red-100 text-red-800",
};

interface KanjiDetailsPanelProps {
  kanji: string;
  onClose: () => void;
}

export function KanjiDetailsPanel({ kanji, onClose }: KanjiDetailsPanelProps) {
  const [details, setDetails] = useState<KanjiDetails | null>(null);
  const [selectedWord, setSelectedWord] = useState<WordEntry | null>(null);

  useEffect(() => {
    setDetails(null);
    setSelectedWord(null);
    getKanjiDetails(kanji).then(setDetails);
  }, [kanji]);

  if (!details) return null;

  return (
    <aside className="fixed right-0 top-0 h-full w-96 border-l bg-background shadow-lg overflow-y-auto z-50">
      <div className="p-6 space-y-6">
        {selectedWord ? (
          <WordDetailView
            wordEntry={selectedWord}
            kanji={details.kanji}
            onBack={() => setSelectedWord(null)}
            onClose={onClose}
          />
        ) : (
          <KanjiDetailView details={details} onClose={onClose} onSelectWord={setSelectedWord} />
        )}
      </div>
    </aside>
  );
}

function KanjiDetailView({
  details,
  onClose,
  onSelectWord,
}: {
  details: KanjiDetails;
  onClose: () => void;
  onSelectWord: (word: WordEntry) => void;
}) {
  return (
    <>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-6xl font-medium">{details.kanji}</p>
          <p className="mt-2 text-sm text-muted-foreground">{details.meaning?.join(", ")}</p>
        </div>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground text-xl cursor-pointer">
          ✕
        </button>
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm">
        {details.onReading?.length > 0 && (
          <div>
            <p className="text-muted-foreground">On</p>
            <p>{details.onReading.join("、")}</p>
          </div>
        )}
        {details.kunReading?.length > 0 && (
          <div>
            <p className="text-muted-foreground">Kun</p>
            <p>{details.kunReading.join("、")}</p>
          </div>
        )}
        {details.strokeCount && (
          <div>
            <p className="text-muted-foreground">Strokes</p>
            <p>{details.strokeCount}</p>
          </div>
        )}
        {details.grade && (
          <div>
            <p className="text-muted-foreground">Grade</p>
            <p>{details.grade}</p>
          </div>
        )}
        {details.jlptLevel && (
          <div>
            <p className="text-muted-foreground">JLPT</p>
            <p>N{details.jlptLevel}</p>
          </div>
        )}
        {details.kankenLevel != null && (
          <div>
            <p className="text-muted-foreground">Kanken</p>
            <p>{details.kankenLevel === 2.5 ? "準2級" : details.kankenLevel === 1.5 ? "準1級" : `${details.kankenLevel}級`}</p>
          </div>
        )}
      </div>

      <div>
        <h3 className="text-sm font-medium mb-2">Words ({details.words.length})</h3>
        <ul className="space-y-2">
          {details.words.map((word) => (
            <li
              key={word.word}
              onClick={() => onSelectWord(word)}
              className="text-sm border-b border-border/50 pb-2 cursor-pointer hover:bg-muted/50 -mx-2 px-2 py-1 rounded"
            >
              <div className="flex items-center gap-2">
                <span className="font-medium">{word.word}</span>
                {word.reading?.length > 0 && <span className="text-muted-foreground">{word.reading[0]}</span>}
                {word.retentionStatus && (
                  <span className={`text-xs px-1.5 py-0.5 rounded ${STATUS_COLORS[word.retentionStatus] ?? ""}`}>
                    {word.retentionStatus}
                  </span>
                )}
              </div>
              {word.meaning?.length > 0 && <p className="text-muted-foreground mt-0.5">{word.meaning[0]}</p>}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}

function WordDetailView({
  wordEntry,
  kanji,
  onBack,
  onClose,
}: {
  wordEntry: WordEntry;
  kanji: string;
  onBack: () => void;
  onClose: () => void;
}) {
  const { config } = useConfig();
  const [sentences, setSentences] = useState<ExampleSentence[]>([]);
  const [loadingSentences, setLoadingSentences] = useState(false);
  const [selectedSentenceIndex, setSelectedSentenceIndex] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const [addResult, setAddResult] = useState<string | null>(null);

  useEffect(() => {
    setLoadingSentences(true);
    setSentences([]);
    setSelectedSentenceIndex(null);
    getExampleSentences(wordEntry.word)
      .then(setSentences)
      .finally(() => setLoadingSentences(false));
  }, [wordEntry.word]);

  async function handleAddToDeck() {
    const deck = config.selectedDeck;
    const modelName = Object.keys(config.fieldMapping)[0];
    if (!deck || !modelName) return;

    const mapping = config.fieldMapping[modelName];
    const fields: Record<string, string> = {};

    if (mapping.expression) fields[mapping.expression] = wordEntry.word;
    if (mapping.reading && wordEntry.reading?.length > 0) fields[mapping.reading] = wordEntry.reading[0];
    if (mapping.meaning && wordEntry.meaning?.length > 0) fields[mapping.meaning] = wordEntry.meaning.join(", ");
    if (mapping.sentence && selectedSentenceIndex != null)
      fields[mapping.sentence] = sentences[selectedSentenceIndex].sentence;

    setAdding(true);
    setAddResult(null);
    try {
      await anki("addNote", {
        note: {
          deckName: deck,
          modelName,
          fields,
          tags: ["kagura"],
          options: {
            allowDuplicate: false,
            duplicateScope: "deck",
            duplicateScopeOptions: { deckName: deck, checkChildren: false, checkAllModels: false },
          },
        },
      });
      setAddResult("Added to deck.");
    } catch (e) {
      setAddResult(e instanceof Error ? "Error: " + e.message : "Unknown error");
    } finally {
      setAdding(false);
    }
  }

  const canAdd = !!config.selectedDeck && Object.keys(config.fieldMapping).length > 0;

  return (
    <>
      <div className="flex items-center justify-between">
        <button onClick={onBack} className="text-sm text-muted-foreground hover:text-foreground cursor-pointer">
          ← Back to {kanji}
        </button>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground text-xl cursor-pointer">
          ✕
        </button>
      </div>

      <div>
        <p className="text-4xl font-medium">{wordEntry.word}</p>
        {wordEntry.reading?.length > 0 && (
          <p className="mt-2 text-lg text-muted-foreground">{wordEntry.reading.join("、")}</p>
        )}
      </div>

      {wordEntry.retentionStatus && (
        <span
          className={`text-xs px-1.5 py-0.5 rounded inline-block ${STATUS_COLORS[wordEntry.retentionStatus] ?? ""}`}
        >
          {wordEntry.retentionStatus}
        </span>
      )}

      {wordEntry.meaning?.length > 0 && (
        <div>
          <h3 className="text-sm font-medium mb-2">Meanings</h3>
          <ol className="list-decimal list-inside space-y-1 text-sm text-muted-foreground">
            {wordEntry.meaning.map((m, i) => (
              <li key={i}>{m}</li>
            ))}
          </ol>
        </div>
      )}

      <div>
        <h3 className="text-sm font-medium mb-2">Example sentences</h3>
        {loadingSentences && <p className="text-sm text-muted-foreground">Loading...</p>}
        {!loadingSentences && sentences.length === 0 && (
          <p className="text-sm text-muted-foreground">No example sentences found.</p>
        )}
        {sentences.length > 0 && (
          <ul className="space-y-3">
            {sentences.map((s, i) => (
              <li
                key={i}
                onClick={() => setSelectedSentenceIndex(i === selectedSentenceIndex ? null : i)}
                className={`text-sm pb-3 cursor-pointer rounded -mx-2 px-2 py-2 ${
                  i === selectedSentenceIndex
                    ? "ring-2 ring-primary bg-muted/50"
                    : "border-b border-border/50 hover:bg-muted/30"
                }`}
              >
                <p>
                  {s.sentence.slice(0, s.wordPosition)}
                  <mark className="bg-yellow-200 rounded-sm px-0.5">
                    {s.sentence.slice(s.wordPosition, s.wordPosition + s.wordLength)}
                  </mark>
                  {s.sentence.slice(s.wordPosition + s.wordLength)}
                </p>
                {s.source && <p className="text-xs text-muted-foreground mt-1">Source: {s.source}</p>}
              </li>
            ))}
          </ul>
        )}
      </div>

      {canAdd && (
        <button
          onClick={handleAddToDeck}
          disabled={adding}
          className="w-full rounded bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50 cursor-pointer"
        >
          {adding ? "Adding..." : "Add to deck"}
        </button>
      )}
      {addResult && <p className="text-sm text-muted-foreground">{addResult}</p>}
    </>
  );
}
