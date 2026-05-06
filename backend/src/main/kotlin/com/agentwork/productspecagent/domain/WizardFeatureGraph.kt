package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class FeatureScope { FRONTEND, BACKEND }

@Serializable
data class GraphPosition(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
data class AcceptanceCriterion(
    val id: String,
    val text: String,
)

@Serializable
data class WizardFeature(
    val id: String,
    val title: String,
    val scopes: Set<FeatureScope> = emptySet(),
    val description: String = "",
    val scopeFields: Map<String, String> = emptyMap(),
    val acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),
    val position: GraphPosition = GraphPosition(),
)

@Serializable
data class WizardFeatureEdge(
    val id: String,
    val from: String,
    val to: String,
)

@Serializable
data class WizardFeatureGraph(
    val features: List<WizardFeature> = emptyList(),
    val edges: List<WizardFeatureEdge> = emptyList(),
)
