"use client";
import { Circle } from "lucide-react";
import type { PromptListItem } from "@/lib/api";
import { cn } from "@/lib/utils";

interface PromptListProps {
  items: PromptListItem[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function PromptList({ items, selectedId, onSelect }: PromptListProps) {
  const grouped = items.reduce<Record<string, PromptListItem[]>>((acc, item) => {
    (acc[item.agent] ??= []).push(item);
    return acc;
  }, {});

  return (
    <div className="overflow-y-auto py-2">
      {Object.entries(grouped).map(([agent, agentItems]) => (
        <div key={agent} className="mb-4">
          <div className="px-4 py-1 text-xs font-semibold uppercase text-muted-foreground">
            {agent}
          </div>
          {agentItems.map((it) => (
            <button
              key={it.id}
              onClick={() => onSelect(it.id)}
              className={cn(
                "w-full px-4 py-2 text-left text-sm flex items-center gap-2 hover:bg-muted/50 transition-colors",
                selectedId === it.id && "bg-muted",
              )}
            >
              <span className="flex-1 truncate">{it.title}</span>
              {it.isOverridden && (
                <Circle size={8} className="fill-primary text-primary" aria-label="Überschrieben" />
              )}
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}
