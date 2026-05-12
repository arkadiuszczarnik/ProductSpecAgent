package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class FeatureCompletionTestEvidence(
    val name: String,
    val status: String,
)

@Serializable
data class FeatureCompletionSnapshot(
    val projectId: String,
    val featureId: String,
    val derivedStatus: LivingSyncFeatureStatus,
    val summary: String,
    val implementedItems: List<String> = emptyList(),
    val deviations: List<String> = emptyList(),
    val openPoints: List<String> = emptyList(),
    val technicalDebt: List<String> = emptyList(),
    val tests: List<FeatureCompletionTestEvidence> = emptyList(),
    val warnings: List<String> = emptyList(),
    val sourceEventId: String,
    val sourceFileName: String,
    val updatedAt: String,
)
