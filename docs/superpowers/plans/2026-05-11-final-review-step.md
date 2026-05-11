# Final Review Step Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit `REVIEW` wizard step that is always the final gate before generating `spec.md` and enabling export.

**Architecture:** The backend owns the source of truth for wizard progression by adding `FlowStepType.REVIEW` and ensuring every category plan ends with it. The frontend renders a read-only `ReviewForm` from existing `WizardData`; completing `REVIEW` sends `{ confirmed: true }` to the existing completion endpoint. The existing final-spec generation path moves from "last visible category step" to the new terminal `REVIEW` step.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, AssertJ, Next.js 16 App Router, React 19, TypeScript, Zustand, Tailwind CSS 4, lucide-react.

---

## File Map

- Modify `docs/features/00-feature-set-overview.md`: add Feature 48 to the project feature table.
- Create `docs/features/48-final-review-step.md`: project feature document aligned with the approved design.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt`: add `REVIEW` enum value.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`: guarantee `REVIEW` is appended to every visible step plan.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogDefaults.kt`: include `REVIEW` in category defaults.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`: make `REVIEW` the only terminal export-generating step and suppress markers for it.
- Modify `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`: expect 9 initial flow steps.
- Modify `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicyTest.kt`: update category terminal assertions to `REVIEW`.
- Modify `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`: update old terminal tests and add `REVIEW` completion coverage.
- Modify `frontend/src/lib/api.ts`: add `"REVIEW"` to `StepType`.
- Modify `frontend/src/lib/category-step-config.ts`: add `REVIEW` to step keys and append it to visible steps.
- Modify `frontend/src/lib/stores/wizard-store.ts`: add `REVIEW` label and send `{ confirmed: true }` when completing the review step.
- Modify `frontend/src/components/wizard/WizardForm.tsx`: render `ReviewForm`, add help text/icon, and label the primary review action.
- Create `frontend/src/components/wizard/steps/ReviewForm.tsx`: read-only summary UI.

## Task 1: Register The Feature In Project Docs

**Files:**

- Modify: `docs/features/00-feature-set-overview.md`
- Create: `docs/features/48-final-review-step.md`

- [ ] **Step 1: Add Feature 48 to the overview**

Edit `docs/features/00-feature-set-overview.md` and append this row after Feature 47:

```markdown
| 48 | Final Review Step | [48-final-review-step.md](48-final-review-step.md) | Feature 11, 12, 13, 46 | M |
```

- [ ] **Step 2: Create the feature document**

Create `docs/features/48-final-review-step.md` with this content:

```markdown
# Feature 48: Final Review Step

## Zusammenfassung

Der Wizard bekommt einen expliziten finalen `REVIEW`-Step fuer alle Projektarten. Dieser Step zeigt eine read-only Zusammenfassung aller Wizard-Daten, dient als bewusstes Review-Gate und aktiviert den Export erst nach finaler Bestaetigung.

## Problem

Aktuell erzeugt der jeweils letzte sichtbare Fach-Step direkt die finale `spec.md` und oeffnet den Export. Je nach Kategorie ist das `MVP`, `ARCHITECTURE`, `BACKEND` oder `FRONTEND`. Dadurch fehlt ein expliziter Abschlussmoment, an dem Nutzer die gesamte Spezifikation pruefen und bewusst freigeben.

## Ziel

- `REVIEW` ist immer der letzte sichtbare Wizard-Step.
- Kein fachlicher Step erzeugt direkt den Export.
- Der Review-Step zeigt eine strukturierte Zusammenfassung aus bestehenden Wizard-Daten.
- Erst `Final bestaetigen` erzeugt `spec.md` und macht Export verfuegbar.
- Feature-Hinzufuegen im Review-Step ist nicht enthalten.

## Betroffene Dateien

- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogDefaults.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/category-step-config.ts`
- `frontend/src/lib/stores/wizard-store.ts`
- `frontend/src/components/wizard/WizardForm.tsx`
- `frontend/src/components/wizard/steps/ReviewForm.tsx`

## Akzeptanzkriterien

- Jede Kategorie zeigt `Review` als letzten Wizard-Step.
- Der bisherige letzte Fach-Step navigiert zu `Review` statt Export zu oeffnen.
- `Review` rendert zentrale Wizard-Daten read-only.
- `Final bestaetigen` schliesst `REVIEW` ab und erzeugt `spec.md`.
- Erst danach ist Export verfuegbar.
```

- [ ] **Step 3: Verify docs are staged cleanly**

Run:

```bash
git diff -- docs/features/00-feature-set-overview.md docs/features/48-final-review-step.md
```

Expected: one new overview row and one new feature document.

- [ ] **Step 4: Commit docs**

Run:

```bash
git add docs/features/00-feature-set-overview.md docs/features/48-final-review-step.md
git commit -m "docs: add final review step feature"
```

## Task 2: Backend Progression And Completion

**Files:**

- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogDefaults.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicyTest.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`

- [ ] **Step 1: Write failing progression-policy expectations**

Update `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicyTest.kt`.

Replace `fullFlowSteps` with:

```kotlin
private val fullFlowSteps = listOf(
    FlowStepType.IDEA,
    FlowStepType.PROBLEM,
    FlowStepType.FEATURES,
    FlowStepType.MVP,
    FlowStepType.DESIGN,
    FlowStepType.ARCHITECTURE,
    FlowStepType.BACKEND,
    FlowStepType.FRONTEND,
    FlowStepType.REVIEW,
)
```

Update the terminal assertions:

```kotlin
assertThat(plan.visibleSteps).containsExactly(
    FlowStepType.IDEA,
    FlowStepType.PROBLEM,
    FlowStepType.FEATURES,
    FlowStepType.MVP,
    FlowStepType.REVIEW,
)
assertThat(plan.isTerminal(FlowStepType.REVIEW)).isTrue()
assertThat(plan.nextAfter(FlowStepType.MVP)).isEqualTo(FlowStepType.REVIEW)
```

For CLI:

```kotlin
assertThat(plan.visibleSteps).containsExactly(
    FlowStepType.IDEA,
    FlowStepType.PROBLEM,
    FlowStepType.FEATURES,
    FlowStepType.MVP,
    FlowStepType.ARCHITECTURE,
    FlowStepType.REVIEW,
)
assertThat(plan.isTerminal(FlowStepType.REVIEW)).isTrue()
assertThat(plan.nextAfter(FlowStepType.ARCHITECTURE)).isEqualTo(FlowStepType.REVIEW)
```

For API:

```kotlin
assertThat(plan.visibleSteps).containsExactly(
    FlowStepType.IDEA,
    FlowStepType.PROBLEM,
    FlowStepType.FEATURES,
    FlowStepType.MVP,
    FlowStepType.ARCHITECTURE,
    FlowStepType.BACKEND,
    FlowStepType.REVIEW,
)
assertThat(plan.isTerminal(FlowStepType.REVIEW)).isTrue()
assertThat(plan.nextAfter(FlowStepType.BACKEND)).isEqualTo(FlowStepType.REVIEW)
```

In `catalog visible steps override category defaults`, keep the custom catalog without `REVIEW` and assert the policy appends it:

```kotlin
assertThat(plan.visibleSteps).containsExactly(
    FlowStepType.IDEA,
    FlowStepType.PROBLEM,
    FlowStepType.FEATURES,
    FlowStepType.MVP,
    FlowStepType.BACKEND,
    FlowStepType.REVIEW,
)
assertThat(plan.isTerminal(FlowStepType.REVIEW)).isTrue()
```

- [ ] **Step 2: Run policy tests and confirm failure**

Run:

```bash
cd backend && ./gradlew test --tests 'com.agentwork.productspecagent.service.WizardProgressionPolicyTest'
```

Expected: compilation fails because `FlowStepType.REVIEW` does not exist.

- [ ] **Step 3: Add the backend enum value**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt`:

```kotlin
enum class FlowStepType {
    IDEA, PROBLEM, FEATURES, MVP, DESIGN,
    ARCHITECTURE, BACKEND, FRONTEND, REVIEW
}
```

- [ ] **Step 4: Guarantee REVIEW in backend progression plans**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`.

Add helper:

```kotlin
private fun withReviewStep(steps: List<FlowStepType>): List<FlowStepType> =
    if (steps.lastOrNull() == FlowStepType.REVIEW) steps
    else steps.filterNot { it == FlowStepType.REVIEW } + FlowStepType.REVIEW
```

Wrap the catalog return:

```kotlin
if (catalogSteps != null) return withReviewStep(catalogSteps)
```

Wrap the category fallback:

```kotlin
return withReviewStep(
    when (category) {
        ProductCategory.LIBRARY -> listOf(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
        )
        ProductCategory.CLI_TOOL -> listOf(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.ARCHITECTURE,
        )
        ProductCategory.API -> listOf(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.ARCHITECTURE,
            FlowStepType.BACKEND,
        )
        ProductCategory.SAAS,
        ProductCategory.MOBILE_APP,
        ProductCategory.DESKTOP_APP,
        null -> fullFlowSteps
    }
)
```

Update `fullFlowSteps` to include `FlowStepType.REVIEW` at the end.

- [ ] **Step 5: Include REVIEW in option defaults**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogDefaults.kt`.

Add:

```kotlin
private fun withReviewStep(steps: List<FlowStepType>): List<FlowStepType> =
    steps.filterNot { it == FlowStepType.REVIEW } + FlowStepType.REVIEW
```

Wrap each `visibleSteps = ...` value:

```kotlin
visibleSteps = withReviewStep(baseSteps + listOf(FlowStepType.DESIGN, FlowStepType.ARCHITECTURE, FlowStepType.BACKEND, FlowStepType.FRONTEND))
```

For CLI:

```kotlin
visibleSteps = withReviewStep(baseSteps + FlowStepType.ARCHITECTURE)
```

For Library:

```kotlin
visibleSteps = withReviewStep(baseSteps)
```

For API:

```kotlin
visibleSteps = withReviewStep(baseSteps + listOf(FlowStepType.ARCHITECTURE, FlowStepType.BACKEND))
```

- [ ] **Step 6: Update initial flow-state count test**

In `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`, change:

```kotlin
assertEquals(8, response.flowState.steps.size)
```

to:

```kotlin
assertEquals(9, response.flowState.steps.size)
assertTrue(response.flowState.steps.any { it.stepType == FlowStepType.REVIEW })
```

- [ ] **Step 7: Update completion tests for the new terminal step**

In `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`, rename and rewrite `complete FRONTEND suppresses blocker markers and generates final spec` to:

```kotlin
@Test
fun `complete FRONTEND advances to review without generating final spec`() = runBlocking {
    val project = projectService.createProject("Test")
    setFlowProgress(
        project.project.id,
        FlowStepType.FRONTEND,
        setOf(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.DESIGN,
            FlowStepType.ARCHITECTURE,
            FlowStepType.BACKEND,
        ),
    )
    val agent = CapturingWizardAgent("Frontend looks good.")
    val completion = createCompletion(agent)

    val result = completion.complete(
        CompleteWizardStep(
            projectId = project.project.id,
            step = FlowStepType.FRONTEND,
            fields = mapOf("framework" to "Next.js+React", "theme" to "Both"),
        )
    )

    assertThat(result.message).isEqualTo("Frontend looks good.")
    assertThat(result.nextStep).isEqualTo(FlowStepType.REVIEW)
    assertThat(result.exportTriggered).isFalse()
    assertThat(result.progression.status).isEqualTo("IN_PROGRESS")
    assertThat(result.progression.currentStep).isEqualTo(FlowStepType.REVIEW.name)
    assertThat(result.progression.primaryAction.type).isEqualTo("COMPLETE_STEP")
    assertThat(result.progression.primaryAction.step).isEqualTo(FlowStepType.REVIEW.name)
    assertThat(result.action.type).isEqualTo("SHOW_STEP")
    assertThat(result.action.step).isEqualTo(FlowStepType.REVIEW.name)
    assertThat(projectService.readSpecFile(project.project.id, "spec.md")).isNull()
    Unit
}
```

Add a new terminal test:

```kotlin
@Test
fun `complete REVIEW suppresses blocker markers and generates final spec`() = runBlocking {
    val project = projectService.createProject("Test")
    setFlowProgress(
        project.project.id,
        FlowStepType.REVIEW,
        setOf(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.DESIGN,
            FlowStepType.ARCHITECTURE,
            FlowStepType.BACKEND,
            FlowStepType.FRONTEND,
        ),
    )
    val agent = SequenceWizardAgent(
        listOf(
            "Review confirmed.\n[DECISION_NEEDED]: Ignore this marker\n[CLARIFICATION_NEEDED]: Ignore? | final step",
            "# Product Specification\n\nDone.",
        )
    )
    val completion = createCompletion(agent)

    val result = completion.complete(
        CompleteWizardStep(
            projectId = project.project.id,
            step = FlowStepType.REVIEW,
            fields = mapOf("confirmed" to true),
        )
    )

    assertThat(result.message).isEqualTo("Review confirmed.")
    assertThat(result.nextStep).isNull()
    assertThat(result.exportTriggered).isTrue()
    assertThat(result.decisionId).isNull()
    assertThat(result.clarificationId).isNull()
    assertThat(result.progression.status).isEqualTo("READY_FOR_EXPORT")
    assertThat(result.progression.currentStep).isEqualTo(FlowStepType.REVIEW.name)
    assertThat(result.progression.primaryAction.type).isEqualTo("OPEN_EXPORT")
    assertThat(result.progression.steps.first { it.step == FlowStepType.REVIEW.name }.finalVisibleStep).isTrue()
    assertThat(result.action.type).isEqualTo("OPEN_EXPORT")
    assertThat(decisionService.listDecisions(project.project.id)).isEmpty()
    assertThat(clarificationService.listClarifications(project.project.id)).isEmpty()
    assertThat(projectService.readSpecFile(project.project.id, "spec.md"))
        .isEqualTo("# Product Specification\n\nDone.")
    assertThat(agent.calls).hasSize(2)
    for (call in agent.calls) {
        assertThat(call.userPrompt).doesNotContain("MANDATORY OUTPUT REQUIREMENT")
        assertThat(call.userPrompt).doesNotContain("Err on the side of including a marker")
    }
    Unit
}
```

Update `complete final step instructs spec agent to reference design artifact when present` to use `FlowStepType.REVIEW` as `currentStep`, include `FlowStepType.FRONTEND` in completed steps, and complete `REVIEW` with `fields = mapOf("confirmed" to true)`.

Replace `library completion opens export at MVP` with a test asserting MVP advances to REVIEW:

```kotlin
assertThat(result.nextStep).isEqualTo(FlowStepType.REVIEW)
assertThat(result.exportTriggered).isFalse()
assertThat(projectService.readSpecFile(project.project.id, "spec.md")).isNull()
```

Replace `api completion opens export at BACKEND` with equivalent assertions for `BACKEND -> REVIEW`.

- [ ] **Step 8: Run backend tests and confirm failures are now implementation-specific**

Run:

```bash
cd backend && ./gradlew test --tests 'com.agentwork.productspecagent.service.WizardProgressionPolicyTest' --tests 'com.agentwork.productspecagent.service.WizardStepCompletionServiceTest' --tests 'com.agentwork.productspecagent.service.ProjectServiceTest'
```

Expected before implementation is complete: failures around terminal logic or missing `REVIEW` handling. After Steps 3-7, expected: PASS.

- [ ] **Step 9: Keep final review prompt marker-free**

Update the `isLastStep` branch in `buildWizardStepFeedbackPrompt` in `WizardStepCompletion.kt` so the final review prompt is marker-free:

```kotlin
if (isLastStep) {
    return buildString {
        appendLine("The user just completed the FINAL wizard step: $step")
        appendLine("Their input for this step:")
        appendLine(fieldsDescription)
        appendLine()
        appendLine("This is the last step of the wizard. The specification is now complete.")
        appendLine("Provide a short closing message (2-3 sentences) acknowledging the final confirmation.")
        appendLine("Do NOT ask for clarifications. Do NOT propose decisions. Do NOT emit any markers.")
    }
}
```

Keep this branch marker-free and make sure only `REVIEW` reaches it as the terminal step.

- [ ] **Step 10: Commit backend changes**

Run:

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogDefaults.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicyTest.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt
git commit -m "feat(wizard): add final review progression"
```

## Task 3: Frontend Review Step

**Files:**

- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/category-step-config.ts`
- Modify: `frontend/src/lib/stores/wizard-store.ts`
- Modify: `frontend/src/components/wizard/WizardForm.tsx`
- Create: `frontend/src/components/wizard/steps/ReviewForm.tsx`

- [ ] **Step 1: Add REVIEW to frontend types and step config**

In `frontend/src/lib/api.ts`, update `StepType`:

```ts
export type StepType = "IDEA" | "PROBLEM" | "FEATURES" | "MVP" | "DESIGN"
  | "ARCHITECTURE" | "BACKEND" | "FRONTEND" | "REVIEW";
```

In `frontend/src/lib/category-step-config.ts`, update `ALL_STEP_KEYS`:

```ts
export const ALL_STEP_KEYS = [
  "IDEA", "PROBLEM", "FEATURES", "MVP", "DESIGN",
  "ARCHITECTURE", "BACKEND", "FRONTEND", "REVIEW",
] as const;
```

Add helper:

```ts
function withReviewStep(steps: StepType[]): StepType[] {
  return [...steps.filter((step) => step !== "REVIEW"), "REVIEW"];
}
```

Wrap every `visibleSteps` value:

```ts
visibleSteps: withReviewStep([...BASE_STEPS, "DESIGN", "ARCHITECTURE", "BACKEND", "FRONTEND"]),
```

For Library:

```ts
visibleSteps: withReviewStep([...BASE_STEPS]),
```

In `getVisibleStepsFromCatalog`, guarantee catalog values also end in review:

```ts
return withReviewStep(
  catalog.categories.find((entry) => entry.id === category)?.visibleSteps ?? getVisibleSteps(category),
);
```

In `getVisibleSteps`, return `withReviewStep(...)` for fallback values too.

- [ ] **Step 2: Register REVIEW in the wizard store**

In `frontend/src/lib/stores/wizard-store.ts`, append:

```ts
{ key: "REVIEW", label: "Review" },
```

to `WIZARD_STEPS`.

Add a dedicated review branch before the generic save path in `completeStep`:

```ts
if (step === "REVIEW") {
  const chatMessage = ["**Review**", "", "Finale Zusammenfassung geprueft und bestaetigt."].join("\n");
  const userMsg = {
    id: `wizard-${Date.now()}`,
    role: "user" as const,
    content: chatMessage,
    timestamp: Date.now(),
  };
  useProjectStore.setState((s) => ({
    messages: [...s.messages, userMsg],
    chatSending: true,
  }));

  set({ chatPending: true, saving: true });
  try {
    const locale = typeof navigator !== "undefined" ? navigator.language : "de";
    const response = await completeWizardStep(projectId, {
      step,
      fields: { confirmed: true },
      locale,
    });
    const refreshedData = await getWizardData(projectId).catch(() => null);
    const agentMsg = {
      id: `wizard-agent-${Date.now()}`,
      role: "agent" as const,
      content: response.message,
      timestamp: Date.now(),
    };
    useProjectStore.setState((s) => ({
      messages: [...s.messages, agentMsg],
      chatSending: false,
    }));
    set({
      data: refreshedData ?? get().data,
      progression: response.progression ?? get().progression,
      saving: false,
      chatPending: false,
    });
    return { exportTriggered: response.action?.type === "OPEN_EXPORT" || response.exportTriggered };
  } catch (err) {
    const errMsg = {
      id: `wizard-err-${Date.now()}`,
      role: "system" as const,
      content: `Fehler: ${err instanceof Error ? err.message : "Agent konnte nicht antworten"}`,
      timestamp: Date.now(),
    };
    useProjectStore.setState((s) => ({
      messages: [...s.messages, errMsg],
      chatSending: false,
    }));
    set({ saving: false, chatPending: false });
    return null;
  }
}
```

- [ ] **Step 3: Create ReviewForm**

Create `frontend/src/components/wizard/steps/ReviewForm.tsx`:

```tsx
"use client";

import { CheckCircle2, Layers, Monitor, Server, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { useWizardStore, selectFeatures } from "@/lib/stores/wizard-store";
import type { WizardFeature } from "@/lib/api";

function text(value: unknown): string {
  if (typeof value === "string") return value.trim();
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return "";
}

function list(value: unknown): string[] {
  return Array.isArray(value) ? value.map(text).filter(Boolean) : [];
}

function SummarySection({
  title,
  icon,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-md border border-border bg-card/60 p-4">
      <div className="mb-3 flex items-center gap-2">
        <div className="flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-primary">
          {icon}
        </div>
        <h2 className="text-sm font-semibold text-foreground">{title}</h2>
      </div>
      {children}
    </section>
  );
}

function FieldRow({ label, value }: { label: string; value: unknown }) {
  const rendered = Array.isArray(value) ? list(value).join(", ") : text(value);
  if (!rendered) return null;
  return (
    <div className="grid gap-1 border-t border-border/70 py-2 first:border-t-0 sm:grid-cols-[150px_1fr]">
      <div className="text-xs font-medium text-muted-foreground">{label}</div>
      <div className="text-sm text-foreground">{rendered}</div>
    </div>
  );
}

function FeatureSummary({ features }: { features: WizardFeature[] }) {
  if (features.length === 0) {
    return <p className="text-sm text-muted-foreground">Keine Features erfasst.</p>;
  }

  return (
    <div className="space-y-3">
      {features.map((feature) => (
        <div key={feature.id} className="rounded-md border border-border/70 p-3">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold text-foreground">{feature.title || "Unbenanntes Feature"}</h3>
            {feature.scopes?.map((scope) => (
              <Badge key={scope} variant="secondary">{scope}</Badge>
            ))}
          </div>
          {feature.description && (
            <p className="mt-2 text-sm text-muted-foreground">{feature.description}</p>
          )}
          {feature.acceptanceCriteria?.length > 0 && (
            <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
              {feature.acceptanceCriteria.map((criterion) => (
                <li key={criterion.id}>{criterion.text}</li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </div>
  );
}

export function ReviewForm() {
  const { data, visibleSteps } = useWizardStore();
  const features = useWizardStore(selectFeatures);
  const visible = new Set(visibleSteps().map((step) => step.key));
  const idea = data?.steps.IDEA?.fields ?? {};
  const problem = data?.steps.PROBLEM?.fields ?? {};
  const mvp = data?.steps.MVP?.fields ?? {};
  const design = data?.steps.DESIGN?.fields ?? {};
  const architecture = data?.steps.ARCHITECTURE?.fields ?? {};
  const backend = data?.steps.BACKEND?.fields ?? {};
  const frontend = data?.steps.FRONTEND?.fields ?? {};

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <div className="rounded-md border border-primary/20 bg-primary/5 p-4">
        <div className="flex items-start gap-3">
          <CheckCircle2 className="mt-0.5 h-5 w-5 text-primary" />
          <div>
            <h2 className="text-sm font-semibold text-foreground">Finaler Review</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Pruefe die Zusammenfassung. Wenn etwas fehlt, springe ueber die Step-Navigation zurueck.
            </p>
          </div>
        </div>
      </div>

      <SummarySection title="Idee" icon={<Sparkles className="h-4 w-4" />}>
        <FieldRow label="Produktname" value={idea.productName} />
        <FieldRow label="Kategorie" value={idea.category} />
        <FieldRow label="Vision" value={idea.vision} />
      </SummarySection>

      <SummarySection title="Problem & Zielgruppe" icon={<Layers className="h-4 w-4" />}>
        <FieldRow label="Kernproblem" value={problem.coreProblem} />
        <FieldRow label="Zielgruppe" value={problem.primaryAudience} />
        <FieldRow label="Pain Points" value={problem.painPoints} />
      </SummarySection>

      <SummarySection title="Features" icon={<Layers className="h-4 w-4" />}>
        <FeatureSummary features={features} />
      </SummarySection>

      <SummarySection title="MVP" icon={<CheckCircle2 className="h-4 w-4" />}>
        <FieldRow label="Ziel" value={mvp.goal} />
        <FieldRow label="MVP Features" value={mvp.mvpFeatures} />
        <FieldRow label="Erfolgskriterien" value={mvp.successCriteria} />
      </SummarySection>

      {visible.has("DESIGN") && (
        <SummarySection title="Design" icon={<Monitor className="h-4 w-4" />}>
          <FieldRow label="Beschreibung" value={design.description} />
          <FieldRow label="Aktives Design" value={design.activeDesignTitle} />
        </SummarySection>
      )}

      {visible.has("ARCHITECTURE") && (
        <SummarySection title="Architektur" icon={<Server className="h-4 w-4" />}>
          <FieldRow label="Architektur" value={architecture.architecture} />
          <FieldRow label="Datenbank" value={architecture.database} />
          <FieldRow label="Deployment" value={architecture.deployment} />
        </SummarySection>
      )}

      {visible.has("BACKEND") && (
        <SummarySection title="Backend" icon={<Server className="h-4 w-4" />}>
          <FieldRow label="Framework" value={backend.framework} />
          <FieldRow label="API Stil" value={backend.apiStyle} />
          <FieldRow label="Auth" value={backend.auth} />
        </SummarySection>
      )}

      {visible.has("FRONTEND") && (
        <SummarySection title="Frontend" icon={<Monitor className="h-4 w-4" />}>
          <FieldRow label="Framework" value={frontend.framework} />
          <FieldRow label="UI Library" value={frontend.uiLibrary} />
          <FieldRow label="Styling" value={frontend.styling} />
          <FieldRow label="Theme" value={frontend.theme} />
        </SummarySection>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Register ReviewForm in WizardForm**

In `frontend/src/components/wizard/WizardForm.tsx`, add imports:

```tsx
import { CheckCircle2 } from "lucide-react";
import { ReviewForm } from "./steps/ReviewForm";
```

If `CheckCircle2` is added to the existing lucide import list, do not create a second lucide import.

Add to `FORM_MAP`:

```ts
REVIEW: ReviewForm,
```

Add to `STEP_HELP`:

```ts
REVIEW: "Pruefe die Zusammenfassung und bestaetige die Spezifikation fuer den Export.",
```

Add to `StepIcon`:

```tsx
case "REVIEW":
  return <CheckCircle2 className={iconClass} />;
```

Change the primary button label logic so `REVIEW` shows final confirmation before export:

```tsx
{wizardDone ? (
  <><Download size={14} /> Exportieren</>
) : activeStep === "REVIEW" ? (
  <><CheckCircle2 size={14} /> Final bestaetigen</>
) : isLast ? (
  <><Save size={14} /> Abschliessen</>
) : (
  <>Weiter <ArrowRight size={14} /></>
)}
```

- [ ] **Step 5: Run frontend verification**

Run:

```bash
cd frontend && npm run lint
```

Expected: PASS.

Run:

```bash
cd frontend && npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit frontend changes**

Run:

```bash
git add frontend/src/lib/api.ts \
  frontend/src/lib/category-step-config.ts \
  frontend/src/lib/stores/wizard-store.ts \
  frontend/src/components/wizard/WizardForm.tsx \
  frontend/src/components/wizard/steps/ReviewForm.tsx
git commit -m "feat(wizard): add review step UI"
```

## Task 4: End-To-End Verification And Feature Done Doc

**Files:**

- Create: `docs/features/48-final-review-step-done.md`

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
cd backend && ./gradlew test --tests 'com.agentwork.productspecagent.service.WizardProgressionPolicyTest' --tests 'com.agentwork.productspecagent.service.WizardStepCompletionServiceTest' --tests 'com.agentwork.productspecagent.service.ProjectServiceTest'
```

Expected: PASS.

- [ ] **Step 2: Run full backend test suite**

Run:

```bash
cd backend && ./gradlew test
```

Expected: PASS.

- [ ] **Step 3: Run frontend checks**

Run:

```bash
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: both PASS.

- [ ] **Step 4: Write the done file**

Create `docs/features/48-final-review-step-done.md`:

```markdown
# Feature 48: Final Review Step - Done

## Zusammenfassung der Implementierung

- Neuer Wizard-Step `REVIEW` als explizites finales Review-Gate.
- Jede Projektkategorie endet mit `REVIEW`.
- Fachliche Steps navigieren zum Review statt direkt Export zu oeffnen.
- `ReviewForm` zeigt eine read-only Zusammenfassung der Wizard-Daten.
- `Final bestaetigen` erzeugt die finale `spec.md` und aktiviert Export.

## Abweichungen vom Plan

- Keine.

## Offene Fragen oder technische Schulden

- Kein Inline-Feature-Hinzufuegen im Review-Step; bewusst ausserhalb dieses Scopes.

## Validierung

- `cd backend && ./gradlew test`
- `cd frontend && npm run lint`
- `cd frontend && npm run build`
```

- [ ] **Step 5: Commit final docs**

Run:

```bash
git add docs/features/48-final-review-step-done.md
git commit -m "docs: mark final review step done"
```

- [ ] **Step 6: Final status check**

Run:

```bash
git status --short
```

Expected: no unrelated changes from this feature left unstaged. If user changes are present, leave them untouched and report them.

## Self-Review Notes

- Spec coverage: the plan covers the new `REVIEW` step, category-independent terminal behavior, read-only summary UI, final confirmation, export activation, no inline feature add, and tests.
- Placeholder scan: no unresolved plan markers are present.
- Type consistency: backend uses `FlowStepType.REVIEW`; frontend uses `"REVIEW"` in `StepType`, `ALL_STEP_KEYS`, `WIZARD_STEPS`, and `FORM_MAP`.
