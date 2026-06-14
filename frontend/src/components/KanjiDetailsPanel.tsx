import { useEffect, useState } from "react";
import { getKanjiDetails, type KanjiDetails, type WordEntry } from "@/lib/api";
import { WordDetailView } from "./WordDetailView";

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
            backLabel={`Back to ${details.kanji}`}
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

