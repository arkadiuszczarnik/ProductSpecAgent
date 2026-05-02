# Features-Edit-Modal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Side-Panel im Features-Step durch ein shadcn-Dialog-Modal ersetzen, sodass der Rete-Graph 100 % der Workspace-Breite nutzt.

**Architecture:** Neue isolierte Komponente `FeatureEditDialog.tsx` mit lokalem Form-State und Dirty-Tracking; `FeaturesGraphEditor.tsx` rendert den Dialog statt des resizable Side-Panels und steuert ihn über `selectedId`. `FeatureSidePanel.tsx` wird gelöscht.

**Tech Stack:** Next.js 16 + React 19 + TypeScript + Tailwind CSS 4 + shadcn/ui (neu, koexistierend mit base-ui/react) + Rete.js 2 + Zustand 5.

**Spec / Design:**
- Feature-Doc: `docs/features/36-features-edit-modal.md`
- Design-Spec: `docs/superpowers/specs/2026-05-02-features-edit-modal-design.md`

---

## File Structure

```
frontend/
├── components.json                                            # NEU (shadcn-init)
├── package.json / package-lock.json                           # MOD (Radix-Dialog/-Label)
├── src/
│   ├── app/globals.css                                        # ggf. MOD (shadcn-Tokens)
│   └── components/
│       ├── ui/
│       │   ├── dialog.tsx                                     # NEU (shadcn)
│       │   ├── input.tsx                                      # NEU (shadcn)
│       │   ├── textarea.tsx                                   # NEU (shadcn)
│       │   └── label.tsx                                      # NEU (shadcn)
│       └── wizard/steps/features/
│           ├── FeatureEditDialog.tsx                          # NEU
│           ├── FeatureSidePanel.tsx                           # DELETE
│           └── FeaturesGraphEditor.tsx                        # MOD
└── CLAUDE.md                                                  # MOD (UI-Lib-Hinweis)
```

**Verantwortlichkeiten:**
- `FeatureEditDialog.tsx`: gesamtes Modal — Props-Interface, lokaler Draft-State, Dirty-Vergleich, JSX, Save/Cancel/Delete-Handler. Kennt weder Store noch IDs.
- `FeaturesGraphEditor.tsx`: Graph-Container + Toolbar + Dialog-Controller (`open={selectedId !== null}`). Zustand-Wiring, Persistenz.
- `frontend/src/components/ui/{dialog,input,textarea,label}.tsx`: standard shadcn-Komponenten — kein Hand-Editing.
- `frontend/src/components/ui/{button,card,badge,progress}.tsx` (bestehend, base-ui): unverändert.

**Hinweis zu Tests:** Frontend hat per `frontend/CLAUDE.md` keinen Test-Runner. Verifikation erfolgt **manuell im Browser** (Dev-Server auf `:3001`). TypeScript-Compile und ESLint-Lauf sind die maschinellen Gates.

---

## Task 1: shadcn-Setup im Frontend

**Files:**
- Create: `frontend/components.json`
- Create: `frontend/src/components/ui/dialog.tsx`
- Create: `frontend/src/components/ui/input.tsx`
- Create: `frontend/src/components/ui/textarea.tsx`
- Create: `frontend/src/components/ui/label.tsx`
- Modify: `frontend/package.json`, `frontend/package-lock.json` (auto via npm)
- Possibly modify: `frontend/src/app/globals.css` (shadcn-Tokens)

- [ ] **Step 1: `globals.css` sichern**

```bash
cp frontend/src/app/globals.css /tmp/globals.css.bak
```
Diente als Vergleichsbasis falls `shadcn init` das Datei überschreibt.

- [ ] **Step 2: shadcn initialisieren (interaktiv)**

```bash
cd frontend
npx shadcn@latest init
```
Antworten:
- Style: **New York**
- Base color: **Neutral**
- CSS-Variablen: **Yes**
- Pfad-Alias: nutzt `@/*` aus `tsconfig.json` (vorgeschlagen)
- `components.json` wird im `frontend/` angelegt
- Falls die Frage nach einem Tailwind-Config-File kommt: ablehnen, wir haben CSS-only (`@theme` in `globals.css`)

- [ ] **Step 3: `globals.css` gegenchecken**

```bash
diff /tmp/globals.css.bak frontend/src/app/globals.css
```
Erwartet: shadcn ergänzt evtl. zusätzliche `--shadcn-*`-Tokens am Anfang/Ende des `@theme`-Blocks. Bestehende Custom-Variablen (Violet/Cyan, Plus Jakarta) **müssen erhalten bleiben**. Falls etwas Bestehendes überschrieben wurde: aus `/tmp/globals.css.bak` zurückholen und die shadcn-Tokens manuell daneben legen.

- [ ] **Step 4: vier Komponenten installieren**

```bash
cd frontend
npx shadcn@latest add dialog input textarea label
```
Erwartet: vier neue Dateien in `src/components/ui/`. Falls `button` als Dependency miterscheint: **ablehnen** oder die Datei direkt wieder löschen — `frontend/src/components/ui/button.tsx` (base-ui) bleibt unangetastet.

```bash
git status frontend/src/components/ui/
```
Soll zeigen: `dialog.tsx`, `input.tsx`, `textarea.tsx`, `label.tsx` neu. Kein modifizierter `button.tsx`.

- [ ] **Step 5: Build + Lint**

```bash
cd frontend
npm run build
npm run lint
```
Erwartet: beides grün.

- [ ] **Step 6: Commit**

```bash
git add frontend/components.json frontend/src/components/ui/dialog.tsx \
        frontend/src/components/ui/input.tsx frontend/src/components/ui/textarea.tsx \
        frontend/src/components/ui/label.tsx frontend/package.json \
        frontend/package-lock.json frontend/src/app/globals.css
git commit -m "$(cat <<'EOF'
chore(frontend): introduce shadcn/ui with dialog input textarea label

Initialisiert shadcn/ui im frontend/ koexistierend mit base-ui. Nur die für
Feature 36 benötigten Komponenten installiert; bestehender Button bleibt
unverändert.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `FeatureEditDialog`-Skeleton (Types + Helpers + leeres Modal)

**Files:**
- Create: `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx`

- [ ] **Step 1: Datei mit Props-Interface, Helpers und leerem Dialog anlegen**

```tsx
"use client";
import { useEffect, useRef, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { FeatureScope, WizardFeature } from "@/lib/api";

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

  // Snapshot beim Übergang closed→open
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

  if (!draft) {
    return (
      <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
        <DialogContent />
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Feature bearbeiten</DialogTitle>
          <DialogDescription>{draft.title || "(ohne Titel)"}</DialogDescription>
        </DialogHeader>
        <div className="py-4">
          <p className="text-sm text-muted-foreground">Form folgt in Task 3.</p>
        </div>
        <DialogFooter />
      </DialogContent>
    </Dialog>
  );
}

// Suppress "unused" until Task 3 wires them.
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const _used = { onSave, onDelete, allowedScopes, equalDraft };
```

Hinweis zu `_used`: nur bis Task 3, dann entfernen. Alternativ können die Imports in Task 3 ergänzt werden — aber sie sind hier schon im Props-Interface, ESLint erkennt das.

Falls ESLint trotzdem `unused`-Warnings wirft, die `_used`-Zeile beibehalten oder direkt zu Task 3 weiterlaufen.

- [ ] **Step 2: Build + Lint**

```bash
cd frontend
npm run build
npm run lint
```
Erwartet: beides grün. Die Komponente wird noch nicht importiert — kein Render-Effekt.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx
git commit -m "$(cat <<'EOF'
feat(features-modal): add FeatureEditDialog skeleton with draft snapshot

Props-Interface, snapshot/equalDraft Helper, lokaler Draft-State und leerer
shadcn Dialog. Form-Inhalt folgt in nächstem Schritt.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Form-Inhalt im Modal (2 Spalten, Stammdaten + Scope-Felder)

**Files:**
- Modify: `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx`

- [ ] **Step 1: Imports und Form-Body ergänzen**

Komplette Datei (ersetzt Task 2):

```tsx
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
```

Hinweise:
- Scope-Chip-Styling verwendet bewusst die gleichen Klassen wie heute in `FeatureSidePanel.tsx:60-66`, damit Look konsistent bleibt.
- `equalDraft`, `onSave`, `onDelete` werden in Task 4 verwendet — bis dahin als No-Op gerendert (`noop`).

- [ ] **Step 2: Build + Lint**

```bash
cd frontend
npm run build
npm run lint
```
Erwartet: grün. Falls ESLint `equalDraft`/`onSave`/`onDelete` als unused meldet: ignorierbar bis Task 4, oder mit `// eslint-disable-next-line` kurzfristig stummschalten (in Task 4 wieder weg).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx
git commit -m "$(cat <<'EOF'
feat(features-modal): render 2-column form in FeatureEditDialog

Stammdaten links (Titel, Scope-Chips, Beschreibung), Scope-Felder rechts mit
optionalen FRONTEND/BACKEND-Headers. Save/Cancel/Delete-Handler folgen.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Save / Cancel / Delete-Handler mit Dirty-Confirm

**Files:**
- Modify: `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx`

- [ ] **Step 1: Handler-Block ergänzen, `noop` ersetzen, Footer verdrahten**

Suche im Body von `FeatureEditDialog`:

```tsx
  // Handler für Save/Delete/Close folgen in Task 4 — vorerst No-Op, damit Form rendert.
  const noop = () => {};
```

Ersetze durch:

```tsx
  const isDirty = initialRef.current
    ? !equalDraft(draft, initialRef.current)
    : false;

  function handleClose() {
    if (isDirty && !window.confirm("Änderungen verwerfen?")) return;
    onClose();
  }

  function handleSave() {
    if (!draft) return;
    onSave({
      title: draft.title,
      description: draft.description,
      scopes: draft.scopes,
      scopeFields: draft.scopeFields,
    });
    onClose();
  }

  function handleDelete() {
    if (!window.confirm("Feature wirklich löschen?")) return;
    onDelete();
  }
```

In der JSX-Struktur:

1. Im `<Dialog open={open} onOpenChange={(o) => !o && onClose()}>`-Aufruf `onClose()` durch `handleClose()` ersetzen (zwei Stellen: Top-Level + Skeleton-Branch).
2. Im `<DialogFooter>`-Block die drei `noop`-Aufrufe durch `handleDelete`, `handleClose`, `handleSave` ersetzen.

Komplette Footer-JSX nach Änderung:

```tsx
        <DialogFooter className="flex flex-row justify-between sm:justify-between">
          <Button variant="ghost" onClick={handleDelete}>
            <Trash2 size={14} className="mr-1" /> Löschen
          </Button>
          <div className="flex gap-2">
            <Button variant="outline" onClick={handleClose}>Abbrechen</Button>
            <Button onClick={handleSave}>Speichern</Button>
          </div>
        </DialogFooter>
```

Und der Top-Level-Dialog:

```tsx
    <Dialog open={open} onOpenChange={(o) => !o && handleClose()}>
```

(beide Stellen — auch im Skeleton-Branch direkt darüber).

- [ ] **Step 2: `noop`-Variable entfernen**

Sicherstellen, dass `const noop = () => {};` aus Task 3 vollständig gelöscht ist.

- [ ] **Step 3: Build + Lint**

```bash
cd frontend
npm run build
npm run lint
```
Erwartet: grün. Keine unused-Vars mehr.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx
git commit -m "$(cat <<'EOF'
feat(features-modal): wire save/cancel/delete with dirty confirm

Esc, Overlay-Klick und "Abbrechen" zeigen window.confirm bei Dirty.
"Löschen" zeigt Lösch-Confirm. "Speichern" sendet einen einzelnen
updateFeature-Patch.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `FeaturesGraphEditor` umbauen — Side-Panel raus, Dialog rein

**Files:**
- Modify: `frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx`

- [ ] **Step 1: Imports anpassen**

Ersetze:
```tsx
import { useResizable } from "@/lib/hooks/use-resizable";
```
und
```tsx
import { FeatureSidePanel } from "./FeatureSidePanel";
```
durch:
```tsx
import { FeatureEditDialog } from "./FeatureEditDialog";
```

`updateFeature` aus dem Wizard-Store ergänzen (nutzt heute nur `FeatureSidePanel`):

```tsx
  const updateFeature = useWizardStore((s) => s.updateFeature);
```
direkt unter `const moveFeature = useWizardStore((s) => s.moveFeature);`.

- [ ] **Step 2: `useResizable`-Aufruf entfernen**

Lösche den Block (etwa Zeile 62–66):

```tsx
  const { width: panelWidth, handleProps } = useResizable({
    initialWidth: 360,
    minWidth: 280,
    maxWidth: 560,
  });
```

- [ ] **Step 3: Container-JSX umbauen — Side-Panel und Resize-Handle entfernen, Dialog ergänzen**

Suche das gesamte Return-Block ab Zeile ≈133:

```tsx
  return (
    <div className="flex h-[600px] min-h-[400px] rounded-lg border bg-background overflow-hidden">
      <div className="flex-1 min-w-0 flex flex-col">
        ...Graph + Toolbar bleibt unverändert...
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
          <FeatureSidePanel ... />
        ) : (
          <p className="p-4 text-sm text-muted-foreground">
            Waehle ein Feature, um es zu bearbeiten.
          </p>
        )}
      </div>
    </div>
  );
```

Ersetze durch:

```tsx
  return (
    <div className="flex h-[600px] min-h-[400px] rounded-lg border bg-background overflow-hidden">
      <div className="flex-1 min-w-0 flex flex-col">
        <div
          ref={ref}
          className="flex-1"
          style={{ background: "var(--color-background)" }}
        />
        <div className="border-t px-3 py-2 flex flex-col gap-1.5">
          <div className="flex items-center gap-2">
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
                shouldAutoLayoutRef.current = true;
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
          <p className="text-xs text-muted-foreground">
            Berücksichtigt Markdown- und Text-Dateien aus dem Documents-Tab.
          </p>
        </div>
      </div>

      <FeatureEditDialog
        feature={selected}
        allowedScopes={allowedScopes}
        open={selectedId !== null}
        onClose={() => setSelectedId(null)}
        onSave={(patch) => {
          if (selected) updateFeature(selected.id, patch);
        }}
        onDelete={() => {
          if (selected) {
            removeFeature(selected.id);
            setSelectedId(null);
          }
        }}
      />
    </div>
  );
```

Erläuterung:
- Mittlerer `<div className="w-1 cursor-col-resize ...">` und rechtes Panel-`<div>` sind weg.
- Innerer `<div className="flex-1 min-w-0 flex flex-col">` (Graph + Toolbar) bleibt 1:1 — dieser Block ist absichtlich unverändert kopiert, damit die Migration mechanisch nachvollziehbar bleibt.
- `<FeatureEditDialog>` rendert auf gleicher Ebene wie der Graph-Container — Portal-mounting macht React/Radix selbst.

- [ ] **Step 4: Build + Lint**

```bash
cd frontend
npm run build
npm run lint
```
Erwartet: grün. Falls Lint `useResizable` als unused report: prüfen, ob der Import wirklich entfernt wurde (Step 1).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx
git commit -m "$(cat <<'EOF'
feat(features-modal): replace SidePanel with FeatureEditDialog

Graph nutzt jetzt 100 % der Breite. selectedId steuert Dialog-open. Resize-
Handle und useResizable entfernt.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: `FeatureSidePanel.tsx` löschen

**Files:**
- Delete: `frontend/src/components/wizard/steps/features/FeatureSidePanel.tsx`

- [ ] **Step 1: Vorab prüfen, dass keine Imports mehr verweisen**

```bash
grep -rn "FeatureSidePanel" frontend/src
```
Erwartet: keine Treffer. Falls doch — die Stelle entfernen, bevor die Datei gelöscht wird.

- [ ] **Step 2: Datei löschen**

```bash
rm frontend/src/components/wizard/steps/features/FeatureSidePanel.tsx
```

- [ ] **Step 3: Build + Lint**

```bash
cd frontend
npm run build
npm run lint
```
Erwartet: grün.

- [ ] **Step 4: Commit**

```bash
git add -u frontend/src/components/wizard/steps/features/FeatureSidePanel.tsx
git commit -m "$(cat <<'EOF'
refactor(features-modal): remove FeatureSidePanel.tsx

Ersetzt durch FeatureEditDialog. Keine Imports mehr.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Browser-Verifikation (manuell, ohne Commit)

**Files:** keine Änderungen.

Voraussetzung: Backend läuft auf `:8081`. Falls nicht: `./gradlew bootRun --quiet` aus `backend/`.

- [ ] **Step 1: Dev-Server starten**

```bash
cd frontend && npm run dev
```

- [ ] **Step 2: Test-Projekt öffnen oder neu anlegen**

Browser auf `http://localhost:3001/projects` → ein Projekt mit mindestens 2 Features öffnen, im Wizard zum Step **FEATURES** navigieren.

- [ ] **Step 3: Verifikations-Checkliste durchgehen**

  1. **Klick öffnet** — Single-Click auf Node → Modal öffnet, Felder gefüllt.
  2. **Speichern** — Titel ändern → "Speichern" → Modal zu, neuer Titel am Node sichtbar (innerhalb der Auto-Save-Debounce-Zeit auch im Backend persistiert).
  3. **Dirty-Confirm Cancel** — Beschreibung ändern → "Abbrechen" → Confirm "Änderungen verwerfen?" erscheint.
  4. **Dirty-Confirm Esc** — Feld ändern → Esc → Confirm.
  5. **Dirty-Confirm Overlay** — Feld ändern → Klick auf Overlay → Confirm.
  6. **Clean-Close** — keine Änderung → Esc/Overlay/"Abbrechen" schliessen direkt ohne Confirm.
  7. **Scope-Toggle lokal** — Scope-Chip toggeln → rechte Spalte ändert sich; "Abbrechen" → Original-Scope wieder am Node.
  8. **Löschen** — "Löschen" → Confirm "Feature wirklich löschen?" → Node verschwindet aus Graph.
  9. **Auto-Save-Echo** — Modal offen lassen, in einem zweiten Tab dasselbe Projekt öffnen und ein anderes Feature ändern → Eingaben im offenen Modal bleiben unberührt.
  10. **Library-Mode** — Projekt mit Category=Library → Modal zeigt CORE-Felder (kein Scope-Block).
  11. **Single-Scope** — Projekt mit Category=Mobile App → rechte Spalte zeigt nur FRONTEND-Felder ohne Scope-Header.
  12. **Voller Graph** — kein Feature ausgewählt → Graph-Container nutzt 100 % Workspace-Breite, keine vertikale Trennlinie.
  13. **Mobile** — Browser auf < 768 px Breite → `<FeaturesFallbackList>` greift wie bisher.

- [ ] **Step 4: Bei Defekt → Issue-Issue-Map**

| Symptom | Ursache | Lösung |
|---|---|---|
| Modal öffnet nicht beim Klick | `onNodeSelect` ruft `setSelectedId` mit `null` auf | im Browser-Devtool React-State von `FeaturesGraphEditor` prüfen |
| Confirm erscheint immer (auch ohne Edit) | `equalDraft` falsch oder `initialRef` wird neu gesetzt | useEffect-Deps prüfen — nur bei `open && feature` snapshot |
| Beschriftung "(ohne Titel)" trotz Titel | `draft` nicht initialisiert | Skeleton-Branch greift — feature war `null` beim Open |
| Dialog hat scrollbares Backdrop | shadcn-Defaults überschrieben | `DialogContent`-Klassen prüfen |
| Tailwind-Tokens fehlen | `globals.css`-Mismatch aus Task 1 | aus `/tmp/globals.css.bak` zurückspielen |

---

## Task 8: `frontend/CLAUDE.md` aktualisieren

**Files:**
- Modify: `frontend/CLAUDE.md`

- [ ] **Step 1: shadcn-Hinweis ergänzen**

In `frontend/CLAUDE.md` den Abschnitt **Stack-Details** um eine Zeile erweitern, direkt nach der `base-ui/react`-Zeile:

```md
- **shadcn/ui** (seit Feature 36) als Komponenten-Quelle für neue UI — koexistiert mit base-ui. shadcn-Komponenten landen in `src/components/ui/` (Dialog, Input, Textarea, Label installiert). Bestehende base-ui-Komponenten (`button`, `card`, `badge`, `progress`) bleiben unverändert. Neue Komponenten via `npx shadcn@latest add <name>`.
```

- [ ] **Step 2: Commit**

```bash
git add frontend/CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(frontend): note shadcn/ui coexists with base-ui

Ergänzt CLAUDE.md um den shadcn-Hinweis (eingeführt mit Feature 36).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Done-Doc

**Files:**
- Create: `docs/features/36-features-edit-modal-done.md` (Konvention: bestehende `*-done.md` im Repo)

- [ ] **Step 1: Datei anlegen**

```md
# Feature 36 — Features-Edit-Modal — Done

**Datum:** [aktuelles Datum]
**Branch:** [Branch-Name]
**Commits:** [List der Commits aus Tasks 1–8]

## Was umgesetzt wurde

- shadcn/ui im Frontend initialisiert (Dialog/Input/Textarea/Label).
- `FeatureEditDialog` ersetzt `FeatureSidePanel`.
- `FeaturesGraphEditor` nutzt Graph in voller Breite, steuert Modal über `selectedId`.
- Explizites Speichern mit Dirty-Confirm (Esc/Overlay/Abbrechen).
- Lösch-Confirm.

## Akzeptanzkriterien

[Liste aus docs/features/36-features-edit-modal.md → Akzeptanzkriterien, jeweils mit ✓ + Hinweis aus Browser-Verifikation]

## Nicht gemacht (YAGNI bestätigt)

[Liste aus YAGNI-Sektion der Spec]

## Bekannte Restpunkte

- Rete-Selection bleibt visuell selektiert nach Modal-Close (kosmetisch).
- Bestehende base-ui-Komponenten nicht migriert (separate Aufgabe).
```

- [ ] **Step 2: Commit**

```bash
git add docs/features/36-features-edit-modal-done.md
git commit -m "$(cat <<'EOF'
docs(feature-36): add done-doc

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review Checklist (für Plan-Autor)

- ✅ Alle 11 Akzeptanzkriterien aus dem Feature-Doc sind in Task 7 Step 3 als Verifikations-Punkte abgedeckt (#10 → Punkt 1 Build/Lint in Task 1, #11 → Task 6).
- ✅ Keine Placeholder ("TBD", "siehe oben", "ähnlich wie") — jede Code-Sektion ist vollständig.
- ✅ Type-Konsistenz: `DraftFeature`, `snapshot`, `equalDraft`, `FeatureEditDialogProps` werden in Tasks 2–4 identisch verwendet.
- ✅ Commit-Granularität: jede Task endet mit einem Commit (Task 7 ohne, da nur Verifikation).
- ✅ Reihenfolge ist korrekt — `FeatureSidePanel`-Delete (Task 6) **nach** der Editor-Migration (Task 5), damit kein Build-Bruch zwischen Commits.
- ✅ shadcn-Init-Risiko (`globals.css`-Überschreibung) explizit mitigiert mit Backup-Step.
