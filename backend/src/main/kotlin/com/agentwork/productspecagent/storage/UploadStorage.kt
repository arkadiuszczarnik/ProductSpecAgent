package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.domain.DocumentState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

@Service
open class UploadStorage(
    @Value("\${app.data-path}") private val dataPath: String
) {

    @Serializable
    data class IndexEntry(
        val id: String,
        val filename: String,
        val title: String,
        val mimeType: String,
        val createdAt: String
    )

    @Serializable
    private data class IndexFile(val documents: List<IndexEntry> = emptyList())

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun uploadsDir(projectId: String): Path =
        Paths.get(dataPath, "projects", projectId, "docs", "uploads")

    private fun indexFile(projectId: String): Path =
        uploadsDir(projectId).resolve(".index.json")

    open fun save(
        projectId: String,
        docId: String,
        title: String,
        mimeType: String,
        bytes: ByteArray,
        createdAt: String = Instant.now().toString()
    ): String {
        val dir = uploadsDir(projectId)
        Files.createDirectories(dir)

        val sanitized = sanitizeFilename(title)
        val filename = uniqueFilename(dir, sanitized)
        Files.write(dir.resolve(filename), bytes)

        val entries = readEntries(projectId).filter { it.id != docId }.toMutableList()
        entries += IndexEntry(docId, filename, title, mimeType, createdAt)
        writeEntries(projectId, entries)

        return filename
    }

    open fun delete(projectId: String, docId: String) {
        val entries = readEntries(projectId).toMutableList()
        val entry = entries.firstOrNull { it.id == docId } ?: return
        entries.remove(entry)
        Files.deleteIfExists(uploadsDir(projectId).resolve(entry.filename))
        writeEntries(projectId, entries)
    }

    open fun read(projectId: String, filename: String): ByteArray =
        Files.readAllBytes(uploadsDir(projectId).resolve(filename))

    open fun list(projectId: String): List<String> {
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

    open fun listAsDocuments(projectId: String): List<Document> =
        readEntries(projectId).map { it.toDocument() }

    open fun getDocument(projectId: String, docId: String): Document? =
        readEntries(projectId).firstOrNull { it.id == docId }?.toDocument()

    private fun IndexEntry.toDocument() = Document(
        id = id,
        title = title,
        mimeType = mimeType,
        state = DocumentState.LOCAL,
        createdAt = createdAt
    )

    private fun readEntries(projectId: String): List<IndexEntry> {
        val file = indexFile(projectId)
        if (!Files.exists(file)) return emptyList()
        val raw = Files.readString(file)
        if (raw.isBlank()) return emptyList()

        // Try new format first.
        val newFormatAttempt = try {
            json.decodeFromString<IndexFile>(raw).documents
        } catch (_: Exception) {
            null
        }

        // If the decode succeeded AND the JSON actually carries a `documents` field,
        // we trust the new-format result (even if the list is empty).
        if (newFormatAttempt != null && raw.matches(Regex("(?s).*\"documents\"\\s*:.*"))) {
            return newFormatAttempt
        }

        // Otherwise, attempt legacy decode + migrate.
        val legacy = try {
            json.decodeFromString<Map<String, String>>(raw)
        } catch (_: Exception) {
            return newFormatAttempt ?: emptyList()
        }
        val migrated = legacy.map { (id, filename) ->
            IndexEntry(
                id = id,
                filename = filename,
                title = filename,
                mimeType = inferMimeType(filename),
                createdAt = readMtime(projectId, filename)
            )
        }
        writeEntries(projectId, migrated)
        return migrated
    }

    private fun writeEntries(projectId: String, entries: List<IndexEntry>) {
        val file = indexFile(projectId)
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(IndexFile(entries)))
    }

    private fun readMtime(projectId: String, filename: String): String {
        val file = uploadsDir(projectId).resolve(filename)
        return try {
            Files.getLastModifiedTime(file).toInstant().toString()
        } catch (_: Exception) {
            Instant.now().toString()
        }
    }

    private fun inferMimeType(filename: String): String {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".md") -> "text/markdown"
            lower.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
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
