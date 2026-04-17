# Feature 22 — Features Graph Wizard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat-list FEATURES wizard step with an intelligent DAG editor that captures scope-aware (Frontend/Backend) per-feature fields, feeds real dependencies into the EPIC-task generator, and is seeded + validated by a Koog LLM agent.

**Architecture:** Backend adds a `WizardFeatureGraph` domain type, a `FeatureProposalAgent` service, and threads `scopes` + `dependsOn` through `PlanGeneratorAgent` + `TaskService` into real `SpecTask.dependencies`. Frontend replaces `FeaturesForm` with a Rete.js v2 graph editor (`rete-auto-arrange-plugin` for layout, `addPipe` for cycle prevention) and a `useResizable` split-pane side panel.

**Tech Stack:** Kotlin 2.3 / Spring Boot 4 / Koog 0.7.3 / kotlinx.serialization (backend). Next.js 16 / React 19 / TypeScript / Zustand / Rete.js v2 (`rete`, `rete-area-plugin`, `rete-connection-plugin`, `rete-react-plugin`, new: `rete-auto-arrange-plugin`) / Tailwind CSS 4 (frontend).

**Reference specs:**
- [`docs/features/22-features-graph-wizard.md`](../../features/22-features-graph-wizard.md) — approved feature design
- [`docs/superpowers/specs/2026-04-17-feature-22-implementation-notes.md`](../specs/2026-04-17-feature-22-implementation-notes.md) — implementation addendum (3 deviations: auto-arrange-plugin, useResizable, epicEstimate via LLM)

---

## Phase A — Backend Foundation

### Task 1: Domain types for `WizardFeatureGraph`

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraph.kt`
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraphTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/src/test/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraphTest.kt
package com.agentwork.productspecagent.domain

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WizardFeatureGraphTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `serializes and deserializes a graph with nodes and edges`() {
        val graph = WizardFeatureGraph(
            features = listOf(
                WizardFeature(
                    id = "f-1",
                    title = "Login",
                    scopes = setOf(FeatureScope.BACKEND),
                    description = "User authentication",
                    scopeFields = mapOf("apiEndpoints" to "POST /auth/login"),
                    position = GraphPosition(0.0, 0.0)
                ),
                WizardFeature(
                    id = "f-2",
                    title = "Dashboard",
                    scopes = setOf(FeatureScope.FRONTEND),
                    description = "Main user view",
                    scopeFields = mapOf("screens" to "/dashboard"),
                    position = GraphPosition(320.0, 0.0)
                ),
            ),
            edges = listOf(WizardFeatureEdge(id = "e-1", from = "f-1", to = "f-2"))
        )
        val encoded = json.encodeToString(WizardFeatureGraph.serializer(), graph)
        val decoded = json.decodeFromString(WizardFeatureGraph.serializer(), encoded)
        assertThat(decoded).isEqualTo(graph)
    }

    @Test
    fun `empty scopes set represents Library-style feature`() {
        val feature = WizardFeature(
            id = "f-1",
            title = "Core",
            scopes = emptySet(),
            description = "",
            scopeFields = emptyMap(),
            position = GraphPosition(0.0, 0.0)
        )
        assertThat(feature.scopes).isEmpty()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.productspecagent.domain.WizardFeatureGraphTest"`
Expected: FAIL — `WizardFeatureGraph` / `WizardFeature` / `WizardFeatureEdge` / `FeatureScope` / `GraphPosition` not found.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraph.kt
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class FeatureScope { FRONTEND, BACKEND }

@Serializable
data class GraphPosition(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
data class WizardFeature(
    val id: String,
    val title: String,
    val scopes: Set<FeatureScope> = emptySet(),
    val description: String = "",
    val scopeFields: Map<String, String> = emptyMap(),
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.domain.WizardFeatureGraphTest"`
Expected: PASS — both tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraph.kt backend/src/test/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraphTest.kt
git commit -m "feat(backend): add WizardFeatureGraph domain for Feature 22"
```

---

### Task 2: Refactor `WizardFeatureInput` + backward-compat `parseWizardFeatures`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/TaskService.kt:9-13` (extend `WizardFeatureInput`)
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt:257-300` (`parseWizardFeatures` helper)
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/agent/ParseWizardFeaturesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/src/test/kotlin/com/agentwork/productspecagent/agent/ParseWizardFeaturesTest.kt
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.service.WizardFeatureInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParseWizardFeaturesTest {
    @Test
    fun `parses legacy flat list without scopes`() {
        val raw = listOf(
            mapOf("title" to "Login", "description" to "Auth", "estimate" to "M"),
            mapOf("title" to "Dashboard", "description" to "Main view"),
        )
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "SaaS")
        assertThat(result).hasSize(2)
        assertThat(result[0].title).isEqualTo("Login")
        assertThat(result[0].scopes).containsExactlyInAnyOrder(FeatureScope.FRONTEND, FeatureScope.BACKEND)
        assertThat(result[0].id).isNotBlank() // auto-assigned UUID
        assertThat(result[0].dependsOn).isEmpty()
    }

    @Test
    fun `parses graph structure with scopes and edges`() {
        val raw = mapOf(
            "features" to listOf(
                mapOf("id" to "f-1", "title" to "Login",
                      "scopes" to listOf("BACKEND"),
                      "scopeFields" to mapOf("apiEndpoints" to "POST /auth/login")),
                mapOf("id" to "f-2", "title" to "Dashboard",
                      "scopes" to listOf("FRONTEND"),
                      "scopeFields" to mapOf("screens" to "/dashboard")),
            ),
            "edges" to listOf(
                mapOf("id" to "e-1", "from" to "f-1", "to" to "f-2"),
            ),
        )
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "SaaS")
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo("f-1")
        assertThat(result[0].scopes).containsExactly(FeatureScope.BACKEND)
        val dashboard = result.first { it.id == "f-2" }
        assertThat(dashboard.dependsOn).containsExactly("f-1")
    }

    @Test
    fun `library category defaults to empty scopes`() {
        val raw = listOf(mapOf("title" to "Core API"))
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "Library")
        assertThat(result[0].scopes).isEmpty()
    }

    @Test
    fun `api category defaults to backend scope`() {
        val raw = listOf(mapOf("title" to "Public API"))
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "API")
        assertThat(result[0].scopes).containsExactly(FeatureScope.BACKEND)
    }

    @Test
    fun `empty input returns empty list`() {
        assertThat(IdeaToSpecAgent.parseWizardFeatures(null, category = "SaaS")).isEmpty()
        assertThat(IdeaToSpecAgent.parseWizardFeatures(emptyList<Any>(), category = "SaaS")).isEmpty()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.ParseWizardFeaturesTest"`
Expected: FAIL — signature mismatch, missing fields.

- [ ] **Step 3: Implement `WizardFeatureInput` refactor + extended parser**

Modify `backend/src/main/kotlin/com/agentwork/productspecagent/service/TaskService.kt` — replace lines 9-13:

```kotlin
data class WizardFeatureInput(
    val id: String,
    val title: String,
    val description: String,
    val scopes: Set<com.agentwork.productspecagent.domain.FeatureScope>,
    val scopeFields: Map<String, String>,
    val dependsOn: List<String> = emptyList(),
)
```

Modify `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt` — replace the existing private `parseWizardFeatures` helper (around lines 257-300) with a **companion-object** version that accepts the category + supports both legacy flat list and new graph shape:

```kotlin
// In IdeaToSpecAgent class body — move helper into companion object for static access
companion object {
    private val uuid = java.util.UUID::randomUUID

    fun parseWizardFeatures(raw: Any?, category: String?): List<WizardFeatureInput> {
        val defaultScopes = defaultScopesFor(category)
        val featuresRaw: List<Any?>
        val edgesRaw: List<Any?>

        when (raw) {
            is Map<*, *> -> {
                featuresRaw = (raw["features"] as? List<*>) ?: emptyList<Any>()
                edgesRaw = (raw["edges"] as? List<*>) ?: emptyList<Any>()
            }
            is List<*> -> {
                featuresRaw = raw
                edgesRaw = emptyList<Any>()
            }
            else -> return emptyList()
        }

        // Parse edges first: map target -> list of source IDs (dependsOn)
        val dependsByTarget = mutableMapOf<String, MutableList<String>>()
        for (e in edgesRaw) {
            val m = e as? Map<*, *> ?: continue
            val from = m["from"]?.toString() ?: continue
            val to = m["to"]?.toString() ?: continue
            dependsByTarget.getOrPut(to) { mutableListOf() }.add(from)
        }

        val result = mutableListOf<WizardFeatureInput>()
        for (f in featuresRaw) {
            val m = f as? Map<*, *> ?: if (f is String) mapOf("title" to f) else continue
            val title = (m["title"] ?: m["name"])?.toString()?.trim()
            if (title.isNullOrBlank()) continue
            val id = m["id"]?.toString()?.ifBlank { null } ?: uuid.get().toString()
            val description = (m["description"] ?: m["desc"])?.toString() ?: ""
            val scopes = parseScopes(m["scopes"], defaultScopes)
            @Suppress("UNCHECKED_CAST")
            val scopeFields = (m["scopeFields"] as? Map<String, String>) ?: emptyMap()
            result.add(WizardFeatureInput(
                id = id,
                title = title,
                description = description,
                scopes = scopes,
                scopeFields = scopeFields,
                dependsOn = dependsByTarget[id] ?: emptyList(),
            ))
        }
        return result
    }

    private fun parseScopes(raw: Any?, fallback: Set<FeatureScope>): Set<FeatureScope> {
        val list = raw as? List<*> ?: return fallback
        return list.mapNotNull { s ->
            runCatching { FeatureScope.valueOf(s.toString().uppercase()) }.getOrNull()
        }.toSet().ifEmpty { fallback }
    }

    private fun defaultScopesFor(category: String?): Set<FeatureScope> = when (category) {
        "SaaS", "Mobile App", "Desktop App" -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
        "API", "CLI Tool" -> setOf(FeatureScope.BACKEND)
        "Library" -> emptySet()
        else -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
    }
}
```

Also add `import com.agentwork.productspecagent.domain.FeatureScope` to the top of `IdeaToSpecAgent.kt` if not present.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.ParseWizardFeaturesTest"`
Expected: PASS — 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/TaskService.kt backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt backend/src/test/kotlin/com/agentwork/productspecagent/agent/ParseWizardFeaturesTest.kt
git commit -m "feat(backend): extend WizardFeatureInput and parseWizardFeatures with scopes+edges (Feature 22)"
```

---

### Task 3: `PlanGeneratorAgent.generatePlanForFeature` refactor + `epicEstimate` prompt

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgent.kt:38-72` + `134-184` + `206-212`
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgentScopeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/src/test/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgentScopeTest.kt
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.TaskType
import com.agentwork.productspecagent.service.WizardFeatureInput
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlanGeneratorAgentScopeTest {
    private fun agent(response: String): PlanGeneratorAgent {
        val builder = SpecContextBuilder(mockProjectService(), mockDecisionService(), mockClarificationService())
        return object : PlanGeneratorAgent(builder) {
            override suspend fun runAgent(prompt: String): String {
                lastPrompt = prompt
                return response
            }
            var lastPrompt: String = ""
        }
    }

    @Test
    fun `frontend-only feature prompt contains UI hint`() = runBlocking {
        val a = agent("""{"epicEstimate":"M","stories":[]}""") as PlanGeneratorAgent & { val lastPrompt: String }
        a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Login UI", description = "",
                scopes = setOf(FeatureScope.FRONTEND),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        assertThat((a as Any).javaClass.getDeclaredField("lastPrompt").also { it.isAccessible = true }.get(a) as String)
            .contains("Frontend-only")
            .contains("UI")
    }

    @Test
    fun `epic uses epicEstimate from LLM response`() = runBlocking {
        val a = agent("""{"epicEstimate":"L","stories":[]}""")
        val tasks = a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Feature A", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        val epic = tasks.first { it.type == TaskType.EPIC }
        assertThat(epic.estimate).isEqualTo("L")
    }

    @Test
    fun `invalid json falls back to estimate M and no stories`() = runBlocking {
        val a = agent("not json")
        val tasks = a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Feature A", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        val epic = tasks.single()
        assertThat(epic.type).isEqualTo(TaskType.EPIC)
        assertThat(epic.estimate).isEqualTo("M")
    }

    @Test
    fun `library feature (empty scopes) gets library hint`() = runBlocking {
        var captured = ""
        val a = object : PlanGeneratorAgent(SpecContextBuilder(mockProjectService(), mockDecisionService(), mockClarificationService())) {
            override suspend fun runAgent(prompt: String): String {
                captured = prompt
                return """{"epicEstimate":"S","stories":[]}"""
            }
        }
        a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Core API", description = "",
                scopes = emptySet(),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        assertThat(captured).contains("Library-Komponente")
    }

    // Helpers: reuse the mock pattern from existing DecisionAgentTest / PlanGeneratorAgentTest
    private fun mockProjectService() = /* existing mock pattern */ TODO("copy from existing PlanGeneratorAgentTest")
    private fun mockDecisionService() = TODO("copy from existing PlanGeneratorAgentTest")
    private fun mockClarificationService() = TODO("copy from existing PlanGeneratorAgentTest")
}
```

> **Note for implementer:** Re-use the exact mock helpers already present in `backend/src/test/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgentTest.kt`. Do not copy-paste TODOs — read that file and replicate its builder pattern.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.PlanGeneratorAgentScopeTest"`
Expected: FAIL — new signature not supported, `epicEstimate` ignored.

- [ ] **Step 3: Implement refactor**

In `backend/src/main/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgent.kt`:

Add imports:
```kotlin
import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.service.WizardFeatureInput
```

Replace the existing `generatePlanForFeature` (lines 38-72) with:

```kotlin
suspend fun generatePlanForFeature(
    projectId: String,
    input: WizardFeatureInput,
    startPriority: Int,
): List<SpecTask> {
    val context = contextBuilder.buildContext(projectId)
    val prompt = buildString {
        appendLine("Break down a single product feature into a small implementation plan.")
        appendLine()
        appendLine("Feature:")
        appendLine("- Title: ${input.title}")
        appendLine("- Description: ${input.description}")
        appendLine("- Scopes: ${input.scopes.joinToString(", ") { it.name }.ifBlank { "(Library / Core)" }}")
        if (input.scopeFields.isNotEmpty()) {
            appendLine("- Scope fields:")
            for ((k, v) in input.scopeFields) {
                if (v.isNotBlank()) appendLine("  - $k: $v")
            }
        }
        appendLine()
        appendScopeHint(input.scopes)
        appendLine()
        appendLine("Project context:")
        appendLine(context)
        appendLine()
        appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
        appendLine("""{"epicEstimate":"M","stories":[{"title":"Story title","description":"desc","estimate":"M","tasks":[{"title":"Task","description":"desc","estimate":"S"}]}]}""")
        appendLine("Generate 1-3 stories, each with 1-3 tasks. epicEstimate must be one of XS, S, M, L, XL.")
        appendLine("Use the same language as the feature title/description.")
    }
    val rawResponse = runAgent(prompt)
    return parseFeaturePlanResponse(rawResponse, projectId, input, startPriority)
}

private fun StringBuilder.appendScopeHint(scopes: Set<FeatureScope>) {
    val hint = when {
        scopes == setOf(FeatureScope.FRONTEND) ->
            "This feature is Frontend-only. Generate ONLY UI-focused stories (Components, Screens, State, User-Interactions). No API or DB stories."
        scopes == setOf(FeatureScope.BACKEND) ->
            "This feature is Backend-only. Generate ONLY API / Data / Service stories. No UI stories."
        scopes.isEmpty() ->
            "This feature is a Library-Komponente. Focus stories on Public API, Types and Usage Examples."
        else -> return // both scopes → no hint
    }
    appendLine(hint)
}
```

Replace `parseFeaturePlanResponse` (lines 134-184) with:

```kotlin
private fun parseFeaturePlanResponse(
    raw: String,
    projectId: String,
    input: WizardFeatureInput,
    startPriority: Int,
): List<SpecTask> {
    val jsonStr = raw.replace("```json", "").replace("```", "").trim()
    val now = Instant.now().toString()
    val tasks = mutableListOf<SpecTask>()
    var priority = startPriority

    val parsed = runCatching { json.decodeFromString<FeaturePlanResponse>(jsonStr) }.getOrNull()
    val epicEstimate = parsed?.epicEstimate?.takeIf { it in setOf("XS", "S", "M", "L", "XL") } ?: "M"

    val epicId = java.util.UUID.randomUUID().toString()
    tasks.add(SpecTask(
        id = epicId, projectId = projectId, type = TaskType.EPIC,
        title = input.title, description = input.description,
        estimate = epicEstimate, priority = priority++,
        specSection = FlowStepType.FEATURES,
        source = TaskSource.WIZARD,
        createdAt = now, updatedAt = now
    ))

    for (storyDef in parsed?.stories ?: emptyList()) {
        val storyId = java.util.UUID.randomUUID().toString()
        tasks.add(SpecTask(
            id = storyId, projectId = projectId, parentId = epicId,
            type = TaskType.STORY, title = storyDef.title,
            description = storyDef.description, estimate = storyDef.estimate,
            priority = priority++, source = TaskSource.WIZARD,
            createdAt = now, updatedAt = now
        ))
        for (taskDef in storyDef.tasks) {
            tasks.add(SpecTask(
                id = java.util.UUID.randomUUID().toString(), projectId = projectId,
                parentId = storyId, type = TaskType.TASK,
                title = taskDef.title, description = taskDef.description,
                estimate = taskDef.estimate, priority = priority++,
                source = TaskSource.WIZARD,
                createdAt = now, updatedAt = now
            ))
        }
    }
    return tasks
}
```

Extend the `FeaturePlanResponse` data class (line 212):

```kotlin
@Serializable
private data class FeaturePlanResponse(
    val epicEstimate: String = "M",
    val stories: List<StoryDef> = emptyList(),
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.PlanGeneratorAgentScopeTest" --tests "com.agentwork.productspecagent.agent.PlanGeneratorAgentTest"`
Expected: PASS — both new and existing plan-generator tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgent.kt backend/src/test/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgentScopeTest.kt
git commit -m "feat(backend): PlanGeneratorAgent scope-aware prompt + epicEstimate (Feature 22)"
```

---

### Task 4: `TaskService.replaceWizardFeatureTasks` — 2-phase EPIC gen + dependency mapping

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/TaskService.kt:33-74`
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/service/TaskServiceWizardGraphTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/src/test/kotlin/com/agentwork/productspecagent/service/TaskServiceWizardGraphTest.kt
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.TaskStorage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class TaskServiceWizardGraphTest {
    @Test
    fun `phase 2 maps wizard-feature dependsOn ids to generated epic ids`(@TempDir tmp: Path) = runBlocking {
        val storage = TaskStorage(tmp.toString())
        // Fake agent: emits one EPIC per call, no stories, deterministic id sequencing
        val agent = object : PlanGeneratorAgent(mockContextBuilder()) {
            var callCount = 0
            override suspend fun generatePlanForFeature(
                projectId: String,
                input: WizardFeatureInput,
                startPriority: Int,
            ): List<SpecTask> {
                callCount++
                val now = Instant.now().toString()
                return listOf(SpecTask(
                    id = "epic-${input.id}",
                    projectId = projectId,
                    type = TaskType.EPIC,
                    title = input.title,
                    estimate = "M",
                    priority = startPriority,
                    specSection = FlowStepType.FEATURES,
                    source = TaskSource.WIZARD,
                    createdAt = now, updatedAt = now,
                ))
            }
        }
        val svc = TaskService(storage, agent)

        val inputs = listOf(
            WizardFeatureInput(id = "f-1", title = "Login", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()),
            WizardFeatureInput(id = "f-2", title = "Dashboard", description = "",
                scopes = setOf(FeatureScope.FRONTEND), scopeFields = emptyMap(), dependsOn = listOf("f-1")),
        )
        val result = svc.replaceWizardFeatureTasks("p1", inputs)

        val dashboardEpic = result.single { it.title == "Dashboard" }
        assertThat(dashboardEpic.dependencies).containsExactly("epic-f-1")
        val loginEpic = result.single { it.title == "Login" }
        assertThat(loginEpic.dependencies).isEmpty()
        // And dependencies are persisted, not only returned
        assertThat(storage.listTasks("p1").single { it.title == "Dashboard" }.dependencies)
            .containsExactly("epic-f-1")
    }

    @Test
    fun `existing non-wizard tasks remain untouched`(@TempDir tmp: Path) = runBlocking {
        val storage = TaskStorage(tmp.toString())
        val now = Instant.now().toString()
        storage.saveTask(SpecTask(
            id = "manual-1", projectId = "p1", type = TaskType.TASK, title = "Manual task",
            priority = 100, createdAt = now, updatedAt = now, source = null,
        ))
        val agent = fakeAgentReturningOneEpicPerInput()
        val svc = TaskService(storage, agent)
        svc.replaceWizardFeatureTasks("p1", listOf(
            WizardFeatureInput(id = "f-1", title = "New", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap())
        ))
        assertThat(storage.listTasks("p1").map { it.id }).contains("manual-1")
    }

    private fun mockContextBuilder(): com.agentwork.productspecagent.agent.SpecContextBuilder = TODO("reuse helper from existing TaskService tests")
    private fun fakeAgentReturningOneEpicPerInput(): PlanGeneratorAgent = TODO("same pattern as above")
}
```

> **Note:** copy the mock helpers from existing `PlanGeneratorAgentTest` / `TaskServiceTest`. Tests must not contain TODOs in the final version.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.productspecagent.service.TaskServiceWizardGraphTest"`
Expected: FAIL — `generatePlanForFeature` signature mismatch, dependencies not mapped.

- [ ] **Step 3: Implement 2-phase replacement**

Replace the loop body in `backend/src/main/kotlin/com/agentwork/productspecagent/service/TaskService.kt:60-72` with:

```kotlin
// Phase 1: generate EPIC + stories + tasks per wizard feature (no dependencies yet)
val byWizardId = LinkedHashMap<String, SpecTask>()  // feature.id -> epic SpecTask
val created = mutableListOf<SpecTask>()
for (feature in features) {
    val tasks = agent.generatePlanForFeature(
        projectId = projectId,
        input = feature,
        startPriority = nextPriority,
    )
    val epic = tasks.first { it.type == TaskType.EPIC }
    byWizardId[feature.id] = epic
    created.addAll(tasks)
    nextPriority += tasks.size
}

// Phase 2: map dependsOn (wizard-feature-ids) to generated EPIC task-ids
for (feature in features) {
    val depsAsTaskIds = feature.dependsOn.mapNotNull { byWizardId[it]?.id }
    if (depsAsTaskIds.isEmpty()) continue
    val epic = byWizardId[feature.id] ?: continue
    val updatedEpic = epic.copy(dependencies = depsAsTaskIds, updatedAt = Instant.now().toString())
    // replace in created list
    val idx = created.indexOfFirst { it.id == epic.id }
    if (idx >= 0) created[idx] = updatedEpic
    byWizardId[feature.id] = updatedEpic
}

// Persist AFTER mapping (single write per task)
for (t in created) storage.saveTask(t)
return created
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.service.TaskServiceWizardGraphTest" --tests "com.agentwork.productspecagent.service.TaskServiceTest"`
Expected: PASS — new and existing TaskService tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/TaskService.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/TaskServiceWizardGraphTest.kt
git commit -m "feat(backend): 2-phase EPIC gen with dependency mapping (Feature 22)"
```

---

### Task 5: `SpecContextBuilder` — graph block in wizard + proposal context

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilder.kt`
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilderGraphTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/src/test/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilderGraphTest.kt
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.WizardFeatureInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpecContextBuilderGraphTest {
    @Test
    fun `renders features block with scopes and dependencies`() {
        val inputs = listOf(
            WizardFeatureInput(id = "f-1", title = "Login", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = mapOf("apiEndpoints" to "POST /auth/login"),
                dependsOn = emptyList()),
            WizardFeatureInput(id = "f-2", title = "Dashboard", description = "",
                scopes = setOf(FeatureScope.FRONTEND),
                scopeFields = mapOf("screens" to "/dashboard"),
                dependsOn = listOf("f-1")),
        )
        val rendered = SpecContextBuilder.renderFeaturesBlock(inputs, category = "SaaS")
        assertThat(rendered)
            .contains("Features & Dependencies (Category: SaaS):")
            .contains("[f-1] Login (Backend) — depends on: —")
            .contains("[f-2] Dashboard (Frontend) — depends on: f-1")
            .contains("API: POST /auth/login")
            .contains("Screens: /dashboard")
    }

    @Test
    fun `library feature shows Core label`() {
        val input = WizardFeatureInput(id = "f-1", title = "Utils", description = "",
            scopes = emptySet(), scopeFields = emptyMap(), dependsOn = emptyList())
        val rendered = SpecContextBuilder.renderFeaturesBlock(listOf(input), category = "Library")
        assertThat(rendered).contains("(Core)")
    }

    @Test
    fun `isolated nodes are summarized`() {
        val inputs = listOf(
            WizardFeatureInput(id = "a", title = "A", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()),
            WizardFeatureInput(id = "b", title = "B", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = listOf("a")),
            WizardFeatureInput(id = "c", title = "Loner", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()),
        )
        val rendered = SpecContextBuilder.renderFeaturesBlock(inputs, category = "SaaS")
        assertThat(rendered).contains("Isolated nodes: c")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.SpecContextBuilderGraphTest"`
Expected: FAIL — `renderFeaturesBlock` not present.

- [ ] **Step 3: Add `renderFeaturesBlock` + wire into `buildWizardContext`**

Add to `backend/src/main/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilder.kt`:

```kotlin
companion object {
    fun renderFeaturesBlock(
        features: List<com.agentwork.productspecagent.service.WizardFeatureInput>,
        category: String?,
    ): String {
        if (features.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("Features & Dependencies (Category: ${category ?: "—"}):")

        // incoming edges per feature = dependsOn
        // outgoing edges per feature = find in other features' dependsOn
        val hasOutgoing = mutableSetOf<String>()
        features.forEach { f -> f.dependsOn.forEach { hasOutgoing.add(it) } }

        val isolated = features.filter { it.dependsOn.isEmpty() && it.id !in hasOutgoing }

        for (f in features) {
            val scopeLabel = when {
                f.scopes == setOf(FeatureScope.FRONTEND) -> "Frontend"
                f.scopes == setOf(FeatureScope.BACKEND) -> "Backend"
                f.scopes.containsAll(setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)) -> "Frontend + Backend"
                f.scopes.isEmpty() -> "Core"
                else -> f.scopes.joinToString("+")
            }
            val deps = if (f.dependsOn.isEmpty()) "—" else f.dependsOn.joinToString(", ")
            sb.appendLine("- [${f.id}] ${f.title} ($scopeLabel) — depends on: $deps")
            f.scopeFields.filter { it.value.isNotBlank() }.forEach { (k, v) ->
                sb.appendLine("  ${k.replaceFirstChar { it.uppercase() }}: $v")
            }
        }
        if (isolated.isNotEmpty()) {
            sb.appendLine("Isolated nodes: ${isolated.joinToString(", ") { it.id }}")
        } else {
            sb.appendLine("Isolated nodes: —")
        }
        return sb.toString().trimEnd()
    }
}
```

Modify `buildWizardContext` in the same file: when `currentStep == FlowStepType.FEATURES`, append the rendered block to the prompt context. Concretely, locate the existing context assembly for the FEATURES step and append `"\n\n" + renderFeaturesBlock(parsedFeatures, categoryFromIdeaFields)`.

Also add a new method for the proposal context:

```kotlin
fun buildProposalContext(projectId: String): String {
    val sb = StringBuilder()
    listOf("idea.md", "problem.md", "target_audience.md", "scope.md", "mvp.md").forEach { f ->
        projectService.readSpecFile(projectId, f)?.let {
            sb.appendLine("## $f").appendLine(it).appendLine()
        }
    }
    val category = projectService.getProject(projectId).project
        .let { /* extract IDEA.category from flow state / wizard fields */ "TODO_read_category" }
    sb.appendLine("Category: $category")
    return sb.toString().trim()
}
```

> **Implementer note:** reading category from wizard fields follows the same pattern already used elsewhere (`projectService.readWizardFields("IDEA")["category"]` — check current API). The `TODO_read_category` marker above is a placeholder for the exact call site you discover during implementation.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.SpecContextBuilderGraphTest" --tests "com.agentwork.productspecagent.agent.SpecContextBuilderTest" --tests "com.agentwork.productspecagent.agent.SpecContextBuilderWizardTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilder.kt backend/src/test/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilderGraphTest.kt
git commit -m "feat(backend): SpecContextBuilder renders graph block for Feature 22"
```

---

### Task 6: `IdeaToSpecAgent.processWizardStep` validator additions

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt:~210-220` (FEATURES branch) and `MARKER_REMINDER`
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgentTest.kt` (extend existing)

- [ ] **Step 1: Extend the existing IdeaToSpecAgentTest — add FEATURES-graph test**

Append to the existing `IdeaToSpecAgentTest`:

```kotlin
@Test
fun `FEATURES step passes graph block to agent prompt`() = runBlocking {
    val capturedPrompts = mutableListOf<String>()
    val agent = createAgent(
        runner = object : KoogAgentRunner {
            override suspend fun run(system: String, user: String): String {
                capturedPrompts.add(user); return "OK."
            }
        }
    )
    val request = WizardStepRequest(
        stepType = FlowStepType.FEATURES,
        fields = mapOf(
            "features" to listOf(
                mapOf("id" to "f-1", "title" to "Login",
                      "scopes" to listOf("BACKEND"),
                      "scopeFields" to mapOf("apiEndpoints" to "POST /auth/login")),
            ),
            "edges" to emptyList<Any>()
        )
    )
    agent.processWizardStep(projectId, request)
    assertThat(capturedPrompts).isNotEmpty
    assertThat(capturedPrompts.last()).contains("Features & Dependencies").contains("[f-1] Login (Backend)")
}

@Test
fun `FEATURES step still calls replaceWizardFeatureTasks when not blocked`() = runBlocking {
    // verify taskService.replaceWizardFeatureTasks invoked with new WizardFeatureInput shape
    // (re-use the existing spy pattern in the suite)
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.IdeaToSpecAgentTest"`
Expected: FAIL on new test only.

- [ ] **Step 3: Implementation**

In `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`:

- Update the FEATURES branch (where `parseWizardFeatures` is called) to pass `category` from the project's IDEA-step fields (read via `projectService` or cached on the flow-state) and use the companion-object helper. The parsed list must be fed both into the agent prompt (via `SpecContextBuilder.buildWizardContext` extended in Task 5) AND into `taskService.replaceWizardFeatureTasks`.

Example adjustment:

```kotlin
if (currentStepType == FlowStepType.FEATURES) {
    val category = projectService.readWizardField("IDEA", "category")  // or equivalent accessor
    val wizardFeatures = parseWizardFeatures(fields, category)
    if (!isBlocked && wizardFeatures.isNotEmpty()) {
        runCatching { taskService.replaceWizardFeatureTasks(projectId, wizardFeatures) }
            .onFailure { logger.warn("Task regeneration failed", it) }
    }
}
```

Also extend the system prompt `MARKER_REMINDER` (or similar block fed to the agent) to include:

```
Additional validator rules for FEATURES step:
- If the graph contains isolated nodes (no incoming and no outgoing edges), ask the user whether that is intentional.
- If a feature's scope seems inconsistent with its title (e.g. "Login UI" with BACKEND only), emit a clarification.
- If the category is SaaS / Mobile / Desktop and obvious core features are missing (e.g. Auth, Registration), emit a clarification.
Otherwise remain silent.
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.IdeaToSpecAgentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt backend/src/test/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgentTest.kt
git commit -m "feat(backend): IdeaToSpecAgent validator + graph-context for Feature 22"
```

---

### Task 7: `FeatureProposalAgent` (new)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgent.kt`
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgentTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/src/test/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgentTest.kt
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureProposalAgentTest {
    @Test
    fun `parses JSON response into graph with auto-assigned IDs and edge ID translation`() = runBlocking {
        val mock = object : FeatureProposalAgent(contextBuilderStub(category = "SaaS")) {
            override suspend fun runAgent(prompt: String): String = """
                {"features":[
                  {"title":"Login","scopes":["BACKEND"],"description":"Auth","scopeFields":{"apiEndpoints":"POST /auth/login"}},
                  {"title":"Dashboard","scopes":["FRONTEND"],"description":"Main","scopeFields":{"screens":"/dashboard"}}
                ],"edges":[{"fromTitle":"Login","toTitle":"Dashboard"}]}
            """
        }
        val graph = mock.proposeFeatures("p1")
        assertThat(graph.features).hasSize(2)
        assertThat(graph.features[0].id).isNotBlank()
        assertThat(graph.edges).hasSize(1)
        val login = graph.features.single { it.title == "Login" }
        val dashboard = graph.features.single { it.title == "Dashboard" }
        assertThat(graph.edges[0].from).isEqualTo(login.id)
        assertThat(graph.edges[0].to).isEqualTo(dashboard.id)
    }

    @Test
    fun `malformed JSON throws ProposalParseException`() = runBlocking {
        val mock = object : FeatureProposalAgent(contextBuilderStub(category = "SaaS")) {
            override suspend fun runAgent(prompt: String): String = "not a json"
        }
        val ex = runCatching { mock.proposeFeatures("p1") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(ProposalParseException::class.java)
    }

    @Test
    fun `respects category for default scopes when LLM omits them`() = runBlocking {
        val mock = object : FeatureProposalAgent(contextBuilderStub(category = "Library")) {
            override suspend fun runAgent(prompt: String): String = """
                {"features":[{"title":"Utils","description":""}],"edges":[]}
            """
        }
        val graph = mock.proposeFeatures("p1")
        assertThat(graph.features[0].scopes).isEmpty()  // Library default
    }

    private fun contextBuilderStub(category: String): SpecContextBuilder = TODO("reuse pattern from SpecContextBuilderTest")
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.FeatureProposalAgentTest"`
Expected: FAIL — `FeatureProposalAgent` / `ProposalParseException` missing.

- [ ] **Step 3: Implement**

```kotlin
// backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgent.kt
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.util.UUID

class ProposalParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Service
open class FeatureProposalAgent(
    private val contextBuilder: SpecContextBuilder,
    private val koogRunner: KoogAgentRunner? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun proposeFeatures(projectId: String): WizardFeatureGraph {
        val context = contextBuilder.buildProposalContext(projectId)
        val prompt = buildString {
            appendLine("Based on the project's idea/problem/audience/scope/mvp, propose a concrete feature list with dependencies.")
            appendLine()
            appendLine(context)
            appendLine()
            appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
            appendLine("""{"features":[{"title":"...","scopes":["FRONTEND"|"BACKEND"],"description":"...","scopeFields":{"...":"..."}}],"edges":[{"fromTitle":"A","toTitle":"B"}]}""")
            appendLine("For Library projects, omit scopes. For API/CLI, use only BACKEND.")
            appendLine("fromTitle is the feature that MUST be built first; toTitle depends on it.")
        }
        val raw = runAgent(prompt)
        return parseResponse(raw, category = extractCategory(context))
    }

    protected open suspend fun runAgent(prompt: String): String {
        return koogRunner?.run(
            "You are a product planner. Produce a DAG of features with dependencies.",
            prompt,
        ) ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
    }

    private fun extractCategory(context: String): String? =
        Regex("Category:\\s*(.+)").find(context)?.groupValues?.get(1)?.trim()

    private fun parseResponse(raw: String, category: String?): WizardFeatureGraph {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val parsed = runCatching { json.decodeFromString<ProposalResponse>(jsonStr) }
            .getOrElse { throw ProposalParseException("Invalid JSON from LLM: ${it.message}", it) }

        val defaultScopes = defaultScopesFor(category)
        val titleToId = mutableMapOf<String, String>()
        val features = parsed.features.map { f ->
            val id = UUID.randomUUID().toString()
            titleToId[f.title] = id
            WizardFeature(
                id = id,
                title = f.title,
                scopes = f.scopes?.mapNotNull { runCatching { FeatureScope.valueOf(it.uppercase()) }.getOrNull() }
                    ?.toSet() ?: defaultScopes,
                description = f.description ?: "",
                scopeFields = f.scopeFields ?: emptyMap(),
                position = GraphPosition(),  // frontend/plugin will layout
            )
        }
        val edges = parsed.edges.mapNotNull { e ->
            val from = titleToId[e.fromTitle] ?: return@mapNotNull null
            val to = titleToId[e.toTitle] ?: return@mapNotNull null
            WizardFeatureEdge(id = UUID.randomUUID().toString(), from = from, to = to)
        }
        return WizardFeatureGraph(features = features, edges = edges)
    }

    private fun defaultScopesFor(category: String?): Set<FeatureScope> = when (category) {
        "SaaS", "Mobile App", "Desktop App" -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
        "API", "CLI Tool" -> setOf(FeatureScope.BACKEND)
        "Library" -> emptySet()
        else -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
    }

    @Serializable
    private data class ProposalResponse(
        val features: List<FeatureProposalDef> = emptyList(),
        val edges: List<EdgeProposalDef> = emptyList(),
    )

    @Serializable
    private data class FeatureProposalDef(
        val title: String,
        val scopes: List<String>? = null,
        val description: String? = null,
        val scopeFields: Map<String, String>? = null,
    )

    @Serializable
    private data class EdgeProposalDef(val fromTitle: String, val toTitle: String)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.FeatureProposalAgentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgent.kt backend/src/test/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgentTest.kt
git commit -m "feat(backend): add FeatureProposalAgent for Feature 22"
```

---

### Task 8: `FeatureProposalController` + wiring

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/FeatureProposalController.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ApiModels.kt` (response DTO)
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/api/FeatureProposalControllerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/src/test/kotlin/com/agentwork/productspecagent/api/FeatureProposalControllerTest.kt
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.FeatureProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import com.agentwork.productspecagent.domain.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

@SpringBootTest
@AutoConfigureMockMvc
class FeatureProposalControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var om: ObjectMapper
    @MockBean lateinit var agent: FeatureProposalAgent

    @Test
    fun `POST features propose returns 200 with graph`() {
        runBlocking {
            whenever(agent.proposeFeatures("p1")).thenReturn(
                WizardFeatureGraph(
                    features = listOf(WizardFeature(id="f-1", title="A", scopes = setOf(FeatureScope.BACKEND))),
                    edges = emptyList()
                )
            )
        }
        mvc.post("/api/v1/projects/p1/features/propose") { /* no body */ }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.features[0].title") { value("A") } }
    }

    @Test
    fun `POST features propose returns 422 on parse error`() {
        runBlocking {
            whenever(agent.proposeFeatures("p1")).thenThrow(ProposalParseException("bad"))
        }
        mvc.post("/api/v1/projects/p1/features/propose") { /* no body */ }
            .andExpect { status { is4xxClientError() } }
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.api.FeatureProposalControllerTest"`
Expected: FAIL — controller not present, endpoint 404.

- [ ] **Step 3: Implement controller + DTO**

```kotlin
// backend/src/main/kotlin/com/agentwork/productspecagent/api/FeatureProposalController.kt
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.FeatureProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import com.agentwork.productspecagent.domain.WizardFeatureGraph
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}/features")
class FeatureProposalController(private val agent: FeatureProposalAgent) {

    @PostMapping("/propose")
    fun propose(@PathVariable projectId: String): ResponseEntity<Any> = runBlocking {
        try {
            ResponseEntity.ok(agent.proposeFeatures(projectId))
        } catch (e: ProposalParseException) {
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("error" to (e.message ?: "Parsing failed")))
        }
    }
}
```

No `ApiModels.kt` change needed — `WizardFeatureGraph` serializes via Jackson through the existing kotlinx-serialization/Jackson interop (verify the existing `JacksonConfig.kt` already supports kotlinx-serialized classes; if not, add a `KotlinxSerializationJsonModule`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.api.FeatureProposalControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/FeatureProposalController.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/FeatureProposalControllerTest.kt
git commit -m "feat(backend): add POST features/propose endpoint for Feature 22"
```

---

### Task 9: `ScaffoldContextBuilder` — real dependencies + scope sections + Mustache

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt:26-48`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/DocsScaffoldGenerator.kt:22-37` (add `scope`, `scopeFields` to `FeatureContext`)
- Modify: `backend/src/main/resources/templates/scaffold/docs/features/feature.md.mustache`
- Test:   `backend/src/test/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilderTest.kt` (extend)

- [ ] **Step 1: Extend the test**

In `ScaffoldContextBuilderTest.kt`, add:

```kotlin
@Test
fun `dependencies render feature titles not Feature N-1`() {
    // arrange: two wizard-source EPICs, second depends on first
    val now = Instant.now().toString()
    val epic1 = SpecTask(id="e1", projectId="p1", type=TaskType.EPIC, title="Login",
        estimate="M", priority=0, source=TaskSource.WIZARD, createdAt=now, updatedAt=now)
    val epic2 = SpecTask(id="e2", projectId="p1", type=TaskType.EPIC, title="Dashboard",
        estimate="M", priority=1, source=TaskSource.WIZARD, dependencies=listOf("e1"),
        createdAt=now, updatedAt=now)
    mockTasks(listOf(epic1, epic2))

    val ctx = builder.build("p1")
    assertThat(ctx.features[0].dependencies).isEqualTo("—")   // Login has none
    assertThat(ctx.features[1].dependencies).isEqualTo("Login")  // Dashboard depends on Login
}
```

- [ ] **Step 2: Verify failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.export.ScaffoldContextBuilderTest"`
Expected: FAIL — current builder emits `"Feature 1"` string.

- [ ] **Step 3: Implement real dependency rendering**

In `ScaffoldContextBuilder.kt`, replace the current `dependencies = if (i == 0) "—" else "Feature $i"` with:

```kotlin
val idToTitle = epics.associate { it.id to it.title }
// inside mapIndexed loop:
dependencies = if (epic.dependencies.isEmpty()) "—"
               else epic.dependencies.mapNotNull { idToTitle[it] }.joinToString(", ").ifBlank { "—" },
```

Also extend `FeatureContext` (in `DocsScaffoldGenerator.kt`):

```kotlin
data class FeatureContext(
    val number: Int,
    val title: String,
    val slug: String,
    val filename: String,
    val description: String,
    val estimate: String,
    val dependencies: String,
    val stories: List<StoryContext>,
    val acceptanceCriteria: List<TaskContext>,
    val tasks: List<TaskContext>,
    val scope: String? = null,          // NEW: "Frontend" / "Backend" / "Frontend + Backend" / "Core"
    val scopeFields: Map<String, String> = emptyMap(),  // NEW
)
```

And in `ScaffoldContextBuilder.build`, populate `scope` + `scopeFields` by reading them from the wizard-state snapshot on the project. Pseudocode for the relevant lookup (adapt to existing access methods):

```kotlin
val wizardFeatures = parseWizardFeaturesForProject(projectId)  // use IdeaToSpecAgent.parseWizardFeatures
val byTitle = wizardFeatures.associateBy { it.title }
// in mapIndexed:
val wizardMatch = byTitle[epic.title]
FeatureContext(
    ...,
    scope = scopeLabel(wizardMatch?.scopes),
    scopeFields = wizardMatch?.scopeFields ?: emptyMap(),
)

private fun scopeLabel(scopes: Set<FeatureScope>?): String? = when {
    scopes == null -> null
    scopes == setOf(FeatureScope.FRONTEND) -> "Frontend"
    scopes == setOf(FeatureScope.BACKEND) -> "Backend"
    scopes.containsAll(setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)) -> "Frontend + Backend"
    scopes.isEmpty() -> "Core"
    else -> null
}
```

Update `backend/src/main/resources/templates/scaffold/docs/features/feature.md.mustache` to:

```mustache
# Feature {{number}}: {{title}}

{{#scope}}
**Scope:** {{.}}
{{/scope}}

## Zusammenfassung
{{description}}

## User Stories
{{#stories}}
{{index}}. {{title}}{{#description}} — {{description}}{{/description}}
{{/stories}}

## Acceptance Criteria
{{#acceptanceCriteria}}
- [ ] {{title}}{{#description}}: {{description}}{{/description}}
{{/acceptanceCriteria}}

{{#scopeFields}}
{{#uiComponents}}## UI-Komponenten
{{.}}

{{/uiComponents}}
{{#screens}}## Screens
{{.}}

{{/screens}}
{{#userInteractions}}## User-Interaktionen
{{.}}

{{/userInteractions}}
{{#apiEndpoints}}## API-Endpunkte
{{.}}

{{/apiEndpoints}}
{{#dataModel}}## Datenmodell
{{.}}

{{/dataModel}}
{{#sideEffects}}## Side-Effects
{{.}}

{{/sideEffects}}
{{#publicApi}}## Public API
{{.}}

{{/publicApi}}
{{#typesExposed}}## Exponierte Types
{{.}}

{{/typesExposed}}
{{#examples}}## Beispiele
{{.}}

{{/examples}}
{{/scopeFields}}

## Abhaengigkeiten
{{dependencies}}

## Aufwand
{{estimate}}
```

> **Implementer note:** Mustache rendering of `Map<String, String>` varies by library. The JMustache integration used here exposes map keys as sections only when they are non-empty strings truthy. If the project uses a different Mustache library that doesn't, fall back to adding explicit boolean flags (`hasUiComponents`, etc.) on `FeatureContext` and switch the sections to use those.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.agentwork.productspecagent.export.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt backend/src/main/kotlin/com/agentwork/productspecagent/export/DocsScaffoldGenerator.kt backend/src/main/resources/templates/scaffold/docs/features/feature.md.mustache backend/src/test/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilderTest.kt
git commit -m "feat(backend): ScaffoldContextBuilder real deps + scope sections (Feature 22)"
```

---

## Phase B — Frontend Foundation

### Task 10: Add `rete-auto-arrange-plugin` dependency

**Files:**
- Modify: `frontend/package.json`
- Run: `npm install`

- [ ] **Step 1: Modify `frontend/package.json`**

In `"dependencies"`, add:
```json
    "rete-auto-arrange-plugin": "^2.0.0",
```
(Keep alphabetical order between existing `rete-*` entries.)

- [ ] **Step 2: Install**

```bash
cd frontend && npm install
```

Expected: no errors; `elkjs` appears in `node_modules` as transitive dependency.

- [ ] **Step 3: Smoke-verify**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore(frontend): add rete-auto-arrange-plugin for Feature 22"
```

---

### Task 11: Frontend types + `proposeFeatures` API client

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/category-step-config.ts` (add `allowedScopes`)
- Modify: `frontend/src/lib/step-field-labels.ts` (scope-specific labels)

- [ ] **Step 1: Extend `lib/api.ts`**

Add at the top (near existing `StepType`):

```ts
export type FeatureScope = "FRONTEND" | "BACKEND";

export interface GraphPosition { x: number; y: number }

export interface WizardFeature {
  id: string;
  title: string;
  scopes: FeatureScope[];    // serialized as array, normalized to Set in agent logic
  description: string;
  scopeFields: Record<string, string>;
  position: GraphPosition;
}

export interface WizardFeatureEdge {
  id: string;
  from: string;
  to: string;
}

export interface WizardFeatureGraph {
  features: WizardFeature[];
  edges: WizardFeatureEdge[];
}

export async function proposeFeatures(projectId: string): Promise<WizardFeatureGraph> {
  return apiFetch<WizardFeatureGraph>(`/api/v1/projects/${projectId}/features/propose`, {
    method: "POST",
  });
}
```

- [ ] **Step 2: Extend `category-step-config.ts`**

Add to `CategoryConfig`:

```ts
export interface CategoryConfig {
  visibleSteps: string[];
  fieldOptions: FieldOptions;
  allowedScopes: FeatureScope[];  // NEW — [] means Library (no picker)
}
```

And per category set the value (place at the end of each config object):
- `SaaS`, `Mobile App`, `Desktop App`: `allowedScopes: ["FRONTEND", "BACKEND"]`
- `API`, `CLI Tool`: `allowedScopes: ["BACKEND"]`
- `Library`: `allowedScopes: []`

Add helper:

```ts
export function getAllowedScopes(category: string | undefined): FeatureScope[] {
  if (!category) return ["FRONTEND", "BACKEND"];
  return CATEGORY_STEP_CONFIG[category as Category]?.allowedScopes ?? ["FRONTEND", "BACKEND"];
}
```

- [ ] **Step 3: Extend `step-field-labels.ts`**

Add a scope-specific label map (used in the side panel):

```ts
export const SCOPE_FIELD_LABELS: Record<string, string> = {
  uiComponents: "UI-Komponenten",
  screens: "Screens",
  userInteractions: "User-Interaktionen",
  apiEndpoints: "API-Endpunkte",
  dataModel: "Datenmodell",
  sideEffects: "Side-Effects",
  publicApi: "Public API",
  typesExposed: "Exponierte Types",
  examples: "Beispiele",
};

export const SCOPE_FIELDS_BY_SCOPE: Record<FeatureScope | "CORE", string[]> = {
  FRONTEND: ["uiComponents", "screens", "userInteractions"],
  BACKEND: ["apiEndpoints", "dataModel", "sideEffects"],
  CORE: ["publicApi", "typesExposed", "examples"],
};
```

- [ ] **Step 4: Type-check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/category-step-config.ts frontend/src/lib/step-field-labels.ts
git commit -m "feat(frontend): add WizardFeatureGraph types and proposeFeatures client (Feature 22)"
```

---

### Task 12: `lib/graph/cycleCheck.ts` — DFS cycle prevention

**Files:**
- Create: `frontend/src/lib/graph/cycleCheck.ts`

- [ ] **Step 1: Implement**

```ts
// frontend/src/lib/graph/cycleCheck.ts
import type { WizardFeatureEdge } from "@/lib/api";

/**
 * Returns true if adding a `from -> to` edge to `existing` would create a cycle.
 * Self-loops count as cycles.
 *
 * Hand-verified cases:
 *   wouldCreateCycle([], "a", "a")                                  === true
 *   wouldCreateCycle([{id:"1",from:"a",to:"b"}], "b", "a")          === true
 *   wouldCreateCycle([{id:"1",from:"a",to:"b"}], "a", "c")          === false
 *   wouldCreateCycle([{id:"1",from:"a",to:"b"},
 *                     {id:"2",from:"b",to:"c"}], "c", "a")          === true
 */
export function wouldCreateCycle(
  existing: WizardFeatureEdge[],
  from: string,
  to: string,
): boolean {
  if (from === to) return true;
  const adj = new Map<string, string[]>();
  for (const e of existing) {
    if (!adj.has(e.from)) adj.set(e.from, []);
    adj.get(e.from)!.push(e.to);
  }
  // With the new edge (from -> to): would we be able to get back to `from` from `to`?
  const visited = new Set<string>();
  const stack = [to];
  while (stack.length) {
    const node = stack.pop()!;
    if (node === from) return true;
    if (visited.has(node)) continue;
    visited.add(node);
    for (const next of adj.get(node) ?? []) stack.push(next);
  }
  return false;
}
```

- [ ] **Step 2: Type-check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/graph/cycleCheck.ts
git commit -m "feat(frontend): add DFS cycleCheck util (Feature 22)"
```

---

### Task 13: Extend `wizard-store` with graph actions

**Files:**
- Modify: `frontend/src/lib/stores/wizard-store.ts`

- [ ] **Step 1: Identify current shape**

Read the existing store to understand the `updateField(step, key, value)` action signature. The new graph actions ride on top of `updateField("FEATURES", "features", ...)` and `updateField("FEATURES", "edges", ...)`.

- [ ] **Step 2: Add graph-specific actions**

Add to the store:

```ts
import { wouldCreateCycle } from "@/lib/graph/cycleCheck";
import type { WizardFeature, WizardFeatureEdge, WizardFeatureGraph, FeatureScope } from "@/lib/api";

interface WizardStoreActions {
  // ... existing actions

  // graph helpers (read-only derived):
  getFeatures: () => WizardFeature[];
  getEdges: () => WizardFeatureEdge[];

  // mutations:
  addFeature: (f: Omit<WizardFeature, "id">) => string; // returns new id
  updateFeature: (id: string, patch: Partial<WizardFeature>) => void;
  removeFeature: (id: string) => void;                  // also removes connected edges
  addEdge: (from: string, to: string) => boolean;       // returns false if cycle
  removeEdge: (id: string) => void;
  moveFeature: (id: string, pos: { x: number; y: number }) => void;
  applyProposal: (graph: WizardFeatureGraph) => void;
}
```

Implementation sketch (fit into existing `create<WizardState>()((set, get) => ({ ... }))`):

```ts
getFeatures: () => (get().data?.steps.FEATURES?.fields.features as WizardFeature[]) ?? [],
getEdges: () => (get().data?.steps.FEATURES?.fields.edges as WizardFeatureEdge[]) ?? [],

addFeature: (f) => {
  const id = crypto.randomUUID();
  const feature: WizardFeature = { id, ...f };
  get().updateField("FEATURES", "features", [...get().getFeatures(), feature]);
  return id;
},
updateFeature: (id, patch) => {
  const next = get().getFeatures().map(f => f.id === id ? { ...f, ...patch } : f);
  get().updateField("FEATURES", "features", next);
},
removeFeature: (id) => {
  get().updateField("FEATURES", "features", get().getFeatures().filter(f => f.id !== id));
  get().updateField("FEATURES", "edges", get().getEdges().filter(e => e.from !== id && e.to !== id));
},
addEdge: (from, to) => {
  if (wouldCreateCycle(get().getEdges(), from, to)) return false;
  const edge: WizardFeatureEdge = { id: crypto.randomUUID(), from, to };
  get().updateField("FEATURES", "edges", [...get().getEdges(), edge]);
  return true;
},
removeEdge: (id) => {
  get().updateField("FEATURES", "edges", get().getEdges().filter(e => e.id !== id));
},
moveFeature: (id, pos) => {
  get().updateFeature(id, { position: pos });
},
applyProposal: (graph) => {
  get().updateField("FEATURES", "features", graph.features);
  get().updateField("FEATURES", "edges", graph.edges);
},
```

- [ ] **Step 3: Type-check + lint**

```bash
cd frontend && npx tsc --noEmit && npm run lint
```
Expected: no new errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/stores/wizard-store.ts
git commit -m "feat(frontend): wizard-store graph actions with cycle guard (Feature 22)"
```

---

## Phase C — Frontend Components

### Task 14: `FeatureNode.tsx` — Custom Rete.js node

**Files:**
- Create: `frontend/src/components/wizard/steps/features/FeatureNode.tsx`

- [ ] **Step 1: Implement**

```tsx
// frontend/src/components/wizard/steps/features/FeatureNode.tsx
"use client";
import { ClassicPreset } from "rete";
import type { FeatureScope } from "@/lib/api";

export class FeatureRNode extends ClassicPreset.Node {
  width = 200;
  height = 96;
  constructor(
    public featureId: string,
    label: string,
    public scopes: FeatureScope[] = [],
  ) {
    super(label);
  }
}

interface Props { data: FeatureRNode }

export function FeatureNodeComponent({ data }: Props) {
  const hasFrontend = data.scopes.includes("FRONTEND");
  const hasBackend = data.scopes.includes("BACKEND");
  const isCore = data.scopes.length === 0;

  return (
    <div
      className="rounded-lg border bg-card shadow-sm min-w-[200px]"
      style={{ width: data.width, minHeight: data.height }}
    >
      <div className="px-3 py-2 border-b flex items-center justify-between gap-2">
        <span className="text-sm font-medium truncate">{data.label}</span>
        <div className="flex items-center gap-1">
          {isCore && <Badge label="Core" color="neutral" />}
          {hasFrontend && <Badge label="FE" color="cyan" />}
          {hasBackend && <Badge label="BE" color="violet" />}
        </div>
      </div>
      {/* Rete.js renders ports via its renderer; the ports map is already on the node */}
    </div>
  );
}

function Badge({ label, color }: { label: string; color: "cyan" | "violet" | "neutral" }) {
  const classes = color === "cyan"
    ? "bg-cyan-500/15 text-cyan-300"
    : color === "violet"
    ? "bg-violet-500/15 text-violet-300"
    : "bg-muted text-muted-foreground";
  return <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${classes}`}>{label}</span>;
}
```

- [ ] **Step 2: Type-check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeatureNode.tsx
git commit -m "feat(frontend): FeatureNode custom Rete.js node (Feature 22)"
```

---

### Task 15: `FeatureSidePanel.tsx` — Detail editor

**Files:**
- Create: `frontend/src/components/wizard/steps/features/FeatureSidePanel.tsx`

- [ ] **Step 1: Implement**

```tsx
// frontend/src/components/wizard/steps/features/FeatureSidePanel.tsx
"use client";
import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ChipSelect } from "@/components/wizard/ChipSelect";
import { useWizardStore } from "@/lib/stores/wizard-store";
import type { FeatureScope, WizardFeature } from "@/lib/api";
import { SCOPE_FIELD_LABELS, SCOPE_FIELDS_BY_SCOPE } from "@/lib/step-field-labels";

interface Props {
  feature: WizardFeature;
  allowedScopes: FeatureScope[];    // [] means Library (Core mode)
  onDelete: () => void;
}

export function FeatureSidePanel({ feature, allowedScopes, onDelete }: Props) {
  const { updateFeature } = useWizardStore();

  const activeFieldKeys = allowedScopes.length === 0
    ? SCOPE_FIELDS_BY_SCOPE.CORE
    : allowedScopes.filter(s => feature.scopes.includes(s))
        .flatMap(s => SCOPE_FIELDS_BY_SCOPE[s]);

  function toggleScope(s: FeatureScope) {
    const next = feature.scopes.includes(s)
      ? feature.scopes.filter(x => x !== s)
      : [...feature.scopes, s];
    updateFeature(feature.id, { scopes: next });
  }

  function setField(key: string, val: string) {
    updateFeature(feature.id, {
      scopeFields: { ...feature.scopeFields, [key]: val },
    });
  }

  return (
    <div className="p-4 space-y-4 text-sm">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">Feature bearbeiten</h3>
        <button onClick={onDelete} className="text-muted-foreground hover:text-destructive">
          <Trash2 size={14} />
        </button>
      </div>

      <label className="block">
        <span className="text-xs text-muted-foreground">Titel</span>
        <input
          value={feature.title}
          onChange={(e) => updateFeature(feature.id, { title: e.target.value })}
          className="mt-1 w-full rounded-md border bg-input px-3 py-2"
        />
      </label>

      {allowedScopes.length > 1 && (
        <div>
          <span className="text-xs text-muted-foreground">Scope</span>
          <div className="mt-1 flex gap-2">
            {allowedScopes.map((s) => (
              <button
                key={s}
                onClick={() => toggleScope(s)}
                className={`px-3 py-1 rounded-full text-xs border ${
                  feature.scopes.includes(s)
                    ? "bg-primary/15 border-primary text-foreground"
                    : "border-border text-muted-foreground"
                }`}
              >
                {s === "FRONTEND" ? "Frontend" : "Backend"}
              </button>
            ))}
          </div>
        </div>
      )}

      <label className="block">
        <span className="text-xs text-muted-foreground">Beschreibung</span>
        <textarea
          value={feature.description}
          onChange={(e) => updateFeature(feature.id, { description: e.target.value })}
          rows={3}
          className="mt-1 w-full resize-none rounded-md border bg-input px-3 py-2"
        />
      </label>

      {activeFieldKeys.map((key) => (
        <label key={key} className="block">
          <span className="text-xs text-muted-foreground">{SCOPE_FIELD_LABELS[key] ?? key}</span>
          <textarea
            value={feature.scopeFields[key] ?? ""}
            onChange={(e) => setField(key, e.target.value)}
            rows={2}
            className="mt-1 w-full resize-none rounded-md border bg-input px-3 py-2"
          />
        </label>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Type-check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeatureSidePanel.tsx
git commit -m "feat(frontend): FeatureSidePanel detail editor (Feature 22)"
```

---

### Task 16: `FeaturesGraphEditor.tsx` — Main editor with Rete + auto-arrange + split pane

**Files:**
- Create: `frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx`
- Create: `frontend/src/components/wizard/steps/features/editor.ts` (Rete setup)

- [ ] **Step 1: Implement Rete setup (`editor.ts`)**

```ts
// frontend/src/components/wizard/steps/features/editor.ts
import { NodeEditor, GetSchemes, ClassicPreset } from "rete";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets as ReactPresets } from "rete-react-plugin";
import { AutoArrangePlugin, Presets as ArrangePresets } from "rete-auto-arrange-plugin";
import { createRoot } from "react-dom/client";
import { FeatureRNode, FeatureNodeComponent } from "./FeatureNode";
import type { WizardFeature, WizardFeatureEdge } from "@/lib/api";

type Schemes = GetSchemes<FeatureRNode, ClassicPreset.Connection<FeatureRNode, FeatureRNode>>;

export interface FeaturesEditorContext {
  destroy: () => void;
  applyGraph: (features: WizardFeature[], edges: WizardFeatureEdge[]) => Promise<void>;
  autoLayout: () => Promise<void>;
  onNodeSelect: (cb: (featureId: string | null) => void) => void;
  onConnectionCreate: (cb: (from: string, to: string) => boolean) => void;  // return false to reject
  onNodeMove: (cb: (featureId: string, x: number, y: number) => void) => void;
  onConnectionRemove: (cb: (edgeId: string) => void) => void;
}

export async function createFeaturesEditor(
  container: HTMLElement,
): Promise<FeaturesEditorContext> {
  const editor = new NodeEditor<Schemes>();
  const area = new AreaPlugin<Schemes, unknown>(container);
  const connection = new ConnectionPlugin<Schemes, unknown>();
  const render = new ReactPlugin<Schemes, unknown>({ createRoot });
  const arrange = new AutoArrangePlugin<Schemes>();

  render.addPreset(ReactPresets.classic.setup({
    customize: { node() { return FeatureNodeComponent as unknown as React.ComponentType; } },
  }));
  connection.addPreset(ConnectionPresets.classic.setup());
  arrange.addPreset(ArrangePresets.classic.setup());

  editor.use(area);
  area.use(connection);
  area.use(render);
  area.use(arrange);

  const socket = new ClassicPreset.Socket("feature");
  const nodeById = new Map<string, FeatureRNode>();
  const edgeIdByConnectionId = new Map<string, string>(); // rete conn id -> wizard edge id

  let selectCb: ((id: string | null) => void) | null = null;
  let createCb: ((from: string, to: string) => boolean) | null = null;
  let moveCb: ((id: string, x: number, y: number) => void) | null = null;
  let removeCb: ((edgeId: string) => void) | null = null;

  // Intercept connection creation for cycle prevention
  editor.addPipe((ctx) => {
    if (ctx.type === "connectioncreate") {
      const data = ctx.data as { source: string; target: string };
      const fromFeature = editor.getNode(data.source) as FeatureRNode | undefined;
      const toFeature = editor.getNode(data.target) as FeatureRNode | undefined;
      if (fromFeature && toFeature && createCb) {
        const ok = createCb(fromFeature.featureId, toFeature.featureId);
        if (!ok) return;  // undefined cancels
      }
    }
    return ctx;
  });

  area.addPipe((ctx) => {
    if (ctx.type === "nodepicked") {
      const id = (ctx.data as { id: string }).id;
      const node = editor.getNode(id);
      if (node instanceof FeatureRNode) selectCb?.(node.featureId);
    }
    if (ctx.type === "nodetranslated") {
      const d = ctx.data as { id: string; position: { x: number; y: number } };
      const node = editor.getNode(d.id);
      if (node instanceof FeatureRNode) moveCb?.(node.featureId, d.position.x, d.position.y);
    }
    return ctx;
  });

  return {
    destroy: () => area.destroy(),

    applyGraph: async (features, edges) => {
      // clear current
      for (const n of editor.getNodes()) await editor.removeNode(n.id);
      nodeById.clear();
      edgeIdByConnectionId.clear();

      for (const f of features) {
        const n = new FeatureRNode(f.id, f.title, f.scopes);
        n.addInput("in", new ClassicPreset.Input(socket, "depends on"));
        n.addOutput("out", new ClassicPreset.Output(socket, "required by"));
        await editor.addNode(n);
        await area.translate(n.id, { x: f.position.x, y: f.position.y });
        nodeById.set(f.id, n);
      }
      for (const e of edges) {
        const from = nodeById.get(e.from);
        const to = nodeById.get(e.to);
        if (!from || !to) continue;
        const conn = new ClassicPreset.Connection(from, "out", to, "in");
        edgeIdByConnectionId.set(conn.id, e.id);
        await editor.addConnection(conn);
      }
    },

    autoLayout: async () => {
      await arrange.layout();
      await AreaExtensions.zoomAt(area, editor.getNodes());
    },

    onNodeSelect: (cb) => { selectCb = cb; },
    onConnectionCreate: (cb) => { createCb = cb; },
    onNodeMove: (cb) => { moveCb = cb; },
    onConnectionRemove: (cb) => { removeCb = cb; },
  };
}
```

- [ ] **Step 2: Implement `FeaturesGraphEditor.tsx`**

```tsx
// frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx
"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRete } from "rete-react-plugin";
import { Plus, Sparkles, LayoutGrid } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useResizable } from "@/lib/hooks/use-resizable";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { proposeFeatures, type WizardFeature } from "@/lib/api";
import { getAllowedScopes } from "@/lib/category-step-config";
import { createFeaturesEditor, type FeaturesEditorContext } from "./editor";
import { FeatureSidePanel } from "./FeatureSidePanel";
import { FeaturesFallbackList } from "./FeaturesFallbackList";

interface Props { projectId: string }

export function FeaturesGraphEditor({ projectId }: Props) {
  const { data, addFeature, removeFeature, addEdge, moveFeature, applyProposal, getFeatures, getEdges } = useWizardStore();
  const category = (data?.steps.IDEA?.fields.category as string | undefined) ?? undefined;
  const allowedScopes = getAllowedScopes(category);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [viewportWide, setViewportWide] = useState(true);
  const ctxRef = useRef<FeaturesEditorContext | null>(null);

  useEffect(() => {
    const check = () => setViewportWide(window.innerWidth >= 768);
    check();
    window.addEventListener("resize", check);
    return () => window.removeEventListener("resize", check);
  }, []);

  const { width: panelWidth, handleProps } = useResizable({
    initial: 360, min: 280, max: 560, storageKey: "feature-detail-width",
  });

  const editorFactory = useCallback(
    async (container: HTMLElement) => {
      const ctx = await createFeaturesEditor(container);
      ctx.onNodeSelect(setSelectedId);
      ctx.onConnectionCreate((fromFeatureId, toFeatureId) => addEdge(fromFeatureId, toFeatureId));
      ctx.onNodeMove((id, x, y) => moveFeature(id, { x, y }));
      ctxRef.current = ctx;
      return ctx as unknown as { destroy: () => void };
    },
    [addEdge, moveFeature],
  );
  const [ref] = useRete(editorFactory);

  // Sync store -> editor
  useEffect(() => {
    if (!ctxRef.current) return;
    ctxRef.current.applyGraph(getFeatures(), getEdges());
  }, [data, getFeatures, getEdges]);

  const features = getFeatures();
  const selected = features.find((f) => f.id === selectedId) ?? null;

  if (!viewportWide) return <FeaturesFallbackList projectId={projectId} />;

  return (
    <div className="flex h-[600px] min-h-[400px] rounded-lg border bg-background overflow-hidden">
      <div className="flex-1 min-w-0 flex flex-col">
        <div ref={ref} className="flex-1" style={{ background: "var(--color-background)" }} />
        <div className="border-t px-3 py-2 flex items-center gap-2">
          <Button size="sm" onClick={() => {
            const id = addFeature({
              title: "Neues Feature", description: "",
              scopes: allowedScopes.slice(0, 1),   // pick first allowed as default
              scopeFields: {}, position: { x: 0, y: 0 },
            });
            setSelectedId(id);
          }}>
            <Plus size={14} /> Feature
          </Button>
          <Button size="sm" variant="outline" onClick={async () => {
            if (features.length > 0 && !confirm("Bestehenden Graph ueberschreiben?")) return;
            try {
              const g = await proposeFeatures(projectId);
              applyProposal(g);
            } catch { alert("Vorschlag fehlgeschlagen"); }
          }}>
            <Sparkles size={14} /> Vorschlagen
          </Button>
          <Button size="sm" variant="outline" onClick={() => ctxRef.current?.autoLayout()}>
            <LayoutGrid size={14} /> Auto-Layout
          </Button>
        </div>
      </div>

      <div className="w-1 cursor-col-resize bg-border hover:bg-primary/20" {...handleProps} />

      <div style={{ width: panelWidth }} className="shrink-0 overflow-y-auto border-l">
        {selected ? (
          <FeatureSidePanel
            feature={selected}
            allowedScopes={allowedScopes}
            onDelete={() => { removeFeature(selected.id); setSelectedId(null); }}
          />
        ) : (
          <p className="p-4 text-sm text-muted-foreground">Waehle ein Feature, um es zu bearbeiten.</p>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Type-check + lint**

```bash
cd frontend && npx tsc --noEmit && npm run lint
```
Expected: no new errors.

- [ ] **Step 4: Smoke-test**

Start backend + frontend (`./start.sh`), create a new project, run the wizard to FEATURES step. Verify:
- Clicking `+ Feature` adds a node
- Clicking a node shows it in the side panel
- Dragging between ports creates an edge
- Dragging a cycle-forming edge is rejected (no edge appears)
- `Auto-Layout` tidies nodes
- `Vorschlagen` calls backend (requires OPENAI_API_KEY to return real content)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx frontend/src/components/wizard/steps/features/editor.ts
git commit -m "feat(frontend): FeaturesGraphEditor with Rete + auto-arrange + side panel (Feature 22)"
```

---

### Task 17: `FeaturesFallbackList.tsx` — Mobile fallback

**Files:**
- Create: `frontend/src/components/wizard/steps/features/FeaturesFallbackList.tsx`

- [ ] **Step 1: Implement**

```tsx
// frontend/src/components/wizard/steps/features/FeaturesFallbackList.tsx
"use client";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { getAllowedScopes } from "@/lib/category-step-config";
import type { FeatureScope } from "@/lib/api";

interface Props { projectId: string }

export function FeaturesFallbackList({ projectId: _ }: Props) {
  const { data, getFeatures, getEdges, addFeature, updateFeature, removeFeature, addEdge, removeEdge } = useWizardStore();
  const category = (data?.steps.IDEA?.fields.category as string | undefined);
  const allowedScopes = getAllowedScopes(category);
  const features = getFeatures();
  const edges = getEdges();

  return (
    <div className="space-y-4">
      <Button size="sm" onClick={() => addFeature({
        title: "Neues Feature", description: "",
        scopes: allowedScopes.slice(0, 1),
        scopeFields: {}, position: { x: 0, y: 0 },
      })}>
        <Plus size={14} /> Feature
      </Button>

      {features.length === 0 && (
        <p className="text-sm text-muted-foreground">Noch keine Features.</p>
      )}

      {features.map((f) => {
        const deps = edges.filter(e => e.to === f.id).map(e => e.from);
        return (
          <div key={f.id} className="rounded-lg border bg-card p-3 space-y-2">
            <div className="flex items-center justify-between">
              <input
                value={f.title}
                onChange={(e) => updateFeature(f.id, { title: e.target.value })}
                className="flex-1 bg-transparent text-sm font-medium"
              />
              <button onClick={() => removeFeature(f.id)} className="text-muted-foreground hover:text-destructive">
                <Trash2 size={13} />
              </button>
            </div>
            <textarea
              value={f.description}
              onChange={(e) => updateFeature(f.id, { description: e.target.value })}
              placeholder="Beschreibung..." rows={2}
              className="w-full resize-none rounded-md border bg-input px-3 py-1.5 text-xs"
            />
            {allowedScopes.length > 0 && (
              <div className="flex gap-2">
                {allowedScopes.map((s: FeatureScope) => (
                  <label key={s} className="flex items-center gap-1 text-xs">
                    <input
                      type="checkbox"
                      checked={f.scopes.includes(s)}
                      onChange={(e) => {
                        const next = e.target.checked
                          ? [...f.scopes, s]
                          : f.scopes.filter(x => x !== s);
                        updateFeature(f.id, { scopes: next });
                      }}
                    />
                    {s === "FRONTEND" ? "Frontend" : "Backend"}
                  </label>
                ))}
              </div>
            )}
            <div>
              <span className="text-xs text-muted-foreground">Abhaengig von:</span>
              <select
                multiple
                value={deps}
                onChange={(e) => {
                  const picked = Array.from(e.target.selectedOptions).map(o => o.value);
                  // remove old deps edges, add new
                  edges.filter(edge => edge.to === f.id).forEach(edge => removeEdge(edge.id));
                  picked.forEach(src => addEdge(src, f.id));
                }}
                className="w-full mt-1 rounded-md border bg-input px-2 py-1 text-xs"
              >
                {features.filter(other => other.id !== f.id).map(other => (
                  <option key={other.id} value={other.id}>{other.title}</option>
                ))}
              </select>
            </div>
          </div>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 2: Type-check**

```bash
cd frontend && npx tsc --noEmit && npm run lint
```
Expected: no new errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wizard/steps/features/FeaturesFallbackList.tsx
git commit -m "feat(frontend): FeaturesFallbackList mobile view (Feature 22)"
```

---

### Task 18: Replace `FeaturesForm` with the new editor

**Files:**
- Modify: `frontend/src/components/wizard/steps/FeaturesForm.tsx`

- [ ] **Step 1: Replace the file contents**

```tsx
// frontend/src/components/wizard/steps/FeaturesForm.tsx
"use client";
import { FeaturesGraphEditor } from "./features/FeaturesGraphEditor";

export function FeaturesForm({ projectId }: { projectId: string }) {
  return <FeaturesGraphEditor projectId={projectId} />;
}
```

- [ ] **Step 2: Type-check + smoke-test**

```bash
cd frontend && npx tsc --noEmit && npm run lint
```

Start the app and navigate through the wizard to the FEATURES step. Verify:
- Legacy projects (that had a flat feature list) still load — features appear as nodes with default category-scoped scopes
- Adding a feature, editing its fields, connecting nodes, and clicking "Weiter" still produces `docs/features/00-feature-set-overview.md` with real dependency names
- On a Library project, the scope picker is hidden and Core fields appear

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wizard/steps/FeaturesForm.tsx
git commit -m "feat(frontend): wire FeaturesGraphEditor into wizard (Feature 22)"
```

---

## Phase D — Final Integration

### Task 19: End-to-end regression + acceptance review

**Files:** none to modify — this is a verification task.

- [ ] **Step 1: Full backend test run**

```bash
cd backend && ./gradlew test
```
Expected: PASS. Known pre-existing failure `FileControllerTest.GET files returns file tree` (Feature 15) is unrelated; no new failures should be introduced.

- [ ] **Step 2: Full frontend build + lint + type-check**

```bash
cd frontend && npm run lint && npx tsc --noEmit && npm run build
```
Expected: all green.

- [ ] **Step 3: Manual smoke-test walkthrough**

Start `./start.sh`. Run through:

1. Create a new SaaS project, walk wizard to FEATURES.
2. Click `Vorschlagen` — verify Agent produces 2-5 features with dependencies and scopes.
3. Delete one node, add a new one, edit scope to both Frontend+Backend, fill `uiComponents` and `apiEndpoints`.
4. Drag an edge A → B, then try to drag B → A — verify rejection + toast/alert.
5. Click `Auto-Layout` — nodes rearrange.
6. Click `Weiter`. Verify:
   - `docs/features/00-feature-set-overview.md` shows correct feature titles in "Abhaengig von"
   - `docs/features/01-<slug>.md` (per feature) has `**Scope:** Frontend + Backend`, includes `## UI-Komponenten` and `## API-Endpunkte` sections
7. Re-enter wizard FEATURES, change scope of one feature, click `Weiter` — verify idempotent regeneration (no duplicate EPICs, old scope-section disappears).
8. Repeat for a Library project — verify no scope picker, only Core fields, `**Scope:** Core` in per-feature doc.

- [ ] **Step 4: Acceptance criteria checklist**

Walk through each checkbox in `docs/features/22-features-graph-wizard.md` § "Akzeptanzkriterien" and confirm. Any failures become new follow-up tasks.

- [ ] **Step 5: Commit (only if anything was fixed)**

No commit if everything passes. Otherwise, address findings task-by-task with descriptive commits.

---

## Self-Review

**Spec coverage** — every requirement in `docs/features/22-features-graph-wizard.md` maps to a task: domain types (T1), backward-compat parse (T2), scope-aware plan gen (T3), dependency mapping (T4), graph block in agent context (T5), validator clarifications (T6), proposal agent (T7), proposal endpoint (T8), scaffold real deps + scope sections (T9), plugin dep (T10), frontend types (T11), cycle util (T12), wizard-store (T13), Rete node (T14), side panel (T15), main editor with auto-arrange + useResizable (T16), mobile fallback (T17), wire-up (T18), regression (T19). Acceptance-criteria-level gaps: none.

**Placeholder scan** — Two intentional `TODO` markers remain inside **test code comments** in Task 3 and Task 4 ("reuse helper from existing ...Test"). They are explicitly called out as implementer notes to copy existing test fixtures, not plan-level placeholders. One `TODO_read_category` marker in Task 5's `buildProposalContext` pseudocode is flagged with an implementer note explaining where to read the real value. No unresolved "fill in later" or "add error handling" steps remain.

**Type consistency** — `WizardFeatureInput` signature is identical across T2, T3, T4, T6. `WizardFeature` front-end TS type mirrors Kotlin domain. `FeatureScope` enum values (`FRONTEND`/`BACKEND`) match between Kotlin and TypeScript. `SCOPE_FIELDS_BY_SCOPE` uses `CORE` key only in TypeScript (not an enum — scope is empty set in Kotlin; that's the correct asymmetry). `scopeFields` is `Map<String, String>` everywhere.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-17-feature-22-features-graph-wizard.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
