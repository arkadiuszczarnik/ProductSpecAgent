package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.ProjectStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ProjectServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: ProjectStorage
    private lateinit var service: ProjectService

    @BeforeEach
    fun setUp() {
        storage = ProjectStorage(tempDir.toString())
        service = ProjectService(storage)
    }

    @Test
    fun `createProject saves project, flowState, and idea file`() {
        val response = service.createProject("My Project")

        assertEquals("My Project", response.project.name)
        assertEquals(ProjectStatus.DRAFT, response.project.status)
        assertEquals("anonymous", response.project.ownerId)
        assertEquals(FlowStepType.IDEA, response.flowState.currentStep)
        assertEquals(9, response.flowState.steps.size)

        // Verify persisted
        assertNotNull(storage.loadProject(response.project.id))
        assertNotNull(storage.loadFlowState(response.project.id))
    }

    @Test
    fun `getProject returns project and flowState`() {
        val created = service.createProject("Test")
        val response = service.getProject(created.project.id)

        assertEquals(created.project.id, response.project.id)
        assertEquals(created.flowState.currentStep, response.flowState.currentStep)
    }

    @Test
    fun `getProject throws ProjectNotFoundException when project not found`() {
        assertThrows(ProjectNotFoundException::class.java) {
            service.getProject("ghost")
        }
    }

    @Test
    fun `deleteProject removes project`() {
        val created = service.createProject("Delete Me")
        service.deleteProject(created.project.id)

        assertThrows(ProjectNotFoundException::class.java) {
            service.getProject(created.project.id)
        }
    }

    @Test
    fun `deleteProject throws ProjectNotFoundException when project not found`() {
        assertThrows(ProjectNotFoundException::class.java) {
            service.deleteProject("ghost")
        }
    }

    @Test
    fun `listProjects returns all projects`() {
        service.createProject("P1")
        service.createProject("P2")

        val result = service.listProjects()
        assertEquals(2, result.size)
    }

    @Test
    fun `listProjects returns empty list when no projects`() {
        assertEquals(0, service.listProjects().size)
    }

    @Test
    fun `getFlowState returns flowState for existing project`() {
        val created = service.createProject("Flow")
        val result = service.getFlowState(created.project.id)

        assertEquals(created.project.id, result.projectId)
        assertEquals(FlowStepType.IDEA, result.currentStep)
    }

    @Test
    fun `getFlowState throws ProjectNotFoundException when project not found`() {
        assertThrows(ProjectNotFoundException::class.java) {
            service.getFlowState("ghost")
        }
    }
}
