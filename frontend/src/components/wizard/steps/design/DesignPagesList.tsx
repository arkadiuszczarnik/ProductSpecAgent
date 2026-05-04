"use client";

import type { DesignPage } from "@/lib/api";
import { cn } from "@/lib/utils";

interface Props {
  pages: DesignPage[];
  activeId: string | null;
  onSelect: (page: DesignPage) => void;
}

export function DesignPagesList({ pages, activeId, onSelect }: Props) {
  if (pages.length === 0) {
    return (
      <div className="px-3 py-4 text-xs text-muted-foreground">
        Keine Pages erkannt.
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-1 p-2">
      <div className="px-2 py-1 text-xs font-semibold text-muted-foreground">
        Pages ({pages.length})
      </div>
      {pages.map((p) => (
        <button
          key={p.id}
          onClick={() => onSelect(p)}
          className={cn(
            "flex flex-col items-start gap-0.5 rounded-md px-2 py-2 text-left text-sm transition-colors",
            activeId === p.id
              ? "bg-primary/10 text-primary"
              : "text-foreground hover:bg-muted",
          )}
        >
          <span className="font-medium leading-tight">{p.label}</span>
          <span className="text-[10px] text-muted-foreground">
            {p.sectionTitle} · {p.width}×{p.height}
          </span>
        </button>
      ))}
    </div>
  );
}
