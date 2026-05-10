package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DesignGenerationInput
import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.domain.DesignInputCategory
import com.agentwork.productspecagent.domain.DesignWorkbench
import com.agentwork.productspecagent.domain.GeneratedDesign
import com.agentwork.productspecagent.storage.DesignWorkbenchStorage
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private const val LEGACY_DESIGN_COMPAT_MESSAGE =
    "Temporary compatibility for legacy design workbench flow until Simple Design Generator V1 controller migration removes callers."

class InvalidDesignWorkbenchException(message: String) : RuntimeException(message)

@Service
class DesignWorkbenchService(
    private val storage: DesignWorkbenchStorage,
    private val previewValidator: DesignPreviewValidator,
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

    fun generate(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        if (workbench.description.isNullOrBlank() && workbench.imageInput == null) {
            throw InvalidDesignWorkbenchException("Design generation requires a description or image.")
        }

        val result = designVariantAgent.generate(
            DesignGenerationInput(
                projectId = projectId,
                description = workbench.description,
                image = workbench.imageInput,
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
        return storage.readCurrentDesign(projectId)
    }

    fun complete(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        if (workbench.currentDesign == null) {
            throw InvalidDesignWorkbenchException("Generate a design before completing the DESIGN step.")
        }
        storage.writeActiveScreen(projectId, storage.readCurrentDesign(projectId))
        return workbench
    }

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun addTextInput(projectId: String, text: String): DesignWorkbench =
        saveInput(projectId, text, null, null, null)

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun addImageInput(projectId: String, originalName: String?, bytes: ByteArray, contentType: String?): DesignWorkbench =
        saveInput(projectId, null, originalName, bytes, contentType)

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun addSnippetInput(projectId: String, snippet: String, name: String?): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design snippet inputs are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun updateInput(
        projectId: String,
        inputId: String,
        userLabel: String?,
        category: DesignInputCategory?,
        summary: String?,
        suggestedUse: String?,
        confidence: Double?,
    ): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design input updates are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun analyzeInputs(projectId: String): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design analysis is not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun proposeScreens(projectId: String): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design screen proposals are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun addScreen(projectId: String, name: String, purpose: String?): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design screens are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun updateScreen(projectId: String, screenId: String, name: String?, purpose: String?): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design screens are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun removeScreen(projectId: String, screenId: String): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design screens are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun generateVariant(projectId: String, screenId: String, prompt: String?): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design variants are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun applySuggestion(projectId: String, screenId: String, suggestionId: String): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design suggestions are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun setActiveVariant(projectId: String, screenId: String, variantId: String): DesignWorkbench =
        throw InvalidDesignWorkbenchException("Legacy design variants are not supported by the V1 generator.")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun readVariant(projectId: String, htmlPath: String): ByteArray =
        throw InvalidDesignWorkbenchException("Legacy design variant previews are not supported by the V1 generator.")
}
