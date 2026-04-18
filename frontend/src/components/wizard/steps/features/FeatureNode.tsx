"use client";
import { ClassicPreset } from "rete";
import type { FeatureScope } from "@/lib/api";

export class FeatureRNode extends ClassicPreset.Node {
  width = 200;
  height = 96;
  constructor(
    public featureId: string,
    label: string,
    public scopes: FeatureScope[] = [],
  ) {
    super(label);
  }
}

interface Props { data: FeatureRNode }

export function FeatureNodeComponent({ data }: Props) {
  const hasFrontend = data.scopes.includes("FRONTEND");
  const hasBackend = data.scopes.includes("BACKEND");
  const isCore = data.scopes.length === 0;

  return (
    <div
      className="rounded-lg border bg-card shadow-sm min-w-[200px]"
      style={{ width: data.width, minHeight: data.height }}
    >
      <div className="px-3 py-2 border-b flex items-center justify-between gap-2">
        <span className="text-sm font-medium truncate">{data.label}</span>
        <div className="flex items-center gap-1">
          {isCore && <Badge label="Core" color="neutral" />}
          {hasFrontend && <Badge label="FE" color="cyan" />}
          {hasBackend && <Badge label="BE" color="violet" />}
        </div>
      </div>
    </div>
  );
}

function Badge({ label, color }: { label: string; color: "cyan" | "violet" | "neutral" }) {
  const classes = color === "cyan"
    ? "bg-cyan-500/15 text-cyan-300"
    : color === "violet"
    ? "bg-violet-500/15 text-violet-300"
    : "bg-muted text-muted-foreground";
  return <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${classes}`}>{label}</span>;
}
