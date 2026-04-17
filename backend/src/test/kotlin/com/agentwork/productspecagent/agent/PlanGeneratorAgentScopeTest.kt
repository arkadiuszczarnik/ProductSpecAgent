package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.TaskType
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardFeatureInput
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PlanGeneratorAgentScopeTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var contextBuilder: SpecContextBuilder
    private lateinit var projectId: String

    @BeforeEach
    fun setup() {
        val storage = ProjectStorage(tempDir.toString())
        val projectService = ProjectService(storage)
        contextBuilder = SpecContextBuilder(projectService)
        val response = projectService.createProject("Test")
        projectId = response.project.id
    }

    // Test-local subclass: captures the last prompt passed to the LLM and returns a canned response.
    private class CapturingAgent(
        contextBuilder: SpecContextBuilder,
        private val cannedResponse: String,
    ) : PlanGeneratorAgent(contextBuilder) {
        var lastPrompt: String = ""
        override suspend fun runAgent(prompt: String): String {
            lastPrompt = prompt
            return cannedResponse
        }
    }

    private fun agent(response: String): CapturingAgent =
        CapturingAgent(contextBuilder, response)

    @Test
    fun `frontend-only feature prompt contains UI hint`() = runBlocking<Unit> {
        val a = agent("""{"epicEstimate":"M","stories":[]}""")
        a.generatePlanForFeature(
            projectId = projectId,
            input = WizardFeatureInput(
                id = "f-1", title = "Login UI", description = "",
                scopes = setOf(FeatureScope.FRONTEND),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        assertThat(a.lastPrompt).contains("Frontend-only").contains("UI")
    }

    @Test
    fun `backend-only feature prompt contains API hint`() = runBlocking<Unit> {
        val a = agent("""{"epicEstimate":"M","stories":[]}""")
        a.generatePlanForFeature(
            projectId = projectId,
            input = WizardFeatureInput(
                id = "f-1", title = "Payments", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        assertThat(a.lastPrompt).contains("Backend-only").contains("API")
    }

    @Test
    fun `library feature (empty scopes) gets Library-Komponente hint`() = runBlocking<Unit> {
        val a = agent("""{"epicEstimate":"S","stories":[]}""")
        a.generatePlanForFeature(
            projectId = projectId,
            input = WizardFeatureInput(
                id = "f-1", title = "Core API", description = "",
                scopes = emptySet(),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        assertThat(a.lastPrompt).contains("Library-Komponente")
    }

    @Test
    fun `fullstack feature (both scopes) emits no scope-specific hint`() = runBlocking<Unit> {
        val a = agent("""{"epicEstimate":"M","stories":[]}""")
        a.generatePlanForFeature(
            projectId = projectId,
            input = WizardFeatureInput(
                id = "f-1", title = "User Profile", description = "",
                scopes = setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        assertThat(a.lastPrompt).doesNotContain("Frontend-only").doesNotContain("Backend-only")
    }

    @Test
    fun `epic uses epicEstimate from LLM response`() = runBlocking<Unit> {
        val a = agent("""{"epicEstimate":"L","stories":[]}""")
        val tasks = a.generatePlanForFeature(
            projectId = projectId,
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
    fun `invalid epicEstimate falls back to M`() = runBlocking<Unit> {
        val a = agent("""{"epicEstimate":"HUGE","stories":[]}""")
        val tasks = a.generatePlanForFeature(
            projectId = projectId,
            input = WizardFeatureInput(
                id = "f-1", title = "Feature A", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = emptyMap(), dependsOn = emptyList()
            ),
            startPriority = 0,
        )
        val epic = tasks.first { it.type == TaskType.EPIC }
        assertThat(epic.estimate).isEqualTo("M")
    }

    @Test
    fun `invalid json falls back to estimate M and no stories`() = runBlocking<Unit> {
        val a = agent("not json")
        val tasks = a.generatePlanForFeature(
            projectId = projectId,
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
    fun `scopeFields are injected into the prompt`() = runBlocking<Unit> {
        val a = agent("""{"epicEstimate":"M","stories":[]}""")
        a.generatePlanForFeature(
            projectId = projectId,
            input = WizardFeatureInput(
                id = "f-1", title = "Login", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = mapOf("apiEndpoints" to "POST /auth/login"),
                dependsOn = emptyList(),
            ),
            startPriority = 0,
        )
        assertThat(a.lastPrompt).contains("apiEndpoints").contains("POST /auth/login")
    }
}
