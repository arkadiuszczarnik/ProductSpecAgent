package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.GraphPosition
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.domain.WizardFeatureEdge
import com.agentwork.productspecagent.domain.WizardFeatureGraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Thrown when the LLM response cannot be decoded into the expected proposal JSON shape.
 * Surfaces up to the controller, which maps it to a 502 Bad Gateway.
 */
class ProposalParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Produces an initial [WizardFeatureGraph] proposal from the project's idea/problem/audience/scope/mvp
 * spec files. The LLM references features by their human-readable `title`; this agent resolves those
 * titles to stable UUIDs and silently drops edges whose endpoints can't be resolved (we don't want a
 * single typo to fail the whole call).
 *
 * Default scopes when the LLM omits them are derived from the project's category:
 *  - SaaS / Mobile App / Desktop App → Frontend + Backend
 *  - API / CLI Tool                  → Backend only
 *  - Library                         → no scopes (Core component)
 *  - unknown / missing               → Frontend + Backend (safe fallback)
 */
@Service
open class FeatureProposalAgent(
    private val contextBuilder: SpecContextBuilder,
    private val koogRunner: KoogAgentRunner? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun proposeFeatures(projectId: String): WizardFeatureGraph {
        val context = contextBuilder.buildProposalContext(projectId)
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
        return parseResponse(raw, category = extractCategory(context))
    }

    protected open suspend fun runAgent(prompt: String): String {
        return koogRunner?.run(
            "You are a product planner. Produce a DAG of features with dependencies.",
            prompt,
        ) ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
    }

    private fun extractCategory(context: String): String? =
        Regex("Category:\\s*(.+)").find(context)?.groupValues?.get(1)?.trim()
            ?.takeIf { it != "—" && it.isNotBlank() }

    private fun parseResponse(raw: String, category: String?): WizardFeatureGraph {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val parsed = runCatching { json.decodeFromString<ProposalResponse>(jsonStr) }
            .getOrElse { throw ProposalParseException("Invalid JSON from LLM: ${it.message}", it) }

        val defaultScopes = defaultScopesFor(category)
        val titleToId = mutableMapOf<String, String>()
        val features = parsed.features.map { f ->
            val id = UUID.randomUUID().toString()
            titleToId[f.title] = id
            val scopeSet: Set<FeatureScope> = f.scopes
                ?.mapNotNull { runCatching { FeatureScope.valueOf(it.uppercase()) }.getOrNull() }
                ?.toSet()
                ?: defaultScopes
            WizardFeature(
                id = id,
                title = f.title,
                scopes = scopeSet,
                description = f.description ?: "",
                scopeFields = sanitizeScopeFields(f.scopeFields),
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

    /**
     * Coerces a heterogeneously typed scopeFields map (the LLM may emit numbers, booleans, nested
     * objects) into a flat `Map<String, String>`. Primitives unwrap to their content; anything else
     * falls back to its JSON serialization, so we never lose data silently.
     */
    private fun sanitizeScopeFields(fields: Map<String, JsonElement>?): Map<String, String> {
        if (fields.isNullOrEmpty()) return emptyMap()
        return fields.mapValues { (_, v) ->
            (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
        }
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
        val scopeFields: Map<String, JsonElement>? = null,
    )

    @Serializable
    private data class EdgeProposalDef(val fromTitle: String, val toTitle: String)
}
