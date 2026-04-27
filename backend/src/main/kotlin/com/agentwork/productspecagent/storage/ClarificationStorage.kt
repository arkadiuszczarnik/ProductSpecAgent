package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Clarification
import com.agentwork.productspecagent.service.ClarificationNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

@Service
class ClarificationStorage(
    @Value("\${app.data-path}") private val dataPath: String
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun clarificationsDir(projectId: String): Path =
        Path.of(dataPath, "projects", projectId, "docs", "clarifications")

    private fun clarificationFile(projectId: String, clarificationId: String): Path =
        clarificationsDir(projectId).resolve("$clarificationId.json")

    fun saveClarification(clarification: Clarification) {
        val dir = clarificationsDir(clarification.projectId)
        Files.createDirectories(dir)
        clarificationFile(clarification.projectId, clarification.id)
            .writeText(json.encodeToString(clarification))
    }

    fun loadClarification(projectId: String, clarificationId: String): Clarification {
        val file = clarificationFile(projectId, clarificationId)
        if (!file.exists()) throw ClarificationNotFoundException(clarificationId)
        return json.decodeFromString(file.readText())
    }

    fun listClarifications(projectId: String): List<Clarification> {
        val dir = clarificationsDir(projectId)
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries("*.json")
            .map { json.decodeFromString<Clarification>(it.readText()) }
            .sortedBy { it.createdAt }
    }

    fun deleteClarification(projectId: String, clarificationId: String) {
        clarificationFile(projectId, clarificationId).deleteIfExists()
    }
}
