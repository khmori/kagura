import { Callout } from "@/components/Callout";
import { Button } from "@/components/ui/button";

export default function NoAnki() {
  return (
    <div className="bg-background text-foreground flex min-h-screen items-start justify-center px-6 pt-32">
      <div className="w-full max-w-md space-y-5">
        <header>
          <h1 className="text-2xl font-semibold tracking-tight">
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
