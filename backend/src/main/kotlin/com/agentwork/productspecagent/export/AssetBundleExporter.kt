package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.assetBundleSlug
import com.agentwork.productspecagent.storage.AssetBundleStorage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.zip.ZipOutputStream

data class MatchedBundle(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

@Service
class AssetBundleExporter(private val storage: AssetBundleStorage) {

    private val log = LoggerFactory.getLogger(AssetBundleExporter::class.java)

    fun matchedBundles(wizardData: WizardData): List<MatchedBundle> {
        val manifests = try {
            storage.listAll()
        } catch (e: Exception) {
            log.warn("AssetBundleStorage.listAll failed — exporting without bundles: {}", e.message)
            return emptyList()
        }

        val matched = manifests.filter { m ->
            val raw = wizardData.steps[m.step.name]?.fields?.get(m.field) ?: return@filter false
            val candidates: Set<String> = when (raw) {
                is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
                is JsonPrimitive -> if (raw is JsonNull) emptySet() else setOf(raw.content)
                else -> emptySet()
            }
            val bundleSlug = assetBundleSlug(m.value)
            candidates.any { assetBundleSlug(it) == bundleSlug }
        }

        return matched.mapNotNull { m ->
            try {
                val bundle = storage.find(m.step, m.field, m.value)
                if (bundle == null) {
                    log.warn("Bundle '{}' disappeared between listAll and find — skipping", m.id)
                    null
                } else {
                    MatchedBundle(bundle.manifest, bundle.files)
                }
            } catch (e: Exception) {
                log.warn("Failed to load bundle '{}': {} — skipping", m.id, e.message)
                null
            }
        }.sortedBy { it.manifest.id }
    }

    fun writeToZip(zip: ZipOutputStream, prefix: String, bundles: List<MatchedBundle>) {
        // Implemented in a later task
        throw NotImplementedError("writeToZip not yet implemented")
    }

    fun renderReadmeSection(bundles: List<MatchedBundle>): String {
        // Implemented in a later task
        throw NotImplementedError("renderReadmeSection not yet implemented")
    }
}
