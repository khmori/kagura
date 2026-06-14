import { useEffect, useState } from "react";
import { getRecommendedWords, type RecommendedWord, type WordEntry } from "@/lib/api";
import { WordDetailView } from "./WordDetailView";

export function RecommendedWords() {
  const [words, setWords] = useState<RecommendedWord[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedWord, setSelectedWord] = useState<RecommendedWord | null>(null);

  useEffect(() => {
    getRecommendedWords(20)
      .then(setWords)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="text-sm text-muted-foreground">Loading recommendations...</p>;
  if (words.length === 0) return null;

  return (
    <>
      <section>
        <h2 className="text-lg font-semibold mb-3">Recommended words</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {words.map((word) => (
            <button
              key={word.id}
              onClick={() => setSelectedWord(word)}
              className="text-left rounded-lg border p-3 hover:bg-muted/50 cursor-pointer transition-colors"
            >
              <div className="flex items-baseline gap-2">
                <span className="text-lg font-medium">{word.word}</span>
                {word.reading?.length > 0 && (
                  <span className="text-sm text-muted-foreground">{word.reading[0]}</span>
                )}
              </div>
              {word.meaning?.length > 0 && (
                <p className="text-sm text-muted-foreground mt-1 line-clamp-1">{word.meaning[0]}</p>
              )}
              <div className="flex items-center gap-2 mt-2">
                <span className="text-xs text-muted-foreground">#{word.frequencyRank}</span>
                {word.reinforces.length > 0 && (
                  <span className="text-xs text-blue-600">
                    Reinforces {word.reinforces.join(", ")}
                  </span>
                )}
              </div>
            </button>
          ))}
        </div>
      </section>

      {selectedWord && (
        <aside className="fixed right-0 top-0 h-full w-96 border-l bg-background shadow-lg overflow-y-auto z-50">
          <div className="p-6 space-y-6">
            <WordDetailView
              wordEntry={recommendedToWordEntry(selectedWord)}
              backLabel="Back to recommendations"
              onBack={() => setSelectedWord(null)}
              onClose={() => setSelectedWord(null)}
            />
          </div>
        </aside>
      )}
    </>
  );
}

function recommendedToWordEntry(rec: RecommendedWord): WordEntry {
  return {
    word: rec.word,
    reading: rec.reading,
    meaning: rec.meaning,
    common: true,
    frequencyRank: rec.frequencyRank,
    retentionStatus: null,
  };
}
