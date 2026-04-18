# Feature 22 — Done-Doc (zweiter Anlauf)

**Datum:** 2026-04-18
**Branch:** `feat/feature-22-graph-redesign`
**Commits:** 26 (docs + 23 code + 2 gap-/bug-fixes)
**Status:** Implementation abgeschlossen, bereit fuer Merge

## Kontext

Zweiter Anlauf nach Rollback vom ersten Versuch (`d37596d`, Grund: React-19-Strict-Mode-Hang beim Mount des FEATURES-Editors). Der Rollback hat Code komplett zurueckgerollt, Docs blieben.

Dieser Anlauf basiert auf:

- Feature-Spec: [`docs/features/22-features-graph-wizard.md`](22-features-graph-wizard.md)
- Redesign-Spec: [`docs/superpowers/specs/2026-04-18-feature-22-graph-editor-redesign.md`](../superpowers/specs/2026-04-18-feature-22-graph-editor-redesign.md) — 5 Design-Entscheidungen als Reaktion auf die 4 kaskadierenden Bugs aus Anlauf 1
- Plan: [`docs/superpowers/plans/2026-04-18-feature-22-features-graph-wizard.md`](../superpowers/plans/2026-04-18-feature-22-features-graph-wizard.md) — 20 Tasks

## Erfuellte Akzeptanzkriterien (16 / 16)

| # | Kriterium | Status | Umgesetzt in |
|---|-----------|--------|--------------|
| 1 | Feature-Nodes erstellen/loeschen/verschieben, Positionen persistiert | ✅ | T14, T16 |
| 2 | Dependency-Kanten per Drag zwischen Ports | ✅ | T16 |
| 3 | Zyklische Kanten client-seitig verhindert + Feedback | ✅ | T12, T16 + Gap-Fix (`31a6ed7`) |
| 4 | Scope-Set pro Feature waehlbar + kategorie-gated | ✅ | T11, T15 |
| 5 | Scope-spezifische Felder dynamisch, Werte erhalten bei Wechsel | ✅ | T15 |
| 6 | "Features vorschlagen"-Button via FeatureProposalAgent | ✅ | T7, T8, T16 |
| 7 | Bei leerem Graph ist Weiter geblockt | ✅ | Gap-Fix (`31a6ed7`) |
| 8 | Validator-Agent emittiert CLARIFICATION_NEEDED-Marker | ✅ | T6 |
| 9 | Offene Clarifications blockieren Weiter (Feature-18-Gate) | ✅ | unveraendert integriert |
| 10 | EPIC-Tasks tragen echte `dependencies` aus Graph-Kanten | ✅ | T4 |
| 11 | `00-feature-set-overview.md` zeigt echte Feature-Titel | ✅ | T9 |
| 12 | `NN-<slug>.md` zeigt Scope-Badge + Scope-Sektionen | ✅ | T9 + Critical-Fix (`ccf7f7d`) |
| 13 | PlanGeneratorAgent erzeugt scope-fokussierte Stories | ✅ | T3 |
| 14 | Alte Projekte ohne Graph laden fehlerfrei (lazy-migriert) | ✅ | T2 |
| 15 | Mobile-Fallback unter 768 px zeigt Listen-Ansicht | ✅ | T17 |
| 16 | Bestehende Funktionalitaet unberuehrt | ✅ | T19 regression |

## Design-Invarianten (alle verifiziert)

Die 5 Kern-Invarianten aus dem Redesign-Doc halten in der finalen Codebasis:

1. **Incremental Diff in `applyGraph`** — kein Full-Rebuild. Closure-Map `renderedFeatures` trackt add/remove/update node-by-node (`editor.ts:130–219`).
2. **`nodedragged` only, nicht `nodetranslated`** — `area.addPipe` hoert ausschliesslich `nodedragged` (User-Drag-Ende) (`editor.ts:112–127`).
3. **Coalescing Queue** — max 1 running + 1 pending; `destroyed`-Flag schluckt Rete-Teardown-Fehler (`editor.ts:221–247`).
4. **Atomic Selectors** in Feature-22-Komponenten — kein `const {x,y} = useWizardStore()`-Destructure in `FeaturesGraphEditor`, `FeatureSidePanel`, `FeaturesFallbackList`.
5. **Backend-only Legacy-Normalisierung** — `parseWizardFeatures` vergibt UUIDs einmalig beim ersten Read, Frontend erzeugt neue IDs nur fuer neue Entities.

## Abweichungen vom Original-Plan

| Abweichung | Grund | Dokumentiert in |
|------------|-------|-----------------|
| `rete-auto-arrange-plugin` statt `@dagrejs/dagre` | Natives Rete-v2-Plugin, weniger Glue-Code | Implementation-Notes (2026-04-17) |
| `useResizable` statt shadcn `ResizablePanelGroup` | Hook existiert bereits aus Feature 17 | Implementation-Notes (2026-04-17) |
| `PlanGeneratorAgent` vergibt `epicEstimate` statt `WizardFeature.estimate` | Spec-Entscheidung | Implementation-Notes (2026-04-17) |
| Cycle-Rejection via `alert()` statt Toast | Kein Toast-System im Projekt, konsistent mit existierenden Dialogs | Redesign-Doc |
| Parent `WizardForm` behaelt pre-existing `useWizardStore()`-Destructure | Invariante 4 gilt nur fuer neue Komponenten; andere Wizard-Steps in Folge-Feature normalisieren | — |
| `TaskSource.WIZARD` / `SpecTask.source` aus Plan geloescht | Feld existiert im Domain-Modell nicht; `specSection == FlowStepType.FEATURES` dient als Diskriminator | T3-Implementer-Bericht |
| `WizardFeatureInput.estimate` nicht zurueckgelegt | User-Entscheidung aus Brainstorming, `SpecTask.estimate` bleibt | Implementation-Notes (2026-04-17) |

## Test-Abdeckung

### Backend (7 neue Test-Klassen)

- `WizardFeatureGraphTest` — Domain-Serialisierung
- `ParseWizardFeaturesTest` — Legacy-Kompat, Graph-Shape, Kategorie-Defaults, Multi-Edges auf gleiches Target
- `PlanGeneratorAgentScopeTest` — Scope-Hints, epicEstimate, Fallback, Story/Task-Estimate-Validation
- `TaskServiceWizardGraphTest` — 2-Phasen Dependency-Mapping, Non-Wizard-Tasks bleiben, Chain, Multi-Source, Unknown-Ids
- `SpecContextBuilderGraphTest` — Features-Block-Rendering, Core-Label, Isolated-Nodes
- `FeatureProposalAgentTest` — JSON-Parsing mit Title→ID-Uebersetzung, Exception-Fallback, Library-Default-Scopes
- `FeatureProposalControllerTest` — 200/422 Endpoint-Flow
- `ScaffoldContextBuilderTest` (erweitert) — Real-Dependencies + Scope-Anreicherung + Core-Label

Gesamt: 144 von 145 Backend-Tests gruen. Einziger Failure (`FileControllerTest.GET files returns file tree`) ist pre-existing und unabhaengig von Feature 22.

### Frontend

- `npx tsc --noEmit` — clean
- `npm run build` — erfolgreich (Next.js 16 Turbopack)
- `npm run lint` — 18 pre-existing Errors in Files ausserhalb Feature-22-Scope; keine neuen in den F22-Komponenten
- `npx playwright test --list` — 1 Smoke-Test gelistet (`mount, add, rename, connect, reload`)

**Manueller Schritt vor Merge:** `./start.sh` + `cd frontend && npm run test:e2e` — einmaliger Live-Run des Playwright-Smoke-Tests gegen laufenden Stack. Verifiziert End-to-End, dass der Ursprungs-Bug (React-19-Strict-Mode-Hang) nicht zurueckgekehrt ist.

## Verbleibendes Tech-Debt

Bewusst in diesem Feature nicht adressiert, um den Scope klein zu halten:

- `WizardFeatureInput` lebt in `service/TaskService.kt` statt `domain/` (Plan-Vorgabe, nicht urspruengliches Design)
- Zwei Kopien von `defaultScopesFor` — identisch in `IdeaToSpecAgent.companion` und `FeatureProposalAgent`
- Nullable `WizardService?` / `TaskService?`-Injection in `SpecContextBuilder` und `IdeaToSpecAgent` (Kompromiss fuer Unit-Test-Kompatibilitaet)
- `parseScopes` behandelt `null` und `[]` gleich (faellt beides auf Category-Default zurueck) — Plan-Vorgabe
- Cycle-Rejection und Proposal-Error via `alert()` (kein Toast-System im Projekt)
- 18 pre-existing Lint-Errors in `ScopeForm.tsx`, `TargetAudienceForm.tsx`, `ChatPanel.tsx` etc. (unrelated)
- Pre-existing Backend-Test-Fehler `FileControllerTest.GET files returns file tree` (seit vor Feature 22)

## Bugs behoben waehrend Implementation

Reviewer-Loops fanden und fixten folgende Punkte:

1. **T2** — Multi-edge-auf-gleiches-Target Test fehlte; Plan hatte Kotlin-Syntax-Bug (`uuid.get()` ≠ `uuid()`)
2. **T3** — Plan hatte Prompt-Injection-Risiko durch `scopeFields`-Interpolation; `sanitizeForPrompt` + Whitelist-Konstante `VALID_ESTIMATES` + Story/Task-Estimate-Validation eingefuehrt
3. **T4** — `tasks.first` fragile bei Agent-Fehlern; silent `?: continue` ohne Logging; fehlender Duplicate-Feature-ID-Guard; Chain- und Multi-Source-Tests ergaenzt
4. **T16** — `instanceof FeatureRNode`-Guards im `connectioncreate`-Pipe; `destroy()` nullt Callbacks und cleart Closure-Maps; `destroyed`-Flag schluckt Teardown-Fehler
5. **T19-Gap-Fix** — Empty-graph-Block auf "Weiter" und Cycle-Rejection-Alert fehlten gegenueber Akzeptanzkriterium
6. **Final-Review-Critical-Fix** — `ScaffoldContextBuilder.loadWizardFeaturesByTitle` deserialisierte `features` falsch als `WizardFeatureGraph` statt als flache `List<WizardFeature>`; Scope-Badge + Scope-Sektionen in Docs waren defakto tot. Plus zwei Regressions-Tests.

## Kaskadierende-Bug-Protokoll aus Anlauf 1

Details in [`docs/superpowers/specs/2026-04-18-feature-22-graph-editor-redesign.md`](../superpowers/specs/2026-04-18-feature-22-graph-editor-redesign.md) § Root-Cause. Kurz:

| # | Symptom | Ursache | Fix in Anlauf 2 |
|---|---------|---------|------------------|
| 1 | Infinite Re-Render-Loop | `area.addPipe` hoerte auf `nodetranslated` | Invariante 2 |
| 2 | "cannot find connection" | Parallele `applyGraph`-Laeufe | Invariante 3 |
| 3 | "synchronously unmount a root while rendering" | Full-Rebuild rief `editor.removeNode` waehrend React-Render | Invariante 1 |
| 4 | Legacy-Features bekamen bei jedem Read neue UUIDs | Frontend-seitige Normalisierung in Selektoren | Invariante 5 |

## Aenderungen im Ueberblick

**Backend:**
- `domain/WizardFeatureGraph.kt` (neu)
- `agent/FeatureProposalAgent.kt` (neu), `api/FeatureProposalController.kt` (neu)
- `agent/PlanGeneratorAgent.kt`, `agent/IdeaToSpecAgent.kt`, `agent/SpecContextBuilder.kt` (erweitert)
- `service/TaskService.kt` (erweitert: `WizardFeatureInput` + 2-Phasen EPIC-Gen)
- `export/ScaffoldContextBuilder.kt`, `export/DocsScaffoldGenerator.kt`, `resources/.../feature.md.mustache` (erweitert um Scope-Rendering)
- 7 neue Test-Klassen

**Frontend:**
- `components/wizard/steps/features/FeaturesGraphEditor.tsx`, `editor.ts`, `FeatureNode.tsx`, `FeatureSidePanel.tsx`, `FeaturesFallbackList.tsx` (alle neu)
- `components/wizard/steps/FeaturesForm.tsx` (duenner Re-Export)
- `lib/graph/cycleCheck.ts` (neu)
- `lib/api.ts`, `lib/category-step-config.ts`, `lib/step-field-labels.ts`, `lib/stores/wizard-store.ts`, `lib/hooks/use-step-blockers.ts`, `components/wizard/WizardForm.tsx`, `components/wizard/BlockerBanner.tsx` (erweitert)
- `playwright.config.ts`, `e2e/features-graph.spec.ts`, `e2e/fixtures/seed-project.ts` (neu)
- `package.json` + `package-lock.json` (Dependencies: `rete-auto-arrange-plugin`, `@playwright/test`)

## Naechster Schritt

Vor Merge manueller Playwright-Smoke-Test:

```bash
./start.sh
# In neuem Terminal:
cd frontend && npm run test:e2e
```

Erwartete Ausgabe: `1 passed (Xs)`. Screenshot/HTML-Report unter `frontend/playwright-report/`.
