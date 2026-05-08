"use client";

import { useEffect } from "react";
import { HelpCircle, ChevronDown, ChevronRight } from "lucide-react";
import { ClarificationCard } from "./ClarificationCard";
import { useClarificationStore } from "@/lib/stores/clarification-store";
import { cn } from "@/lib/utils";

interface ClarificationListProps {
  projectId: string;
}

export function ClarificationList({ projectId }: ClarificationListProps) {
  const { clarifications, loading, loadClarifications, selectedClarificationId, selectClarification } = useClarificationStore();

  useEffect(() => {
    loadClarifications(projectId);
  }, [projectId]); // eslint-disable-line react-hooks/exhaustive-deps

  const open = clarifications.filter((c) => c.status === "OPEN");
  const answered = clarifications.filter((c) => c.status === "ANSWERED");

  if (loading) {
    return <div className="p-4 text-sm text-muted-foreground">Klaerungen werden geladen...</div>;
  }

  if (clarifications.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 p-8 text-center text-muted-foreground">
        <HelpCircle size={24} className="opacity-30" />
        <p className="text-sm">Noch keine Klaerungen.</p>
        <p className="text-xs">Klaerungen erscheinen, sobald der Agent Luecken oder Widersprueche erkennt.</p>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto">
      <div className="px-4 py-3 border-b flex items-center justify-between">
        <div className="flex items-center gap-2">
          <HelpCircle size={15} className="text-amber-400" />
          <span className="text-sm font-semibold">Clarifications</span>
        </div>
        {open.length > 0 && (
          <span className="rounded-full bg-amber-500/20 px-2 py-0.5 text-[10px] text-amber-400 font-medium">
            {open.length} offen
          </span>
        )}
      </div>
      <div className="p-3 space-y-2">
        {open.map((c) => (
          <ClarificationItem
            key={c.id} clarification={c} projectId={projectId}
            isOpen={selectedClarificationId === c.id}
            onToggle={() => selectClarification(selectedClarificationId === c.id ? null : c.id)}
          />
        ))}
        {answered.length > 0 && open.length > 0 && <div className="border-t my-3" />}
        {answered.map((c) => (
          <ClarificationItem
            key={c.id} clarification={c} projectId={projectId}
            isOpen={selectedClarificationId === c.id}
            onToggle={() => selectClarification(selectedClarificationId === c.id ? null : c.id)}
          />
        ))}
      </div>
    </div>
  );
}

function ClarificationItem({ clarification, projectId, isOpen, onToggle }: {
  clarification: import("@/lib/api").Clarification;
  projectId: string;
  isOpen: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="rounded-lg border bg-card">
      <button
        onClick={onToggle}
        className={cn(
          "flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm transition-colors hover:bg-muted/50",
          isOpen && "border-b"
        )}
      >
        {isOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        <span className="flex-1 truncate font-medium">{clarification.question}</span>
        <span className={cn(
          "rounded-full px-1.5 py-0.5 text-[10px] font-medium",
          clarification.status === "ANSWERED" ? "bg-[oklch(0.65_0.15_160)] text-black" : "bg-amber-500/20 text-amber-400"
        )}>
          {clarification.status === "ANSWERED" ? "Erledigt" : "Offen"}
        </span>
      </button>
      {isOpen && (
        <div className="p-2">
          <ClarificationCard clarification={clarification} projectId={projectId} />
        </div>
      )}
    </div>
  );
}
