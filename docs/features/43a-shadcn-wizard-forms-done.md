# Feature 43a: shadcn-Migration Phase 1 (Wizard-Forms & Inputs) — Done

**Datum:** 2026-05-05
**Branch:** `feat/43a-shadcn-wizard-forms`
**Spec:** [docs/superpowers/specs/2026-05-05-shadcn-wizard-forms-design.md](../superpowers/specs/2026-05-05-shadcn-wizard-forms-design.md)
**Plan:** [docs/superpowers/plans/2026-05-05-shadcn-wizard-forms.md](../superpowers/plans/2026-05-05-shadcn-wizard-forms.md)

## Zusammenfassung

10 Frontend-Dateien auf die existierenden shadcn-Wrapper `<Input>` / `<Textarea>` migriert. Logik unverändert. Hybrid-Optik: shadcn-Defaults für Border/Background/Focus, bestehende Layout-Klassen (`min-h-*`, `resize-*`, `rows`) durchgereicht. Drei embedded-Cases (innerhalb gestylter Outer-Boxen) verwenden Override-Klassen, um Default-Chrome zu neutralisieren.

### Migrierte Dateien

| # | Datei | Edits |
|---|-------|-------|
| 1 | `wizard/steps/IdeaForm.tsx` | 1× Input, 1× Textarea, `cn`-Import entfernt |
| 2 | `wizard/steps/ProblemForm.tsx` | 1× Textarea, 1× Input |
| 3 | `wizard/steps/MvpForm.tsx` | 2× Textarea |
| 4 | `wizard/steps/ArchitectureForm.tsx` | 1× Textarea |
| 5 | `chat/ChatPanel.tsx` | 1× Textarea (embedded mit Override-Klassen + `dark:bg-transparent`), `cn`-Import entfernt |
| 6 | `decisions/DecisionCard.tsx` | 1× Textarea (`cn` weiter benötigt) |
| 7 | `clarifications/ClarificationCard.tsx` | 1× Textarea (`cn` weiter benötigt) |
| 8 | `app/projects/new/page.tsx` | 1× Input, `cn`-Import entfernt |
| 9 | `wizard/TagInput.tsx` | 1× Input (embedded mit Override-Klassen + `dark:bg-transparent`) |
| 10 | `wizard/steps/features/FeaturesFallbackList.tsx` | 1× Input (embedded), 1× Textarea — `<button>` Trash, `<input type="checkbox">`, `<select>` bewusst unverändert (43b/43c) |

### Commits (11 auf `feat/43a-shadcn-wizard-forms`)

```
5d04556 refactor(frontend): use shadcn Input/Textarea in IdeaForm
22f00f7 refactor(frontend): use shadcn Input/Textarea in ProblemForm
c265d18 refactor(frontend): use shadcn Textarea in MvpForm
96209b8 refactor(frontend): use shadcn Textarea in ArchitectureForm
1370b59 refactor(frontend): use shadcn Textarea in ChatPanel
1abe289 refactor(frontend): use shadcn Textarea in DecisionCard
3e73c81 refactor(frontend): use shadcn Textarea in ClarificationCard
75b8874 refactor(frontend): use shadcn Input in NewProjectPage
01a7f9d refactor(frontend): use shadcn Input (embedded) in TagInput
30b6ac6 refactor(frontend): use shadcn Input/Textarea in FeaturesFallbackList
b59d6d0 fix(frontend): add dark:bg-transparent to embedded shadcn Input/Textarea
```

## Abweichungen vom Plan / Spec

### Scope-Reduktion: 14 → 10 Dateien

Die ursprüngliche Spec listete 14 Dateien. Bei der detaillierten Analyse während des Planschritts stellte sich heraus, dass folgende vier Dateien keine nativen `<input>`/`<textarea>` enthalten und aus 43a herausfallen:

- `wizard/steps/BackendForm.tsx` — nur `ChipSelect` (43c)
- `wizard/steps/FrontendForm.tsx` — nur `ChipSelect` (43c)
- `wizard/steps/design/DesignForm.tsx` — nur `Button` + `DesignDropzone` (file-input → 43c)
- `documents/DocumentsPanel.tsx` — der einzige `<input>` ist `type="file"` (43c)

### Zusätzlicher Fix: `dark:bg-transparent`

Während Code-Review von Task 11 (TagInput) wurde entdeckt, dass die shadcn-Defaults `dark:bg-input/30` enthalten — `bg-transparent` allein reicht nicht aus, da `tailwind-merge` die `dark:`-Variante als orthogonale Klasse erhält. Da die App per Default in Dark-Mode läuft (`<html class="dark">` in `layout.tsx`), würde ohne diesen Fix ein zarter Dark-Background durch die embedded Inputs durchscheinen.

Lösung: `dark:bg-transparent` als zusätzliche Override-Klasse bei allen drei embedded-Cases:
- ChatPanel.tsx (Fixup-Commit `b59d6d0`)
- TagInput.tsx (Fixup-Commit `b59d6d0`)
- FeaturesFallbackList.tsx (proaktiv beim Original-Commit `30b6ac6`)

## Verifikation

### Automatisiert

- ✅ `npm run lint`: 32 Probleme (16 Errors + 16 Warnings) — **identisch zur Baseline**, keine Regressionen
- ✅ `npm run build`: Exit 0, „Compiled successfully"
- ⏭️ `npm run test:e2e`: Skip (Backend war zur Verifikationszeit nicht aktiv) — wird beim nächsten lokalen Backend-Lauf nachgezogen

### Manueller Smoke-Test (durch Mensch zu erledigen)

- [ ] `/projects/new` — Projekt-Name eintippen, „Create Project" funktioniert (cancel-Knopf zurück zu /projects)
- [ ] Wizard IDEA — Produktname + Vision speichern, geprüft via `data/projects/{id}/spec/idea.md`
- [ ] Wizard PROBLEM — Kernproblem (Textarea) + Primäre Zielgruppe (Input) speichern, TagInput für Pain Points (Enter/Komma fügt hinzu, Backspace auf leerem Input entfernt letzten Tag)
- [ ] Wizard MVP — Goal + Erfolgskriterien tippen
- [ ] Wizard ARCHITECTURE — Architektur-Notizen tippen (Textarea)
- [ ] Chat-Panel — Mehrzeilige Eingabe (Shift+Enter), Submit per Enter, disabled-Zustand während des Sendens
- [ ] Decision editieren — Rationale tippen (Textarea), „Confirm Choice" funktioniert
- [ ] Clarification editieren — Answer tippen (Textarea), „Answer" funktioniert
- [ ] FeaturesFallbackList — Title (embedded Input) + Description (Textarea) editieren; Trash/Checkbox/Select bleiben funktional unverändert
- [ ] Dark-Mode visuell prüfen — keine sichtbaren Backgrounds bei den embedded Inputs (ChatPanel, TagInput, FeaturesFallbackList Title)

## Offene Punkte / Technische Schulden

- **43b — Buttons:** ~13 Dateien mit nativem `<button>` warten auf Migration zu `<Button>` (BlockerBanner, ChipSelect, StepIndicator, FileTree, SpecFileViewer, BundleList/Upload/Detail, GraphMeshToggle, FeaturesFallbackList Trash, etc.)
- **43c — Dialoge & Selects:**
  - 2-3 Custom-Dialoge (DeleteProjectDialog, ExportDialog, ggf. HandoffDialog) auf `<Dialog>`
  - HandoffDialog enthält zusätzlich noch ein natives `<textarea>` — fiel weder in 43a-Spec noch in 43a-Plan-Scope auf; wird beim Dialog-Umbau mit-migriert.
  - `<select>`-Elemente in FeaturesFallbackList und BundleList migrieren — erfordert `npx shadcn@latest add select`
  - `<input type="checkbox">` in FeaturesFallbackList und GraphMeshToggle migrieren — `npx shadcn@latest add checkbox`
  - File-Inputs in DesignDropzone, BundleUpload, DocumentsPanel
- **Pre-existing Lint-Errors:** Die 16 `no-explicit-any`-Errors und 16 `no-unused-vars`-Warnings im Frontend sind unverändert geblieben (nicht im Scope dieser Migration). Eigenes Cleanup-Feature wäre sinnvoll.

## Hinweise für Reviewer

- Logik-, State-, Validierungs-Verhalten ist bewusst nicht angefasst — reines Wrapper-Refactoring
- Visuelle Unterschiede sind möglich (Border-Farbe, Focus-Ring, leichte Padding-Differenz: shadcn `Input` hat `h-8 py-1` vs. vorher `py-2`) — siehe Hybrid-Optik-Strategie in der Spec
- shadcn `Textarea` nutzt `field-sizing-content` (Auto-Grow) — für `chat/ChatPanel` und alle Wizard-Forms ist das ein UX-Vorteil; `min-h-*` bleibt als Mindesthöhe
- Drei embedded-Cases (ChatPanel, TagInput, FeaturesFallbackList Title-Input) nutzen Override-Klassen, um Default-Chrome zu neutralisieren — siehe Code-Kommentare in den Files (sofern vorhanden) oder die Spec-Sektion „Edge Case TagInput"
