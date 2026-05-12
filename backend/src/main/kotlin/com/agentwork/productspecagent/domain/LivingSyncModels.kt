package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class LivingSyncEventType {
    FEATURE_PROGRESS,
    FEATURE_DONE_IMPORT,
    TEST_RUN,
    TOKEN_USAGE,
    CODE_CHANGES,
    SYNC_NOTE
}

@Serializable
// Shared by append-only sync events and imported completion snapshots.
enum class LivingSyncFeatureStatus {
    PLANNED,
    IN_PROGRESS,
    BLOCKED,
    DONE
}

@Serializable
data class LivingSyncEvent(
    val id: String,
    val projectId: String,
    val type: LivingSyncEventType,
    val featureId: String? = null,
    val taskId: String? = null,
    val agentName: String? = null,
    val model: String? = null,
    val status: String? = null,
    val summary: String,
    val evidence: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val commits: List<String> = emptyList(),
    val testCommand: String? = null,
    val testsPassed: Int? = null,
    val testsFailed: Int? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val createdAt: String,
)

@Serializable
data class LivingSyncFeatureProgressRequest(
    val featureId: String,
    val status: LivingSyncFeatureStatus,
    val summary: String,
    val evidence: List<String> = emptyList(),
    val taskId: String? = null,
    val agentName: String? = null,
)

@Serializable
data class LivingSyncFeatureDoneImportRequest(
    val featureId: String,
    val fileName: String,
    val markdown: String,
    val agentName: String? = null,
)

@Serializable
data class LivingSyncTestRunRequest(
    val command: String,
    val status: String,
    val summary: String,
    val passed: Int = 0,
    val failed: Int = 0,
    val featureId: String? = null,
    val taskId: String? = null,
    val agentName: String? = null,
)

@Serializable
data class LivingSyncTokenUsageRequest(
    val agentName: String,
    val model: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = inputTokens + outputTokens,
    val summary: String = "Token usage reported.",
    val taskId: String? = null,
    val featureId: String? = null,
)

@Serializable
data class LivingSyncCodeChangesRequest(
    val summary: String,
    val files: List<String>? = null,
    val commits: List<String>? = null,
    val featureId: String? = null,
    val taskId: String? = null,
    val agentName: String? = null,
)

@Serializable
data class LivingSyncNoteRequest(
    val severity: CheckSeverity,
    val message: String,
    val suggestedAction: String? = null,
    val featureId: String? = null,
    val taskId: String? = null,
    val agentName: String? = null,
)

@Serializable
data class LivingSyncFeatureSummary(
    val featureId: String,
    val status: LivingSyncFeatureStatus,
    val summary: String,
    val updatedAt: String,
)

@Serializable
data class LivingSyncTestSummary(
    val totalRuns: Int,
    val passed: Int,
    val failed: Int,
    val lastStatus: String? = null,
    val lastCommand: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class LivingSyncTokenSummary(
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
)

@Serializable
data class LivingSyncSummary(
    val projectId: String,
    val features: List<LivingSyncFeatureSummary>,
    val featureCompletions: List<FeatureCompletionSnapshot>,
    val tests: LivingSyncTestSummary,
    val tokens: LivingSyncTokenSummary,
    val changedFiles: List<String>,
    val commits: List<String>,
    val notes: List<LivingSyncEvent>,
    val recentEvents: List<LivingSyncEvent>,
    val updatedAt: String? = null,
)
