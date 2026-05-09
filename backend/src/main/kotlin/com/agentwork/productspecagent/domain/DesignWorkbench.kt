package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class DesignWorkbench(
    val projectId: String,
    val inputs: List<DesignInput> = emptyList(),
    val screens: List<DesignScreen> = emptyList(),
    val suggestions: List<DesignSuggestion> = emptyList(),
    val updatedAt: String,
)

@Serializable
data class DesignInput(
    val id: String,
    val kind: DesignInputKind,
    val originalName: String? = null,
    val userLabel: String? = null,
    val classification: DesignInputClassification? = null,
    val contentRef: String,
    val createdAt: String,
)

@Serializable
enum class DesignInputKind {
    TEXT,
    IMAGE,
    HTML_CSS_SNIPPET,
}

@Serializable
data class DesignInputClassification(
    val category: DesignInputCategory,
    val summary: String,
    val suggestedUse: String,
    val confidence: Double,
)

@Serializable
enum class DesignInputCategory {
    REFERENCE_IMAGE,
    ASSET_IMAGE,
    HTML_CSS_REFERENCE,
    UNCLEAR,
}

@Serializable
data class DesignScreen(
    val id: String,
    val name: String,
    val purpose: String,
    val variants: List<DesignVariant> = emptyList(),
    val activeVariantId: String? = null,
)

@Serializable
data class DesignVariant(
    val id: String,
    val screenId: String,
    val version: Int,
    val title: String,
    val htmlPath: String,
    val status: DesignVariantStatus,
    val rationale: String,
    val createdAt: String,
)

@Serializable
enum class DesignVariantStatus {
    DRAFT,
    VALID,
    INVALID,
}

@Serializable
data class DesignSuggestion(
    val id: String,
    val screenId: String,
    val title: String,
    val description: String,
    val createdAt: String,
)
