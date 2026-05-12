package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.FeatureDoneImportAgent
import com.agentwork.productspecagent.agent.FeatureDoneImportHeaderCheck
import com.agentwork.productspecagent.agent.FeatureDoneImportResult
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.CheckSeverity
import com.agentwork.productspecagent.domain.FeatureCompletionSnapshot
import com.agentwork.productspecagent.domain.FeatureCompletionTestEvidence
import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.domain.LivingSyncFeatureProgressRequest
import com.agentwork.productspecagent.domain.LivingSyncFeatureStatus
import com.agentwork.productspecagent.domain.LivingSyncTestRunRequest
import com.agentwork.productspecagent.domain.LivingSyncTokenUsageRequest
import com.agentwork.productspecagent.domain.LivingSyncCodeChangesRequest
import com.agentwork.productspecagent.domain.LivingSyncEventType
import com.agentwork.productspecagent.domain.LivingSyncFeatureDoneImportRequest
import com.agentwork.productspecagent.domain.LivingSyncNoteRequest
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.LivingSyncStorage
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LivingSyncServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private data class Fixture(
        val projectId: String,
        val service: LivingSyncService,
        val storage: LivingSyncStorage,
    )

    private fun fixture(
        features: List<WizardFeature> = emptyList(),
        importAgent: FeatureDoneImportAgent = fakeImportAgent(),
    ): Fixture {
        val objectStore = InMemoryObjectStore()
        val projectService = ProjectService(ProjectStorage(objectStore))
        val wizardService = WizardService(ProjectStorage(objectStore))
        val projectId = projectService.createProject("Living Sync").project.id
        if (features.isNotEmpty()) {
            val featuresJson = json.encodeToJsonElement(features)
            val stepData = WizardStepData(fields = mapOf("features" to featuresJson))
            wizardService.saveWizardData(projectId, WizardData(projectId = projectId, steps = mapOf("FEATURES" to stepData)))
        }
        val storage = LivingSyncStorage(objectStore)
        val service = LivingSyncService(projectService, storage, importAgent, wizardService)
        return Fixture(projectId, service, storage)
    }

    private fun fakeImportAgent(
        resultSummary: String = "Imported from done report",
        status: LivingSyncFeatureStatus = LivingSyncFeatureStatus.DONE,
        headerWarnings: List<String> = emptyList(),
        resultWarnings: List<String> = emptyList(),
    ): FeatureDoneImportAgent = object : FeatureDoneImportAgent(
        contextBuilder = SpecContextBuilder(ProjectService(ProjectStorage(InMemoryObjectStore())), null),
        wizardService = WizardService(ProjectStorage(InMemoryObjectStore())),
        promptService = PromptService(PromptRegistry(), InMemoryObjectStore()),
    ) {
        override suspend fun importDoneReport(
            projectId: String,
            featureId: String,
            fileName: String,
            markdown: String,
        ) = FeatureDoneImportResult(
            featureId = featureId,
            headerCheck = FeatureDoneImportHeaderCheck(
                matchesExpectedFeature = true,
                reportedFeatureLabel = fileName,
                warnings = headerWarnings,
            ),
            derivedStatus = status,
            summary = resultSummary,
            implementedItems = listOf("Imported item"),
            tests = listOf(FeatureCompletionTestEvidence(name = "LivingSyncServiceTest", status = "PRESENT")),
            openPoints = listOf("Follow-up"),
            warnings = resultWarnings,
        )
    }

    @Test
    fun `report feature progress stores event and summary latest status`() {
        val fixture = fixture()
        val projectId = fixture.projectId
        val service = fixture.service

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
        val fixture = fixture()
        val projectId = fixture.projectId
        val service = fixture.service

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

    @Test
    fun `import feature done markdown stores event raw markdown snapshot and projects summary`() = runBlocking {
        val feature = WizardFeature(
            id = "feature-1",
            title = "Living Sync via MCP",
            scopes = setOf(FeatureScope.BACKEND),
        )
        val fixture = fixture(features = listOf(feature))

        val event = fixture.service.importFeatureDoneMarkdown(
            fixture.projectId,
            LivingSyncFeatureDoneImportRequest(
                featureId = "feature-1",
                fileName = "feature-1-done.md",
                markdown = "# Done\nImported and verified",
                agentName = "codex",
            ),
        )

        assertEquals(LivingSyncEventType.FEATURE_DONE_IMPORT, event.type)
        assertEquals("feature-1", event.featureId)
        assertEquals("Imported feature-1-done.md: Imported from done report", event.summary)

        assertEquals(
            "# Done\nImported and verified",
            fixture.storage.loadImportedDoneMarkdown(fixture.projectId, "feature-1", event.id),
        )

        val snapshot = fixture.storage.loadFeatureCompletionSnapshot(fixture.projectId, "feature-1")
        assertNotNull(snapshot)
        assertEquals(LivingSyncFeatureStatus.DONE, snapshot.derivedStatus)
        assertEquals("Imported from done report", snapshot.summary)

        val summary = fixture.service.getSummary(fixture.projectId)
        assertEquals(1, summary.featureCompletions.size)
        assertEquals("feature-1", summary.featureCompletions.single().featureId)
        assertEquals(LivingSyncFeatureStatus.DONE, summary.featureCompletions.single().derivedStatus)
        assertEquals(LivingSyncFeatureStatus.DONE, summary.features.single().status)
        assertEquals("Imported from done report", summary.features.single().summary)
        assertEquals(event.id, summary.featureCompletions.single().sourceEventId)
        assertEquals(LivingSyncEventType.FEATURE_DONE_IMPORT, summary.recentEvents.first().type)
    }

    @Test
    fun `summary includes snapshot only state without requiring import event lookup`() {
        val feature = WizardFeature(
            id = "feature-1",
            title = "Living Sync via MCP",
            scopes = setOf(FeatureScope.BACKEND),
        )
        val fixture = fixture(features = listOf(feature))
        fixture.storage.saveFeatureCompletionSnapshot(
            FeatureCompletionSnapshot(
                projectId = fixture.projectId,
                featureId = "feature-1",
                derivedStatus = LivingSyncFeatureStatus.DONE,
                summary = "Snapshot only",
                sourceEventId = "event-snapshot",
                sourceFileName = "done.md",
                updatedAt = "2026-05-12T10:00:00Z",
            ),
        )

        val summary = fixture.service.getSummary(fixture.projectId)

        assertEquals(1, summary.featureCompletions.size)
        assertEquals("feature-1", summary.featureCompletions.single().featureId)
        assertEquals("Snapshot only", summary.featureCompletions.single().summary)
        assertEquals(LivingSyncFeatureStatus.DONE, summary.features.single().status)
        assertEquals("Snapshot only", summary.features.single().summary)
    }

    @Test
    fun `summary keeps untouched wizard features visible as planned`() {
        val fixture = fixture(
            features = listOf(
                WizardFeature(id = "feature-1", title = "Imported", scopes = setOf(FeatureScope.BACKEND)),
                WizardFeature(id = "feature-2", title = "Untouched", scopes = setOf(FeatureScope.BACKEND)),
            ),
        )
        fixture.storage.saveFeatureCompletionSnapshot(
            FeatureCompletionSnapshot(
                projectId = fixture.projectId,
                featureId = "feature-1",
                derivedStatus = LivingSyncFeatureStatus.DONE,
                summary = "Imported snapshot",
                sourceEventId = "event-1",
                sourceFileName = "done.md",
                updatedAt = "2026-05-12T10:00:00Z",
            ),
        )

        val summary = fixture.service.getSummary(fixture.projectId)

        assertEquals(listOf("feature-1", "feature-2"), summary.features.map { it.featureId })
        assertEquals(LivingSyncFeatureStatus.DONE, summary.features[0].status)
        assertEquals(LivingSyncFeatureStatus.PLANNED, summary.features[1].status)
        assertEquals("", summary.features[1].summary)
    }

    @Test
    fun `import feature done markdown preserves header check warnings in event evidence and snapshot warnings`() = runBlocking {
        val fixture = fixture(
            features = listOf(
                WizardFeature(id = "feature-1", title = "Living Sync via MCP", scopes = setOf(FeatureScope.BACKEND)),
            ),
            importAgent = fakeImportAgent(
                headerWarnings = listOf("Header mismatch warning"),
                resultWarnings = listOf("Top-level warning"),
            ),
        )

        val event = fixture.service.importFeatureDoneMarkdown(
            fixture.projectId,
            LivingSyncFeatureDoneImportRequest(
                featureId = "feature-1",
                fileName = "feature-1-done.md",
                markdown = "# Done",
            ),
        )

        val snapshot = fixture.storage.loadFeatureCompletionSnapshot(fixture.projectId, "feature-1")
        assertNotNull(snapshot)
        assertEquals(
            listOf("Header mismatch warning", "Top-level warning"),
            snapshot.warnings,
        )
        assertTrue(event.evidence.contains("Header mismatch warning"))
        assertTrue(event.evidence.contains("Top-level warning"))
    }

    @Test
    fun `summary prefers latest feature progress event over older imported snapshot`() {
        val feature = WizardFeature(
            id = "feature-1",
            title = "Living Sync via MCP",
            scopes = setOf(FeatureScope.BACKEND),
        )
        val fixture = fixture(features = listOf(feature))
        runBlocking {
            fixture.service.importFeatureDoneMarkdown(
                fixture.projectId,
                LivingSyncFeatureDoneImportRequest(
                    featureId = "feature-1",
                    fileName = "done.md",
                    markdown = "# Done",
                ),
            )
        }
        fixture.storage.saveFeatureCompletionSnapshot(
            FeatureCompletionSnapshot(
                projectId = fixture.projectId,
                featureId = "feature-1",
                derivedStatus = LivingSyncFeatureStatus.DONE,
                summary = "Imported snapshot",
                sourceEventId = fixture.storage.listEvents(fixture.projectId).single { it.type == LivingSyncEventType.FEATURE_DONE_IMPORT }.id,
                sourceFileName = "done.md",
                updatedAt = "2026-05-12T10:00:00Z",
            ),
        )
        fixture.service.reportFeatureProgress(
            fixture.projectId,
            LivingSyncFeatureProgressRequest(
                featureId = "feature-1",
                status = LivingSyncFeatureStatus.BLOCKED,
                summary = "Blocked after import",
            ),
        )

        val summary = fixture.service.getSummary(fixture.projectId)

        assertEquals(1, summary.featureCompletions.size)
        assertEquals("Imported snapshot", summary.featureCompletions.single().summary)
        assertEquals(LivingSyncFeatureStatus.BLOCKED, summary.features.single().status)
        assertEquals("Blocked after import", summary.features.single().summary)
    }
}
