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
        return folders.mapNotNull { folder -> readManifest(folder) }
    }

    fun find(step: FlowStepType, field: String, value: String): AssetBundle? {
        val id = assetBundleId(step, field, value)
        val bundlePrefix = "$rootPrefix$id/"
        val manifest = readManifestByKey("${bundlePrefix}manifest.json") ?: return null

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

    private fun readManifest(folder: String): AssetBundleManifest? =
        readManifestByKey("$rootPrefix$folder/manifest.json")

    private fun readManifestByKey(key: String): AssetBundleManifest? {
        val bytes = objectStore.get(key)
        if (bytes == null) {
            log.warn("Asset bundle '{}' has no manifest.json — skipping", key)
            return null
        }
        return try {
            json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            log.warn("Asset bundle '{}' has invalid manifest.json: {} — skipping", key, e.message)
            null
        }
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
