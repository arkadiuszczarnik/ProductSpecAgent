"use client";

import { useState } from "react";
import { HelpCircle, CheckCircle2, Loader2, AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import type { Clarification } from "@/lib/api";
import { useClarificationStore } from "@/lib/stores/clarification-store";
import { cn } from "@/lib/utils";

interface ClarificationCardProps {
  clarification: Clarification;
  projectId: string;
}

export function ClarificationCard({ clarification, projectId }: ClarificationCardProps) {
  const { answerClarification } = useClarificationStore();
  const [answer, setAnswer] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const isAnswered = clarification.status === "ANSWERED";

  async function handleSubmit() {
    if (!answer.trim() || submitting) return;
    setSubmitting(true);
    await answerClarification(projectId, clarification.id, answer.trim());
    setSubmitting(false);
  }

  return (
    <Card className={cn("border-amber-500/20", isAnswered && "opacity-70")}>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {isAnswered ? (
              <CheckCircle2 size={16} className="text-[oklch(0.65_0.15_160)]" />
            ) : (
              <HelpCircle size={16} className="text-amber-400" />
            )}
            <CardTitle className="text-sm">{clarification.question}</CardTitle>
          </div>
          <span className={cn(
            "rounded-full px-2 py-0.5 text-[10px] font-medium",
            isAnswered ? "bg-[oklch(0.65_0.15_160)] text-black" : "bg-amber-500/20 text-amber-400"
          )}>
            {isAnswered ? "Answered" : "Open"}
          </span>
        </div>
      </CardHeader>

      <CardContent className="space-y-3">
        <div className="flex items-start gap-2 rounded-md border border-amber-500/10 bg-amber-500/5 px-3 py-2 text-xs text-muted-foreground">
          <AlertTriangle size={12} className="mt-0.5 shrink-0 text-amber-400" />
          <span>{clarification.reason}</span>
        </div>

        {isAnswered && clarification.answer && (
          <div className="text-sm">
            <span className="font-medium text-[oklch(0.65_0.15_160)]">Answer: </span>
            {clarification.answer}
          </div>
        )}

        {!isAnswered && (
          <Textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            placeholder="Provide your answer..."
            rows={3}
            className="resize-none"
          />
        )}
      </CardContent>

      {!isAnswered && (
        <CardFooter className="justify-end">
          <Button size="sm" disabled={!answer.trim() || submitting} onClick={handleSubmit}>
            {submitting ? <><Loader2 size={14} className="animate-spin" /> Submitting...</> : "Answer"}
          </Button>
        </CardFooter>
      )}
    </Card>
  );
}
