package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.config.DesignBundleProperties
import com.agentwork.productspecagent.domain.DesignBundle
import com.agentwork.productspecagent.domain.DesignBundleFile
import com.agentwork.productspecagent.domain.DesignPage
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.zip.ZipFile
import org.springframework.stereotype.Service

@Service
open class DesignBundleExtractor(
    private val props: DesignBundleProperties,
) {
    class InvalidBundleException(message: String) : RuntimeException(message)

    data class ExtractionResult(
        val bundle: DesignBundle,
        val files: Map<String, ByteArray>,  // relative path → bytes
    )
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

    fun extract(zipBytes: ByteArray, originalFilename: String): ExtractionResult {
        if (zipBytes.size > props.maxZipBytes) {
            throw InvalidBundleException("zip exceeds maxZipBytes ${props.maxZipBytes}")
        }

        val files = mutableMapOf<String, ByteArray>()
        var totalBytes = 0L
        val rootPath = Paths.get("/__bundle__").normalize()

        val tmpFile = Files.createTempFile("design-bundle-", ".zip")
        try {
            Files.write(tmpFile, zipBytes)
            ZipFile(tmpFile.toFile()).use { zf ->
                for (entry in zf.entries()) {
                    val name = entry.name
                    // Skip metadata and directories
                    if (name.startsWith("__MACOSX/") || name.endsWith(".DS_Store") || name.endsWith("/")) {
                        continue
                    }
                    // Path traversal: resolve against synthetic root and check containment.
                    val resolved = rootPath.resolve(name).normalize()
                    if (!resolved.startsWith(rootPath) || name.startsWith("/") || name.startsWith("\\")) {
                        throw InvalidBundleException("path traversal rejected: $name")
                    }
                    val data = zf.getInputStream(entry).readBytes()
                    if (data.size > props.maxFileBytes) {
                        throw InvalidBundleException("file exceeds maxFileBytes: $name")
                    }
                    totalBytes += data.size
                    if (totalBytes > props.maxExtractedBytes) {
                        throw InvalidBundleException("extracted size exceeds maxExtractedBytes ${props.maxExtractedBytes}")
                    }
                    if (files.size >= props.maxFiles) {
                        throw InvalidBundleException("file count exceeds maxFiles ${props.maxFiles}")
                    }
                    files[name] = data
                }
            }
        } finally {
            Files.deleteIfExists(tmpFile)
        }

        // Find HTML files at bundle root (no slash in path)
        val rootHtmls = files.filterKeys { it.endsWith(".html") && '/' !in it }
            .mapValues { (_, bytes) -> bytes.toString(Charsets.UTF_8) }
        if (rootHtmls.isEmpty()) {
            throw InvalidBundleException("no html file found at bundle root")
        }
        val entryHtml = findEntryHtml(rootHtmls)
            ?: throw InvalidBundleException("no html file found")

        val entryHtmlContent = rootHtmls[entryHtml]!!
        val pages = parsePages(entryHtmlContent)

        val fileInventory = files.entries.map { (path, bytes) ->
            DesignBundleFile(
                path = path,
                sizeBytes = bytes.size.toLong(),
                mimeType = mimeTypeFor(path),
            )
        }.sortedBy { it.path }

        val bundle = DesignBundle(
            projectId = "",  // populated by storage at save time
            originalFilename = originalFilename,
            uploadedAt = Instant.now().toString(),
            sizeBytes = zipBytes.size.toLong(),
            entryHtml = entryHtml,
            pages = pages,
            files = fileInventory,
        )
        return ExtractionResult(bundle, files)
    }

    private fun mimeTypeFor(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".html") -> "text/html"
            lower.endsWith(".css")  -> "text/css"
            lower.endsWith(".js") || lower.endsWith(".jsx") -> "text/javascript"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".png")  -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif")  -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".svg")  -> "image/svg+xml"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".ttf")  -> "font/ttf"
            lower.endsWith(".md")   -> "text/markdown"
            lower.endsWith(".txt")  -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
