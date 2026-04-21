package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.agent.DecisionAgent
import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.*
import com.agentwork.productspecagent.storage.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class ScaffoldContextBuilderTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var taskStorage: TaskStorage
    private lateinit var projectStorage: ProjectStorage
    private lateinit var projectService: ProjectService
    private lateinit var taskService: TaskService
    private lateinit var decisionService: DecisionService
    private lateinit var builder: ScaffoldContextBuilder

    private lateinit var projectId: String

    private val testJson = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        val dataPath = tempDir.toString()
        projectStorage = ProjectStorage(dataPath)
        taskStorage = TaskStorage(dataPath)
        val decisionStorage = DecisionStorage(dataPath)

        projectService = ProjectService(projectStorage)

        val specCtxBuilder = SpecContextBuilder(projectService, null)

        // Stubs — runAgent wird in diesen Tests nie aufgerufen, da kein Plan generiert wird
        val planAgent = object : PlanGeneratorAgent(specCtxBuilder) {
            override suspend fun runAgent(prompt: String): String =
                error("should not be called in scaffold tests")
        }
        val decisionAgent = object : DecisionAgent(specCtxBuilder) {
            override suspend fun runAgent(prompt: String): String =
                error("should not be called in scaffold tests")
        }

        taskService = TaskService(taskStorage, planAgent)
        decisionService = DecisionService(decisionStorage, decisionAgent)

        builder = ScaffoldContextBuilder(projectService, taskService, decisionService)

        val resp = projectService.createProject("Test Project")
        projectId = resp.project.id
    }

    private fun mockTasks(tasks: List<SpecTask>) {
        tasks.forEach { taskStorage.saveTask(it) }
    }

    /**
     * Seeds a WizardData with a FEATURES step containing the given features as a flat JsonArray.
     * This mirrors exactly what the frontend wizard-store writes
     * (updateField("FEATURES", "features", [...]) — no wrapper object).
     */
    private fun seedWizardFeatures(features: List<WizardFeature>) {
        val wizardService = WizardService(projectStorage)
        val featuresJson = testJson.encodeToJsonElement(features)
        val stepData = WizardStepData(fields = mapOf("features" to featuresJson))
        val wizardData = WizardData(projectId = projectId, steps = mapOf("FEATURES" to stepData))
        wizardService.saveWizardData(projectId, wizardData)
        // Re-create builder with WizardService so scope enrichment is active
        val specCtxBuilder = SpecContextBuilder(projectService, null)
        val planAgent = object : PlanGeneratorAgent(specCtxBuilder) {
            override suspend fun runAgent(prompt: String): String =
                error("should not be called in scaffold tests")
        }
        val decisionAgent = object : DecisionAgent(specCtxBuilder) {
            override suspend fun runAgent(prompt: String): String =
                error("should not be called in scaffold tests")
        }
        val ts = TaskService(taskStorage, planAgent)
        val ds = DecisionService(DecisionStorage(tempDir.toString()), decisionAgent)
        builder = ScaffoldContextBuilder(projectService, ts, ds, wizardService)
    }

    @Test
    fun `dependencies render feature titles not Feature N-1`() {
        val now = Instant.now().toString()
        val epic1 = SpecTask(
            id = "e1", projectId = projectId, type = TaskType.EPIC, title = "Login",
            estimate = "M", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val epic2 = SpecTask(
            id = "e2", projectId = projectId, type = TaskType.EPIC, title = "Dashboard",
            estimate = "M", priority = 1, specSection = FlowStepType.FEATURES,
            dependencies = listOf("e1"),
            createdAt = now, updatedAt = now,
        )
        mockTasks(listOf(epic1, epic2))

        val ctx = builder.build(projectId)
        assertThat(ctx.features[0].dependencies).isEqualTo("—")
        assertThat(ctx.features[1].dependencies).isEqualTo("Login")
    }

    @Test
    fun `empty dependencies renders dash`() {
        val now = Instant.now().toString()
        val epic = SpecTask(
            id = "e1", projectId = projectId, type = TaskType.EPIC, title = "Login",
            estimate = "M", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        mockTasks(listOf(epic))

        val ctx = builder.build(projectId)
        assertThat(ctx.features[0].dependencies).isEqualTo("—")
    }

    @Test
    fun `multiple dependencies render comma-separated titles`() {
        val now = Instant.now().toString()
        val epic1 = SpecTask(
            id = "e1", projectId = projectId, type = TaskType.EPIC, title = "Login",
            estimate = "M", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val epic2 = SpecTask(
            id = "e2", projectId = projectId, type = TaskType.EPIC, title = "Permissions",
            estimate = "S", priority = 1, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        val epic3 = SpecTask(
            id = "e3", projectId = projectId, type = TaskType.EPIC, title = "Dashboard",
            estimate = "L", priority = 2, specSection = FlowStepType.FEATURES,
            dependencies = listOf("e1", "e2"),
            createdAt = now, updatedAt = now,
        )
        mockTasks(listOf(epic1, epic2, epic3))

        val ctx = builder.build(projectId)
        assertThat(ctx.features[2].dependencies).isEqualTo("Login, Permissions")
    }

    // ── Scope-Enrichment Regressionstests (Feature 22, AC-11) ──────────────────

    @Test
    fun `scope and scopeFields are populated from wizard features matched by title`() {
        // Arrange: wizard.json FEATURES step enthält ein WizardFeature als flaches JsonArray.
        // Das spiegelt genau das wieder, was wizard-store.ts schreibt:
        //   updateField("FEATURES", "features", [{id, title, scopes, scopeFields, ...}])
        val wizardFeature = WizardFeature(
            id = "f1",
            title = "Login",
            scopes = setOf(FeatureScope.BACKEND),
            scopeFields = mapOf("apiEndpoints" to "POST /api/auth/login"),
        )
        seedWizardFeatures(listOf(wizardFeature))

        val now = Instant.now().toString()
        val epic = SpecTask(
            id = "e1", projectId = projectId, type = TaskType.EPIC, title = "Login",
            estimate = "M", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        mockTasks(listOf(epic))

        // Act
        val ctx = builder.build(projectId)

        // Assert: Scope-Label korrekt aufgelöst und scopeFields weitergegeben
        assertThat(ctx.features).hasSize(1)
        assertThat(ctx.features[0].scope).isEqualTo("Backend")
        assertThat(ctx.features[0].scopeFields).containsEntry("apiEndpoints", "POST /api/auth/login")
    }

    @Test
    fun `library feature with empty scopes renders Core label`() {
        // Arrange: WizardFeature ohne Scopes → "Core"
        val wizardFeature = WizardFeature(
            id = "f2",
            title = "Utils",
            scopes = emptySet(),
            scopeFields = emptyMap(),
        )
        seedWizardFeatures(listOf(wizardFeature))

        val now = Instant.now().toString()
        val epic = SpecTask(
            id = "e2", projectId = projectId, type = TaskType.EPIC, title = "Utils",
            estimate = "S", priority = 0, specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now,
        )
        mockTasks(listOf(epic))

        val ctx = builder.build(projectId)

        assertThat(ctx.features).hasSize(1)
        assertThat(ctx.features[0].scope).isEqualTo("Core")
        assertThat(ctx.features[0].scopeFields).isEmpty()
    }
}
