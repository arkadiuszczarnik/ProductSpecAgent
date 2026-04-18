"use client";

import { ArrowLeft, ArrowRight, Loader2, Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { useStepBlockers } from "@/lib/hooks/use-step-blockers";
import { BlockerBanner } from "./BlockerBanner";
import { IdeaForm } from "./steps/IdeaForm";
import { ProblemForm } from "./steps/ProblemForm";
import { TargetAudienceForm } from "./steps/TargetAudienceForm";
import { ScopeForm } from "./steps/ScopeForm";
import { MvpForm } from "./steps/MvpForm";
import { FeaturesForm } from "./steps/FeaturesForm";
import { ArchitectureForm } from "./steps/ArchitectureForm";
import { BackendForm } from "./steps/BackendForm";
import { FrontendForm } from "./steps/FrontendForm";

const FORM_MAP: Record<string, React.ComponentType<{ projectId: string }>> = {
  IDEA: IdeaForm,
  PROBLEM: ProblemForm,
  TARGET_AUDIENCE: TargetAudienceForm,
  SCOPE: ScopeForm,
  MVP: MvpForm,
  FEATURES: FeaturesForm,
  ARCHITECTURE: ArchitectureForm,
  BACKEND: BackendForm,
  FRONTEND: FrontendForm,
};

interface WizardFormProps {
  projectId: string;
  onBlockerClick?: (tab: "decisions" | "clarifications") => void;
}

type NavigableBlockerTab = "decisions" | "clarifications";

export function WizardForm({ projectId, onBlockerClick }: WizardFormProps) {
  const { activeStep, saving, chatPending, completeStep, goPrev, visibleSteps } = useWizardStore();
  const { isBlocked, blockerSummary, firstBlockerTab } = useStepBlockers(activeStep);

  const steps = visibleSteps();
  const stepInfo = steps.find((s) => s.key === activeStep);
  const stepIdx = steps.findIndex((s) => s.key === activeStep);
  const isFirst = stepIdx === 0;
  const isLast = stepIdx === steps.length - 1;
  const isWorking = saving || chatPending;

  const FormComponent = FORM_MAP[activeStep];

  async function handleNext() {
    if (isBlocked) {
      if (firstBlockerTab !== "empty-graph") {
        onBlockerClick?.(firstBlockerTab as NavigableBlockerTab);
      }
      return;
    }
    await completeStep(projectId, activeStep);
  }

  return (
    <div className="flex flex-col h-full">
      {/* Form Content */}
      <div className="flex-1 overflow-y-auto px-8 py-6">
        <div className="max-w-2xl mx-auto">
          <h2 className="text-lg font-bold mb-1">{stepInfo?.label ?? activeStep}</h2>
          <p className="text-sm text-muted-foreground mb-6">
            Schritt {stepIdx + 1} von {steps.length}
          </p>
          {FormComponent && <FormComponent projectId={projectId} />}
        </div>
      </div>

      {/* Navigation */}
      <div className="shrink-0 border-t px-8 py-3 flex flex-col gap-2 bg-card/50">
        {isBlocked && (
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
              disabled={isWorking || isBlocked}
              className="gap-1.5"
            >
              {isLast ? (
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
