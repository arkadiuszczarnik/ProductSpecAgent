package com.agentwork.productspecagent.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClarificationModelsTest {

    @Test
    fun `Clarification has OPEN status by default`() {
        val c = Clarification(
            id = "c1", projectId = "p1", stepType = FlowStepType.FEATURES,
            question = "How should offline be handled?",
            reason = "Spec mentions both online-first and offline support.",
            createdAt = "2026-03-30T00:00:00Z"
        )
        assertEquals(ClarificationStatus.OPEN, c.status)
        assertNull(c.answer)
        assertNull(c.answeredAt)
    }
}
