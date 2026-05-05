# Design: shadcn-Migration Phase 1 — Wizard-Forms & Inputs (Feature 43a)

**Datum:** 2026-05-05
**Feature-Nummer:** 43a (Phase 1 von 3)
**Status:** Spezifikation
**Phase-Folgen:** 43b (Buttons), 43c (Dialoge & Selects)

## Problem & Ziel

Das Frontend hat seit Feature 36 shadcn/ui (Style `base-nova`) als Component-Library etabliert. Die Foundation steht: `components.json`, `Input` und `Textarea` sind in `src/components/ui/` installiert. Trotzdem nutzen ~14 Feature-Komponenten weiterhin native `<input>`/`<textarea>`-Elemente mit copy-paste Tailwind-Klassen.

**Folgen:**
- Style-Drift zwischen Forms (verschiedene Border-, Focus-, Padding-Werte)
- Doppelte Wartung beim Design-Token-Update
- shadcn-Verbesserungen (a11y, Auto-Grow, Aria-Invalid) erreichen die Forms nicht

**Ziel von Phase 1:** Alle Wizard-Forms, Chat-Eingabe, Karten-Editoren und das Projekt-Anlage-Form auf die existierenden `<Input>`/`<Textarea>`-Wrapper migrieren. Buttons, Selects und Dialoge bleiben Phase 2/3.

## Scope

### In Scope (14 Dateien)

| # | Datei | Native Elemente → Ersatz |
|---|-------|---|
| 1 | `frontend/src/components/wizard/steps/IdeaForm.tsx` | `<input>` Z.16 → `<Input>`, `<textarea>` Z.20 → `<Textarea>` |
| 2 | `frontend/src/components/wizard/steps/ProblemForm.tsx` | `<input>` Z.22 → `<Input>`, `<textarea>` Z.17 → `<Textarea>` |
| 3 | `frontend/src/components/wizard/steps/MvpForm.tsx` | alle nativen `<input>`/`<textarea>` → shadcn-Wrapper |
| 4 | `frontend/src/components/wizard/steps/ArchitectureForm.tsx` | dto. |
| 5 | `frontend/src/components/wizard/steps/BackendForm.tsx` | dto. |
| 6 | `frontend/src/components/wizard/steps/FrontendForm.tsx` | dto. |
| 7 | `frontend/src/components/wizard/steps/design/DesignForm.tsx` | `<input>`/`<textarea>` (NICHT der file-`<input>` in `DesignDropzone`) |
| 8 | `frontend/src/components/chat/ChatPanel.tsx` | `<textarea>` Z.70 → `<Textarea>` |
| 9 | `frontend/src/components/decisions/DecisionCard.tsx` | `<textarea>` → `<Textarea>` |
| 10 | `frontend/src/components/clarifications/ClarificationCard.tsx` | `<textarea>` → `<Textarea>` |
| 11 | `frontend/src/app/projects/new/page.tsx` | `<input>` Z.63–76 → `<Input>` |
| 12 | `frontend/src/components/documents/DocumentsPanel.tsx` | `<input>` (kein file-input) → `<Input>` |
| 13 | `frontend/src/components/wizard/TagInput.tsx` | eingebetteter `<input>` Z.48 → `<Input>` mit Override-Klassen |
| 14 | `frontend/src/components/wizard/steps/features/FeaturesFallbackList.tsx` | `<input>` Z.45 → `<Input>`, `<textarea>` Z.54 → `<Textarea>` (`<select>` Z.82 und Trash-`<button>` Z.50 bleiben — sie gehören zu 43c bzw. 43b) |

### Out of Scope (bewusst ausgeschlossen)

- Buttons → Phase 2 (43b)
- `<select>`-Elemente, Custom-Dialoge, Checkboxen, file-Inputs → Phase 3 (43c)
- Bestehende base-ui-basierte Komponenten (`Button`, `Card`, `Badge`, `Progress`) — laut `frontend/CLAUDE.md` bewusste Koexistenz
- Änderungen an `src/components/ui/` (Foundation bleibt unverändert)
- Änderungen am Theme (`globals.css` `@theme`-Blöcke)
- Form-Validierung
- a11y-Audit über das Triviale hinaus
- Neue Tests einführen

## Architektur & Vorgehen

**Mechanisches Wrapper-Refactoring.** Logik, State und Validierung bleiben unverändert.

### Optik-Strategie: Hybrid (C)

- **shadcn-Defaults gewinnen:** Border, Background, Focus-Ring, Padding, Font-Size, Rounded
- **Layout-Klassen bleiben:** `w-full`, `min-h-[100px]`, `resize-y`, `rows={N}`
- **Entfernt werden** redundante Default-Style-Klassen wie `bg-input`, `border`, `px-3 py-2`, `focus:ring-*`, `rounded-md`, `text-sm`, `placeholder:text-muted-foreground`

### Auto-Grow-Textarea

shadcn-`Textarea` nutzt `field-sizing-content` (CSS Auto-Grow). Damit ersetzen wir das bisherige `resize-y`-Verhalten implizit. Begründung:
- Verbessert UX (kein manuelles Resizen)
- Browser-Support modern (Safari 17.4+, Chrome 123+) ist für die Wizard-App akzeptabel
- `min-h-[100px]` o.ä. bleibt als Mindesthöhe
- Falls eine Stelle zwingend `resize-y` braucht, kann lokal überschrieben werden

### Edge Case TagInput

`TagInput.tsx` rendert einen `<input>` **innerhalb** einer Outer-Box ohne eigenen Border (Z. 36–57). Ein direktes `<Input>` brächte doppelten Border + Focus-Ring. Lösung: minimal-invasive Override-Klassen.

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

Tab-Navigation und Submit-Verhalten werden im Smoke-Test verifiziert.

### Beispiel: IdeaForm vorher / nachher

**Vorher** (`IdeaForm.tsx` Z.16–22):
```tsx
<input value={get("productName")} onChange={(e) => set("productName", e.target.value)}
  placeholder="z.B. TaskFlow Pro"
  className={cn("w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring")} />

<textarea value={get("vision")} onChange={(e) => set("vision", e.target.value)}
  placeholder="Beschreibe deine Produktidee in 2-3 Saetzen..." rows={4}
  className={cn("w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring min-h-[100px]")} />
```

**Nachher:**
```tsx
<Input value={get("productName")} onChange={(e) => set("productName", e.target.value)}
  placeholder="z.B. TaskFlow Pro" />

<Textarea value={get("vision")} onChange={(e) => set("vision", e.target.value)}
  placeholder="Beschreibe deine Produktidee in 2-3 Saetzen..." rows={4}
  className="min-h-[100px]" />
```

`cn()`-Import bleibt nur, wenn er sonst noch gebraucht wird; sonst entfernen.

## Konsistenz-Regeln pro Edit

1. Import: `import { Input } from "@/components/ui/input";` bzw. `import { Textarea } from "@/components/ui/textarea";`
2. Reine Default-Style-Klassen aus `cn(...)` entfernen
3. Layout-Klassen via `className`-Prop durchreichen
4. `placeholder`, `value`, `onChange`, `rows`, `required`, `name`, `type` bleiben 1:1
5. Keine neuen Hooks, keine neuen Imports außer den UI-Komponenten
6. Keine logischen Änderungen
7. Wenn `cn()` durch das Edit unbenutzt wird: Import entfernen (Lint sonst rot)

## Risiken & Mitigation

| Risiko | Mitigation |
|---|---|
| Visuelle Regression durch andere Default-Optik | Hybrid bewusst akzeptiert; Smoke-Test fängt Härtefälle |
| Auto-Grow-Textarea fühlt sich anders an | Bewusste UX-Verbesserung; lokal überschreibbar |
| `TagInput`-Override bricht Tab-Navigation oder a11y | Override minimal halten; manueller Tab-Test |
| `MvpForm`/`ArchitectureForm`/`BackendForm`/`FrontendForm` enthalten mehr als Inputs (Listen, Drag&Drop) | Vor Edit jede Datei lesen, nur identifizierte Stellen anfassen, Rest 43b/43c |
| `cn()`-Import wird nach Edit unbenutzt → ESLint-Error | Cleanup-Schritt pro Datei |

## Verifikation

### Automatisiert
1. `cd frontend && npm run lint` → 0 Errors, 0 neue Warnings
2. `cd frontend && npm run build` → grün, keine TypeScript-Fehler
3. `cd frontend && npm run test:e2e` → `features-graph.spec.ts` grün

### Manuell (Browser-Checkliste in `43a-...-done.md` festhalten)
- [ ] `/projects/new` — Projekt-Name eintippen, "Anlegen" funktioniert
- [ ] Wizard IDEA → Produktname + Vision speichern (geprüft via `data/projects/{id}/spec/idea.md`)
- [ ] Wizard PROBLEM → Eingabefelder funktionieren
- [ ] Wizard MVP, ARCHITECTURE, BACKEND, FRONTEND → je ein Eingabe-Test pro Step
- [ ] Wizard DESIGN → Form-Inputs funktionieren (Dropzone bleibt nativ)
- [ ] Chat-Panel → Textarea akzeptiert mehrzeilige Eingabe, Submit per Enter
- [ ] Decision/Clarification editieren → Textarea-Edits speichern
- [ ] TagInput → Tag mit Enter/Komma hinzufügen, mit Backspace entfernen, Tab-Navigation
- [ ] Dark-Mode optisch konsistent (Border, Focus-Ring)

## Akzeptanzkriterien

1. In allen 14 Dateien sind die im Scope identifizierten nativen `<input>`/`<textarea>` durch `<Input>`/`<Textarea>` ersetzt
2. `npm run lint`, `npm run build`, `npm run test:e2e` grün
3. Manueller Smoke-Test laut Checkliste komplett bestanden
4. Done-Datei `docs/features/43a-shadcn-wizard-forms-done.md` enthält Smoke-Test-Ergebnisse, Abweichungen vom Plan und offene Punkte
5. Keine Datei außerhalb des Scopes verändert (insb. keine Edits in `src/components/ui/`, keine Theme-Änderungen)

## Folgefeatures (Hinweis)

- **43b — Buttons-Migration:** ~13 Dateien mit nativem `<button>` → `<Button>` aus `@/components/ui/button`
- **43c — Dialoge & Selects:** Custom-Dialoge auf `<Dialog>` umbauen; `<select>`-Elemente migrieren (erfordert `npx shadcn@latest add select`); ggf. Checkbox-Migration

Beide Folgefeatures bekommen eigene Brainstorming-Sessions und eigene Spec-/Plan-/Done-Dateien.
