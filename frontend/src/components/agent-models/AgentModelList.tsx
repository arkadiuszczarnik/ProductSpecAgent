"use client";
import { Circle } from "lucide-react";
import type { AgentModelInfo } from "@/lib/api";
import { cn } from "@/lib/utils";

interface AgentModelListProps {
  items: AgentModelInfo[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function AgentModelList({ items, selectedId, onSelect }: AgentModelListProps) {
  return (
    <div className="overflow-y-auto py-2">
      {items.map((it) => (
        <button
          key={it.agentId}
          onClick={() => onSelect(it.agentId)}
          className={cn(
            "w-full px-4 py-2 text-left text-sm flex items-center gap-2 hover:bg-muted/50 transition-colors",
            selectedId === it.agentId && "bg-muted",
          )}
        >
          <span className="flex-1 truncate">{it.displayName}</span>
          {it.isOverridden && (
            <Circle size={8} className="fill-primary text-primary" aria-label="Überschrieben" />
          )}
        </button>
      ))}
    </div>
  );
}
