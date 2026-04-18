"use client";
import { FeaturesGraphEditor } from "./features/FeaturesGraphEditor";

export function FeaturesForm({ projectId }: { projectId: string }) {
  return <FeaturesGraphEditor projectId={projectId} />;
}
