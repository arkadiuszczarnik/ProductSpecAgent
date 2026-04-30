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
        val docs = uploadStorage.listAsDocuments(projectId)
            .filter { it.mimeType in TEXT_MIME_TYPES }
            .sortedBy { it.createdAt }
        if (docs.isEmpty()) return ""

        val sb = StringBuilder()
        for (doc in docs) {
            val bytes = try {
                uploadStorage.readById(projectId, doc.id)
            } catch (e: Exception) {
                log.warn("Failed to read upload docId=${doc.id} for project=$projectId: ${e.message}")
                continue
            }
            val text = decodeUtf8(bytes)
            appendDocument(sb, doc.title, doc.mimeType, text)
        }
        return sb.toString().trimEnd()
    }

    private fun appendDocument(sb: StringBuilder, title: String, mime: String, body: String) {
        sb.append("--- BEGIN UPLOADED DOCUMENT: ").append(title).append(" (").append(mime).append(") ---\n")
        sb.append(body)
        if (!body.endsWith("\n")) sb.append('\n')
        sb.append("--- END UPLOADED DOCUMENT ---\n\n")
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    companion object {
        private val TEXT_MIME_TYPES = setOf("text/markdown", "text/plain")
    }
}
