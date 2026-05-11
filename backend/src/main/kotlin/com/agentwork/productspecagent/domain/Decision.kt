package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class DecisionStatus { PENDING, RESOLVED }

@Serializable
data class DecisionOption(
    val id: String,
    val label: String,
    val pros: List<String>,
    val cons: List<String>,
    val recommended: Boolean
)

@Serializable
data class Decision(
    val id: String,
    val projectId: String,
    val stepType: FlowStepType,
    val title: String,
    val options: List<DecisionOption>,
    val recommendation: String,
    val status: DecisionStatus = DecisionStatus.PENDING,
    val chosenOptionId: String? = null,
    val rationale: String? = null,
    val createdAt: String,
    val resolvedAt: String? = null,
    val appliedAt: String? = null,
    val appliedFields: List<String> = emptyList()
)

@Serializable
data class CreateDecisionRequest(
    val title: String,
    val stepType: FlowStepType
)

@Serializable
data class ResolveDecisionRequest(
    val chosenOptionId: String,
    val rationale: String
)
