# Wizard Step Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move wizard step completion behind a stable use-case boundary without changing the HTTP contract.

**Architecture:** Add a `WizardStepCompletion` service boundary and a `WizardCompletionAgent` LLM port. The controller validates transport input and maps the use-case result back to the existing `WizardStepCompleteResponse`.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, AssertJ, coroutine tests via `runBlocking`.

---

### Task 1: Boundary Service

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`

- [x] Write a failing boundary test that completes `IDEA`, advances flow to `PROBLEM`, writes `idea.md`, and captures the LLM prompts.
- [x] Implement `CompleteWizardStep`, `WizardStepCompletionResult`, `WizardStepCompletion`, `WizardCompletionAgent`, `KoogWizardCompletionAgent`, and `WizardStepCompletionService`.
- [x] Run `cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest`.

### Task 2: Marker Side Effects

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`

- [x] Add tests for non-final clarification markers and final-step marker suppression.
- [x] Keep marker cleanup user-facing and create decisions/clarifications only for non-final steps.
- [x] Run the focused service test again.

### Task 3: Controller Migration

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardChatController.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardChatControllerTest.kt`

- [x] Change the controller dependency from `IdeaToSpecAgent` to `WizardStepCompletion`.
- [x] Keep invalid step validation as HTTP `400`.
- [x] Update controller tests to stub the new boundary instead of subclassing the agent.
- [x] Run `cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.WizardChatControllerTest`.

### Task 4: Legacy Agent Cleanup

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgentTest.kt`

- [x] Remove `processWizardStep` and wizard-only prompt construction from `IdeaToSpecAgent`.
- [x] Move marker parsing into a reusable helper used by both chat and wizard completion.
- [x] Preserve chat tests and move wizard tests to the service boundary.
- [x] Run the affected agent and service tests.

### Task 5: Verification

- [x] Run `cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest --tests com.agentwork.productspecagent.api.WizardChatControllerTest --tests com.agentwork.productspecagent.agent.IdeaToSpecAgentTest`.
- [x] Run `git diff --check`.
