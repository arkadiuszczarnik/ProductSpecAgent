package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.SpecTask
import com.agentwork.productspecagent.domain.TaskSource
import com.agentwork.productspecagent.domain.TaskType
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.TaskStorage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class TaskServiceWizardGraphTest {

    // Stub agent: for each wizard feature, emits exactly ONE epic task with id = "epic-${input.id}",
    // no stories. Deterministic for dependency-mapping assertions.
    private class StubSingleEpicAgent(contextBuilder: SpecContextBuilder) : PlanGeneratorAgent(contextBuilder) {
        override suspend fun generatePlanForFeature(
            projectId: String,
            input: WizardFeatureInput,
            startPriority: Int,
        ): List<SpecTask> {
            val now = Instant.now().toString()
            return listOf(
                SpecTask(
                    id = "epic-${input.id}",
                    projectId = projectId,
                    type = TaskType.EPIC,
                    title = input.title,
                    description = input.description,
                    estimate = "M",
                    priority = startPriority,
                    specSection = FlowStepType.FEATURES,
                    source = TaskSource.WIZARD,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    // Agent that returns a tree (epic + 1 story + 1 task) — for verifying priority counting.
    private class StubTreeAgent(contextBuilder: SpecContextBuilder) : PlanGeneratorAgent(contextBuilder) {
        override suspend fun generatePlanForFeature(
            projectId: String,
            input: WizardFeatureInput,
            startPriority: Int,
        ): List<SpecTask> {
            val now = Instant.now().toString()
            val epicId = "epic-${input.id}"
            val storyId = "story-${input.id}"
            return listOf(
                SpecTask(
                    id = epicId, projectId = projectId, type = TaskType.EPIC,
                    title = input.title, description = "", estimate = "M",
                    priority = startPriority, specSection = FlowStepType.FEATURES,
                    source = TaskSource.WIZARD, createdAt = now, updatedAt = now,
                ),
                SpecTask(
                    id = storyId, projectId = projectId, parentId = epicId,
                    type = TaskType.STORY, title = "Story A", description = "",
                    estimate = "M", priority = startPriority + 1,
                    source = TaskSource.WIZARD, createdAt = now, updatedAt = now,
                ),
                SpecTask(
                    id = "task-${input.id}", projectId = projectId, parentId = storyId,
                    type = TaskType.TASK, title = "Task A", description = "",
                    estimate = "S", priority = startPriority + 2,
                    source = TaskSource.WIZARD, createdAt = now, updatedAt = now,
                ),
            )
        }
    }

    /**
     * Replicates the SpecContextBuilder construction used by PlanGeneratorAgentScopeTest:
     * real ProjectStorage + ProjectService rooted at [tmp]. The stub agents do not actually
     * call `contextBuilder.buildContext`, so this just needs to be a valid instance.
     */
    private fun buildContextBuilder(tmp: Path): SpecContextBuilder {
        val storage = ProjectStorage(tmp.toString())
        val projectService = ProjectService(storage)
        return SpecContextBuilder(projectService)
    }

    @Test
    fun `phase 2 maps wizard-feature dependsOn ids to generated epic ids`(@TempDir tmp: Path) = runBlocking<Unit> {
        val storage = TaskStorage(tmp.toString())
        val svc = TaskService(storage, StubSingleEpicAgent(buildContextBuilder(tmp)))

        val inputs = listOf(
            WizardFeatureInput(
                id = "f-1", title = "Login", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            WizardFeatureInput(
                id = "f-2", title = "Dashboard", description = "",
                scopes = setOf(FeatureScope.FRONTEND), scopeFields = emptyMap(), dependsOn = listOf("f-1")
            ),
        )
        val result = svc.replaceWizardFeatureTasks("p1", inputs)

        val dashboardEpic = result.single { it.title == "Dashboard" }
        assertThat(dashboardEpic.dependencies).containsExactly("epic-f-1")
        val loginEpic = result.single { it.title == "Login" }
        assertThat(loginEpic.dependencies).isEmpty()

        // Persisted (not only returned in memory):
        assertThat(storage.listTasks("p1").single { it.title == "Dashboard" }.dependencies)
            .containsExactly("epic-f-1")
    }

    @Test
    fun `existing non-wizard tasks remain untouched`(@TempDir tmp: Path) = runBlocking<Unit> {
        val storage = TaskStorage(tmp.toString())
        val now = Instant.now().toString()
        storage.saveTask(
            SpecTask(
                id = "manual-1", projectId = "p1", type = TaskType.TASK, title = "Manual task",
                priority = 100, createdAt = now, updatedAt = now, source = null,
            )
        )
        val svc = TaskService(storage, StubSingleEpicAgent(buildContextBuilder(tmp)))
        svc.replaceWizardFeatureTasks(
            "p1", listOf(
                WizardFeatureInput(
                    id = "f-1", title = "New", description = "",
                    scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap()
                ),
            )
        )
        assertThat(storage.listTasks("p1").map { it.id }).contains("manual-1")
    }

    @Test
    fun `priority counter increases across tasks in a tree`(@TempDir tmp: Path) = runBlocking<Unit> {
        val storage = TaskStorage(tmp.toString())
        val svc = TaskService(storage, StubTreeAgent(buildContextBuilder(tmp)))
        val result = svc.replaceWizardFeatureTasks(
            "p1", listOf(
                WizardFeatureInput(
                    id = "f-1", title = "A", description = "",
                    scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap()
                ),
                WizardFeatureInput(
                    id = "f-2", title = "B", description = "",
                    scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap()
                ),
            )
        )
        // First feature: priorities 0,1,2. Second feature: 3,4,5.
        val priorities = result.map { it.priority }.toSet()
        assertThat(priorities).contains(0, 1, 2, 3, 4, 5)
    }

    @Test
    fun `missing dependsOn target is silently dropped`(@TempDir tmp: Path) = runBlocking<Unit> {
        val storage = TaskStorage(tmp.toString())
        val svc = TaskService(storage, StubSingleEpicAgent(buildContextBuilder(tmp)))
        // f-2 depends on "ghost" which is not in the input list → dependency skipped, no crash.
        val result = svc.replaceWizardFeatureTasks(
            "p1", listOf(
                WizardFeatureInput(
                    id = "f-2", title = "Dashboard", description = "",
                    scopes = setOf(FeatureScope.FRONTEND), scopeFields = emptyMap(),
                    dependsOn = listOf("ghost")
                ),
            )
        )
        val dashboard = result.single { it.title == "Dashboard" }
        assertThat(dashboard.dependencies).isEmpty()
    }
}
