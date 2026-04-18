"use client";
import { Plus, Trash2 } from "lucide-react";
import { useShallow } from "zustand/react/shallow";
import { Button } from "@/components/ui/button";
import {
  useWizardStore,
  selectFeatures,
  selectEdges,
} from "@/lib/stores/wizard-store";
import { getAllowedScopes } from "@/lib/category-step-config";
import type { FeatureScope } from "@/lib/api";

interface Props { projectId: string }

export function FeaturesFallbackList({ projectId: _projectId }: Props) {
  const features = useWizardStore(useShallow(selectFeatures));
  const edges = useWizardStore(useShallow(selectEdges));
  const category = useWizardStore((s) => s.data?.steps.IDEA?.fields.category as string | undefined);
  const addFeature = useWizardStore((s) => s.addFeature);
  const updateFeature = useWizardStore((s) => s.updateFeature);
  const removeFeature = useWizardStore((s) => s.removeFeature);
  const addEdge = useWizardStore((s) => s.addEdge);
  const removeEdge = useWizardStore((s) => s.removeEdge);
  const allowedScopes = getAllowedScopes(category);

  return (
    <div className="space-y-4">
      <Button size="sm" onClick={() => addFeature({
        title: "Neues Feature", description: "",
        scopes: allowedScopes.slice(0, 1),
        scopeFields: {}, position: { x: 0, y: 0 },
      })}>
        <Plus size={14} /> Feature
      </Button>

      {features.length === 0 && (
        <p className="text-sm text-muted-foreground">Noch keine Features.</p>
      )}

      {features.map((f) => {
        const deps = edges.filter((e) => e.to === f.id).map((e) => e.from);
        return (
          <div key={f.id} className="rounded-lg border bg-card p-3 space-y-2">
            <div className="flex items-center justify-between">
              <input
                value={f.title}
                onChange={(e) => updateFeature(f.id, { title: e.target.value })}
                className="flex-1 bg-transparent text-sm font-medium"
              />
              <button onClick={() => removeFeature(f.id)} className="text-muted-foreground hover:text-destructive">
                <Trash2 size={13} />
              </button>
            </div>
            <textarea
              value={f.description}
              onChange={(e) => updateFeature(f.id, { description: e.target.value })}
              placeholder="Beschreibung..."
              rows={2}
              className="w-full resize-none rounded-md border bg-input px-3 py-1.5 text-xs"
            />
            {allowedScopes.length > 0 && (
              <div className="flex gap-2">
                {allowedScopes.map((s: FeatureScope) => (
                  <label key={s} className="flex items-center gap-1 text-xs">
                    <input
                      type="checkbox"
                      checked={f.scopes.includes(s)}
                      onChange={(e) => {
                        const next = e.target.checked
                          ? [...f.scopes, s]
                          : f.scopes.filter((x) => x !== s);
                        updateFeature(f.id, { scopes: next });
                      }}
                    />
                    {s === "FRONTEND" ? "Frontend" : "Backend"}
                  </label>
                ))}
              </div>
            )}
            <div>
              <span className="text-xs text-muted-foreground">Abhaengig von:</span>
              <select
                multiple
                value={deps}
                onChange={(e) => {
                  const picked = Array.from(e.target.selectedOptions).map((o) => o.value);
                  edges.filter((edge) => edge.to === f.id).forEach((edge) => removeEdge(edge.id));
                  picked.forEach((src) => addEdge(src, f.id));
                }}
                className="w-full mt-1 rounded-md border bg-input px-2 py-1 text-xs"
              >
                {features.filter((other) => other.id !== f.id).map((other) => (
                  <option key={other.id} value={other.id}>{other.title}</option>
                ))}
              </select>
            </div>
          </div>
        );
      })}
    </div>
  );
}
