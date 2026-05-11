# Wizard Blocker Apply Agent Design

## Context

The wizard currently blocks a step when the step-completion agent creates a Decision or Clarification. After the user resolves the blocker, clicking **Weiter** calls the normal step-completion agent again. That second call can only be made safe by ignoring repeated markers after one blocker was answered, which prevents loops but does not improve the actual wizard form values.

The desired flow is: once the user has answered the blocker, a different agent should incorporate that answer into the current step's `wizard.json` fields, then advance the wizard.

## Current State

`wizard.json` is the right persistence target for this feature.

Backend:

- `WizardData.steps[STEP].fields` stores each wizard step as a flexible JSON object.
- `WizardService.saveStepData(projectId, stepName, stepData)` can write one step back to `wizard.json`.
- `WizardStepCompletionService.complete(...)` owns step completion, blocker detection, and progression actions.
- `Decision` and `Clarification` currently store `stepType`, status, the user answer or choice, and timestamps. They do not store a target field or applied state.

Frontend:

- `useWizardStore.data` reads from `wizard.json`.
- Forms render directly from `data.steps[STEP].fields`.
- The step blocker UI already disables **Weiter** while there are open blockers for the active step.

Context7 findings:

- Kotlinx Serialization supports flexible persisted JSON through `JsonElement` and `JsonObject`, and default values make new nullable fields backward-compatible for existing JSON files.
- Zustand async actions should update state after awaited API calls and use immutable state replacement, which matches the existing `wizard-store.ts` pattern.

## Goals

1. After all blockers for the active step are answered, clicking **Weiter** should call an apply-focused agent, not the normal completion agent.
2. The apply agent should update the current step fields in `wizard.json`.
3. The wizard should then advance to the next visible step.
4. The apply flow must not create new Decisions or Clarifications.
5. A resolved blocker must be applied at most once.

## Non-Goals

- No diff preview or manual confirmation UI.
- No broad migration of old projects.
- No new dynamic form schema system.
- No automatic edits outside the current wizard step.

## User Flow

1. User completes a wizard step and clicks **Weiter**.
2. Normal step-completion agent runs.
3. If it emits a Decision or Clarification marker, the backend creates the blocker and keeps the step in progress.
4. The UI shows the blocker warning and disables **Weiter**.
5. User resolves every open blocker for that step.
6. User clicks **Weiter** again.
7. Backend detects answered but unapplied blockers for the current step.
8. Backend calls the apply agent with current fields plus those answered blockers.
9. Backend validates and writes returned field updates into `wizard.json`.
10. Backend marks those blockers as applied.
11. Backend completes the step and returns the normal next-step progression action.

## Data Model

Extend both blocker models with backward-compatible fields:

```kotlin
val appliedAt: String? = null
val appliedFields: List<String> = emptyList()
```

For `Decision`, `appliedAt` means the chosen option and rationale have been incorporated into wizard fields.

For `Clarification`, `appliedAt` means the answer has been incorporated into wizard fields.

Existing persisted blocker JSON remains valid because both fields have defaults.

## Apply Agent Contract

Add a new backend port, separate from the current step-completion agent:

```kotlin
interface WizardBlockerApplyAgent {
    suspend fun apply(command: ApplyWizardBlockers): WizardBlockerApplyResult
}
```

The agent receives:

- project id
- current step
- current step field JSON
- allowed field names for that step
- answered unapplied Decisions for that step
- answered unapplied Clarifications for that step
- locale

The agent returns JSON only:

```json
{
  "message": "Ich habe die Klaerung in die Zielgruppe eingearbeitet.",
  "fieldUpdates": {
    "primaryAudience": "Product Owner in kleinen B2B-SaaS-Teams"
  }
}
```

Rules:

- `fieldUpdates` may be empty.
- Keys outside the allowed field set are discarded.
- The agent cannot emit blocker markers.
- Invalid JSON falls back to no field updates and a short safe message.

## Field Schema

The backend should own a small static allowed-field map for apply validation, rather than relying on frontend labels.

Initial allowed fields:

```kotlin
IDEA: productName, vision, category
PROBLEM: coreProblem, primaryAudience, painPoints
FEATURES: features, edges
MVP: goal, mvpFeatures, successCriteria
DESIGN: summary, bundleName, pageCount
ARCHITECTURE: architecture, database, deployment, notes
BACKEND: framework, apiStyle, auth
FRONTEND: framework, uiLibrary, styling, theme
REVIEW: confirmed
```

Note: the frontend label map currently contains `MVP.mvpGoal`, while `MvpForm` stores `MVP.goal`. The apply schema must use the actual persisted key, `goal`.

For the first implementation, complex `FEATURES.features` graph edits can be allowed but conservative: the agent may update existing feature text fields, but should not be trusted to restructure graph edges unless tests explicitly cover it. The backend should still validate only at field-key level in this feature.

## Backend Design

`WizardStepCompletionService.complete(...)` gains a branch after current-step validation and open-blocker checks:

1. Load answered unapplied blockers for `command.step`.
2. If any exist, run `WizardBlockerApplyAgent`.
3. Merge valid `fieldUpdates` into the current step fields.
4. Save the merged step to `wizard.json` with `completedAt = now`.
5. Mark applied blockers with `appliedAt = now` and `appliedFields`.
6. Continue with the existing step completion/progression update, but skip the normal completion agent call.

The existing open-blocker short circuit remains before this branch. Open blockers still block and do not call any agent.

The existing "answered blocker prevents repeated marker loop" behavior can be replaced by applied-state handling:

- open blockers: block immediately
- answered unapplied blockers: call apply agent once, then complete
- answered applied blockers: ignore for future apply detection

Because the apply branch does not call the marker-producing agent, it cannot create a new blocker loop.

## Frontend Design

No new UI is required for the selected Variant A.

Small store behavior change:

- After `completeWizardStep(...)` returns, if the response contains updated wizard data or if an apply action occurred, refresh `getWizardData(projectId)` before navigating.
- This ensures the next rendered step and any later review reflects the modified `wizard.json` values.

The API response can either include updated `WizardData` or the frontend can fetch it. Fetching is simpler and follows the existing store pattern.

## API Shape

Keep the existing `POST /api/v1/projects/{id}/agent/wizard-step-complete` endpoint.

Extend `WizardStepCompleteResponse` minimally:

```kotlin
val appliedDecisionIds: List<String> = emptyList()
val appliedClarificationIds: List<String> = emptyList()
val wizardDataChanged: Boolean = false
```

The frontend uses `wizardDataChanged` to refresh wizard data after completion.

## Prompt and Agent Registry

Add a prompt definition:

- id: `wizard-blocker-apply-system`
- agent: `WizardBlockerApply`
- resource: `/prompts/wizard-blocker-apply-system.md`

Add an agent model id:

- `wizard-blocker-apply`

Default tier should be `MEDIUM`, matching the current decision-oriented agent tier unless project configuration says otherwise.

## Error Handling

- If the apply agent returns invalid JSON, complete the step without field updates and mark blockers applied with an empty `appliedFields` list.
- If the apply agent returns unknown fields, ignore those fields and apply the valid subset.
- If saving `wizard.json` or blocker state fails, do not advance the step.
- If there are still open blockers, do not call the apply agent.

## Testing

Backend tests:

- answered Clarification triggers apply agent, updates the expected wizard field, marks the clarification applied, and advances to the next step
- resolved Decision triggers apply agent, updates the expected wizard field, marks the decision applied, and advances
- open blocker does not call either agent and stays on the current step
- answered already-applied blocker is not applied twice
- apply agent returning an unknown field does not write that field
- invalid apply-agent JSON still completes the step and marks the blocker applied with no field updates

Frontend tests or targeted store tests:

- completion response with `wizardDataChanged = true` refreshes wizard data before navigation finishes
- blocker stores still load new Decisions/Clarifications before `chatPending` is cleared

## Acceptance Criteria

1. Resolving a Clarification and clicking **Weiter** updates the relevant current-step field in `wizard.json`.
2. Resolving a Decision and clicking **Weiter** updates the relevant current-step field in `wizard.json`.
3. After apply, the wizard advances to the next visible step.
4. The normal marker-producing completion agent is not called in the apply path.
5. No new Decision or Clarification can be created during the apply path.
6. Repeated clicks do not apply the same answered blocker more than once.
7. Existing projects with old Decision/Clarification JSON continue to load.

## Open Implementation Notes

- The apply agent should receive field labels in addition to field keys for better output quality, but validation must use keys.
- `FEATURES` graph edits are higher risk than simple scalar fields; first implementation should keep tests focused on scalar fields and existing feature subfields before allowing broader graph changes.
- The final review step should include the post-apply wizard data because `wizard.json` remains the source of truth.
