# Feature 40 — Design-Bundle-Step — Done

**Datum:** 2026-05-04
**Branch:** `feat/design-bundle-step` (gemerged via Merge-Commit `7104843`, plus Post-Merge-Fixes auf `main`)
**Spec:** `docs/superpowers/specs/2026-05-03-design-bundle-step-design.md`
**Plan:** `docs/superpowers/plans/2026-05-03-design-bundle-step.md`
**Feature-Doc:** `docs/features/40-design-bundle-step.md`
**Beispiel-Bundle:** `examples/Scheduler.zip` (436 KB, 14 Files, 5 Pages)

## Was umgesetzt wurde

**Backend**

- Neuer `FlowStepType.DESIGN` zwischen `MVP` und `ARCHITECTURE`. `FlowStateStorage.load` macht eine idempotente Lazy-Migration: fehlende Enum-Werte in bestehenden `flow-state.json`-Dateien werden als `OPEN` ergänzt, ohne den `updatedAt`-Timestamp zu touchen, wenn nichts zu migrieren ist (`FlowStateStorageMigrationTest` deckt Fixture-Roundtrip + Idempotenz ab).
- Domain-Modelle: `DesignBundle`, `DesignPage`, `DesignBundleFile` (kotlinx.serialization).
- `DesignBundleProperties` mit `@ConfigurationProperties("design.bundle")`: 7 Limits (zip-bytes, extracted-bytes, file-count, per-file-bytes, summary-cap × 3) jeweils mit Env-Var-Override (siehe Feature-Doc Tabelle).
- `DesignBundleExtractor`: ZIP-Validation in Defense-in-Depth-Reihenfolge — Größencheck zuerst, dann Entry-für-Entry Pfad-Traversal-Reject, Symlink-Reject, Zip-Bomb-Schutz (kumulative entpackte Bytes vs. `maxExtractedBytes`), Per-File-Cap, `_MACOSX/`-Filter. Entry-HTML-Erkennung mit Fallback-Heuristik bei mehreren HTMLs. Page-Parsing über `DCArtboard`-Tag-Extraction aus Entry-HTML.
- `DesignBundleStorage`: persistiert `bundle.zip`, `manifest.json`, `files/...` unter `data/projects/{id}/design/`. Atomares Replace via temp-dir + rename. `readFile` resolved relative Pfade gegen `files/`-Root und rejected Traversal noch einmal redundant.
- `DesignSummaryAgent` (Tier `MEDIUM`, registriert in `AgentRegistry`): liest aus dem Bundle bis zu N JSX-Files (Cap aus `summaryMax*Bytes` + `summaryMaxJsxFiles`) und generiert `spec/design.md` mit Pages- und Komponenten-Sektion. Marker-Phrasen (`[STEP_COMPLETE]` etc.) werden im Prompt mit Zero-Width-Space neutralisiert; Output-Validation entfernt versehentlich generierte Marker. Bei Agent-Failure: Fallback auf Page-Liste-only (kein Crash).
- `DesignBundleController` unter `/api/v1/projects/{id}/design`: `POST /upload` (multipart, 413 bei Limit-Überschreitung), `GET /` (Bundle-Metadata), `DELETE /`, `GET /files/{path:.+}` (mit URL-Decode vor Traversal-Check, `X-Content-Type-Options: nosniff`, CSP-Header, Content-Type-Whitelist), `POST /complete` (führt `DesignSummaryAgent` aus, schreibt `design.md`, advance Flow → ARCHITECTURE; ohne Bundle: Skip-Pfad ohne Agent-Call).
- `ExportService`: nimmt Bundle-Files unter `design/files/...` plus `design/manifest.json` ins Project-ZIP-Export.
- Bugfix nebenher: `ProjectService.advanceStep` wirft jetzt `ProjectNotFoundException` statt NPE, wenn `FlowState` fehlt.

**Frontend**

- `lib/api.ts`: Types und Wrapper-Functions (`uploadDesignBundle`, `getDesignBundle`, `deleteDesignBundle`, `completeDesignStep`).
- `useDesignBundleStore` (Zustand) analog `useDocumentStore`: `bundle`, `loading`, `error`, plus `loadBundle / uploadBundle / deleteBundle / reset`. Wird in der Workspace-Page in der `useEffect`-Kette geladen, im Cleanup zurückgesetzt.
- `category-step-config.ts` + `WIZARD_STEPS` + `step-field-labels.ts`: DESIGN sichtbar in `SaaS`, `MobileApp`, `DesktopApp`, ausgeblendet in `CLI`, `Library`, `API`.
- 6 neue UI-Komponenten unter `frontend/src/components/wizard/steps/design/`:
  - `DesignDropzone` (Empty-State mit Drag-and-Drop + „Überspringen"-Button)
  - `DesignBundleHeader` (Filename, Size, Page-Count, Replace + Remove)
  - `DesignPagesList` (Sidebar mit Section-Title, Label, WxH-Badge; Click navigiert per Hash)
  - `DesignIframePreview` (`sandbox="allow-scripts allow-same-origin"`, 5s-Loading-Fallback; restrukturiert um setState-in-effect zu vermeiden)
  - `DesignReplaceConfirmDialog` (shadcn `Dialog` für Replace-Bestätigung)
  - `DesignForm` (Assembly: Header + List + Iframe oder Dropzone, plus „Weiter"-Button für Step-Complete)
- `wizard-store`: `completeStep`-Branch für DESIGN ruft `/design/complete` und schreibt `wizardData.steps.DESIGN.completedAt`, damit `StepIndicator` den Checkmark zeigt.
- `WizardForm` blendet seinen eigenen Bottom-Nav-Next-Button auf dem DESIGN-Step aus (DESIGN bringt seinen eigenen Weiter-Button mit).

## Bewusste Abweichungen

- **Originaler Plan-Schritt zur Frontend-Layout-Höhe wurde reverted.** Commit `f1ba23a` ("DESIGN step uses full available height") wurde mit `bf3f1ac` rückgängig gemacht, weil der Höhen-Override mit anderen Wizard-Steps kollidierte. Aktuelle Lösung: DESIGN nutzt volle Breite (`54037ae`, max-w-2xl-Cap aufgehoben), aber nicht volle Höhe.
- **Post-Merge-Fix-Commit `2e62887`** (auf `main`, nach dem Merge) musste zwei Parity-Probleme nachreichen, die im ursprünglichen Plan fehlten: (1) `wizardData.steps.DESIGN.completedAt` wird jetzt nach `/complete` gesetzt, sonst blieb der Step-Indicator-Checkmark aus, (2) Button-Label in `DesignForm` von „Step abschließen" auf „Weiter" + `ArrowRight`-Icon angepasst. Dropzone behält „Überspringen", weil semantisch unterschiedlich.
- **`fdef9cc` (`include design bundle in project export`)** wurde als separater Commit erst nach der Haupt-Implementierung nachgezogen — der Plan hatte das als Punkt im selben Schritt vorgesehen. Funktional identisch zum Plan.
- **`4c2294a` (`align frontend-origin default to 3000`)** ist eigentlich kein Feature-40-Code, sondern fix für ein über die Iframe-Tests aufgefallenes CORS-Mismatch (Frontend-dev läuft auf 3000, Backend-Default war 3001). Wurde der Einfachheit halber im selben Branch mitgenommen.
- **`SpecContextBuilder` brauchte keine Code-Änderung** — `design.md` wird automatisch mitgeladen, weil sie in `spec/` liegt und der DESIGN-Step beim Lesen schon `COMPLETED` ist (wie im Spec § 6 vorhergesagt).
- **`HandoffService` und `DocsScaffoldGenerator`-Mustache-Template** aus der Feature-Doc-Tabelle „Berührung anderer Subsysteme" wurden **nicht** umgesetzt. Beides war als optional/„kann nachträglich" markiert; Bundle ist im Project-Export enthalten, das deckt den Haupt-Use-Case ab.

## Nicht umgesetzt (bewusst out-of-scope)

Wie in Feature-Doc § "Out of Scope" dokumentiert: Versionierung, Iframe-per-Page, manuelles Edit der `design.md`, Bundle-Sharing, Multi-Bundle, Live-Re-Render, Token-Extraktion, Bundle-Diff, Page-Thumbnails, Custom-Naming, FeatureProposal-Bundle-Integration.

Zusätzlich nicht umgesetzt:
- Optionales `docs/design/overview.md.mustache`-Template (Feature-Doc Tabelle).
- Handoff-Bundle-Inventar (Feature-Doc Tabelle).

## Akzeptanzkriterien-Status

| # | Kriterium | Status |
|---|---|---|
| 1 | DESIGN-Step zwischen MVP und ARCHITECTURE in SaaS/Mobile/Desktop, nicht in CLI/Library/API | ✅ |
| 2 | `POST /design/upload` mit `examples/Scheduler.zip` → 5 Pages + `entryHtml = "Scheduler.html"` | ✅ (`DesignBundleControllerTest`) |
| 3 | ZIP > 5 MB → 413; Zip-Bombe → 400 ohne persistierte Files | ✅ |
| 4 | Pfad-Traversal in Upload und in `GET /design/files/...` → 400 | ✅ |
| 5 | File-Serving mit Content-Type, `nosniff`, CSP, relative Asset-Pfade | ✅ |
| 6 | Empty-State-Dropzone, Upload zeigt Header + Pages-Liste + Iframe | ✅ |
| 7 | Iframe rendert Scheduler.html mit allen 5 Artboards, Sandbox-Flags wie spec | ✅ |
| 8 | Pages-Liste mit Section-Title, Label, WxH-Badge; Click navigiert | ✅ |
| 9 | „Ersetzen" → Confirm-Dialog → Re-Upload überschreibt | ✅ |
| 10 | „Step abschließen" mit Bundle: Agent läuft, `design.md` wird geschrieben, Flow → ARCHITECTURE | ✅ |
| 11 | „Step überspringen" ohne Bundle: kein Agent, kein `design.md`, Flow advanced | ✅ |
| 12 | ARCHITECTURE bekommt `design.md` automatisch | ✅ (`SpecContextBuilder` unverändert) |
| 13 | Marker-Phrasen neutralisiert + Output-Validation | ✅ (`DesignSummaryAgentTest`) |
| 14 | `npm run lint` / `npm run build` / `./gradlew test` grün, Lint-Baseline unverändert | ✅ |

## Follow-up-Kandidaten

- `WizardForm`-Bottom-Nav-Conditional auf DESIGN-Step ist ein Stempel — wenn weitere Steps eigene Weiter-Buttons mitbringen, sollte das in eine deklarative Step-Capability migriert werden.
- Iframe-Höhe: aktuell statisch, weil der Full-Height-Versuch reverted wurde. Robustere Lösung wäre ein ResizeObserver am Outer-Container.
- Bundle-Inventar im Handoff (Feature-Doc Tabelle) wäre eine kleine Ergänzung mit hohem Wert für die Agent-Ready-Übergabe.
- Optionales `docs/design/overview.md.mustache` würde die Pages-Liste in der Auto-Doku spiegeln (Pattern aus anderen Mustache-Sections).
- Feature-Proposal-Agent zieht weiterhin keinen Bundle-Kontext, weil er vor DESIGN läuft. Re-Run von FeatureProposal nach DESIGN wäre eine separate Feature-Idee.
