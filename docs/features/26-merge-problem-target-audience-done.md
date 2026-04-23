# Feature 26 — Merge PROBLEM and TARGET_AUDIENCE Steps (Done)

## Status
Umgesetzt am 2026-04-21 auf Branch `feat/feature-26-merge-problem-target-audience`. Subagent-Driven-Development mit Spec- und Code-Quality-Reviews nach jedem Haupt-Commit. 4 Commits (2 Haupt-Commits + 2 Review-getriebene Cleanup-Commits).

## Zusammenfassung der Implementierung

### Backend
- **`FlowStepType`-Enum** in `domain/FlowState.kt`: `TARGET_AUDIENCE` entfernt. Das Enum hat jetzt 8 Werte; `createInitialFlowState` propagiert die Reduktion automatisch, da es `FlowStepType.entries` iteriert. `stepOrder` in `IdeaToSpecAgent` tut dasselbe.
- **`IdeaToSpecAgent.buildWizardStepFeedbackPrompt`**:
  - `"TARGET_AUDIENCE" ->`-Branch komplett entfernt
  - `"PROBLEM" ->`-Branch erweitert: deckt jetzt Problem, Zielgruppe **und** Pain Points in einem kombinierten Analyse-Prompt ab. Agent wird angewiesen, eine kohärente `problem.md` mit allen drei Aspekten zu schreiben. `[DECISION_NEEDED]`-Trigger für B2B/B2C-Audience-Konflikte aus dem gelöschten Branch übernommen
  - Zwei `TARGET_AUDIENCE`-Mentions im IDEA-Step-Prompt (englisch + deutsch) entfernt
- **`application.yml`** (main + test): System-Prompt-Step-Liste von 5 auf 4 Einträge gekürzt; PROBLEM beschreibt jetzt „core problem and its primary audience".
- **Tests** angepasst: step-count-Assertions `9 → 8` in `FlowStateTest`, `ProjectServiceTest`, `ProjectControllerTest` (zwei Stellen — Plan übersah eine), `ProjectStorageTest`. `FlowStateTest.expectedOrder` ohne `TARGET_AUDIENCE`. Funktions-Name `createInitialFlowState creates all 9 steps` → `... all 8 steps` im separaten Cleanup-Commit nach Reviewer-Hinweis.

### Frontend
- **`StepType`** in `api.ts`: Union ohne `TARGET_AUDIENCE`
- **`WIZARD_STEPS`** in `wizard-store.ts`: zwei Einträge → einer mit Label „Problem & Zielgruppe"
- **`BASE_STEPS` + `ALL_STEP_KEYS`** in `category-step-config.ts`: ohne `TARGET_AUDIENCE`; alle Category-Overrides spreaden `BASE_STEPS`, daher kein separater Fix in den Kategorie-Blöcken nötig
- **`step-field-labels.ts`**: PROBLEM-Block neu (`coreProblem`, `primaryAudience`, `painPoints`); TARGET_AUDIENCE-Block gelöscht; Step-Name-Map zeigt `PROBLEM: "Problem & Zielgruppe"`
- **`WizardForm.tsx`**: `TargetAudienceForm`-Import + `FORM_MAP`-Eintrag entfernt
- **`ProblemForm.tsx`**: komplett ersetzt durch 3 `FormField`s — Kernproblem (textarea, required), Primäre Zielgruppe (input, required), Pain Points (TagInput, optional)
- **`TargetAudienceForm.tsx`**: per `git rm` gelöscht
- **`spec-flow/editor.ts`**: `TARGET_AUDIENCE`-Eintrag aus der Step-Liste entfernt
- **`e2e/fixtures/seed-project.ts`**: E2E-Seed-Fixture aktualisiert — `TARGET_AUDIENCE`-PUT entfernt, PROBLEM-Fields auf `coreProblem` + `primaryAudience` umgestellt (Fix nach Code-Quality-Review; ohne diesen Fix hätte der Playwright-Smoke-Test nach dem Merge 400er geliefert)

## Geänderte Dateien

### Backend
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`
- `backend/src/main/resources/application.yml`
- `backend/src/test/resources/application.yml`
- `backend/src/test/kotlin/com/agentwork/productspecagent/domain/FlowStateTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`

### Frontend
- `frontend/src/lib/api.ts`
- `frontend/src/lib/stores/wizard-store.ts`
- `frontend/src/lib/category-step-config.ts`
- `frontend/src/lib/step-field-labels.ts`
- `frontend/src/components/wizard/WizardForm.tsx`
- `frontend/src/components/wizard/steps/ProblemForm.tsx`
- `frontend/src/components/spec-flow/editor.ts`
- `frontend/src/components/wizard/steps/TargetAudienceForm.tsx` — **gelöscht**
- `frontend/e2e/fixtures/seed-project.ts`

## Commit-Sequenz auf Branch
```
9944d96  test(frontend): update e2e seed fixture for 8-step flow
44ab0ac  feat(frontend): merge PROBLEM and TARGET_AUDIENCE forms
c7e996f  test(backend): rename FlowStateTest to reflect 8-step flow
068cb95  feat(backend): merge PROBLEM and TARGET_AUDIENCE wizard steps
```

## Acceptance Criteria — Abdeckung
- [x] `FlowStepType`-Enum enthält kein `TARGET_AUDIENCE` mehr
- [x] Initialer `FlowState` hat 8 Steps
- [x] PROBLEM-Step hat im UI das Label „Problem & Zielgruppe"
- [x] `ProblemForm` zeigt drei Felder: Kernproblem, Primäre Zielgruppe, Pain Points
- [x] `TargetAudienceForm.tsx` gelöscht; `FORM_MAP` ohne `TARGET_AUDIENCE`
- [x] `step-field-labels.ts` PROBLEM-Block mit `coreProblem` / `primaryAudience` / `painPoints`; kein TARGET_AUDIENCE-Block mehr
- [x] Agent-Prompt für PROBLEM fordert kombinierte Markdown-Ausgabe (Problem + Zielgruppe + Pain Points); kein `TARGET_AUDIENCE`-Prompt-Branch
- [x] `saveSpecFile("problem.md", …)` weiterhin aktiv; `target_audience.md` wird für neue Projekte nicht mehr erzeugt
- [x] Backend-Tests an den 8-Step-Flow angepasst (5 Testdateien)
- [x] Frontend-Lint-Baseline nicht regressed (sogar verbessert: 35 → 33 Problems)
- [x] Manuell im Browser verifiziert: „Problem & Zielgruppe" erscheint nach IDEA mit drei Feldern, „Weiter" blockiert auf required fields, danach SCOPE — **von User**

## Abweichungen vom Plan
- **Plan listete 4 step-count-Stellen, real waren es 5.** `ProjectControllerTest.kt` hatte einen zweiten `.value(9)` (Zeile 80 im `full CRUD lifecycle`-Test) — vom Implementer defensiv korrigiert.
- **Plan listete test-YAML only; main-YAML hatte dieselbe TARGET_AUDIENCE-Zeile.** Der Production-System-Prompt wurde gespiegelt aktualisiert, damit er nicht auf einen nicht-existenten Step verweist (defensiver Implementer-Fix, im Spec-Intent).
- **Testname-Widerspruch in `FlowStateTest.kt:9`** (`creates all 9 steps` mit Assertion `== 8`) — Reviewer-Finding, durch separaten Cleanup-Commit `c7e996f` behoben.
- **E2E-Seed-Fixture `seed-project.ts:41`** hat `TARGET_AUDIENCE` gesendet — Code-Quality-Review-Finding, durch Cleanup-Commit `9944d96` behoben. Ohne diesen Fix hätte der Playwright-Smoke nach Merge fehlgeschlagen.

## Bekannte Legacy-Rückstände (außerhalb Feature-Scope)
- **`scripts/test-agent-flow.sh`** hat weiterhin einen `TARGET_AUDIENCE`-Schritt (Zeilen 255–264). Das ist ein manuelles Debug-Script, kein Teil des CI- oder Test-Flows. Bei Nutzung müsste die Sektion in den neuen PROBLEM-Step gemerged werden. Nicht blockierend, als Follow-up dokumentiert.
- **`ScaffoldContextBuilder.kt:88`** und **`SpecContextBuilder.kt:93`** lesen weiterhin den `target_audience.md`-Dateipfad als legacy-Backward-Compat (liefert `null` wenn Datei fehlt). Keine Enum-Referenzen; kein Bug.
- **`cn`-Import in `ProblemForm.tsx` bleibt ungenutzt** — war vorher auch schon so, Plan hat es explizit beibehalten. Separater Codebase-Cleanup möglich (betrifft 6 weitere Step-Forms).

## Verifikation
- `./gradlew test` — **152/152 grün**
- `npm run lint` — 33 problems (17 errors, 16 warnings), gegenüber Baseline 35 (18/17) verbessert
- Pro-Commit Spec-Compliance- und Code-Quality-Reviews (Subagent-Driven-Development) für Tasks 1 + 2 dokumentiert approved
- Manuell im Browser verifiziert — von User
