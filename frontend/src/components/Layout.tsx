import { NavLink, Outlet } from "react-router-dom";
import { useConfig } from "@/lib/config-context";
import { SLOTS } from "@/lib/slots";
import { cn } from "@/lib/utils";

export default function Layout() {
  const needsAttention = useSettingsBadge();

  return (
    <div className="bg-background text-foreground flex min-h-screen">
      <nav className="bg-sidebar border-sidebar-border flex w-56 flex-col gap-1 border-r px-5 py-8">
        <div className="mb-8 px-2">
          <div className="text-base font-semibold tracking-tight">Kagura</div>
        </div>
        <RailLink to="/">Home</RailLink>
        <RailLink to="/settings" badge={needsAttention}>
          Settings
        </RailLink>
      </nav>
      <main className="flex-1 px-10 py-10">
        <div className="mx-auto max-w-4xl">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

function RailLink({ to, children, badge }: { to: string; children: React.ReactNode; badge?: boolean }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        cn(
          "flex items-center justify-between px-2 py-1 text-sm transition-colors",
          isActive ? "text-foreground font-semibold" : "text-muted-foreground hover:text-foreground",
        )
      }
    >
      <span>{children}</span>
      {badge && (
        <span
          title="Setup incomplete"
          className="inline-flex h-4 w-4 items-center justify-center rounded-full bg-amber-400 text-[10px] font-bold text-white"
        >
          !
        </span>
      )}
    </NavLink>
  );
}

function useSettingsBadge(): boolean {
  const { config, modelsInSelectedDeck } = useConfig();
  if (!config.selectedDeck) return true;
  if (modelsInSelectedDeck === null) return false;
  return modelsInSelectedDeck.some((model) => SLOTS.some((slot) => !config.fieldMapping[model]?.[slot]));
}
