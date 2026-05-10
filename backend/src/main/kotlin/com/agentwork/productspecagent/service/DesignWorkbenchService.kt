package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DesignImageAnalysisAgent
import com.agentwork.productspecagent.agent.DesignImageAnalysisInput
import com.agentwork.productspecagent.agent.DesignGenerationInput
import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.agent.InvalidDesignImageAnalysisException
import com.agentwork.productspecagent.domain.DesignImageInput
import com.agentwork.productspecagent.domain.DesignWorkbench
import com.agentwork.productspecagent.domain.GeneratedDesign
import com.agentwork.productspecagent.storage.DesignWorkbenchStorage
import com.agentwork.productspecagent.storage.StaleDesignImageAnalysisException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

class InvalidDesignWorkbenchException(message: String) : RuntimeException(message)

@Service
class DesignWorkbenchService(
    private val storage: DesignWorkbenchStorage,
    private val previewValidator: DesignPreviewValidator,
    private val imageAnalysisAgent: DesignImageAnalysisAgent,
    private val designVariantAgent: DesignVariantAgent,
) {
    private val maxImageInputBytes = 5 * 1024 * 1024

    fun get(projectId: String): DesignWorkbench = storage.load(projectId)

    fun saveInput(
        projectId: String,
        description: String?,
        originalName: String?,
        bytes: ByteArray?,
        contentType: String?,
    ): DesignWorkbench {
        val trimmed = description?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null && (bytes == null || bytes.isEmpty())) {
            throw InvalidDesignWorkbenchException("Design input requires a description or image.")
        }

        val normalizedContentType = if (bytes != null) {
            if (bytes.isEmpty()) {
                throw InvalidDesignWorkbenchException("Design image input must not be empty.")
            }
            if (bytes.size > maxImageInputBytes) {
                throw InvalidDesignWorkbenchException("Design image input must not exceed 5 MB.")
            }
            val normalized = contentType?.trim()?.lowercase().orEmpty()
            if (!normalized.startsWith("image/")) {
                throw InvalidDesignWorkbenchException("Design image input must use an image content type.")
            }
            normalized
        } else {
            null
        }

        val base = storage.saveInput(projectId, trimmed, null)
        if (bytes == null) return base
        return storage.saveImageInput(
            projectId = projectId,
            originalName = originalName?.trim()?.takeIf { it.isNotBlank() } ?: "image-upload",
            bytes = bytes,
            contentType = normalizedContentType ?: error("validated image content type is missing"),
        )
    }

    fun analyzeImage(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        val image = workbench.imageInput
            ?: throw InvalidDesignWorkbenchException("Design image input is required for analysis.")
        val bytes = try {
            storage.readImageInput(projectId)
        } catch (e: NoSuchElementException) {
            val message = e.message ?: "Design image input is missing."
            saveImageAnalysisErrorOrReturnCurrent(projectId, image, message)?.let { return it }
            throw InvalidDesignWorkbenchException(message)
        }

        val result = try {
            imageAnalysisAgent.analyze(
                DesignImageAnalysisInput(
                    projectId = projectId,
                    image = image,
                    imageBytes = bytes,
                ),
            )
        } catch (e: InvalidDesignImageAnalysisException) {
            val message = e.message ?: "Image analysis failed."
            saveImageAnalysisErrorOrReturnCurrent(projectId, image, message)?.let { return it }
            throw InvalidDesignWorkbenchException(message)
        } catch (e: RuntimeException) {
            val message = e.message ?: "Image analysis failed."
            saveImageAnalysisErrorOrReturnCurrent(projectId, image, message)?.let { return it }
            throw InvalidDesignWorkbenchException(message)
        }

        return try {
            storage.saveImageAnalysis(projectId, image, result.analysis)
        } catch (e: StaleDesignImageAnalysisException) {
            storage.load(projectId)
        }
    }

    private fun saveImageAnalysisErrorOrReturnCurrent(
        projectId: String,
        image: DesignImageInput,
        message: String,
    ): DesignWorkbench? =
        try {
            storage.saveImageAnalysisError(projectId, image, message)
            null
        } catch (e: StaleDesignImageAnalysisException) {
            storage.load(projectId)
        }

    fun generate(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        if (workbench.description.isNullOrBlank() && workbench.imageInput == null) {
            throw InvalidDesignWorkbenchException("Design generation requires a description or image.")
        }
        val readyWorkbench = if (workbench.imageInput != null && workbench.imageAnalysis == null) {
            analyzeImage(projectId)
        } else {
            workbench
        }

        val result = designVariantAgent.generate(
            DesignGenerationInput(
                projectId = projectId,
                description = readyWorkbench.description,
                image = readyWorkbench.imageInput,
                imageAnalysis = readyWorkbench.imageAnalysis,
            ),
        )
        previewValidator.validate(result.html)

        return storage.saveGeneratedDesign(
            projectId = projectId,
            analysis = result.analysis,
            generated = GeneratedDesign(
                id = UUID.randomUUID().toString(),
                title = result.title.trim().ifBlank { "Generated Design" },
                htmlPath = storage.currentDesignKey(projectId),
                rationale = result.rationale,
                createdAt = Instant.now().toString(),
            ),
            html = result.html.toByteArray(Charsets.UTF_8),
        )
    }

    fun readPreview(projectId: String): ByteArray {
        if (storage.load(projectId).currentDesign == null) {
            throw InvalidDesignWorkbenchException("No generated design exists.")
        }
        return try {
            storage.readCurrentDesign(projectId)
        } catch (e: NoSuchElementException) {
            throw InvalidDesignWorkbenchException(e.message ?: "Generated design HTML is missing.")
        }
    }

    fun complete(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        if (workbench.currentDesign == null) {
            throw InvalidDesignWorkbenchException("Generate a design before completing the DESIGN step.")
        }
        val html = try {
            storage.readCurrentDesign(projectId)
        } catch (e: NoSuchElementException) {
            throw InvalidDesignWorkbenchException(e.message ?: "Generated design HTML is missing.")
        }
        storage.writeActiveScreen(projectId, html)
        return workbench
    }
}
