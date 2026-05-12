package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.FeatureCompletionSnapshot
import com.agentwork.productspecagent.domain.LivingSyncEvent
import com.agentwork.productspecagent.domain.LivingSyncEventType
import com.agentwork.productspecagent.domain.LivingSyncFeatureStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LivingSyncStorageTest : S3TestSupport() {

    private fun storage() = LivingSyncStorage(objectStore())

    private fun event(
        id: String,
        createdAt: String,
        projectId: String = "p1",
    ) = LivingSyncEvent(
        id = id,
        projectId = projectId,
        type = LivingSyncEventType.SYNC_NOTE,
        summary = "Summary $id",
        createdAt = createdAt,
    )

    @Test
    fun `save and list events sorted by createdAt`() {
        val storage = storage()
        storage.saveEvent(event("evt-2", "2026-05-07T10:02:00Z"))
        storage.saveEvent(event("evt-1", "2026-05-07T10:01:00Z"))

        val events = storage.listEvents("p1")

        assertEquals(listOf("evt-1", "evt-2"), events.map { it.id })
    }

    @Test
    fun `list events only returns requested project`() {
        val storage = storage()
        storage.saveEvent(event("evt-1", "2026-05-07T10:01:00Z", projectId = "p1"))
        storage.saveEvent(event("evt-2", "2026-05-07T10:02:00Z", projectId = "p2"))

        val events = storage.listEvents("p1")

        assertEquals(listOf("evt-1"), events.map { it.id })
    }

    @Test
    fun `save event writes under sync events prefix`() {
        storage().saveEvent(event("evt-1", "2026-05-07T10:01:00Z"))

        assertTrue(objectStore().exists("projects/p1/sync/events/evt-1.json"))
    }

    @Test
    fun `stores raw done markdown and latest feature completion snapshot`() {
        val objectStore = InMemoryObjectStore()
        val storage = LivingSyncStorage(objectStore)

        val snapshot = FeatureCompletionSnapshot(
            projectId = "project-1",
            featureId = "feature-1",
            derivedStatus = LivingSyncFeatureStatus.DONE,
            summary = "Implemented and verified.",
            implementedItems = listOf("New MCP tool"),
            sourceEventId = "event-1",
            sourceFileName = "45-living-sync-mcp-done.md",
            updatedAt = "2026-05-12T10:00:00Z",
        )

        storage.saveImportedDoneMarkdown("project-1", "feature-1", "event-1", "# Feature 45")
        storage.saveFeatureCompletionSnapshot(snapshot)

        assertEquals(
            "# Feature 45",
            storage.loadImportedDoneMarkdown("project-1", "feature-1", "event-1"),
        )
        assertEquals(snapshot, storage.loadFeatureCompletionSnapshot("project-1", "feature-1"))
    }
}
