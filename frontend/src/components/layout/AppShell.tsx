"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { FolderKanban, Plus, Settings, Sun, Moon, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { useTheme } from "@/lib/hooks/use-theme";

interface AppShellProps {
  children: React.ReactNode;
}

interface NavItemProps {
  href: string;
  icon: React.ReactNode;
  label: string;
  active: boolean;
}

function NavItem({ href, icon, label, active }: NavItemProps) {
  return (
    <Link
      href={href}
      className={cn(
        "relative flex h-10 w-10 items-center justify-center rounded-lg transition-colors duration-150",
        active
          ? "text-sidebar-primary"
          : "text-sidebar-foreground hover:text-zinc-200"
      )}
      title={label}
    >
      {active && (
        <div className="absolute left-0 top-1/2 -translate-y-1/2 h-5 w-[3px] rounded-r-full bg-sidebar-primary" />
      )}
      {icon}
    </Link>
  );
}

function IconRail() {
  const pathname = usePathname();
  const { resolvedTheme, setTheme, theme, mounted } = useTheme();

  function cycleTheme() {
    const next = theme === "dark" ? "light" : theme === "light" ? "system" : "dark";
    setTheme(next);
  }

  return (
    <aside className="flex h-screen w-14 shrink-0 flex-col items-center bg-sidebar py-3 gap-1">
      <Link
        href="/projects"
        className="flex h-8 w-8 items-center justify-center rounded-lg bg-sidebar-primary text-white mb-2"
      >
        <Sparkles size={16} />
      </Link>

      <div className="h-px w-6 bg-zinc-700 mb-1" />

      <nav className="flex flex-col items-center gap-1 flex-1">
        <NavItem
          href="/projects"
          icon={<FolderKanban size={20} />}
          label="Projects"
          active={pathname === "/projects"}
        />
        <NavItem
          href="/projects/new"
          icon={<Plus size={20} />}
          label="New Project"
          active={pathname === "/projects/new"}
        />
      </nav>

      <div className="flex flex-col items-center gap-1">
        <button
          onClick={cycleTheme}
          className="flex h-10 w-10 items-center justify-center rounded-lg text-sidebar-foreground hover:text-zinc-200 transition-colors duration-150"
          title={mounted ? `Theme: ${theme}` : "Theme: dark"}
        >
          {!mounted || resolvedTheme === "dark" ? <Moon size={20} /> : <Sun size={20} />}
        </button>
        <button
          className="flex h-10 w-10 items-center justify-center rounded-lg text-sidebar-foreground hover:text-zinc-200 transition-colors duration-150"
          title="Settings"
        >
          <Settings size={20} />
        </button>
      </div>
    </aside>
  );
}

export function AppShell({ children }: AppShellProps) {
  const pathname = usePathname();
  const isWorkspace = pathname?.startsWith("/projects/") && pathname !== "/projects/new";

  return (
    <div className="flex h-screen bg-background">
      <IconRail />
      {isWorkspace ? (
        <>{children}</>
      ) : (
        <main className="flex-1 overflow-y-auto">{children}</main>
      )}
    </div>
  );
}
