# Remove SCOPE Step & Reorder FEATURES before MVP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the SCOPE wizard step from the Product-Spec-Agent flow, reorder so FEATURES comes before MVP, rewire MvpForm to select from defined features, and migrate the 3 existing project data folders.

**Architecture:** Single-enum source of truth (`FlowStepType`) drives step order. Reducing the enum from 8 to 7 values auto-propagates through `FlowStepType.entries` iteration. Frontend mirrors the enum via `StepType` union, `WIZARD_STEPS`, and `BASE_STEPS`. MvpForm reads `data.steps["FEATURES"].fields["features"]` and offers chip-select where value = feature.id, label = feature.title. Existing project files are migrated in the same branch so every checkout is self-consistent.

**Tech Stack:** Kotlin 2.3 / Spring Boot 4 / kotlinx.serialization (Backend), Next.js 16 / React 19 / TypeScript / Zustand (Frontend), Gradle 8, Playwright (E2E).

**Spec reference:** `docs/superpowers/specs/2026-04-23-remove-scope-step-design.md` · **Feature doc:** `docs/features/27-remove-scope-merge-into-features.md`

---

## Task 1: Update FlowStepType enum + FlowStateTest

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt:6-9`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/domain/FlowStateTest.kt:9,11,44-48`

- [ ] **Step 1: Update the failing test first**

Replace `FlowStateTest.kt` body:

```kotlin
package com.agentwork.productspecagent.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FlowStateTest {

    @Test
    fun `createInitialFlowState creates all 7 steps`() {
        val flowState = createInitialFlowState("test-project-id")
        assertEquals(7, flowState.steps.size)
    }

    @Test
    fun `createInitialFlowState sets IDEA step as IN_PROGRESS`() {
        val flowState = createInitialFlowState("test-project-id")
        val ideaStep = flowState.steps.find { it.stepType == FlowStepType.IDEA }
        assertNotNull(ideaStep)
        assertEquals(FlowStepStatus.IN_PROGRESS, ideaStep!!.status)
    }

    @Test
    fun `createInitialFlowState sets all other steps as OPEN`() {
        val flowState = createInitialFlowState("test-project-id")
        val nonIdeaSteps = flowState.steps.filter { it.stepType != FlowStepType.IDEA }
        assertTrue(nonIdeaSteps.all { it.status == FlowStepStatus.OPEN })
    }

    @Test
    fun `createInitialFlowState sets currentStep to IDEA`() {
        val flowState = createInitialFlowState("test-project-id")
        assertEquals(FlowStepType.IDEA, flowState.currentStep)
    }

    @Test
    fun `createInitialFlowState sets correct projectId`() {
        val flowState = createInitialFlowState("my-project-123")
        assertEquals("my-project-123", flowState.projectId)
    }

    @Test
    fun `createInitialFlowState steps are in correct order`() {
        val flowState = createInitialFlowState("test-project-id")
        val expectedOrder = listOf(
            FlowStepType.IDEA, FlowStepType.PROBLEM,
            FlowStepType.FEATURES, FlowStepType.MVP,
            FlowStepType.ARCHITECTURE, FlowStepType.BACKEND, FlowStepType.FRONTEND
        )
        assertEquals(expectedOrder, flowState.steps.map { it.stepType })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.productspecagent.domain.FlowStateTest" --quiet`
Expected: FAIL — `expected 7 but got 8`, and ordering mismatch.

- [ ] **Step 3: Update the enum**

In `FlowState.kt`, replace lines 6–9:

```kotlin
enum class FlowStepType {
    IDEA, PROBLEM, FEATURES, MVP,
    ARCHITECTURE, BACKEND, FRONTEND
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.agentwork.productspecagent.domain.FlowStateTest" --quiet`
Expected: PASS — all 6 FlowStateTest methods green.

- [ ] **Step 5: Do not commit yet** — other backend tests still reference `FlowStepType.SCOPE` and will break compilation. Tasks 2–5 fix those.

---

## Task 2: Update Agent prompts (IdeaToSpecAgent)

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt:285,315-329`

The SCOPE step disappears, and two prompt mentions reference SCOPE as a future step.

- [ ] **Step 1: Delete the SCOPE branch**

Delete lines 315–329 in `IdeaToSpecAgent.kt` entirely (the whole `"SCOPE" -> buildString { ... }` block including the closing `}`):

```kotlin
            "SCOPE" -> buildString {
                appendLine("The user just completed the SCOPE wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("Analyze the scope definition:")
                appendLine("1. Is the boundary between in-scope and out-of-scope clear?")
                appendLine("2. Are the constraints realistic?")
                appendLine("3. Is the scope appropriate for the stated timeline and budget?")
                appendLine()
                appendLine("If a scope trade-off needs user input (e.g., reduce features vs. extend timeline), use [DECISION_NEEDED].")
                appendLine("If constraints are unclear or contradictory, use [CLARIFICATION_NEEDED].")
                appendLine("Be encouraging and constructive.")
                appendLine()
                appendLine(MARKER_REMINDER)
            }
```

- [ ] **Step 2: Fix IDEA prompt mention of SCOPE**

Line 285, change:

```kotlin
appendLine("IMPORTANT: This is the IDEA step. Focus ONLY on the idea itself. Do NOT discuss problem statement, target audience, value proposition, or technical details – these are handled in later steps (PROBLEM, SCOPE, etc.).")
```

to:

```kotlin
appendLine("IMPORTANT: This is the IDEA step. Focus ONLY on the idea itself. Do NOT discuss problem statement, target audience, value proposition, or technical details – these are handled in later steps (PROBLEM, FEATURES, etc.).")
```

- [ ] **Step 3: Verify German-locale mention around line 457**

Run: `grep -n "SCOPE" backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`
Expected: **no matches**. If any remain (e.g., a German-locale string), replace `SCOPE` with `FEATURES`.

- [ ] **Step 4: Compile-check**

Run: `./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL. If `when`-branches complain about non-exhaustive matches, the `else`-branch at line 345 handles that — no action needed.

---

## Task 3: Update application.yml (main + test)

**Files:**
- Modify: `backend/src/main/resources/application.yml:23-27`
- Modify: `backend/src/test/resources/application.yml` (mirror)

- [ ] **Step 1: Update main application.yml step list**

Replace lines 23–27 (the numbered step list inside `system-prompt`):

Old:
```
    You work through these steps in order:
    1. IDEA - The user's initial idea (already captured, you acknowledge it)
    2. PROBLEM - Clarify the core problem and its primary audience
    3. SCOPE - Define what is in and out of scope
    4. MVP - Define the minimum viable product
```

New:
```
    You work through these steps in order:
    1. IDEA - The user's initial idea (already captured, you acknowledge it)
    2. PROBLEM - Clarify the core problem and its primary audience
    3. FEATURES - Define the feature set
    4. MVP - Define the minimum viable product (selected from features)
```

- [ ] **Step 2: Mirror update in test yml**

Open `backend/src/test/resources/application.yml` and apply the same change. If the file diverges (e.g., has only partial prompt), just ensure no `SCOPE` reference remains.

- [ ] **Step 3: Verify no SCOPE left in yml**

Run: `grep -n "SCOPE" backend/src/main/resources/application.yml backend/src/test/resources/application.yml`
Expected: no matches.

---

## Task 4: Update remaining backend tests

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgentTest.kt:34`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/DecisionAgentTest.kt:42,45`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DecisionStorageTest.kt:18`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ClarificationStorageTest.kt:17`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/domain/DecisionModelsTest.kt:11`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/domain/ClarificationModelsTest.kt:11`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/DecisionControllerTest.kt:53,76`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ClarificationControllerTest.kt:42`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/ConsistencyCheckServiceTest.kt:64,80`

These tests use `FlowStepType.SCOPE` as a placeholder for fixtures. Replace every occurrence with `FlowStepType.FEATURES`. The tests don't assert anything specific to SCOPE semantics — they just need a valid enum value.

- [ ] **Step 1: Bulk-replace across test folder**

Run:
```bash
grep -rln "FlowStepType.SCOPE" backend/src/test/kotlin
```

Expected output: 8 files (list above minus the two JSON-payload files).

For each file listed, open and change `FlowStepType.SCOPE` → `FlowStepType.FEATURES`.

- [ ] **Step 2: Update JSON-payload test files**

In `DecisionControllerTest.kt` lines 53 and 76, the `stepType` in the request body JSON is a string. Replace `"stepType":"SCOPE"` with `"stepType":"FEATURES"`.

In `PlanGeneratorAgentTest.kt` line 34, replace `"specSection":"SCOPE"` with `"specSection":"FEATURES"`.

- [ ] **Step 3: Verify no SCOPE in tests**

Run:
```bash
grep -rn "SCOPE" backend/src/test/kotlin
```
Expected: no matches (or only matches in comments/unrelated strings — inspect each).

- [ ] **Step 4: Run full backend test suite**

Run: `./gradlew test --quiet`
Expected: BUILD SUCCESSFUL, all tests green. If any test in the list above still fails, it's a missed reference — grep for `SCOPE` in that file and fix.

---

## Task 5: Update scaffold builders + Mustache template

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt:85,97`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilder.kt:93`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt` (check if `ScaffoldContext` has `scopeContent` field)
- Modify: `backend/src/main/resources/templates/scaffold/docs/architecture/overview.md.mustache:13-15`

- [ ] **Step 1: Inspect ScaffoldContext data class**

Run: `grep -n "scopeContent" backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt`

If `scopeContent` is a field in `ScaffoldContext`: remove it. Kotlin's compiler will catch any missed usage.

- [ ] **Step 2: Remove scopeContent in ScaffoldContextBuilder.kt**

Delete line 85:
```kotlin
val scopeContent = readNonBlankSpec(projectId, "scope.md")
```

Delete line 97 (the `scopeContent = scopeContent,` assignment inside the `ScaffoldContext(...)` constructor):
```kotlin
scopeContent = scopeContent,
```

- [ ] **Step 3: Remove scope.md from SpecContextBuilder.kt**

Line 93, change:
```kotlin
listOf("idea.md", "problem.md", "target_audience.md", "scope.md", "mvp.md").forEach { f ->
```

to:
```kotlin
listOf("idea.md", "problem.md", "target_audience.md", "mvp.md").forEach { f ->
```

(Note: `target_audience.md` stays as a legacy backward-compat read — out of scope for this feature. See Feature 26 "Bekannte Legacy-Rückstände".)

- [ ] **Step 4: Update Mustache template**

In `overview.md.mustache`, delete lines 13–15:
```mustache
## Scope
{{#scopeContent}}{{{scopeContent}}}{{/scopeContent}}
{{^scopeContent}}Noch nicht definiert.{{/scopeContent}}
```

And remove the blank line that follows them. The MVP section should now come directly after the `{{#targetAudienceContent}}...{{/targetAudienceContent}}` block.

- [ ] **Step 5: Compile + test**

Run: `./gradlew test --quiet`
Expected: BUILD SUCCESSFUL, all tests green.

---

## Task 6: Backend commits

- [ ] **Step 1: Stage + commit enum/agent/yml/scaffold changes**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt \
        backend/src/main/resources/application.yml \
        backend/src/test/resources/application.yml \
        backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilder.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt \
        backend/src/main/resources/templates/scaffold/docs/architecture/overview.md.mustache

git commit -m "$(cat <<'EOF'
feat(backend): remove SCOPE wizard step, reorder FEATURES before MVP

Feature 27. Reduces FlowStepType enum from 8 to 7 values and moves
FEATURES before MVP so MVP can select from defined features.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 2: Stage + commit test updates as separate commit**

```bash
git add backend/src/test/kotlin/

git commit -m "$(cat <<'EOF'
test(backend): update tests for 7-step flow without SCOPE

Replaces FlowStepType.SCOPE with FlowStepType.FEATURES in fixture data
across 9 test files; updates step-count assertion and expected order
in FlowStateTest.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Frontend types + store + category config

**Files:**
- Modify: `frontend/src/lib/api.ts:25`
- Modify: `frontend/src/lib/stores/wizard-store.ts` (WIZARD_STEPS constant)
- Modify: `frontend/src/lib/category-step-config.ts` (ALL_STEP_KEYS, BASE_STEPS)

- [ ] **Step 1: Update StepType union**

In `api.ts` line 25, replace:
```ts
export type StepType = "IDEA" | "PROBLEM" | "SCOPE" | "MVP" | "FEATURES" | "ARCHITECTURE" | "BACKEND" | "FRONTEND";
```

with:
```ts
export type StepType = "IDEA" | "PROBLEM" | "FEATURES" | "MVP" | "ARCHITECTURE" | "BACKEND" | "FRONTEND";
```

- [ ] **Step 2: Update WIZARD_STEPS in wizard-store.ts**

Find `WIZARD_STEPS` constant. Remove the `SCOPE` entry. Reorder so FEATURES comes before MVP:

```ts
export const WIZARD_STEPS = [
  { key: "IDEA", label: "Idee" },
  { key: "PROBLEM", label: "Problem & Zielgruppe" },
  { key: "FEATURES", label: "Features" },
  { key: "MVP", label: "MVP" },
  { key: "ARCHITECTURE", label: "Architektur" },
  { key: "BACKEND", label: "Backend" },
  { key: "FRONTEND", label: "Frontend" },
] as const;
```

- [ ] **Step 3: Update category-step-config.ts**

Update `ALL_STEP_KEYS`:
```ts
export const ALL_STEP_KEYS = [
  "IDEA", "PROBLEM", "FEATURES", "MVP",
  "ARCHITECTURE", "BACKEND", "FRONTEND",
] as const;
```

Update `BASE_STEPS`:
```ts
const BASE_STEPS = ["IDEA", "PROBLEM", "FEATURES", "MVP"] as const;
```

All category blocks that spread `BASE_STEPS` auto-propagate.

- [ ] **Step 4: Verify typescript compiles**

Run: `cd frontend && npx tsc --noEmit`
Expected: Several errors about missing `SCOPE` cases — these will be fixed in Tasks 8–10. If errors only reference `SCOPE` step, that's expected at this point.

---

## Task 8: Frontend labels + WizardForm + delete ScopeForm

**Files:**
- Modify: `frontend/src/lib/step-field-labels.ts:14-17` (SCOPE block in STEP_FIELD_LABELS) and the inline `stepLabel` map inside `formatStepFields()`
- Modify: `frontend/src/components/wizard/WizardForm.tsx:11,21`
- Delete: `frontend/src/components/wizard/steps/ScopeForm.tsx`

- [ ] **Step 1: Remove SCOPE block from STEP_FIELD_LABELS**

Delete this block in `step-field-labels.ts` (around lines 14–17):
```ts
  SCOPE: {
    inScope: "In Scope",
    outOfScope: "Out of Scope",
  },
```

- [ ] **Step 2: Remove SCOPE from stepLabel map**

Find the `stepLabel` map near the bottom of `step-field-labels.ts` (inside `formatStepFields()`):
```ts
    IDEA: "Idee", PROBLEM: "Problem & Zielgruppe",
    SCOPE: "Scope", MVP: "MVP", FEATURES: "Features",
    ARCHITECTURE: "Architektur", BACKEND: "Backend", FRONTEND: "Frontend",
```

Change to:
```ts
    IDEA: "Idee", PROBLEM: "Problem & Zielgruppe",
    FEATURES: "Features", MVP: "MVP",
    ARCHITECTURE: "Architektur", BACKEND: "Backend", FRONTEND: "Frontend",
```

**Important:** `SCOPE_FIELD_LABELS` and `SCOPE_FIELDS_BY_SCOPE` (further down in the same file) refer to *feature* scope (FRONTEND/BACKEND), NOT the SCOPE step. **Leave them untouched.**

- [ ] **Step 3: Remove ScopeForm import + mapping in WizardForm.tsx**

Delete line 11 (import):
```ts
import { ScopeForm } from "./steps/ScopeForm";
```

Delete the `SCOPE: ScopeForm` entry in the `FORM_MAP` (around line 21).

- [ ] **Step 4: Delete the ScopeForm file**

```bash
git rm frontend/src/components/wizard/steps/ScopeForm.tsx
```

- [ ] **Step 5: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: Errors narrow down to `MvpForm.tsx` (still references SCOPE step) and possibly `spec-flow/editor.ts`. Those are fixed in Tasks 10–11.

---

## Task 9: Extend ChipSelect to support {label, value} options

**Files:**
- Modify: `frontend/src/components/wizard/ChipSelect.tsx`

The current API takes `options: string[]`. MvpForm needs to show feature titles (labels) while storing feature IDs (values). Extend the API to accept either `string[]` or `{ label, value }[]`, preserving backward compatibility.

- [ ] **Step 1: Update ChipSelect**

Replace the full content of `ChipSelect.tsx`:

```tsx
"use client";

import { cn } from "@/lib/utils";

type ChipOption = string | { label: string; value: string };

interface ChipSelectProps {
  options: ChipOption[];
  value: string | string[];
  onChange: (value: string | string[]) => void;
  multiSelect?: boolean;
}

function normalize(opt: ChipOption): { label: string; value: string } {
  return typeof opt === "string" ? { label: opt, value: opt } : opt;
}

export function ChipSelect({ options, value, onChange, multiSelect = false }: ChipSelectProps) {
  const selected = Array.isArray(value) ? value : value ? [value] : [];
  const normalized = options.map(normalize);

  function handleClick(v: string) {
    if (multiSelect) {
      const next = selected.includes(v)
        ? selected.filter((x) => x !== v)
        : [...selected, v];
      onChange(next);
    } else {
      onChange(v);
    }
  }

  return (
    <div className="flex flex-wrap gap-2">
      {normalized.map((opt) => {
        const isSelected = selected.includes(opt.value);
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => handleClick(opt.value)}
            className={cn(
              "rounded-full px-3 py-1.5 text-xs font-medium transition-all",
              isSelected
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-muted/80 hover:text-foreground"
            )}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 2: Typecheck — existing callers still work**

Run: `cd frontend && npx tsc --noEmit`
Expected: No new errors from ChipSelect callers (existing `string[]` usage remains valid).

---

## Task 10: Refactor MvpForm to use Features

**Files:**
- Modify: `frontend/src/components/wizard/steps/MvpForm.tsx`

- [ ] **Step 1: Replace MvpForm.tsx**

Overwrite with:

```tsx
"use client";
import { FormField } from "../FormField";
import { ChipSelect } from "../ChipSelect";
import { useWizardStore } from "@/lib/stores/wizard-store";

interface FeatureLike {
  id: string;
  title: string;
}

export function MvpForm({ projectId }: { projectId: string }) {
  const { data, updateField } = useWizardStore();
  const fields = data?.steps["MVP"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const set = (key: string, val: unknown) => updateField("MVP", key, val);

  const featuresFields = data?.steps["FEATURES"]?.fields ?? {};
  const features = (featuresFields["features"] as FeatureLike[] | undefined) ?? [];
  const featureOptions = features.map((f) => ({ label: f.title, value: f.id }));

  return (
    <div className="space-y-5">
      <FormField label="MVP-Ziel" required>
        <textarea value={get("goal")} onChange={(e) => set("goal", e.target.value)}
          placeholder="Was soll das MVP leisten?" rows={3}
          className="w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring min-h-[80px]" />
      </FormField>
      {featureOptions.length > 0 && (
        <FormField label="MVP Features (aus Feature-Liste)">
          <ChipSelect
            options={featureOptions}
            value={(fields["mvpFeatures"] as string[]) ?? []}
            onChange={(v) => set("mvpFeatures", v)}
            multiSelect
          />
        </FormField>
      )}
      <FormField label="Erfolgskriterien">
        <textarea value={get("successCriteria")} onChange={(e) => set("successCriteria", e.target.value)}
          placeholder="Woran erkennst du, dass das MVP erfolgreich ist?" rows={2}
          className="w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
      </FormField>
    </div>
  );
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: MvpForm errors are gone. Only `spec-flow/editor.ts` may still reference SCOPE.

---

## Task 11: Update spec-flow editor + e2e seed fixture

**Files:**
- Modify: `frontend/src/components/spec-flow/editor.ts:28`
- Modify: `frontend/e2e/fixtures/seed-project.ts:38-43`

- [ ] **Step 1: Update spec-flow STEPS array**

Find the `STEPS` array in `editor.ts`. Remove the `{ type: "SCOPE", label: "Scope" }` entry and move `FEATURES` before `MVP`:

```ts
const STEPS = [
  { type: "IDEA", label: "Idee" },
  { type: "PROBLEM", label: "Problem & Zielgruppe" },
  { type: "FEATURES", label: "Features" },
  { type: "MVP", label: "MVP" },
  { type: "ARCHITECTURE", label: "Architektur" },
  { type: "BACKEND", label: "Backend" },
  { type: "FRONTEND", label: "Frontend" },
];
```

(Verify the exact shape of this array in the file — if it differs, preserve any extra fields but remove SCOPE and reorder.)

- [ ] **Step 2: Update e2e seed fixture**

In `seed-project.ts`, replace the `steps` array (lines 38–43):

```ts
  const steps: Array<[string, Record<string, unknown>]> = [
    ["IDEA",     { idea: "E2E smoke-test project", category: "SaaS" }],
    ["PROBLEM",  { coreProblem: "Validate graph editor end-to-end", primaryAudience: "Developers" }],
    ["FEATURES", { features: [] }],
    ["MVP",      { goal: "Add/rename/connect nodes + persist" }],
  ];
```

(The FEATURES step is seeded with an empty features array — the Playwright test populates it via the graph editor.)

Also update the JSDoc comment (lines 9–12):

```ts
/**
 * Creates a minimal SaaS project seeded through IDEA→FEATURES so the FEATURES
 * step is reachable without triggering any AI/LLM calls.
 *
 * DTO shapes verified against:
 * ...
 */
```

(Just verify the current wording and adjust if it mentions MVP as the last seeded step.)

- [ ] **Step 3: Typecheck + lint**

Run: `cd frontend && npx tsc --noEmit && npm run lint`
Expected: No TypeScript errors; lint warnings may remain at pre-feature baseline.

- [ ] **Step 4: Build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESSFUL.

---

## Task 12: Frontend commit

- [ ] **Step 1: Stage + commit**

```bash
git add frontend/src/lib/api.ts \
        frontend/src/lib/stores/wizard-store.ts \
        frontend/src/lib/category-step-config.ts \
        frontend/src/lib/step-field-labels.ts \
        frontend/src/components/wizard/WizardForm.tsx \
        frontend/src/components/wizard/ChipSelect.tsx \
        frontend/src/components/wizard/steps/MvpForm.tsx \
        frontend/src/components/wizard/steps/ScopeForm.tsx \
        frontend/src/components/spec-flow/editor.ts \
        frontend/e2e/fixtures/seed-project.ts

git commit -m "$(cat <<'EOF'
feat(frontend): remove SCOPE wizard step and route MVP to FEATURES

Feature 27. Reduces StepType union from 8 to 7 values. MvpForm now reads
from the FEATURES step's feature list and stores selected feature IDs.
ChipSelect extended to support {label, value} options for id/title split.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Migrate existing project data

**Files:** (12 files across 3 project directories in `backend/data/projects/`)
- Modify: `6bbd692e-210e-455d-b537-2c1ce9493202/flow-state.json`
- Modify: `ba4017dc-e796-4c64-950a-38f13d0c6174/flow-state.json`
- Modify: `d052d24e-b79d-43c2-b1e5-61ee02451bb6/flow-state.json`
- Modify: `ba4017dc-.../wizard.json`
- Modify: `d052d24e-.../wizard.json`
- Modify: `ba4017dc-.../clarifications/9a632294-6960-4579-97f5-8d5c10eb3123.json`
- Modify: `ba4017dc-.../tasks/05cae415-f1a3-44e0-89ae-7b02ce17397e.json`
- Modify: `ba4017dc-.../tasks/c9aff265-22f5-42a7-bc8a-b906d08acb5f.json`
- Modify: `ba4017dc-.../docs/architecture/overview.md`
- Modify: `d052d24e-.../docs/architecture/overview.md`
- Delete: `ba4017dc-.../spec/scope.md`
- Delete: `d052d24e-.../spec/scope.md`

- [ ] **Step 1: Remove SCOPE step from all 3 flow-state.json files**

For each of the three files, delete the JSON object block with `"stepType": "SCOPE"` (and the trailing comma of the previous entry if needed). After edit, validate with:

```bash
for f in backend/data/projects/*/flow-state.json; do
  python3 -c "import json; json.load(open('$f'))" && echo "OK: $f" || echo "INVALID: $f"
done
```

Expected: `OK` for all three files.

- [ ] **Step 2: Remove SCOPE block from 2 wizard.json files**

In `ba4017dc-.../wizard.json`, delete this block:
```json
        "SCOPE": {
            "fields": {
                "inScope": ["-"],
                "outOfScope": ["-"]
            },
            "completedAt": "..."
        },
```

In `d052d24e-.../wizard.json`, delete:
```json
        "SCOPE": {
            "fields": {
                "inScope": ["Jira API", "Frontend", "Backend", "Agenten"],
                "outOfScope": ["Mocking der Agenten"]
            },
            "completedAt": "..."
        },
```

(Use Read first to get exact content, then Edit with exact strings. The surrounding commas must be handled — if SCOPE is the first or last key, adjust accordingly.)

Validate with:
```bash
for f in backend/data/projects/*/wizard.json; do
  python3 -c "import json; json.load(open('$f'))" && echo "OK: $f" || echo "INVALID: $f"
done
```

- [ ] **Step 3: Update Clarification stepType**

In `ba4017dc-.../clarifications/9a632294-6960-4579-97f5-8d5c10eb3123.json`, change:
```json
"stepType": "SCOPE",
```
to:
```json
"stepType": "FEATURES",
```

- [ ] **Step 4: Update Task specSection for 2 tasks**

In `ba4017dc-.../tasks/05cae415-f1a3-44e0-89ae-7b02ce17397e.json` and `ba4017dc-.../tasks/c9aff265-22f5-42a7-bc8a-b906d08acb5f.json`, change:
```json
"specSection": "SCOPE"
```
to:
```json
"specSection": "FEATURES"
```

- [ ] **Step 5: Delete scope.md spec files**

```bash
git rm backend/data/projects/ba4017dc-e796-4c64-950a-38f13d0c6174/spec/scope.md \
       backend/data/projects/d052d24e-b79d-43c2-b1e5-61ee02451bb6/spec/scope.md
```

- [ ] **Step 6: Remove ## Scope section from 2 overview.md**

In both `ba4017dc-.../docs/architecture/overview.md` and `d052d24e-.../docs/architecture/overview.md`, delete the `## Scope` section (including its body lines and trailing blank line — stops at the next `##` header).

Use Read first to get the exact content; the sections will look something like:
```markdown
## Scope
# Scope

- **inScope**: [...]
- **outOfScope**: [...]


```

- [ ] **Step 7: Final verification — no residual SCOPE references in data**

```bash
grep -rln '"stepType": "SCOPE"\|"specSection": "SCOPE"\|"SCOPE":' backend/data/projects/
```
Expected: **no output**.

```bash
grep -rln "## Scope" backend/data/projects/*/docs/
```
Expected: **no output**.

- [ ] **Step 8: Commit data migration**

```bash
git add backend/data/projects/

git commit -m "$(cat <<'EOF'
chore(data): migrate existing projects to 7-step flow

Feature 27. Removes SCOPE step entries from flow-state.json and wizard.json
across 3 projects; remaps 1 clarification and 2 tasks from SCOPE to FEATURES;
deletes scope.md spec files and Scope sections from generated overview.md.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Manual verification

- [ ] **Step 1: Start backend**

Run: `cd backend && ./gradlew bootRun --quiet` (in background or separate terminal).

Expected: no `SerializationException`, startup completes, log shows `Started Application`.

- [ ] **Step 2: Start frontend**

Run: `cd frontend && npm run dev`

Expected: dev server on http://localhost:3000.

- [ ] **Step 3: Open all 3 existing projects**

Visit `/projects` and click into each of the 3 seeded projects. For each:

- Verify: project page loads without error banner.
- Verify: wizard sidebar shows 7 steps (`Idee, Problem & Zielgruppe, Features, MVP, Architektur, Backend, Frontend`) — NO `Scope` entry.
- Verify: clicking into the MVP step shows the form without runtime errors.

- [ ] **Step 4: Open `d052d24e-...` and verify MVP Features chips**

In project `d052d24e-...` (TaskMaster — has 2 defined features), navigate to MVP step. Verify chips appear showing feature titles from FEATURES step.

- [ ] **Step 5: Create a new project**

Via `/projects/new`, create a new SaaS project. Complete IDEA. Verify flow navigates:
`IDEA → PROBLEM → FEATURES → MVP → ARCHITECTURE → BACKEND → FRONTEND`.

- [ ] **Step 6: Confirm no console errors**

Open DevTools Console. Expected: no red errors related to `SCOPE`, `stepType`, or missing fields.

---

## Self-Review

### Spec coverage check

| Spec requirement | Implementing task |
|------------------|-------------------|
| Enum `SCOPE` entfernt, Reihenfolge `IDEA, PROBLEM, FEATURES, MVP, …` | Task 1 |
| SCOPE-Prompt-Block entfernt | Task 2 |
| Prompt-Mentions `(PROBLEM, SCOPE, etc.)` → FEATURES | Task 2 |
| `application.yml` Step-Liste aktualisiert | Task 3 |
| 10 Backend-Tests angepasst | Task 4 + Task 1 (FlowStateTest) |
| Scaffold/Spec/Mustache ohne scopeContent | Task 5 |
| `StepType` Union ohne `SCOPE` | Task 7 |
| `WIZARD_STEPS`, `BASE_STEPS`, `ALL_STEP_KEYS` aktualisiert | Task 7 |
| `step-field-labels.ts` SCOPE-Block raus | Task 8 |
| `WizardForm` ohne ScopeForm | Task 8 |
| `ScopeForm.tsx` gelöscht | Task 8 |
| ChipSelect supports {label, value} | Task 9 |
| MvpForm liest aus FEATURES, speichert IDs | Task 10 |
| `spec-flow/editor.ts` ohne SCOPE | Task 11 |
| E2E seed fixture | Task 11 |
| 3 flow-state.json migriert | Task 13.1 |
| 2 wizard.json migriert | Task 13.2 |
| 1 Clarification remapped | Task 13.3 |
| 2 Tasks remapped | Task 13.4 |
| 2 scope.md gelöscht | Task 13.5 |
| 2 overview.md ohne Scope-Section | Task 13.6 |
| Manuelle Verifikation | Task 14 |

All spec requirements covered.

### Placeholder scan

- No TBDs.
- Task 5 Step 1 ("if `scopeContent` is a field") is a conditional but includes the concrete action — acceptable because the cost of spelling it out for both branches is low and the engineer can grep in 5 seconds.
- All code blocks are complete.
- All shell commands have expected outputs.

### Type consistency

- `FlowStepType` enum uses `FEATURES` throughout.
- `StepType` TS union matches enum.
- `FeatureLike { id, title }` in MvpForm matches the shape of features defined in the FEATURES wizard step (see `d052d24e-.../wizard.json` features block).
- `ChipSelect` extended API is backward-compatible with existing `string[]` callers.

No inconsistencies found.

---

## Execution choice

Plan complete and saved to `docs/superpowers/plans/2026-04-23-remove-scope-step.md`. Two execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks.
2. **Inline Execution** — execute tasks in this session with checkpoints.
