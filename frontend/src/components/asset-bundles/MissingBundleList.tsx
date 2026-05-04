// frontend/src/components/asset-bundles/MissingBundleList.tsx
"use client";

import { PackageOpen } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import {
  bundleId,
  groupByStepAndField,
  RELEVANT_STEPS,
} from "@/lib/asset-bundles/possible-triples";
import { cn } from "@/lib/utils";

export function MissingBundleList() {
  const selectedMissingTripleId = useAssetBundleStore((s) => s.selectedMissingTripleId);
  const selectMissingTriple = useAssetBundleStore((s) => s.selectMissingTriple);
  const getMissingTriples = useAssetBundleStore((s) => s.getMissingTriples);
  // bundles ist hier nicht direkt genutzt, aber die Subscription stellt sicher,
  // dass der Component re-rendert, wenn sich die Bundle-Liste ändert.
  useAssetBundleStore((s) => s.bundles);

  const missing = getMissingTriples();

  if (missing.length === 0) {
    return (
      <div className="p-4 text-sm text-muted-foreground">
        Vollständige Abdeckung — alle bekannten Triples haben ein Bundle.
      </div>
    );
  }

  const grouped = groupByStepAndField(missing);

  return (
    <div className="flex h-full flex-col overflow-y-auto">
      {RELEVANT_STEPS.map((step) => {
        const fields = grouped[step];
        const fieldNames = Object.keys(fields).sort();
        if (fieldNames.length === 0) return null;
        return (
          <section key={step}>
            <h2 className="sticky top-0 z-10 bg-background px-3 py-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground border-b">
              {step}
            </h2>
            {fieldNames.map((field) => (
              <div key={field}>
                <h3 className="bg-muted/30 px-3 py-1.5 text-xs font-medium text-muted-foreground">
                  {field} ({fields[field].length})
                </h3>
                {fields[field].map((t) => {
                  const id = bundleId(t.step, t.field, t.value);
                  return (
                    <button
                      key={id}
                      onClick={() => selectMissingTriple(id)}
                      className={cn(
                        "w-full border-b px-3 py-2.5 text-left transition-colors hover:bg-muted/50",
                        selectedMissingTripleId === id ? "bg-muted" : "",
                      )}
                    >
                      <div className="flex items-center gap-2 font-mono text-xs text-muted-foreground">
                        <PackageOpen size={12} />
                        <span>{id}</span>
                      </div>
                      <div className="mt-1 text-sm font-medium">{t.value}</div>
                    </button>
                  );
                })}
              </div>
            ))}
          </section>
        );
      })}
    </div>
  );
}
