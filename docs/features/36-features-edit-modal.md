# Feature 36 — Features-Edit-Modal

**Phase:** UX-Polish
**Abhängig von:** Feature 22 (Features-Graph-Wizard, done)
**Aufwand:** S
**Design-Spec:** [`docs/superpowers/specs/2026-05-02-features-edit-modal-design.md`](../superpowers/specs/2026-05-02-features-edit-modal-design.md)

## Problem

Im Wizard-Step **FEATURES** teilt sich die Fläche heute zwischen Graph (links) und resizable Side-Panel (rechts, 280–560px). Solange kein Feature ausgewählt ist, zeigt das rechte Panel nur den Hinweis *"Wähle ein Feature, um es zu bearbeiten."* — die Fläche ist verschenkt. Bei vielen Features oder breiten Verbindungen wird der Graph dadurch unnötig eng und schwer zu lesen.

## Ziel

Die Eingabefelder zum Bearbeiten eines Features ziehen in einen Modal-Dialog um. Der Graph nutzt dann die volle Breite des Wizard-Workspaces. Ein Single-Click auf einen Feature-Node öffnet das Modal sofort. Der Modal nutzt eine 2-spaltige Anordnung (Stammdaten links, Scope-Felder rechts), hat explizite Speichern/Abbrechen-Buttons und schützt unbeabsichtigte Verluste durch Dirty-Tracking.

Begleitend wird **shadcn/ui** als neue Frontend-UI-Bibliothek eingeführt — koexistierend mit den bestehenden base-ui-Komponenten. Für dieses Feature werden nur die nötigen Komponenten installiert (`dialog`, `input`, `textarea`, `label`); die bestehenden `Button`/`Card`/`Badge`/`Progress` aus `components/ui/` bleiben unangetastet.

## Architektur

Siehe Design-Spec für das vollständige Bild. Kurzfassung:

- **Neu:** `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx` — Modal-Komponente mit lokalem Form-State, Dirty-Tracking, 2-Spalten-Layout, Confirm-Dialog beim Schliessen mit Änderungen.
- **Geändert:** `FeaturesGraphEditor.tsx` — Side-Panel-Branch + Resize-Handle + `useResizable`-Aufruf entfernt; Graph nutzt 100% Breite; `<FeatureEditDialog>` wird mit `open={selectedId !== null}` gesteuert.
- **Gelöscht:** `FeatureSidePanel.tsx`.
- **shadcn-Setup:** `npx shadcn@latest init` im `frontend/`, dann `npx shadcn@latest add dialog input textarea label`. Erzeugt `frontend/components.json` und legt die Komponenten in `frontend/src/components/ui/` ab.

## Datenmodell

Keine Änderungen am Domain-Modell oder an der Persistenz. `WizardFeature`, `WizardFeatureEdge`, `FeatureScope` und das Auto-Save-Verhalten des Wizard-Stores bleiben unverändert. Die einzige Verhaltensänderung: `updateFeature` wird pro Bearbeitungsvorgang nur noch einmal (auf "Speichern") aufgerufen, nicht mehr pro Tastendruck.

## UI-Verhalten

- Single-Click auf einen Feature-Node → Modal öffnet, Felder mit dem aktuellen Stand vorbefüllt.
- Eingaben mutieren ausschliesslich lokalen Form-State (`draft`).
- "Speichern" → ein einzelner `updateFeature(id, draft)`-Call, Modal schliesst.
- "Abbrechen" / Esc / Klick auf Overlay → bei Dirty erscheint Confirm `"Änderungen verwerfen?"` (`window.confirm`, konsistent mit bestehendem Stil im Editor); ohne Dirty schliesst direkt.
- "Löschen" → Confirm `"Feature wirklich löschen?"`, dann `removeFeature(id)`, Modal schliesst, `selectedId` wird auf `null` gesetzt.
- Bei nur einem aktiven Scope (Mobile App, CLI Tool) oder Library-Mode (`allowedScopes=[]`) zeigt die rechte Spalte nur die jeweils relevanten Felder ohne Scope-Header.
- Auto-Save-Echo aus dem Store überschreibt bei offenem Modal nicht die Eingaben — Snapshot beim Öffnen ist die Wahrheit.
- Bei kleinen Viewports (`< 768px`) bleibt der bestehende `<FeaturesFallbackList>` aktiv (greift schon vor dem Graph-Render).

## Akzeptanzkriterien

1. Single-Click auf einen Node öffnet das Modal mit den Feldern des Features.
2. Der Graph nutzt die volle Workspace-Breite (kein rechtes Panel, kein Resize-Handle mehr).
3. Modal hat Header "Feature bearbeiten", 2-Spalten-Body (Allgemein | Scope-Felder) und Footer mit `Löschen` (links) sowie `Abbrechen` + `Speichern` (rechts).
4. Eingaben persistieren erst beim Klick auf "Speichern".
5. "Abbrechen", Esc und Overlay-Klick zeigen bei Dirty einen Confirm; ohne Dirty schliessen sie direkt.
6. "Löschen" entfernt das Feature aus dem Graph nach Confirm und schliesst den Modal.
7. Modal-Titel-Sub-Headline (falls vorhanden) updated live mit dem Titel-Eingabefeld.
8. Scope-Chip-Toggle wirkt sofort auf die rechte Spalte (lokal); bei "Abbrechen" wird der Original-Scope wiederhergestellt.
9. Library-Mode (`allowedScopes=[]`) zeigt CORE-Felder; Single-Scope-Mode zeigt nur die Felder dieses Scopes.
10. shadcn-Komponenten sind in `frontend/src/components/ui/{dialog,input,textarea,label}.tsx` installiert; bestehende base-ui-Komponenten (`button.tsx`, `card.tsx`, `badge.tsx`, `progress.tsx`) bleiben unverändert.
11. `FeatureSidePanel.tsx` ist gelöscht; alle Imports entfernt.

## Betroffene Dateien

**Frontend (neu):**
- `frontend/components.json` (shadcn-Init)
- `frontend/src/components/ui/dialog.tsx` (shadcn)
- `frontend/src/components/ui/input.tsx` (shadcn)
- `frontend/src/components/ui/textarea.tsx` (shadcn)
- `frontend/src/components/ui/label.tsx` (shadcn)
- `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx`

**Frontend (geändert):**
- `frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx`
- `frontend/package.json` / `frontend/package-lock.json` (Radix-Dialog/Label-Dependencies via shadcn)

**Frontend (gelöscht):**
- `frontend/src/components/wizard/steps/features/FeatureSidePanel.tsx`

**Backend:** keine Änderungen.

## YAGNI

- Keine Migration der bestehenden base-ui-Komponenten (`Button`, `Card`, `Badge`, `Progress`) auf shadcn.
- Kein Tastatur-Shortcut zum Öffnen (kein `Enter` auf selektiertem Node, kein `n` für neues Feature).
- Kein Keyboard-Delete für Nodes.
- Keine Inline-Edit am Node.
- Keine Mehrfach-Selektion / Bulk-Edit.
- Keine Undo/Redo für Feature-Edits.
- Kein eigenes Confirm-Modal — `window.confirm` reicht (konsistent mit `FeaturesGraphEditor.tsx:163`).
- Kein "Gespeichert ✓"-Indikator — explizites Speichern macht das überflüssig.
