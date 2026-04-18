package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.agent.DecisionAgent
import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.*
import com.agentwork.productspecagent.storage.*
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
    private lateinit var projectService: ProjectService
    private lateinit var taskService: TaskService
    private lateinit var decisionService: DecisionService
    private lateinit var builder: ScaffoldContextBuilder

    private lateinit var projectId: String

    @BeforeEach
    fun setUp() {
        val dataPath = tempDir.toString()
        val projectStorage = ProjectStorage(dataPath)
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

        val resp = projectService.createProject("Test Project", "An idea")
        projectId = resp.project.id
    }

    private fun mockTasks(tasks: List<SpecTask>) {
        tasks.forEach { taskStorage.saveTask(it) }
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
}
