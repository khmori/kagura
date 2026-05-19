import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import "./App.css";
import Layout from "./components/Layout";
import { ConfigProvider } from "./lib/config-context";
import Home from "./pages/Home";
import NoAnki from "./pages/NoAnki";
import Settings from "./pages/Settings";
import { anki, type NoteInfo, pingAnki } from "./lib/anki";
import { getUserConfig, type UserConfig } from "./lib/api";

type Status = "checking" | "no-anki" | "ready";

function App() {
  const [status, setStatus] = useState<Status>("checking");
  const [config, setConfig] = useState<UserConfig | null>(null);
  const [modelsInSelectedDeck, setModelsInSelectedDeck] = useState<
    string[] | null
  >(null);

  // Boot: ping Anki, then load the user's saved config.
  useEffect(() => {
    pingAnki().then(async (up) => {
      if (!up) {
        setStatus("no-anki");
        return;
      }
      const loaded = await getUserConfig();
      setConfig(loaded);
      setStatus("ready");
    });
  }, []);

  // Whenever the selected deck changes, recompute the set of note types in it.
  // The badge logic in Layout reads this.
  useEffect(() => {
    if (!config?.selectedDeck) {
      setModelsInSelectedDeck(null);
      return;
    }
    let cancelled = false;
    (async () => {
      const noteIds = await anki<number[]>("findNotes", {
        query: `deck:"${config.selectedDeck}"`,
      });
      const notes =
        noteIds.length === 0
          ? []
          : await anki<NoteInfo[]>("notesInfo", { notes: noteIds });
      if (cancelled) return;
      setModelsInSelectedDeck(
        Array.from(new Set(notes.map((n) => n.modelName))).sort(),
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [config?.selectedDeck]);

  if (status === "checking") return <div>Checking for Anki…</div>;
  if (status === "no-anki") return <NoAnki />;
  if (!config) return null; // unreachable: status === "ready" implies config is loaded

  return (
    <ConfigProvider value={{ config, setConfig, modelsInSelectedDeck }}>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </ConfigProvider>
  );
}

export default App;
