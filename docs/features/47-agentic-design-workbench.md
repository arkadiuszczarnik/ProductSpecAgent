# Feature 47 - Agentic Design Workbench

**Phase:** Wizard / Design
**Abhaengig von:** Feature 11 (Guided Wizard Forms), Feature 12 (Dynamische Wizard-Steps), Feature 13 (Wizard-Chat Integration), Feature 40 (Design-Bundle-Step), Feature 46 (Wizard-Options-Admin)
**Aufwand:** XL
**Status:** Spec erstellt, Implementierung ausstehend
**Design-Spec:** [`docs/superpowers/specs/2026-05-09-agentic-design-workbench-design.md`](../superpowers/specs/2026-05-09-agentic-design-workbench-design.md)

## Problem

Der heutige DESIGN-Step ist auf den Import eines Claude-Design-ZIP-Bundles ausgelegt. Das ist nuetzlich, wenn der Product Owner bereits ein fertiges externes Design-Artefakt hat. Fuer den eigentlichen Produktfluss ist es aber der falsche Einstieg: Der Wizard soll aus Produktbeschreibung, vorhandenen Referenzen, Bildern und kuratierten Vorschlaegen selbst zu belastbaren HTML-Designvorlagen fuehren.

Der ZIP-Upload macht den DESIGN-Step zu einem Importformular. Gewuenscht ist stattdessen eine agentische Design-Workbench, die aehnlich wie Claude Artifacts/Design ueber Beschreibung, Iteration, Varianten und Live-Vorschau arbeitet.

## Ziel

Der DESIGN-Step fuer Kategorien mit Frontend wird vollstaendig ersetzt:

- kein ZIP-Upload mehr in der normalen UI,
- ein Preview-first Canvas rendert echte self-contained HTML/CSS-Vorlagen,
- Nutzer geben Textbeschreibungen, Bilder/Screenshots und HTML/CSS-Referenzen ein,
- Agenten analysieren Referenzen, schlagen Screen-Typen vor und erzeugen Varianten,
- Nutzer kuratieren Screens und setzen pro Screen eine aktive Variante,
- Abschluss schreibt `spec/design.md` plus aktive HTML/CSS-Screen-Dateien ins Projekt.

DESIGN wird fuer `SaaS`, `Mobile App` und `Desktop App` verpflichtend. `CLI Tool`, `Library` und `API` zeigen den Step weiterhin nicht.

## Scope

### In Scope

- Neue Domain-Begriffe und Modelle: `DesignWorkbench`, `DesignInput`, `DesignScreen`, `DesignVariant`.
- Bestehende `DesignBundle*`-UI wird aus dem normalen DESIGN-Step entfernt.
- Neue dreigeteilte Workbench-UI:
  - links Inputs, Referenzanalyse und Screen-Vorschlaege,
  - Mitte HTML-Canvas/Preview,
  - rechts Controls, Agent-Vorschlaege und Varianten.
- Inputs:
  - Textbeschreibung,
  - Bilder/Screenshots,
  - HTML/CSS-Referenz-Snippets.
- Reference-Analysis-Schritt:
  - Agent klassifiziert Bilder/Snippets als Referenz, Asset, HTML/CSS-Referenz oder unklar,
  - Nutzer kann Klassifikation, Namen und Nutzung korrigieren.
- Screen-Proposal-Schritt:
  - Agent schlaegt passende Screen-Typen aus Wizard-Kontext und Referenzen vor,
  - Nutzer kann Screens hinzufuegen, entfernen und umbenennen.
- Variant-Generation-Schritt:
  - Agent erzeugt self-contained HTML/CSS pro Screen,
  - kleines Inline-JS ist erlaubt fuer UI-Demos wie Tabs oder Accordions,
  - externe URLs, Netzwerk-Requests, Fremd-Scripts und Projekt-API-Zugriffe sind verboten.
- Varianten:
  - einfache Variantenliste pro Screen,
  - eine aktive Variante pro Screen,
  - Agent-Vorschlaege erzeugen direkt neue Varianten, die der Nutzer vergleichen und aktiv setzen kann.
- Abschlusskriterium:
  - mindestens ein aktiver Screen mit gueltiger HTML/CSS-Vorlage.
- Abschluss-Output:
  - `spec/design.md`,
  - `design/screens/{screen}/index.html`,
  - `design/assets/...` nur fuer freigegebene Assetbilder.

### Out of Scope

- Freier Figma-artiger Element-Canvas mit Drag, Resize und Properties pro DOM-Element.
- React/TSX-/Framework-Komponentenexport aus dem DESIGN-Step.
- Vollstaendiges Design-System mit Tokens und Komponentenbibliothek als Pflicht-Output.
- Vollstaendige Prompt-/Diff-Historie aller Varianten.
- Alte/verworfene Varianten im Export/Handoff.
- Direkte Ausfuehrung vom Nutzer eingefuegter HTML/CSS-Snippets ohne Agent-Neuschreibung und Validierung.
- Externe Asset-URLs oder Runtime-Netzwerkzugriffe aus generierten Vorlagen.

## Architektur

V1 nutzt einen Hybrid-Pipeline-Ansatz:

1. `ReferenceAnalysisAgent`
   - analysiert Bilder/Screenshots und HTML/CSS-Snippets,
   - erstellt sichtbare, korrigierbare Analyse-Karten.
2. `ScreenProposalAgent`
   - schlaegt Screen-Typen anhand Wizard-Kontext, Analyse und Nutzerbeschreibung vor.
3. `DesignVariantAgent`
   - erzeugt initiale HTML/CSS-Varianten,
   - wendet Agent-Vorschlaege an, indem neue Varianten erzeugt werden.
4. Completion-Logik
   - validiert mindestens einen aktiven Screen,
   - schreibt `spec/design.md` und aktive Screen-Dateien,
   - advanced den Wizard zum naechsten Step.

Die bestehende `DesignBundle*`-Implementierung kann intern vorerst bestehen bleiben, ist aber nicht mehr die fachliche Hauptdomain fuer den DESIGN-Step. Neue API, Storage und UI sollen die Workbench-Begriffe verwenden.

## Datenmodell

```kotlin
@Serializable
data class DesignWorkbench(
    val projectId: String,
    val inputs: List<DesignInput>,
    val screens: List<DesignScreen>,
    val updatedAt: String,
)

@Serializable
data class DesignInput(
    val id: String,
    val kind: DesignInputKind,
    val originalName: String?,
    val userLabel: String?,
    val classification: DesignInputClassification?,
    val contentRef: String,
    val createdAt: String,
)

@Serializable
data class DesignScreen(
    val id: String,
    val name: String,
    val purpose: String,
    val variants: List<DesignVariant>,
    val activeVariantId: String?,
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
```

## API-Skizze

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/api/v1/projects/{id}/design/workbench` | Workbench-Zustand laden |
| `POST` | `/api/v1/projects/{id}/design/inputs` | Text, Bild oder Snippet hinzufuegen |
| `POST` | `/api/v1/projects/{id}/design/analyze` | Inputs analysieren und klassifizieren |
| `PATCH` | `/api/v1/projects/{id}/design/inputs/{inputId}` | Klassifikation/Label korrigieren |
| `POST` | `/api/v1/projects/{id}/design/screens/propose` | Screen-Vorschlaege erzeugen |
| `POST` | `/api/v1/projects/{id}/design/screens` | Screen manuell anlegen |
| `PATCH` | `/api/v1/projects/{id}/design/screens/{screenId}` | Screen umbenennen/aktivieren/deaktivieren |
| `POST` | `/api/v1/projects/{id}/design/screens/{screenId}/variants` | Variante erzeugen |
| `POST` | `/api/v1/projects/{id}/design/screens/{screenId}/suggestions/{suggestionId}/apply` | Vorschlag als neue Variante anwenden |
| `PATCH` | `/api/v1/projects/{id}/design/screens/{screenId}/active-variant` | Aktive Variante setzen |
| `GET` | `/api/v1/projects/{id}/design/preview/{variantId}` | HTML-Preview-Datei ausliefern |
| `POST` | `/api/v1/projects/{id}/design/complete` | DESIGN abschliessen |

## Akzeptanzkriterien

1. In `SaaS`, `Mobile App` und `Desktop App` zeigt DESIGN keine ZIP-Dropzone mehr, sondern die Workbench.
2. In `CLI Tool`, `Library` und `API` bleibt DESIGN unsichtbar.
3. Der Step kann ohne aktiven Screen nicht abgeschlossen werden.
4. Nutzer koennen Text, Bilder/Screenshots und HTML/CSS-Snippets als Inputs hinzufuegen.
5. `ReferenceAnalysisAgent` klassifiziert Inputs sichtbar; Nutzer koennen Klassifikationen korrigieren.
6. `ScreenProposalAgent` schlaegt Screen-Typen vor; Nutzer koennen Screens kuratieren.
7. `DesignVariantAgent` erzeugt pro Screen self-contained HTML/CSS-Varianten.
8. Die Preview rendert Varianten in einem sandboxed Iframe mit strenger CSP.
9. Validierung blockiert externe URLs, Netzwerk-Requests, Fremd-Scripts und Projekt-API-Zugriffe.
10. Kleines Inline-JS fuer rein lokale UI-Demos ist erlaubt.
11. Agent-Vorschlaege erzeugen neue Varianten; Nutzer koennen Varianten vergleichen und aktiv setzen.
12. Abschluss schreibt `spec/design.md` und aktive HTML/CSS-Dateien unter `design/screens/...`.
13. Nur aktive Varianten und freigegebene Assetbilder landen im Export/Handoff.
14. Bestehende Projekt-Exporte bleiben lesbar; alte `DesignBundle*`-Daten fuehren nicht zu Crashs.
15. Backend-Tests decken Storage, Input-Validierung, Agent-Fallbacks, Preview-Security und Complete-Gating ab.
16. Frontend prueft Empty-, Loading-, Error-, Analyze-, Generate-, Variant- und Complete-Zustaende.

## Verifikation

Backend:

```bash
cd backend
./gradlew test --tests "*.DesignWorkbench*"
./gradlew test --tests "*.DesignInput*"
./gradlew test --tests "*.DesignVariant*"
./gradlew test
```

Frontend:

```bash
cd frontend
npm run lint
npm run build
```

Manuell:

1. Neues SaaS-Projekt erstellen und bis DESIGN gehen.
2. Sicherstellen, dass keine ZIP-Dropzone mehr sichtbar ist.
3. Textbeschreibung eingeben, Screenshot und HTML/CSS-Referenz hinzufuegen.
4. Analyse ausfuehren, Klassifikation korrigieren.
5. Screen-Vorschlaege erzeugen, mindestens einen Screen auswaehlen.
6. Variante generieren, im Canvas ansehen.
7. Agent-Vorschlag anwenden, neue Variante aktiv setzen.
8. DESIGN abschliessen.
9. Pruefen, dass `spec/design.md` und `design/screens/.../index.html` existieren.
10. ARCHITECTURE/FRONTEND bekommen den Design-Kontext.

## Offene Punkte fuer Implementierungsplanung

- Ob alte `DesignBundle*`-Endpoints direkt deprecated oder nur aus der UI entfernt werden.
- Ob Snippet-Inputs als Rohtext gespeichert oder vorab normalisiert werden.
- Ob Preview-HTML serverseitig bei jedem Speichern validiert oder zusaetzlich beim Ausliefern geprueft wird.
- Welche maximale Groesse fuer Bilder, Snippets und generierte HTML-Dateien gilt.
