import { createContext, useContext, type ReactNode } from "react";
import type { UserConfig } from "./api";

// Shared app state, hydrated by App.tsx after boot and consumed by every page.
// `modelsInSelectedDeck` is null while it's loading (or if no deck is picked).
export interface ConfigContextValue {
  config: UserConfig;
  setConfig: (config: UserConfig) => void;
  modelsInSelectedDeck: string[] | null;
}

const ConfigContext = createContext<ConfigContextValue | null>(null);

export function ConfigProvider({
  value,
  children,
}: {
  value: ConfigContextValue;
  children: ReactNode;
}) {
  return (
    <ConfigContext.Provider value={value}>{children}</ConfigContext.Provider>
  );
}

export function useConfig(): ConfigContextValue {
  const ctx = useContext(ConfigContext);
  if (!ctx) throw new Error("useConfig must be used inside <ConfigProvider>");
  return ctx;
}
