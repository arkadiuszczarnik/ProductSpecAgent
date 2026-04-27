package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ClarificationNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ClarificationStorageTest {

    @TempDir lateinit var tempDir: Path

    private fun storage() = ClarificationStorage(tempDir.toString())

    private fun sample(id: String = "c1", projectId: String = "p1") = Clarification(
        id = id, projectId = projectId, stepType = FlowStepType.FEATURES,
        question = "How handle offline?", reason = "Contradicting requirements",
        createdAt = "2026-03-30T12:00:00Z"
    )

    @Test
    fun `save and load round-trip`() {
        val s = storage()
        s.saveClarification(sample())
        val loaded = s.loadClarification("p1", "c1")
        assertEquals("c1", loaded.id)
        assertEquals("How handle offline?", loaded.question)
    }

    @Test
    fun `load throws for non-existent`() {
        assertThrows(ClarificationNotFoundException::class.java) {
            storage().loadClarification("p1", "nope")
        }
    }

    @Test
    fun `list returns all for project`() {
        val s = storage()
        s.saveClarification(sample("c1"))
        s.saveClarification(sample("c2"))
        assertEquals(2, s.listClarifications("p1").size)
    }

    @Test
    fun `list returns empty when none exist`() {
        assertEquals(0, storage().listClarifications("p1").size)
    }

    @Test
    fun `delete removes file`() {
        val s = storage()
        s.saveClarification(sample())
        s.deleteClarification("p1", "c1")
        assertThrows(ClarificationNotFoundException::class.java) {
            s.loadClarification("p1", "c1")
        }
    }

    @Test
    fun `delete on non-existent does not throw`() {
        assertDoesNotThrow { storage().deleteClarification("p1", "nope") }
    }

    @Test
    fun `saveClarification writes to docs-clarifications subdirectory`() {
        storage().saveClarification(sample())
        assertTrue(Files.exists(tempDir.resolve("projects/p1/docs/clarifications/c1.json")))
    }
}
