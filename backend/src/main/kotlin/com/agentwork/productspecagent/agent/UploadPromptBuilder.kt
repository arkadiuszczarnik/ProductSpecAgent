package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.config.FeatureProposalUploadsProperties
import com.agentwork.productspecagent.storage.UploadStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

@Component
open class UploadPromptBuilder(
    private val uploadStorage: UploadStorage,
    private val props: FeatureProposalUploadsProperties,
) {
    private val log = LoggerFactory.getLogger(UploadPromptBuilder::class.java)

    open fun renderUploadsSection(projectId: String): String {
        return try {
            buildSection(projectId)
        } catch (e: Exception) {
            log.warn("Failed to render uploads section for project=$projectId: ${e.message}")
            ""
        }
    }

    private fun buildSection(projectId: String): String {
        val all = uploadStorage.listAsDocuments(projectId)
        all.filter { it.mimeType !in TEXT_MIME_TYPES }
            .forEach { log.debug("Skipping non-text upload: {} ({})", it.title, it.mimeType) }
        val docs = all
            .filter { it.mimeType in TEXT_MIME_TYPES }
            .sortedBy { it.createdAt }
        if (docs.isEmpty()) return ""

        val sb = StringBuilder()
        var bytesUsed = 0L
        var renderedCount = 0
        for (doc in docs) {
            val bytes = try {
                uploadStorage.readById(projectId, doc.id)
            } catch (e: Exception) {
                log.warn("Failed to read upload docId=${doc.id} for project=$projectId: ${e.message}")
                continue
            }
            val truncated = truncatePerFile(bytes)
            val sectionBytes = truncated.text.toByteArray(StandardCharsets.UTF_8).size.toLong()
            if (bytesUsed + sectionBytes > props.maxBytesTotal) break
            appendDocument(sb, doc.title, doc.mimeType, escapeMarkers(truncated.text))
            bytesUsed += sectionBytes
            renderedCount++
        }

        val skipped = docs.size - renderedCount
        if (skipped > 0) {
            sb.append("[")
            sb.append(skipped)
            sb.append(" additional documents skipped due to total budget]\n")
        }
        return sb.toString().trimEnd()
    }

    private fun appendDocument(sb: StringBuilder, title: String, mime: String, body: String) {
        sb.append("--- BEGIN UPLOADED DOCUMENT: ").append(title).append(" (").append(mime).append(") ---\n")
        sb.append(body)
        if (!body.endsWith("\n")) sb.append('\n')
        sb.append("--- END UPLOADED DOCUMENT ---\n\n")
    }

    private fun escapeMarkers(body: String): String =
        body
            .replace("--- BEGIN UPLOADED DOCUMENT", "-​-- BEGIN UPLOADED DOCUMENT")
            .replace("--- END UPLOADED DOCUMENT", "-​-- END UPLOADED DOCUMENT")

    private fun decodeUtf8(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    private fun truncatePerFile(bytes: ByteArray): TruncationResult {
        val limit = props.maxBytesPerFile.toInt()
        return if (bytes.size <= limit) {
            TruncationResult(decodeUtf8(bytes), originalBytes = bytes.size, truncated = false)
        } else {
            val slice = bytes.copyOfRange(0, limit)
            val truncatedText = decodeUtf8(slice) +
                "\n[…truncated, original was ${bytes.size / 1024} KB]"
            TruncationResult(truncatedText, originalBytes = bytes.size, truncated = true)
        }
    }

    private data class TruncationResult(val text: String, val originalBytes: Int, val truncated: Boolean)

    companion object {
        private val TEXT_MIME_TYPES = setOf("text/markdown", "text/plain")
    }
}
