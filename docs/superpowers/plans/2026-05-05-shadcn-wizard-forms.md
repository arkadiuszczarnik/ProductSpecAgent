# shadcn-Migration Phase 1 — Wizard-Forms & Inputs (Feature 43a) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Native `<input>`/`<textarea>`-Elemente in 10 Frontend-Dateien durch die existierenden shadcn-Wrapper `<Input>`/`<Textarea>` aus `@/components/ui` ersetzen, ohne Logik oder Verhalten zu ändern.

**Architecture:** Mechanisches Wrapper-Refactoring mit Hybrid-Optik — shadcn-Defaults gewinnen (Border, Background, Focus-Ring, Padding, Font-Size, Rounded), Layout-Klassen (`w-full`, `min-h-*`, `resize-*`, `rows`) bleiben durchgereicht. Frontend hat keinen Unit-Test-Runner; Verifikation via TypeScript-Build + ESLint + bestehender Playwright-E2E-Test + manueller Browser-Smoke-Test.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript, Tailwind 4, shadcn/ui (Style `base-nova` auf base-ui-Primitiven). Frontend-Quellen unter `frontend/src/`. Pfad-Alias `@/*` → `./src/*`.

**Spec:** `docs/superpowers/specs/2026-05-05-shadcn-wizard-forms-design.md`

**Scope-Korrektur ggü. Spec:** Beim Lesen der Spec-Dateien stellte sich heraus, dass `BackendForm.tsx`, `FrontendForm.tsx`, `DesignForm.tsx` und `DocumentsPanel.tsx` keine nativen `<input>`/`<textarea>` enthalten (nur ChipSelects, Buttons, file-Inputs). Sie fallen aus 43a raus. **Effektive Datei-Anzahl: 10**.

---

## Globale Konventionen

**Imports:**
```tsx
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
```

**Aus `cn(...)` zu entfernen** (sind shadcn-Defaults):
- `bg-input`, `border`, `rounded-md`, `rounded-lg`
- `px-3 py-2`, `px-3 py-1.5`
- `text-sm`, `text-base`
- `placeholder:text-muted-foreground`
- `focus:outline-none`, `focus:ring-2`, `focus:ring-ring`
- `disabled:opacity-50` (für Input)

**Behalten / via `className` durchreichen:**
- `w-full` (außer beim TagInput-Edge-Case)
- `min-h-[*]`, `max-h-[*]`
- `resize-y`, `resize-none`
- `flex-1`, `mt-*` und andere Layout-/Spacing-Klassen
- `text-xs` (kleinere Schrift, ist kein Default)

**`rows`, `placeholder`, `value`, `onChange`, `onKeyDown`, `disabled`, `id`, `type`, `maxLength`, `required`** bleiben 1:1.

**Nach jedem Edit prüfen:** Wird `cn()` in der Datei noch verwendet? Wenn nicht → `import { cn } from "@/lib/utils";` entfernen, sonst `npm run lint` schlägt fehl.

**Commit-Schema:** `refactor(frontend): use shadcn Input/Textarea in <component>` pro Datei.

---

## Task 0: Pre-flight Baseline

**Files:** keine Edits

- [ ] **Step 1: Working directory wechseln**

```bash
cd frontend
```

- [ ] **Step 2: Baseline-Lint**

Run: `npm run lint`
Expected: Exit 0, keine Errors. (Vorhandene Warnings notieren, damit nach den Edits klar ist, was neu ist.)

- [ ] **Step 3: Baseline-Build**

Run: `npm run build`
Expected: Exit 0, „Compiled successfully". Dauer: ~30-60s.

- [ ] **Step 4: Bestätigen, dass beide UI-Wrapper installiert sind**

Run: `ls src/components/ui/input.tsx src/components/ui/textarea.tsx`
Expected: Beide Pfade vorhanden, kein Fehler.

---

## Phase 1 — Wizard-Core-Forms (4 Dateien)

### Task 1: IdeaForm

**Files:**
- Modify: `frontend/src/components/wizard/steps/IdeaForm.tsx`

- [ ] **Step 1: Import hinzufügen**

In `frontend/src/components/wizard/steps/IdeaForm.tsx` nach Zeile 1 (`"use client";`) und vor den bestehenden Imports:

```tsx
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
```

- [ ] **Step 2: Native input ersetzen (Z. 16-17)**

Ersetze:
```tsx
        <input value={get("productName")} onChange={(e) => set("productName", e.target.value)}
          placeholder="z.B. TaskFlow Pro" className={cn("w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring")} />
```

durch:
```tsx
        <Input value={get("productName")} onChange={(e) => set("productName", e.target.value)}
          placeholder="z.B. TaskFlow Pro" />
```

- [ ] **Step 3: Native textarea ersetzen (Z. 20-22)**

Ersetze:
```tsx
        <textarea value={get("vision")} onChange={(e) => set("vision", e.target.value)}
          placeholder="Beschreibe deine Produktidee in 2-3 Saetzen..." rows={4}
          className={cn("w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring min-h-[100px]")} />
```

durch:
```tsx
        <Textarea value={get("vision")} onChange={(e) => set("vision", e.target.value)}
          placeholder="Beschreibe deine Produktidee in 2-3 Saetzen..." rows={4}
          className="resize-y min-h-[100px]" />
```

- [ ] **Step 4: cn-Import entfernen**

`cn()` wird in IdeaForm nach den Edits nicht mehr verwendet. Entferne Zeile 5:
```tsx
import { cn } from "@/lib/utils";
```

- [ ] **Step 5: Lint**

Run: `cd frontend && npx eslint src/components/wizard/steps/IdeaForm.tsx`
Expected: 0 Errors, 0 Warnings.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/wizard/steps/IdeaForm.tsx
git commit -m "refactor(frontend): use shadcn Input/Textarea in IdeaForm"
```

---

### Task 2: ProblemForm

**Files:**
- Modify: `frontend/src/components/wizard/steps/ProblemForm.tsx`

- [ ] **Step 1: Imports hinzufügen**

Nach `"use client";` (Zeile 1):
```tsx
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
```

- [ ] **Step 2: Textarea ersetzen (Z. 17-19)**

Ersetze:
```tsx
        <textarea value={get("coreProblem")} onChange={(e) => set("coreProblem", e.target.value)}
          placeholder="Welches Problem loest dein Produkt?" rows={3}
          className="w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring min-h-[80px]" />
```

durch:
```tsx
        <Textarea value={get("coreProblem")} onChange={(e) => set("coreProblem", e.target.value)}
          placeholder="Welches Problem loest dein Produkt?" rows={3}
          className="resize-y min-h-[80px]" />
```

- [ ] **Step 3: Input ersetzen (Z. 22-24)**

Ersetze:
```tsx
        <input value={get("primaryAudience")} onChange={(e) => set("primaryAudience", e.target.value)}
          placeholder="z.B. Product Owner, Startup-Gründer"
          className="w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
```

durch:
```tsx
        <Input value={get("primaryAudience")} onChange={(e) => set("primaryAudience", e.target.value)}
          placeholder="z.B. Product Owner, Startup-Gründer" />
```

- [ ] **Step 4: cn-Import bereinigen**

Pre-existing: `cn` wird in der Datei nicht aktiv genutzt (war auch vorher unbenutzt, vermutlich von ESLint toleriert). Da der Import ohnehin als unused gekennzeichnet werden könnte, **lass ihn unverändert** — er war vor dem Edit vorhanden, gehört nicht zu unserem Refactor und wird laut CLAUDE.md („Don't remove pre-existing dead code unless asked") nicht angefasst. Falls Lint nach den Edits einen unused-Error wirft, entferne den Import nachträglich.

- [ ] **Step 5: Lint**

Run: `cd frontend && npx eslint src/components/wizard/steps/ProblemForm.tsx`
Expected: 0 Errors. Falls `'cn' is defined but never used` als Error erscheint → Import von Zeile 5 entfernen und nochmal linten.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/wizard/steps/ProblemForm.tsx
git commit -m "refactor(frontend): use shadcn Input/Textarea in ProblemForm"
```

---

### Task 3: MvpForm

**Files:**
- Modify: `frontend/src/components/wizard/steps/MvpForm.tsx`

- [ ] **Step 1: Import hinzufügen**

Nach `"use client";` (Zeile 1):
```tsx
import { Textarea } from "@/components/ui/textarea";
```

(Kein `Input` nötig — Datei hat nur Textareas.)

- [ ] **Step 2: Erste Textarea ersetzen (Z. 24-26, MVP-Ziel)**

Ersetze:
```tsx
        <textarea value={get("goal")} onChange={(e) => set("goal", e.target.value)}
          placeholder="Was soll das MVP leisten?" rows={3}
          className="w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring min-h-[80px]" />
```

durch:
```tsx
        <Textarea value={get("goal")} onChange={(e) => set("goal", e.target.value)}
          placeholder="Was soll das MVP leisten?" rows={3}
          className="resize-y min-h-[80px]" />
```

- [ ] **Step 3: Zweite Textarea ersetzen (Z. 39-41, Erfolgskriterien)**

Ersetze:
```tsx
        <textarea value={get("successCriteria")} onChange={(e) => set("successCriteria", e.target.value)}
          placeholder="Woran erkennst du, dass das MVP erfolgreich ist?" rows={2}
          className="w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
```

durch:
```tsx
        <Textarea value={get("successCriteria")} onChange={(e) => set("successCriteria", e.target.value)}
          placeholder="Woran erkennst du, dass das MVP erfolgreich ist?" rows={2}
          className="resize-y" />
```

- [ ] **Step 4: Lint**

Run: `cd frontend && npx eslint src/components/wizard/steps/MvpForm.tsx`
Expected: 0 Errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/wizard/steps/MvpForm.tsx
git commit -m "refactor(frontend): use shadcn Textarea in MvpForm"
```

---

### Task 4: ArchitectureForm

**Files:**
- Modify: `frontend/src/components/wizard/steps/ArchitectureForm.tsx`

- [ ] **Step 1: Import hinzufügen**

Nach `"use client";` (Zeile 1):
```tsx
import { Textarea } from "@/components/ui/textarea";
```

- [ ] **Step 2: Textarea ersetzen (Z. 34-36, Architektur-Notizen)**

Ersetze:
```tsx
        <textarea value={get("notes")} onChange={(e) => set("notes", e.target.value)}
          placeholder="Zusaetzliche Architektur-Details..." rows={3}
          className="w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
```

durch:
```tsx
        <Textarea value={get("notes")} onChange={(e) => set("notes", e.target.value)}
          placeholder="Zusaetzliche Architektur-Details..." rows={3}
          className="resize-y" />
```

- [ ] **Step 3: Lint**

Run: `cd frontend && npx eslint src/components/wizard/steps/ArchitectureForm.tsx`
Expected: 0 Errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/wizard/steps/ArchitectureForm.tsx
git commit -m "refactor(frontend): use shadcn Textarea in ArchitectureForm"
```

---

### Task 5: Phase 1 Verification

**Files:** keine Edits

- [ ] **Step 1: Lint über das gesamte Frontend**

Run: `cd frontend && npm run lint`
Expected: Exit 0, keine neuen Errors gegenüber Baseline (Task 0).

- [ ] **Step 2: Build**

Run: `cd frontend && npm run build`
Expected: Exit 0, „Compiled successfully".

- [ ] **Step 3: Bei Fehlern**

- TypeScript-Fehler: shadcn-Komponenten erwarten `React.ComponentProps<"input">` bzw. `"textarea"` — alle nativen Props (value, onChange, placeholder, rows, …) sind kompatibel. Falls dennoch Typfehler: vergewissere dich, dass kein nicht-existierendes Prop (z. B. `as="span"`) gesetzt wurde.
- ESLint `'cn' is defined but never used`: Import entfernen, dann amend committen.

---

## Phase 2 — Cards & Chat (3 Dateien)

### Task 6: ChatPanel

**Files:**
- Modify: `frontend/src/components/chat/ChatPanel.tsx`

- [ ] **Step 1: Import hinzufügen**

Nach Zeile 5 (`import { Button } from "@/components/ui/button";`):
```tsx
import { Textarea } from "@/components/ui/textarea";
```

- [ ] **Step 2: Textarea ersetzen (Z. 70-82)**

Ersetze den gesamten Textarea-Block:
```tsx
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Message the agent... (Enter to send)"
            rows={1}
            disabled={chatSending}
            className={cn(
              "flex-1 resize-none bg-transparent text-sm text-foreground placeholder:text-muted-foreground",
              "focus:outline-none disabled:opacity-50",
              "max-h-32 min-h-[24px]"
            )}
          />
```

durch:
```tsx
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Message the agent... (Enter to send)"
            rows={1}
            disabled={chatSending}
            className="flex-1 resize-none border-0 bg-transparent shadow-none focus-visible:ring-0 max-h-32 min-h-[24px] px-0 py-0"
          />
```

**Begründung der Override-Klassen:** Die Textarea sitzt innerhalb einer Outer-Box mit eigenem Border (Z. 69 `rounded-lg border border-border bg-background p-2`). Doppelter Border + Focus-Ring würde optisch stören → analog zum TagInput-Edge-Case (Spec Abschnitt „Edge Case TagInput").

- [ ] **Step 3: cn-Import bereinigen**

`cn()` wurde in der Datei nur für diese Textarea verwendet. Wenn keine weiteren `cn()`-Aufrufe in der Datei sind → Zeile 8 entfernen:
```tsx
import { cn } from "@/lib/utils";
```

Verifiziere mit `grep -n "cn(" frontend/src/components/chat/ChatPanel.tsx` — wenn die Ausgabe leer ist, entfernen.

- [ ] **Step 4: Lint**

Run: `cd frontend && npx eslint src/components/chat/ChatPanel.tsx`
Expected: 0 Errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/chat/ChatPanel.tsx
git commit -m "refactor(frontend): use shadcn Textarea in ChatPanel"
```

---

### Task 7: DecisionCard

**Files:**
- Modify: `frontend/src/components/decisions/DecisionCard.tsx`

- [ ] **Step 1: Import hinzufügen**

Nach Zeile 6 (`import { Card, CardHeader, … } from "@/components/ui/card";`):
```tsx
import { Textarea } from "@/components/ui/textarea";
```

- [ ] **Step 2: Textarea ersetzen (Z. 112-121)**

Ersetze:
```tsx
            <textarea
              value={rationale}
              onChange={(e) => setRationale(e.target.value)}
              placeholder="Why did you choose this option?"
              rows={2}
              className={cn(
                "w-full resize-none rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground",
                "focus:outline-none focus:ring-2 focus:ring-ring"
              )}
            />
```

durch:
```tsx
            <Textarea
              value={rationale}
              onChange={(e) => setRationale(e.target.value)}
              placeholder="Why did you choose this option?"
              rows={2}
              className="resize-none"
            />
```

- [ ] **Step 3: cn-Import nicht entfernen**

`cn()` wird in der Datei mehrfach für andere Stellen genutzt (Z. 37-42, 58-63). Import bleibt.

- [ ] **Step 4: Lint**

Run: `cd frontend && npx eslint src/components/decisions/DecisionCard.tsx`
Expected: 0 Errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/decisions/DecisionCard.tsx
git commit -m "refactor(frontend): use shadcn Textarea in DecisionCard"
```

---

### Task 8: ClarificationCard

**Files:**
- Modify: `frontend/src/components/clarifications/ClarificationCard.tsx`

- [ ] **Step 1: Import hinzufügen**

Nach Zeile 6:
```tsx
import { Textarea } from "@/components/ui/textarea";
```

- [ ] **Step 2: Textarea ersetzen (Z. 65-74)**

Ersetze:
```tsx
          <textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            placeholder="Provide your answer..."
            rows={3}
            className={cn(
              "w-full resize-none rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground",
              "focus:outline-none focus:ring-2 focus:ring-ring"
            )}
          />
```

durch:
```tsx
          <Textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            placeholder="Provide your answer..."
            rows={3}
            className="resize-none"
          />
```

- [ ] **Step 3: cn-Import nicht entfernen**

`cn()` wird in der Datei für andere Stellen genutzt (Z. 31, 42-44). Import bleibt.

- [ ] **Step 4: Lint**

Run: `cd frontend && npx eslint src/components/clarifications/ClarificationCard.tsx`
Expected: 0 Errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/clarifications/ClarificationCard.tsx
git commit -m "refactor(frontend): use shadcn Textarea in ClarificationCard"
```

---

### Task 9: Phase 2 Verification

**Files:** keine Edits

- [ ] **Step 1: Lint**

Run: `cd frontend && npm run lint`
Expected: Exit 0, keine neuen Errors.

- [ ] **Step 2: Build**

Run: `cd frontend && npm run build`
Expected: Exit 0.

---

## Phase 3 — Pages (1 Datei)

### Task 10: projects/new/page.tsx

**Files:**
- Modify: `frontend/src/app/projects/new/page.tsx`

- [ ] **Step 1: Import hinzufügen**

Nach Zeile 8 (`import { Card, … } from "@/components/ui/card";`):
```tsx
import { Input } from "@/components/ui/input";
```

- [ ] **Step 2: Input ersetzen (Z. 63-76)**

Ersetze:
```tsx
                <input
                  id="project-name"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. TaskFlow Pro"
                  maxLength={120}
                  disabled={loading}
                  className={cn(
                    "w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground",
                    "focus:outline-none focus:ring-2 focus:ring-ring",
                    "disabled:opacity-50"
                  )}
                />
```

durch:
```tsx
                <Input
                  id="project-name"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. TaskFlow Pro"
                  maxLength={120}
                  disabled={loading}
                />
```

- [ ] **Step 3: cn-Import bereinigen**

`cn()` war nur für diesen Block. Verifiziere mit:
```bash
grep -n "cn(" frontend/src/app/projects/new/page.tsx
```

Wenn die Ausgabe leer ist, entferne Zeile 10:
```tsx
import { cn } from "@/lib/utils";
```

- [ ] **Step 4: Lint**

Run: `cd frontend && npx eslint src/app/projects/new/page.tsx`
Expected: 0 Errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/projects/new/page.tsx
git commit -m "refactor(frontend): use shadcn Input in NewProjectPage"
```

---

## Phase 4 — Composites (2 Dateien)

### Task 11: TagInput

**Files:**
- Modify: `frontend/src/components/wizard/TagInput.tsx`

- [ ] **Step 1: Import hinzufügen**

Nach Zeile 4 (`import { X } from "lucide-react";`):
```tsx
import { Input } from "@/components/ui/input";
```

- [ ] **Step 2: Input ersetzen (Z. 48-55)**

Ersetze:
```tsx
      <input
        type="text"
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={handleKey}
        placeholder={tags.length === 0 ? placeholder : ""}
        className="w-full bg-transparent text-xs text-foreground placeholder:text-muted-foreground focus:outline-none"
      />
```

durch:
```tsx
      <Input
        type="text"
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={handleKey}
        placeholder={tags.length === 0 ? placeholder : ""}
        className="border-0 bg-transparent shadow-none focus-visible:ring-0 h-auto p-0 text-xs"
      />
```

**Begründung:** Der Input sitzt innerhalb einer Outer-Box mit eigenem Border (Z. 37). `border-0`, `shadow-none`, `focus-visible:ring-0` neutralisieren shadcn-Defaults; `h-auto p-0` entfernt die fixe Höhe; `text-xs` bewahrt die kleinere Schrift.

- [ ] **Step 3: cn-Import-Status prüfen**

`cn()` wird in der Datei aktiv für `colors.tag` und `colors.border` benutzt (Z. 37, 40). Import bleibt.

- [ ] **Step 4: Lint**

Run: `cd frontend && npx eslint src/components/wizard/TagInput.tsx`
Expected: 0 Errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/wizard/TagInput.tsx
git commit -m "refactor(frontend): use shadcn Input (embedded) in TagInput"
```

---

### Task 12: FeaturesFallbackList

**Files:**
- Modify: `frontend/src/components/wizard/steps/features/FeaturesFallbackList.tsx`

**Wichtig:** Nur die `<input>` in Z. 45 (Title-Edit) und `<textarea>` in Z. 54 (Description) anfassen. Die folgenden Elemente bleiben unverändert (gehören zu 43b/43c):
- `<button>` Z. 50 (Trash) → 43b
- `<input type="checkbox">` Z. 65 → 43c
- `<select>` Z. 82 → 43c
- `<Button>` Z. 28 ist bereits shadcn — nicht anfassen.

- [ ] **Step 1: Imports hinzufügen**

Nach Zeile 4 (`import { Button } from "@/components/ui/button";`):
```tsx
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
```

- [ ] **Step 2: Title-Input ersetzen (Z. 45-49)**

Ersetze:
```tsx
              <input
                value={f.title}
                onChange={(e) => updateFeature(f.id, { title: e.target.value })}
                className="flex-1 bg-transparent text-sm font-medium"
              />
```

durch:
```tsx
              <Input
                value={f.title}
                onChange={(e) => updateFeature(f.id, { title: e.target.value })}
                className="flex-1 border-0 bg-transparent shadow-none focus-visible:ring-0 h-auto p-0 text-sm font-medium"
              />
```

**Begründung:** Auch dieser Input ist embedded — die Outer-Card (Z. 43 `rounded-lg border bg-card p-3`) liefert den Rahmen. Override-Klassen analog zu TagInput.

- [ ] **Step 3: Description-Textarea ersetzen (Z. 54-60)**

Ersetze:
```tsx
            <textarea
              value={f.description}
              onChange={(e) => updateFeature(f.id, { description: e.target.value })}
              placeholder="Beschreibung..."
              rows={2}
              className="w-full resize-none rounded-md border bg-input px-3 py-1.5 text-xs"
            />
```

durch:
```tsx
            <Textarea
              value={f.description}
              onChange={(e) => updateFeature(f.id, { description: e.target.value })}
              placeholder="Beschreibung..."
              rows={2}
              className="resize-none text-xs"
            />
```

- [ ] **Step 4: Verifizieren, dass `<button>`, `<input type="checkbox">`, `<select>` UNVERÄNDERT sind**

```bash
grep -n "<button\|<input type=\"checkbox\|<select" frontend/src/components/wizard/steps/features/FeaturesFallbackList.tsx
```
Expected: drei Treffer (Zeilen ~50, ~65, ~82) — alle unverändert.

- [ ] **Step 5: Lint**

Run: `cd frontend && npx eslint src/components/wizard/steps/features/FeaturesFallbackList.tsx`
Expected: 0 Errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeaturesFallbackList.tsx
git commit -m "refactor(frontend): use shadcn Input/Textarea in FeaturesFallbackList"
```

---

## Phase 5 — Final Verification & Done-Datei

### Task 13: Full Verification

**Files:** keine Edits

- [ ] **Step 1: Final Lint**

Run: `cd frontend && npm run lint`
Expected: Exit 0, **keine neuen Errors oder Warnings** verglichen mit Task-0-Baseline.

- [ ] **Step 2: Final Build**

Run: `cd frontend && npm run build`
Expected: Exit 0, „Compiled successfully", keine TS-Fehler.

- [ ] **Step 3: E2E-Test**

Vorbedingung: Backend muss laufen. Falls noch nicht:
```bash
cd backend && ./gradlew bootRun --quiet &
# Warten bis Port 8080 antwortet
```

Run: `cd frontend && npm run test:e2e`
Expected: Exit 0, `features-graph.spec.ts` grün. Bei Backend-Verbindungsfehlern: Test ist OK zu skippen, in Done-Datei vermerken.

- [ ] **Step 4: Visuelle Inspektion (manueller Smoke-Test)**

Starte Frontend (Backend muss laufen):
```bash
cd frontend && npm run dev
```

Öffne `http://localhost:3001` im Browser und arbeite die Smoke-Test-Checkliste aus der Spec ab (siehe Task 14, Done-Datei). **Dieser Schritt ist menschlich** — der Subagent dokumentiert in der Done-Datei nur, dass die Liste erstellt wurde und welche Items zu prüfen sind.

---

### Task 14: Done-Datei schreiben

**Files:**
- Create: `docs/features/43a-shadcn-wizard-forms-done.md`

- [ ] **Step 1: Done-Datei anlegen**

Inhalt von `docs/features/43a-shadcn-wizard-forms-done.md`:

```markdown
# Feature 43a: shadcn-Migration Phase 1 (Wizard-Forms & Inputs) — Done

**Datum:** 2026-05-05
**Spec:** [docs/superpowers/specs/2026-05-05-shadcn-wizard-forms-design.md](../superpowers/specs/2026-05-05-shadcn-wizard-forms-design.md)
**Plan:** [docs/superpowers/plans/2026-05-05-shadcn-wizard-forms.md](../superpowers/plans/2026-05-05-shadcn-wizard-forms.md)

## Zusammenfassung

10 Frontend-Dateien auf die existierenden shadcn-Wrapper `<Input>` / `<Textarea>` migriert. Logik unverändert. Hybrid-Optik: shadcn-Defaults für Border/Background/Focus, bestehende Layout-Klassen (`w-full`, `min-h-*`, `resize-*`, `rows`) durchgereicht.

### Migrierte Dateien

| # | Datei | Edits |
|---|-------|-------|
| 1 | `wizard/steps/IdeaForm.tsx` | 1× Input, 1× Textarea, cn-Import entfernt |
| 2 | `wizard/steps/ProblemForm.tsx` | 1× Input, 1× Textarea |
| 3 | `wizard/steps/MvpForm.tsx` | 2× Textarea |
| 4 | `wizard/steps/ArchitectureForm.tsx` | 1× Textarea |
| 5 | `chat/ChatPanel.tsx` | 1× Textarea (embedded mit Override-Klassen) |
| 6 | `decisions/DecisionCard.tsx` | 1× Textarea |
| 7 | `clarifications/ClarificationCard.tsx` | 1× Textarea |
| 8 | `app/projects/new/page.tsx` | 1× Input |
| 9 | `wizard/TagInput.tsx` | 1× Input (embedded mit Override-Klassen) |
| 10 | `wizard/steps/features/FeaturesFallbackList.tsx` | 1× Input (embedded), 1× Textarea |

### Abweichungen vom Plan / Spec

- **Scope-Reduktion:** Spec listete 14 Dateien, tatsächlich nur 10. `BackendForm`, `FrontendForm`, `DesignForm`, `DocumentsPanel` haben keine nativen `<input>`/`<textarea>` (nur ChipSelects, Buttons, file-Inputs) → out of scope.
- (Ggf. weitere Abweichungen hier dokumentieren.)

## Verifikation

### Automatisiert
- [x] `npm run lint` grün
- [x] `npm run build` grün
- [x] `npm run test:e2e` grün (oder vermerkt, falls Backend nicht verfügbar)

### Manueller Smoke-Test
- [ ] `/projects/new` — Projekt-Name eintippen, „Anlegen" funktioniert
- [ ] Wizard IDEA — Produktname + Vision speichern (geprüft via `data/projects/{id}/spec/idea.md`)
- [ ] Wizard PROBLEM — Eingabefelder funktionieren (Kernproblem, Primäre Zielgruppe), TagInput für Pain Points
- [ ] Wizard MVP — Goal + Erfolgskriterien tippen
- [ ] Wizard ARCHITECTURE — Architektur-Notizen tippen
- [ ] Chat-Panel — Textarea akzeptiert mehrzeilige Eingabe, Submit per Enter
- [ ] Decision editieren — Rationale tippen, „Confirm Choice"
- [ ] Clarification editieren — Answer tippen, „Answer"
- [ ] FeaturesFallbackList — Title + Description editieren (Trash, Checkbox, Select bleiben in 43b/43c)
- [ ] Dark-Mode — Border und Focus-Ring konsistent

## Offene Punkte / Technische Schulden

- **43b** (Buttons): ~13 Dateien mit nativem `<button>` warten auf Migration
- **43c** (Dialoge & Selects): Custom-Dialoge, `<select>`-Elemente, Checkboxen, file-Inputs
- (Ggf. konkrete Edge-Cases aus dem Smoke-Test hier ergänzen.)
```

- [ ] **Step 2: Done-Datei committen**

```bash
git add docs/features/43a-shadcn-wizard-forms-done.md
git commit -m "docs: feature 43a done — shadcn wizard forms migration

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (durchgeführt)

**1. Spec coverage:** Alle 10 Dateien aus dem korrigierten Scope haben einen Task. Hybrid-Optik, Override-Edge-Case (TagInput), `cn()`-Cleanup, Verifikations-Strategie sind abgedeckt. ChatPanel braucht ebenfalls Override-Klassen (analog TagInput) — wurde explizit in Task 6 eingebaut.

**2. Placeholder scan:** Keine TBDs. Jeder Code-Step zeigt vollständigen vorher/nachher-Code. Verifikations-Befehle sind exakt.

**3. Type consistency:** `Input` und `Textarea` sind aus `@/components/ui/input` bzw. `@/components/ui/textarea` importiert (konsistent in allen Tasks). Override-Klassen-Set ist konsistent zwischen TagInput, FeaturesFallbackList und ChatPanel.

**4. Scope check:** 14 Tasks, alle innerhalb eines logisch zusammenhängenden Migrationsthemas. Geeignet für subagent-driven development — jede Datei ist unabhängig, Phase-Verifikationen fangen Regressionen früh.
