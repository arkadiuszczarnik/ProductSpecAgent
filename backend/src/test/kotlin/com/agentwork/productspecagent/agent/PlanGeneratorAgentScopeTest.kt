package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.TaskType
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardFeatureInput
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlanGeneratorAgentScopeTest {

    private class CapturingAgent(
        builder: SpecContextBuilder,
        private val response: String,
    ) : PlanGeneratorAgent(builder) {
        var capturedPrompt: String = ""
        override suspend fun runAgent(prompt: String): String {
            capturedPrompt = prompt
            return response
        }
    }

    private fun buildSpecContextBuilder(): SpecContextBuilder {
        val storage = ProjectStorage(InMemoryObjectStore())
        val projectService = ProjectService(storage)
        return object : SpecContextBuilder(projectService) {
            override fun buildContext(projectId: String): String =
                "Idea: Test project\nProblem: Test problem"
        }
    }

    private fun agent(response: String): CapturingAgent {
        val builder = buildSpecContextBuilder()
        return CapturingAgent(builder, response)
    }

    @Test
    fun `frontend-only feature prompt contains UI hint`(): Unit = runBlocking {
        val a = agent("""{"epicEstimate":"M","stories":[]}""")
        a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Login UI", description = "",
                scopes = setOf(FeatureScope.FRONTEND),
                scopeFields = emptyMap(), dependsOn = emptyList(),
            ),
            startPriority = 0,
        )
        assertThat(a.capturedPrompt)
            .contains("Frontend-only")
            .contains("UI")
    }

    @Test
    fun `epic uses epicEstimate from LLM response`(): Unit = runBlocking {
        val a = agent("""{"epicEstimate":"L","stories":[]}""")
        val tasks = a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Feature A", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = emptyMap(), dependsOn = emptyList(),
            ),
            startPriority = 0,
        )
        val epic = tasks.first { it.type == TaskType.EPIC }
        assertThat(epic.estimate).isEqualTo("L")
    }

    @Test
    fun `invalid json falls back to estimate M and no stories`(): Unit = runBlocking {
        val a = agent("not json")
        val tasks = a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Feature A", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = emptyMap(), dependsOn = emptyList(),
            ),
            startPriority = 0,
        )
        val epic = tasks.single()
        assertThat(epic.type).isEqualTo(TaskType.EPIC)
        assertThat(epic.estimate).isEqualTo("M")
    }

    @Test
    fun `library feature (empty scopes) gets library hint`(): Unit = runBlocking {
        val a = agent("""{"epicEstimate":"S","stories":[]}""")
        a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Core API", description = "",
                scopes = emptySet(),
                scopeFields = emptyMap(), dependsOn = emptyList(),
            ),
            startPriority = 0,
        )
        assertThat(a.capturedPrompt).contains("Library-Komponente")
    }

    @Test
    fun `story and task with invalid estimate fall back to M`(): Unit = runBlocking {
        val a = agent("""
            {"epicEstimate":"M","stories":[
                {"title":"s1","description":"","estimate":"LARGE",
                 "tasks":[{"title":"t1","description":"","estimate":""}]}
            ]}
        """.trimIndent())
        val tasks = a.generatePlanForFeature(
            projectId = "p1",
            input = WizardFeatureInput(
                id = "f-1", title = "Feature A", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = emptyMap(), dependsOn = emptyList(),
            ),
            startPriority = 0,
        )
        val story = tasks.first { it.type == TaskType.STORY }
        val task = tasks.first { it.type == TaskType.TASK }
        assertThat(story.estimate).isEqualTo("M")
        assertThat(task.estimate).isEqualTo("M")
    }
}
