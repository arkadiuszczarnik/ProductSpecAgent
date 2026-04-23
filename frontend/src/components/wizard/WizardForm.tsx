"use client";

import { ArrowLeft, ArrowRight, Download, Loader2, Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { useProjectStore } from "@/lib/stores/project-store";
import { useStepBlockers } from "@/lib/hooks/use-step-blockers";
import { BlockerBanner } from "./BlockerBanner";
import { IdeaForm } from "./steps/IdeaForm";
import { ProblemForm } from "./steps/ProblemForm";
import { MvpForm } from "./steps/MvpForm";
import { FeaturesForm } from "./steps/FeaturesForm";
import { ArchitectureForm } from "./steps/ArchitectureForm";
import { BackendForm } from "./steps/BackendForm";
import { FrontendForm } from "./steps/FrontendForm";

const FORM_MAP: Record<string, React.ComponentType<{ projectId: string }>> = {
  IDEA: IdeaForm,
  PROBLEM: ProblemForm,
  MVP: MvpForm,
  FEATURES: FeaturesForm,
  ARCHITECTURE: ArchitectureForm,
  BACKEND: BackendForm,
  FRONTEND: FrontendForm,
};

interface WizardFormProps {
  projectId: string;
  onBlockerClick?: (tab: "decisions" | "clarifications") => void;
  onExportClick?: () => void;
}

type NavigableBlockerTab = "decisions" | "clarifications";

export function WizardForm({ projectId, onBlockerClick, onExportClick }: WizardFormProps) {
  const { activeStep, saving, chatPending, completeStep, goPrev, visibleSteps } = useWizardStore();
  const flowState = useProjectStore((s) => s.flowState);
  const { isBlocked, blockerSummary, firstBlockerTab } = useStepBlockers(activeStep);

  const steps = visibleSteps();
  const stepIdx = steps.findIndex((s) => s.key === activeStep);
  const isFirst = stepIdx === 0;
  const isLast = stepIdx === steps.length - 1;
  const isWorking = saving || chatPending;

  // Once the last visible step is COMPLETED in flow state, the wizard is done —
  // clicking the button should open Export instead of re-triggering the agent.
  const lastStepKey = steps[steps.length - 1]?.key;
  const lastStepCompleted =
    !!lastStepKey &&
    flowState?.steps.find((s) => s.stepType === lastStepKey)?.status === "COMPLETED";
  const wizardDone = isLast && lastStepCompleted;

  const FormComponent = FORM_MAP[activeStep];

  async function handleNext() {
    if (wizardDone) {
      onExportClick?.();
      return;
    }
    if (isBlocked) {
      if (firstBlockerTab !== "empty-graph") {
        onBlockerClick?.(firstBlockerTab as NavigableBlockerTab);
      }
      return;
    }
    const result = await completeStep(projectId, activeStep);
    if (result?.exportTriggered) {
      onExportClick?.();
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Form Content */}
      <div className="flex-1 overflow-y-auto px-8 py-6">
        <div className={activeStep === "FEATURES" ? "" : "max-w-2xl mx-auto"}>
          {FormComponent && <FormComponent projectId={projectId} />}
        </div>
      </div>

      {/* Navigation */}
      <div className="shrink-0 border-t px-8 py-3 flex flex-col gap-2 bg-card/50">
        {isBlocked && !wizardDone && (
          <BlockerBanner
            summary={blockerSummary}
            onClick={
              firstBlockerTab !== "empty-graph"
                ? () => onBlockerClick?.(firstBlockerTab as NavigableBlockerTab)
                : undefined
            }
          />
        )}
        <div className="flex items-center justify-between">
          <Button variant="ghost" size="sm" onClick={goPrev} disabled={isFirst || isWorking} className="gap-1.5">
            <ArrowLeft size={14} /> Zurueck
          </Button>
          <div className="flex items-center gap-2">
            {isWorking && (
              <span className="flex items-center gap-1 text-xs text-muted-foreground">
                <Loader2 size={12} className="animate-spin" />
                {chatPending ? "Agent antwortet..." : "Saving..."}
              </span>
            )}
            <Button
              size="sm"
              onClick={handleNext}
              disabled={isWorking || (isBlocked && !wizardDone)}
              className="gap-1.5"
            >
              {wizardDone ? (
                <><Download size={14} /> Exportieren</>
              ) : isLast ? (
                <><Save size={14} /> Abschliessen</>
              ) : (
                <>Weiter <ArrowRight size={14} /></>
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
