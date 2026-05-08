package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.HandoffPreview
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

@Service
class HandoffOverlayWriter(
    private val handoffContentFactory: HandoffContentFactory,
) {
    fun writeHandoffOverlay(writer: ZipArchiveWriter, preview: HandoffPreview) {
        writer.addText("CLAUDE.md", preview.claudeMd)
        writer.addText("AGENTS.md", preview.agentsMd)
        writer.addText("implementation-order.md", preview.implementationOrder)
        writer.addText(".asset-bundles/settings.json", handoffContentFactory.livingSyncSettings())
        writer.addText(".asset-bundles/living-sync.json", handoffContentFactory.livingSyncConfig(preview.syncUrl))
        writeEmbeddedSyncBundles(writer)
        writeToolSymlinks(writer)
    }

    private fun writeEmbeddedSyncBundles(writer: ZipArchiveWriter) {
        for ((resourcePath, bundleId) in embeddedBundleResources) {
            val bytes = loadResourceBytes(resourcePath) ?: continue
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name != "manifest.json") {
                        val slash = entry.name.indexOf('/')
                        if (slash > 0) {
                            val type = entry.name.substring(0, slash)
                            val rest = entry.name.substring(slash + 1)
                            if (type in allowedBundleTypes && rest.isNotBlank()) {
                                val target = ".asset-bundles/$type/$bundleId/$rest"
                                if (!writer.hasEntry(target)) {
                                    writer.addBytes(target, zip.readBytes())
                                }
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun loadResourceBytes(resourcePath: String): ByteArray? {
        Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?.use { return it.readBytes() }

        for (base in localBundleRoots) {
            val path = base.resolve(resourcePath)
            if (Files.exists(path)) return Files.readAllBytes(path)
        }
        return null
    }

    private fun writeToolSymlinks(writer: ZipArchiveWriter) {
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

    private companion object {
        val embeddedBundleResources = listOf(
            "asset-bundles/living-sync-reporter-bundle.zip" to "global.living-sync-reporter",
            "asset-bundles/product-spec-sync-bundle.zip" to "global.product-spec-sync",
        )
        val allowedBundleTypes = setOf("skills", "commands", "agents")
        val localBundleRoots = listOf(Path.of("."), Path.of(".."))
    }
}
