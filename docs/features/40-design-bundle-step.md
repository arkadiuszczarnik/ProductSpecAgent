# Feature 40 — Design-Bundle-Step

**Datum:** 2026-05-03
**Branch:** `feat/design-bundle-step`
**Spec:** [`docs/superpowers/specs/2026-05-03-design-bundle-step-design.md`](../superpowers/specs/2026-05-03-design-bundle-step-design.md)
**Plan:** _wird in der nächsten Phase via `superpowers:writing-plans` erstellt_
**Beispiel-Bundle:** [`examples/Scheduler.zip`](../../examples/Scheduler.zip)

## Problem

Product Owner bringen häufig bereits ein visuelles Design-Konzept mit
(typischerweise als ZIP-Export aus Claude Design — eine self-contained
React-App mit Canvas, View-JSX-Dateien, Tokens und optionalen
Komponenten-Doku-Markdowns). Bisher hat der Wizard keinen Step, der dieses
Konzept aufnimmt. Folge: ARCHITECTURE-/BACKEND-/FRONTEND-Specs werden ohne
Kenntnis der visuellen Vorgaben generiert, und der PO sieht im Workspace
nicht, welche Design-Pages er beigegeben hat.

## Ziel

Ein neuer optionaler Wizard-Step **DESIGN**, der nach `MVP` und vor
`ARCHITECTURE` erscheint — aber nur in Categories mit FRONTEND-Step
(SaaS, Mobile App, Desktop App). Der Step:

1. nimmt **ein** Claude-Design-ZIP entgegen (Single-Slot, replaceable),
2. zeigt den Inhalt im Browser des PO als **Iframe-Live-Preview** der
   Original-Canvas, flankiert von einer **Page-Liste** (geparst aus
   `DCArtboard`-Tags der Entry-HTML),
3. erzeugt beim Step-Complete via neuem `DesignSummaryAgent` ein
   strukturiertes `spec/design.md`, das automatisch in alle Folge-Steps
   in den Agent-Kontext fließt.

Der Step ist *überspringbar* — ohne Bundle wird kein Agent gerufen, der
Flow läuft trotzdem weiter, mit Hinweis dass FRONTEND/BACKEND-Output
weniger konkret wird.

## Scope

### In Scope

- Neuer `FlowStepType.DESIGN` mit lazy Migration für bestehende Projekte
  (Strategy: in `FlowStateStorage.load` fehlende Enum-Werte als `OPEN`
  ergänzen, idempotent).
- Backend `DesignBundleController` mit Endpoints für Upload, Get, Delete,
  File-Serving (`/files/{path:.+}`) und Step-Complete.
- `DesignBundleStorage` als dedizierte Klasse (nicht in `UploadStorage`
  einhängen — andere Kardinalität, Multi-File-Struktur).
- `DesignBundleExtractor` mit harter Validation: max 5 MB ZIP, max 10 MB
  entpackt, max 500 Files, max 5 MB pro File, Pfad-Traversal-Schutz,
  Symlink-Reject, Zip-Bomb-Defense.
- `DesignSummaryAgent` (Modell-Tier MEDIUM) mit Marker-Escape und
  Output-Validation, Fallback auf Page-Liste-only bei Agent-Fehler.
- Frontend-Step `DesignForm` mit Empty-State-Dropzone, Bundle-Header,
  Page-Liste, Iframe-Preview (`sandbox="allow-scripts allow-same-origin"`)
  und Replace-Confirm-Dialog.
- File-Serving mit `X-Content-Type-Options: nosniff`, CSP-Header,
  Content-Type-Whitelist nach Extension.
- Dedizierter Zustand-Store `useDesignBundleStore` (analog
  `useDocumentStore`).
- Erweiterungen in `category-step-config.ts`, `WIZARD_STEPS`, `StepType`
  und `step-field-labels.ts`.
- Backend-Tests: Extractor, Storage, Controller, Agent, FlowState-Migration.
- Manueller Frontend-Smoke-Test (kein Test-Runner im Frontend).

### Out of Scope

- Versionierung / Historie alter Bundles (Single-Slot).
- Iframe-Per-Page mit Thumbnails (Live-Canvas reicht).
- Manuelles Edit der `design.md`.
- Bundle-Sharing / separater Export (Bundle wird via Project-Export
  mitgenommen).
- Multi-Bundle pro Projekt.
- Live-Re-Render bei File-Änderungen (Replace = vollständiger Re-Upload).
- Strukturierte Design-Token-Extraktion.
- Bundle-Diff alt vs. neu.
- Page-Thumbnails via Headless-Browser.
- Custom Page-Naming durch User (Labels kommen aus dem Bundle).
- `FeatureProposalAgent` zieht Bundle (FeatureProposal läuft *vor*
  DESIGN-Step).

## Akzeptanzkriterien

| # | Kriterium |
|---|---|
| 1 | `FlowStepType.DESIGN` existiert; in SaaS/Mobile/Desktop sichtbar zwischen MVP und ARCHITECTURE. CLI/Library/API zeigen den Step nicht |
| 2 | `POST /api/v1/projects/{id}/design/upload` mit `examples/Scheduler.zip` antwortet mit `DesignBundle` inkl. 5 erkannten Pages und `entryHtml = "Scheduler.html"` |
| 3 | ZIP > 5 MB → 413 mit verständlicher Message. Zip-Bombe → 400 mit Abbruch, keine Files persistiert |
| 4 | Pfad-Traversal im ZIP → 400, keine Files. Pfad-Traversal in `GET /design/files/...` → 400 |
| 5 | `GET /design/files/Scheduler.html` mit korrektem Content-Type, `nosniff`-Header, CSP-Header. Relative Asset-Pfade resolven gegen `/design/files/` |
| 6 | Frontend-DESIGN-Step zeigt Empty-State-Dropzone. Upload ersetzt UI durch Bundle-Header + Pages-Liste + Iframe |
| 7 | Iframe rendert `Scheduler.html` mit React/Babel und zeigt alle 5 Artboards. Sandbox-Flags wie spezifiziert |
| 8 | Pages-Liste zeigt 5 Pages mit Section-Title, Label, WxH-Badge. Click navigiert Iframe (Hash oder Scroll-Fallback) |
| 9 | "Ersetzen" öffnet Confirm-Dialog. Bestätigung + neuer Upload überschreibt Bundle vollständig |
| 10 | "Step abschließen" mit Bundle: `DesignSummaryAgent` läuft, `spec/design.md` wird geschrieben, FlowState → ARCHITECTURE |
| 11 | "Step überspringen" ohne Bundle: kein Agent-Call, keine `design.md`, FlowState advanced. Chat zeigt System-Hinweis |
| 12 | Folge-Step ARCHITECTURE bekommt `design.md` automatisch via `SpecContextBuilder` |
| 13 | Marker-Phrasen im Bundle-Inhalt werden im Prompt mit Zero-Width-Space neutralisiert. Output-Validation entfernt versehentliche Marker |
| 14 | `npm run lint` / `npm run build` (frontend) + `./gradlew test` (backend) grün. Lint-Baseline (15 Errors) unverändert |

## Verifikation

### Backend

```bash
cd backend
./gradlew test --tests "*.DesignBundleExtractorTest"
./gradlew test --tests "*.DesignBundleStorageTest"
./gradlew test --tests "*.DesignBundleControllerTest"
./gradlew test --tests "*.DesignSummaryAgentTest"
./gradlew test --tests "*.FlowStateStorageMigrationTest"
./gradlew test    # full suite green
```

### Frontend

```bash
cd frontend
npm run lint   # baseline of 15 errors unchanged
npm run build  # SUCCESSFUL
```

### Manuell (Browser)

1. `./start.sh`
2. Neues SaaS-Projekt anlegen, Wizard bis MVP durchklicken, MVP abschließen.
3. DESIGN-Step erscheint zwischen MVP und ARCHITECTURE.
4. `examples/Scheduler.zip` per Drag-Drop hochladen → Bundle-Header
   ("Scheduler.zip · 436 KB · 5 Pages") + Pages-Liste mit allen 5 Views +
   Iframe rendert die Canvas mit allen Artboards.
5. Click auf eine Page in der Liste → Iframe navigiert zum Artboard.
6. "Step abschließen" → Chat zeigt User-Msg + Agent-Antwort, Flow advanced
   auf ARCHITECTURE, `data/projects/{id}/spec/design.md` existiert mit
   Pages- und Komponenten-Sektion.
7. Replace-Flow: erneut hochladen → Confirm-Dialog → Iframe re-mount.
8. Neues CLI-Projekt: DESIGN-Step nicht in der Step-Liste sichtbar.
9. Skip-Flow: SaaS-Projekt ohne Bundle, "Überspringen" → Flow advanced,
   kein `design.md`.
10. Edge: kaputtes ZIP (txt umbenannt) → User-friendly Error-Toast.

## Konfiguration

Neue `@ConfigurationProperties("design.bundle")` mit Env-Var-Override
(Pattern aus Feature 35):

| Property | Default | Env-Var |
|---|---|---|
| `maxZipBytes` | 5 MB | `DESIGN_BUNDLE_MAX_ZIP_BYTES` |
| `maxExtractedBytes` | 10 MB | `DESIGN_BUNDLE_MAX_EXTRACTED_BYTES` |
| `maxFiles` | 500 | `DESIGN_BUNDLE_MAX_FILES` |
| `maxFileBytes` | 5 MB | `DESIGN_BUNDLE_MAX_FILE_BYTES` |
| `summaryMaxFileBytes` | 50 KB | `DESIGN_BUNDLE_SUMMARY_MAX_FILE_BYTES` |
| `summaryMaxTotalBytes` | 200 KB | `DESIGN_BUNDLE_SUMMARY_MAX_TOTAL_BYTES` |
| `summaryMaxJsxFiles` | 5 | `DESIGN_BUNDLE_SUMMARY_MAX_JSX_FILES` |

## Berührung anderer Subsysteme

| Subsystem | Änderung |
|---|---|
| **Project-Export (`ExportService`)** | Bundle-Files in Project-ZIP-Export unter `design/files/...` plus `design/manifest.json` |
| **Docs-Scaffold (`DocsScaffoldGenerator`)** | Optional: neue Mustache-Template `docs/design/overview.md.mustache` rendert Pages-Liste in `docs/design/` |
| **Handoff (`HandoffService`)** | Bundle-Files-Inventar im Handoff-Output |
| **`SpecContextBuilder.buildContext`** | Keine Code-Änderung — `design.md` wird automatisch geladen, weil zu COMPLETED-Step |
| **Frontend `AppShell` / Workspace-Page** | `loadDesignBundle(id)` in `useEffect`-Kette, `reset()` im Cleanup |
