"use client";

import { useDecisionStore } from "@/lib/stores/decision-store";
import { useClarificationStore } from "@/lib/stores/clarification-store";
import { useWizardStore, selectFeatures } from "@/lib/stores/wizard-store";
import { useShallow } from "zustand/react/shallow";

export interface StepBlockers {
  pendingDecisions: number;
  openClarifications: number;
  isBlocked: boolean;
  blockerCount: number;
  blockerSummary: string;
  firstBlockerTab: "decisions" | "clarifications" | "empty-graph";
}

export function useStepBlockers(stepKey: string): StepBlockers {
  const decisions = useDecisionStore((s) => s.decisions);
  const clarifications = useClarificationStore((s) => s.clarifications);
  const features = useWizardStore(useShallow(selectFeatures));

  const pendingDecisions = decisions.filter(
    (d) => d.stepType === stepKey && d.status === "PENDING"
  ).length;

  const openClarifications = clarifications.filter(
    (c) => c.stepType === stepKey && c.status === "OPEN"
  ).length;

  const emptyGraph = stepKey === "FEATURES" && features.length === 0;

  const blockerCount = pendingDecisions + openClarifications;
  // decisions/clarifications take priority; empty-graph is the fallback
  const isBlocked = blockerCount > 0 || emptyGraph;

  let blockerSummary = "";
  if (pendingDecisions > 0 && openClarifications > 0) {
    blockerSummary = `${pendingDecisions} Entscheidung${pendingDecisions > 1 ? "en" : ""} und ${openClarifications} Klaerung${openClarifications > 1 ? "en" : ""} blockieren den naechsten Schritt`;
  } else if (pendingDecisions > 0) {
    blockerSummary = `${pendingDecisions} offene Entscheidung${pendingDecisions > 1 ? "en" : ""} blockier${pendingDecisions > 1 ? "en" : "t"} den naechsten Schritt`;
  } else if (openClarifications > 0) {
    blockerSummary = `${openClarifications} offene Klaerung${openClarifications > 1 ? "en" : ""} blockier${openClarifications > 1 ? "en" : "t"} den naechsten Schritt`;
  } else if (emptyGraph) {
    blockerSummary = "Fuege mindestens ein Feature hinzu.";
  }

  const firstBlockerTab: "decisions" | "clarifications" | "empty-graph" =
    blockerCount > 0
      ? openClarifications > 0
        ? "clarifications"
        : "decisions"
      : "empty-graph";

  return {
    pendingDecisions,
    openClarifications,
    isBlocked,
    blockerCount,
    blockerSummary,
    firstBlockerTab,
  };
}
