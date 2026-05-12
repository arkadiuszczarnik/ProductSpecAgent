package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.FeatureDoneImportAgent
import com.agentwork.productspecagent.domain.FeatureCompletionSnapshot
import com.agentwork.productspecagent.domain.LivingSyncCodeChangesRequest
import com.agentwork.productspecagent.domain.LivingSyncEvent
import com.agentwork.productspecagent.domain.LivingSyncEventType
import com.agentwork.productspecagent.domain.LivingSyncFeatureDoneImportRequest
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
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class LivingSyncService(
    private val projectService: ProjectService,
    private val storage: LivingSyncStorage,
    private val featureDoneImportAgent: FeatureDoneImportAgent,
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

    fun importFeatureDoneMarkdown(projectId: String, request: LivingSyncFeatureDoneImportRequest): LivingSyncEvent {
        projectService.getProject(projectId)
        val importResult = runBlocking {
            featureDoneImportAgent.importDoneReport(
                projectId = projectId,
                featureId = request.featureId,
                fileName = request.fileName,
                markdown = request.markdown,
            )
        }
        val timestamp = now()
        val event = LivingSyncEvent(
            id = newId(),
            projectId = projectId,
            type = LivingSyncEventType.FEATURE_DONE_IMPORT,
            featureId = request.featureId,
            agentName = request.agentName ?: FeatureDoneImportAgent.AGENT_ID,
            status = importResult.derivedStatus.name,
            summary = "Imported ${request.fileName}: ${importResult.summary}",
            evidence = buildList {
                addAll(importResult.implementedItems)
                addAll(importResult.deviations)
                addAll(importResult.openPoints)
                addAll(importResult.technicalDebt)
                addAll(importResult.warnings)
                addAll(importResult.tests.map { "${it.name}: ${it.status}" })
            },
            createdAt = timestamp,
        )
        storage.saveImportedDoneMarkdown(projectId, request.featureId, event.id, request.markdown)
        storage.saveFeatureCompletionSnapshot(
            FeatureCompletionSnapshot(
                projectId = projectId,
                featureId = request.featureId,
                derivedStatus = importResult.derivedStatus,
                summary = importResult.summary,
                implementedItems = importResult.implementedItems,
                deviations = importResult.deviations,
                openPoints = importResult.openPoints,
                technicalDebt = importResult.technicalDebt,
                tests = importResult.tests,
                warnings = importResult.warnings,
                sourceEventId = event.id,
                sourceFileName = request.fileName,
                updatedAt = timestamp,
            ),
        )
        storage.saveEvent(event)
        return event
    }

    fun getSummary(projectId: String): LivingSyncSummary {
        projectService.getProject(projectId)
        val events = storage.listEvents(projectId)
        val progressEventsByFeature = events
            .filter { it.type == LivingSyncEventType.FEATURE_PROGRESS && it.featureId != null }
            .groupBy { it.featureId!! }
        val featureCompletions = events
            .filter { it.type == LivingSyncEventType.FEATURE_DONE_IMPORT && it.featureId != null }
            .mapNotNull { it.featureId }
            .distinct()
            .mapNotNull { featureId -> storage.loadFeatureCompletionSnapshot(projectId, featureId) }
            .sortedBy { it.featureId }
        val featureIds = (progressEventsByFeature.keys + featureCompletions.map { it.featureId }).toSortedSet()
        val featureSummaries = featureIds.map { featureId ->
            val latestProgress = progressEventsByFeature[featureId]?.maxByOrNull { it.createdAt }
            val snapshot = featureCompletions.firstOrNull { it.featureId == featureId }
            when {
                latestProgress == null && snapshot != null -> snapshot.toFeatureSummary()
                latestProgress != null && snapshot == null -> latestProgress.toFeatureSummary(featureId)
                latestProgress != null && snapshot != null -> {
                    if (latestProgress.createdAt >= snapshot.updatedAt) {
                        latestProgress.toFeatureSummary(featureId)
                    } else {
                        snapshot.toFeatureSummary()
                    }
                }
                else -> LivingSyncFeatureSummary(
                    featureId = featureId,
                    status = LivingSyncFeatureStatus.PLANNED,
                    summary = "",
                    updatedAt = "",
                )
            }
        }

        val testEvents = events.filter { it.type == LivingSyncEventType.TEST_RUN }
        val latestTest = testEvents.maxByOrNull { it.createdAt }
        val tokenEvents = events.filter { it.type == LivingSyncEventType.TOKEN_USAGE }

        return LivingSyncSummary(
            projectId = projectId,
            features = featureSummaries,
            featureCompletions = featureCompletions,
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

    private fun LivingSyncEvent.toFeatureSummary(featureId: String): LivingSyncFeatureSummary =
        LivingSyncFeatureSummary(
            featureId = featureId,
            status = status?.let { LivingSyncFeatureStatus.valueOf(it) } ?: LivingSyncFeatureStatus.PLANNED,
            summary = summary,
            updatedAt = createdAt,
        )

    private fun FeatureCompletionSnapshot.toFeatureSummary(): LivingSyncFeatureSummary =
        LivingSyncFeatureSummary(
            featureId = featureId,
            status = derivedStatus,
            summary = summary,
            updatedAt = updatedAt,
        )
}
