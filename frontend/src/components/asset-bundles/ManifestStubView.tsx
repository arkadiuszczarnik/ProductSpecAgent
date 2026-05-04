// frontend/src/components/asset-bundles/ManifestStubView.tsx
"use client";

import { useState, useMemo } from "react";
import { Copy, Check } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import {
  bundleId,
  buildManifestStub,
  getAllPossibleTriples,
} from "@/lib/asset-bundles/possible-triples";
import { cn } from "@/lib/utils";

export function ManifestStubView() {
  const selectedMissingTripleId = useAssetBundleStore((s) => s.selectedMissingTripleId);
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">("idle");

  const triple = useMemo(() => {
    if (!selectedMissingTripleId) return null;
    return (
      getAllPossibleTriples().find(
        (t) => bundleId(t.step, t.field, t.value) === selectedMissingTripleId,
      ) ?? null
    );
  }, [selectedMissingTripleId]);

  if (!triple) {
    return (
      <div className="flex h-full items-center justify-center p-6 text-sm text-muted-foreground">
        Wähle links einen fehlenden Triple, um einen manifest.json-Stub zu generieren.
      </div>
    );
  }

  const stub = buildManifestStub(triple, new Date());
  const json = JSON.stringify(stub, null, 2);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(json);
      setCopyState("copied");
    } catch {
      setCopyState("failed");
    }
    setTimeout(() => setCopyState("idle"), 1500);
  }

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <header className="border-b px-4 py-3">
        <div className="font-mono text-xs text-muted-foreground">{stub.id}</div>
        <h2 className="mt-1 text-base font-semibold">{stub.title}</h2>
      </header>
      <div className="flex-1 space-y-3 overflow-y-auto p-4">
        <div className="relative rounded-md border bg-muted/30">
          <button
            onClick={handleCopy}
            className={cn(
              "absolute right-2 top-2 inline-flex items-center gap-1 rounded-md border bg-background px-2 py-1 text-xs hover:bg-muted",
              copyState === "copied" && "text-green-600",
              copyState === "failed" && "text-red-600",
            )}
          >
            {copyState === "copied" ? (
              <>
                <Check size={12} /> Kopiert
              </>
            ) : copyState === "failed" ? (
              "Kopieren fehlgeschlagen"
            ) : (
              <>
                <Copy size={12} /> Copy
              </>
            )}
          </button>
          <pre className="overflow-x-auto p-4 font-mono text-xs">{json}</pre>
        </div>
        <p className="text-xs text-muted-foreground">
          Lege diesen Inhalt als <code className="font-mono">manifest.json</code> im Root deines Bundle-Ordners ab.
          Lade den ZIP anschließend über den „Hochgeladen"-Tab hoch.
        </p>
      </div>
    </div>
  );
}
