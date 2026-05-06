package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.AcceptanceCriterion
import com.agentwork.productspecagent.domain.FeatureScope
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

class AcceptanceCriteriaProposalAgentTest {

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
    fun `parses JSON response into AcceptanceCriterion list with assigned UUIDs`(): Unit = runBlocking {
        val feature = WizardFeature(
            id = "f1", title = "Login",
            scopes = setOf(FeatureScope.BACKEND), description = "Auth flow",
        )
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : AcceptanceCriteriaProposalAgent(
            specCtxStub(), wizardService, promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = """
                {"criteria":[
                  {"text":"User can log in with valid credentials"},
                  {"text":"Wrong password is rejected with a clear message"}
                ]}
            """
        }

        val criteria = agent.propose(projectId, "f1")

        assertThat(criteria).hasSize(2)
        assertThat(criteria[0].id).isNotBlank()
        assertThat(criteria[0].text).isEqualTo("User can log in with valid credentials")
        assertThat(criteria[1].text).isEqualTo("Wrong password is rejected with a clear message")
        // UUIDs are unique per criterion
        assertThat(criteria[0].id).isNotEqualTo(criteria[1].id)
    }

    @Test
    fun `malformed JSON throws ProposalParseException`(): Unit = runBlocking {
        val feature = WizardFeature(id = "f1", title = "Login")
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : AcceptanceCriteriaProposalAgent(
            specCtxStub(), wizardService, promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = "not a json"
        }

        val ex = runCatching { agent.propose(projectId, "f1") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(ProposalParseException::class.java)
    }

    @Test
    fun `unknown featureId throws IllegalArgumentException`(): Unit = runBlocking {
        val feature = WizardFeature(id = "f1", title = "Login")
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : AcceptanceCriteriaProposalAgent(
            specCtxStub(), wizardService, promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = """{"criteria":[]}"""
        }

        val ex = runCatching { agent.propose(projectId, "does-not-exist") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `prompt includes feature title and description in context`(): Unit = runBlocking {
        var capturedPrompt = ""
        val feature = WizardFeature(
            id = "f1", title = "Login Flow",
            scopes = setOf(FeatureScope.BACKEND), description = "OAuth + email",
        )
        val (projectId, wizardService) = setup(listOf(feature))

        val agent = object : AcceptanceCriteriaProposalAgent(
            specCtxStub(), wizardService, promptService,
        ) {
            override suspend fun runAgent(prompt: String): String {
                capturedPrompt = prompt
                return """{"criteria":[]}"""
            }
        }
        agent.propose(projectId, "f1")

        assertThat(capturedPrompt).contains("Title: Login Flow")
        assertThat(capturedPrompt).contains("Description: OAuth + email")
        assertThat(capturedPrompt).contains("Scopes: BACKEND")
    }
}
