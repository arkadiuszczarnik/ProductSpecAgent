"use client";

import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Cpu,
  Download,
  FileText,
  FolderKanban,
  Layers,
  Loader2,
  Monitor,
  Save,
  Sparkles,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { useStepBlockers } from "@/lib/hooks/use-step-blockers";
import { BlockerBanner } from "./BlockerBanner";
import { IdeaForm } from "./steps/IdeaForm";
import { ProblemForm } from "./steps/ProblemForm";
import { MvpForm } from "./steps/MvpForm";
import { FeaturesForm } from "./steps/FeaturesForm";
import { ArchitectureForm } from "./steps/ArchitectureForm";
import { BackendForm } from "./steps/BackendForm";
import { FrontendForm } from "./steps/FrontendForm";
import { DesignWorkbenchForm } from "./steps/design-workbench/DesignWorkbenchForm";

const FORM_MAP: Record<string, React.ComponentType<{ projectId: string }>> = {
  IDEA: IdeaForm,
  PROBLEM: ProblemForm,
  MVP: MvpForm,
  FEATURES: FeaturesForm,
  ARCHITECTURE: ArchitectureForm,
  BACKEND: BackendForm,
  FRONTEND: FrontendForm,
  DESIGN: DesignWorkbenchForm,
};

const STEP_HELP: Record<string, string> = {
  IDEA: "Verdichte Produktname, Vision und Kategorie zu einem belastbaren Startpunkt.",
  PROBLEM: "Klaere Problem, Zielgruppe und messbaren Nutzen, bevor Features entstehen.",
  FEATURES: "Priorisiere den Funktionsumfang und halte Abhaengigkeiten sichtbar.",
  MVP: "Lege fest, was in die erste umsetzbare Version gehoert.",
  DESIGN: "Verbinde Produktanforderungen mit UI-Richtung, Screens und Assets.",
  ARCHITECTURE: "Definiere Systemform, Datenhaltung und Deployment-Rahmen.",
  BACKEND: "Spezifiziere API, Authentifizierung und serverseitige Komponenten.",
  FRONTEND: "Schaerfe Framework, UI-Bibliothek, Styling und Frontend-Schnittstellen.",
};

function StepIcon({ step }: { step: string }) {
  const iconClass = "h-[15px] w-[15px]";
  switch (step) {
    case "IDEA":
      return <Sparkles className={iconClass} />;
    case "PROBLEM":
      return <BookOpen className={iconClass} />;
    case "FEATURES":
      return <Layers className={iconClass} />;
    case "MVP":
      return <FolderKanban className={iconClass} />;
    case "DESIGN":
      return <FileText className={iconClass} />;
    case "ARCHITECTURE":
      return <Layers className={iconClass} />;
    case "BACKEND":
      return <Cpu className={iconClass} />;
    case "FRONTEND":
      return <Monitor className={iconClass} />;
    default:
      return <FileText className={iconClass} />;
  }
}

interface WizardFormProps {
  projectId: string;
  onBlockerClick?: (tab: "decisions" | "clarifications") => void;
  onExportClick?: () => void;
}

type NavigableBlockerTab = "decisions" | "clarifications";

export function WizardForm({ projectId, onBlockerClick, onExportClick }: WizardFormProps) {
  const { activeStep, saving, chatPending, completeStep, goPrev, visibleSteps, progression } = useWizardStore();
  const { isBlocked, blockerSummary, firstBlockerTab } = useStepBlockers(activeStep);

  const steps = visibleSteps();
  const stepIdx = steps.findIndex((s) => s.key === activeStep);
  const isFirst = stepIdx === 0;
  const isLast = stepIdx === steps.length - 1;
  const isWorking = saving || chatPending;
  const primaryAction = progression?.primaryAction;
  const exportReady = primaryAction?.type === "OPEN_EXPORT";
  const wizardDone = exportReady && (isLast || activeStep === "DESIGN");

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
      <div className="flex-1 overflow-y-auto">
        <div className="flex h-[52px] items-center gap-2 border-b border-border px-4">
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
            <StepIcon step={activeStep} />
          </div>
          <div className="flex min-w-0 max-w-full items-baseline gap-2">
            <h1 className="shrink-0 whitespace-nowrap text-sm font-semibold text-foreground">{steps[stepIdx]?.label ?? activeStep}</h1>
            <p className="min-w-0 truncate text-[11px] text-muted-foreground">
              {STEP_HELP[activeStep] ?? "Bearbeite diesen Schritt und lasse offene Punkte vom Agenten klaeren."}
            </p>
          </div>
        </div>
        <div className={activeStep === "FEATURES" || activeStep === "DESIGN" ? "h-[calc(100%-52px)] px-8 py-6" : "mx-auto max-w-2xl px-8 py-6"}>
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
                {chatPending ? "Agent antwortet..." : "Speichert..."}
              </span>
            )}
            {/* DESIGN owns its own complete/skip CTAs until export is ready. */}
            {(activeStep !== "DESIGN" || wizardDone) && (
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
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
