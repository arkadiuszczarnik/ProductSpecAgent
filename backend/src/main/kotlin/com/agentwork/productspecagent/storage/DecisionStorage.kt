package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Decision
import com.agentwork.productspecagent.service.DecisionNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

@Service
class DecisionStorage(
    @Value("\${app.data-path}") private val dataPath: String
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun decisionsDir(projectId: String): Path =
        Path.of(dataPath, "projects", projectId, "docs", "decisions")

    private fun decisionFile(projectId: String, decisionId: String): Path =
        decisionsDir(projectId).resolve("$decisionId.json")

    fun saveDecision(decision: Decision) {
        val dir = decisionsDir(decision.projectId)
        Files.createDirectories(dir)
        decisionFile(decision.projectId, decision.id).writeText(json.encodeToString(decision))
    }

    fun loadDecision(projectId: String, decisionId: String): Decision {
        val file = decisionFile(projectId, decisionId)
        if (!file.exists()) throw DecisionNotFoundException(decisionId)
        return json.decodeFromString(file.readText())
    }

    fun listDecisions(projectId: String): List<Decision> {
        val dir = decisionsDir(projectId)
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries("*.json")
            .map { json.decodeFromString<Decision>(it.readText()) }
            .sortedBy { it.createdAt }
    }

    fun deleteDecision(projectId: String, decisionId: String) {
        decisionFile(projectId, decisionId).deleteIfExists()
    }
}
