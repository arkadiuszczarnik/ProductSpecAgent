"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRete } from "rete-react-plugin";
import { Plus, Sparkles, LayoutGrid } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useResizable } from "@/lib/hooks/use-resizable";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { proposeFeatures } from "@/lib/api";
import { getAllowedScopes } from "@/lib/category-step-config";
import { createFeaturesEditor, type FeaturesEditorContext } from "./editor";
import { FeatureSidePanel } from "./FeatureSidePanel";

interface Props {
  projectId: string;
}

export function FeaturesGraphEditor({ projectId }: Props) {
  const {
    data,
    addFeature,
    removeFeature,
    addEdge,
    moveFeature,
    applyProposal,
    getFeatures,
    getEdges,
  } = useWizardStore();
  const category = (data?.steps.IDEA?.fields.category as string | undefined) ?? undefined;
  const allowedScopes = getAllowedScopes(category);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [cycleWarning, setCycleWarning] = useState<string | null>(null);
  const ctxRef = useRef<FeaturesEditorContext | null>(null);
  const cycleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const { width: panelWidth, handleProps } = useResizable({
    initialWidth: 360,
    minWidth: 280,
    maxWidth: 560,
  });

  // Keep the latest store actions in refs so the editor factory stays stable.
  // This mirrors the pattern used in SpecFlowGraph (avoid recreating the editor on every render).
  const addEdgeRef = useRef(addEdge);
  const moveFeatureRef = useRef(moveFeature);
  useEffect(() => {
    addEdgeRef.current = addEdge;
  }, [addEdge]);
  useEffect(() => {
    moveFeatureRef.current = moveFeature;
  }, [moveFeature]);
  useEffect(() => {
    return () => {
      if (cycleTimerRef.current) clearTimeout(cycleTimerRef.current);
    };
  }, []);

  const editorFactory = useCallback(async (container: HTMLElement) => {
    const ctx = await createFeaturesEditor(container);
    ctx.onNodeSelect((id) => setSelectedId(id));
    ctx.onConnectionCreate((fromFeatureId, toFeatureId) => {
      const ok = addEdgeRef.current(fromFeatureId, toFeatureId);
      if (!ok) {
        setCycleWarning("Zyklus verhindert: Die Verbindung wuerde einen Kreis erzeugen.");
        if (cycleTimerRef.current) clearTimeout(cycleTimerRef.current);
        cycleTimerRef.current = setTimeout(() => setCycleWarning(null), 3000);
      }
      return ok;
    });
    ctx.onNodeMove((id, x, y) => moveFeatureRef.current(id, { x, y }));
    ctxRef.current = ctx;
    return ctx as unknown as { destroy: () => void };
  }, []);
  const [ref] = useRete(editorFactory);

  // Sync store -> editor whenever wizard data changes.
  useEffect(() => {
    if (!ctxRef.current) return;
    ctxRef.current.applyGraph(getFeatures(), getEdges());
  }, [data, getFeatures, getEdges]);

  const features = getFeatures();
  const selected = features.find((f) => f.id === selectedId) ?? null;

  async function handlePropose() {
    if (features.length > 0) {
      if (!confirm("Den bestehenden Graph ueberschreiben?")) return;
    }
    try {
      const graph = await proposeFeatures(projectId);
      applyProposal(graph);
    } catch {
      alert("Vorschlag fehlgeschlagen. Bitte manuell anlegen.");
    }
  }

  function handleAddFeature() {
    const defaultScope = allowedScopes.slice(0, 1);
    const id = addFeature({
      title: "Neues Feature",
      description: "",
      scopes: defaultScope,
      scopeFields: {},
      position: { x: 0, y: 0 },
    });
    setSelectedId(id);
  }

  return (
    <div className="flex h-[600px] min-h-[400px] rounded-lg border bg-background overflow-hidden">
      <div className="flex-1 min-w-0 flex flex-col">
        <div
          ref={ref}
          className="flex-1"
          style={{ background: "var(--color-background)" }}
        />
        {cycleWarning && (
          <div className="border-t bg-amber-500/10 text-amber-300 text-xs px-3 py-1.5">
            {cycleWarning}
          </div>
        )}
        <div className="border-t px-3 py-2 flex items-center gap-2">
          <Button size="sm" onClick={handleAddFeature}>
            <Plus size={14} /> Feature
          </Button>
          <Button size="sm" variant="outline" onClick={handlePropose}>
            <Sparkles size={14} /> Vorschlagen
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => ctxRef.current?.autoLayout()}
          >
            <LayoutGrid size={14} /> Auto-Layout
          </Button>
        </div>
      </div>

      <div
        className="w-1 cursor-col-resize bg-border hover:bg-primary/20"
        {...handleProps}
      />

      <div
        style={{ width: panelWidth }}
        className="shrink-0 overflow-y-auto border-l"
      >
        {selected ? (
          <FeatureSidePanel
            feature={selected}
            allowedScopes={allowedScopes}
            onDelete={() => {
              removeFeature(selected.id);
              setSelectedId(null);
            }}
          />
        ) : (
          <p className="p-4 text-sm text-muted-foreground">
            Waehle ein Feature, um es zu bearbeiten.
          </p>
        )}
      </div>
    </div>
  );
}
