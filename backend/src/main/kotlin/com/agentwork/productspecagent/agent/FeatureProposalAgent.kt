package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.GraphPosition
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.domain.WizardFeatureEdge
import com.agentwork.productspecagent.domain.WizardFeatureGraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.util.UUID

class ProposalParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Service
open class FeatureProposalAgent(
    private val contextBuilder: SpecContextBuilder,
) {
    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun proposeFeatures(projectId: String): WizardFeatureGraph {
        val context = contextBuilder.buildProposalContext(projectId)
        val category = extractCategory(context)
        val prompt = buildString {
            appendLine("Based on the project's idea/problem/audience/scope/mvp, propose a concrete feature list with dependencies.")
            appendLine()
            appendLine(context)
            appendLine()
            appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
            appendLine("""{"features":[{"title":"...","scopes":["FRONTEND"|"BACKEND"],"description":"...","scopeFields":{"...":"..."}}],"edges":[{"fromTitle":"A","toTitle":"B"}]}""")
            appendLine("For Library projects, omit scopes. For API/CLI, use only BACKEND.")
            appendLine("fromTitle is the feature that MUST be built first; toTitle depends on it.")
        }
        val raw = runAgent(prompt)
        return parseResponse(raw, category)
    }

    // Override in tests. Production subclass (if any) wires Koog here.
    protected open suspend fun runAgent(prompt: String): String =
        throw UnsupportedOperationException("runAgent must be overridden or provided via DI")

    private fun extractCategory(context: String): String? =
        Regex("Category:\\s*(.+)").find(context)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && it != "—" }

    private fun parseResponse(raw: String, category: String?): WizardFeatureGraph {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val parsed = runCatching { json.decodeFromString<ProposalResponse>(jsonStr) }
            .getOrElse { throw ProposalParseException("Invalid JSON from LLM: ${it.message}", it) }

        val defaultScopes = defaultScopesFor(category)
        val titleToId = mutableMapOf<String, String>()
        val features = parsed.features.map { f ->
            val id = UUID.randomUUID().toString()
            titleToId[f.title] = id
            WizardFeature(
                id = id,
                title = f.title,
                scopes = f.scopes
                    ?.mapNotNull { runCatching { FeatureScope.valueOf(it.uppercase()) }.getOrNull() }
                    ?.toSet()
                    ?.ifEmpty { defaultScopes }
                    ?: defaultScopes,
                description = f.description ?: "",
                scopeFields = f.scopeFields ?: emptyMap(),
                position = GraphPosition(),
            )
        }
        val edges = parsed.edges.mapNotNull { e ->
            val from = titleToId[e.fromTitle] ?: return@mapNotNull null
            val to = titleToId[e.toTitle] ?: return@mapNotNull null
            WizardFeatureEdge(id = UUID.randomUUID().toString(), from = from, to = to)
        }
        return WizardFeatureGraph(features = features, edges = edges)
    }

    private fun defaultScopesFor(category: String?): Set<FeatureScope> = when (category) {
        "SaaS", "Mobile App", "Desktop App" -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
        "API", "CLI Tool" -> setOf(FeatureScope.BACKEND)
        "Library" -> emptySet()
        else -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
    }

    @Serializable
    private data class ProposalResponse(
        val features: List<FeatureProposalDef> = emptyList(),
        val edges: List<EdgeProposalDef> = emptyList(),
    )

    @Serializable
    private data class FeatureProposalDef(
        val title: String,
        val scopes: List<String>? = null,
        val description: String? = null,
        val scopeFields: Map<String, String>? = null,
    )

    @Serializable
    private data class EdgeProposalDef(val fromTitle: String, val toTitle: String)
}
