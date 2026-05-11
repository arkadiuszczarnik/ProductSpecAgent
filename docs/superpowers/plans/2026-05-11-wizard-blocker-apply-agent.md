# Wizard Blocker Apply Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the second marker-producing wizard-agent call after answered Decisions/Clarifications with an apply-focused agent that writes validated field updates into `wizard.json` and then advances the step.

**Architecture:** Keep `wizard.json` as the source of truth for wizard form values. Add applied-state metadata to Decision/Clarification, a new `wizard-blocker-apply` agent port that returns structured field patches, and a branch in `WizardStepCompletionService` that applies answered blockers before normal completion. The frontend only refreshes wizard data when the backend reports that wizard data changed.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, kotlinx.serialization `JsonElement`, JetBrains Koog, JUnit 5/AssertJ, Next.js/React/Zustand TypeScript.

---

## File Structure

- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Decision.kt`
  - Add `appliedAt` and `appliedFields`.
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Clarification.kt`
  - Add `appliedAt` and `appliedFields`.
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DecisionService.kt`
  - Add `markApplied(...)`.
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/ClarificationService.kt`
  - Add `markApplied(...)`.
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardBlockerApply.kt`
  - Own the apply-agent port, Koog adapter, result parser, field schema, and value merge helpers.
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
  - Add apply branch before the normal completion-agent call.
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardChatModels.kt`
  - Add response flags for applied blockers and wizard-data refresh.
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardChatController.kt`
  - Map new result fields into API response.
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`
  - Register apply prompt.
- Create: `backend/src/main/resources/prompts/wizard-blocker-apply-system.md`
  - System prompt for the apply agent.
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt`
  - Add `wizard-blocker-apply`.
- Modify: `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-dev.yml`, `backend/src/test/resources/application.yml`
  - Add default model tier for `wizard-blocker-apply`.
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`
  - Add apply-path tests and adjust existing loop-prevention tests.
- Add: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardBlockerApplyTest.kt`
  - Test parser/field filtering in isolation.
- Modify: `frontend/src/lib/api.ts`
  - Add response fields.
- Modify: `frontend/src/lib/stores/wizard-store.ts`
  - Refresh `wizard.json` when `wizardDataChanged` is true.

---

### Task 1: Add Applied Metadata to Blockers

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Decision.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Clarification.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DecisionService.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/ClarificationService.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`

- [ ] **Step 1: Write failing test for applied Clarification metadata**

Add this test to `WizardStepCompletionServiceTest` near the existing clarification tests:

```kotlin
@Test
fun `mark clarification applied records applied fields`() {
    val project = projectService.createProject("Test")
    val clarification = clarificationService.createClarification(
        project.project.id,
        "Wer ist die Zielgruppe?",
        "Grundlage fuer alles weitere",
        FlowStepType.PROBLEM,
    )
    val answered = clarificationService.answerClarification(project.project.id, clarification.id, "Teams in KMU")

    val applied = clarificationService.markApplied(project.project.id, answered.id, listOf("primaryAudience"))

    assertThat(applied.appliedAt).isNotNull()
    assertThat(applied.appliedFields).containsExactly("primaryAudience")
    val reloaded = clarificationService.getClarification(project.project.id, answered.id)
    assertThat(reloaded.appliedAt).isEqualTo(applied.appliedAt)
    assertThat(reloaded.appliedFields).containsExactly("primaryAudience")
}
```

- [ ] **Step 2: Write failing test for applied Decision metadata**

Add this test to `WizardStepCompletionServiceTest`:

```kotlin
@Test
fun `mark decision applied records applied fields`() = runBlocking {
    val project = projectService.createProject("Test")
    val decision = decisionService.createDecision(project.project.id, "Which audience?", FlowStepType.PROBLEM)
    val resolved = decisionService.resolveDecision(project.project.id, decision.id, "opt-1", "B2B teams")

    val applied = decisionService.markApplied(project.project.id, resolved.id, listOf("primaryAudience"))

    assertThat(applied.appliedAt).isNotNull()
    assertThat(applied.appliedFields).containsExactly("primaryAudience")
    val reloaded = decisionService.getDecision(project.project.id, resolved.id)
    assertThat(reloaded.appliedAt).isEqualTo(applied.appliedAt)
    assertThat(reloaded.appliedFields).containsExactly("primaryAudience")
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest
```

Expected: FAIL because `markApplied`, `appliedAt`, and `appliedFields` do not exist.

- [ ] **Step 4: Add model fields**

In `Decision.kt`, extend `Decision`:

```kotlin
@Serializable
data class Decision(
    val id: String,
    val projectId: String,
    val stepType: FlowStepType,
    val title: String,
    val options: List<DecisionOption>,
    val recommendation: String,
    val status: DecisionStatus = DecisionStatus.PENDING,
    val chosenOptionId: String? = null,
    val rationale: String? = null,
    val createdAt: String,
    val resolvedAt: String? = null,
    val appliedAt: String? = null,
    val appliedFields: List<String> = emptyList(),
)
```

In `Clarification.kt`, extend `Clarification`:

```kotlin
@Serializable
data class Clarification(
    val id: String,
    val projectId: String,
    val stepType: FlowStepType,
    val question: String,
    val reason: String,
    val status: ClarificationStatus = ClarificationStatus.OPEN,
    val answer: String? = null,
    val createdAt: String,
    val answeredAt: String? = null,
    val appliedAt: String? = null,
    val appliedFields: List<String> = emptyList(),
)
```

- [ ] **Step 5: Add service methods**

In `DecisionService.kt`, add:

```kotlin
fun markApplied(projectId: String, decisionId: String, appliedFields: List<String>): Decision {
    val decision = storage.loadDecision(projectId, decisionId)
    val applied = decision.copy(
        appliedAt = Instant.now().toString(),
        appliedFields = appliedFields,
    )
    storage.saveDecision(applied)
    return applied
}
```

In `ClarificationService.kt`, add:

```kotlin
fun markApplied(projectId: String, clarificationId: String, appliedFields: List<String>): Clarification {
    val clarification = storage.loadClarification(projectId, clarificationId)
    val applied = clarification.copy(
        appliedAt = Instant.now().toString(),
        appliedFields = appliedFields,
    )
    storage.saveClarification(applied)
    return applied
}
```

- [ ] **Step 6: Run tests to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest
```

Expected: PASS for the two new metadata tests.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/Decision.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/domain/Clarification.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/DecisionService.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/ClarificationService.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt
git commit -m "feat(wizard): track applied blockers"
```

---

### Task 2: Add Apply Agent Contract, Parser, and Field Schema

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardBlockerApply.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardBlockerApplyTest.kt`

- [ ] **Step 1: Write failing parser and schema tests**

Create `WizardBlockerApplyTest.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WizardBlockerApplyTest {

    @Test
    fun `parseResult keeps only allowed fields`() {
        val result = WizardBlockerApplyJson.parseResult(
            raw = """
                {
                  "message": "Applied.",
                  "fieldUpdates": {
                    "primaryAudience": "B2B SaaS teams",
                    "unknown": "ignored"
                  }
                }
            """.trimIndent(),
            allowedFields = setOf("primaryAudience"),
        )

        assertThat(result.message).isEqualTo("Applied.")
        assertThat(result.fieldUpdates).containsOnlyKeys("primaryAudience")
        assertThat(result.fieldUpdates["primaryAudience"]).isEqualTo(JsonPrimitive("B2B SaaS teams"))
        assertThat(result.appliedFields).containsExactly("primaryAudience")
    }

    @Test
    fun `parseResult returns safe empty result for invalid JSON`() {
        val result = WizardBlockerApplyJson.parseResult(
            raw = "not json",
            allowedFields = setOf("coreProblem"),
        )

        assertThat(result.message).isEqualTo("Die Antwort wurde beruecksichtigt.")
        assertThat(result.fieldUpdates).isEmpty()
        assertThat(result.appliedFields).isEmpty()
    }

    @Test
    fun `allowed fields use persisted MVP goal key`() {
        assertThat(WizardStepFieldSchema.allowedFields(FlowStepType.MVP))
            .contains("goal")
            .doesNotContain("mvpGoal")
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardBlockerApplyTest
```

Expected: FAIL because `WizardBlockerApplyJson` and `WizardStepFieldSchema` do not exist.

- [ ] **Step 3: Create implementation file**

Create `WizardBlockerApply.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.KoogAgentRunner
import com.agentwork.productspecagent.domain.Clarification
import com.agentwork.productspecagent.domain.Decision
import com.agentwork.productspecagent.domain.FlowStepType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

data class ApplyWizardBlockers(
    val projectId: String,
    val step: FlowStepType,
    val fields: Map<String, JsonElement>,
    val decisions: List<Decision>,
    val clarifications: List<Clarification>,
    val locale: String,
)

data class WizardBlockerApplyResult(
    val message: String,
    val fieldUpdates: Map<String, JsonElement>,
    val appliedFields: List<String>,
)

interface WizardBlockerApplyAgent {
    suspend fun apply(command: ApplyWizardBlockers): WizardBlockerApplyResult
}

@Component
class KoogWizardBlockerApplyAgent(
    private val koogRunner: KoogAgentRunner,
    private val promptService: PromptService,
) : WizardBlockerApplyAgent {
    override suspend fun apply(command: ApplyWizardBlockers): WizardBlockerApplyResult {
        val allowedFields = WizardStepFieldSchema.allowedFields(command.step)
        val raw = koogRunner.run(AGENT_ID, promptService.get("wizard-blocker-apply-system"), buildPrompt(command, allowedFields))
        return WizardBlockerApplyJson.parseResult(raw, allowedFields)
    }

    private fun buildPrompt(command: ApplyWizardBlockers, allowedFields: Set<String>): String = buildString {
        appendLine("Project: ${command.projectId}")
        appendLine("Step: ${command.step.name}")
        appendLine("Locale: ${command.locale}")
        appendLine("Allowed fields: ${allowedFields.joinToString(", ")}")
        appendLine()
        appendLine("Current fields:")
        command.fields.forEach { (key, value) -> appendLine("- $key: ${WizardMarkdown.renderValue(value)}") }
        appendLine()
        appendLine("Resolved decisions:")
        command.decisions.forEach { decision ->
            appendLine("- ${decision.title}")
            appendLine("  chosenOptionId: ${decision.chosenOptionId ?: "none"}")
            appendLine("  chosenLabel: ${decision.options.find { it.id == decision.chosenOptionId }?.label ?: "none"}")
            appendLine("  rationale: ${decision.rationale ?: ""}")
        }
        appendLine()
        appendLine("Answered clarifications:")
        command.clarifications.forEach { clarification ->
            appendLine("- question: ${clarification.question}")
            appendLine("  reason: ${clarification.reason}")
            appendLine("  answer: ${clarification.answer ?: ""}")
        }
    }

    private companion object {
        const val AGENT_ID = "wizard-blocker-apply"
    }
}

object WizardStepFieldSchema {
    private val fieldsByStep: Map<FlowStepType, Set<String>> = mapOf(
        FlowStepType.IDEA to setOf("productName", "vision", "category"),
        FlowStepType.PROBLEM to setOf("coreProblem", "primaryAudience", "painPoints"),
        FlowStepType.FEATURES to setOf("features", "edges"),
        FlowStepType.MVP to setOf("goal", "mvpFeatures", "successCriteria"),
        FlowStepType.DESIGN to setOf("summary", "bundleName", "pageCount"),
        FlowStepType.ARCHITECTURE to setOf("architecture", "database", "deployment", "notes"),
        FlowStepType.BACKEND to setOf("framework", "apiStyle", "auth"),
        FlowStepType.FRONTEND to setOf("framework", "uiLibrary", "styling", "theme"),
        FlowStepType.REVIEW to setOf("confirmed"),
    )

    fun allowedFields(step: FlowStepType): Set<String> = fieldsByStep.getValue(step)
}

object WizardBlockerApplyJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseResult(raw: String, allowedFields: Set<String>): WizardBlockerApplyResult {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        return runCatching {
            val parsed = json.decodeFromString<ApplyAgentResponse>(cleaned)
            val updates = parsed.fieldUpdates
                .filterKeys { it in allowedFields }
            WizardBlockerApplyResult(
                message = parsed.message.ifBlank { "Die Antwort wurde beruecksichtigt." },
                fieldUpdates = updates,
                appliedFields = updates.keys.toList(),
            )
        }.getOrElse {
            WizardBlockerApplyResult(
                message = "Die Antwort wurde beruecksichtigt.",
                fieldUpdates = emptyMap(),
                appliedFields = emptyList(),
            )
        }
    }
}

@Serializable
private data class ApplyAgentResponse(
    val message: String = "Die Antwort wurde beruecksichtigt.",
    val fieldUpdates: Map<String, JsonElement> = emptyMap(),
)
```

- [ ] **Step 4: Run tests to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardBlockerApplyTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardBlockerApply.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardBlockerApplyTest.kt
git commit -m "feat(wizard): add blocker apply agent contract"
```

---

### Task 3: Integrate Apply Path Into Wizard Completion

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`

- [ ] **Step 1: Extend test fixture with fake apply agent**

In `WizardStepCompletionServiceTest.kt`, add a property:

```kotlin
private lateinit var applyAgent: CapturingApplyAgent
```

In `setup()`, initialize:

```kotlin
applyAgent = CapturingApplyAgent(
    WizardBlockerApplyResult(
        message = "Antwort wurde eingearbeitet.",
        fieldUpdates = mapOf("primaryAudience" to JsonPrimitive("B2B SaaS teams")),
        appliedFields = listOf("primaryAudience"),
    )
)
```

Add this helper class near `CapturingWizardAgent`:

```kotlin
private class CapturingApplyAgent(private val result: WizardBlockerApplyResult) : WizardBlockerApplyAgent {
    val calls = mutableListOf<ApplyWizardBlockers>()

    override suspend fun apply(command: ApplyWizardBlockers): WizardBlockerApplyResult {
        calls.add(command)
        return result
    }
}
```

Update `createCompletion(...)` to pass `applyAgent = applyAgent`.

- [ ] **Step 2: Write failing apply-path Clarification test**

Add:

```kotlin
@Test
fun `answered unapplied clarification is applied to wizard data and advances without completion agent`() = runBlocking {
    val project = projectService.createProject("Test")
    setFlowProgress(project.project.id, FlowStepType.PROBLEM, setOf(FlowStepType.IDEA))
    val clarification = clarificationService.createClarification(
        project.project.id,
        "Wer ist die Zielgruppe?",
        "Grundlage fuer alles weitere",
        FlowStepType.PROBLEM,
    )
    clarificationService.answerClarification(project.project.id, clarification.id, "B2B SaaS teams")
    val completionAgent = CapturingWizardAgent("Should not be called.")
    val completion = createCompletion(completionAgent)

    val result = completion.complete(
        CompleteWizardStep(
            projectId = project.project.id,
            step = FlowStepType.PROBLEM,
            fields = mapOf("coreProblem" to "Zu unklar", "primaryAudience" to ""),
        )
    )

    assertThat(completionAgent.calls).isEmpty()
    assertThat(applyAgent.calls).hasSize(1)
    assertThat(result.message).isEqualTo("Antwort wurde eingearbeitet.")
    assertThat(result.nextStep).isEqualTo(FlowStepType.FEATURES)
    assertThat(result.action.type).isEqualTo("SHOW_STEP")
    assertThat(result.wizardDataChanged).isTrue()
    assertThat(result.appliedClarificationIds).containsExactly(clarification.id)

    val wizardData = wizardService.getWizardData(project.project.id)
    assertThat(wizardData.steps["PROBLEM"]?.fields?.get("primaryAudience"))
        .isEqualTo(JsonPrimitive("B2B SaaS teams"))
    assertThat(clarificationService.getClarification(project.project.id, clarification.id).appliedAt).isNotNull()
}
```

- [ ] **Step 3: Write failing already-applied test**

Add:

```kotlin
@Test
fun `already applied clarification is not applied twice`() = runBlocking {
    val project = projectService.createProject("Test")
    setFlowProgress(project.project.id, FlowStepType.PROBLEM, setOf(FlowStepType.IDEA))
    val clarification = clarificationService.createClarification(
        project.project.id,
        "Wer ist die Zielgruppe?",
        "Grundlage fuer alles weitere",
        FlowStepType.PROBLEM,
    )
    val answered = clarificationService.answerClarification(project.project.id, clarification.id, "B2B SaaS teams")
    clarificationService.markApplied(project.project.id, answered.id, listOf("primaryAudience"))
    val completionAgent = CapturingWizardAgent("Looks good.")
    val completion = createCompletion(completionAgent)

    val result = completion.complete(
        CompleteWizardStep(
            projectId = project.project.id,
            step = FlowStepType.PROBLEM,
            fields = mapOf("coreProblem" to "Zu unklar", "primaryAudience" to "B2B SaaS teams"),
        )
    )

    assertThat(applyAgent.calls).isEmpty()
    assertThat(completionAgent.calls).hasSize(1)
    assertThat(result.nextStep).isEqualTo(FlowStepType.FEATURES)
}
```

- [ ] **Step 4: Run tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest
```

Expected: FAIL because `WizardStepCompletionResult` has no apply fields, constructor has no `applyAgent`, and no apply branch exists.

- [ ] **Step 5: Extend result model**

In `WizardStepCompletion.kt`, extend `WizardStepCompletionResult`:

```kotlin
data class WizardStepCompletionResult(
    val message: String,
    val nextStep: FlowStepType?,
    val exportTriggered: Boolean,
    val decisionId: String? = null,
    val clarificationId: String? = null,
    val progression: WizardProgressionView = emptyWizardProgressionView(),
    val action: WizardClientActionDto = stayClientAction,
    val artifacts: WizardCreatedArtifacts = WizardCreatedArtifacts(),
    val appliedDecisionIds: List<String> = emptyList(),
    val appliedClarificationIds: List<String> = emptyList(),
    val wizardDataChanged: Boolean = false,
)
```

- [ ] **Step 6: Inject apply agent**

In the `WizardStepCompletionService` constructor, add:

```kotlin
private val applyAgent: WizardBlockerApplyAgent,
```

Update all test calls to `WizardStepCompletionService(...)` to include `applyAgent = applyAgent`.

- [ ] **Step 7: Add helper methods**

In `WizardStepCompletionService`, replace `hasAnsweredStepBlocker(...)` with helpers that distinguish applied from unapplied:

```kotlin
private fun answeredUnappliedDecisions(projectId: String, step: FlowStepType) =
    decisionService.listDecisions(projectId).filter {
        it.stepType == step && it.status == DecisionStatus.RESOLVED && it.appliedAt == null
    }

private fun answeredUnappliedClarifications(projectId: String, step: FlowStepType) =
    clarificationService.listClarifications(projectId).filter {
        it.stepType == step && it.status == ClarificationStatus.ANSWERED && it.appliedAt == null
    }

private fun hasAnsweredStepBlocker(projectId: String, step: FlowStepType): Boolean =
    decisionService.listDecisions(projectId).any {
        it.stepType == step && it.status == DecisionStatus.RESOLVED
    } || clarificationService.listClarifications(projectId).any {
        it.stepType == step && it.status == ClarificationStatus.ANSWERED
    }
```

Keep `hasAnsweredStepBlocker` for the normal marker-producing branch until Task 3 is green; this preserves the existing loop guard for already-applied blockers.

- [ ] **Step 8: Add apply branch after open blocker check**

In `complete(...)`, immediately after the open-blocker return and before `isLastStep`, add:

```kotlin
val unappliedDecisions = answeredUnappliedDecisions(command.projectId, command.step)
val unappliedClarifications = answeredUnappliedClarifications(command.projectId, command.step)
if (unappliedDecisions.isNotEmpty() || unappliedClarifications.isNotEmpty()) {
    return applyAnsweredBlockers(
        command = command,
        flowState = flowState,
        plan = plan,
        decisions = unappliedDecisions,
        clarifications = unappliedClarifications,
    )
}
```

Add the private method:

```kotlin
private suspend fun applyAnsweredBlockers(
    command: CompleteWizardStep,
    flowState: com.agentwork.productspecagent.domain.FlowState,
    plan: WizardProgressionPlan,
    decisions: List<com.agentwork.productspecagent.domain.Decision>,
    clarifications: List<com.agentwork.productspecagent.domain.Clarification>,
): WizardStepCompletionResult {
    val stepName = command.step.name
    val existingFields = wizardService.getWizardData(command.projectId).steps[stepName]?.fields.orEmpty()
    val commandFields = command.fields.mapValues { (_, value) -> WizardMarkdown.toJsonElement(value) }
    val baseFields = existingFields + commandFields
    val applyResult = applyAgent.apply(
        ApplyWizardBlockers(
            projectId = command.projectId,
            step = command.step,
            fields = baseFields,
            decisions = decisions,
            clarifications = clarifications,
            locale = command.locale,
        )
    )
    val mergedFields = baseFields + applyResult.fieldUpdates
    val now = Instant.now().toString()
    wizardService.saveStepData(
        command.projectId,
        stepName,
        WizardStepData(fields = mergedFields, completedAt = now),
    )
    decisions.forEach { decisionService.markApplied(command.projectId, it.id, applyResult.appliedFields) }
    clarifications.forEach { clarificationService.markApplied(command.projectId, it.id, applyResult.appliedFields) }

    val isLastStep = plan.isTerminal(command.step)
    val nextStep = if (!isLastStep) plan.nextAfter(command.step) else null
    val updatedSteps = flowState.steps.map { step ->
        when (step.stepType) {
            command.step -> step.copy(status = FlowStepStatus.COMPLETED, updatedAt = now)
            nextStep -> step.copy(status = FlowStepStatus.IN_PROGRESS, updatedAt = now)
            else -> step
        }
    }
    projectService.updateFlowState(
        command.projectId,
        flowState.copy(steps = updatedSteps, currentStep = nextStep ?: command.step),
    )
    if (!isLastStep) {
        projectService.regenerateDocsScaffold(command.projectId)
    }

    val progression = snapshotFor(command.projectId)
    val action = when {
        isLastStep -> openExportClientAction
        nextStep != null -> showStepAction(nextStep)
        else -> stayClientAction
    }
    return WizardStepCompletionResult(
        message = applyResult.message,
        nextStep = nextStep,
        exportTriggered = isLastStep,
        progression = progression,
        action = action,
        appliedDecisionIds = decisions.map { it.id },
        appliedClarificationIds = clarifications.map { it.id },
        wizardDataChanged = true,
    )
}
```

- [ ] **Step 9: Run tests to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt
git commit -m "feat(wizard): apply answered blockers before advancing"
```

---

### Task 4: Register Prompt and Agent Model

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`
- Create: `backend/src/main/resources/prompts/wizard-blocker-apply-system.md`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Modify: `backend/src/test/resources/application.yml`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/PromptRegistryTest.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistryTest.kt`

- [ ] **Step 1: Write failing prompt registry test**

In `PromptRegistryTest.kt`, add:

```kotlin
@Test
fun `registry includes wizard blocker apply prompt`() {
    val registry = PromptRegistry()

    val prompt = registry.byId("wizard-blocker-apply-system")

    assertThat(prompt.agent).isEqualTo("WizardBlockerApply")
    assertThat(prompt.resourcePath).isEqualTo("/prompts/wizard-blocker-apply-system.md")
}
```

- [ ] **Step 2: Write failing agent model registry test**

In `AgentModelRegistryTest.kt`, add:

```kotlin
@Test
fun `known agents include wizard blocker apply`() {
    assertThat(AgentModelRegistry.KNOWN_AGENT_IDS).contains("wizard-blocker-apply")
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.PromptRegistryTest --tests com.agentwork.productspecagent.agent.AgentModelRegistryTest
```

Expected: FAIL because the prompt and agent id are not registered.

- [ ] **Step 4: Add prompt file**

Create `backend/src/main/resources/prompts/wizard-blocker-apply-system.md`:

```markdown
Du bist ein Wizard-Apply-Agent. Deine Aufgabe ist es, beantwortete Decisions und Clarifications in die vorhandenen Felder eines Wizard-Schritts einzuarbeiten.

Regeln:
- Antworte ausschliesslich als JSON.
- Erzeuge keine Decisions.
- Erzeuge keine Clarifications.
- Verwende nur Feldnamen aus "Allowed fields".
- Wenn keine sinnvolle Aenderung noetig ist, gib ein leeres fieldUpdates-Objekt zurueck.
- Veraendere keine anderen Schritte.

Antwortformat:
{"message":"Kurze Rueckmeldung fuer den User","fieldUpdates":{"fieldName":"neuer Wert"}}
```

- [ ] **Step 5: Register prompt**

In `PromptRegistry.kt`, add a `PromptDefinition` after `decision-system`:

```kotlin
PromptDefinition(
    id = "wizard-blocker-apply-system",
    title = "WizardBlockerApply — System-Prompt",
    description = "Arbeitet beantwortete Decisions und Clarifications in Wizard-Felder ein.",
    agent = "WizardBlockerApply",
    resourcePath = "/prompts/wizard-blocker-apply-system.md",
    validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
),
```

- [ ] **Step 6: Register agent id and defaults**

In `AgentModelRegistry.kt`, add:

```kotlin
"wizard-blocker-apply",
```

to `KNOWN_AGENT_IDS`.

In all three application YAML files, add under `agent.models.defaults`:

```yaml
      wizard-blocker-apply: MEDIUM
```

- [ ] **Step 7: Run tests to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.PromptRegistryTest --tests com.agentwork.productspecagent.agent.AgentModelRegistryTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt \
  backend/src/main/resources/prompts/wizard-blocker-apply-system.md \
  backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt \
  backend/src/main/resources/application.yml \
  backend/src/main/resources/application-dev.yml \
  backend/src/test/resources/application.yml \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/PromptRegistryTest.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistryTest.kt
git commit -m "feat(wizard): register blocker apply agent"
```

---

### Task 5: Expose Apply Result Through API and Refresh Frontend Wizard Data

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardChatModels.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardChatController.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardChatControllerTest.kt`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/stores/wizard-store.ts`

- [ ] **Step 1: Write failing controller mapping test**

In `WizardChatControllerTest.kt`, adjust the test fixture `testWizardStepCompletion()` to return:

```kotlin
WizardStepCompletionResult(
    message = "Applied.",
    nextStep = FlowStepType.FEATURES,
    exportTriggered = false,
    appliedClarificationIds = listOf("clar-1"),
    wizardDataChanged = true,
)
```

Add expectations to the existing successful completion test:

```kotlin
.andExpect(jsonPath("$.appliedClarificationIds[0]").value("clar-1"))
.andExpect(jsonPath("$.wizardDataChanged").value(true))
```

- [ ] **Step 2: Run controller test to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.WizardChatControllerTest
```

Expected: FAIL because `WizardStepCompleteResponse` does not expose the new fields.

- [ ] **Step 3: Extend API domain response**

In `WizardChatModels.kt`, add:

```kotlin
val appliedDecisionIds: List<String> = emptyList(),
val appliedClarificationIds: List<String> = emptyList(),
val wizardDataChanged: Boolean = false,
```

to `WizardStepCompleteResponse`.

- [ ] **Step 4: Map fields in controller**

In `WizardChatController.kt`, add:

```kotlin
appliedDecisionIds = result.appliedDecisionIds,
appliedClarificationIds = result.appliedClarificationIds,
wizardDataChanged = result.wizardDataChanged,
```

to `WizardStepCompleteResponse(...)`.

- [ ] **Step 5: Run controller test to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.WizardChatControllerTest
```

Expected: PASS.

- [ ] **Step 6: Update frontend API types**

In `frontend/src/lib/api.ts`, extend `WizardStepCompleteResponse`:

```ts
  appliedDecisionIds?: string[];
  appliedClarificationIds?: string[];
  wizardDataChanged?: boolean;
```

- [ ] **Step 7: Refresh wizard data after apply**

In `frontend/src/lib/stores/wizard-store.ts`, after loading decisions/clarifications and before navigation, add:

```ts
      if (response.wizardDataChanged) {
        const refreshedData = await getWizardData(projectId).catch(() => null);
        if (refreshedData) {
          set({ data: refreshedData });
        }
      }
```

Keep this before the `const action = response.action;` navigation block so the next visible screen and final review use fresh wizard data.

- [ ] **Step 8: Run frontend lint**

Run:

```bash
cd frontend && npm run lint
```

Expected: exit 0. Existing warnings in unrelated files are acceptable if there are no errors.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardChatModels.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardChatController.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardChatControllerTest.kt \
  frontend/src/lib/api.ts \
  frontend/src/lib/stores/wizard-store.ts
git commit -m "feat(wizard): expose applied blocker completion"
```

---

### Task 6: Add End-to-End Backend Coverage for Decision Apply and Filtering

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt` if test reveals integration gaps

- [ ] **Step 1: Add resolved Decision apply test**

Add:

```kotlin
@Test
fun `resolved unapplied decision is applied to wizard data and advances without completion agent`() = runBlocking {
    val project = projectService.createProject("Test")
    setFlowProgress(project.project.id, FlowStepType.PROBLEM, setOf(FlowStepType.IDEA))
    val decision = decisionService.createDecision(project.project.id, "Welche Zielgruppe?", FlowStepType.PROBLEM)
    decisionService.resolveDecision(project.project.id, decision.id, "opt-1", "B2B SaaS teams")
    val completionAgent = CapturingWizardAgent("Should not be called.")
    val completion = createCompletion(completionAgent)

    val result = completion.complete(
        CompleteWizardStep(
            projectId = project.project.id,
            step = FlowStepType.PROBLEM,
            fields = mapOf("coreProblem" to "Zu unklar", "primaryAudience" to ""),
        )
    )

    assertThat(completionAgent.calls).isEmpty()
    assertThat(applyAgent.calls).hasSize(1)
    assertThat(result.appliedDecisionIds).containsExactly(decision.id)
    assertThat(result.wizardDataChanged).isTrue()
    assertThat(wizardService.getWizardData(project.project.id).steps["PROBLEM"]?.fields?.get("primaryAudience"))
        .isEqualTo(JsonPrimitive("B2B SaaS teams"))
    assertThat(decisionService.getDecision(project.project.id, decision.id).appliedAt).isNotNull()
}
```

- [ ] **Step 2: Add unknown-field filtering integration test**

In the test, set `applyAgent` to return an unknown field:

```kotlin
applyAgent = CapturingApplyAgent(
    WizardBlockerApplyResult(
        message = "Antwort wurde eingearbeitet.",
        fieldUpdates = mapOf("notAField" to JsonPrimitive("bad")),
        appliedFields = emptyList(),
    )
)
```

Then assert:

```kotlin
assertThat(wizardService.getWizardData(project.project.id).steps["PROBLEM"]?.fields)
    .doesNotContainKey("notAField")
```

If this fails, move filtering from the Koog adapter into `WizardStepCompletionService.applyAnsweredBlockers(...)` so tests cannot bypass validation with a fake agent:

```kotlin
val allowedFields = WizardStepFieldSchema.allowedFields(command.step)
val validUpdates = applyResult.fieldUpdates.filterKeys { it in allowedFields }
val mergedFields = baseFields + validUpdates
val appliedFields = validUpdates.keys.toList()
```

Use `appliedFields` for `markApplied(...)` and result metadata.

- [ ] **Step 3: Run service tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt
git commit -m "test(wizard): cover blocker apply completion"
```

---

### Task 7: Final Verification and Done File

**Files:**
- Create: `docs/features/49-wizard-blocker-apply-agent-done.md`

- [ ] **Step 1: Run backend tests**

Run:

```bash
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run frontend lint**

Run:

```bash
cd frontend && npm run lint
```

Expected: exit 0. Existing warnings are acceptable only if there are no errors.

- [ ] **Step 3: Write done file**

Create `docs/features/49-wizard-blocker-apply-agent-done.md`:

```markdown
# Wizard Blocker Apply Agent — Done

## Summary

Implemented an apply-focused wizard agent path for answered Decisions and Clarifications. When all blockers on the current step are answered, clicking **Weiter** now applies those answers into the current step's `wizard.json` fields and advances without calling the marker-producing completion agent.

## Validation

- `cd backend && ./gradlew test`
- `cd frontend && npm run lint`

## Deviations

- No diff preview UI was added, matching Variant A.
- Field validation is backend-owned and uses persisted wizard field keys.

## Follow-Up

- Complex `FEATURES` graph edits should get additional focused tests before allowing structural graph rewrites.
```

- [ ] **Step 4: Commit**

```bash
git add docs/features/49-wizard-blocker-apply-agent-done.md
git commit -m "docs: mark wizard blocker apply agent done"
```

- [ ] **Step 5: Review final status**

Run:

```bash
git status --short --branch
git log --oneline -8
```

Expected: working tree clean, recent commits match this plan.

---

## Self-Review

Spec coverage:

- Apply agent path: Task 2 and Task 3.
- `wizard.json` updates: Task 3 and Task 6.
- Applied-state metadata: Task 1.
- No marker loop: Task 3 ensures the completion agent is not called in apply path.
- API/frontend refresh: Task 5.
- Prompt/model registration: Task 4.
- Final verification and done file: Task 7.

Placeholder scan:

- No placeholder phrases from the plan checklist are present.
- Each code-changing step includes concrete code or exact insertion snippets.

Type consistency:

- Backend response fields use `appliedDecisionIds`, `appliedClarificationIds`, and `wizardDataChanged` consistently across service result, API model, controller mapping, and frontend type.
- Persisted MVP field uses `goal`, not `mvpGoal`.
