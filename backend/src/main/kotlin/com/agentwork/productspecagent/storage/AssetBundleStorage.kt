package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.AssetBundle
import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.assetBundleId
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AssetBundleStorage(private val objectStore: ObjectStore) {

    private val log = LoggerFactory.getLogger(AssetBundleStorage::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val rootPrefix = "asset-bundles/"

    fun listAll(): List<AssetBundleManifest> {
        val folders = objectStore.listCommonPrefixes(rootPrefix, "/")
        return folders.mapNotNull { folder ->
            readManifest(folder)?.also { warnIfIdMismatch(folder, it) }
        }
    }

    fun find(step: FlowStepType, field: String, value: String): AssetBundle? {
        val id = assetBundleId(step, field, value)
        val bundlePrefix = "$rootPrefix$id/"
        val manifest = readManifestByKey("${bundlePrefix}manifest.json") ?: return null
        warnIfIdMismatch(id, manifest)

        val files = objectStore.listKeys(bundlePrefix)
            .filter { it != "${bundlePrefix}manifest.json" }
            .map { fullKey ->
                val relativePath = fullKey.removePrefix(bundlePrefix)
                AssetBundleFile(
                    relativePath = relativePath,
                    size = (objectStore.get(fullKey)?.size ?: 0).toLong(),
                    contentType = contentTypeFor(relativePath),
                )
            }

        return AssetBundle(manifest = manifest, files = files)
    }

    private fun readManifest(folder: String): AssetBundleManifest? {
        val key = "$rootPrefix$folder/manifest.json"
        val bytes = objectStore.get(key)
        if (bytes == null) {
            log.warn("Asset bundle folder '{}' has no manifest.json — skipping", folder)
            return null
        }
        return parseManifest(key, bytes)
    }

    private fun readManifestByKey(key: String): AssetBundleManifest? {
        val bytes = objectStore.get(key) ?: return null
        return parseManifest(key, bytes)
    }

    private fun parseManifest(key: String, bytes: ByteArray): AssetBundleManifest? = try {
        json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
    } catch (e: Exception) {
        log.warn("Asset bundle '{}' has invalid manifest.json: {} — skipping", key, e.message)
        null
    }

    private fun warnIfIdMismatch(expectedId: String, manifest: AssetBundleManifest) {
        if (manifest.id != expectedId) {
            log.warn(
                "Asset bundle at folder '{}' has manifest id '{}' — id should match folder name (folder is source of truth for location, manifest is source of truth otherwise)",
                expectedId, manifest.id
            )
        }
    }

    fun writeBundle(manifest: AssetBundleManifest, files: Map<String, ByteArray>) {
        val bundlePrefix = "$rootPrefix${manifest.id}/"
        // Write all files first, manifest LAST — find() uses manifest as existence marker
        files.forEach { (relativePath, bytes) ->
            objectStore.put("$bundlePrefix$relativePath", bytes, contentTypeFor(relativePath))
        }
        val manifestJson = json.encodeToString(AssetBundleManifest.serializer(), manifest).toByteArray()
        objectStore.put("${bundlePrefix}manifest.json", manifestJson, "application/json")
    }

    fun delete(step: FlowStepType, field: String, value: String) {
        val id = assetBundleId(step, field, value)
        objectStore.deletePrefix("$rootPrefix$id/")
    }

    fun loadFileBytes(step: FlowStepType, field: String, value: String, relativePath: String): ByteArray? {
        val id = assetBundleId(step, field, value)
        return objectStore.get("$rootPrefix$id/$relativePath")
    }

    private fun contentTypeFor(relativePath: String): String =
        when (relativePath.substringAfterLast('.', "").lowercase()) {
            "md", "markdown" -> "text/markdown"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "yaml", "yml" -> "application/yaml"
            "py" -> "text/x-python"
            "ts", "tsx" -> "application/typescript"
            "js", "mjs" -> "application/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
}
