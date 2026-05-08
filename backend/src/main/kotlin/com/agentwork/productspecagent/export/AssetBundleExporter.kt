package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.AssetBundleScope
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.assetBundleSlug
import com.agentwork.productspecagent.storage.AssetBundleStorage
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.StringWriter
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.agentwork.productspecagent.export.ZipSymlinkSupport.addSymlinkEntry

data class MatchedBundle(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

@Service
class AssetBundleExporter(private val storage: AssetBundleStorage) {

    private val log = LoggerFactory.getLogger(AssetBundleExporter::class.java)
    private val mf: MustacheFactory = DefaultMustacheFactory("templates/export")

    fun matchedBundles(wizardData: WizardData): List<MatchedBundle> {
        val manifests = try {
            storage.listAll()
        } catch (e: Exception) {
            log.warn("AssetBundleStorage.listAll failed — exporting without bundles: {}", e.message)
            return emptyList()
        }

        val matched = manifests.filter { m ->
            if (m.scope == AssetBundleScope.GLOBAL) return@filter true
            val step = m.step ?: return@filter false
            val field = m.field ?: return@filter false
            val value = m.value ?: return@filter false
            val raw = wizardData.steps[step.name]?.fields?.get(field) ?: return@filter false
            val candidates: Set<String> = when (raw) {
                is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
                is JsonPrimitive -> if (raw is JsonNull) emptySet() else setOf(raw.content)
                else -> emptySet()
            }
            val bundleSlug = assetBundleSlug(value)
            candidates.any { assetBundleSlug(it) == bundleSlug }
        }

        return matched.mapNotNull { m ->
            try {
                val bundle = storage.findById(m.id)
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

                val bytes = storage.loadFileBytesById(bundle.manifest.id, file.relativePath)
                if (bytes == null) {
                    log.warn("loadFileBytes returned null for {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                    continue
                }

                val entryName = "$prefix/.asset-bundles/$type/${bundle.manifest.id}/$rest"
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun writeToArchive(writer: ZipArchiveWriter, bundles: List<MatchedBundle>) {
        val allowedTypes = setOf("skills", "commands", "agents")
        for (bundle in bundles) {
            for (file in bundle.files) {
                val firstSlash = file.relativePath.indexOf('/')
                if (firstSlash < 0) continue
                val type = file.relativePath.substring(0, firstSlash)
                val rest = file.relativePath.substring(firstSlash + 1)
                if (type !in allowedTypes) continue

                if (isSuspiciousPath(file.relativePath)) {
                    log.warn("Suspicious path {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                    continue
                }

                val bytes = storage.loadFileBytesById(bundle.manifest.id, file.relativePath)
                if (bytes == null) {
                    log.warn("loadFileBytes returned null for {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                    continue
                }

                writer.addBytes(".asset-bundles/$type/${bundle.manifest.id}/$rest", bytes)
            }
        }
    }

    fun writeToolLinksToZip(zip: ZipOutputStream, prefix: String): List<String> {
        val root = prefix.trimEnd('/')
        val base = if (root.isBlank()) "" else "$root/"
        for (type in listOf("skills", "commands", "agents")) {
            zip.putNextEntry(ZipEntry("${base}.asset-bundles/$type/"))
            zip.closeEntry()
        }

        val links = mapOf(
            "${base}.claude" to ".asset-bundles",
            "${base}.agents" to ".asset-bundles",
        )
        for ((name, target) in links) {
            zip.addSymlinkEntry(name, target)
        }
        return links.keys.toList()
    }

    fun writeToolLinksToArchive(writer: ZipArchiveWriter) {
        listOf(
            ".asset-bundles/skills",
            ".asset-bundles/commands",
            ".asset-bundles/agents",
        ).forEach { writer.addDirectory(it) }

        val links = mapOf(
            ".claude" to ".asset-bundles",
            ".agents" to ".asset-bundles",
        )
        for ((name, target) in links) {
            writer.addSymlink(name, target)
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

        return render(
            "asset-bundles-readme-section.md.mustache",
            mapOf(
                "bundles" to bundles.sortedBy { it.manifest.id }.map { bundle ->
                    val manifest = bundle.manifest
                    mapOf(
                        "title" to manifest.title,
                        "id" to manifest.id,
                        "version" to manifest.version,
                        "scope" to manifest.scope.name,
                        "step" to manifest.step?.name,
                        "field" to manifest.field,
                        "value" to manifest.value,
                        "description" to manifest.description.ifBlank { null },
                    )
                },
            ),
        )
    }

    private fun render(templatePath: String, scope: Any): String {
        val mustache = mf.compile(templatePath)
        val writer = StringWriter()
        mustache.execute(writer, scope).flush()
        return writer.toString()
    }
}
