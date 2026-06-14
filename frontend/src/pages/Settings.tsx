import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { anki, type NoteInfo } from "@/lib/anki";
import { putUserConfig, type FieldMapping } from "@/lib/api";
import { useConfig } from "@/lib/config-context";
import { guessFieldMapping } from "@/lib/heuristics";
import { SLOTS, type Slot } from "@/lib/slots";
import { cn } from "@/lib/utils";

export default function Settings() {
  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">Settings</h1>
        <p className="mt-1 text-sm text-muted-foreground">Configure how Kagura reads your Anki deck.</p>
      </header>
      <Tabs defaultValue="sync" orientation="vertical" className="flex gap-8">
        <TabsList variant="line" className="flex h-auto w-32 flex-col items-stretch gap-1 p-0">
          <TabsTrigger value="sync" className={SUB_TAB}>
            Sync
          </TabsTrigger>
        </TabsList>
        <TabsContent value="sync" className="mt-0 flex-1">
          <SyncTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

// Shared trigger classes: slightly-rounded rectangle, yellow tint when unmapped.
const TRIGGER_BASE = "rounded-md";
const UNMAPPED_TRIGGER = "border-amber-400 bg-amber-50 hover:bg-amber-50";

// Sub-tab styling that matches the sidebar rail in Layout: just text, no
// background/border/shadow/underline. The `!` on after:opacity beats the
// line-variant's `group-data-[variant=line]/tabs-list:data-active:after:opacity-100`
// rule on specificity.
const SUB_TAB =
  "justify-start border-0 bg-transparent px-2 py-1 text-sm font-normal text-muted-foreground transition-colors hover:text-foreground focus-visible:ring-0 data-active:font-semibold data-active:text-foreground data-active:after:opacity-0!";

function SyncTab() {
  const { config, setConfig } = useConfig();

  const [decks, setDecks] = useState<string[]>([]);
  const [selectedDeck, setSelectedDeck] = useState<string | null>(config.selectedDeck);
  const [fieldMapping, setFieldMapping] = useState<FieldMapping>(config.fieldMapping);
  const [studyMode, setStudyMode] = useState<"none" | "jlpt" | "kanken">(config.studyMode);
  const [targetLevel, setTargetLevel] = useState<string | null>(config.targetLevel);

  const [modelsInDeck, setModelsInDeck] = useState<string[]>([]);
  const [fieldsByModel, setFieldsByModel] = useState<Record<string, string[]>>({});

  const [loadingDecks, setLoadingDecks] = useState(true);
  const [loadingModels, setLoadingModels] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveMsg, setSaveMsg] = useState<string | null>(null);

  useEffect(() => {
    anki<string[]>("deckNames").then((names) => {
      setDecks([...names].sort());
      setLoadingDecks(false);
    });
  }, []);

  useEffect(() => {
    if (!selectedDeck) {
      setModelsInDeck([]);
      setFieldsByModel({});
      return;
    }

    let cancelled = false;
    setLoadingModels(true);

    (async () => {
      const noteIds = await anki<number[]>("findNotes", {
        query: `deck:"${selectedDeck}"`,
      });
      const notes = noteIds.length === 0 ? [] : await anki<NoteInfo[]>("notesInfo", { notes: noteIds });
      if (cancelled) return;

      const models = Array.from(new Set(notes.map((n) => n.modelName))).sort();
      setModelsInDeck(models);

      const fieldEntries = await Promise.all(
        models.map((m) => anki<string[]>("modelFieldNames", { modelName: m }).then((fields) => [m, fields] as const)),
      );
      if (cancelled) return;
      const fieldsMap = Object.fromEntries(fieldEntries);
      setFieldsByModel(fieldsMap);

      // Heuristic auto-fill: only fill slots the user hasn't already set.
      setFieldMapping((prev) => {
        const next = { ...prev };
        let changed = false;
        for (const model of models) {
          const existing = next[model] ?? {};
          const guess = guessFieldMapping(fieldsMap[model] ?? []);
          const merged = { ...guess, ...existing }; // user choices win
          if (JSON.stringify(merged) !== JSON.stringify(existing)) {
            next[model] = merged;
            changed = true;
          }
        }
        return changed ? next : prev;
      });

      setLoadingModels(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [selectedDeck]);

  function setSlot(model: string, slot: Slot, field: string) {
    setFieldMapping((prev) => {
      const next = { ...prev, [model]: { ...(prev[model] ?? {}) } };
      if (field === "") {
        delete next[model][slot];
      } else {
        next[model][slot] = field;
      }
      return next;
    });
  }

  // Keep track of selected deck + field mapping (context)
  async function save() {
    setSaving(true);
    setSaveMsg(null);
    try {
      const updated = { selectedDeck, fieldMapping, studyMode, targetLevel };
      await putUserConfig(updated);
      setConfig(updated);
      setSaveMsg("Saved.");
    } catch (e) {
      setSaveMsg(`Save failed: ${(e as Error).message}`);
    } finally {
      setSaving(false);
    }
  }

  if (loadingDecks) {
    return <p className="text-muted-foreground text-sm">Loading decks…</p>;
  }

  return (
    <div className="space-y-6">
      <section>
        <label className="mb-1.5 block text-sm font-medium">Study mode</label>
        <p className="mb-1.5 text-xs text-muted-foreground">
          Organize the kanji grid by certification level.
        </p>
        <Select
          value={studyMode}
          onValueChange={(v) => {
            setStudyMode(v as "none" | "jlpt" | "kanken");
            setTargetLevel(null);
          }}
        >
          <SelectTrigger className={cn(TRIGGER_BASE, "w-72")}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="none">None</SelectItem>
            <SelectItem value="jlpt">JLPT</SelectItem>
            <SelectItem value="kanken">Kanken</SelectItem>
          </SelectContent>
        </Select>

        {studyMode === "jlpt" && (
          <div className="mt-3">
            <label className="mb-1.5 block text-sm font-medium">Target level</label>
            <Select value={targetLevel ?? ""} onValueChange={(v) => setTargetLevel(v || null)}>
              <SelectTrigger className={cn(TRIGGER_BASE, "w-72")}>
                <SelectValue placeholder="— pick a level —" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="5">N5</SelectItem>
                <SelectItem value="4">N4</SelectItem>
                <SelectItem value="3">N3</SelectItem>
                <SelectItem value="2">N2</SelectItem>
                <SelectItem value="1">N1</SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}

        {studyMode === "kanken" && (
          <div className="mt-3">
            <label className="mb-1.5 block text-sm font-medium">Target level</label>
            <Select value={targetLevel ?? ""} onValueChange={(v) => setTargetLevel(v || null)}>
              <SelectTrigger className={cn(TRIGGER_BASE, "w-72")}>
                <SelectValue placeholder="— pick a level —" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="10">10級</SelectItem>
                <SelectItem value="9">9級</SelectItem>
                <SelectItem value="8">8級</SelectItem>
                <SelectItem value="7">7級</SelectItem>
                <SelectItem value="6">6級</SelectItem>
                <SelectItem value="5">5級</SelectItem>
                <SelectItem value="4">4級</SelectItem>
                <SelectItem value="3">3級</SelectItem>
                <SelectItem value="2.5">準2級</SelectItem>
                <SelectItem value="2">2級</SelectItem>
                <SelectItem value="1.5">準1級</SelectItem>
                <SelectItem value="1">1級</SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}
      </section>

      <section>
        <label className="mb-1.5 block text-sm font-medium">Deck to sync</label>
        <Select value={selectedDeck ?? ""} onValueChange={(v) => setSelectedDeck(v || null)}>
          <SelectTrigger className={cn(TRIGGER_BASE, "w-72", !selectedDeck && UNMAPPED_TRIGGER)}>
            <SelectValue placeholder="— pick a deck —" />
          </SelectTrigger>
          <SelectContent>
            {decks.map((d) => (
              <SelectItem key={d} value={d}>
                {d}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </section>

      {selectedDeck && loadingModels && <p className="text-muted-foreground text-sm">Scanning deck…</p>}
      {selectedDeck && !loadingModels && modelsInDeck.length === 0 && (
        <p className="text-muted-foreground text-sm">No notes found in this deck.</p>
      )}

      <div className="space-y-4">
        {modelsInDeck.map((model) => (
          <ModelCard
            key={model}
            model={model}
            fields={fieldsByModel[model] ?? []}
            mapping={fieldMapping[model] ?? {}}
            onChange={(slot, field) => setSlot(model, slot, field)}
          />
        ))}
      </div>

      <div className="flex items-center gap-3 pt-2">
        <Button onClick={save} disabled={saving || !selectedDeck} className="min-w-20">
          {saving ? "Saving…" : "Save"}
        </Button>
        {saveMsg && <span className="text-muted-foreground text-sm">{saveMsg}</span>}
      </div>
    </div>
  );
}

function ModelCard({
  model,
  fields,
  mapping,
  onChange,
}: {
  model: string;
  fields: string[];
  mapping: Record<string, string>;
  onChange: (slot: Slot, field: string) => void;
}) {
  return (
    <section className="bg-card rounded-md border p-6">
      <div className="mb-4">
        <div className="text-xs font-medium text-muted-foreground opacity-80">Note type</div>
        <h3 className="mt-0.5 font-semibold tracking-tight">{model}</h3>
      </div>
      <div className="grid grid-cols-[auto_1fr] items-center gap-x-4 gap-y-2">
        {SLOTS.map((slot) => {
          const value = mapping[slot] ?? "";
          return (
            <SlotRow key={slot} slot={slot} value={value} fields={fields} onChange={(field) => onChange(slot, field)} />
          );
        })}
      </div>
    </section>
  );
}

function SlotRow({
  slot,
  value,
  fields,
  onChange,
}: {
  slot: Slot;
  value: string;
  fields: string[];
  onChange: (field: string) => void;
}) {
  const NONE = "__none__"; // shadcn Select disallows empty string values
  return (
    <>
      <span className="text-muted-foreground text-sm">{slot}</span>
      <Select value={value === "" ? NONE : value} onValueChange={(v: string | null) => onChange(v === NONE || v === null ? "" : v)}>
        <SelectTrigger className={cn(TRIGGER_BASE, "w-full max-w-xs", !value && UNMAPPED_TRIGGER)}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={NONE}>— none —</SelectItem>
          {fields.map((f) => (
            <SelectItem key={f} value={f}>
              {f}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </>
  );
}
