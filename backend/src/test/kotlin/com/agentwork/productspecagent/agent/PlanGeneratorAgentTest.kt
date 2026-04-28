package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class PlanGeneratorAgentTest {

    private lateinit var contextBuilder: SpecContextBuilder
    private lateinit var projectId: String

    @BeforeEach
    fun setup() {
        val storage = ProjectStorage(InMemoryObjectStore())
        val projectService = ProjectService(storage)
        contextBuilder = SpecContextBuilder(projectService)
        val response = projectService.createProject("Test")
        projectId = response.project.id
    }

    private fun fakeAgent(response: String) = object : PlanGeneratorAgent(contextBuilder) {
        override suspend fun runAgent(prompt: String) = response
    }

    @Test
    fun `generates tasks from valid JSON`() = runBlocking {
        val agent = fakeAgent("""{"epics":[{"title":"Auth","description":"Login system","estimate":"L","specSection":"FEATURES","stories":[{"title":"Login Page","description":"Build login","estimate":"M","tasks":[{"title":"Form UI","description":"Create form","estimate":"S"}]}]}]}""")

        val tasks = agent.generatePlan(projectId)

        assertTrue(tasks.isNotEmpty())
        val epic = tasks.find { it.type == TaskType.EPIC }
        assertNotNull(epic)
        assertEquals("Auth", epic.title)
    }

    @Test
    fun `handles malformed JSON with fallback`() = runBlocking {
        val agent = fakeAgent("This is not JSON")
        val tasks = agent.generatePlan(projectId)
        assertEquals(1, tasks.size)
        assertEquals(TaskType.EPIC, tasks[0].type)
        assertEquals("Implementation Plan", tasks[0].title)
    }
}
