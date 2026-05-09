"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type {
  StepType,
  WizardOptionCatalog,
  WizardOptionCategory,
  WizardOptionField,
} from "@/lib/api";
import { useWizardOptionsStore } from "@/lib/stores/wizard-options-store";
import { Button } from "@/components/ui/button";
import { WizardOptionFieldEditor } from "@/components/wizard-options/WizardOptionFieldEditor";
import { cn } from "@/lib/utils";

const STEP_ORDER: StepType[] = [
  "IDEA",
  "PROBLEM",
  "FEATURES",
  "MVP",
  "DESIGN",
  "ARCHITECTURE",
  "BACKEND",
  "FRONTEND",
];

function cloneCatalog(catalog: WizardOptionCatalog): WizardOptionCatalog {
  return {
    ...catalog,
    categories: catalog.categories.map((category) => ({
      ...category,
      visibleSteps: [...category.visibleSteps],
      allowedScopes: [...category.allowedScopes],
      fields: category.fields.map((field) => ({
        ...field,
        options: field.options.map((option) => ({ ...option })),
      })),
    })),
  };
}

function catalogFingerprint(catalog: WizardOptionCatalog | null): string {
  return catalog ? JSON.stringify(catalog) : "";
}

function groupFieldsByStep(fields: WizardOptionField[]): Array<{
  step: StepType;
  fields: WizardOptionField[];
}> {
  const grouped = new Map<StepType, WizardOptionField[]>();
  for (const field of fields) {
    grouped.set(field.step, [...(grouped.get(field.step) ?? []), field]);
  }

  return [...grouped.entries()]
    .sort(([left], [right]) => STEP_ORDER.indexOf(left) - STEP_ORDER.indexOf(right))
    .map(([step, stepFields]) => ({ step, fields: stepFields }));
}

function replaceField(
  category: WizardOptionCategory,
  nextField: WizardOptionField,
): WizardOptionCategory {
  return {
    ...category,
    fields: category.fields.map((field) => (
      field.step === nextField.step && field.key === nextField.key ? nextField : field
    )),
  };
}

export function WizardOptionsAdminPage() {
  const adminCatalog = useWizardOptionsStore((state) => state.adminCatalog);
  const loading = useWizardOptionsStore((state) => state.loading);
  const saving = useWizardOptionsStore((state) => state.saving);
  const error = useWizardOptionsStore((state) => state.error);
  const loadAdminCatalog = useWizardOptionsStore((state) => state.loadAdminCatalog);
  const saveAdminCatalog = useWizardOptionsStore((state) => state.saveAdminCatalog);
  const resetAdminCatalog = useWizardOptionsStore((state) => state.resetAdminCatalog);

  const [draft, setDraft] = useState<WizardOptionCatalog | null>(null);
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);

  const applyCatalogDraft = useCallback((catalog: WizardOptionCatalog) => {
    setDraft(cloneCatalog(catalog));
    setSelectedCategoryId((current) => (
      current && catalog.categories.some((category) => category.id === current)
        ? current
        : catalog.categories[0]?.id ?? null
    ));
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadCatalog() {
      await loadAdminCatalog();
      const nextCatalog = useWizardOptionsStore.getState().adminCatalog;
      if (!cancelled && nextCatalog) {
        applyCatalogDraft(nextCatalog);
      }
    }

    void loadCatalog();

    return () => {
      cancelled = true;
    };
  }, [applyCatalogDraft, loadAdminCatalog]);

  const dirty = useMemo(() => (
    catalogFingerprint(draft) !== catalogFingerprint(adminCatalog)
  ), [adminCatalog, draft]);
  const busy = saving || loading;

  const selectedCategory = draft?.categories.find((category) => (
    category.id === selectedCategoryId
  )) ?? null;

  const groupedFields = useMemo(() => (
    selectedCategory ? groupFieldsByStep(selectedCategory.fields) : []
  ), [selectedCategory]);

  function updateSelectedCategory(nextCategory: WizardOptionCategory) {
    setDraft((current) => {
      if (!current) return current;

      return {
        ...current,
        categories: current.categories.map((category) => (
          category.id === nextCategory.id ? nextCategory : category
        )),
      };
    });
  }

  function discardDraft() {
    if (!adminCatalog) return;
    setDraft(cloneCatalog(adminCatalog));
  }

  async function handleSave() {
    if (!draft) return;
    try {
      await saveAdminCatalog(draft);
      const nextCatalog = useWizardOptionsStore.getState().adminCatalog;
      if (nextCatalog) {
        applyCatalogDraft(nextCatalog);
      }
    } catch {
      // Store owns the inline error message.
    }
  }

  async function handleReset() {
    if (!window.confirm("Wizard Optionen wirklich zuruecksetzen?")) {
      return;
    }

    try {
      await resetAdminCatalog();
      const nextCatalog = useWizardOptionsStore.getState().adminCatalog;
      if (nextCatalog) {
        applyCatalogDraft(nextCatalog);
      }
    } catch {
      // Store owns the inline error message.
    }
  }

  return (
    <div className="flex h-screen flex-col">
      <header className="flex items-center justify-between gap-4 border-b px-6 py-4">
        <div>
          <h1 className="text-xl font-semibold">Wizard Optionen</h1>
          <p className="text-sm text-muted-foreground">
            Auswahlwerte je Kategorie, Schritt und Feld pflegen.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={discardDraft}
            disabled={!dirty || !adminCatalog || busy}
          >
            Aenderungen verwerfen
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={handleReset}
            disabled={busy}
          >
            Zuruecksetzen
          </Button>
          <Button
            type="button"
            onClick={handleSave}
            disabled={!draft || busy}
          >
            {saving ? "Speichert..." : "Speichern"}
          </Button>
        </div>
      </header>

      {error ? (
        <div className="mx-6 mt-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      <div className="flex min-h-0 flex-1">
        <aside className="w-64 shrink-0 overflow-y-auto border-r px-3 py-4">
          <div className="mb-2 px-2 text-xs font-medium uppercase text-muted-foreground">
            Kategorien
          </div>
          <div className="space-y-1">
            {draft?.categories.map((category) => (
              <button
                key={category.id}
                type="button"
                onClick={() => setSelectedCategoryId(category.id)}
                disabled={busy}
                className={cn(
                  "flex w-full items-center justify-between rounded-md px-2 py-2 text-left text-sm transition-colors",
                  category.id === selectedCategoryId
                    ? "bg-secondary text-secondary-foreground"
                    : "text-muted-foreground hover:bg-secondary/60 hover:text-foreground",
                  busy && "cursor-not-allowed opacity-60",
                )}
              >
                <span className="truncate">{category.label}</span>
                <span className="ml-2 text-xs">{category.fields.length}</span>
              </button>
            ))}
          </div>
        </aside>

        <main className="min-w-0 flex-1 overflow-y-auto px-6 py-4">
          {!draft && loading ? (
            <div className="text-sm text-muted-foreground">Laedt Optionen...</div>
          ) : null}

          {draft && !selectedCategory ? (
            <div className="text-sm text-muted-foreground">Keine Kategorie ausgewaehlt.</div>
          ) : null}

          {selectedCategory ? (
            <div className="space-y-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h2 className="text-lg font-semibold">{selectedCategory.label}</h2>
                  <p className="text-sm text-muted-foreground">
                    {selectedCategory.visibleSteps.join(", ")}
                  </p>
                </div>
                {dirty ? (
                  <span className="rounded-md bg-amber-100 px-2 py-1 text-xs font-medium text-amber-900">
                    Ungespeicherte Aenderungen
                  </span>
                ) : null}
              </div>

              {groupedFields.length === 0 ? (
                <div className="rounded-md border px-3 py-6 text-sm text-muted-foreground">
                  Diese Kategorie hat keine Optionsfelder.
                </div>
              ) : (
                groupedFields.map((group) => (
                  <section key={group.step} className="rounded-md border">
                    <div className="border-b bg-muted/30 px-3 py-2">
                      <h2 className="text-sm font-semibold">{group.step}</h2>
                    </div>
                    <div className="px-3">
                      {group.fields.map((field) => (
                        <WizardOptionFieldEditor
                          key={`${selectedCategory.id}-${field.step}-${field.key}`}
                          field={field}
                          disabled={busy}
                          onChange={(nextField) => {
                            updateSelectedCategory(replaceField(selectedCategory, nextField));
                          }}
                        />
                      ))}
                    </div>
                  </section>
                ))
              )}
            </div>
          ) : null}
        </main>
      </div>
    </div>
  );
}
