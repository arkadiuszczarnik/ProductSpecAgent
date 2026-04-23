package com.agentwork.productspecagent.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DecisionModelsTest {

    @Test
    fun `Decision has PENDING status by default`() {
        val decision = Decision(
            id = "d1", projectId = "p1", stepType = FlowStepType.FEATURES,
            title = "Should feature X be in MVP?",
            options = listOf(
                DecisionOption("opt-1", "Include in MVP", listOf("Users need it"), listOf("More dev time"), true)
            ),
            recommendation = "Include because users need it.",
            createdAt = "2026-03-30T00:00:00Z"
        )
        assertEquals(DecisionStatus.PENDING, decision.status)
        assertNull(decision.chosenOptionId)
        assertNull(decision.rationale)
        assertNull(decision.resolvedAt)
        assertEquals(1, decision.options.size)
    }
}
