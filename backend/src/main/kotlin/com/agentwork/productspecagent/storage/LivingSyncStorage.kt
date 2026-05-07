package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.LivingSyncEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class LivingSyncStorage(private val objectStore: ObjectStore) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun eventKey(projectId: String, eventId: String) = "projects/$projectId/sync/events/$eventId.json"
    private fun eventsPrefix(projectId: String) = "projects/$projectId/sync/events/"

    fun saveEvent(event: LivingSyncEvent) {
        objectStore.put(
            eventKey(event.projectId, event.id),
            json.encodeToString(event).toByteArray(),
            "application/json",
        )
    }

    fun listEvents(projectId: String): List<LivingSyncEvent> =
        objectStore.listKeys(eventsPrefix(projectId))
            .mapNotNull { key ->
                objectStore.get(key)
                    ?.toString(Charsets.UTF_8)
                    ?.let { json.decodeFromString<LivingSyncEvent>(it) }
            }
            .sortedBy { it.createdAt }
}
