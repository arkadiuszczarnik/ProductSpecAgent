package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.FlowStepStatus
import com.agentwork.productspecagent.domain.FlowStepType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowStateStorageMigrationTest {

    // Pre-Feature-40 fixture: 7 steps, no DESIGN entry
    private val legacyJson = """
        {
          "projectId": "proj-1",
          "currentStep": "MVP",
          "steps": [
            {"stepType":"IDEA","status":"COMPLETED","updatedAt":"2026-01-01T00:00:00Z"},
            {"stepType":"PROBLEM","status":"COMPLETED","updatedAt":"2026-01-01T00:00:00Z"},
            {"stepType":"FEATURES","status":"COMPLETED","updatedAt":"2026-01-01T00:00:00Z"},
            {"stepType":"MVP","status":"IN_PROGRESS","updatedAt":"2026-01-01T00:00:00Z"},
            {"stepType":"ARCHITECTURE","status":"OPEN","updatedAt":"2026-01-01T00:00:00Z"},
            {"stepType":"BACKEND","status":"OPEN","updatedAt":"2026-01-01T00:00:00Z"},
            {"stepType":"FRONTEND","status":"OPEN","updatedAt":"2026-01-01T00:00:00Z"}
          ]
        }
    """.trimIndent()

    @Test
    fun `load fills missing FlowStepType entries as OPEN`() {
        val store = InMemoryObjectStore()
        val projectStorage = ProjectStorage(store)
        val projectId = "proj-1"
        // Write legacy file directly to bypass projectStorage.saveFlowState (which would already include DESIGN)
        store.put("projects/$projectId/flow-state.json", legacyJson.toByteArray(), "application/json")

        val loaded = projectStorage.loadFlowState(projectId)!!

        val designStep = loaded.steps.firstOrNull { it.stepType == FlowStepType.DESIGN }
        assertThat(designStep).isNotNull
        assertThat(designStep!!.status).isEqualTo(FlowStepStatus.OPEN)
        // Existing entries unchanged
        assertThat(loaded.steps.first { it.stepType == FlowStepType.MVP }.status)
            .isEqualTo(FlowStepStatus.IN_PROGRESS)
    }

    @Test
    fun `load is idempotent - repeated loads of a legacy fixture produce identical steps`() {
        val store = InMemoryObjectStore()
        val projectStorage = ProjectStorage(store)
        val projectId = "proj-1"
        // Write the same legacy fixture (no DESIGN) to trigger migration on every load
        store.put("projects/$projectId/flow-state.json", legacyJson.toByteArray(), "application/json")

        val loaded1 = projectStorage.loadFlowState(projectId)!!
        val loaded2 = projectStorage.loadFlowState(projectId)!!

        // Deep equality: both loads must produce identical steps including identical updatedAt for migrated DESIGN
        assertThat(loaded2.steps).isEqualTo(loaded1.steps)
        assertThat(loaded2.steps).hasSize(FlowStepType.entries.size)
    }
}
