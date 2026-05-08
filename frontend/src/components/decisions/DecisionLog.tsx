"use client";

import { useEffect } from "react";
import { Scale, ChevronDown, ChevronRight } from "lucide-react";
import { DecisionCard } from "./DecisionCard";
import { useDecisionStore } from "@/lib/stores/decision-store";
import { cn } from "@/lib/utils";

interface DecisionLogProps {
  projectId: string;
  autoOpenDecisionId?: string | null;
}

export function DecisionLog({ projectId, autoOpenDecisionId }: DecisionLogProps) {
  const { decisions, loading, loadDecisions, selectedDecisionId, selectDecision } = useDecisionStore();

  useEffect(() => {
    loadDecisions(projectId);
  }, [projectId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (autoOpenDecisionId) selectDecision(autoOpenDecisionId);
  }, [autoOpenDecisionId]); // eslint-disable-line react-hooks/exhaustive-deps

  const pending = decisions.filter((d) => d.status === "PENDING");
  const resolved = decisions.filter((d) => d.status === "RESOLVED");

  if (loading) {
    return <div className="p-4 text-sm text-muted-foreground">Entscheidungen werden geladen...</div>;
  }

  if (decisions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 p-8 text-center text-muted-foreground">
        <Scale size={24} className="opacity-30" />
        <p className="text-sm">Noch keine Entscheidungen.</p>
        <p className="text-xs">Entscheidungen erscheinen, sobald der Agent einen Auswahlpunkt erkennt.</p>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto">
      <div className="px-4 py-3 border-b flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Scale size={15} className="text-primary" />
          <span className="text-sm font-semibold">Decisions</span>
        </div>
        {pending.length > 0 && (
          <span className="rounded-full bg-primary px-2 py-0.5 text-[10px] text-primary-foreground font-medium">
            {pending.length} offen
          </span>
        )}
      </div>

      <div className="p-3 space-y-2">
        {pending.map((d) => (
          <DecisionItem
            key={d.id}
            decision={d}
            projectId={projectId}
            isOpen={selectedDecisionId === d.id}
            onToggle={() => selectDecision(selectedDecisionId === d.id ? null : d.id)}
          />
        ))}

        {resolved.length > 0 && pending.length > 0 && (
          <div className="border-t my-3" />
        )}

        {resolved.map((d) => (
          <DecisionItem
            key={d.id}
            decision={d}
            projectId={projectId}
            isOpen={selectedDecisionId === d.id}
            onToggle={() => selectDecision(selectedDecisionId === d.id ? null : d.id)}
          />
        ))}
      </div>
    </div>
  );
}

function DecisionItem({
  decision, projectId, isOpen, onToggle,
}: {
  decision: import("@/lib/api").Decision;
  projectId: string;
  isOpen: boolean;
  onToggle: () => void;
}) {
  const isResolved = decision.status === "RESOLVED";
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
        <span className="flex-1 truncate font-medium">{decision.title}</span>
        <span className={cn(
          "rounded-full px-1.5 py-0.5 text-[10px] font-medium",
          isResolved ? "bg-[oklch(0.65_0.15_160)] text-black" : "bg-primary/10 text-primary"
        )}>
          {isResolved ? "Erledigt" : "Offen"}
        </span>
      </button>
      {isOpen && (
        <div className="p-2">
          <DecisionCard decision={decision} projectId={projectId} />
        </div>
      )}
    </div>
  );
}
