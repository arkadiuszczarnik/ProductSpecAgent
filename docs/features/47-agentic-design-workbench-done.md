# Feature 47 - Agentic Design Workbench - Done

**Datum:** 2026-05-11
**Feature-Doc:** `docs/features/47-agentic-design-workbench.md`
**Simple-V1-Spec:** `docs/superpowers/specs/2026-05-10-simple-design-generator-v1-design.md`
**Bildanalyse-Spec:** `docs/superpowers/specs/2026-05-10-design-image-analysis-agent-design.md`
**Design-Artefakt-Spec:** `docs/superpowers/specs/2026-05-10-design-artifact-reference-design.md`
**Simple-V1-Plan:** `docs/superpowers/plans/2026-05-10-simple-design-generator-v1.md`
**Bildanalyse-Plan:** `docs/superpowers/plans/2026-05-10-design-image-analysis-agent.md`
**Design-Artefakt-Plan:** `docs/superpowers/plans/2026-05-10-design-artifact-reference.md`

## Zusammenfassung

Der DESIGN-Step wurde von der urspruenglichen ZIP-/Multi-Screen-Workbench auf eine vereinfachte agentische V1 reduziert.

Der aktuelle produktive Stand:

- Keine ZIP-Dropzone im normalen DESIGN-Step.
- Nutzer koennen eine Beschreibung, ein Bild oder beides als Input speichern.
- Ein spezieller Bildanalyse-Agent analysiert hochgeladene Bilder.
- Die Bildanalyse liefert ein detailliertes JSON-Modell mit Palette, Typografie, Layout-Hierarchie, Komponenten, Mood-Tags, Brand-Signalen und `designBrief`.
- Der Design-Generator erzeugt genau eine self-contained HTML-Vorlage.
- Die HTML-Vorlage wird als Canvas/iframe-Preview angezeigt.
- `Neu generieren` ersetzt das aktive Design.
- `Design uebernehmen` ist erst moeglich, wenn ein gueltiges `currentDesign` existiert.
- Completion schreibt die Design-Zusammenfassung dauerhaft nach `design/design.md`.
- Completion schreibt die aktive HTML-Ausgabe nach `design/screens/design/index.html`.
- Die finale `spec/spec.md` verweist auf `design/design.md`, statt den Design-Inhalt zu duplizieren.
- Export und Handoff enthalten die aktiven Design-Artefakte unter `design/...`.

## Entfernt Gegenueber Dem Ursprungsentwurf

Die folgenden Konzepte sind nicht Teil der V1-Implementierung:

- Claude-Design-ZIP-Upload im normalen DESIGN-Step.
- HTML/CSS-Snippet-Upload.
- Mehrere Screens.
- Variantenliste oder Variantenhistorie.
- Screen-Proposal-Workflow.
- Suggestion-Liste mit Apply-Aktionen.
- Reference-Classification-UI.
- Figma-artiger Canvas mit Element-Auswahl, Drag/Resize oder Property-Editor.
- Legacy-Kompatibilitaet fuer alte Workbench-Felder.

Alte `DesignBundle*`-Klassen existieren noch fuer bestehende Endpunkte/Tests, sind aber nicht der produktive DESIGN-Step-Pfad dieser V1.

## Backend-Stand

Aktuelle Endpunkte unter `/api/v1/projects/{projectId}/design`:

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/workbench` | Workbench-Zustand laden |
| `PUT` | `/input` | Beschreibung und/oder Bild speichern |
| `POST` | `/image/analyze` | Gespeichertes Bild analysieren |
| `POST` | `/generate` | Design generieren und aktives Ergebnis ersetzen |
| `GET` | `/preview` | Aktives HTML sicher ausliefern |
| `POST` | `/complete` | DESIGN-Step abschliessen |

`DesignWorkbenchStorage` speichert aktuell:

- `projects/{projectId}/design/workbench.json`
- `projects/{projectId}/design/input/reference-image`
- `projects/{projectId}/design/current/index.html`
- `projects/{projectId}/design/design.md`
- `projects/{projectId}/design/screens/design/index.html`

`DesignImageAnalysisAgent` nutzt den Agent `design-image-analysis` und den Prompt `design-image-analysis-system`.

`DesignVariantAgent` nutzt Beschreibung, Bild-Metadaten und vorhandene `imageAnalysis` fuer die HTML-Erzeugung. Wenn ein Bild vorhanden ist und noch keine Analyse existiert, fuehrt `DesignWorkbenchService.generate()` die Bildanalyse automatisch aus.

Die Preview validiert generiertes HTML vor dem Speichern und blockiert externe Subresources, Netzwerk-APIs, Browser-Storage, Cookies sowie Parent-/Opener-Zugriffe.

## Frontend-Stand

Die UI besteht aus:

- `DesignInputPanel`
  - Beschreibung
  - Bild-Upload
  - Bildanalyse-Status und kompakte Analyseanzeige
  - Generate-/Regenerate-Aktion
  - Complete-Aktion
- `DesignCanvasPreview`
  - Empty State
  - Loading State
  - iframe-Preview fuer `/design/preview?v={currentDesign.id}`
- `design-workbench-store`
  - `load`
  - `saveInput`
  - `analyzeImage`
  - `generate`
  - `complete`
  - `reset`

## Export Und Handoff

Nach Completion werden nur die aktiven Design-Artefakte exportiert:

```text
design/design.md
design/screens/design/index.html
```

Inaktive oder stale Design-Screen-Dateien werden nicht exportiert.

Die generierte Projektspezifikation liegt weiterhin unter:

```text
docs/spec.md
```

Wenn ein Design-Artefakt existiert, enthaelt die finale Spec eine kurze Design-Sektion mit Links auf:

```text
../design/design.md
../design/screens/design/index.html
```

## Wichtige Fixes Nach V1

- Bildanalyse-Ergebnisse werden nur gespeichert, wenn sie noch zum analysierten Bild passen. Ein langsames Analyseergebnis kann dadurch kein inzwischen neu hochgeladenes Bild ueberschreiben.
- Bildanalyse-Fehler erhalten bestehende Analysen, damit ein Retry-Fehler nicht die letzte nutzbare Analyse entfernt.
- `design/design.md` liegt ausserhalb von `spec/`, damit das finale Speichern von `spec/spec.md` die Design-Zusammenfassung nicht loescht.
- Export und Handoff enthalten `design/design.md` zusammen mit der aktiven HTML-Preview.

## Verifikation

Ausgefuehrt:

```bash
cd backend && ./gradlew test
cd frontend && npm run build
git diff --check
```

Ergebnis:

- Backend-Tests erfolgreich.
- Frontend-Build erfolgreich.
- Diff-Whitespace-Check erfolgreich.

Bekannter Reststand:

- `cd frontend && npm run lint` scheitert weiterhin an bestehenden Baseline-Fehlern in unveraenderten Frontend-Dateien, nicht an der DESIGN-V1-Implementierung.

## Offene Folgearbeit

- Produktivere strukturierte Agent-Antwortparser mit besserer Fehlerdiagnose.
- Preview-UX fuer verschiedene Viewport-Groessen.
- Optional spaeter: Variantenhistorie als neues Feature, nicht als Legacy-Kompatibilitaet.
- Optional spaeter: Design-System-Ableitung aus Bildanalyse und generiertem HTML.
