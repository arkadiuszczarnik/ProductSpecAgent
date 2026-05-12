package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureCompletionTestEvidence
import com.agentwork.productspecagent.domain.LivingSyncFeatureStatus
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.WizardService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.springframework.stereotype.Service

@Service
open class FeatureDoneImportAgent(
    private val contextBuilder: SpecContextBuilder,
    private val wizardService: WizardService,
    private val promptService: PromptService,
    private val koogRunner: KoogAgentRunner? = null,
) {
    companion object {
        const val AGENT_ID = "feature-done-import"
    }

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun importDoneReport(
        projectId: String,
        featureId: String,
        fileName: String,
        markdown: String,
    ): FeatureDoneImportResult {
        val feature = loadFeature(projectId, featureId)
            ?: throw IllegalArgumentException("Feature $featureId not found")
        val context = contextBuilder.buildProposalContext(projectId)
        val prompt = buildPrompt(context, feature, fileName, markdown)
        val raw = runAgent(prompt)
        val parsed = parseResponse(raw)
        val parsedFeature = loadFeature(projectId, parsed.featureId)
            ?: throw IllegalArgumentException("Feature ${parsed.featureId} not found")
        require(parsedFeature.id == feature.id) {
            "Imported featureId ${parsed.featureId} does not match requested featureId ${feature.id}"
        }
        return parsed
    }

    protected open suspend fun runAgent(prompt: String): String =
        koogRunner?.run(AGENT_ID, promptService.get("feature-done-import-system"), prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")

    private fun buildPrompt(
        context: String,
        feature: WizardFeature,
        fileName: String,
        markdown: String,
    ): String = buildString {
        appendLine("Analyze the following markdown done report for a specific feature and extract a strict JSON summary.")
        appendLine("Use the feature metadata to confirm whether the report header matches the expected feature.")
        appendLine()
        appendLine("=== PROJECT CONTEXT ===")
        appendLine(context)
        appendLine()
        appendLine("=== TARGET FEATURE ===")
        appendLine("Feature ID: ${feature.id}")
        appendLine("Title: ${feature.title}")
        if (feature.description.isNotBlank()) appendLine("Description: ${feature.description}")
        if (feature.scopes.isNotEmpty()) appendLine("Scopes: ${feature.scopes.joinToString()}")
        if (feature.scopeFields.isNotEmpty()) appendLine("Scope fields: ${feature.scopeFields}")
        appendLine()
        appendLine("=== SOURCE FILE ===")
        appendLine("File name: $fileName")
        appendLine()
        appendLine("=== MARKDOWN REPORT ===")
        appendLine(markdown)
        appendLine()
        appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
        appendLine(
            """{"featureId":"${feature.id}","headerCheck":{"matchesExpectedFeature":true,"reportedFeatureLabel":"...","warnings":[]},"derivedStatus":"DONE","summary":"...","implementedItems":["..."],"deviations":[],"tests":[{"name":"...","status":"PRESENT"}],"openPoints":[],"technicalDebt":[],"warnings":[]}"""
        )
        appendLine("Use one of these derivedStatus values only: PLANNED, IN_PROGRESS, BLOCKED, DONE.")
    }

    private fun loadFeature(projectId: String, featureId: String): WizardFeature? {
        val wizardData = runCatching { wizardService.getWizardData(projectId) }.getOrNull() ?: return null
        val featuresElement = wizardData.steps["FEATURES"]?.fields?.get("features") ?: return null
        return runCatching {
            json.decodeFromJsonElement<List<WizardFeature>>(featuresElement)
                .firstOrNull { it.id == featureId }
        }.getOrNull()
    }

    private fun parseResponse(raw: String): FeatureDoneImportResult {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        return runCatching { json.decodeFromString<FeatureDoneImportResult>(jsonStr) }
            .getOrElse { throw ProposalParseException("Invalid JSON from LLM: ${it.message}", it) }
    }
}

@Serializable
data class FeatureDoneImportResult(
    val featureId: String,
    val headerCheck: FeatureDoneImportHeaderCheck,
    val derivedStatus: LivingSyncFeatureStatus,
    val summary: String,
    val implementedItems: List<String> = emptyList(),
    val deviations: List<String> = emptyList(),
    val tests: List<FeatureCompletionTestEvidence> = emptyList(),
    val openPoints: List<String> = emptyList(),
    val technicalDebt: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class FeatureDoneImportHeaderCheck(
    val matchesExpectedFeature: Boolean,
    val reportedFeatureLabel: String,
    val warnings: List<String> = emptyList(),
)
