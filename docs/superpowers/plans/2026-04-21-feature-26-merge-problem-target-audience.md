# Feature 26: Merge PROBLEM and TARGET_AUDIENCE Steps — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the two Wizard steps `PROBLEM` and `TARGET_AUDIENCE` into a single step labelled „Problem & Zielgruppe" with three fields (`coreProblem`, `primaryAudience`, `painPoints`). The enum `PROBLEM` stays, `TARGET_AUDIENCE` is removed from both backend and frontend. Wizard flow shrinks from 9 to 8 steps.

**Architecture:** Two atomic implementation commits (Backend + Frontend), each after a short TDD-style pre-flight run where the broken tests are the failing-state. The spec-file pattern stays — `problem.md` is still written by the agent on step-complete; `target_audience.md` is simply never produced on new projects. The existing test suite is our behavioural verification; no new tests needed beyond fixing the count assertions.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, Next.js 16, React 19, Zustand, Tailwind 4.

---

## File Structure

### Backend (modified)
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt` — drop `TARGET_AUDIENCE` from enum
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt` — remove TARGET_AUDIENCE prompt branch, extend PROBLEM branch, drop TARGET_AUDIENCE mentions in IDEA prompts
- `backend/src/test/resources/application.yml` — remove TARGET_AUDIENCE line from the system-prompt step list

### Backend (tests — step-count and enum references)
- `backend/src/test/kotlin/com/agentwork/productspecagent/domain/FlowStateTest.kt` — 9 → 8, expected-order list
- `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt` — 9 → 8
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerTest.kt` — 9 → 8
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt` — 9 → 8

### Frontend (modified)
- `frontend/src/lib/api.ts` — `StepType` union
- `frontend/src/lib/stores/wizard-store.ts` — `WIZARD_STEPS`
- `frontend/src/lib/category-step-config.ts` — `BASE_STEPS`
- `frontend/src/lib/step-field-labels.ts` — PROBLEM block + step-name map
- `frontend/src/components/wizard/WizardForm.tsx` — `FORM_MAP`
- `frontend/src/components/wizard/steps/ProblemForm.tsx` — add two new fields
- `frontend/src/components/spec-flow/editor.ts` — step list

### Frontend (deleted)
- `frontend/src/components/wizard/steps/TargetAudienceForm.tsx`

---

## Task 1: Backend — drop TARGET_AUDIENCE, extend PROBLEM prompt

**Files (modify):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`
- `backend/src/test/resources/application.yml`
- `backend/src/test/kotlin/com/agentwork/productspecagent/domain/FlowStateTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`

- [ ] **Step 1: Baseline run — confirm suite is green before any edit**

```bash
cd backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`, 152 tests completed.

- [ ] **Step 2: Drop `TARGET_AUDIENCE` from the enum**

File: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt`

```kotlin
enum class FlowStepType {
    IDEA, PROBLEM, SCOPE, MVP,
    FEATURES, ARCHITECTURE, BACKEND, FRONTEND
}
```

`createInitialFlowState` needs no change — it iterates `FlowStepType.entries`, so the reduction propagates automatically.

- [ ] **Step 3: Drop the `"TARGET_AUDIENCE" ->` branch in `IdeaToSpecAgent.buildWizardStepFeedbackPrompt`**

File: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`

Delete the entire block at roughly lines 312–326:
```kotlin
"TARGET_AUDIENCE" -> buildString {
    appendLine("The user just completed the TARGET_AUDIENCE wizard step with the following input:")
    appendLine(fieldsDescription)
    appendLine()
    appendLine("Analyze the target audience definition:")
    appendLine("1. Is the primary audience clearly defined?")
    appendLine("2. Are user needs specific and actionable?")
    appendLine("3. Is there a potential conflict between primary and secondary audiences?")
    appendLine()
    appendLine("If the audience is too broad or there is a strategic choice to make (e.g., B2B vs B2C), use [DECISION_NEEDED].")
    appendLine("If important details are missing, use [CLARIFICATION_NEEDED].")
    appendLine("Be encouraging and constructive.")
    appendLine()
    appendLine(MARKER_REMINDER)
}
```

- [ ] **Step 4: Extend the `"PROBLEM" ->` branch to cover audience + pain points**

File: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`

Replace the existing `"PROBLEM" ->` branch (roughly lines 298–311) with:

```kotlin
"PROBLEM" -> buildString {
    appendLine("The user just completed the PROBLEM wizard step with the following input:")
    appendLine(fieldsDescription)
    appendLine()
    appendLine("This step now covers BOTH the core problem AND the primary target audience + their pain points.")
    appendLine("Analyze the combined problem-and-audience definition:")
    appendLine("1. Is the core problem clearly defined and specific enough?")
    appendLine("2. Is the primary audience concrete (not 'everyone' or 'users')?")
    appendLine("3. Are the pain points tied to the stated audience and problem?")
    appendLine()
    appendLine("The generated problem.md spec should document problem, audience, and pain points together in one coherent section.")
    appendLine("If the audience is too broad or a strategic choice is needed (e.g., B2B vs B2C), use [DECISION_NEEDED].")
    appendLine("If there are contradictions or missing aspects, use [CLARIFICATION_NEEDED].")
    appendLine("Be encouraging and constructive.")
    appendLine()
    appendLine(MARKER_REMINDER)
}
```

- [ ] **Step 5: Update the IDEA-step prompt references to TARGET_AUDIENCE**

File: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`

Line 285 (currently):
```kotlin
appendLine("IMPORTANT: This is the IDEA step. Focus ONLY on the idea itself. Do NOT discuss problem statement, target audience, value proposition, or technical details – these are handled in later steps (PROBLEM, TARGET_AUDIENCE, SCOPE, etc.).")
```
Replace with:
```kotlin
appendLine("IMPORTANT: This is the IDEA step. Focus ONLY on the idea itself. Do NOT discuss problem statement, target audience, value proposition, or technical details – these are handled in later steps (PROBLEM, SCOPE, etc.).")
```

Line 469 (currently):
```kotlin
- Diese Themen werden in späteren Schritten behandelt (PROBLEM, TARGET_AUDIENCE, SCOPE, MVP, etc.).
```
Replace with:
```kotlin
- Diese Themen werden in späteren Schritten behandelt (PROBLEM, SCOPE, MVP, etc.).
```

- [ ] **Step 6: Update the test-resources system-prompt step list**

File: `backend/src/test/resources/application.yml`

Replace lines 23–29 (the `1. IDEA … 5. MVP` list) with:
```yaml
    You work through these steps in order:
    1. IDEA - The user's initial idea (already captured, you acknowledge it)
    2. PROBLEM - Clarify the core problem and its primary audience
    3. SCOPE - Define what is in and out of scope
    4. MVP - Define the minimum viable product
```

- [ ] **Step 7: Fix `FlowStateTest.kt`**

File: `backend/src/test/kotlin/com/agentwork/productspecagent/domain/FlowStateTest.kt`

Line 11:
```kotlin
assertEquals(9, flowState.steps.size)
```
→
```kotlin
assertEquals(8, flowState.steps.size)
```

Lines 44–46 (the `expectedOrder` list) — remove `FlowStepType.TARGET_AUDIENCE`:
```kotlin
val expectedOrder = listOf(
    FlowStepType.IDEA, FlowStepType.PROBLEM,
    FlowStepType.SCOPE, FlowStepType.MVP,
    FlowStepType.FEATURES, FlowStepType.ARCHITECTURE,
    FlowStepType.BACKEND, FlowStepType.FRONTEND
)
```
(Exact formatting depends on the existing line breaks — keep the style of the existing list, just drop the `TARGET_AUDIENCE` entry.)

- [ ] **Step 8: Fix `ProjectServiceTest.kt` step count**

File: `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`

Line 36:
```kotlin
assertEquals(9, response.flowState.steps.size)
```
→
```kotlin
assertEquals(8, response.flowState.steps.size)
```

- [ ] **Step 9: Fix `ProjectControllerTest.kt` step count**

File: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerTest.kt`

Line 30:
```kotlin
.andExpect(jsonPath("$.flowState.steps.length()").value(9))
```
→
```kotlin
.andExpect(jsonPath("$.flowState.steps.length()").value(8))
```

- [ ] **Step 10: Fix `ProjectStorageTest.kt` step count**

File: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`

Line 92:
```kotlin
assertEquals(9, loaded.steps.size)
```
→
```kotlin
assertEquals(8, loaded.steps.size)
```

- [ ] **Step 11: Run the full backend suite — must be green**

```bash
cd backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`. Tests completed should be 152 — same count as baseline (we are not adding tests, only modifying existing ones).

If any other test fails that wasn't in Steps 7–10, STOP and report as `NEEDS_CONTEXT`. The plan may have missed a call-site.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt \
        backend/src/test/resources/application.yml \
        backend/src/test/kotlin/com/agentwork/productspecagent/domain/FlowStateTest.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerTest.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt
git commit -m "feat(backend): merge PROBLEM and TARGET_AUDIENCE wizard steps (Feature 26)

Drops TARGET_AUDIENCE from FlowStepType; the PROBLEM step now covers core
problem, primary audience, and pain points together. Agent prompt for
PROBLEM is extended so the generated problem.md documents all three.
Wizard flow shrinks from 9 to 8 steps.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Frontend — consolidate steps into one

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/stores/wizard-store.ts`
- Modify: `frontend/src/lib/category-step-config.ts`
- Modify: `frontend/src/lib/step-field-labels.ts`
- Modify: `frontend/src/components/wizard/WizardForm.tsx`
- Modify: `frontend/src/components/wizard/steps/ProblemForm.tsx`
- Modify: `frontend/src/components/spec-flow/editor.ts`
- Delete: `frontend/src/components/wizard/steps/TargetAudienceForm.tsx`

- [ ] **Step 1: Baseline lint — confirm 18 errors / 17 warnings**

```bash
cd frontend && npm run lint 2>&1 | tail -3
```

Expected: `✖ 35 problems (18 errors, 17 warnings)`.

- [ ] **Step 2: `StepType` union**

File: `frontend/src/lib/api.ts`

Line 25:
```typescript
export type StepType = "IDEA" | "PROBLEM" | "TARGET_AUDIENCE" | "SCOPE" | "MVP" | "FEATURES" | "ARCHITECTURE" | "BACKEND" | "FRONTEND";
```
→
```typescript
export type StepType = "IDEA" | "PROBLEM" | "SCOPE" | "MVP" | "FEATURES" | "ARCHITECTURE" | "BACKEND" | "FRONTEND";
```

- [ ] **Step 3: `WIZARD_STEPS` entries**

File: `frontend/src/lib/stores/wizard-store.ts`

Replace the PROBLEM + TARGET_AUDIENCE entries (currently around lines 10–12):
```typescript
  { key: "PROBLEM", label: "Problem" },
  { key: "TARGET_AUDIENCE", label: "Zielgruppe" },
```
with a single entry:
```typescript
  { key: "PROBLEM", label: "Problem & Zielgruppe" },
```

- [ ] **Step 4: `BASE_STEPS` and type arrays in category-step-config**

File: `frontend/src/lib/category-step-config.ts`

Line 8 (likely in `StepKey` / union type):
```typescript
  "IDEA", "PROBLEM", "TARGET_AUDIENCE", "SCOPE", "MVP",
```
→
```typescript
  "IDEA", "PROBLEM", "SCOPE", "MVP",
```

Line 12 (`BASE_STEPS`):
```typescript
const BASE_STEPS = ["IDEA", "PROBLEM", "TARGET_AUDIENCE", "SCOPE", "MVP", "FEATURES"] as const;
```
→
```typescript
const BASE_STEPS = ["IDEA", "PROBLEM", "SCOPE", "MVP", "FEATURES"] as const;
```

After the edit, open the file and scan for any other occurrences of `TARGET_AUDIENCE` — remove them too if they appear (e.g. in per-category overrides). If anything is unclear, grep: `grep -n TARGET_AUDIENCE frontend/src/lib/category-step-config.ts`.

- [ ] **Step 5: `step-field-labels.ts` — PROBLEM block + step-name map**

File: `frontend/src/lib/step-field-labels.ts`

Replace the PROBLEM block (lines 9–13):
```typescript
  PROBLEM: {
    coreProblem: "Kernproblem",
    affected: "Wer ist betroffen?",
    workarounds: "Aktuelle Workarounds",
  },
```
with:
```typescript
  PROBLEM: {
    coreProblem: "Kernproblem",
    primaryAudience: "Primäre Zielgruppe",
    painPoints: "Pain Points",
  },
```

Delete the TARGET_AUDIENCE block (currently lines 15–19):
```typescript
  TARGET_AUDIENCE: {
    primaryAudience: "Primaere Zielgruppe",
    painPoints: "Pain Points",
    secondaryAudience: "Sekundaere Zielgruppe",
  },
```
(delete entirely, including the trailing comma on the previous closing brace if needed)

Update the step-name map at line 71:
```typescript
    IDEA: "Idee", PROBLEM: "Problem", TARGET_AUDIENCE: "Zielgruppe",
```
→
```typescript
    IDEA: "Idee", PROBLEM: "Problem & Zielgruppe",
```

- [ ] **Step 6: `FORM_MAP` in `WizardForm.tsx`**

File: `frontend/src/components/wizard/WizardForm.tsx`

Remove line 22 (`TARGET_AUDIENCE: TargetAudienceForm,`) from `FORM_MAP`. Also remove the corresponding `import { TargetAudienceForm } …` statement at the top of the file.

- [ ] **Step 7: Extend `ProblemForm.tsx` with audience fields**

File: `frontend/src/components/wizard/steps/ProblemForm.tsx`

Full replacement content:
```tsx
"use client";
import { FormField } from "../FormField";
import { TagInput } from "../TagInput";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { cn } from "@/lib/utils";

export function ProblemForm({ projectId }: { projectId: string }) {
  const { data, updateField } = useWizardStore();
  const fields = data?.steps["PROBLEM"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const getTags = (key: string): string[] => (fields[key] as string[]) ?? [];
  const set = (key: string, val: any) => updateField("PROBLEM", key, val);

  return (
    <div className="space-y-5">
      <FormField label="Kernproblem" required>
        <textarea value={get("coreProblem")} onChange={(e) => set("coreProblem", e.target.value)}
          placeholder="Welches Problem loest dein Produkt?" rows={3}
          className="w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring min-h-[80px]" />
      </FormField>
      <FormField label="Primäre Zielgruppe" required>
        <input value={get("primaryAudience")} onChange={(e) => set("primaryAudience", e.target.value)}
          placeholder="z.B. Product Owner, Startup-Gründer"
          className="w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
      </FormField>
      <FormField label="Pain Points">
        <TagInput tags={getTags("painPoints")}
          onAdd={(t) => set("painPoints", [...getTags("painPoints"), t])}
          onRemove={(t) => set("painPoints", getTags("painPoints").filter((x: string) => x !== t))}
          placeholder="Pain Point eingeben + Enter" />
      </FormField>
    </div>
  );
}
```
(The `cn` import may be flagged as unused by lint — it was already unused before this change, so leave it in to keep the lint diff at zero.)

- [ ] **Step 8: `spec-flow/editor.ts`**

File: `frontend/src/components/wizard/..` → actually `frontend/src/components/spec-flow/editor.ts`

Remove line 28 (`{ type: "TARGET_AUDIENCE", label: "Zielgruppe" },`) from the step list. Check the surrounding array for correct comma placement after the edit.

- [ ] **Step 9: Delete `TargetAudienceForm.tsx`**

```bash
rm frontend/src/components/wizard/steps/TargetAudienceForm.tsx
```

- [ ] **Step 10: Lint — must match baseline or improve**

```bash
cd frontend && npm run lint 2>&1 | tail -3
```

Expected: `✖ 35 problems (18 errors, 17 warnings)` or fewer. Zero NEW errors.

If the count increased, open the file that reports a new error and check whether a leftover import (e.g. `TargetAudienceForm`) or leftover reference to `TARGET_AUDIENCE` remains. Fix, re-lint. If the count decreased, even better.

- [ ] **Step 11: Commit**

```bash
git add frontend/src/lib/api.ts \
        frontend/src/lib/stores/wizard-store.ts \
        frontend/src/lib/category-step-config.ts \
        frontend/src/lib/step-field-labels.ts \
        frontend/src/components/wizard/WizardForm.tsx \
        frontend/src/components/wizard/steps/ProblemForm.tsx \
        frontend/src/components/spec-flow/editor.ts
git rm frontend/src/components/wizard/steps/TargetAudienceForm.tsx
git commit -m "feat(frontend): merge PROBLEM and TARGET_AUDIENCE forms (Feature 26)

Wizard now shows a single 'Problem & Zielgruppe' step with three fields:
Kernproblem, Primäre Zielgruppe, Pain Points. TargetAudienceForm deleted;
StepType union, WIZARD_STEPS, BASE_STEPS, FORM_MAP, step-field labels,
and the spec-flow editor all updated to match the backend's 8-step flow.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Manual browser test + Done-Doc + merge to main

- [ ] **Step 1: Start backend + frontend**

```bash
./start.sh
```

- [ ] **Step 2: Browser check**

Open `http://localhost:3001/projects/new`. Verify:
- Create a project with just a name
- Workspace opens at step IDEA
- Step rail shows 8 steps (IDEA, Problem & Zielgruppe, SCOPE, MVP, FEATURES, ARCHITECTURE, BACKEND, FRONTEND)
- Skip IDEA (fill out and advance) → land on "Problem & Zielgruppe"
- Three fields visible: Kernproblem (required), Primäre Zielgruppe (required), Pain Points (tag input)
- „Weiter" disabled until Kernproblem + Primäre Zielgruppe are non-empty
- Fill in both required fields + a pain-point tag → advance → land on SCOPE

- [ ] **Step 3: Verify agent output**

- File explorer shows `spec/problem.md` after completing the step
- `spec/target_audience.md` does NOT appear
- Open `problem.md` — content should reference both the problem and the audience (agent prompt was extended)

- [ ] **Step 4: Write done-doc**

File: `docs/features/26-merge-problem-target-audience-done.md`

Follow the pattern of `docs/features/23-simplify-project-create-done.md`:
- Status
- Zusammenfassung (Backend + Frontend + Tests)
- Geänderte Dateien
- Commit-Sequenz
- Acceptance Criteria — Abdeckung (check each box from the spec)
- Abweichungen (if any)
- Verifikation (tests green + manual test done)

- [ ] **Step 5: Commit done-doc**

```bash
git add docs/features/26-merge-problem-target-audience-done.md
git commit -m "docs: Feature 26 done-doc

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 6: Merge feature branch back to main and delete it**

```bash
git checkout main
git merge feat/feature-26-merge-problem-target-audience
git branch -d feat/feature-26-merge-problem-target-audience
git log --oneline -6
```

Expected: fast-forward merge, branch deleted, top commits show Feature 26 landed.
