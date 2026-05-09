package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.agent.ReferenceAnalysisAgent
import com.agentwork.productspecagent.agent.ScreenProposalAgent
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
    fun get(projectId: String): DesignWorkbench = storage.load(projectId)

    fun addTextInput(projectId: String, text: String): DesignWorkbench {
        if (text.isBlank()) {
            throw InvalidDesignWorkbenchException("Design input text must not be blank.")
        }
        storage.addTextInput(projectId, text)
        return storage.load(projectId)
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

    fun generateVariant(projectId: String, screenId: String, prompt: String?): DesignWorkbench {
        val generated = designVariantAgent.generate(projectId, screenId, prompt)
        previewValidator.validate(generated.html)
        val workbench = storage.load(projectId)
        val screen = workbench.screens.firstOrNull { it.id == screenId }
            ?: throw InvalidDesignWorkbenchException("Screen not found: $screenId")
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

    fun setActiveVariant(projectId: String, screenId: String, variantId: String): DesignWorkbench =
        storage.setActiveVariant(projectId, screenId, variantId)

    fun readVariant(projectId: String, htmlPath: String): ByteArray = storage.readByKey(htmlPath)

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
            storage.writeActiveScreen(projectId, screen.name.toSlug(), storage.readByKey(variant.htmlPath))
        }
        return workbench
    }

    private fun String.toSlug(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
