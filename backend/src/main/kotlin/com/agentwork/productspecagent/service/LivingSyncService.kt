package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.LivingSyncCodeChangesRequest
import com.agentwork.productspecagent.domain.LivingSyncEvent
import com.agentwork.productspecagent.domain.LivingSyncEventType
import com.agentwork.productspecagent.domain.LivingSyncFeatureProgressRequest
import com.agentwork.productspecagent.domain.LivingSyncFeatureStatus
import com.agentwork.productspecagent.domain.LivingSyncFeatureSummary
import com.agentwork.productspecagent.domain.LivingSyncNoteRequest
import com.agentwork.productspecagent.domain.LivingSyncSummary
import com.agentwork.productspecagent.domain.LivingSyncTestRunRequest
import com.agentwork.productspecagent.domain.LivingSyncTestSummary
import com.agentwork.productspecagent.domain.LivingSyncTokenSummary
import com.agentwork.productspecagent.domain.LivingSyncTokenUsageRequest
import com.agentwork.productspecagent.storage.LivingSyncStorage
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class LivingSyncService(
    private val projectService: ProjectService,
    private val storage: LivingSyncStorage,
) {

    fun reportFeatureProgress(projectId: String, request: LivingSyncFeatureProgressRequest): LivingSyncEvent =
        save(
            projectId,
            LivingSyncEvent(
                id = newId(),
                projectId = projectId,
                type = LivingSyncEventType.FEATURE_PROGRESS,
                featureId = request.featureId,
                taskId = request.taskId,
                agentName = request.agentName,
                status = request.status.name,
                summary = request.summary,
                evidence = request.evidence,
                createdAt = now(),
            ),
        )

    fun reportTestRun(projectId: String, request: LivingSyncTestRunRequest): LivingSyncEvent =
        save(
            projectId,
            LivingSyncEvent(
                id = newId(),
                projectId = projectId,
                type = LivingSyncEventType.TEST_RUN,
                featureId = request.featureId,
                taskId = request.taskId,
                agentName = request.agentName,
                status = request.status,
                summary = request.summary,
                testCommand = request.command,
                testsPassed = request.passed,
                testsFailed = request.failed,
                createdAt = now(),
            ),
        )

    fun reportTokenUsage(projectId: String, request: LivingSyncTokenUsageRequest): LivingSyncEvent =
        save(
            projectId,
            LivingSyncEvent(
                id = newId(),
                projectId = projectId,
                type = LivingSyncEventType.TOKEN_USAGE,
                featureId = request.featureId,
                taskId = request.taskId,
                agentName = request.agentName,
                model = request.model,
                summary = request.summary,
                inputTokens = request.inputTokens,
                outputTokens = request.outputTokens,
                totalTokens = request.totalTokens,
                createdAt = now(),
            ),
        )

    fun reportCodeChanges(projectId: String, request: LivingSyncCodeChangesRequest): LivingSyncEvent =
        save(
            projectId,
            LivingSyncEvent(
                id = newId(),
                projectId = projectId,
                type = LivingSyncEventType.CODE_CHANGES,
                featureId = request.featureId,
                taskId = request.taskId,
                agentName = request.agentName,
                summary = request.summary,
                files = request.files.orEmpty(),
                commits = request.commits.orEmpty(),
                createdAt = now(),
            ),
        )

    fun reportSyncNote(projectId: String, request: LivingSyncNoteRequest): LivingSyncEvent =
        save(
            projectId,
            LivingSyncEvent(
                id = newId(),
                projectId = projectId,
                type = LivingSyncEventType.SYNC_NOTE,
                featureId = request.featureId,
                taskId = request.taskId,
                agentName = request.agentName,
                status = request.severity.name,
                summary = request.suggestedAction?.let { "${request.message} Suggested action: $it" } ?: request.message,
                createdAt = now(),
            ),
        )

    fun getSummary(projectId: String): LivingSyncSummary {
        projectService.getProject(projectId)
        val events = storage.listEvents(projectId)
        val featureSummaries = events
            .filter { it.type == LivingSyncEventType.FEATURE_PROGRESS && it.featureId != null }
            .groupBy { it.featureId!! }
            .mapNotNull { (featureId, featureEvents) ->
                val latest = featureEvents.maxByOrNull { it.createdAt } ?: return@mapNotNull null
                LivingSyncFeatureSummary(
                    featureId = featureId,
                    status = latest.status?.let { LivingSyncFeatureStatus.valueOf(it) } ?: LivingSyncFeatureStatus.PLANNED,
                    summary = latest.summary,
                    updatedAt = latest.createdAt,
                )
            }
            .sortedBy { it.featureId }

        val testEvents = events.filter { it.type == LivingSyncEventType.TEST_RUN }
        val latestTest = testEvents.maxByOrNull { it.createdAt }
        val tokenEvents = events.filter { it.type == LivingSyncEventType.TOKEN_USAGE }

        return LivingSyncSummary(
            projectId = projectId,
            features = featureSummaries,
            tests = LivingSyncTestSummary(
                totalRuns = testEvents.size,
                passed = testEvents.sumOf { it.testsPassed ?: 0 },
                failed = testEvents.sumOf { it.testsFailed ?: 0 },
                lastStatus = latestTest?.status,
                lastCommand = latestTest?.testCommand,
                updatedAt = latestTest?.createdAt,
            ),
            tokens = LivingSyncTokenSummary(
                inputTokens = tokenEvents.sumOf { it.inputTokens ?: 0 },
                outputTokens = tokenEvents.sumOf { it.outputTokens ?: 0 },
                totalTokens = tokenEvents.sumOf { it.totalTokens ?: 0 },
            ),
            changedFiles = events.flatMap { it.files }.distinct().sorted(),
            commits = events.flatMap { it.commits }.distinct().sorted(),
            notes = events.filter { it.type == LivingSyncEventType.SYNC_NOTE }.sortedByDescending { it.createdAt },
            recentEvents = events.sortedByDescending { it.createdAt }.take(20),
            updatedAt = events.maxOfOrNull { it.createdAt },
        )
    }

    private fun save(projectId: String, event: LivingSyncEvent): LivingSyncEvent {
        projectService.getProject(projectId)
        storage.saveEvent(event)
        return event
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun now(): String = Instant.now().toString()
}
