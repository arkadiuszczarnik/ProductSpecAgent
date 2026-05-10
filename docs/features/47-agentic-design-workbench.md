# Feature 47 - Agentic Design Workbench

**Phase:** Wizard / Design
**Abhaengig von:** Feature 11 (Guided Wizard Forms), Feature 12 (Dynamische Wizard-Steps), Feature 13 (Wizard-Chat Integration), Feature 38 (Per-Agent Model Selection), Feature 40 (Design-Bundle-Step), Feature 46 (Wizard-Options-Admin)
**Aufwand:** M
**Status:** Umgesetzt als Simple Design Generator V1 mit Bildanalyse-Agent
**Aktuelle Spec:** [`docs/superpowers/specs/2026-05-10-simple-design-generator-v1-design.md`](../superpowers/specs/2026-05-10-simple-design-generator-v1-design.md)
**Bildanalyse-Spec:** [`docs/superpowers/specs/2026-05-10-design-image-analysis-agent-design.md`](../superpowers/specs/2026-05-10-design-image-analysis-agent-design.md)
**Umsetzungsplan:** [`docs/superpowers/plans/2026-05-10-simple-design-generator-v1.md`](../superpowers/plans/2026-05-10-simple-design-generator-v1.md)

## Kurzfassung

Der DESIGN-Step ist kein ZIP-Import und keine grosse Multi-Screen-Workbench mehr. Der aktuelle Stand ist ein vereinfachter agentischer Generator:

- Nutzer geben eine Designbeschreibung, ein Bild oder beides ein.
- `DesignImageAnalysisAgent` analysiert hochgeladene Bilder und erzeugt strukturierte Designsignale.
- `DesignVariantAgent` nutzt Beschreibung und Bildanalyse, um genau eine self-contained HTML-Vorlage zu erzeugen.
- Die Vorlage wird als Canvas/Preview im iframe gerendert.
- `Neu generieren` ersetzt das aktive Design.
- `Design uebernehmen` schliesst den DESIGN-Step erst ab, wenn ein gueltiges generiertes Design existiert.
- Export und Handoff enthalten nur die aktive generierte Design-Datei unter `design/screens/design/index.html`.

Es gibt keine Workbench-Kompatibilitaet fuer alte Inputs, Screens, Varianten, Suggestions oder Klassifikationen mehr.

## Problem

Der urspruengliche DESIGN-Step war auf Claude-Design-ZIP-Bundles ausgelegt. Danach wurde ein zu grosser agentischer Workbench-Entwurf geplant: Inputs, Snippets, Klassifikation, Screen-Vorschlaege, Variantenlisten und Suggestions. Fuer die erste produktive Version war das zu breit und schwer zu stabilisieren.

Der aktuelle Produktbedarf ist enger: Der User soll schnell aus Beschreibung und/oder Bild eine pruefbare HTML-Designvorlage bekommen, diese im Canvas sehen und den Wizard damit fortsetzen.

## Zielbild V1

Der DESIGN-Step fuer frontend-faehige Kategorien zeigt einen fokussierten Generator:

1. Beschreibung eintragen.
2. Optional ein Bild hochladen.
3. `Design generieren` ausfuehren.
4. Generiertes HTML im Canvas pruefen.
5. Optional `Neu generieren`.
6. `Design uebernehmen`, wenn das Ergebnis passt.

DESIGN bleibt fuer frontend-relevante Kategorien sichtbar und wird fuer Kategorien ohne Frontend weiterhin nicht als Pflichtschritt genutzt.

## In Scope

- Vereinfachtes `DesignWorkbench`-Modell mit einer Eingabe und einem aktiven Ergebnis.
- Multipart-Upload fuer eine optionale Bildreferenz.
- Beschreibung-only, Bild-only und kombinierte Eingaben.
- Strukturierte Bildanalyse ueber `POST /design/image/analyze`.
- Automatische Bildanalyse bei `POST /design/generate`, wenn sie fuer ein gespeichertes Bild noch fehlt.
- Ein `DesignVariantAgent`, der ueber Koog und den editierbaren Prompt `design-variant-system` ein JSON-Ergebnis erzeugt.
- Deterministischer Fallback fuer lokale Entwicklung und Tests.
- Serverseitige Preview-Validierung vor dem Speichern.
- Sandboxed iframe-Preview ueber `/design/preview`.
- Completion-Gating: Ohne `currentDesign` kein Abschluss.
- Export/Handoff ueber die aktive Datei `design/screens/design/index.html`.
- Keine Legacy-Kompatibilitaet fuer alte Workbench-Felder oder alte Service-/Storage-Methoden.

## Out of Scope

- ZIP-Upload im normalen DESIGN-Step.
- HTML/CSS-Snippet-Upload in V1.
- Mehrere Screens.
- Variantenhistorie oder Varianten-Picker.
- Reference-Classification-UI.
- Agent-Suggestion-Liste mit Apply-Workflow.
- Figma-artiger Element-Canvas mit Drag, Resize oder DOM-Properties.
- Framework-Komponentenexport aus dem DESIGN-Step.
- Export alter oder inaktiver Varianten.

## Datenmodell

Das produktive Workbench-Modell ist bewusst klein:

```kotlin
@Serializable
data class DesignWorkbench(
    val projectId: String,
    val description: String? = null,
    val imageInput: DesignImageInput? = null,
    val analysis: DesignAnalysis? = null,
    val currentDesign: GeneratedDesign? = null,
    val updatedAt: String,
)

@Serializable
data class DesignImageInput(
    val originalName: String,
    val contentRef: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: String,
)

@Serializable
data class DesignAnalysis(
    val summary: String,
    val visualDirection: String,
    val rationale: String,
)

@Serializable
data class GeneratedDesign(
    val id: String,
    val title: String,
    val htmlPath: String,
    val rationale: String,
    val createdAt: String,
)
```

Entfernt wurden die alten Workbench-Typen `DesignInput`, `DesignScreen`, `DesignVariant`, `DesignSuggestion` sowie die dazugehoerigen Kompatibilitaetsmethoden.

## Backend

### API

Alle aktuellen Endpunkte liegen unter `/api/v1/projects/{projectId}/design`.

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/workbench` | Aktuellen V1-Workbench-Zustand laden |
| `PUT` | `/input` | Beschreibung und/oder Bild als Multipart-Input speichern |
| `POST` | `/image/analyze` | Gespeichertes Bild strukturiert analysieren |
| `POST` | `/generate` | Agent aus bestehender Eingabe ausfuehren und aktives Design ersetzen |
| `GET` | `/preview` | Aktives HTML-Design mit sicheren Preview-Headern ausliefern |
| `POST` | `/complete` | DESIGN abschliessen und Wizard fortsetzen |

`PUT /input` lehnt leere Eingaben ab. Wenn eine Eingabe gespeichert wird, werden `analysis` und `currentDesign` zurueckgesetzt, weil das bisherige Ergebnis nicht mehr zur neuen Eingabe passt.

`POST /design/image/analyze` verlangt ein gespeichertes Bild und schreibt strukturierte Analysefelder wie Palette, Typografie, Layout-Hierarchie, Komponenten, Mood-Tags, Brand-Signale und `designBrief` in die Workbench.

`POST /design/generate` verlangt mindestens Beschreibung oder Bild. Wenn ein Bild vorhanden ist und noch keine Bildanalyse existiert, wird sie automatisch erstellt. Das generierte HTML wird validiert, bevor es als `currentDesign` gespeichert wird.

`POST /complete` verlangt ein `currentDesign`, schreibt die Design-Zusammenfassung nach `design.md`, speichert die Wizard-Step-Daten und advanced den Wizard.

### Storage

`DesignWorkbenchStorage` speichert:

- `projects/{projectId}/design/workbench.json`
- `projects/{projectId}/design/input/reference-image`
- `projects/{projectId}/design/current/index.html`
- `projects/{projectId}/design/screens/design/index.html` als aktive Export-/Handoff-Ausgabe nach Completion

Es gibt keine Storage-API mehr fuer alte Inputs, Screens, Varianten oder aktive Varianten.

### Agent

`DesignImageAnalysisAgent` nutzt:

- gespeicherte Bildbytes aus `DesignImageInput`
- Agent-ID `design-image-analysis`
- Prompt `backend/src/main/resources/prompts/design-image-analysis-system.md`
- strukturierte Ausgabe als `DesignImageAnalysis`

`DesignVariantAgent` nutzt:

- `DesignGenerationInput(projectId, description, image, imageAnalysis)`
- `DesignGenerationResult(analysis, title, html, rationale)`
- Agent-ID `design-variant`
- Prompt `backend/src/main/resources/prompts/design-variant-system.md`
- Modell-Tier `MEDIUM`

Der produktive Pfad ruft `KoogAgentRunner` mit dem editierbaren Systemprompt auf. Die Antwort muss ein JSON-Objekt mit `analysis`, `title`, `html` und `rationale` sein. Wenn kein Runner verfuegbar ist oder Parsing fehlschlaegt, erzeugt der Agent ein deterministisches Fallback-HTML. Vorhandene Bildanalyse wird in den Prompt aufgenommen und fuer die HTML-Generierung genutzt.

### Sicherheit

Generated HTML ist untrusted. Vor dem Speichern prueft `DesignPreviewValidator` unter anderem:

- keine externen URLs,
- keine protocol-relative URLs,
- keine Netzwerk-APIs,
- kein Browser-Storage,
- keine Cookies,
- kein Parent-/Opener-Zugriff,
- keine unsicheren Form-Actions.

Die Preview wird mit `text/html`, `nosniff`, `no-store` und restriktiver CSP ausgeliefert. Das Frontend rendert sie in einem sandboxed iframe.

## Frontend

Die sichtbare UI besteht aus zwei Zonen:

- links `DesignInputPanel`
  - Beschreibung
  - Bildreferenz
  - `Design generieren` / `Neu generieren`
  - `Design uebernehmen`
  - kompakte Bildanalyse-Karten nach erfolgreicher Analyse
  - Retry-Aktion bei fehlgeschlagener Bildanalyse
  - kompakte Analyse nach erfolgreicher Generierung
- rechts `DesignCanvasPreview`
  - Empty State vor der Generierung
  - Loading Overlay waehrend Arbeit laeuft
  - iframe aus `/design/preview?v={currentDesign.id}`

Der Zustand liegt in `frontend/src/lib/stores/design-workbench-store.ts` und bietet nur noch:

- `load`
- `saveInput`
- `analyzeImage`
- `generate`
- `complete`
- `reset`

Die frueheren Controls fuer Screens, Varianten, Suggestions, Snippets und Klassifikationen sind nicht Teil von V1.

## Export Und Handoff

Nach erfolgreichem Abschluss kopiert `complete` das aktuelle HTML in den aktiven Screen-Pfad. Export und Handoff lesen anschliessend nur diese aktive Ausgabe:

```text
design/screens/design/index.html
```

Stale oder alte Screen-Dateien werden nicht exportiert. Das Handoff nutzt weiterhin `backend/src/main/resources/templates/handoff/agent-template.md.mustache`.

## Akzeptanzkriterien

1. Der normale DESIGN-Step zeigt keine ZIP-Dropzone.
2. Nutzer koennen Beschreibung-only speichern und daraus ein Design generieren.
3. Nutzer koennen Bild-only speichern und daraus ein Design generieren.
4. Nutzer koennen Beschreibung plus Bild speichern und daraus ein Design generieren.
5. Leere Eingaben werden blockiert.
6. Nicht-Bilder und Bilder ueber 5 MB werden blockiert.
7. `POST /design/image/analyze` erzeugt strukturierte Bildanalyse fuer gespeicherte Bilder.
8. `POST /design/generate` ergaenzt fehlende Bildanalyse automatisch.
9. `POST /design/generate` erzeugt `analysis` und `currentDesign`.
10. Regeneration ersetzt das aktive Design.
11. Unsicheres HTML wird nicht gespeichert.
12. Die Preview rendert nur das aktuelle `currentDesign`.
13. `Design uebernehmen` ist ohne `currentDesign` nicht moeglich.
14. Completion schreibt `design.md` und aktive HTML-Ausgabe.
15. Export/Handoff enthalten nur `design/screens/design/index.html`.
16. Alte Workbench-Kompatibilitaet existiert nicht mehr im produktiven Domain-/Service-/Storage-Code.
17. Backend-Tests decken Storage, Service, API, Agent, Preview-Security, Export und Handoff ab.
18. Frontend-Build und relevante Lint-Pruefungen laufen fuer die V1-UI.

## Verifikation

Backend:

```bash
cd backend
./gradlew test
```

Frontend:

```bash
cd frontend
npm run lint
npm run build
```

Gezielte Legacy-Pruefung:

```bash
rg -n "LEGACY_DESIGN_COMPAT_MESSAGE|GeneratedDesignVariant|DesignInput\\b|DesignScreen\\b|DesignSuggestion\\b|ReferenceAnalysisAgent" backend/src/main backend/src/test
```

Die Suche darf keine produktiven Legacy-Treffer liefern.

## Bekannte Folgearbeit

- Stabilerer strukturierter Parser fuer Agentenantworten mit klarer Fehlerdiagnose.
- Preview-UX fuer verschiedene Viewport-Groessen.
- Optional spaeter: Variantenhistorie als bewusst neues Feature, nicht als Legacy-Kompatibilitaet.
- Optional spaeter: Design-System-Ableitung aus dem generierten HTML.
