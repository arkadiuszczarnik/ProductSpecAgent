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
        writeToolSymlinks(writer)
    }

    private fun writeToolSymlinks(writer: ZipArchiveWriter) {
        listOf(
            ".asset-bundles/skills",
            ".asset-bundles/commands",
            ".asset-bundles/agents",
        ).forEach { writer.addDirectory(it) }

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

}
