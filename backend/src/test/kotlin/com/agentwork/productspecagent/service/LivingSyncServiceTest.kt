package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.CheckSeverity
import com.agentwork.productspecagent.domain.LivingSyncFeatureProgressRequest
import com.agentwork.productspecagent.domain.LivingSyncFeatureStatus
import com.agentwork.productspecagent.domain.LivingSyncTestRunRequest
import com.agentwork.productspecagent.domain.LivingSyncTokenUsageRequest
import com.agentwork.productspecagent.domain.LivingSyncCodeChangesRequest
import com.agentwork.productspecagent.domain.LivingSyncNoteRequest
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.LivingSyncStorage
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LivingSyncServiceTest {

    private fun fixture(): Pair<String, LivingSyncService> {
        val objectStore = InMemoryObjectStore()
        val projectService = ProjectService(ProjectStorage(objectStore))
        val projectId = projectService.createProject("Living Sync").project.id
        val service = LivingSyncService(projectService, LivingSyncStorage(objectStore))
        return projectId to service
    }

    @Test
    fun `report feature progress stores event and summary latest status`() {
        val (projectId, service) = fixture()

        service.reportFeatureProgress(
            projectId,
            LivingSyncFeatureProgressRequest(
                featureId = "feature-1",
                status = LivingSyncFeatureStatus.IN_PROGRESS,
                summary = "Started implementation",
                evidence = listOf("src/auth.ts"),
            ),
        )
        service.reportFeatureProgress(
            projectId,
            LivingSyncFeatureProgressRequest(
                featureId = "feature-1",
                status = LivingSyncFeatureStatus.DONE,
                summary = "Implemented and tested",
            ),
        )

        val summary = service.getSummary(projectId)

        assertEquals(LivingSyncFeatureStatus.DONE, summary.features.single().status)
        assertEquals("Implemented and tested", summary.features.single().summary)
        assertEquals(2, summary.recentEvents.size)
    }

    @Test
    fun `summary aggregates tests tokens files commits and notes`() {
        val (projectId, service) = fixture()

        service.reportTestRun(
            projectId,
            LivingSyncTestRunRequest(
                command = "npm test",
                status = "passed",
                summary = "All tests passed",
                passed = 12,
                failed = 0,
            ),
        )
        service.reportTokenUsage(
            projectId,
            LivingSyncTokenUsageRequest(
                agentName = "codex",
                model = "gpt-5.4",
                inputTokens = 100,
                outputTokens = 40,
                totalTokens = 140,
            ),
        )
        service.reportCodeChanges(
            projectId,
            LivingSyncCodeChangesRequest(
                summary = "Updated login",
                files = listOf("src/login.tsx", "src/login.test.tsx"),
                commits = listOf("abc123"),
            ),
        )
        service.reportSyncNote(
            projectId,
            LivingSyncNoteRequest(
                severity = CheckSeverity.WARNING,
                message = "Deviation from plan",
                suggestedAction = "Review scope",
            ),
        )

        val summary = service.getSummary(projectId)

        assertEquals(1, summary.tests.totalRuns)
        assertEquals(12, summary.tests.passed)
        assertEquals(0, summary.tests.failed)
        assertEquals("npm test", summary.tests.lastCommand)
        assertEquals(100, summary.tokens.inputTokens)
        assertEquals(40, summary.tokens.outputTokens)
        assertEquals(140, summary.tokens.totalTokens)
        assertEquals(listOf("src/login.test.tsx", "src/login.tsx"), summary.changedFiles)
        assertEquals(listOf("abc123"), summary.commits)
        assertTrue(summary.notes.single().summary.contains("Deviation from plan"))
    }
}
