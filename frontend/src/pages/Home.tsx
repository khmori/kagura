import { Callout } from "@/components/Callout";
import { Button } from "@/components/ui/button";
import { useConfig } from "@/lib/config-context";
import { probe } from "@/lib/anki";
import { sync } from "@/lib/sync";

export default function Home() {
  const { config } = useConfig();

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">Home</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Your Japanese learning dashboard.
        </p>
      </header>

      {!config.selectedDeck && (
        <Callout variant="news" eyebrow="Setup needed">
          Pick a deck and confirm your field mappings in{" "}
          <span className="font-semibold">Settings</span> to start syncing.
        </Callout>
      )}

      <section className="flex flex-wrap gap-2">
        <Button onClick={sync} disabled={!config.selectedDeck}>
          Sync with AnkiConnect
        </Button>
        <Button variant="outline" onClick={probe}>
          Probe
        </Button>
      </section>
    </div>
  );
}
