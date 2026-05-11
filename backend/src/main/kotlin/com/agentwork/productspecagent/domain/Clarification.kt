package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ClarificationStatus { OPEN, ANSWERED }

@Serializable
data class Clarification(
    val id: String,
    val projectId: String,
    val stepType: FlowStepType,
    val question: String,
    val reason: String,
    val status: ClarificationStatus = ClarificationStatus.OPEN,
    val answer: String? = null,
    val createdAt: String,
    val answeredAt: String? = null,
    val appliedAt: String? = null,
    val appliedFields: List<String> = emptyList()
)

@Serializable
data class AnswerClarificationRequest(
    val answer: String
)
