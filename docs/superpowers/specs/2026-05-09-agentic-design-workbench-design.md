# Agentic Design Workbench - Design Spec

**Datum:** 2026-05-09
**Bezug:** Feature 47. Ersetzt den ZIP-zentrierten DESIGN-Step durch eine agentische Workbench fuer HTML/CSS-Screen-Vorlagen.
**Status:** Design - Review ausstehend

## Kontext

Der aktuelle DESIGN-Step stammt aus Feature 40. Er nimmt ein Claude-Design-ZIP entgegen, extrahiert Pages, zeigt eine Iframe-Vorschau und erzeugt beim Abschluss eine `spec/design.md`.

Das ist technisch isoliert und funktioniert fuer Import-Szenarien. Fuer den Produktfluss ist es aber zu passiv: Product Owner sollen nicht zuerst ein externes ZIP haben muessen. Der Wizard soll aus Beschreibung, Referenzmaterial und Agent-Vorschlaegen selbst zu brauchbaren Designvorlagen fuehren.

Das neue Feature ersetzt die normale DESIGN-Step-UX vollstaendig. Der alte ZIP-Begriff verschwindet fachlich aus dem Step.

## Ziele

- DESIGN wird eine agentische Design-Workbench.
- Der Canvas rendert echte self-contained HTML/CSS-Vorlagen.
- Nutzer koennen Text, Bilder/Screenshots und HTML/CSS-Snippets als Referenzen einbringen.
- Agenten analysieren Referenzen, schlagen Screens vor, erzeugen Varianten und machen Verbesserungsangebote.
- Nutzer behalten Kontrolle: Klassifikationen, Screens und aktive Varianten sind editierbar.
- Abschluss erzeugt belastbare Design-Artefakte fuer Folge-Steps:
  - `spec/design.md`
  - `design/screens/{screen}/index.html`
  - freigegebene lokale Assets unter `design/assets/...`

## Nicht-Ziele

- Kein Figma-Klon.
- Keine freie DOM-/Canvas-Elementbearbeitung mit Drag/Resize in V1.
- Kein React/TSX-Export aus DESIGN.
- Kein vollstaendiges Design-System als Pflicht-Output.
- Keine externe URL-Ausfuehrung in generierten Vorlagen.
- Keine Speicherung aller verworfenen Varianten im Export/Handoff.
- Keine direkte Ausfuehrung von Nutzer-Snippets ohne Validierung oder Agent-Neuschreibung.

## Produktentscheidung

Die Workbench ist ein **Preview-first Hybrid**:

- Der Canvas ist eine Live-Preview, keine freie Zeichenflaeche.
- Controls beeinflussen Stil, Dichte, Zielgeraet und Generierungsrichtung.
- Agent-Vorschlaege erzeugen neue Varianten statt unkontrolliert den aktiven Screen zu veraendern.
- Nutzer waehlen pro Screen aktiv aus, welche Variante relevant ist.

DESIGN ist fuer Kategorien mit Frontend verpflichtend:

- `SaaS`
- `Mobile App`
- `Desktop App`

Fuer `CLI Tool`, `Library` und `API` bleibt DESIGN unsichtbar.

Minimales Abschlusskriterium: mindestens ein aktiver Screen mit gueltiger HTML/CSS-Vorlage.

## Recherche-Notizen

Context7 zu Next.js bestaetigt das vorhandene Muster: interaktive Editor-UI gehoert in Client Components (`"use client"`), Browser-APIs wie Canvas/Iframe werden erst clientseitig verwendet, Uploads koennen mit `FormData` und `fetch` laufen.

Context7 zu React bestaetigt fuer die Editor-UI zwei relevante Regeln: Refs sind fuer DOM-/Iframe-Zugriff geeignet, aber nicht waehrend des Renderns zu lesen/schreiben; abgeleitete Werte sollen bevorzugt waehrend Render berechnet werden statt ueber unnoetige Effects synchronisiert zu werden.

Context7 zu Konva bestaetigt, dass Konva fuer interaktive 2D-Design-Editoren geeignet waere. Wir verwenden es fuer V1 bewusst nicht als Kern, weil die gewaehlte Richtung kein freier Canvas-Editor ist. Falls spaeter ein echter Element-Canvas kommt, ist Konva oder React-Konva ein naheliegender Kandidat.

Anthropic-Dokumentation und Help-Center beschreiben Artifacts als interaktive Apps, die aus Gespraechen entstehen und iterativ angepasst werden koennen. Claude Vision kann Bilder analysieren; Computer-use zeigt ausserdem, dass screenshotbasierte Analyse eigene Sicherheitsrisiken hat. Daraus leiten wir ab: Bildanalyse ist sichtbar und korrigierbar, und generierte Vorlagen laufen sandboxed.

Quellen:

- Next.js App Router Docs via Context7: `/vercel/next.js`
- React Docs via Context7: `/reactjs/react.dev`
- Konva Docs via Context7: `/konvajs/site`
- Anthropic Artifacts Help: https://support.anthropic.com/articles/11649427-use-artifacts-to-visualize-and-create-ai-apps-without-ever-writing-a-line-of-code
- Anthropic Vision Docs: https://docs.anthropic.com/en/docs/build-with-claude/vision
- Anthropic Computer Use Docs: https://docs.anthropic.com/en/docs/build-with-claude/computer-use

## UX-Design

### Layout

Dreigeteilte Workbench:

```text
+----------------------+---------------------------+----------------------+
| Inputs / Analysis    | HTML Canvas Preview       | Controls / Variants  |
| Screen Vorschlaege   | Aktiver Screen            | Agent Vorschlaege    |
+----------------------+---------------------------+----------------------+
```

### Linke Spalte

Aufgaben:

- Textbeschreibung erfassen.
- Bilder/Screenshots hinzufuegen.
- HTML/CSS-Referenz-Snippets hinzufuegen.
- Analyse-Karten anzeigen:
  - Referenzbild
  - Assetbild
  - HTML/CSS-Referenz
  - unklar
- Screen-Vorschlaege anzeigen und kuratieren.

### Mitte

Aufgaben:

- Aktive Variante als gerenderte HTML-Preview anzeigen.
- Desktop/Mobile-Preview umschalten.
- Lade-, Fehler- und leere Zustaende anzeigen.
- Keine direkte freie Elementbearbeitung in V1.

### Rechte Spalte

Aufgaben:

- Controls fuer Generierung:
  - Zielgeraet
  - Stilrichtung
  - Farbstimmung
  - UI-Dichte
  - Fokus, z. B. Landing, Dashboard, Detail, Empty State
- Agent-Vorschlaege anzeigen.
- Klick auf Vorschlag erzeugt neue Variante.
- Variantenliste anzeigen.
- Aktive Variante setzen.

## Workflow

### 1. Inputs erfassen

Der Nutzer kann mehrere Input-Typen erfassen:

- Freitextbeschreibung
- Bild/Screenshot
- HTML/CSS-Snippet

Jeder Input wird als `DesignInput` gespeichert. Bilder werden lokal persistiert; Snippets werden als Text gespeichert und nicht direkt ausgefuehrt.

### 2. Referenzen analysieren

`ReferenceAnalysisAgent` erstellt fuer jeden Input eine Analyse:

- Klassifikation
- Kurzbeschreibung
- Relevante visuelle Merkmale
- Empfehlung, ob das Material als Referenz oder Asset genutzt werden soll
- Rueckfrage, wenn unklar

Der Nutzer kann Analyse und Klassifikation korrigieren.

### 3. Screens vorschlagen

`ScreenProposalAgent` nutzt:

- Wizard-Daten aus IDEA/PROBLEM/FEATURES/MVP,
- Referenzanalyse,
- Nutzerbeschreibung,
- Produktkategorie.

Er schlaegt 2-5 Screen-Typen vor. Der Nutzer kann sie annehmen, entfernen, umbenennen oder manuell ergaenzen.

### 4. Varianten erzeugen

`DesignVariantAgent` erzeugt pro Screen self-contained HTML/CSS.

Erlaubt:

- statisches HTML,
- CSS im Dokument oder als lokale Datei,
- kleines Inline-JS fuer lokale Demo-Interaktionen.

Verboten:

- externe Script-/Style-/Image-URLs,
- `fetch`, XHR, WebSocket,
- Projekt-API-Zugriffe,
- Zugriff auf Cookies, Storage oder Parent Window,
- Form-Submits an externe Ziele.

### 5. Vorschlaege anwenden

Agent-Vorschlaege sind keine Text-TODOs. Sie erzeugen direkt neue Varianten.

Beispiel:

- Vorschlag: "CTA-Hierarchie staerken"
- Aktion: neue Variante `Landing v3`
- Nutzer vergleicht `v2` und `v3`
- Nutzer setzt `v3` aktiv oder verwirft sie

### 6. DESIGN abschliessen

Complete ist nur moeglich, wenn mindestens ein Screen eine aktive, gueltige Variante hat.

Beim Abschluss:

- aktive Varianten werden nach `design/screens/{slug}/index.html` geschrieben,
- freigegebene Assets werden nach `design/assets/...` geschrieben,
- `spec/design.md` wird erzeugt,
- Wizard advanced zum naechsten Step.

## Backend-Design

### Domain

Neue Datei:

```text
backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt
```

Kernmodelle:

```kotlin
@Serializable
data class DesignWorkbench(
    val projectId: String,
    val inputs: List<DesignInput> = emptyList(),
    val screens: List<DesignScreen> = emptyList(),
    val updatedAt: String,
)

@Serializable
data class DesignInput(
    val id: String,
    val kind: DesignInputKind,
    val originalName: String? = null,
    val userLabel: String? = null,
    val classification: DesignInputClassification? = null,
    val contentRef: String,
    val createdAt: String,
)

@Serializable
enum class DesignInputKind {
    TEXT,
    IMAGE,
    HTML_CSS_SNIPPET,
}

@Serializable
data class DesignInputClassification(
    val category: DesignInputCategory,
    val summary: String,
    val suggestedUse: String,
    val confidence: Double,
)

@Serializable
enum class DesignInputCategory {
    REFERENCE_IMAGE,
    ASSET_IMAGE,
    HTML_CSS_REFERENCE,
    UNCLEAR,
}

@Serializable
data class DesignScreen(
    val id: String,
    val name: String,
    val purpose: String,
    val variants: List<DesignVariant> = emptyList(),
    val activeVariantId: String? = null,
)

@Serializable
data class DesignVariant(
    val id: String,
    val screenId: String,
    val version: Int,
    val title: String,
    val htmlPath: String,
    val status: DesignVariantStatus,
    val rationale: String,
    val createdAt: String,
)

@Serializable
enum class DesignVariantStatus {
    DRAFT,
    VALID,
    INVALID,
}
```

### Storage

Neue Klasse:

```text
backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt
```

Persistenzstruktur:

```text
projects/{projectId}/design/workbench.json
projects/{projectId}/design/inputs/{inputId}/content
projects/{projectId}/design/inputs/{inputId}/metadata.json
projects/{projectId}/design/variants/{screenId}/{variantId}.html
projects/{projectId}/design/assets/{assetId}/{filename}
projects/{projectId}/design/screens/{screenSlug}/index.html
```

`design/screens/...` ist Abschluss-Output. `design/variants/...` ist interner Workbench-Zustand.

### Services

Neue Services:

- `DesignWorkbenchService`
  - Orchestriert Workbench-Operationen.
  - Validiert Abschlussgating.
  - Schreibt aktive Outputs.
- `DesignPreviewValidator`
  - prueft HTML auf verbotene URLs, Scripts, APIs und Form-Targets.
  - markiert Varianten als `VALID` oder `INVALID`.
- `DesignPreviewRenderer`
  - liefert Preview-Dateien mit CSP, `nosniff`, `no-store`.

### Agents

#### ReferenceAnalysisAgent

Input:

- Liste nicht analysierter `DesignInput`s,
- Wizard-Kontext,
- ggf. Bilddaten oder Bildbeschreibungen.

Output:

- strukturierte Klassifikationen.

Fallback:

- Textinputs bleiben als Referenz.
- Bilder werden als `REFERENCE_IMAGE` mit niedriger Confidence markiert.

#### ScreenProposalAgent

Input:

- Wizard-Kontext,
- Input-Analysen,
- existierende Screens.

Output:

- Screen-Vorschlaege mit Name, Zweck und Begruendung.

Fallback:

- Fuer SaaS: Landing, Dashboard.
- Fuer Mobile: Onboarding, Home.
- Fuer Desktop: Main Window, Settings.

#### DesignVariantAgent

Modi:

- `GENERATE_INITIAL`
- `APPLY_SUGGESTION`

Input:

- Screen,
- aktive Variante optional,
- Controls,
- relevante Inputs/Analysen,
- Wizard-Kontext.

Output:

- HTML-Dokument,
- Rationale,
- Vorschlaege fuer Folgevarianten.

Fallback:

- Fehler wird im UI sichtbar; bestehende aktive Variante bleibt unveraendert.

### API

Neue Controller-Klasse:

```text
backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt
```

Endpoints:

```text
GET    /api/v1/projects/{projectId}/design/workbench
POST   /api/v1/projects/{projectId}/design/inputs
POST   /api/v1/projects/{projectId}/design/analyze
PATCH  /api/v1/projects/{projectId}/design/inputs/{inputId}
POST   /api/v1/projects/{projectId}/design/screens/propose
POST   /api/v1/projects/{projectId}/design/screens
PATCH  /api/v1/projects/{projectId}/design/screens/{screenId}
DELETE /api/v1/projects/{projectId}/design/screens/{screenId}
POST   /api/v1/projects/{projectId}/design/screens/{screenId}/variants
PATCH  /api/v1/projects/{projectId}/design/screens/{screenId}/active-variant
GET    /api/v1/projects/{projectId}/design/preview/{variantId}
POST   /api/v1/projects/{projectId}/design/complete
```

### Security

Preview response headers:

- `Content-Security-Policy`
- `X-Content-Type-Options: nosniff`
- `Cache-Control: no-store`

Iframe:

- sandboxed,
- no same-origin unless required for local asset resolution,
- no top navigation,
- no form submission.

Validation rejects:

- `http://`, `https://`, protocol-relative URLs,
- `<script src=...>`,
- `fetch(`, `XMLHttpRequest`, `WebSocket`, `EventSource`,
- `localStorage`, `sessionStorage`, `document.cookie`,
- `window.parent`, `postMessage`,
- form actions.

If inline JS is retained, it must be limited to local UI state and pass validator checks.

## Frontend-Design

### Store

Neue Datei:

```text
frontend/src/lib/stores/design-workbench-store.ts
```

State:

- `workbench`
- `selectedScreenId`
- `selectedVariantId`
- `loading`
- `analyzing`
- `proposingScreens`
- `generatingVariant`
- `completing`
- `error`

Actions:

- `load(projectId)`
- `addTextInput(projectId, text)`
- `uploadImageInput(projectId, file)`
- `addSnippetInput(projectId, snippet)`
- `analyzeInputs(projectId)`
- `updateInput(projectId, inputId, patch)`
- `proposeScreens(projectId)`
- `addScreen(projectId, payload)`
- `updateScreen(projectId, screenId, patch)`
- `deleteScreen(projectId, screenId)`
- `generateVariant(projectId, screenId, controls)`
- `applySuggestion(projectId, screenId, suggestionId)`
- `setActiveVariant(projectId, screenId, variantId)`
- `complete(projectId)`

### Komponenten

Neue Komponenten unter:

```text
frontend/src/components/wizard/steps/design-workbench/
```

Komponenten:

- `DesignWorkbenchForm`
- `DesignInputPanel`
- `DesignInputCard`
- `DesignReferenceAnalysisList`
- `DesignScreenProposalList`
- `DesignCanvasPreview`
- `DesignControlPanel`
- `DesignSuggestionList`
- `DesignVariantList`
- `DesignCompleteBar`

Bestehendes `DesignForm` wird entweder ersetzt oder als duenner Wrapper belassen, der die neue Workbench rendert.

### Empty States

Startzustand:

- Textfeld fuer Produkt-/Designbeschreibung,
- Upload fuer Bilder,
- Snippet-Feld fuer HTML/CSS-Referenzen,
- Button "Referenzen analysieren".

Nach Analyse:

- Karten mit Klassifikation und Korrekturmoeglichkeit,
- Button "Screens vorschlagen".

Nach Screen-Auswahl:

- Button "Variante erzeugen".

Nach erster Variante:

- Canvas sichtbar,
- Controls und Vorschlaege aktiv,
- Complete-Bar aktiv, sobald Variante gueltig und aktiv ist.

## Export und Handoff

Project-Export soll enthalten:

```text
spec/design.md
design/screens/{screen}/index.html
design/assets/...
```

Handoff soll aktive Screen-Dateien nennen oder einschliessen, damit Coding Agents nicht nur Textbeschreibung, sondern konkrete HTML-Vorlagen sehen.

Interne Workbench-Dateien wie verworfene Varianten muessen nicht exportiert werden.

## Migration

Bestehende Projekte mit altem Design-Bundle:

- duerfen nicht crashen,
- koennen beim Oeffnen des neuen DESIGN-Steps einen Hinweis erhalten, dass der alte Bundle-Import ersetzt wurde,
- muessen fuer V1 nicht automatisch migriert werden.

Bestehende `DesignBundle*`-Backendklassen koennen in einer ersten Implementierung parallel existieren. Neue Produktpfade nutzen aber `DesignWorkbench*`.

## Tests

### Backend

- `DesignWorkbenchStorageTest`
  - Workbench lesen/schreiben,
  - Inputs speichern,
  - Varianten speichern,
  - aktive Outputs schreiben.
- `DesignPreviewValidatorTest`
  - gueltiges HTML/CSS akzeptiert,
  - externe URLs rejected,
  - Netzwerk-APIs rejected,
  - gefaehrliche Browser APIs rejected,
  - erlaubtes lokales Inline-JS akzeptiert.
- `DesignWorkbenchControllerTest`
  - Workbench laden,
  - Input hinzufuegen,
  - Analyse starten,
  - Screen vorschlagen,
  - Variante erzeugen,
  - aktive Variante setzen,
  - Complete ohne aktive Variante rejected,
  - Complete mit aktiver Variante schreibt Output.
- Agent-Tests mit Stub-Agenten:
  - ReferenceAnalysis fallback,
  - ScreenProposal fallback,
  - VariantGeneration invalid-output handling.

### Frontend

- Store-Unit-Tests, falls vorhandenes Testsetup passt.
- Mindestens Build/Lint-Validation.
- Manuelle Browser-Verifikation:
  - Empty State,
  - Analyse,
  - Screen-Vorschlaege,
  - Variante,
  - Vorschlag anwenden,
  - aktive Variante setzen,
  - Complete.

## Akzeptanzkriterien

1. DESIGN-Step zeigt keine ZIP-Dropzone mehr.
2. Workbench-Layout nutzt linke Input-/Screen-Spalte, mittleren Canvas und rechte Control-/Variantenspalte.
3. Text, Bilder und HTML/CSS-Snippets koennen als Inputs erfasst werden.
4. Inputs werden agentisch analysiert und im UI korrigierbar angezeigt.
5. Screens werden agentisch vorgeschlagen und vom Nutzer kuratiert.
6. Varianten werden als self-contained HTML/CSS erzeugt.
7. Preview rendert aktive Variante in einem sandboxed Iframe.
8. Externe URLs, Netzwerkzugriffe und gefaehrliche Browser APIs werden blockiert.
9. Agent-Vorschlaege erzeugen neue Varianten.
10. Nutzer kann pro Screen eine aktive Variante setzen.
11. Complete ist ohne aktive gueltige Variante blockiert.
12. Complete schreibt `spec/design.md` und aktive HTML/CSS-Dateien.
13. Export/Handoff enthalten aktive Design-Artefakte, nicht alle verworfenen Varianten.
14. CLI/Library/API-Kategorien zeigen DESIGN weiterhin nicht.

## Offene Punkte fuer den Implementierungsplan

- Bestehende `DesignBundleController`-Endpoints deaktivieren, deprecaten oder nur aus UI entfernen.
- Maximalgroessen fuer Bilder, Snippets und HTML-Dateien festlegen.
- Genauer CSP-Satz fuer Preview mit lokalen Assets.
- Ob aktive Screens direkt bei Variant-Aktivierung nach `design/screens/...` gespiegelt werden oder erst bei Complete.
- Ob `spec/design.md` vom `DesignVariantAgent` oder einem separaten Summary-Schritt erzeugt wird.
