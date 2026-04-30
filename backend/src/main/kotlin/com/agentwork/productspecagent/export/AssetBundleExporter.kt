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
import java.nio.file.Paths
import java.util.zip.ZipEntry
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
        val allowedTypes = setOf("skills", "commands", "agents")
        for (bundle in bundles) {
            for (file in bundle.files) {
                val firstSlash = file.relativePath.indexOf('/')
                if (firstSlash < 0) continue
                val type = file.relativePath.substring(0, firstSlash)
                val rest = file.relativePath.substring(firstSlash + 1)
                if (type !in allowedTypes) continue

                // Defense in depth: reject suspicious paths even though ZipExtractor (B) already guards uploads.
                if (isSuspiciousPath(file.relativePath)) {
                    log.warn("Suspicious path {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                    continue
                }

                val bytes = storage.loadFileBytes(
                    bundle.manifest.step,
                    bundle.manifest.field,
                    bundle.manifest.value,
                    file.relativePath,
                )
                if (bytes == null) {
                    log.warn("loadFileBytes returned null for {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                    continue
                }

                val entryName = "$prefix/.claude/$type/${bundle.manifest.id}/$rest"
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun isSuspiciousPath(relativePath: String): Boolean {
        if (relativePath.startsWith("/")) return true
        if ("..".let { it in relativePath.split('/') }) return true
        return try {
            val normalized = Paths.get(relativePath).normalize().toString().replace('\\', '/')
            normalized != relativePath
        } catch (e: Exception) {
            true
        }
    }

    fun renderReadmeSection(bundles: List<MatchedBundle>): String {
        if (bundles.isEmpty()) return ""

        val sorted = bundles.sortedBy { it.manifest.id }
        return buildString {
            appendLine()
            appendLine("## Included Asset Bundles")
            appendLine()
            appendLine("The following Claude Code asset bundles were merged into `.claude/` based on your wizard choices:")
            appendLine()
            for (b in sorted) {
                val m = b.manifest
                appendLine("- **${m.title}** (`${m.id}` v${m.version}) — matched on `${m.step.name}.${m.field} = ${m.value}`")
                if (m.description.isNotBlank()) {
                    appendLine("  ${m.description}")
                }
            }
        }
    }
}
