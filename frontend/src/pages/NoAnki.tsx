import { Callout } from "@/components/Callout";
import { Button } from "@/components/ui/button";

export default function NoAnki() {
  return (
    <div className="bg-background text-foreground flex min-h-screen items-center justify-center p-6">
      <div className="w-full max-w-md space-y-5">
        <header>
          <div className="text-[10px] font-semibold tracking-[0.15em] uppercase text-muted-foreground">
            Kagura
          </div>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">
            Anki isn&apos;t running
          </h1>
        </header>

        <Callout variant="info" eyebrow="To continue">
          <ol className="list-decimal space-y-1.5 pl-5">
            <li>
              Install the{" "}
              <a
                className="font-medium underline underline-offset-2"
                href="https://ankiweb.net/shared/info/2055492159"
              >
                AnkiConnect
              </a>{" "}
              add-on (code{" "}
              <code className="font-mono text-xs">2055492159</code>).
            </li>
            <li>Start Anki and keep it open.</li>
          </ol>
        </Callout>

        <Button onClick={() => window.location.reload()} className="w-full">
          Retry
        </Button>
      </div>
    </div>
  );
}
