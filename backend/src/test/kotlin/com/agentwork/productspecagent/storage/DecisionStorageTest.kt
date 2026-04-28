package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.DecisionNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DecisionStorageTest : S3TestSupport() {

    private fun storage() = DecisionStorage(objectStore())

    private fun sampleDecision(id: String = "d1", projectId: String = "p1") = Decision(
        id = id, projectId = projectId, stepType = FlowStepType.FEATURES,
        title = "Test decision",
        options = listOf(
            DecisionOption("opt-1", "Option A", listOf("pro1"), listOf("con1"), true),
            DecisionOption("opt-2", "Option B", listOf("pro2"), listOf("con2"), false)
        ),
        recommendation = "Go with A",
        createdAt = "2026-03-30T12:00:00Z"
    )

    @Test
    fun `saveDecision and loadDecision round-trip`() {
        val s = storage()
        val d = sampleDecision()
        s.saveDecision(d)
        val loaded = s.loadDecision("p1", "d1")
        assertEquals("d1", loaded.id)
        assertEquals("Test decision", loaded.title)
        assertEquals(2, loaded.options.size)
    }

    @Test
    fun `loadDecision throws for non-existent decision`() {
        assertThrows(DecisionNotFoundException::class.java) {
            storage().loadDecision("p1", "nope")
        }
    }

    @Test
    fun `listDecisions returns all decisions for a project`() {
        val s = storage()
        s.saveDecision(sampleDecision("d1", "p1"))
        s.saveDecision(sampleDecision("d2", "p1"))
        assertEquals(2, s.listDecisions("p1").size)
    }

    @Test
    fun `listDecisions returns empty for project with no decisions`() {
        assertEquals(0, storage().listDecisions("p1").size)
    }

    @Test
    fun `deleteDecision removes the object`() {
        val s = storage()
        s.saveDecision(sampleDecision())
        s.deleteDecision("p1", "d1")
        assertThrows(DecisionNotFoundException::class.java) {
            s.loadDecision("p1", "d1")
        }
    }

    @Test
    fun `deleteDecision on non-existent does not throw`() {
        assertDoesNotThrow { storage().deleteDecision("p1", "nope") }
    }

    @Test
    fun `saveDecision writes to docs-decisions key prefix`() {
        storage().saveDecision(sampleDecision())
        assertTrue(objectStore().exists("projects/p1/docs/decisions/d1.json"))
    }
}
