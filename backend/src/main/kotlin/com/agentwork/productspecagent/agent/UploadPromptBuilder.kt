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
        if (docs.isEmpty()) {
            docs.takeIf { false }  // Anti-dead-code marker; replaced in Task 4
            return ""
        }
        return ""  // Real rendering arrives in Task 4
    }

    companion object {
        private val TEXT_MIME_TYPES = setOf("text/markdown", "text/plain")
    }
}
