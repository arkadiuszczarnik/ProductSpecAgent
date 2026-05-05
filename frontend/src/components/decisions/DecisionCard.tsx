"use client";

import { useState } from "react";
import { Check, X, Star, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import type { Decision } from "@/lib/api";
import { useDecisionStore } from "@/lib/stores/decision-store";
import { cn } from "@/lib/utils";

interface DecisionCardProps {
  decision: Decision;
  projectId: string;
}

export function DecisionCard({ decision, projectId }: DecisionCardProps) {
  const { resolveDecision } = useDecisionStore();
  const [selectedOption, setSelectedOption] = useState<string | null>(null);
  const [rationale, setRationale] = useState("");
  const [resolving, setResolving] = useState(false);

  const isResolved = decision.status === "RESOLVED";
  const canResolve = selectedOption !== null && rationale.trim().length > 0 && !resolving;

  async function handleResolve() {
    if (!selectedOption || !rationale.trim()) return;
    setResolving(true);
    await resolveDecision(projectId, decision.id, selectedOption, rationale.trim());
    setResolving(false);
  }

  return (
    <Card className="border-primary/20">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">{decision.title}</CardTitle>
          <span className={cn(
            "rounded-full px-2 py-0.5 text-xs font-medium",
            isResolved
              ? "bg-[oklch(0.65_0.15_160)] text-black"
              : "bg-primary text-primary-foreground"
          )}>
            {isResolved ? "Resolved" : "Pending"}
          </span>
        </div>
        <span className="text-xs text-muted-foreground">{decision.stepType.replace("_", " ")}</span>
      </CardHeader>

      <CardContent className="space-y-3">
        {decision.options.map((opt) => {
          const isChosen = isResolved ? decision.chosenOptionId === opt.id : selectedOption === opt.id;
          const isDimmed = isResolved && decision.chosenOptionId !== opt.id;

          return (
            <div
              key={opt.id}
              onClick={() => !isResolved && setSelectedOption(opt.id)}
              className={cn(
                "rounded-lg border p-3 transition-colors",
                isChosen ? "border-primary bg-primary/5" : "border-border",
                isDimmed && "opacity-40",
                !isResolved && "cursor-pointer hover:border-primary/50"
              )}
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-medium">{opt.label}</span>
                <div className="flex items-center gap-1">
                  {opt.recommended && (
                    <span className="flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-[10px] text-primary">
                      <Star size={10} /> Recommended
                    </span>
                  )}
                  {isChosen && !isResolved && (
                    <span className="rounded-full bg-primary px-2 py-0.5 text-[10px] text-primary-foreground">
                      Selected
                    </span>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div>
                  {opt.pros.map((pro, i) => (
                    <div key={i} className="flex items-start gap-1 text-[oklch(0.65_0.15_160)]">
                      <Check size={11} className="mt-0.5 shrink-0" />
                      <span>{pro}</span>
                    </div>
                  ))}
                </div>
                <div>
                  {opt.cons.map((con, i) => (
                    <div key={i} className="flex items-start gap-1 text-destructive">
                      <X size={11} className="mt-0.5 shrink-0" />
                      <span>{con}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          );
        })}

        {/* AI Recommendation */}
        <div className="rounded-md border border-primary/20 bg-primary/5 px-3 py-2 text-xs text-muted-foreground">
          <span className="font-medium text-primary">AI Recommendation: </span>
          {decision.recommendation}
        </div>

        {/* Resolve form (only if pending) */}
        {!isResolved && selectedOption && (
          <div className="space-y-2 pt-1">
            <Textarea
              value={rationale}
              onChange={(e) => setRationale(e.target.value)}
              placeholder="Why did you choose this option?"
              rows={2}
              className="resize-none"
            />
          </div>
        )}

        {/* Resolved rationale */}
        {isResolved && decision.rationale && (
          <div className="text-xs text-muted-foreground">
            <span className="font-medium">Rationale: </span>{decision.rationale}
          </div>
        )}
      </CardContent>

      {!isResolved && selectedOption && (
        <CardFooter className="justify-end">
          <Button size="sm" disabled={!canResolve} onClick={handleResolve}>
            {resolving ? <><Loader2 size={14} className="animate-spin" /> Resolving...</> : "Confirm Choice"}
          </Button>
        </CardFooter>
      )}
    </Card>
  );
}
