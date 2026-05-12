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
    fun `stores raw done markdown under imports prefix`() {
        val storage = storage()
        val projectId = "project-1"
        val featureId = "feature-1"
        val eventId = "event-1"
        val expectedKey = "projects/$projectId/sync/imports/$featureId/$eventId.md"

        storage.saveImportedDoneMarkdown(projectId, featureId, eventId, "# Feature 45")

        assertTrue(objectStore().exists(expectedKey))
        assertEquals("# Feature 45", storage.loadImportedDoneMarkdown(projectId, featureId, eventId))
    }

    @Test
    fun `stores feature completion snapshot under feature snapshots prefix`() {
        val storage = storage()
        val expectedKey = "projects/project-1/sync/feature-snapshots/feature-1.json"

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

        storage.saveFeatureCompletionSnapshot(snapshot)

        assertTrue(objectStore().exists(expectedKey))
        assertEquals(snapshot, storage.loadFeatureCompletionSnapshot("project-1", "feature-1"))
    }

    @Test
    fun `list feature completion snapshots returns all stored snapshots for project`() {
        val storage = storage()
        storage.saveFeatureCompletionSnapshot(
            FeatureCompletionSnapshot(
                projectId = "project-1",
                featureId = "feature-2",
                derivedStatus = LivingSyncFeatureStatus.IN_PROGRESS,
                summary = "In progress.",
                sourceEventId = "event-2",
                sourceFileName = "feature-2.md",
                updatedAt = "2026-05-12T10:01:00Z",
            ),
        )
        storage.saveFeatureCompletionSnapshot(
            FeatureCompletionSnapshot(
                projectId = "project-1",
                featureId = "feature-1",
                derivedStatus = LivingSyncFeatureStatus.DONE,
                summary = "Done.",
                sourceEventId = "event-1",
                sourceFileName = "feature-1.md",
                updatedAt = "2026-05-12T10:00:00Z",
            ),
        )
        storage.saveFeatureCompletionSnapshot(
            FeatureCompletionSnapshot(
                projectId = "project-2",
                featureId = "feature-x",
                derivedStatus = LivingSyncFeatureStatus.BLOCKED,
                summary = "Other project.",
                sourceEventId = "event-x",
                sourceFileName = "feature-x.md",
                updatedAt = "2026-05-12T10:02:00Z",
            ),
        )

        val snapshots = storage.listFeatureCompletionSnapshots("project-1")

        assertEquals(listOf("feature-1", "feature-2"), snapshots.map { it.featureId })
    }
}
