package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class ProjectStorageTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: ProjectStorage

    @BeforeEach
    fun setUp() {
        storage = ProjectStorage(tempDir.toString())
    }

    private fun makeProject(id: String = "proj-1") = Project(
        id = id,
        name = "Test Project",
        ownerId = "user-1",
        status = ProjectStatus.DRAFT,
        createdAt = Instant.now().toString(),
        updatedAt = Instant.now().toString()
    )

    @Test
    fun `saveProject and loadProject round-trips correctly`() {
        val project = makeProject()
        storage.saveProject(project)

        val loaded = storage.loadProject(project.id)
        assertNotNull(loaded)
        assertEquals(project.id, loaded!!.id)
        assertEquals(project.name, loaded.name)
        assertEquals(project.status, loaded.status)
    }

    @Test
    fun `loadProject returns null for non-existent project`() {
        val result = storage.loadProject("does-not-exist")
        assertNull(result)
    }

    @Test
    fun `deleteProject removes project directory`() {
        val project = makeProject()
        storage.saveProject(project)
        assertNotNull(storage.loadProject(project.id))

        storage.deleteProject(project.id)
        assertNull(storage.loadProject(project.id))
    }

    @Test
    fun `deleteProject on non-existent project does not throw`() {
        assertDoesNotThrow { storage.deleteProject("ghost-project") }
    }

    @Test
    fun `listProjects returns all saved projects`() {
        storage.saveProject(makeProject("proj-1"))
        storage.saveProject(makeProject("proj-2"))

        val projects = storage.listProjects()
        assertEquals(2, projects.size)
        assertTrue(projects.any { it.id == "proj-1" })
        assertTrue(projects.any { it.id == "proj-2" })
    }

    @Test
    fun `listProjects returns empty list when no projects exist`() {
        assertEquals(emptyList<Project>(), storage.listProjects())
    }

    @Test
    fun `saveFlowState and loadFlowState round-trips correctly`() {
        val project = makeProject()
        storage.saveProject(project)
        val flowState = createInitialFlowState(project.id)
        storage.saveFlowState(flowState)

        val loaded = storage.loadFlowState(project.id)
        assertNotNull(loaded)
        assertEquals(flowState.projectId, loaded!!.projectId)
        assertEquals(flowState.currentStep, loaded.currentStep)
        assertEquals(7, loaded.steps.size)
    }

    @Test
    fun `loadFlowState returns null for non-existent project`() {
        assertNull(storage.loadFlowState("no-such-project"))
    }

    @Test
    fun `saveSpecStep writes file to spec directory`() {
        val project = makeProject()
        storage.saveProject(project)
        storage.saveSpecStep(project.id, "idea.md", "# My Idea\nThis is a great idea.")

        val specFile = tempDir.resolve("projects/${project.id}/spec/idea.md")
        assertTrue(specFile.toFile().exists())
        assertEquals("# My Idea\nThis is a great idea.", specFile.toFile().readText())
    }

    @Test
    fun `project with collectionId saves and loads correctly`() {
        val project = Project(
            id = "p1",
            name = "Demo",
            ownerId = "u1",
            status = ProjectStatus.DRAFT,
            createdAt = "2026-04-24T10:00:00Z",
            updatedAt = "2026-04-24T10:00:00Z",
            collectionId = "col-abc-123"
        )
        storage.saveProject(project)
        val loaded = storage.loadProject("p1")!!
        assertEquals("col-abc-123", loaded.collectionId)
    }

    @Test
    fun `project loads correctly when collectionId is missing in JSON`() {
        val storage = ProjectStorage(tempDir.toString())
        val dir = tempDir.resolve("projects/p1")
        java.nio.file.Files.createDirectories(dir)
        val legacyJson = """{"id":"p1","name":"Old","ownerId":"u1","status":"DRAFT","createdAt":"x","updatedAt":"y"}"""
        java.nio.file.Files.writeString(dir.resolve("project.json"), legacyJson)
        val loaded = storage.loadProject("p1")!!
        assertNull(loaded.collectionId)
    }

    @Test
    fun `loads project with graphmeshEnabled default false when missing in JSON`() {
        val storage = ProjectStorage(tempDir.toString())
        val pid = "p1"
        val dir = tempDir.resolve("projects/$pid")
        java.nio.file.Files.createDirectories(dir)
        java.nio.file.Files.writeString(
            dir.resolve("project.json"),
            """{"id":"p1","name":"Demo","ownerId":"u1","status":"DRAFT","createdAt":"x","updatedAt":"x"}"""
        )

        val loaded = storage.loadProject(pid)!!

        assertFalse(loaded.graphmeshEnabled)
    }

    @Test
    fun `roundtrips project with graphmeshEnabled=true`() {
        val storage = ProjectStorage(tempDir.toString())
        val project = Project(
            id = "p1", name = "Demo", ownerId = "u1",
            status = ProjectStatus.DRAFT,
            createdAt = "x", updatedAt = "x",
            graphmeshEnabled = true
        )
        storage.saveProject(project)

        val loaded = storage.loadProject("p1")!!

        assertTrue(loaded.graphmeshEnabled)
    }
}
