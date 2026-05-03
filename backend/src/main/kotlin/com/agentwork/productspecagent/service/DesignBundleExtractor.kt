package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.config.DesignBundleProperties
import com.agentwork.productspecagent.domain.DesignPage
import org.springframework.stereotype.Service

@Service
open class DesignBundleExtractor(
    private val props: DesignBundleProperties,
) {
    private val sectionRegex = Regex(
        """<DCSection\s+id=["']([^"']+)["']\s+title=["']([^"']+)["'](?:\s+subtitle=["']([^"']*)["'])?[^>]*>"""
    )
    private val artboardRegex = Regex(
        """<DCArtboard\s+id=["']([^"']+)["']\s+label=["']([^"']+)["']\s+width=\{(\d+)\}\s+height=\{(\d+)\}[^>]*>"""
    )
    private val canvasMarkerRegex = Regex(
        """<script[^>]+src=["'][^"']*design-canvas\.jsx["']"""
    )

    fun parsePages(html: String): List<DesignPage> {
        // Build (offset → section) lookup so each artboard inherits the most
        // recent enclosing section.
        val sectionRanges = sectionRegex.findAll(html).map { m ->
            Triple(m.range.first, m.groupValues[1], m.groupValues[2])
        }.toList()

        return artboardRegex.findAll(html).map { m ->
            val offset = m.range.first
            val enclosing = sectionRanges.lastOrNull { it.first <= offset }
            DesignPage(
                id = m.groupValues[1],
                label = m.groupValues[2],
                sectionId = enclosing?.second ?: "",
                sectionTitle = enclosing?.third ?: "",
                width = m.groupValues[3].toInt(),
                height = m.groupValues[4].toInt(),
            )
        }.toList()
    }

    fun findEntryHtml(candidates: Map<String, String>): String? {
        if (candidates.isEmpty()) return null
        val withCanvas = candidates.filter { (_, body) -> canvasMarkerRegex.containsMatchIn(body) }
        val pool = if (withCanvas.isNotEmpty()) withCanvas else candidates
        return pool.keys.sorted().first()
    }
}
