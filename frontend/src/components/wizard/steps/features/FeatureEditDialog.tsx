"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { Trash2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import type { FeatureScope, WizardFeature } from "@/lib/api";
import { SCOPE_FIELD_LABELS, SCOPE_FIELDS_BY_SCOPE } from "@/lib/step-field-labels";

interface FeatureEditDialogProps {
  feature: WizardFeature | null;
  allowedScopes: FeatureScope[];
  open: boolean;
  onClose: () => void;
  onSave: (patch: Partial<WizardFeature>) => void;
  onDelete: () => void;
}

type DraftFeature = Pick<WizardFeature,
  "title" | "description" | "scopes" | "scopeFields">;

function snapshot(f: WizardFeature): DraftFeature {
  return {
    title: f.title,
    description: f.description,
    scopes: [...f.scopes],
    scopeFields: { ...f.scopeFields },
  };
}

function equalDraft(a: DraftFeature, b: DraftFeature): boolean {
  if (a.title !== b.title || a.description !== b.description) return false;
  if (a.scopes.length !== b.scopes.length) return false;
  for (const s of a.scopes) if (!b.scopes.includes(s)) return false;
  const ak = Object.keys(a.scopeFields);
  const bk = Object.keys(b.scopeFields);
  if (ak.length !== bk.length) return false;
  for (const k of ak) if (a.scopeFields[k] !== b.scopeFields[k]) return false;
  return true;
}

export function FeatureEditDialog({
  feature,
  allowedScopes,
  open,
  onClose,
  onSave,
  onDelete,
}: FeatureEditDialogProps) {
  const initialRef = useRef<DraftFeature | null>(null);
  const [draft, setDraft] = useState<DraftFeature | null>(null);

  useEffect(() => {
    if (open && feature) {
      const snap = snapshot(feature);
      initialRef.current = snap;
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDraft(snap);
    } else if (!open) {
      initialRef.current = null;
      setDraft(null);
    }
  }, [open, feature]);

  const scopeSections = useMemo(() => {
    if (!draft) return [];
    if (allowedScopes.length === 0) {
      return [{ scope: "CORE" as const, fields: SCOPE_FIELDS_BY_SCOPE.CORE }];
    }
    return allowedScopes
      .filter((s) => draft.scopes.includes(s))
      .map((scope) => ({ scope, fields: SCOPE_FIELDS_BY_SCOPE[scope] }));
  }, [draft, allowedScopes]);

  if (!draft) {
    return (
      <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
        <DialogContent />
      </Dialog>
    );
  }

  function patch<K extends keyof DraftFeature>(key: K, value: DraftFeature[K]) {
    setDraft((prev) => (prev ? { ...prev, [key]: value } : prev));
  }

  function toggleScope(s: FeatureScope) {
    if (!draft) return;
    const next = draft.scopes.includes(s)
      ? draft.scopes.filter((x) => x !== s)
      : [...draft.scopes, s];
    patch("scopes", next);
  }

  function setScopeField(key: string, val: string) {
    if (!draft) return;
    patch("scopeFields", { ...draft.scopeFields, [key]: val });
  }

  // Handler für Save/Delete/Close folgen in Task 4 — vorerst No-Op, damit Form rendert.
  const noop = () => {};

  // Suppress "unused" until Task 4 wires them.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const _used = { onSave, onDelete };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Feature bearbeiten</DialogTitle>
          <DialogDescription>{draft.title || "(ohne Titel)"}</DialogDescription>
        </DialogHeader>

        <div className="grid grid-cols-1 md:grid-cols-[1fr_1.3fr] gap-6 py-4">
          {/* Stammdaten */}
          <div className="space-y-4">
            <div>
              <Label htmlFor="feat-title">Titel</Label>
              <Input
                id="feat-title"
                value={draft.title}
                onChange={(e) => patch("title", e.target.value)}
              />
            </div>

            {allowedScopes.length > 1 && (
              <div>
                <Label>Scope</Label>
                <div className="flex gap-2 mt-1">
                  {allowedScopes.map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => toggleScope(s)}
                      className={`px-3 py-1 rounded-full text-xs border ${
                        draft.scopes.includes(s)
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

            <div>
              <Label htmlFor="feat-desc">Beschreibung</Label>
              <Textarea
                id="feat-desc"
                rows={5}
                value={draft.description}
                onChange={(e) => patch("description", e.target.value)}
              />
            </div>
          </div>

          {/* Scope-Felder */}
          <div className="space-y-4">
            {scopeSections.map(({ scope, fields }) => (
              <section key={scope}>
                {scopeSections.length > 1 && (
                  <h4 className="text-xs font-semibold uppercase text-muted-foreground mb-2">
                    {scope === "FRONTEND" ? "Frontend" : "Backend"}
                  </h4>
                )}
                {fields.map((key) => (
                  <div key={key} className="mb-3">
                    <Label htmlFor={`feat-${key}`}>{SCOPE_FIELD_LABELS[key] ?? key}</Label>
                    <Textarea
                      id={`feat-${key}`}
                      rows={2}
                      value={draft.scopeFields[key] ?? ""}
                      onChange={(e) => setScopeField(key, e.target.value)}
                    />
                  </div>
                ))}
              </section>
            ))}
          </div>
        </div>

        <DialogFooter className="flex flex-row justify-between sm:justify-between">
          <Button variant="ghost" onClick={noop}>
            <Trash2 size={14} className="mr-1" /> Löschen
          </Button>
          <div className="flex gap-2">
            <Button variant="outline" onClick={noop}>Abbrechen</Button>
            <Button onClick={noop}>Speichern</Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// Suppress "unused" until Task 4 wires it.
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const _equalDraftUsed = equalDraft;
