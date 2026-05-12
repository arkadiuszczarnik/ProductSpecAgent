package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.FeatureCompletionSnapshot
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
    private fun importedMarkdownKey(projectId: String, featureId: String, eventId: String) =
        "projects/$projectId/sync/imports/$featureId/$eventId.md"
    private fun snapshotKey(projectId: String, featureId: String) =
        "projects/$projectId/sync/feature-snapshots/$featureId.json"

    fun saveEvent(event: LivingSyncEvent) {
        objectStore.put(
            eventKey(event.projectId, event.id),
            json.encodeToString(event).toByteArray(),
            "application/json",
        )
    }

    fun saveImportedDoneMarkdown(projectId: String, featureId: String, eventId: String, markdown: String) {
        objectStore.put(
            importedMarkdownKey(projectId, featureId, eventId),
            markdown.toByteArray(),
            "text/markdown",
        )
    }

    fun loadImportedDoneMarkdown(projectId: String, featureId: String, eventId: String): String? =
        objectStore.get(importedMarkdownKey(projectId, featureId, eventId))
            ?.toString(Charsets.UTF_8)

    fun saveFeatureCompletionSnapshot(snapshot: FeatureCompletionSnapshot) {
        objectStore.put(
            snapshotKey(snapshot.projectId, snapshot.featureId),
            json.encodeToString(snapshot).toByteArray(),
            "application/json",
        )
    }

    fun loadFeatureCompletionSnapshot(projectId: String, featureId: String): FeatureCompletionSnapshot? =
        objectStore.get(snapshotKey(projectId, featureId))
            ?.toString(Charsets.UTF_8)
            ?.let { json.decodeFromString<FeatureCompletionSnapshot>(it) }

    fun listEvents(projectId: String): List<LivingSyncEvent> =
        objectStore.listKeys(eventsPrefix(projectId))
            .mapNotNull { key ->
                objectStore.get(key)
                    ?.toString(Charsets.UTF_8)
                    ?.let { json.decodeFromString<LivingSyncEvent>(it) }
            }
            .sortedBy { it.createdAt }
}
