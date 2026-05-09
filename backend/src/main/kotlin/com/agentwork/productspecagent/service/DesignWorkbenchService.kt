package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.agent.ReferenceAnalysisAgent
import com.agentwork.productspecagent.agent.ScreenProposalAgent
import com.agentwork.productspecagent.domain.DesignInputCategory
import com.agentwork.productspecagent.domain.DesignInputClassification
import com.agentwork.productspecagent.domain.DesignInputKind
import com.agentwork.productspecagent.domain.DesignScreen
import com.agentwork.productspecagent.domain.DesignVariant
import com.agentwork.productspecagent.domain.DesignVariantStatus
import com.agentwork.productspecagent.domain.DesignWorkbench
import com.agentwork.productspecagent.storage.DesignWorkbenchStorage
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

class InvalidDesignWorkbenchException(message: String) : RuntimeException(message)

@Service
class DesignWorkbenchService(
    private val storage: DesignWorkbenchStorage,
    private val previewValidator: DesignPreviewValidator,
    private val referenceAnalysisAgent: ReferenceAnalysisAgent,
    private val screenProposalAgent: ScreenProposalAgent,
    private val designVariantAgent: DesignVariantAgent,
) {
    private val maxImageInputBytes = 5 * 1024 * 1024

    fun get(projectId: String): DesignWorkbench = storage.load(projectId)

    fun addTextInput(projectId: String, text: String): DesignWorkbench {
        if (text.isBlank()) {
            throw InvalidDesignWorkbenchException("Design input text must not be blank.")
        }
        storage.addTextInput(projectId, text)
        return storage.load(projectId)
    }

    fun addImageInput(projectId: String, originalName: String?, bytes: ByteArray, contentType: String?): DesignWorkbench {
        if (bytes.isEmpty()) {
            throw InvalidDesignWorkbenchException("Design image input must not be empty.")
        }
        if (bytes.size > maxImageInputBytes) {
            throw InvalidDesignWorkbenchException("Design image input must not exceed 5 MB.")
        }
        val normalizedContentType = contentType?.trim()?.lowercase().orEmpty()
        if (!normalizedContentType.startsWith("image/")) {
            throw InvalidDesignWorkbenchException("Design image input must use an image content type.")
        }
        storage.addBinaryInput(
            projectId = projectId,
            kind = DesignInputKind.IMAGE,
            originalName = originalName?.trim()?.takeIf { it.isNotBlank() } ?: "image-upload",
            bytes = bytes,
            contentType = normalizedContentType,
        )
        return storage.load(projectId)
    }

    fun addSnippetInput(projectId: String, snippet: String, name: String?): DesignWorkbench {
        if (snippet.isBlank()) {
            throw InvalidDesignWorkbenchException("Design snippet input must not be blank.")
        }
        storage.addBinaryInput(
            projectId = projectId,
            kind = DesignInputKind.HTML_CSS_SNIPPET,
            originalName = name?.trim()?.takeIf { it.isNotBlank() } ?: "snippet.html",
            bytes = snippet.toByteArray(),
            contentType = "text/html; charset=utf-8",
        )
        return storage.load(projectId)
    }

    fun updateInput(
        projectId: String,
        inputId: String,
        userLabel: String?,
        category: DesignInputCategory?,
        summary: String?,
        suggestedUse: String?,
        confidence: Double?,
    ): DesignWorkbench {
        if (confidence != null && confidence !in 0.0..1.0) {
            throw InvalidDesignWorkbenchException("Design input confidence must be between 0 and 1.")
        }
        val workbench = storage.load(projectId)
        val input = workbench.inputs.firstOrNull { it.id == inputId }
            ?: throw InvalidDesignWorkbenchException("Design input not found: $inputId")
        val existingClassification = input.classification
        val classification = if (
            category != null ||
            summary != null ||
            suggestedUse != null ||
            confidence != null ||
            existingClassification != null
        ) {
            DesignInputClassification(
                category = category ?: existingClassification?.category ?: DesignInputCategory.UNCLEAR,
                summary = summary?.trim()?.takeIf { it.isNotBlank() }
                    ?: existingClassification?.summary
                    ?: "User supplied classification",
                suggestedUse = suggestedUse?.trim()?.takeIf { it.isNotBlank() }
                    ?: existingClassification?.suggestedUse
                    ?: "Use as a manually curated design reference.",
                confidence = confidence ?: existingClassification?.confidence ?: 1.0,
            )
        } else {
            null
        }
        val updatedLabel = when {
            userLabel == null -> input.userLabel
            userLabel.isBlank() -> null
            else -> userLabel.trim()
        }

        return storage.save(
            workbench.copy(
                inputs = workbench.inputs.map {
                    if (it.id == inputId) it.copy(userLabel = updatedLabel, classification = classification) else it
                },
            ),
        )
    }

    fun analyzeInputs(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        val analyses = referenceAnalysisAgent.analyze(projectId)
        var updated = workbench
        workbench.inputs.forEachIndexed { index, input ->
            val analysis = analyses.getOrNull(index) ?: analyses.lastOrNull() ?: return@forEachIndexed
            updated = storage.updateInputClassification(projectId, input.id, analysis, input.userLabel)
        }
        return updated
    }

    fun proposeScreens(projectId: String): DesignWorkbench {
        val screens = screenProposalAgent.propose(projectId).map {
            DesignScreen(id = it.id, name = it.name, purpose = it.purpose)
        }
        return storage.saveScreens(projectId, screens)
    }

    fun addScreen(projectId: String, name: String, purpose: String?): DesignWorkbench {
        if (name.isBlank()) {
            throw InvalidDesignWorkbenchException("Design screen name must not be blank.")
        }
        val workbench = storage.load(projectId)
        val baseId = name.toSlug().ifBlank { "screen" }
        val existingIds = workbench.screens.map { it.id }.toSet()
        val screen = DesignScreen(
            id = uniqueId(baseId, existingIds),
            name = name.trim(),
            purpose = purpose?.trim()?.takeIf { it.isNotBlank() } ?: "Manual screen",
        )
        return storage.saveScreens(projectId, workbench.screens + screen)
    }

    fun updateScreen(projectId: String, screenId: String, name: String?, purpose: String?): DesignWorkbench {
        val workbench = storage.load(projectId)
        val screen = workbench.screens.firstOrNull { it.id == screenId }
            ?: throw InvalidDesignWorkbenchException("Screen not found: $screenId")
        val updatedName = name?.trim()?.takeIf { it.isNotBlank() } ?: screen.name
        return storage.saveScreens(
            projectId,
            workbench.screens.map {
                if (it.id == screenId) {
                    it.copy(
                        name = updatedName,
                        purpose = purpose?.trim()?.takeIf { text -> text.isNotBlank() } ?: it.purpose,
                    )
                } else {
                    it
                }
            },
        )
    }

    fun removeScreen(projectId: String, screenId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        if (workbench.screens.none { it.id == screenId }) {
            throw InvalidDesignWorkbenchException("Screen not found: $screenId")
        }
        return storage.saveScreens(projectId, workbench.screens.filterNot { it.id == screenId })
    }

    fun generateVariant(projectId: String, screenId: String, prompt: String?): DesignWorkbench {
        val workbench = storage.load(projectId)
        val screen = workbench.screens.firstOrNull { it.id == screenId }
            ?: throw InvalidDesignWorkbenchException("Screen not found: $screenId")
        val generated = designVariantAgent.generate(projectId, screenId, prompt)
        previewValidator.validate(generated.html)
        val variantId = UUID.randomUUID().toString()
        val variant = DesignVariant(
            id = variantId,
            screenId = screenId,
            version = screen.variants.size + 1,
            title = generated.title,
            htmlPath = storage.variantKey(projectId, screenId, variantId),
            status = DesignVariantStatus.VALID,
            rationale = generated.rationale,
            createdAt = Instant.now().toString(),
        )
        return storage.saveVariant(projectId, screenId, variant, generated.html.toByteArray())
    }

    fun applySuggestion(projectId: String, screenId: String, suggestionId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        val suggestion = workbench.suggestions.firstOrNull { it.id == suggestionId && it.screenId == screenId }
            ?: throw InvalidDesignWorkbenchException("Design suggestion not found: $suggestionId")
        return generateVariant(projectId, screenId, "${suggestion.title}\n\n${suggestion.description}")
    }

    fun setActiveVariant(projectId: String, screenId: String, variantId: String): DesignWorkbench =
        try {
            storage.setActiveVariant(projectId, screenId, variantId)
        } catch (e: NoSuchElementException) {
            throw InvalidDesignWorkbenchException(e.message ?: "Design screen or variant not found.")
        } catch (e: IllegalArgumentException) {
            throw InvalidDesignWorkbenchException(e.message ?: "Design screen or variant not found.")
        }

    fun readVariant(projectId: String, htmlPath: String): ByteArray {
        val ownsVariant = storage.load(projectId).screens.any { screen ->
            screen.variants.any { it.htmlPath == htmlPath }
        }
        if (!ownsVariant) {
            throw InvalidDesignWorkbenchException("Design variant not found in project: $htmlPath")
        }
        return try {
            storage.readByKey(htmlPath)
        } catch (e: NoSuchElementException) {
            throw InvalidDesignWorkbenchException(e.message ?: "Design variant content not found.")
        }
    }

    fun complete(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        val activeScreens = workbench.screens.filter { screen ->
            screen.activeVariantId != null &&
                screen.variants.any { it.id == screen.activeVariantId && it.status == DesignVariantStatus.VALID }
        }
        if (activeScreens.isEmpty()) {
            throw InvalidDesignWorkbenchException("At least one active valid design screen is required.")
        }
        activeScreens.forEach { screen ->
            val variant = screen.variants.first { it.id == screen.activeVariantId }
            val html = try {
                storage.readByKey(variant.htmlPath)
            } catch (e: NoSuchElementException) {
                throw InvalidDesignWorkbenchException(e.message ?: "Design variant content not found.")
            }
            storage.writeActiveScreen(projectId, screen.name.toSlug(), html)
        }
        return workbench
    }

    private fun String.toSlug(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun uniqueId(baseId: String, existingIds: Set<String>): String {
        var candidate = baseId
        var suffix = 2
        while (candidate in existingIds) {
            candidate = "$baseId-$suffix"
            suffix += 1
        }
        return candidate
    }
}
