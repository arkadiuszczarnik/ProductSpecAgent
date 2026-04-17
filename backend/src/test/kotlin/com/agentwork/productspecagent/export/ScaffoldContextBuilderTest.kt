package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.agent.DecisionAgent
import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.DecisionService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.TaskService
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.storage.DecisionStorage
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.TaskStorage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class ScaffoldContextBuilderTest {

    private fun buildBuilder(tmp: Path): Fixture {
        val storage = ProjectStorage(tmp.toString())
        val taskStorage = TaskStorage(tmp.toString())
        val decisionStorage = DecisionStorage(tmp.toString())

        val projectService = ProjectService(storage)
        val wizardService = WizardService(storage)
        // PlanGeneratorAgent / DecisionAgent are injected but never invoked in these tests
        // because we seed SpecTasks directly via TaskStorage — generate* is not called.
        // Unconfigured instances are sufficient here; see existing agent tests for the
        // same pattern.
        val specContextBuilder = SpecContextBuilder(projectService, wizardService)
        val planAgent = PlanGeneratorAgent(specContextBuilder)
        val taskService = TaskService(taskStorage, planAgent)
        val decisionService = DecisionService(decisionStorage, DecisionAgent(specContextBuilder))

        val builder = ScaffoldContextBuilder(projectService, taskService, decisionService, wizardService)
        return Fixture(
            builder = builder,
            projectService = projectService,
            wizardService = wizardService,
            taskStorage = taskStorage,
        )
    }

    private data class Fixture(
        val builder: ScaffoldContextBuilder,
        val projectService: ProjectService,
        val wizardService: WizardService,
        val taskStorage: TaskStorage,
    )

    private fun saveEpic(
        taskStorage: TaskStorage,
        projectId: String,
        id: String,
        title: String,
        priority: Int,
        dependencies: List<String> = emptyList(),
    ): SpecTask {
        val now = Instant.now().toString()
        val task = SpecTask(
            id = id,
            projectId = projectId,
            parentId = null,
            type = TaskType.EPIC,
            title = title,
            description = "",
            estimate = "M",
            priority = priority,
            status = TaskStatus.TODO,
            specSection = FlowStepType.FEATURES,
            dependencies = dependencies,
            source = TaskSource.WIZARD,
            createdAt = now,
            updatedAt = now,
        )
        taskStorage.saveTask(task)
        return task
    }

    @Test
    fun `dependencies render feature titles not Feature N minus 1`(@TempDir tmp: Path) {
        val f = buildBuilder(tmp)
        val projectId = f.projectService.createProject("App").project.id
        val login = saveEpic(f.taskStorage, projectId, id = "epic-login", title = "Login", priority = 0)
        saveEpic(
            f.taskStorage, projectId,
            id = "epic-dashboard", title = "Dashboard", priority = 1,
            dependencies = listOf(login.id),
        )

        val ctx = f.builder.build(projectId)

        val loginCtx = ctx.features.single { it.title == "Login" }
        val dashboardCtx = ctx.features.single { it.title == "Dashboard" }
        assertThat(loginCtx.dependencies).isEqualTo("—")
        assertThat(dashboardCtx.dependencies).isEqualTo("Login")
    }

    @Test
    fun `multiple dependencies join with comma-space`(@TempDir tmp: Path) {
        val f = buildBuilder(tmp)
        val projectId = f.projectService.createProject("App").project.id
        val a = saveEpic(f.taskStorage, projectId, id = "epic-a", title = "A", priority = 0)
        val b = saveEpic(f.taskStorage, projectId, id = "epic-b", title = "B", priority = 1)
        saveEpic(
            f.taskStorage, projectId,
            id = "epic-c", title = "C", priority = 2,
            dependencies = listOf(a.id, b.id),
        )

        val ctx = f.builder.build(projectId)
        val c = ctx.features.single { it.title == "C" }
        assertThat(c.dependencies).isEqualTo("A, B")
    }

    @Test
    fun `unknown dependency ids are skipped silently`(@TempDir tmp: Path) {
        val f = buildBuilder(tmp)
        val projectId = f.projectService.createProject("App").project.id
        saveEpic(
            f.taskStorage, projectId,
            id = "epic-orphan", title = "Orphan", priority = 0,
            dependencies = listOf("does-not-exist"),
        )

        val ctx = f.builder.build(projectId)
        val orphan = ctx.features.single()
        assertThat(orphan.dependencies).isEqualTo("—")
    }

    @Test
    fun `scope and scopeFields are carried into FeatureContext from wizard`(@TempDir tmp: Path) {
        val f = buildBuilder(tmp)
        val projectId = f.projectService.createProject("App").project.id

        // Wizard IDEA category + FEATURES graph with a single BACKEND feature.
        f.wizardService.saveWizardData(
            projectId,
            WizardData(
                projectId = projectId,
                steps = mapOf(
                    FlowStepType.IDEA.name to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive("SaaS"))
                    ),
                    FlowStepType.FEATURES.name to WizardStepData(
                        fields = mapOf("graph" to featuresGraph(
                            features = listOf(
                                graphFeature(
                                    id = "f-1", title = "Login",
                                    scopes = listOf("BACKEND"),
                                    scopeFields = mapOf("apiEndpoints" to "POST /auth/login"),
                                )
                            )
                        ))
                    )
                )
            )
        )

        // EPIC must share the feature title so the builder can match them by title.
        saveEpic(f.taskStorage, projectId, id = "epic-login", title = "Login", priority = 0)

        val ctx = f.builder.build(projectId)
        val login = ctx.features.single()
        assertThat(login.scope).isEqualTo("Backend")
        assertThat(login.scopeFields).containsEntry("apiEndpoints", "POST /auth/login")
        assertThat(login.hasApiEndpoints).isTrue()
        assertThat(login.hasUiComponents).isFalse()
    }

    @Test
    fun `library project feature has Core scope label`(@TempDir tmp: Path) {
        val f = buildBuilder(tmp)
        val projectId = f.projectService.createProject("App").project.id

        f.wizardService.saveWizardData(
            projectId,
            WizardData(
                projectId = projectId,
                steps = mapOf(
                    FlowStepType.IDEA.name to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive("Library"))
                    ),
                    FlowStepType.FEATURES.name to WizardStepData(
                        fields = mapOf("graph" to featuresGraph(
                            features = listOf(
                                // Scopes intentionally omitted so the Library default (empty set)
                                // applies and the label resolves to "Core".
                                graphFeature(id = "f-1", title = "Core API", scopes = null)
                            )
                        ))
                    )
                )
            )
        )
        saveEpic(f.taskStorage, projectId, id = "epic-core", title = "Core API", priority = 0)

        val ctx = f.builder.build(projectId)
        assertThat(ctx.features.single().scope).isEqualTo("Core")
    }

    @Test
    fun `missing wizard data leaves scope null and scopeFields empty`(@TempDir tmp: Path) {
        val f = buildBuilder(tmp)
        val projectId = f.projectService.createProject("App").project.id
        saveEpic(f.taskStorage, projectId, id = "epic-legacy", title = "Legacy", priority = 0)

        val ctx = f.builder.build(projectId)
        val legacy = ctx.features.single()
        assertThat(legacy.scope).isNull()
        assertThat(legacy.scopeFields).isEmpty()
        assertThat(legacy.hasApiEndpoints).isFalse()
    }

    @Test
    fun `frontend plus backend scopes produce combined label`(@TempDir tmp: Path) {
        val f = buildBuilder(tmp)
        val projectId = f.projectService.createProject("App").project.id

        f.wizardService.saveWizardData(
            projectId,
            WizardData(
                projectId = projectId,
                steps = mapOf(
                    FlowStepType.IDEA.name to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive("SaaS"))
                    ),
                    FlowStepType.FEATURES.name to WizardStepData(
                        fields = mapOf("graph" to featuresGraph(
                            features = listOf(
                                graphFeature(
                                    id = "f-1", title = "Checkout",
                                    scopes = listOf("FRONTEND", "BACKEND"),
                                )
                            )
                        ))
                    )
                )
            )
        )
        saveEpic(f.taskStorage, projectId, id = "epic-checkout", title = "Checkout", priority = 0)

        val ctx = f.builder.build(projectId)
        assertThat(ctx.features.single().scope).isEqualTo("Frontend + Backend")
    }

    @Test
    fun `blank scopeField values suppress their section flags`(@TempDir tmp: Path) {
        val f = buildBuilder(tmp)
        val projectId = f.projectService.createProject("App").project.id

        f.wizardService.saveWizardData(
            projectId,
            WizardData(
                projectId = projectId,
                steps = mapOf(
                    FlowStepType.IDEA.name to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive("SaaS"))
                    ),
                    FlowStepType.FEATURES.name to WizardStepData(
                        fields = mapOf("graph" to featuresGraph(
                            features = listOf(
                                graphFeature(
                                    id = "f-1", title = "Login",
                                    scopes = listOf("FRONTEND"),
                                    scopeFields = mapOf(
                                        "uiComponents" to "  ", // blank → section suppressed
                                        "screens" to "/login"
                                    ),
                                )
                            )
                        ))
                    )
                )
            )
        )
        saveEpic(f.taskStorage, projectId, id = "epic-login", title = "Login", priority = 0)

        val ctx = f.builder.build(projectId)
        val login = ctx.features.single()
        assertThat(login.hasUiComponents).isFalse()
        assertThat(login.hasScreens).isTrue()
        assertThat(login.scopeFields).doesNotContainKey("uiComponents")
        assertThat(login.scopeFields).containsEntry("screens", "/login")
    }

    // ---- helpers to build the "graph" JsonObject the wizard persists in FEATURES.fields ----

    private fun graphFeature(
        id: String,
        title: String,
        scopes: List<String>? = null,
        scopeFields: Map<String, String> = emptyMap(),
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        if (scopes != null) {
            put("scopes", buildJsonArray { scopes.forEach { add(JsonPrimitive(it)) } })
        }
        if (scopeFields.isNotEmpty()) {
            put("scopeFields", buildJsonObject {
                scopeFields.forEach { (k, v) -> put(k, v) }
            })
        }
    }

    private fun featuresGraph(
        features: List<JsonObject>,
        edges: List<JsonObject> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("features", JsonArray(features))
        put("edges", JsonArray(edges))
    }
}
