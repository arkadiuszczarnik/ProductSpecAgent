"use client";

import { Package } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { BundleUpload } from "./BundleUpload";
import { cn } from "@/lib/utils";
import type { StepType } from "@/lib/api";

const STEP_OPTIONS: Array<{ value: StepType | "ALL"; label: string }> = [
  { value: "ALL", label: "Alle Steps" },
  { value: "BACKEND", label: "Backend" },
  { value: "FRONTEND", label: "Frontend" },
  { value: "ARCHITECTURE", label: "Architecture" },
];

export function BundleList() {
  const { bundles, selectedBundleId, filterStep, loading, setFilter, select } = useAssetBundleStore();

  const visible = filterStep === "ALL" ? bundles : bundles.filter((b) => b.step === filterStep);

  return (
    <div className="flex h-full flex-col">
      <div className="border-b p-3 space-y-2">
        <BundleUpload />
        <select
          value={filterStep}
          onChange={(e) => setFilter(e.target.value as StepType | "ALL")}
          className="w-full text-xs rounded-md border bg-background px-2 py-1.5"
        >
          {STEP_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
      </div>
      <div className="flex-1 overflow-y-auto">
        {loading && <div className="p-4 text-sm text-muted-foreground">Lädt …</div>}
        {!loading && visible.length === 0 && (
          <div className="p-4 text-sm text-muted-foreground">
            Keine Bundles. ZIP hochladen, um zu starten.
          </div>
        )}
        {visible.map((b) => (
          <button
            key={b.id}
            onClick={() => select(b.id)}
            className={cn(
              "w-full text-left px-3 py-2.5 border-b transition-colors hover:bg-muted/50",
              selectedBundleId === b.id ? "bg-muted" : "",
            )}
          >
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Package size={12} />
              <span>{b.step.toLowerCase()}.{b.field}.{b.value}</span>
            </div>
            <div className="font-medium text-sm mt-1">{b.title}</div>
            <div className="text-xs text-muted-foreground mt-0.5">{b.fileCount} Dateien · v{b.version}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
