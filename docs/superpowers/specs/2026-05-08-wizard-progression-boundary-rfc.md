# RFC: Deepen Wizard Completion and Progression Boundary

## Problem

Wizard completion is a single product workflow, but its rules are split across backend services, backend controllers, and frontend store logic.

The strongest current mismatch is category-specific finality:

- Frontend `category-step-config.ts` defines visible steps per product category.
- Backend `WizardStepCompletionService` currently uses `FlowStepType.entries` for next-step and final-step decisions.
- `Library` should finish at `MVP`, `CLI Tool` at `ARCHITECTURE`, `API` at `BACKEND`, and SaaS/Mobile/Desktop at `FRONTEND`.
- `WizardForm.tsx` infers completion from local visible steps plus `flowState`.
- The backend response still exposes `nextStep` and `exportTriggered`, which forces the frontend to combine backend and local heuristics.

Completion also has several side effects that belong to one workflow boundary:

- save current wizard input
- build agent prompt and locale instruction
- parse agent markers
- create decisions and clarifications
- sync feature tasks for the `FEATURES` step
- write per-step spec markdown
- advance flow state
- generate final `spec.md`
- decide whether export should open

Understanding "what happens when a wizard step completes" currently requires reading `WizardStepCompletion.kt`, `WizardChatController.kt`, `ProjectService.kt`, `DesignBundleController.kt`, `wizard-store.ts`, `WizardForm.tsx`, and `category-step-config.ts`.

## Proposed Interface

Introduce a backend-owned `WizardProgression` boundary. The frontend may still own presentation labels/components, but the backend owns progression semantics.

Recommended hybrid interface:

```kotlin
interface WizardProgression {
    fun snapshot(projectId: String): WizardProgressionView
    suspend fun complete(command: CompleteWizardStep): WizardCompletionResult
}
```

Use existing `FlowStepType` for the first iteration. It is still the canonical persisted enum and is already used by tasks, decisions, specs, and flow state. Avoid a dynamic `WizardStepKey` model until the product actually needs custom steps.

```kotlin
data class WizardProgressionView(
    val category: ProductCategory?,
    val steps: List<WizardStepView>,
    val currentStep: FlowStepType?,
    val status: WizardStatus,
    val primaryAction: WizardPrimaryAction,
)

data class WizardStepView(
    val step: FlowStepType,
    val status: FlowStepStatus,
    val visible: Boolean = true,
    val finalVisibleStep: Boolean = false,
)

enum class WizardStatus {
    IN_PROGRESS,
    READY_FOR_EXPORT,
}

sealed interface WizardPrimaryAction {
    data class CompleteStep(val step: FlowStepType) : WizardPrimaryAction
    data object OpenExport : WizardPrimaryAction
    data object None : WizardPrimaryAction
}

sealed interface WizardClientAction {
    data class ShowStep(val step: FlowStepType) : WizardClientAction
    data object OpenExport : WizardClientAction
    data object Stay : WizardClientAction
}

data class WizardCreatedArtifacts(
    val decisionIds: List<String> = emptyList(),
    val clarificationIds: List<String> = emptyList(),
)

data class WizardCompletionResult(
    val message: String,
    val progression: WizardProgressionView,
    val action: WizardClientAction,
    val artifacts: WizardCreatedArtifacts = WizardCreatedArtifacts(),
)
```

Add a backend category model matching the current frontend wire values:

```kotlin
enum class ProductCategory(val wireValue: String) {
    SAAS("SaaS"),
    MOBILE_APP("Mobile App"),
    CLI_TOOL("CLI Tool"),
    LIBRARY("Library"),
    DESKTOP_APP("Desktop App"),
    API("API");

    companion object {
        fun fromWire(value: String?): ProductCategory? =
            entries.firstOrNull { it.wireValue == value }
    }
}
```

## Internal Design

Split deterministic progression from side-effectful completion.

```kotlin
interface WizardProgressionPolicy {
    fun planFor(wizardData: WizardData): WizardProgressionPlan
}

data class WizardProgressionPlan(
    val category: ProductCategory?,
    val visibleSteps: List<FlowStepType>,
) {
    fun isTerminal(step: FlowStepType): Boolean = visibleSteps.lastOrNull() == step

    fun nextAfter(step: FlowStepType): FlowStepType? =
        visibleSteps.dropWhile { it != step }.drop(1).firstOrNull()
}
```

The plan must use these first-iteration visible steps:

```kotlin
Library: IDEA, PROBLEM, FEATURES, MVP
CLI Tool: IDEA, PROBLEM, FEATURES, MVP, ARCHITECTURE
API: IDEA, PROBLEM, FEATURES, MVP, ARCHITECTURE, BACKEND
SaaS: IDEA, PROBLEM, FEATURES, MVP, DESIGN, ARCHITECTURE, BACKEND, FRONTEND
Mobile App: IDEA, PROBLEM, FEATURES, MVP, DESIGN, ARCHITECTURE, BACKEND, FRONTEND
Desktop App: IDEA, PROBLEM, FEATURES, MVP, DESIGN, ARCHITECTURE, BACKEND, FRONTEND
Unknown/no category: all FlowStepType entries
```

`WizardProgressionService.complete(...)` should own:

- load wizard data and resolve category
- validate requested step belongs to the visible plan
- derive terminal step from `WizardProgressionPlan.isTerminal(step)`
- run current agent prompt flow
- suppress markers on terminal step
- create decision and clarification artifacts for non-terminal steps
- sync feature tasks on `FEATURES`
- update `FlowState` across visible steps only
- save per-step spec file
- generate final `spec.md` when terminal
- return `WizardClientAction.ShowStep(nextStep)` or `WizardClientAction.OpenExport`

`DesignBundleController.complete(...)` should eventually become an adapter into the same boundary, not a separate direct call to `ProjectService.advanceStep`.

## Backend Usage

Existing endpoint can stay initially for compatibility:

```kotlin
@PostMapping("/{id}/agent/wizard-step-complete")
fun wizardStepComplete(
    @PathVariable id: String,
    @RequestBody request: WizardStepCompleteRequest,
): ResponseEntity<WizardStepCompleteResponse> {
    val step = runCatching { FlowStepType.valueOf(request.step) }
        .getOrElse { return ResponseEntity.badRequest().build() }

    val result = runBlocking {
        wizardProgression.complete(
            CompleteWizardStep(
                projectId = id,
                step = step,
                fields = request.fields,
                locale = request.locale,
            )
        )
    }

    return ResponseEntity.ok(result.toResponseDto())
}
```

Add a read endpoint for the frontend to stop computing completion locally:

```kotlin
@GetMapping("/{id}/wizard/progression")
fun wizardProgression(@PathVariable id: String): WizardProgressionView =
    wizardProgression.snapshot(id)
```

## Frontend Usage

Frontend should render active steps from `WizardProgressionView.steps`.

Completion handling should follow explicit server actions:

```ts
const result = await completeWizardStep(projectId, {
  step: activeStep,
  fields: plainFields,
  locale,
});

set({ progression: result.progression });

if (result.action.type === "SHOW_STEP") {
  set({ activeStep: result.action.step });
}

if (result.action.type === "OPEN_EXPORT") {
  onExportClick?.();
}
```

`WizardForm.tsx` should no longer infer finality from:

- `isLast`
- `lastStepCompleted`
- `flowState`
- `exportTriggered`

It should render the button from `progression.primaryAction`.

## Dependency Strategy

Dependency categories:

- **In-process:** `WizardProgressionPolicy`, category profile mapping, terminal-step calculation, client action selection.
- **Local-substitutable:** project/wizard/spec/flow persistence, decisions, clarifications, feature task sync. These can be tested with `InMemoryObjectStore`.
- **True external:** LLM/Koog agent calls. Keep `WizardCompletionAgent` as a port and use fake agent responses in boundary tests.
- **Remote but owned:** frontend/backend HTTP contract. Keep controllers and TypeScript API as adapters around the backend boundary.

## Testing Strategy

New boundary tests should verify:

- `Library` completes at `MVP` and returns `OpenExport`.
- `CLI Tool` completes at `ARCHITECTURE` and returns `OpenExport`.
- `API` completes at `BACKEND` and returns `OpenExport`.
- `SaaS` still completes at `FRONTEND`.
- non-terminal marker responses create decision/clarification artifacts.
- terminal marker responses suppress decision/clarification artifacts.
- `FEATURES` completion still syncs feature tasks.
- `snapshot(projectId)` returns visible steps and `primaryAction` from backend policy.

Old tests to shrink or replace:

- final-step assumptions inside `WizardStepCompletionServiceTest`
- controller tests that assert only old `nextStep/exportTriggered`
- frontend wizard completion heuristics around `wizardDone`

## Implementation Recommendation

Do this incrementally:

1. Add pure `WizardProgressionPolicy` and test category plans.
2. Add `WizardProgression` facade around existing `WizardStepCompletionService` behavior.
3. Change backend final-step calculation to use the policy.
4. Extend response DTOs with `progression`, `action`, and artifacts while preserving old `nextStep`, `exportTriggered`, `decisionId`, `clarificationId` for compatibility.
5. Add frontend API/store fields for progression.
6. Remove frontend wizard finality heuristics once the new response is consumed.
7. Route design completion through the same boundary in a later task if the first migration would otherwise become too large.
