package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.LivingSyncFeatureStatus
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.PromptRegistry
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureDoneImportAgentTest {

    private val promptService = PromptService(PromptRegistry(), InMemoryObjectStore())
    private val testJson = Json { ignoreUnknownKeys = true }

    private fun setup(features: List<WizardFeature>): Pair<String, WizardService> {
        val storage = ProjectStorage(InMemoryObjectStore())
        val projectService = ProjectService(storage)
        val wizardService = WizardService(storage)
        val projectId = projectService.createProject("Test").project.id
        val featuresJson = testJson.encodeToJsonElement(features)
        val stepData = WizardStepData(fields = mapOf("features" to featuresJson))
        wizardService.saveWizardData(projectId, WizardData(projectId = projectId, steps = mapOf("FEATURES" to stepData)))
        return projectId to wizardService
    }

    private fun specCtxStub() = object : SpecContextBuilder(
        ProjectService(ProjectStorage(InMemoryObjectStore())),
        null,
    ) {
        override fun buildProposalContext(projectId: String): String =
            "Idea: Test\nCategory: SaaS"
    }

    @Test
    fun `parses valid JSON response into import result`(): Unit = runBlocking {
        val feature = WizardFeature(
            id = "feature-1",
            title = "Living Sync via MCP",
            scopes = setOf(FeatureScope.BACKEND),
            description = "Imports done reports from markdown",
        )
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : FeatureDoneImportAgent(
            specCtxStub(),
            wizardService,
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = """
                ```json
                {
                  "featureId": "feature-1",
                  "headerCheck": {
                    "matchesExpectedFeature": true,
                    "reportedFeatureLabel": "Feature 45: Living Sync via MCP",
                    "warnings": []
                  },
                  "derivedStatus": "DONE",
                  "summary": "Implemented and tested.",
                  "implementedItems": ["New MCP tool"],
                  "deviations": [],
                  "tests": [{"name":"LivingSyncServiceTest","status":"PRESENT"}],
                  "openPoints": ["Auth hardening remains open."],
                  "technicalDebt": [],
                  "warnings": []
                }
                ```
            """.trimIndent()
        }

        val result = agent.importDoneReport(projectId, "feature-1", "feature-1.md", "# Feature 45\nDone")

        assertThat(result.featureId).isEqualTo("feature-1")
        assertThat(result.derivedStatus).isEqualTo(LivingSyncFeatureStatus.DONE)
        assertThat(result.tests).hasSize(1)
        assertThat(result.tests[0].name).isEqualTo("LivingSyncServiceTest")
        assertThat(result.tests[0].status).isEqualTo("PRESENT")
    }

    @Test
    fun `malformed JSON throws ProposalParseException`(): Unit = runBlocking {
        val feature = WizardFeature(id = "feature-1", title = "Living Sync via MCP")
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : FeatureDoneImportAgent(
            specCtxStub(),
            wizardService,
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = "not a json"
        }

        val ex = runCatching {
            agent.importDoneReport(projectId, "feature-1", "feature-1.md", "# Heading")
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(ProposalParseException::class.java)
    }

    @Test
    fun `unknown featureId in response throws IllegalArgumentException`(): Unit = runBlocking {
        val feature = WizardFeature(id = "feature-1", title = "Living Sync via MCP")
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : FeatureDoneImportAgent(
            specCtxStub(),
            wizardService,
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = """
                {"featureId":"feature-999","headerCheck":{"matchesExpectedFeature":false,"reportedFeatureLabel":"Other","warnings":[]},"derivedStatus":"DONE","summary":"Implemented","implementedItems":[],"deviations":[],"tests":[],"openPoints":[],"technicalDebt":[],"warnings":[]}
            """.trimIndent()
        }

        val ex = runCatching {
            agent.importDoneReport(projectId, "feature-1", "feature-1.md", "# Heading")
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(ex).hasMessageContaining("feature-999")
    }

    @Test
    fun `prompt includes feature title file name and markdown context`(): Unit = runBlocking {
        var capturedPrompt = ""
        val feature = WizardFeature(
            id = "feature-1",
            title = "Living Sync via MCP",
            scopes = setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND),
            description = "Imports done reports from markdown",
        )
        val (projectId, wizardService) = setup(listOf(feature))
        val markdown = """
            # Feature 45: Living Sync via MCP
            
            Implemented and tested.
        """.trimIndent()

        val agent = object : FeatureDoneImportAgent(
            specCtxStub(),
            wizardService,
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String {
                capturedPrompt = prompt
                return """
                    {"featureId":"feature-1","headerCheck":{"matchesExpectedFeature":true,"reportedFeatureLabel":"Feature 45: Living Sync via MCP","warnings":[]},"derivedStatus":"DONE","summary":"Implemented","implementedItems":[],"deviations":[],"tests":[],"openPoints":[],"technicalDebt":[],"warnings":[]}
                """.trimIndent()
            }
        }

        agent.importDoneReport(projectId, "feature-1", "feature-done.md", markdown)

        assertThat(capturedPrompt).contains("Title: Living Sync via MCP")
        assertThat(capturedPrompt).contains("File name: feature-done.md")
        assertThat(capturedPrompt).contains("Description: Imports done reports from markdown")
        assertThat(capturedPrompt).contains("Scopes:")
        assertThat(capturedPrompt).contains("FRONTEND")
        assertThat(capturedPrompt).contains("BACKEND")
        assertThat(capturedPrompt).contains(markdown)
    }
}
