package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Clarification
import com.agentwork.productspecagent.service.ClarificationNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class ClarificationStorage(private val objectStore: ObjectStore) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun clarificationsPrefix(projectId: String) = "projects/$projectId/docs/clarifications/"
    private fun clarificationKey(projectId: String, clarificationId: String) =
        "${clarificationsPrefix(projectId)}$clarificationId.json"

    fun saveClarification(clarification: Clarification) {
        objectStore.put(
            clarificationKey(clarification.projectId, clarification.id),
            json.encodeToString(clarification).toByteArray(),
            "application/json"
        )
    }

    fun loadClarification(projectId: String, clarificationId: String): Clarification {
        val bytes = objectStore.get(clarificationKey(projectId, clarificationId))
            ?: throw ClarificationNotFoundException(clarificationId)
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    fun listClarifications(projectId: String): List<Clarification> =
        objectStore.listKeys(clarificationsPrefix(projectId))
            .filter { it.endsWith(".json") }
            .mapNotNull { key ->
                objectStore.get(key)
                    ?.toString(Charsets.UTF_8)
                    ?.let { json.decodeFromString<Clarification>(it) }
            }
            .sortedBy { it.createdAt }

    fun deleteClarification(projectId: String, clarificationId: String) {
        objectStore.delete(clarificationKey(projectId, clarificationId))
    }
}
