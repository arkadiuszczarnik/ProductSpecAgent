"use client";
import { Trash2 } from "lucide-react";
import { useWizardStore } from "@/lib/stores/wizard-store";
import type { FeatureScope, WizardFeature } from "@/lib/api";
import {
  SCOPE_FIELD_LABELS,
  SCOPE_FIELDS_BY_SCOPE,
} from "@/lib/step-field-labels";

interface Props {
  feature: WizardFeature;
  allowedScopes: FeatureScope[]; // [] means Library (Core mode)
  onDelete: () => void;
}

export function FeatureSidePanel({ feature, allowedScopes, onDelete }: Props) {
  const { updateFeature } = useWizardStore();

  const activeFieldKeys =
    allowedScopes.length === 0
      ? SCOPE_FIELDS_BY_SCOPE.CORE
      : allowedScopes
          .filter((s) => feature.scopes.includes(s))
          .flatMap((s) => SCOPE_FIELDS_BY_SCOPE[s]);

  function toggleScope(s: FeatureScope) {
    const next = feature.scopes.includes(s)
      ? feature.scopes.filter((x) => x !== s)
      : [...feature.scopes, s];
    updateFeature(feature.id, { scopes: next });
  }

  function setField(key: string, val: string) {
    updateFeature(feature.id, {
      scopeFields: { ...feature.scopeFields, [key]: val },
    });
  }

  return (
    <div className="p-4 space-y-4 text-sm">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">Feature bearbeiten</h3>
        <button
          onClick={onDelete}
          aria-label="Feature loeschen"
          className="text-muted-foreground hover:text-destructive"
        >
          <Trash2 size={14} />
        </button>
      </div>

      <label className="block">
        <span className="text-xs text-muted-foreground">Titel</span>
        <input
          value={feature.title}
          onChange={(e) => updateFeature(feature.id, { title: e.target.value })}
          className="mt-1 w-full rounded-md border bg-input px-3 py-2"
        />
      </label>

      {allowedScopes.length > 1 && (
        <div>
          <span className="text-xs text-muted-foreground">Scope</span>
          <div className="mt-1 flex gap-2">
            {allowedScopes.map((s) => (
              <button
                key={s}
                onClick={() => toggleScope(s)}
                type="button"
                className={`px-3 py-1 rounded-full text-xs border ${
                  feature.scopes.includes(s)
                    ? "bg-primary/15 border-primary text-foreground"
                    : "border-border text-muted-foreground"
                }`}
              >
                {s === "FRONTEND" ? "Frontend" : "Backend"}
              </button>
            ))}
          </div>
        </div>
      )}

      <label className="block">
        <span className="text-xs text-muted-foreground">Beschreibung</span>
        <textarea
          value={feature.description}
          onChange={(e) =>
            updateFeature(feature.id, { description: e.target.value })
          }
          rows={3}
          className="mt-1 w-full resize-none rounded-md border bg-input px-3 py-2"
        />
      </label>

      {activeFieldKeys.map((key) => (
        <label key={key} className="block">
          <span className="text-xs text-muted-foreground">
            {SCOPE_FIELD_LABELS[key] ?? key}
          </span>
          <textarea
            value={feature.scopeFields[key] ?? ""}
            onChange={(e) => setField(key, e.target.value)}
            rows={2}
            className="mt-1 w-full resize-none rounded-md border bg-input px-3 py-2"
          />
        </label>
      ))}
    </div>
  );
}
