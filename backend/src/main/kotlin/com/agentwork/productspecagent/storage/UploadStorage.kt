package com.agentwork.productspecagent.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class UploadStorage(
    @Value("\${app.data-path}") private val dataPath: String
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun uploadsDir(projectId: String): Path =
        Paths.get(dataPath, "projects", projectId, "uploads")

    private fun indexFile(projectId: String): Path =
        uploadsDir(projectId).resolve(".index.json")

    fun save(projectId: String, docId: String, title: String, bytes: ByteArray): String {
        val dir = uploadsDir(projectId)
        Files.createDirectories(dir)

        val sanitized = sanitizeFilename(title)
        val filename = uniqueFilename(dir, sanitized)
        Files.write(dir.resolve(filename), bytes)

        val index = readIndex(projectId).toMutableMap()
        index[docId] = filename
        writeIndex(projectId, index)

        return filename
    }

    fun delete(projectId: String, docId: String) {
        val index = readIndex(projectId).toMutableMap()
        val filename = index.remove(docId) ?: return
        val file = uploadsDir(projectId).resolve(filename)
        Files.deleteIfExists(file)
        writeIndex(projectId, index)
    }

    fun list(projectId: String): List<String> {
        val dir = uploadsDir(projectId)
        if (!Files.exists(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream
                .filter { !Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .filter { it != ".index.json" }
                .sorted()
                .toList()
        }
    }

    private fun readIndex(projectId: String): Map<String, String> {
        val file = indexFile(projectId)
        if (!Files.exists(file)) return emptyMap()
        return json.decodeFromString(Files.readString(file))
    }

    private fun writeIndex(projectId: String, index: Map<String, String>) {
        val file = indexFile(projectId)
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(index))
    }

    private fun sanitizeFilename(title: String): String {
        val cleaned = title
            .replace("/", "")
            .replace("\\", "")
            .replace("..", "")
            .trim()
        if (cleaned.isEmpty()) return "document"
        return cleaned.take(255)
    }

    private fun uniqueFilename(dir: Path, sanitized: String): String {
        if (!Files.exists(dir.resolve(sanitized))) return sanitized
        val dotIdx = sanitized.lastIndexOf('.')
        val (base, ext) = if (dotIdx > 0) sanitized.substring(0, dotIdx) to sanitized.substring(dotIdx)
        else sanitized to ""
        var n = 2
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!Files.exists(dir.resolve(candidate))) return candidate
            n++
        }
    }
}
