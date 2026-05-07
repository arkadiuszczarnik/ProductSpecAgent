package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.LivingSyncEvent
import com.agentwork.productspecagent.domain.LivingSyncEventType
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
}
