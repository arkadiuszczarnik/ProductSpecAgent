package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Decision
import com.agentwork.productspecagent.service.DecisionNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class DecisionStorage(private val objectStore: ObjectStore) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun decisionsPrefix(projectId: String) = "projects/$projectId/docs/decisions/"
    private fun decisionKey(projectId: String, decisionId: String) =
        "${decisionsPrefix(projectId)}$decisionId.json"

    fun saveDecision(decision: Decision) {
        objectStore.put(
            decisionKey(decision.projectId, decision.id),
            json.encodeToString(decision).toByteArray(),
            "application/json"
        )
    }

    fun loadDecision(projectId: String, decisionId: String): Decision {
        val bytes = objectStore.get(decisionKey(projectId, decisionId))
            ?: throw DecisionNotFoundException(decisionId)
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    fun listDecisions(projectId: String): List<Decision> =
        objectStore.listKeys(decisionsPrefix(projectId))
            .filter { it.endsWith(".json") }
            .mapNotNull { key ->
                objectStore.get(key)
                    ?.toString(Charsets.UTF_8)
                    ?.let { json.decodeFromString<Decision>(it) }
            }
            .sortedBy { it.createdAt }

    fun deleteDecision(projectId: String, decisionId: String) {
        objectStore.delete(decisionKey(projectId, decisionId))
    }
}
