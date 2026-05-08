"use client";

import { useWizardStore } from "@/lib/stores/wizard-store";
import { AlertTriangle, Lock } from "lucide-react";
import { cn } from "@/lib/utils";
import { useStepBlockers } from "@/lib/hooks/use-step-blockers";

export function StepIndicator() {
  const { data, activeStep, setActiveStep, visibleSteps } = useWizardStore();
  const steps = visibleSteps();
  const activeIdx = steps.findIndex((s) => s.key === activeStep);

  const { isBlocked, openClarifications, pendingDecisions } =
    useStepBlockers(activeStep);

  let blockerBadge = "";
  if (isBlocked) {
    const parts: string[] = [];
    if (openClarifications > 0) parts.push(`${openClarifications} Klaerung${openClarifications > 1 ? "en" : ""}`);
    if (pendingDecisions > 0) parts.push(`${pendingDecisions} Entscheidung${pendingDecisions > 1 ? "en" : ""}`);
    blockerBadge = parts.join(", ") + " offen";
  }

  return (
    <div className="flex h-12 items-center overflow-x-auto border-b border-border bg-background px-8">
      <div className="flex w-full min-w-max items-center justify-between gap-1">
        {steps.map((step, i) => {
          const stepData = data?.steps[step.key];
          const isCompleted = !!stepData?.completedAt;
          const isActive = activeStep === step.key;
          const isAfterBlocked = isBlocked && i > activeIdx;
          const isLocked = !isCompleted && !isActive && isAfterBlocked;

          const canClick = isCompleted || isActive || (!isAfterBlocked && !isLocked);

          return (
            <div key={step.key} className="flex min-w-0 flex-1 items-center last:flex-none">
              <button
                onClick={() => canClick && setActiveStep(step.key)}
                title={step.label}
                className={cn(
                  "flex items-center rounded-md bg-transparent p-1 transition-colors duration-150 hover:bg-secondary",
                  !canClick && "cursor-not-allowed hover:bg-transparent"
                )}
                disabled={!canClick}
              >
                <div
                  className={cn(
                    "flex h-6 w-6 items-center justify-center rounded-full text-[11px] font-semibold transition-colors duration-150",
                    isCompleted && "bg-[oklch(0.65_0.15_160)] text-black",
                    isActive && !isCompleted && !isBlocked && "bg-primary text-primary-foreground ring-[3px] ring-primary/30",
                    isActive && isBlocked && "bg-amber-500 text-white ring-[3px] ring-amber-500/30",
                    isLocked && "bg-muted text-muted-foreground/50",
                    !isActive && !isCompleted && !isLocked && "bg-secondary text-muted-foreground"
                  )}
                >
                  {isActive && isBlocked ? (
                    <AlertTriangle size={13} />
                  ) : isLocked ? (
                    <Lock size={11} />
                  ) : (
                    i + 1
                  )}
                </div>
                {isActive && isBlocked && <span className="sr-only">{blockerBadge}</span>}
              </button>
              {i < steps.length - 1 && (
                <div className={cn(
                  "mx-1 h-[2px] min-w-3 flex-1 transition-colors",
                  isCompleted ? "bg-[oklch(0.65_0.15_160)]" : "bg-border"
                )} />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
