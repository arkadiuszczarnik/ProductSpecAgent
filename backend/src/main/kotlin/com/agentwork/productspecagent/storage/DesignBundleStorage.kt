package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignBundle
import com.agentwork.productspecagent.service.DesignBundleExtractor
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.nio.file.Paths

@Service
open class DesignBundleStorage(
    private val objectStore: ObjectStore,
    private val extractor: DesignBundleExtractor,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun designPrefix(projectId: String) = "projects/$projectId/design/"
    private fun zipKey(projectId: String) = "${designPrefix(projectId)}bundle.zip"
    private fun manifestKey(projectId: String) = "${designPrefix(projectId)}manifest.json"
    private fun filesPrefix(projectId: String) = "${designPrefix(projectId)}files/"
    private fun fileKey(projectId: String, relPath: String) =
        "${filesPrefix(projectId)}$relPath"

    open fun save(projectId: String, originalFilename: String, zipBytes: ByteArray): DesignBundle {
        val result = extractor.extract(zipBytes, originalFilename)

        // Cleanup any prior state
        deleteFiles(projectId)

        // Write all extracted files
        for ((relPath, data) in result.files) {
            objectStore.put(fileKey(projectId, relPath), data, contentTypeFor(relPath))
        }
        // Write original ZIP
        objectStore.put(zipKey(projectId), zipBytes, "application/zip")

        // Persist manifest with projectId populated
        val bundle = result.bundle.copy(projectId = projectId)
        objectStore.put(
            manifestKey(projectId),
            json.encodeToString(DesignBundle.serializer(), bundle).toByteArray(),
            "application/json",
        )
        return bundle
    }

    open fun get(projectId: String): DesignBundle? {
        val raw = objectStore.get(manifestKey(projectId)) ?: return null
        return json.decodeFromString(DesignBundle.serializer(), raw.toString(Charsets.UTF_8))
    }

    open fun readFile(projectId: String, relPath: String): ByteArray {
        val rootPath = Paths.get("/__files__").normalize()
        val resolved = rootPath.resolve(relPath).normalize()
        require(resolved.startsWith(rootPath)) { "path traversal rejected: $relPath" }
        val key = fileKey(projectId, relPath)
        return objectStore.get(key) ?: throw NoSuchElementException("file not found: $relPath")
    }

    open fun delete(projectId: String) {
        deleteFiles(projectId)
        objectStore.delete(zipKey(projectId))
        objectStore.delete(manifestKey(projectId))
        // design.md cleanup is delegated to caller (ProjectService) — keep storage focused.
    }

    private fun deleteFiles(projectId: String) {
        for (key in objectStore.listKeys(filesPrefix(projectId))) {
            objectStore.delete(key)
        }
    }

    private fun contentTypeFor(path: String): String {
        // Mirrors DesignBundleExtractor.mimeTypeFor — duplicated to avoid public API leak
        val lower = path.lowercase()
        return when {
            lower.endsWith(".html") -> "text/html"
            lower.endsWith(".css")  -> "text/css"
            lower.endsWith(".js") || lower.endsWith(".jsx") -> "text/javascript"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".png")  -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif")  -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".svg")  -> "image/svg+xml"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".ttf")  -> "font/ttf"
            lower.endsWith(".md")   -> "text/markdown"
            lower.endsWith(".txt")  -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
