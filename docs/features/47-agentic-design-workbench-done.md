# Feature 47 - Agentic Design Workbench - Done

**Datum:** 2026-05-09
**Feature-Doc:** `docs/features/47-agentic-design-workbench.md`
**Spec:** `docs/superpowers/specs/2026-05-09-agentic-design-workbench-design.md`
**Plan:** `docs/superpowers/plans/2026-05-09-agentic-design-workbench.md`

## Zusammenfassung

- Der DESIGN-Step zeigt die neue Workbench statt der ZIP-Dropzone.
- Workbench-Domain, Storage, Agents, API und Frontend-UI wurden umgesetzt.
- Text-, Bild- und HTML/CSS-Snippet-Inputs koennen erfasst und sichtbar korrigiert werden.
- Screens koennen vorgeschlagen, manuell angelegt, umbenannt und entfernt werden.
- Mindestens ein aktiver gueltiger Screen ist fuer Completion erforderlich.
- Completion schreibt `design/design.md` und aktive HTML/CSS-Screen-Dateien; die finale Spec verweist auf das Design-Artefakt.
- Export und Handoff enthalten aktive Workbench-Ausgaben unter `design/screens/...`.
- Preview-HTML wird serverseitig gegen externe, lokale und Projekt-API-Subresource-Zugriffe validiert.

## Verifikation

- `cd backend && ./gradlew test` erfolgreich.
- `cd frontend && npm run build` erfolgreich.
- Feature-Datei-Lint erfolgreich.
- `cd frontend && npm run lint` zeigt weiterhin bekannte Baseline-Fehler in unveraenderten Dateien.

## Abweichungen

- Der manuelle `./start.sh`-Smoke konnte nicht frisch ausgefuehrt werden, weil bereits lokale Prozesse auf `3000` und `8080` liefen. Ein API-Smoke gegen diese laufenden Prozesse wurde durch den bestehenden Backend-Prozess fuer neue `/design/...`-Workbench-Endpunkte mit `401` blockiert.
- `ReferenceAnalysisAgent`, `ScreenProposalAgent` und `DesignVariantAgent` sind als Pipeline und testbare Komponenten vorhanden, nutzen in V1 aber Fallback-Logik statt produktiver Koog-Prompt-/Parser-Orchestrierung.

## Offene Punkte

- Alte `DesignBundle*`-Klassen koennen in einem Folge-Refactor entfernt werden, sobald keine Migration mehr benoetigt wird.
- Produktive Agent-Prompts, strukturierte LLM-Antwortparser und bessere Nutzung von Wizard-/Referenzkontext sollten in einer Folgeiteration ausgebaut werden.

## 2026-05-10 Simplified V1 Pivot

The DESIGN step was reduced to the Simple Design Generator V1:

- Description, image, or both as the only visible inputs.
- One active generated HTML canvas design.
- Regenerate replaces the active design.
- Screens, variants, snippets, classifications, and suggestions are no longer visible in the V1 UI.
