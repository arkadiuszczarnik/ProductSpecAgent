package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.AcceptanceCriterion
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.WizardService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.springframework.stereotype.Service
import java.util.UUID

@Service
open class AcceptanceCriteriaProposalAgent(
    private val contextBuilder: SpecContextBuilder,
    private val wizardService: WizardService,
    private val promptService: PromptService,
    private val koogRunner: KoogAgentRunner? = null,
) {
    companion object { const val AGENT_ID = "acceptance-criteria-proposal" }

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun propose(projectId: String, featureId: String): List<AcceptanceCriterion> {
        val feature = loadFeature(projectId, featureId)
            ?: throw IllegalArgumentException("Feature $featureId not found")
        val context = contextBuilder.buildProposalContext(projectId)
        val prompt = buildString {
            appendLine("Generate concrete, testable acceptance criteria for the following feature.")
            appendLine("Each criterion must describe a stakeholder-observable Done condition (not implementation steps).")
            appendLine()
            appendLine("=== PROJECT CONTEXT ===")
            appendLine(context)
            appendLine()
            appendLine("=== FEATURE ===")
            appendLine("Title: ${feature.title}")
            if (feature.description.isNotBlank()) appendLine("Description: ${feature.description}")
            if (feature.scopes.isNotEmpty()) appendLine("Scopes: ${feature.scopes.joinToString()}")
            appendLine()
            appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
            appendLine("""{"criteria":[{"title":"...","description":"..."}]}""")
            appendLine("Aim for 3–6 criteria. 'description' is optional (empty string allowed).")
        }
        val raw = runAgent(prompt)
        return parseResponse(raw)
    }

    // Overridden by tests; production path delegates to KoogAgentRunner
    // (same pattern as FeatureProposalAgent / DecisionAgent).
    protected open suspend fun runAgent(prompt: String): String =
        koogRunner?.run(AGENT_ID, promptService.get("acceptance-criteria-proposal-system"), prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")

    private fun loadFeature(projectId: String, featureId: String): WizardFeature? {
        val wizardData = runCatching { wizardService.getWizardData(projectId) }.getOrNull() ?: return null
        val featuresElement = wizardData.steps["FEATURES"]?.fields?.get("features") ?: return null
        return runCatching {
            json.decodeFromJsonElement<List<WizardFeature>>(featuresElement)
                .firstOrNull { it.id == featureId }
        }.getOrNull()
    }

    private fun parseResponse(raw: String): List<AcceptanceCriterion> {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val parsed = runCatching { json.decodeFromString<ProposalResponse>(jsonStr) }
            .getOrElse { throw ProposalParseException("Invalid JSON from LLM: ${it.message}", it) }
        return parsed.criteria.map { c ->
            AcceptanceCriterion(
                id = UUID.randomUUID().toString(),
                title = c.title,
                description = c.description ?: "",
            )
        }
    }

    @Serializable
    private data class ProposalResponse(val criteria: List<CriterionDef> = emptyList())

    @Serializable
    private data class CriterionDef(val title: String, val description: String? = null)
}
