# Feature 44 — Acceptance Criteria in Feature Edit Modal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-feature acceptance criteria editing (title + optional description) to the `FeatureEditDialog`, with a backend AI-suggestion endpoint, and route the new field through to the auto-generated feature docs (with fallback to existing story-subtasks for legacy projects).

**Architecture:** Six bottom-up tasks. Backend domain → Doc-gen fallback → AI agent + endpoint → Frontend types → Modal UI → Suggest-button. Each task ends in a green build and a commit. Backend uses TDD; frontend uses manual browser verification (no test runner configured).

**Tech Stack:** Kotlin 2.3 / Spring Boot 4 / kotlinx-Serialization / JetBrains Koog 0.8.0 (backend); Next.js 16 / React 19 / TypeScript / shadcn/ui (`base-nova` style on `@base-ui/react`) / Tailwind 4 / Zustand 5 (frontend). Branch: `feat/feature-acceptance-criteria` (already created, spec committed).

---

## File Structure

**Backend — create:**
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgent.kt` — single-purpose Koog agent, returns `List<AcceptanceCriterion>`
- `backend/src/main/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalController.kt` — REST endpoint `POST /api/v1/projects/{projectId}/features/{featureId}/acceptance-criteria/propose`
- `backend/src/main/resources/prompts/acceptance-criteria-proposal-system.md` — system prompt resource
- `backend/src/test/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraphTest.kt` — domain test for backward-compatible deserialization
- `backend/src/test/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgentTest.kt` — agent parse-tests
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalControllerTest.kt` — controller HTTP tests

**Backend — modify:**
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraph.kt` — add `AcceptanceCriterion` data class + new field on `WizardFeature`
- `backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt:67` — fallback: prefer wizard AC over story subtasks
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt` — register the new prompt definition
- `backend/src/test/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilderTest.kt` — add three fallback tests

**Frontend — modify:**
- `frontend/src/lib/api.ts` — `AcceptanceCriterion` interface, new field on `WizardFeature`, `proposeAcceptanceCriteria()` wrapper
- `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx` — add `AcceptanceCriteriaList` sub-component, draft/equalDraft/save updates, propose-button + state, `projectId` prop
- `frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx` — pass `projectId` through to `FeatureEditDialog`

**Decision recorded in this plan (resolves the "doc regen on wizard save" risk from the spec):**
- `WizardService.saveStepData` does NOT trigger `DocsScaffoldGenerator`. Re-generation runs only via `ProjectService.saveSpecFile()` (root `CLAUDE.md`). **We accept this** — the existing story-subtasks pipeline has the same property today, so behavior is consistent. Users who only edit AC will see the doc update on the next spec-save (or any export action that rebuilds docs). No additional hook in this feature.

---

## Task 1: Extend Backend Domain Model

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraph.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraphTest.kt`

- [ ] **Step 1: Write the failing domain deserialization test**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraphTest.kt`:

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WizardFeatureGraphTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `legacy WizardFeature JSON without acceptanceCriteria deserializes with empty list`() {
        val legacy = """
            {"id":"f1","title":"Login","scopes":["BACKEND"],"description":"Auth","scopeFields":{}}
        """.trimIndent()

        val feature = json.decodeFromString<WizardFeature>(legacy)

        assertThat(feature.acceptanceCriteria).isEmpty()
        assertThat(feature.title).isEqualTo("Login")
    }

    @Test
    fun `WizardFeature with acceptanceCriteria roundtrips through JSON`() {
        val original = WizardFeature(
            id = "f1",
            title = "Login",
            acceptanceCriteria = listOf(
                AcceptanceCriterion(id = "ac1", title = "User can log in", description = "with valid creds"),
                AcceptanceCriterion(id = "ac2", title = "Wrong password is rejected"),
            ),
        )

        val encoded = json.encodeToString(WizardFeature.serializer(), original)
        val decoded = json.decodeFromString<WizardFeature>(encoded)

        assertThat(decoded.acceptanceCriteria).hasSize(2)
        assertThat(decoded.acceptanceCriteria[0].title).isEqualTo("User can log in")
        assertThat(decoded.acceptanceCriteria[0].description).isEqualTo("with valid creds")
        assertThat(decoded.acceptanceCriteria[1].description).isEmpty()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `backend/`:

```bash
./gradlew test --tests "com.agentwork.productspecagent.domain.WizardFeatureGraphTest"
```

Expected: compile-fail (unresolved reference `AcceptanceCriterion` and unresolved property `acceptanceCriteria`).

- [ ] **Step 3: Add `AcceptanceCriterion` and the `acceptanceCriteria` field**

Modify `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraph.kt`. Replace the file content with:

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class FeatureScope { FRONTEND, BACKEND }

@Serializable
data class GraphPosition(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
data class AcceptanceCriterion(
    val id: String,
    val title: String,
    val description: String = "",
)

@Serializable
data class WizardFeature(
    val id: String,
    val title: String,
    val scopes: Set<FeatureScope> = emptySet(),
    val description: String = "",
    val scopeFields: Map<String, String> = emptyMap(),
    val acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),
    val position: GraphPosition = GraphPosition(),
)

@Serializable
data class WizardFeatureEdge(
    val id: String,
    val from: String,
    val to: String,
)

@Serializable
data class WizardFeatureGraph(
    val features: List<WizardFeature> = emptyList(),
    val edges: List<WizardFeatureEdge> = emptyList(),
)
```

- [ ] **Step 4: Run tests to verify they pass and nothing else broke**

```bash
./gradlew test --tests "com.agentwork.productspecagent.domain.WizardFeatureGraphTest"
./gradlew test
```

Expected: both green. The full test run confirms the new optional field doesn't break any existing wizard/scaffold/agent test that consumes `WizardFeature`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraph.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraphTest.kt
git commit -m "feat(domain): add AcceptanceCriterion and WizardFeature.acceptanceCriteria

Backward-compatible: legacy wizard.json without the new field deserializes
with an empty list (kotlinx default).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: ScaffoldContextBuilder Fallback (Wizard-AC > Story-Subtasks)

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt:67`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilderTest.kt`

- [ ] **Step 1: Write three failing tests covering the fallback paths**

Add these three tests at the end of the existing `ScaffoldContextBuilderTest` class (right before the closing `}`):

```kotlin
    // ── Acceptance-Criteria-Fallback Tests (Feature 44) ──────────────────────

    @Test
    fun `acceptance criteria from wizard feature override story subtasks when present`() {
        val wizardFeature = WizardFeature(
            id = "f1",
            title = "Login",
            acceptanceCriteria = listOf(
                AcceptanceCriterion(id = "ac1", title = "Wizard AC One", description = "from wizard"),
                AcceptanceCriterion(id = "ac2", title = "Wizard AC Two", description = ""),
            ),
        )
        seedWizardFeatures(listOf(wizardFeature))

        val now = Instant.now().toString()
        val epic = SpecTask(
            id = "e1", projectId = projectId, type = TaskType.EPIC, title = "Login",
            estimate = "M", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val story = SpecTask(
            id = "s1", projectId = projectId, parentId = "e1", type = TaskType.STORY,
            title = "Story", estimate = "S", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val subtask = SpecTask(
            id = "t1", projectId = projectId, parentId = "s1", type = TaskType.TASK,
            title = "Should NOT appear as AC", estimate = "S", priority = 0,
            specSection = FlowStepType.FEATURES, createdAt = now, updatedAt = now,
        )
        mockTasks(listOf(epic, story, subtask))

        val ctx = builder.build(projectId)

        assertThat(ctx.features[0].acceptanceCriteria).hasSize(2)
        assertThat(ctx.features[0].acceptanceCriteria[0].title).isEqualTo("Wizard AC One")
        assertThat(ctx.features[0].acceptanceCriteria[1].title).isEqualTo("Wizard AC Two")
    }

    @Test
    fun `empty wizard acceptance criteria fall back to story subtasks`() {
        val wizardFeature = WizardFeature(
            id = "f1",
            title = "Login",
            acceptanceCriteria = emptyList(),
        )
        seedWizardFeatures(listOf(wizardFeature))

        val now = Instant.now().toString()
        val epic = SpecTask(
            id = "e1", projectId = projectId, type = TaskType.EPIC, title = "Login",
            estimate = "M", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val story = SpecTask(
            id = "s1", projectId = projectId, parentId = "e1", type = TaskType.STORY,
            title = "Story", estimate = "S", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val subtask = SpecTask(
            id = "t1", projectId = projectId, parentId = "s1", type = TaskType.TASK,
            title = "Subtask AC", estimate = "S", priority = 0,
            specSection = FlowStepType.FEATURES, createdAt = now, updatedAt = now,
        )
        mockTasks(listOf(epic, story, subtask))

        val ctx = builder.build(projectId)

        assertThat(ctx.features[0].acceptanceCriteria).hasSize(1)
        assertThat(ctx.features[0].acceptanceCriteria[0].title).isEqualTo("Subtask AC")
    }

    @Test
    fun `no wizard feature match falls back to story subtasks`() {
        // No seedWizardFeatures call: builder built without WizardService
        val now = Instant.now().toString()
        val epic = SpecTask(
            id = "e1", projectId = projectId, type = TaskType.EPIC, title = "Login",
            estimate = "M", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val story = SpecTask(
            id = "s1", projectId = projectId, parentId = "e1", type = TaskType.STORY,
            title = "Story", estimate = "S", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val subtask = SpecTask(
            id = "t1", projectId = projectId, parentId = "s1", type = TaskType.TASK,
            title = "Legacy Subtask AC", estimate = "S", priority = 0,
            specSection = FlowStepType.FEATURES, createdAt = now, updatedAt = now,
        )
        mockTasks(listOf(epic, story, subtask))

        val ctx = builder.build(projectId)

        assertThat(ctx.features[0].acceptanceCriteria).hasSize(1)
        assertThat(ctx.features[0].acceptanceCriteria[0].title).isEqualTo("Legacy Subtask AC")
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.agentwork.productspecagent.export.ScaffoldContextBuilderTest"
```

Expected: the three new tests fail; the first one because `acceptanceCriteria` currently contains the subtask `"Should NOT appear as AC"` instead of the wizard AC.

- [ ] **Step 3: Apply the fallback in `ScaffoldContextBuilder.kt`**

In `backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt`, replace the line:

```kotlin
                acceptanceCriteria = subtasks.map { TaskContext(it.title, it.description) },
```

with:

```kotlin
                // Prefer wizard acceptance criteria (Feature 44) when available; fall back to
                // story subtasks for backward compatibility with projects created before this feature.
                acceptanceCriteria = wizardFeature?.acceptanceCriteria
                    ?.takeIf { it.isNotEmpty() }
                    ?.map { TaskContext(it.title, it.description) }
                    ?: subtasks.map { TaskContext(it.title, it.description) },
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.agentwork.productspecagent.export.ScaffoldContextBuilderTest"
./gradlew test --tests "com.agentwork.productspecagent.export.DocsScaffoldGeneratorTest"
```

Expected: both green. The `DocsScaffoldGeneratorTest` test continues to pass because it constructs `TaskContext` directly (not through the wizard fallback path).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilderTest.kt
git commit -m "feat(scaffold): prefer wizard acceptanceCriteria over story subtasks

Backward-compatible fallback: when a WizardFeature has acceptanceCriteria
populated, those are used in the generated feature.md. Legacy projects
without wizard AC continue to render story subtasks.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: AC-Proposal Agent + Prompt + Controller

This task has three sub-tasks. Each sub-task is a fresh red-green-commit cycle.

### Task 3a: Agent + Test

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgent.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgentTest.kt`

- [ ] **Step 1: Write the failing agent test**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgentTest.kt`:

```kotlin
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.AcceptanceCriterion
import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.PromptRegistry
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AcceptanceCriteriaProposalAgentTest {

    private val promptService = PromptService(PromptRegistry(), InMemoryObjectStore())
    private val testJson = Json { ignoreUnknownKeys = true }

    private fun setup(features: List<WizardFeature>): Pair<String, WizardService> {
        val storage = ProjectStorage(InMemoryObjectStore())
        val projectService = ProjectService(storage)
        val wizardService = WizardService(storage)
        val projectId = projectService.createProject("Test").project.id
        val featuresJson = testJson.encodeToJsonElement(features)
        val stepData = WizardStepData(fields = mapOf("features" to featuresJson))
        wizardService.saveWizardData(projectId, WizardData(projectId = projectId, steps = mapOf("FEATURES" to stepData)))
        return projectId to wizardService
    }

    private fun specCtxStub() = object : SpecContextBuilder(
        ProjectService(ProjectStorage(InMemoryObjectStore())),
        null,
    ) {
        override fun buildProposalContext(projectId: String): String =
            "Idea: Test\nCategory: SaaS"
    }

    @Test
    fun `parses JSON response into AcceptanceCriterion list with assigned UUIDs`(): Unit = runBlocking {
        val feature = WizardFeature(
            id = "f1", title = "Login",
            scopes = setOf(FeatureScope.BACKEND), description = "Auth flow",
        )
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : AcceptanceCriteriaProposalAgent(
            specCtxStub(), wizardService, promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = """
                {"criteria":[
                  {"title":"User can log in","description":"with valid creds"},
                  {"title":"Wrong password rejected"}
                ]}
            """
        }

        val criteria = agent.propose(projectId, "f1")

        assertThat(criteria).hasSize(2)
        assertThat(criteria[0].id).isNotBlank()
        assertThat(criteria[0].title).isEqualTo("User can log in")
        assertThat(criteria[0].description).isEqualTo("with valid creds")
        assertThat(criteria[1].title).isEqualTo("Wrong password rejected")
        assertThat(criteria[1].description).isEmpty()
        // UUIDs are unique per criterion
        assertThat(criteria[0].id).isNotEqualTo(criteria[1].id)
    }

    @Test
    fun `malformed JSON throws ProposalParseException`(): Unit = runBlocking {
        val feature = WizardFeature(id = "f1", title = "Login")
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : AcceptanceCriteriaProposalAgent(
            specCtxStub(), wizardService, promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = "not a json"
        }

        val ex = runCatching { agent.propose(projectId, "f1") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(ProposalParseException::class.java)
    }

    @Test
    fun `unknown featureId throws IllegalArgumentException`(): Unit = runBlocking {
        val feature = WizardFeature(id = "f1", title = "Login")
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : AcceptanceCriteriaProposalAgent(
            specCtxStub(), wizardService, promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = """{"criteria":[]}"""
        }

        val ex = runCatching { agent.propose(projectId, "does-not-exist") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `prompt includes feature title and description in context`(): Unit = runBlocking {
        var capturedPrompt = ""
        val feature = WizardFeature(
            id = "f1", title = "Login Flow",
            scopes = setOf(FeatureScope.BACKEND), description = "OAuth + email",
        )
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : AcceptanceCriteriaProposalAgent(
            specCtxStub(), wizardService, promptService,
        ) {
            override suspend fun runAgent(prompt: String): String {
                capturedPrompt = prompt
                return """{"criteria":[]}"""
            }
        }
        agent.propose(projectId, "f1")

        assertThat(capturedPrompt).contains("Title: Login Flow")
        assertThat(capturedPrompt).contains("Description: OAuth + email")
        assertThat(capturedPrompt).contains("Scopes: BACKEND")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "com.agentwork.productspecagent.agent.AcceptanceCriteriaProposalAgentTest"
```

Expected: compile-fail (`AcceptanceCriteriaProposalAgent` does not exist).

- [ ] **Step 3: Implement the agent**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgent.kt`:

```kotlin
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.AcceptanceCriterion
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.WizardService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.springframework.stereotype.Service
import java.util.UUID

@Service
open class AcceptanceCriteriaProposalAgent(
    private val contextBuilder: SpecContextBuilder,
    private val wizardService: WizardService,
    private val promptService: PromptService,
    private val koogRunner: KoogAgentRunner? = null,
) {
    companion object { const val AGENT_ID = "acceptance-criteria-proposal" }

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun propose(projectId: String, featureId: String): List<AcceptanceCriterion> {
        val feature = loadFeature(projectId, featureId)
            ?: throw IllegalArgumentException("Feature $featureId not found")
        val context = contextBuilder.buildProposalContext(projectId)
        val prompt = buildString {
            appendLine("Generate concrete, testable acceptance criteria for the following feature.")
            appendLine("Each criterion must describe a stakeholder-observable Done condition (not implementation steps).")
            appendLine()
            appendLine("=== PROJECT CONTEXT ===")
            appendLine(context)
            appendLine()
            appendLine("=== FEATURE ===")
            appendLine("Title: ${feature.title}")
            if (feature.description.isNotBlank()) appendLine("Description: ${feature.description}")
            if (feature.scopes.isNotEmpty()) appendLine("Scopes: ${feature.scopes.joinToString()}")
            appendLine()
            appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
            appendLine("""{"criteria":[{"title":"...","description":"..."}]}""")
            appendLine("Aim for 3–6 criteria. 'description' is optional (empty string allowed).")
        }
        val raw = runAgent(prompt)
        return parseResponse(raw)
    }

    // Overridden by tests; production path delegates to KoogAgentRunner
    // (same pattern as FeatureProposalAgent / DecisionAgent).
    protected open suspend fun runAgent(prompt: String): String =
        koogRunner?.run(AGENT_ID, promptService.get("acceptance-criteria-proposal-system"), prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")

    private fun loadFeature(projectId: String, featureId: String): WizardFeature? {
        // Same pattern as ScaffoldContextBuilder.loadWizardFeaturesByTitle but
        // returns a single feature by id.
        val wizardData = runCatching { wizardService.getWizardData(projectId) }.getOrNull() ?: return null
        val featuresElement = wizardData.steps["FEATURES"]?.fields?.get("features") ?: return null
        return runCatching {
            json.decodeFromJsonElement<List<WizardFeature>>(featuresElement)
                .firstOrNull { it.id == featureId }
        }.getOrNull()
    }

    private fun parseResponse(raw: String): List<AcceptanceCriterion> {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val parsed = runCatching { json.decodeFromString<ProposalResponse>(jsonStr) }
            .getOrElse { throw ProposalParseException("Invalid JSON from LLM: ${it.message}", it) }
        return parsed.criteria.map { c ->
            AcceptanceCriterion(
                id = UUID.randomUUID().toString(),
                title = c.title,
                description = c.description ?: "",
            )
        }
    }

    @Serializable
    private data class ProposalResponse(val criteria: List<CriterionDef> = emptyList())

    @Serializable
    private data class CriterionDef(val title: String, val description: String? = null)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.agentwork.productspecagent.agent.AcceptanceCriteriaProposalAgentTest"
```

Expected: all four tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgent.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgentTest.kt
git commit -m "feat(agent): add AcceptanceCriteriaProposalAgent

Mirrors FeatureProposalAgent pattern: context+prompt → LLM → JSON parse.
Loads the target feature from wizard.json by id, builds a focused prompt
with feature metadata, and returns AcceptanceCriterion objects with fresh
UUIDs.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 3b: System Prompt Resource + Registry Entry

**Files:**
- Create: `backend/src/main/resources/prompts/acceptance-criteria-proposal-system.md`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`

- [ ] **Step 1: Create the prompt resource file**

Create `backend/src/main/resources/prompts/acceptance-criteria-proposal-system.md`:

```markdown
You generate acceptance criteria for product features.

Acceptance criteria are stakeholder-observable Done conditions, not implementation steps.
Each criterion must be:
- Specific (concrete, not vague)
- Measurable (testable from outside the system)
- Independent (no dependency on other criteria)
- User-language (no technical jargon unless the user is a developer)

Output JSON ONLY. No markdown. No explanation.
```

- [ ] **Step 2: Register the prompt in `PromptRegistry`**

In `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`, append a new `PromptDefinition` to the list (right before the closing `)` of the `definitions` list, after the existing `feature-proposal-system` entry):

```kotlin
        PromptDefinition(
            id = "acceptance-criteria-proposal-system",
            title = "Acceptance-Criteria-Proposal — System-Prompt",
            description = "Rolle des Acceptance-Criteria-Agents (stakeholder-orientierte Done-Bedingungen pro Feature).",
            agent = "AcceptanceCriteriaProposal",
            resourcePath = "/prompts/acceptance-criteria-proposal-system.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
```

- [ ] **Step 3: Verify the registry resolves the new prompt**

Run the existing `PromptControllerTest` (which iterates over registry definitions) plus a one-off check:

```bash
./gradlew test --tests "com.agentwork.productspecagent.api.PromptControllerTest"
./gradlew test --tests "com.agentwork.productspecagent.service.*"
```

Expected: all green. (If a test counts the number of definitions, it will need to be bumped — fix inline if so.)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/prompts/acceptance-criteria-proposal-system.md \
        backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt
git commit -m "feat(prompts): register acceptance-criteria-proposal-system prompt

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 3c: Controller + Test

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalController.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalControllerTest.kt`

- [ ] **Step 1: Write the failing controller test**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalControllerTest.kt`:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.AcceptanceCriteriaProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.AcceptanceCriterion
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.WizardService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class AcceptanceCriteriaProposalControllerTest {

    @TestConfiguration
    class TestAgentConfig {
        @Bean
        @Primary
        fun testAcceptanceCriteriaProposalAgent(
            contextBuilder: SpecContextBuilder,
            wizardService: WizardService,
            promptService: PromptService,
        ): AcceptanceCriteriaProposalAgent {
            return object : AcceptanceCriteriaProposalAgent(contextBuilder, wizardService, promptService) {
                override suspend fun propose(projectId: String, featureId: String): List<AcceptanceCriterion> {
                    return when (featureId) {
                        "f-parse-error" -> throw ProposalParseException("bad JSON from LLM")
                        "f-missing" -> throw IllegalArgumentException("Feature f-missing not found")
                        else -> listOf(
                            AcceptanceCriterion(id = "ac1", title = "User can log in", description = "with valid creds"),
                            AcceptanceCriterion(id = "ac2", title = "Wrong password rejected"),
                        )
                    }
                }
            }
        }
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `POST acceptance-criteria propose returns 200 with criteria list`() {
        val projectId = createProject("AC Test")

        mockMvc.perform(post("/api/v1/projects/$projectId/features/f-1/acceptance-criteria/propose"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("User can log in"))
            .andExpect(jsonPath("$[0].description").value("with valid creds"))
            .andExpect(jsonPath("$[1].title").value("Wrong password rejected"))
    }

    @Test
    fun `POST acceptance-criteria propose returns 422 on parse error`() {
        val projectId = createProject("AC Parse Err")

        mockMvc.perform(post("/api/v1/projects/$projectId/features/f-parse-error/acceptance-criteria/propose"))
            .andExpect(status().isUnprocessableEntity())
    }

    @Test
    fun `POST acceptance-criteria propose returns 404 on missing feature`() {
        val projectId = createProject("AC Missing")

        mockMvc.perform(post("/api/v1/projects/$projectId/features/f-missing/acceptance-criteria/propose"))
            .andExpect(status().isNotFound())
    }

    private fun createProject(name: String): String {
        val result = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"name": "$name"}""")
        ).andExpect(status().isCreated()).andReturn()

        return """"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(result.response.contentAsString)!!
            .groupValues[1]
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "com.agentwork.productspecagent.api.AcceptanceCriteriaProposalControllerTest"
```

Expected: 404 / compile-error (controller does not exist yet).

- [ ] **Step 3: Implement the controller**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalController.kt`:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.AcceptanceCriteriaProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/projects/{projectId}/features/{featureId}/acceptance-criteria")
class AcceptanceCriteriaProposalController(
    private val agent: AcceptanceCriteriaProposalAgent,
) {
    @PostMapping("/propose")
    fun propose(
        @PathVariable projectId: String,
        @PathVariable featureId: String,
    ): ResponseEntity<Any> = runBlocking {
        try {
            ResponseEntity.ok<Any>(agent.propose(projectId, featureId))
        } catch (e: ProposalParseException) {
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("error" to (e.message ?: "Parsing failed")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to (e.message ?: "Feature not found")))
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.agentwork.productspecagent.api.AcceptanceCriteriaProposalControllerTest"
./gradlew test
```

Expected: new controller test green; full test suite green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalController.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalControllerTest.kt
git commit -m "feat(api): add POST acceptance-criteria/propose endpoint

200 with criteria list on happy path; 422 on LLM parse error;
404 on unknown featureId.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Frontend Types & API Wrapper

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: Add the `AcceptanceCriterion` interface and extend `WizardFeature`**

In `frontend/src/lib/api.ts`, locate the `Feature Graph Types` block (around lines 58–82). Replace:

```ts
export interface WizardFeature {
  id: string;
  title: string;
  scopes: FeatureScope[];
  description: string;
  scopeFields: Record<string, string>;
  position: GraphPosition;
}
```

with:

```ts
export interface AcceptanceCriterion {
  id: string;
  title: string;
  description: string;
}

export interface WizardFeature {
  id: string;
  title: string;
  scopes: FeatureScope[];
  description: string;
  scopeFields: Record<string, string>;
  acceptanceCriteria: AcceptanceCriterion[];
  position: GraphPosition;
}
```

- [ ] **Step 2: Add the `proposeAcceptanceCriteria` API wrapper**

Locate `proposeFeatures` (search for `export async function proposeFeatures` — around line 355). Immediately after it, add:

```ts
export async function proposeAcceptanceCriteria(
  projectId: string,
  featureId: string,
): Promise<AcceptanceCriterion[]> {
  return apiFetch<AcceptanceCriterion[]>(
    `/api/v1/projects/${projectId}/features/${featureId}/acceptance-criteria/propose`,
    { method: "POST" },
  );
}
```

- [ ] **Step 3: Verify no type or lint regressions**

Run from `frontend/`:

```bash
npm run lint
npx tsc --noEmit
```

Expected: both pass without new errors. The new field `acceptanceCriteria` is required in the interface, but TypeScript's structural typing combined with the wizard-store's `Partial<WizardFeature>` patches means this only matters when constructing a fresh feature — which already happens via the backend (existing `proposeFeatures` returns `WizardFeature[]` typed by the server). If `tsc` flags spots where features are constructed inline without `acceptanceCriteria`, default them to `[]` at those call sites and re-run.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(api): add AcceptanceCriterion type and propose wrapper

WizardFeature now carries an acceptanceCriteria field;
proposeAcceptanceCriteria() calls the new POST endpoint.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Modal — `AcceptanceCriteriaList` Sub-Component + Wire-up

**Files:**
- Modify: `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx`
- Modify: `frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx`

- [ ] **Step 1: Pass `projectId` from `FeaturesGraphEditor` to the modal**

Open `frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx`. Search for the `<FeatureEditDialog` JSX usage. Add `projectId={projectId}` to its prop list. The `projectId` value comes from the existing `FeaturesGraphEditor` props — if it isn't already passed in, locate where `FeaturesGraphEditor` is consumed (likely `FeaturesForm.tsx`) and ensure it is forwarded. Inspect with:

```bash
grep -n "projectId" frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx
grep -rn "FeaturesGraphEditor" frontend/src/components/wizard/steps/features/
```

If `FeaturesGraphEditor` already receives `projectId` (typical), no upstream change is needed beyond the new prop on the modal. If not, add `projectId: string` to its props interface and forward from the caller.

- [ ] **Step 2: Add `projectId` to `FeatureEditDialogProps` and accept it in the component**

Open `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx`. Modify `FeatureEditDialogProps` and the component signature:

```tsx
interface FeatureEditDialogProps {
  feature: WizardFeature | null;
  allowedScopes: FeatureScope[];
  open: boolean;
  projectId: string;          // NEW
  onClose: () => void;
  onSave: (patch: Partial<WizardFeature>) => void;
  onDelete: () => void;
}

export function FeatureEditDialog({
  feature,
  allowedScopes,
  open,
  projectId,                  // NEW
  onClose,
  onSave,
  onDelete,
}: FeatureEditDialogProps) {
```

- [ ] **Step 3: Extend the `DraftFeature` type and snapshot/equalDraft helpers**

In the same file, replace:

```tsx
type DraftFeature = Pick<WizardFeature,
  "title" | "description" | "scopes" | "scopeFields">;

function snapshot(f: WizardFeature): DraftFeature {
  return {
    title: f.title,
    description: f.description,
    scopes: [...f.scopes],
    scopeFields: { ...f.scopeFields },
  };
}

function equalDraft(a: DraftFeature, b: DraftFeature): boolean {
  if (a.title !== b.title || a.description !== b.description) return false;
  if (a.scopes.length !== b.scopes.length) return false;
  for (const s of a.scopes) if (!b.scopes.includes(s)) return false;
  const ak = Object.keys(a.scopeFields);
  const bk = Object.keys(b.scopeFields);
  if (ak.length !== bk.length) return false;
  for (const k of ak) if (a.scopeFields[k] !== b.scopeFields[k]) return false;
  return true;
}
```

with:

```tsx
type DraftFeature = Pick<WizardFeature,
  "title" | "description" | "scopes" | "scopeFields" | "acceptanceCriteria">;

function snapshot(f: WizardFeature): DraftFeature {
  return {
    title: f.title,
    description: f.description,
    scopes: [...f.scopes],
    scopeFields: { ...f.scopeFields },
    acceptanceCriteria: f.acceptanceCriteria.map((c) => ({ ...c })),
  };
}

function equalDraft(a: DraftFeature, b: DraftFeature): boolean {
  if (a.title !== b.title || a.description !== b.description) return false;
  if (a.scopes.length !== b.scopes.length) return false;
  for (const s of a.scopes) if (!b.scopes.includes(s)) return false;
  const ak = Object.keys(a.scopeFields);
  const bk = Object.keys(b.scopeFields);
  if (ak.length !== bk.length) return false;
  for (const k of ak) if (a.scopeFields[k] !== b.scopeFields[k]) return false;
  if (a.acceptanceCriteria.length !== b.acceptanceCriteria.length) return false;
  for (let i = 0; i < a.acceptanceCriteria.length; i++) {
    const x = a.acceptanceCriteria[i];
    const y = b.acceptanceCriteria[i];
    if (x.id !== y.id || x.title !== y.title || x.description !== y.description) return false;
  }
  return true;
}
```

- [ ] **Step 4: Update the `AcceptanceCriterion` import**

At the top of `FeatureEditDialog.tsx`, change:

```tsx
import type { FeatureScope, WizardFeature } from "@/lib/api";
```

to:

```tsx
import type { AcceptanceCriterion, FeatureScope, WizardFeature } from "@/lib/api";
```

Also update the lucide-react import. Replace:

```tsx
import { Trash2 } from "lucide-react";
```

with:

```tsx
import { ChevronDown, ChevronUp, Loader2, Plus, Sparkles, Trash2 } from "lucide-react";
```

(`Loader2` and `Sparkles` are used in Task 6, but importing them now keeps the import block stable.)

- [ ] **Step 5: Add the `AcceptanceCriteriaList` sub-component**

In the same file, after the `equalDraft` function but before the `export function FeatureEditDialog(...)`, insert:

```tsx
interface AcceptanceCriteriaListProps {
  value: AcceptanceCriterion[];
  onChange: (next: AcceptanceCriterion[]) => void;
  onPropose: () => void;
  isProposing: boolean;
  proposeError: string | null;
}

function AcceptanceCriteriaList({
  value,
  onChange,
  onPropose,
  isProposing,
  proposeError,
}: AcceptanceCriteriaListProps) {
  const inputRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const focusIdRef = useRef<string | null>(null);

  useEffect(() => {
    const id = focusIdRef.current;
    if (id && inputRefs.current[id]) {
      inputRefs.current[id]?.focus();
      focusIdRef.current = null;
    }
  }, [value]);

  function patchItem(id: string, key: "title" | "description", val: string) {
    onChange(value.map((c) => (c.id === id ? { ...c, [key]: val } : c)));
  }

  function appendNew(afterId?: string) {
    const newItem: AcceptanceCriterion = {
      id: crypto.randomUUID(),
      title: "",
      description: "",
    };
    if (!afterId) {
      onChange([...value, newItem]);
    } else {
      const idx = value.findIndex((c) => c.id === afterId);
      const next = [...value];
      next.splice(idx + 1, 0, newItem);
      onChange(next);
    }
    focusIdRef.current = newItem.id;
  }

  function remove(id: string) {
    onChange(value.filter((c) => c.id !== id));
  }

  function move(id: string, direction: -1 | 1) {
    const idx = value.findIndex((c) => c.id === id);
    const target = idx + direction;
    if (target < 0 || target >= value.length) return;
    const next = [...value];
    [next[idx], next[target]] = [next[target], next[idx]];
    onChange(next);
  }

  return (
    <section className="border-t pt-4 mt-2">
      <div className="flex items-center justify-between mb-3">
        <Label>Akzeptanzkriterien</Label>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onPropose}
          disabled={isProposing}
        >
          {isProposing ? (
            <Loader2 className="animate-spin mr-1" size={14} />
          ) : (
            <Sparkles className="mr-1" size={14} />
          )}
          {isProposing ? "Generiere..." : "AC vorschlagen"}
        </Button>
      </div>

      {proposeError && (
        <p className="text-xs text-destructive mb-2">{proposeError}</p>
      )}

      <div className="space-y-3">
        {value.map((c, idx) => (
          <div
            key={c.id}
            className="border border-border rounded-md p-3 space-y-2 bg-muted/20"
          >
            <div>
              <Label htmlFor={`ac-title-${c.id}`} className="text-xs">Titel</Label>
              <Input
                id={`ac-title-${c.id}`}
                ref={(el) => { inputRefs.current[c.id] = el; }}
                value={c.title}
                onChange={(e) => patchItem(c.id, "title", e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    appendNew(c.id);
                  }
                }}
              />
            </div>
            <div>
              <Label htmlFor={`ac-desc-${c.id}`} className="text-xs">Beschreibung (optional)</Label>
              <Textarea
                id={`ac-desc-${c.id}`}
                rows={2}
                value={c.description}
                onChange={(e) => patchItem(c.id, "description", e.target.value)}
              />
            </div>
            <div className="flex justify-end gap-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => move(c.id, -1)}
                disabled={idx === 0}
                aria-label="Nach oben"
              >
                <ChevronUp size={14} />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => move(c.id, 1)}
                disabled={idx === value.length - 1}
                aria-label="Nach unten"
              >
                <ChevronDown size={14} />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => remove(c.id)}
                aria-label="Entfernen"
              >
                <Trash2 size={14} />
              </Button>
            </div>
          </div>
        ))}
      </div>

      <Button
        type="button"
        variant="outline"
        size="sm"
        className="mt-3"
        onClick={() => appendNew()}
      >
        <Plus size={14} className="mr-1" /> Akzeptanzkriterium hinzufügen
      </Button>
    </section>
  );
}
```

- [ ] **Step 6: Add `acceptanceCriteria` state stubs to the modal (without propose-button wiring yet)**

Inside the `FeatureEditDialog` component, just after the `scopeSections = useMemo(...)` block, add stub state for the propose flow (the actual button logic comes in Task 6 — this avoids touching the file twice):

```tsx
const [isProposing, setIsProposing] = useState(false);
const [proposeError, setProposeError] = useState<string | null>(null);
const handlePropose = () => { /* implemented in Task 6 */ };
```

Note: `projectId` is already destructured but unused at this point — TypeScript will flag this as a noUnusedParameters error if your tsconfig has it on. The Task 6 implementation will read `projectId`. To avoid a temporary build break, prefix it with `_` in this task or add a `void projectId;` line. **Cleanest path:** keep `projectId` as the prop name (Task 6 uses it) and add `void projectId;` inside the component body. Remove that line in Task 6.

- [ ] **Step 7: Render `AcceptanceCriteriaList` inside the dialog**

Inside the modal JSX, immediately after the closing `</div>` of the 2-column grid (`<div className="grid grid-cols-1 md:grid-cols-[1fr_1.3fr] gap-6 py-4">`) and before `<DialogFooter>`, insert:

```tsx
<AcceptanceCriteriaList
  value={draft.acceptanceCriteria}
  onChange={(next) => patch("acceptanceCriteria", next)}
  onPropose={handlePropose}
  isProposing={isProposing}
  proposeError={proposeError}
/>
```

- [ ] **Step 8: Update `handleSave` to filter empty acceptance criteria**

Replace the existing `handleSave`:

```tsx
function handleSave() {
  if (!draft) return;
  onSave({
    title: draft.title,
    description: draft.description,
    scopes: draft.scopes,
    scopeFields: draft.scopeFields,
  });
  onClose();
}
```

with:

```tsx
function handleSave() {
  if (!draft) return;
  const cleanedAC = draft.acceptanceCriteria
    .map((c) => ({ ...c, title: c.title.trim(), description: c.description.trim() }))
    .filter((c) => c.title.length > 0);
  onSave({
    title: draft.title,
    description: draft.description,
    scopes: draft.scopes,
    scopeFields: draft.scopeFields,
    acceptanceCriteria: cleanedAC,
  });
  onClose();
}
```

- [ ] **Step 9: Build the frontend to verify it compiles**

```bash
cd frontend && npm run build && npm run lint
```

Expected: build succeeds, lint clean. If any wizard-store / form caller constructs a `WizardFeature` literal without `acceptanceCriteria`, `tsc` will flag it — add `acceptanceCriteria: []` at that call site.

- [ ] **Step 10: Manual browser verification**

Run the backend (`./gradlew bootRun --quiet` from `backend/`) and frontend (`npm run dev` from `frontend/`). Open a project, navigate to the FEATURES wizard step, click a node to open the modal. Verify:

1. The "Akzeptanzkriterien" section appears under the 2-column grid.
2. Click "+ Akzeptanzkriterium hinzufügen" — a new empty entry appears, the title input is focused.
3. Type a title and a description.
4. Press Enter in the title input — a second empty entry appears directly below, focused.
5. Add a third entry; click ↑ on the third — order reshuffles.
6. ↓ on the last entry is disabled; ↑ on the first entry is disabled.
7. Click ✕ on an entry — entry disappears immediately.
8. Click "Abbrechen" with unsaved changes → "Änderungen verwerfen?" confirm appears.
9. Click "Speichern" — modal closes; reopen the same feature → entries are present in the saved order; entries you'd left with empty title are filtered out.
10. The "AC vorschlagen" button is visible but does nothing yet (Task 6).

- [ ] **Step 11: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx \
        frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx
git commit -m "feat(modal): add AcceptanceCriteriaList sub-component to FeatureEditDialog

Inline editing of acceptance criteria (title + optional description),
add/remove, ↑/↓ reorder, Enter inserts a new entry below.
Empty-title entries are silently filtered on save.
projectId is now passed from FeaturesGraphEditor for upcoming propose call.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: "AC vorschlagen" Button (AI generation wire-up)

**Files:**
- Modify: `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx`

- [ ] **Step 1: Import `proposeAcceptanceCriteria`**

In `FeatureEditDialog.tsx`, locate:

```tsx
import type { AcceptanceCriterion, FeatureScope, WizardFeature } from "@/lib/api";
```

Add the value import on a new line below it:

```tsx
import { proposeAcceptanceCriteria } from "@/lib/api";
```

- [ ] **Step 2: Wire `handlePropose`**

Replace the stub:

```tsx
const [isProposing, setIsProposing] = useState(false);
const [proposeError, setProposeError] = useState<string | null>(null);
const handlePropose = () => { /* implemented in Task 6 */ };
```

with:

```tsx
const [isProposing, setIsProposing] = useState(false);
const [proposeError, setProposeError] = useState<string | null>(null);

async function handlePropose() {
  if (!feature || !draft) return;
  setIsProposing(true);
  setProposeError(null);
  try {
    const proposed = await proposeAcceptanceCriteria(projectId, feature.id);
    patch("acceptanceCriteria", [...draft.acceptanceCriteria, ...proposed]);
  } catch (e) {
    setProposeError(e instanceof Error ? e.message : "Vorschlag fehlgeschlagen");
  } finally {
    setIsProposing(false);
  }
}
```

Also remove the `void projectId;` line added in Task 5 (if you used that escape hatch).

- [ ] **Step 3: Build & lint**

```bash
cd frontend && npm run build && npm run lint
```

Expected: green.

- [ ] **Step 4: Manual browser verification (requires `OPENAI_API_KEY` env var on backend)**

Start backend with the API key set, plus frontend. Open a feature in the modal. Verify:

1. Click "AC vorschlagen" — button shows the spinner + "Generiere…", is disabled.
2. After ~2–10 seconds, 3–6 new AC entries are appended below any existing ones.
3. Clicking "AC vorschlagen" again appends additional entries (duplicates allowed; user trims via ✕).
4. Disconnect the OpenAI key (or stop backend mid-call): an inline `text-destructive` error message appears below the button. The button re-enables.
5. After "Speichern" + reopen, the proposed entries are persisted in `wizard.json`.
6. Navigate to a generated feature doc (`docs/features/NN-…md` after a spec save) — Acceptance Criteria block reflects the wizard AC.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx
git commit -m "feat(modal): wire AC vorschlagen button to backend propose endpoint

Append-merge: proposed criteria are added to the existing list; user can
remove unwanted entries via ✕. Loading state disables the button and shows
a spinner; errors render as a small text-destructive line.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Final Verification

After Task 6 commit, run all three quality gates:

- [ ] **Backend full test suite**

```bash
cd backend && ./gradlew test
```

Expected: green.

- [ ] **Frontend build + lint**

```bash
cd frontend && npm run build && npm run lint
```

Expected: green.

- [ ] **End-to-end manual smoke test**

Start both services. Create a fresh project, walk through the wizard to FEATURES, add a feature, open the modal, exercise:
1. Manual AC add/edit/reorder/remove → save → reopen → verified persistence.
2. AC vorschlagen → entries appended → save → reopen → persisted.
3. Save a spec step → check `data/projects/<id>/docs/features/NN-…md` shows the wizard AC under `## Acceptance Criteria`.
4. Open a legacy project (one without wizard AC) → confirm story subtasks still appear in the doc as before.

---

## Spec Coverage Map

| Spec Acceptance Criterion | Implemented in Task |
|---|---|
| AC #1–10 (Frontend Modal) | Task 5 (manual verification step), Task 6 (propose loading state) |
| AC #11 (200 OK response) | Task 3c |
| AC #12 (404 unknown featureId) | Task 3c |
| AC #13 (422 LLM parse error) | Task 3c |
| AC #14 (Append-Merge frontend) | Task 6 |
| AC #15 (Loading-State + Disabled) | Task 5 (sub-component), Task 6 (handler) |
| AC #16 (legacy wizard.json loads with `[]`) | Task 1 |
| AC #17 (saving writes the new field) | Task 1 (roundtrip), Task 5 (manual verify) |
| AC #18 (ScaffoldContextBuilder fallback) | Task 2 |
| AC #19 (feature.md format) | Task 2 (existing template untouched) |
| AC #20 (Agent parse + ProposalParseException) | Task 3a |
| AC #21 (ScaffoldContextBuilderTest 3 paths) | Task 2 |
| AC #22 (manual browser verification) | Task 5 step 10, Task 6 step 4 |

All 22 spec acceptance criteria are covered.
