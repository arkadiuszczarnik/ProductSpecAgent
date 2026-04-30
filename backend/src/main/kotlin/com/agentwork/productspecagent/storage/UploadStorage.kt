package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.domain.DocumentState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.time.Instant

@Service
open class UploadStorage(private val objectStore: ObjectStore) {

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

    private fun uploadsPrefix(projectId: String) = "projects/$projectId/docs/uploads/"
    private fun fileKey(projectId: String, filename: String) =
        "${uploadsPrefix(projectId)}$filename"
    private fun indexKey(projectId: String) =
        "${uploadsPrefix(projectId)}.index.json"

    open fun save(
        projectId: String,
        docId: String,
        title: String,
        mimeType: String,
        bytes: ByteArray,
        createdAt: String = Instant.now().toString()
    ): String {
        val sanitized = sanitizeFilename(title)
        val filename = uniqueFilename(projectId, sanitized)
        objectStore.put(fileKey(projectId, filename), bytes, mimeType)

        val entries = readEntries(projectId).filter { it.id != docId }.toMutableList()
        entries += IndexEntry(docId, filename, title, mimeType, createdAt)
        writeEntries(projectId, entries)

        return filename
    }

    open fun delete(projectId: String, docId: String) {
        val entries = readEntries(projectId).toMutableList()
        val entry = entries.firstOrNull { it.id == docId } ?: return
        entries.remove(entry)
        objectStore.delete(fileKey(projectId, entry.filename))
        writeEntries(projectId, entries)
    }

    open fun read(projectId: String, filename: String): ByteArray =
        objectStore.get(fileKey(projectId, filename))
            ?: throw NoSuchElementException("Upload not found: $filename")

    open fun readById(projectId: String, docId: String): ByteArray {
        val entry = readEntries(projectId).firstOrNull { it.id == docId }
            ?: throw NoSuchElementException("Upload not found for docId: $docId")
        return read(projectId, entry.filename)
    }

    open fun list(projectId: String): List<String> =
        objectStore.listKeys(uploadsPrefix(projectId))
            .map { it.removePrefix(uploadsPrefix(projectId)) }
            .filter { it != ".index.json" }
            .sorted()

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
        val raw = objectStore.get(indexKey(projectId))?.toString(Charsets.UTF_8) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        val newFormatAttempt = try {
            json.decodeFromString<IndexFile>(raw).documents
        } catch (_: Exception) {
            null
        }

        if (newFormatAttempt != null && raw.matches(Regex("(?s).*\"documents\"\\s*:.*"))) {
            return newFormatAttempt
        }

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
        objectStore.put(
            indexKey(projectId),
            json.encodeToString(IndexFile(entries)).toByteArray(),
            "application/json"
        )
    }

    private fun readMtime(projectId: String, filename: String): String {
        val targetKey = fileKey(projectId, filename)
        return objectStore.listEntries(uploadsPrefix(projectId))
            .firstOrNull { it.key == targetKey }
            ?.lastModified
            ?.toString()
            ?: Instant.now().toString()
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

    private fun uniqueFilename(projectId: String, sanitized: String): String {
        if (!objectStore.exists(fileKey(projectId, sanitized))) return sanitized
        val dotIdx = sanitized.lastIndexOf('.')
        val (base, ext) = if (dotIdx > 0) {
            sanitized.substring(0, dotIdx) to sanitized.substring(dotIdx)
        } else {
            sanitized to ""
        }
        var n = 2
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!objectStore.exists(fileKey(projectId, candidate))) return candidate
            n++
        }
    }
}
