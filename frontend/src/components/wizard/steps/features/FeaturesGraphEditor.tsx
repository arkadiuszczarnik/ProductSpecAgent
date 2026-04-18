"use client";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useShallow } from "zustand/react/shallow";
import { useRete } from "rete-react-plugin";
import { Plus, Sparkles, LayoutGrid } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useResizable } from "@/lib/hooks/use-resizable";
import {
  useWizardStore,
  selectFeatures,
  selectEdges,
  selectFeatureById,
} from "@/lib/stores/wizard-store";
import { proposeFeatures } from "@/lib/api";
import { getAllowedScopes } from "@/lib/category-step-config";
import { createFeaturesEditor, type FeaturesEditorContext } from "./editor";
import { FeatureSidePanel } from "./FeatureSidePanel";
import { FeaturesFallbackList } from "./FeaturesFallbackList";

interface Props {
  projectId: string;
}

export function FeaturesGraphEditor({ projectId }: Props) {
  // Atomic store subscriptions — each re-renders only when its own slice changes.
  // NEVER destructure `useWizardStore()` without a selector — that subscribes to
  // every store update and forces a full re-render of this component on every
  // auto-save response or other-step mutation.
  const features = useWizardStore(useShallow(selectFeatures));
  const edges = useWizardStore(useShallow(selectEdges));
  const category = useWizardStore(
    (s) => s.data?.steps.IDEA?.fields.category as string | undefined,
  );
  const addFeature = useWizardStore((s) => s.addFeature);
  const removeFeature = useWizardStore((s) => s.removeFeature);
  const addEdge = useWizardStore((s) => s.addEdge);
  const removeEdge = useWizardStore((s) => s.removeEdge);
  const moveFeature = useWizardStore((s) => s.moveFeature);
  const applyProposal = useWizardStore((s) => s.applyProposal);

  const allowedScopes = useMemo(() => getAllowedScopes(category), [category]);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [viewportWide, setViewportWide] = useState(true);
  const ctxRef = useRef<FeaturesEditorContext | null>(null);

  // Atomic selector for the active feature — returns the live WizardFeature
  // object so the side panel always renders the latest values (including
  // store-side updates during auto-save).
  const selected =
    useWizardStore(
      useShallow((s) => (selectedId ? selectFeatureById(selectedId)(s) : undefined)),
    ) ?? null;

  useEffect(() => {
    const check = () => setViewportWide(window.innerWidth >= 768);
    check();
    window.addEventListener("resize", check);
    return () => window.removeEventListener("resize", check);
  }, []);

  const { width: panelWidth, handleProps } = useResizable({
    initialWidth: 360,
    minWidth: 280,
    maxWidth: 560,
  });

  const editorFactory = useCallback(
    async (container: HTMLElement) => {
      const ctx = await createFeaturesEditor(container);
      ctx.onNodeSelect(setSelectedId);
      ctx.onConnectionCreate((from, to) => addEdge(from, to));
      ctx.onNodeMove((id, x, y) => moveFeature(id, { x, y }));
      ctx.onConnectionRemove((edgeId) => removeEdge(edgeId));
      ctxRef.current = ctx;
      return ctx as unknown as { destroy: () => void };
    },
    [addEdge, moveFeature, removeEdge],
  );
  const [ref] = useRete(editorFactory);

  // Fingerprint of the graph content — changes only when a node/edge is actually
  // added, removed, renamed, rescoped, or repositioned. We depend on the string,
  // not the array identities, so auto-save responses (which replace `data` but
  // not the feature/edge contents) don't re-trigger applyGraph.
  const fingerprint = useMemo(() => {
    const f = features
      .map(
        (x) =>
          `${x.id}:${x.title}:${[...x.scopes].sort().join(",")}:${x.position.x}:${x.position.y}`,
      )
      .join("|");
    const e = edges.map((x) => `${x.id}:${x.from}>${x.to}`).join("|");
    return `${f}##${e}`;
  }, [features, edges]);

  useEffect(() => {
    if (!ctxRef.current) return;
    ctxRef.current.applyGraph(features, edges);
    // Depend on fingerprint only — features/edges identities are already
    // captured in it. The editor's coalescing queue (see editor.ts) serializes
    // concurrent calls safely.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fingerprint]);

  if (!viewportWide) return <FeaturesFallbackList projectId={projectId} />;

  return (
    <div className="flex h-[600px] min-h-[400px] rounded-lg border bg-background overflow-hidden">
      <div className="flex-1 min-w-0 flex flex-col">
        <div
          ref={ref}
          className="flex-1"
          style={{ background: "var(--color-background)" }}
        />
        <div className="border-t px-3 py-2 flex items-center gap-2">
          <Button
            size="sm"
            onClick={() => {
              const id = addFeature({
                title: "Neues Feature",
                description: "",
                scopes: allowedScopes.slice(0, 1),
                scopeFields: {},
                position: { x: 0, y: 0 },
              });
              setSelectedId(id);
            }}
          >
            <Plus size={14} /> Feature
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={async () => {
              if (features.length > 0 && !confirm("Bestehenden Graph ueberschreiben?"))
                return;
              try {
                const g = await proposeFeatures(projectId);
                applyProposal(g);
              } catch {
                alert("Vorschlag fehlgeschlagen");
              }
            }}
          >
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
