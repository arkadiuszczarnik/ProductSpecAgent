package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.TaskStorage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.time.Instant

class TaskServiceWizardGraphTest {

    // Builds a SpecContextBuilder without a real project (same pattern as PlanGeneratorAgentScopeTest)
    private fun buildSpecContextBuilder(): SpecContextBuilder {
        val storage = ProjectStorage(InMemoryObjectStore())
        val projectService = ProjectService(storage)
        return SpecContextBuilder(projectService)
    }

    // Fake agent: emits one EPIC per call, id = "epic-<input.id>", no stories
    private fun fakeAgentReturningOneEpicPerInput(): PlanGeneratorAgent {
        val builder = buildSpecContextBuilder()
        return object : PlanGeneratorAgent(builder) {
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
                        estimate = "M",
                        priority = startPriority,
                        specSection = FlowStepType.FEATURES,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
    }

    // Convenience alias used by the new tests
    private fun makeFakeAgent(): PlanGeneratorAgent = fakeAgentReturningOneEpicPerInput()

    @Test
    fun `phase 2 maps wizard-feature dependsOn ids to generated epic ids`() {
        val storage = TaskStorage(InMemoryObjectStore())
        val agent = fakeAgentReturningOneEpicPerInput()
        val svc = TaskService(storage, agent)

        val inputs = listOf(
            WizardFeatureInput(
                id = "f-1", title = "Login", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(),
                dependsOn = emptyList(),
            ),
            WizardFeatureInput(
                id = "f-2", title = "Dashboard", description = "",
                scopes = setOf(FeatureScope.FRONTEND), scopeFields = emptyMap(),
                dependsOn = listOf("f-1"),
            ),
        )

        val result = runBlocking { svc.replaceWizardFeatureTasks("p1", inputs) }

        val dashboardEpic = result.single { it.title == "Dashboard" }
        assertThat(dashboardEpic.dependencies).containsExactly("epic-f-1")

        val loginEpic = result.single { it.title == "Login" }
        assertThat(loginEpic.dependencies).isEmpty()

        // Persisted state must match returned state
        assertThat(
            storage.listTasks("p1").single { it.title == "Dashboard" }.dependencies
        ).containsExactly("epic-f-1")
    }

    @Test
    fun `existing non-wizard tasks remain untouched`() {
        val storage = TaskStorage(InMemoryObjectStore())
        val now = Instant.now().toString()
        // A manually created task (no specSection = FEATURES) must survive replacement
        storage.saveTask(
            SpecTask(
                id = "manual-1", projectId = "p1", type = TaskType.TASK,
                title = "Manual task", priority = 100,
                createdAt = now, updatedAt = now,
            )
        )
        val agent = fakeAgentReturningOneEpicPerInput()
        val svc = TaskService(storage, agent)

        runBlocking {
            svc.replaceWizardFeatureTasks(
                "p1",
                listOf(
                    WizardFeatureInput(
                        id = "f-1", title = "New Feature", description = "",
                        scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(),
                    ),
                ),
            )
        }

        assertThat(storage.listTasks("p1").map { it.id }).contains("manual-1")
    }

    @Test
    fun `chain dependency A → B → C resolves both edges`() {
        val storage = TaskStorage(InMemoryObjectStore())
        val agent = makeFakeAgent()
        val svc = TaskService(storage, agent)
        val inputs = listOf(
            WizardFeatureInput(id = "f-1", title = "A", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()),
            WizardFeatureInput(id = "f-2", title = "B", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = listOf("f-1")),
            WizardFeatureInput(id = "f-3", title = "C", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = listOf("f-2")),
        )
        val result = runBlocking { svc.replaceWizardFeatureTasks("p1", inputs) }
        assertThat(result.single { it.title == "A" }.dependencies).isEmpty()
        assertThat(result.single { it.title == "B" }.dependencies).containsExactly("epic-f-1")
        assertThat(result.single { it.title == "C" }.dependencies).containsExactly("epic-f-2")
    }

    @Test
    fun `multi-source dependency A,B → C is fully resolved`() {
        val storage = TaskStorage(InMemoryObjectStore())
        val agent = makeFakeAgent()
        val svc = TaskService(storage, agent)
        val inputs = listOf(
            WizardFeatureInput(id = "f-1", title = "A", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()),
            WizardFeatureInput(id = "f-2", title = "B", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()),
            WizardFeatureInput(id = "f-3", title = "C", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = listOf("f-1", "f-2")),
        )
        val result = runBlocking { svc.replaceWizardFeatureTasks("p1", inputs) }
        assertThat(result.single { it.title == "C" }.dependencies)
            .containsExactlyInAnyOrder("epic-f-1", "epic-f-2")
    }

    @Test
    fun `unknown dependsOn id is silently ignored`() {
        val storage = TaskStorage(InMemoryObjectStore())
        val agent = makeFakeAgent()
        val svc = TaskService(storage, agent)
        val inputs = listOf(
            WizardFeatureInput(id = "f-1", title = "X", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = listOf("non-existent")),
        )
        val result = runBlocking { svc.replaceWizardFeatureTasks("p1", inputs) }
        assertThat(result.single().dependencies).isEmpty()
    }
}
