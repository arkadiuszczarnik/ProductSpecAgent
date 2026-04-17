# Feature 22 — Features Graph Wizard (Done)

## Status

Implementiert am 2026-04-17 auf Branch `feat/22-features-graph-wizard` als Nachfolger von Feature 21 (Wizard-Features → EPIC Tasks). Base-Branch: `main` (Commit `3ebc401`). 18 Implementierungs-Commits + 3 Docs-Commits.

## Zusammenfassung

Der FEATURES-Wizard-Step ist jetzt ein Rete.js-basierter DAG-Editor. Pro Feature werden Scope-Flags (`FRONTEND` / `BACKEND`, plus Library-Spezialfall mit leerem Set) erfasst, scope-spezifische Felder (UI-Komponenten, Screens, API-Endpunkte usw.) dynamisch ein-/ausgeblendet und Abhängigkeiten durch Dragging zwischen Ports modelliert. Ein neuer `FeatureProposalAgent` schlägt die initiale Feature-Liste aus den vorgelagerten Idea/Problem/Scope/MVP-Specs vor. Der bestehende `IdeaToSpecAgent` validiert den Graph und emittiert bei isolierten Nodes, Scope-Inkonsistenzen oder fehlenden Standard-Features `[CLARIFICATION_NEEDED]`-Marker. Die DAG-Kanten fliessen jetzt als echte `SpecTask.dependencies` in den EPIC-Task-Tree und ersetzen das bisher naive `"Feature N-1"`-Rendering im Docs-Scaffold.

## Architektur-Aenderungen (erledigt)

### Backend

| Datei                                                                                               | Aenderung                                                          |
|-----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `domain/WizardFeatureGraph.kt`                                                                      | NEU — WizardFeature, WizardFeatureEdge, GraphPosition, FeatureScope |
| `agent/FeatureProposalAgent.kt`                                                                     | NEU — LLM-basierter Feature-Vorschlag, `ProposalParseException`   |
| `api/FeatureProposalController.kt`                                                                  | NEU — `POST /api/v1/projects/{id}/features/propose`               |
| `agent/PlanGeneratorAgent.kt`                                                                       | Scope-aware Prompt (`appendScopeHint`) + `epicEstimate` aus LLM   |
| `service/TaskService.kt`                                                                            | 2-Phasen EPIC-Generierung + Dependency-Mapping; Persistenz erst nach Phase 2 |
| `agent/SpecContextBuilder.kt`                                                                       | Companion `renderFeaturesBlock` + Instanz `buildProposalContext`; optionale `WizardService`-Dep |
| `agent/IdeaToSpecAgent.kt`                                                                          | `parseWizardFeatures` zu Companion; `resolveProjectCategory` Helper; `FEATURES_VALIDATOR_RULES` im User-Prompt |
| `export/ScaffoldContextBuilder.kt`                                                                  | Echte Dependencies aus `epic.dependencies` via `idToTitle`; Scope + `scopeFields` aus Wizard-State in `FeatureContext` |
| `export/DocsScaffoldGenerator.kt`                                                                   | `FeatureContext` um `scope`, `scopeFields` und 9 `hasXxx`-Booleans erweitert (Mustache-Guard) |
| `resources/templates/scaffold/docs/features/feature.md.mustache`                                    | `**Scope:**` Zeile + 9 bedingte Sektionen (UI-Komponenten, Screens, User-Interaktionen, API-Endpunkte, Datenmodell, Side-Effects, Public API, Exponierte Types, Beispiele) |

### Frontend

| Datei                                                                                               | Aenderung                                                          |
|-----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `components/wizard/steps/FeaturesForm.tsx`                                                          | Komplett ersetzt — Viewport-Switch (>=768 px → Graph, sonst Fallback) |
| `components/wizard/steps/features/FeaturesGraphEditor.tsx`                                          | NEU — Split-Pane (Rete + Detail-Panel) mit `useResizable`, Toolbar (Feature / Vorschlagen / Auto-Layout), Cycle-Warning-Banner |
| `components/wizard/steps/features/editor.ts`                                                        | NEU — Rete.js v2 Setup mit Area/Connection/React/AutoArrange Plugins; Cycle-Prevention via `editor.addPipe` |
| `components/wizard/steps/features/FeatureNode.tsx`                                                  | NEU — `FeatureRNode` + `FeatureNodeComponent` mit FE/BE/Core-Badges |
| `components/wizard/steps/features/FeatureSidePanel.tsx`                                             | NEU — scope-awarer Detail-Editor; Multi-Select fuer Scope, scope-spezifische Textareas |
| `components/wizard/steps/features/FeaturesFallbackList.tsx`                                         | NEU — Mobile-Fallback (<768 px) als Liste mit Dependency-Multi-Select |
| `lib/graph/cycleCheck.ts`                                                                           | NEU — DFS-basierte `wouldCreateCycle` |
| `lib/stores/wizard-store.ts`                                                                        | Graph-Actions: `getFeatures`, `getEdges`, `addFeature`, `updateFeature`, `removeFeature`, `addEdge`, `removeEdge`, `moveFeature`, `applyProposal`; Legacy-Normalisierung via `normalizeFeature` in `getFeatures` |
| `lib/api.ts`                                                                                        | Typen (`WizardFeature`, `WizardFeatureEdge`, `WizardFeatureGraph`, `FeatureScope`, `GraphPosition`) + `proposeFeatures()` |
| `lib/category-step-config.ts`                                                                       | `allowedScopes` pro Kategorie + `getAllowedScopes` Helper          |
| `lib/step-field-labels.ts`                                                                          | `SCOPE_FIELD_LABELS` + `SCOPE_FIELDS_BY_SCOPE`                     |
| `package.json`                                                                                      | Neue Dependency `rete-auto-arrange-plugin` (zieht `elkjs` transitiv ein) |

### Tests (neu / erweitert)

- Backend: `WizardFeatureGraphTest`, `ParseWizardFeaturesTest`, `PlanGeneratorAgentScopeTest`, `TaskServiceWizardGraphTest`, `SpecContextBuilderGraphTest`, `FeatureProposalAgentTest`, `FeatureProposalControllerTest`; Erweiterungen an `IdeaToSpecAgentTest`, `SpecContextBuilderTest`, `SpecContextBuilderWizardTest`, `ScaffoldContextBuilderTest`, `DocsScaffoldGeneratorTest`
- Frontend: keine Unit-Tests (Projekt hat bisher kein Frontend-Test-Setup); Verifikation ueber `tsc --noEmit` + `npm run lint` + `npm run build`

## Verifikation

- `./gradlew test` → **168 tests, 1 failed** — einziger Fehler ist der pre-existing `FileControllerTest.GET files returns file tree()` aus Feature 15 (nicht durch diese Aenderung ausgeloest). Alle Feature-22-Tests gruen.
- `npx tsc --noEmit` → clean
- `npm run lint` → 18 errors / 16 warnings, **identisch zur pre-Feature-22-Baseline** (alle in fremden Dateien, keine der neuen Feature-22-Dateien erzeugt Lint-Issues)
- `npm run build` → Compiled successfully in 5.7 s (Next 16.2.1 Turbopack, 6 Routen)

## Abweichungen von Plan / Spec

- **Auto-Layout:** Plan erwaehnte `@dagrejs/dagre`; implementiert wurde `rete-auto-arrange-plugin` (nutzt `elkjs`). Grund: idiomatischer fuer Rete.js v2, weniger Glue-Code. Dokumentiert in `docs/superpowers/specs/2026-04-17-feature-22-implementation-notes.md` (Entscheidung 1, bereits vor der Implementierung abgestimmt).
- **Split-Pane:** Plan erwaehnte `ResizablePanelGroup` (shadcn); das Projekt hat stattdessen den eigenen `useResizable`-Hook aus Feature 17. Letzterer wurde wiederverwendet (Entscheidung 2 der Impl-Notes).
- **`SpecContextBuilder`-Konstruktor:** `wizardService: WizardService? = null` als optionale Dep hinzugefuegt, um `buildProposalContext` die Kategorie-Aufloesung zu ermoeglichen. Default `null` bewahrt Rueckwaertskompatibilitaet aller bestehenden Test-Fixtures.
- **`estimate` in `WizardFeature`:** User-Request wahrend Brainstorming, vor Spec-Finalisierung. EPIC-Estimate wird jetzt vom LLM gesetzt (`epicEstimate`-Feld im JSON-Response), Fallback `"M"` bei Parse-Fehler. `SpecTask.estimate` bleibt `String` (Schema-stabil).
- **`scopeFields` als `Map<String, JsonElement>` im `FeatureProposalAgent`:** Defensiver gegen LLM-Quirks (Zahlen/Booleans/Objects in Values), Sanitizer konvertiert via `contentOrNull`/`toString`. Keine Scope-Aenderung gegenueber Plan.
- **Validator-Regeln im User-Prompt** (nicht System-Prompt): Plan-Praeferenz war User-Prompt; erste Implementierung legte sie im System-Prompt ab, wurde in der Review-Runde korrigiert (Commit `ae380c1`). Grund: System-Prompt bleibt step-unabhaengig, Regeln gehoeren zum step-spezifischen Kontext.
- **Mustache-Section-Conditions ueber explizite `hasXxx`-Booleans:** Statt sich auf Mustache's Map-Value-Truthy-Check zu verlassen. Robuster gegen JMustache-Version-Quirks.

## Nach-Review Polish-Fixes (waehrend Implementation eingebaut)

- **Task 2 Review:** Unsafe `as? Map<String, String>` Cast auf `scopeFields` durch sanitizing `mapNotNull` ersetzt; KDoc auf `parseWizardFeatures`; Test-Assertion per Lookup statt Index.
- **Task 5 Review:** Tests fuer `buildWizardContext` Step-Guard (FEATURES vs. andere Steps) und `buildProposalContext` (em-dash-Fallback + reale Category) ergaenzt; Dead-`else`-Branch im Scope-Label durch `error(...)` ersetzt.
- **Task 6 Review:** Validator-Regeln vom System-Prompt in den User-Prompt verschoben (s. o.); Unused-Default-Parameter entfernt.
- **Task 19 Regression:** Frontend-Legacy-Migration in `getFeatures()` (normalisiert alte `{title, description, estimate}`-Objekte lazy beim Read, persistiert beim naechsten Write); Cycle-Warning-Banner (3 s Amber-Toast ueber der Toolbar) wenn `addEdge` eine zyklische Kante ablehnt.

## Offene Punkte / Technische Schulden

1. **Kein Frontend-Test-Setup** — alle UI-Komponenten nur via `tsc`/`lint`/`build` verifiziert. Ein Vitest + React Testing Library-Setup waere vor dem naechsten grossen Frontend-Feature sinnvoll.
2. **Pre-existing `FileControllerTest.GET files returns file tree` Failure** aus Feature 15 bleibt — orthogonal zu Feature 22.
3. **Pre-existing Compiler-Warning** `Condition is always 'true'` in `IdeaToSpecAgent.kt:240` — bestand vor Feature 22 und wurde nicht eingefuehrt.
4. **Auto-Layout belegt `arrange.layout()` default preset** — fuer sehr grosse Graphen (>30 Nodes) ggf. Custom-Layouter ueberdenken. Fuer Feature-Spec-Scale (1–10 Features) aktuell irrelevant.
5. **Cycle-Warning-Banner** ist eine Inline-Message; bei sensiblen Usern (prefers-reduced-motion) koennte ein Toast-Lib (sonner) in einem folgenden UX-Pass die bessere UX liefern.
6. **Manueller Smoke-Test noch offen:** Die automatisierten Regressions-Checks sind gruen, aber ein End-to-End-Durchlauf (neues Projekt → Wizard bis FEATURES → Vorschlagen → Kanten ziehen → Zyklus versuchen → Auto-Layout → Weiter → Doc-Scaffold pruefen) steht fuer den User aus.
7. **Mobile-Fallback Mount-Flash:** Initialer Render auf Mobile zeigt fuer einen Frame den Graph-Editor (wide=true Default), bevor der useEffect umschaltet. Funktional unkritisch, kosmetisch verbesserbar (z. B. `useSyncExternalStore` oder SSR-safe Viewport-Guess).

## Manueller Smoke-Test (empfohlen)

1. Neues SaaS-Projekt anlegen, Wizard bis FEATURES durchlaufen.
2. `Vorschlagen`-Button → Agent erzeugt 2–5 Features mit Dependencies und Scopes.
3. Einen Node loeschen, einen neuen hinzufuegen, Scope auf Frontend+Backend setzen, `uiComponents` + `apiEndpoints` befuellen.
4. Eine Kante A→B ziehen, dann B→A versuchen — Amber-Banner "Zyklus verhindert" erscheint, Kante wird nicht angelegt.
5. `Auto-Layout`-Button → Nodes werden topologisch angeordnet.
6. `Weiter` klicken. Verify:
   - `docs/features/00-feature-set-overview.md` zeigt echte Feature-Titel in "Abhaengig von".
   - `docs/features/01-<slug>.md` enthaelt `**Scope:** Frontend + Backend`, die Sektionen "UI-Komponenten" und "API-Endpunkte".
7. Wizard zurueck auf FEATURES, Scope eines Features aendern, `Weiter` → idempotente EPIC-Regeneration (keine duplizierten EPICs, scope-specifische Sektionen aktualisiert).
8. Library-Projekt wiederholen → Scope-Picker unsichtbar, nur "Core"-Felder, per-feature doc `**Scope:** Core`.
9. Browser-Fenster auf <768 px verkleinern → Fallback-Liste mit Multi-Select fuer Dependencies erscheint.
