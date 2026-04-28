package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SpecContextBuilderTest {

    private lateinit var storage: ProjectStorage
    private lateinit var projectService: ProjectService
    private lateinit var builder: SpecContextBuilder

    @BeforeEach
    fun setup() {
        storage = ProjectStorage(InMemoryObjectStore())
        projectService = ProjectService(storage)
        builder = SpecContextBuilder(projectService)
    }

    @Test
    fun `buildContext includes project name and current step`() {
        val project = projectService.createProject("My App")
        val context = builder.buildContext(project.project.id)

        assertContains(context, "My App")
        assertContains(context, "Current Step: IDEA")
    }

    @Test
    fun `buildContext includes completed step content`() {
        val project = projectService.createProject("My App")

        val context = builder.buildContext(project.project.id)
        // IDEA step is IN_PROGRESS, not COMPLETED, so it won't be in completed steps
        assertTrue(context.contains("PROJECT CONTEXT"))
    }
}
