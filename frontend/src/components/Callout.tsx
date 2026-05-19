import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type Variant = "info" | "news" | "blog" | "warn";

// Soft pastel callout card. Colors come from CSS custom properties in index.css.
export function Callout({
  variant = "info",
  eyebrow,
  children,
  className,
}: {
  variant?: Variant;
  eyebrow?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "rounded-lg border px-5 py-4",
        VARIANT_CLASS[variant],
        className,
      )}
    >
      {eyebrow && (
        <div className="mb-1 text-xs font-medium opacity-80">{eyebrow}</div>
      )}
      <div className="text-sm leading-relaxed text-foreground/90">
        {children}
      </div>
    </div>
  );
}

const VARIANT_CLASS: Record<Variant, string> = {
  info: "bg-[var(--callout-info-bg)] border-[var(--callout-info-border)] text-[var(--callout-info-fg)]",
  news: "bg-[var(--callout-news-bg)] border-[var(--callout-news-border)] text-[var(--callout-news-fg)]",
  blog: "bg-[var(--callout-blog-bg)] border-[var(--callout-blog-border)] text-[var(--callout-blog-fg)]",
  warn: "bg-[var(--callout-warn-bg)] border-[var(--callout-warn-border)] text-[var(--callout-warn-fg)]",
};
