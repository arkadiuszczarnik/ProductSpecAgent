# RFC: Deepen Wizard Step Completion

## Problem

Wizard step completion is one user-facing workflow, but the implementation is spread across several shallow modules:

- `WizardChatController` validates the raw step string and calls the agent directly.
- `IdeaToSpecAgent.processWizardStep` builds prompts, calls the LLM, parses markers, creates decisions and clarifications, advances flow state, writes spec files, syncs feature tasks, and generates the final summary.
- `SpecContextBuilder`, `ProjectService`, `WizardService`, `DecisionService`, `ClarificationService`, and `TaskService` all participate in the same completion transaction.

This creates integration risk in the ordering between prompt response, marker parsing, persistence, flow advancement, and derived artifacts. Tests currently subclass or spy lower-level pieces instead of asserting the workflow at a stable boundary.

## Proposed Interface

Introduce a deep use-case boundary:

```kotlin
interface WizardStepCompletion {
    suspend fun complete(command: CompleteWizardStep): WizardStepCompletionResult
}

data class CompleteWizardStep(
    val projectId: String,
    val step: FlowStepType,
    val fields: Map<String, Any>,
    val locale: String = "en",
)

data class WizardStepCompletionResult(
    val message: String,
    val nextStep: FlowStepType?,
    val exportTriggered: Boolean,
    val decisionId: String? = null,
    val clarificationId: String? = null,
)
```

Controller usage should become transport-only:

```kotlin
val step = runCatching { FlowStepType.valueOf(request.step) }
    .getOrElse { return ResponseEntity.badRequest().build() }

val result = wizardStepCompletion.complete(
    CompleteWizardStep(
        projectId = id,
        step = step,
        fields = request.fields,
        locale = request.locale,
    )
)

return ResponseEntity.ok(
    WizardStepCompleteResponse(
        message = result.message,
        nextStep = result.nextStep?.name,
        exportTriggered = result.exportTriggered,
        decisionId = result.decisionId,
        clarificationId = result.clarificationId,
    )
)
```

The module owns and hides:

- Wizard data loading and context construction.
- Step-specific feedback prompt construction.
- Locale and base prompt composition.
- LLM feedback execution.
- Decision and clarification marker parsing.
- Marker cleanup for user-facing responses.
- Final-step blocker suppression.
- Feature graph parsing and derived task sync.
- Flow-state advancement.
- Step spec file writes.
- Final `spec.md` generation.

## Dependency Strategy

Use local-substitutable dependencies for project state:

- `ProjectService`
- `WizardService`
- `DecisionService`
- `ClarificationService`
- `TaskService`
- `PromptService`

Boundary tests should use real services backed by `InMemoryObjectStore` where possible.

Treat LLM execution as a true external dependency behind a narrow port:

```kotlin
interface WizardCompletionAgent {
    suspend fun respond(systemPrompt: String, userPrompt: String): String
}
```

The production adapter delegates to Koog/OpenAI. Tests provide deterministic responses and can capture prompts.

## Testing Strategy

New boundary tests should cover:

- Completing a normal step advances flow, writes the step spec file, and returns the next step.
- Decision markers create a decision artifact on non-final steps.
- Clarification markers create a clarification artifact on non-final steps.
- Final `FRONTEND` completion suppresses blocker markers and sets `exportTriggered = true`.
- `FEATURES` completion parses the graph and syncs wizard-derived feature tasks.
- Final completion generates and persists `spec.md`.

Old tests to reduce or replace:

- `IdeaToSpecAgentTest` cases that assert `processWizardStep` side effects by subclassing the agent.
- `WizardChatControllerTest` cases that mock the whole agent just to verify controller response mapping.
- Prompt-capture tests that exist only because prompt construction is mixed with persistence orchestration.

Keep thin controller tests for:

- Invalid step returns `400`.
- Valid request maps to the deep module and returns the API DTO.

## Implementation Recommendations

Implement incrementally:

1. Add `WizardStepCompletion` and a concrete service that initially moves only `processWizardStep` behavior.
2. Add boundary tests around the new service using in-memory stores and a fake `WizardCompletionAgent`.
3. Change `WizardChatController` to depend on `WizardStepCompletion`.
4. Keep `WizardStepCompleteRequest` and `WizardStepCompleteResponse` wire-compatible.
5. After controller migration, shrink `IdeaToSpecAgent` back toward chat and reusable prompt/marker helpers.

Avoid introducing preview/reprocess modes or many completion options until there is a real caller for them. The first deep module should have one public entry point and hide the workflow sequencing internally.
