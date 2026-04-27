package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.TaskNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TaskStorageTest {

    @TempDir lateinit var tempDir: Path

    private fun storage() = TaskStorage(tempDir.toString())

    private fun sample(id: String = "t1", projectId: String = "p1", priority: Int = 0) = SpecTask(
        id = id, projectId = projectId, type = TaskType.EPIC,
        title = "Test task", description = "Description",
        priority = priority, createdAt = "2026-03-30T12:00:00Z", updatedAt = "2026-03-30T12:00:00Z"
    )

    @Test
    fun `save and load round-trip`() {
        val s = storage()
        s.saveTask(sample())
        val loaded = s.loadTask("p1", "t1")
        assertEquals("t1", loaded.id)
        assertEquals("Test task", loaded.title)
    }

    @Test
    fun `load throws for non-existent`() {
        assertThrows(TaskNotFoundException::class.java) {
            storage().loadTask("p1", "nope")
        }
    }

    @Test
    fun `list returns all sorted by priority`() {
        val s = storage()
        s.saveTask(sample("t2", priority = 2))
        s.saveTask(sample("t1", priority = 1))
        val list = s.listTasks("p1")
        assertEquals(2, list.size)
        assertEquals("t1", list[0].id)
        assertEquals("t2", list[1].id)
    }

    @Test
    fun `list returns empty when none exist`() {
        assertEquals(0, storage().listTasks("p1").size)
    }

    @Test
    fun `delete removes file`() {
        val s = storage()
        s.saveTask(sample())
        s.deleteTask("p1", "t1")
        assertThrows(TaskNotFoundException::class.java) { s.loadTask("p1", "t1") }
    }

    @Test
    fun `deleteAll removes all tasks`() {
        val s = storage()
        s.saveTask(sample("t1"))
        s.saveTask(sample("t2"))
        s.deleteAllTasks("p1")
        assertEquals(0, s.listTasks("p1").size)
    }

    @Test
    fun `saveTask writes to docs-tasks subdirectory`() {
        val storage = TaskStorage(tempDir.toString())
        val task = SpecTask(
            id = "t1",
            projectId = "p1",
            type = TaskType.EPIC,
            title = "Epic",
            description = "...",
            estimate = "1w",
            priority = 1,
            status = TaskStatus.TODO,
            parentId = null,
            specSection = null,
            dependencies = emptyList(),
            createdAt = "2026-04-27T00:00:00Z",
            updatedAt = "2026-04-27T00:00:00Z"
        )

        storage.saveTask(task)

        val expected = tempDir.resolve("projects/p1/docs/tasks/t1.json")
        assertTrue(Files.exists(expected), "Task should land at docs/tasks/, got existing files: " +
            Files.walk(tempDir).filter(Files::isRegularFile).toList())
    }
}
