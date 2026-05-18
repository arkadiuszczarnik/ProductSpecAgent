import type { FlowState } from "@/lib/api";

export function isCockpitReady(flowState?: FlowState | null): boolean {
  const steps = flowState?.steps ?? [];
  if (steps.length === 0) return false;
  return steps.every((step) => step.status === "COMPLETED");
}

export function cockpitHref(projectId: string, flowState?: FlowState | null): string {
  return isCockpitReady(flowState) ? `/projects/${projectId}/cockpit` : `/projects/${projectId}`;
}
