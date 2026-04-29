package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.AssetBundleManifest
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

    private fun readManifest(folder: String): AssetBundleManifest? {
        val key = "$rootPrefix$folder/manifest.json"
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
}
