"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronUp, Loader2, Plus, Sparkles, Trash2 } from "lucide-react";
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
import type { AcceptanceCriterion, FeatureScope, WizardFeature } from "@/lib/api";
import { proposeAcceptanceCriteria } from "@/lib/api";
import { SCOPE_FIELD_LABELS, SCOPE_FIELDS_BY_SCOPE } from "@/lib/step-field-labels";

interface FeatureEditDialogProps {
  feature: WizardFeature | null;
  allowedScopes: FeatureScope[];
  open: boolean;
  projectId: string;
  onClose: () => void;
  onSave: (patch: Partial<WizardFeature>) => void;
  onDelete: () => void;
}

type DraftFeature = Pick<WizardFeature,
  "title" | "description" | "scopes" | "scopeFields" | "acceptanceCriteria">;

function snapshot(f: WizardFeature): DraftFeature {
  return {
    title: f.title,
    description: f.description,
    scopes: [...f.scopes],
    scopeFields: { ...f.scopeFields },
    // Defensive: legacy WizardFeature JSON (pre-Feature-44) lacks the field.
    acceptanceCriteria: (f.acceptanceCriteria ?? []).map((c) => ({ ...c })),
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
  if (a.acceptanceCriteria.length !== b.acceptanceCriteria.length) return false;
  for (let i = 0; i < a.acceptanceCriteria.length; i++) {
    const x = a.acceptanceCriteria[i];
    const y = b.acceptanceCriteria[i];
    if (x.id !== y.id || x.text !== y.text) return false;
  }
  return true;
}

interface AcceptanceCriteriaListProps {
  value: AcceptanceCriterion[];
  onChange: (next: AcceptanceCriterion[]) => void;
  onPropose: () => void;
  isProposing: boolean;
  proposeError: string | null;
}

function AcceptanceCriteriaList({
  value,
  onChange,
  onPropose,
  isProposing,
  proposeError,
}: AcceptanceCriteriaListProps) {
  const inputRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const focusIdRef = useRef<string | null>(null);

  useEffect(() => {
    const id = focusIdRef.current;
    if (id && inputRefs.current[id]) {
      inputRefs.current[id]?.focus();
      focusIdRef.current = null;
    }
  }, [value]);

  function patchText(id: string, val: string) {
    onChange(value.map((c) => (c.id === id ? { ...c, text: val } : c)));
  }

  function appendNew(afterId?: string) {
    const newItem: AcceptanceCriterion = {
      id: crypto.randomUUID(),
      text: "",
    };
    if (!afterId) {
      onChange([...value, newItem]);
    } else {
      const idx = value.findIndex((c) => c.id === afterId);
      const next = [...value];
      next.splice(idx + 1, 0, newItem);
      onChange(next);
    }
    focusIdRef.current = newItem.id;
  }

  function remove(id: string) {
    onChange(value.filter((c) => c.id !== id));
  }

  function move(id: string, direction: -1 | 1) {
    const idx = value.findIndex((c) => c.id === id);
    const target = idx + direction;
    if (target < 0 || target >= value.length) return;
    const next = [...value];
    [next[idx], next[target]] = [next[target], next[idx]];
    onChange(next);
  }

  return (
    <section className="border-t pt-4 mt-2">
      <div className="flex items-center justify-between mb-3">
        <Label>Akzeptanzkriterien</Label>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onPropose}
          disabled={isProposing}
        >
          {isProposing ? (
            <Loader2 className="animate-spin mr-1" size={14} />
          ) : (
            <Sparkles className="mr-1" size={14} />
          )}
          {isProposing ? "Generiere..." : "AC vorschlagen"}
        </Button>
      </div>

      {proposeError && (
        <p className="text-xs text-destructive mb-2">{proposeError}</p>
      )}

      <div className="space-y-3">
        {value.map((c, idx) => (
          <div
            key={c.id}
            className="flex items-start gap-2 border border-border rounded-md p-2 bg-muted/20"
          >
            <Input
              id={`ac-text-${c.id}`}
              aria-label={`Akzeptanzkriterium ${idx + 1}`}
              ref={(el: HTMLInputElement | null) => { inputRefs.current[c.id] = el; }}
              value={c.text}
              onChange={(e) => patchText(c.id, e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  appendNew(c.id);
                }
              }}
              className="flex-1"
              placeholder="z. B. Aufruf von / ohne Cookie → automatisch redirect zu /login"
            />
            <div className="flex shrink-0 items-center gap-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => move(c.id, -1)}
                disabled={idx === 0}
                aria-label="Nach oben"
              >
                <ChevronUp size={14} />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => move(c.id, 1)}
                disabled={idx === value.length - 1}
                aria-label="Nach unten"
              >
                <ChevronDown size={14} />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => remove(c.id)}
                aria-label="Entfernen"
              >
                <Trash2 size={14} />
              </Button>
            </div>
          </div>
        ))}
      </div>

      <Button
        type="button"
        variant="outline"
        size="sm"
        className="mt-3"
        onClick={() => appendNew()}
      >
        <Plus size={14} className="mr-1" /> Akzeptanzkriterium hinzufügen
      </Button>
    </section>
  );
}

export function FeatureEditDialog({
  feature,
  allowedScopes,
  open,
  projectId,
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
      setDraft(snap);
    }
    if (!open) {
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

  const [isProposing, setIsProposing] = useState(false);
  const [proposeError, setProposeError] = useState<string | null>(null);

  async function handlePropose() {
    if (!feature || !draft) return;
    setIsProposing(true);
    setProposeError(null);
    try {
      const proposed = await proposeAcceptanceCriteria(projectId, feature.id);
      patch("acceptanceCriteria", [...draft.acceptanceCriteria, ...proposed]);
    } catch (e) {
      setProposeError(e instanceof Error ? e.message : "Vorschlag fehlgeschlagen");
    } finally {
      setIsProposing(false);
    }
  }

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

  function handleClose() {
    if (!draft) {
      onClose();
      return;
    }
    const initial = initialRef.current;
    const isDirty = initial ? !equalDraft(draft, initial) : false;
    if (isDirty && !window.confirm("Änderungen verwerfen?")) return;
    onClose();
  }

  function handleSave() {
    if (!draft) return;
    const cleanedAC = draft.acceptanceCriteria
      .map((c) => ({ ...c, text: c.text.trim() }))
      .filter((c) => c.text.length > 0);
    onSave({
      title: draft.title,
      description: draft.description,
      scopes: draft.scopes,
      scopeFields: draft.scopeFields,
      acceptanceCriteria: cleanedAC,
    });
    onClose();
  }

  function handleDelete() {
    if (!window.confirm("Feature wirklich löschen?")) return;
    onDelete();
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && handleClose()}>
      <DialogContent className="sm:max-w-7xl max-h-[80vh] overflow-y-auto">
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

        <AcceptanceCriteriaList
          value={draft.acceptanceCriteria}
          onChange={(next) => patch("acceptanceCriteria", next)}
          onPropose={handlePropose}
          isProposing={isProposing}
          proposeError={proposeError}
        />

        <DialogFooter className="flex flex-row justify-between sm:justify-between">
          <Button variant="ghost" onClick={handleDelete}>
            <Trash2 size={14} className="mr-1" /> Löschen
          </Button>
          <div className="flex gap-2">
            <Button variant="outline" onClick={handleClose}>Abbrechen</Button>
            <Button onClick={handleSave}>Speichern</Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
