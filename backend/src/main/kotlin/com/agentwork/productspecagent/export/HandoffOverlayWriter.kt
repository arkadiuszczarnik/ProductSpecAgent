package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.HandoffPreview
import org.springframework.stereotype.Service

@Service
class HandoffOverlayWriter(
    private val handoffContentFactory: HandoffContentFactory,
) {

    fun writeHandoffOverlay(writer: ZipArchiveWriter, preview: HandoffPreview) {
        writer.addText("CLAUDE.md", preview.claudeMd)
        writer.addText("AGENTS.md", preview.agentsMd)
        writer.addText("implementation-order.md", preview.implementationOrder)
        writer.addText(".claude/settings.json", handoffContentFactory.livingSyncSettings())
        writer.addText(".claude/living-sync.json", handoffContentFactory.livingSyncConfig(preview.syncUrl))
        writeLivingSyncAssetBundle(writer)
        writeProductSpecSyncAssetBundle(writer)
        writeToolSymlinks(writer)
    }

    private fun writeLivingSyncAssetBundle(writer: ZipArchiveWriter) {
        val bundleId = "global.living-sync-reporter"
        val textFiles = mapOf(
            "SKILL.md" to "SKILL.md",
            "bin/living-sync-reporter" to "bin/living-sync-reporter",
            "bin/living-sync-reporter.cmd" to "bin/living-sync-reporter.cmd",
        )
        val binaryFiles = listOf(
            "bin/linux-amd64/living-sync-reporter.gz",
            "bin/linux-arm64/living-sync-reporter.gz",
            "bin/darwin-amd64/living-sync-reporter.gz",
            "bin/darwin-arm64/living-sync-reporter.gz",
            "bin/windows-amd64/living-sync-reporter.exe.gz",
        )

        val resourcePrefix = "asset-bundles/living-sync-reporter-bundle/skills/living-sync-reporter"
        val zipPrefix = ".asset-bundles/skills/$bundleId/living-sync-reporter"

        for ((resourceName, entryName) in textFiles) {
            writer.addText("$zipPrefix/$entryName", resourceText("$resourcePrefix/$resourceName"))
        }
        for (resourceName in binaryFiles) {
            writer.addBytes("$zipPrefix/$resourceName", resourceBytes("$resourcePrefix/$resourceName"))
        }
    }

    private fun writeProductSpecSyncAssetBundle(writer: ZipArchiveWriter) {
        val bundleId = "global.product-spec-sync"
        val resourcePath = "asset-bundles/product-spec-sync-bundle/skills/product-spec-sync/SKILL.md"
        val entryName = ".asset-bundles/skills/$bundleId/product-spec-sync/SKILL.md"
        writer.addText(entryName, resourceText(resourcePath))
    }

    private fun writeToolSymlinks(writer: ZipArchiveWriter) {
        val links = mapOf(
            ".claude/skills" to "../.asset-bundles/skills",
            ".claude/commands" to "../.asset-bundles/commands",
            ".claude/agents" to "../.asset-bundles/agents",
            ".agents/skills" to "../.asset-bundles/skills",
            ".agents/commands" to "../.asset-bundles/commands",
            ".agents/agents" to "../.asset-bundles/agents",
        )
        for ((name, target) in links) {
            writer.addSymlink(name, target)
        }
    }

    private fun resourceText(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("Missing classpath resource: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun resourceBytes(path: String): ByteArray {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("Missing classpath resource: $path")
        return stream.use { it.readBytes() }
    }
}
