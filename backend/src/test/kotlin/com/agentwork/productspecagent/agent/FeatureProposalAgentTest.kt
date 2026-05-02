package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.config.FeatureProposalUploadsProperties
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.PromptRegistry
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.UploadStorage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureProposalAgentTest {

    private val promptService = PromptService(PromptRegistry(), InMemoryObjectStore())

    @Test
    fun `parses JSON response into graph with auto-assigned IDs and edge ID translation`(): Unit = runBlocking {
        val mock = object : FeatureProposalAgent(
            contextBuilderStub(category = "SaaS"),
            uploadBuilderStub(""),
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = """
                {"features":[
                  {"title":"Login","scopes":["BACKEND"],"description":"Auth","scopeFields":{"apiEndpoints":"POST /auth/login"}},
                  {"title":"Dashboard","scopes":["FRONTEND"],"description":"Main","scopeFields":{"screens":"/dashboard"}}
                ],"edges":[{"fromTitle":"Login","toTitle":"Dashboard"}]}
            """
        }
        val graph = mock.proposeFeatures("p1")
        assertThat(graph.features).hasSize(2)
        assertThat(graph.features[0].id).isNotBlank()
        assertThat(graph.edges).hasSize(1)
        val login = graph.features.single { it.title == "Login" }
        val dashboard = graph.features.single { it.title == "Dashboard" }
        assertThat(graph.edges[0].from).isEqualTo(login.id)
        assertThat(graph.edges[0].to).isEqualTo(dashboard.id)
    }

    @Test
    fun `malformed JSON throws ProposalParseException`(): Unit = runBlocking {
        val mock = object : FeatureProposalAgent(
            contextBuilderStub(category = "SaaS"),
            uploadBuilderStub(""),
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = "not a json"
        }
        val ex = runCatching { mock.proposeFeatures("p1") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(ProposalParseException::class.java)
    }

    @Test
    fun `respects category for default scopes when LLM omits them`(): Unit = runBlocking {
        val mock = object : FeatureProposalAgent(
            contextBuilderStub(category = "Library"),
            uploadBuilderStub(""),
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String = """
                {"features":[{"title":"Utils","description":""}],"edges":[]}
            """
        }
        val graph = mock.proposeFeatures("p1")
        assertThat(graph.features[0].scopes).isEmpty()
    }

    @Test
    fun `embeds uploads section between context and JSON instruction when non-empty`(): Unit = runBlocking {
        var capturedPrompt = ""
        val mock = object : FeatureProposalAgent(
            contextBuilderStub(category = "SaaS"),
            uploadBuilderStub("--- BEGIN UPLOADED DOCUMENT: spec.md (text/markdown) ---\nhello\n--- END UPLOADED DOCUMENT ---"),
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String {
                capturedPrompt = prompt
                return """{"features":[],"edges":[]}"""
            }
        }
        mock.proposeFeatures("p1")

        assertThat(capturedPrompt).contains("=== UPLOADED REFERENCE DOCUMENTS ===")
        assertThat(capturedPrompt).contains("--- BEGIN UPLOADED DOCUMENT: spec.md (text/markdown) ---")
        assertThat(capturedPrompt).contains("=== END UPLOADED DOCUMENTS ===")
        // Section sits between context (Idea/Category) and the JSON instruction
        val ctxIdx = capturedPrompt.indexOf("Category: SaaS")
        val sectionIdx = capturedPrompt.indexOf("=== UPLOADED REFERENCE DOCUMENTS ===")
        val jsonIdx = capturedPrompt.indexOf("Respond with EXACTLY this JSON format")
        assertThat(ctxIdx).isGreaterThanOrEqualTo(0)
        assertThat(sectionIdx).isGreaterThan(ctxIdx)
        assertThat(jsonIdx).isGreaterThan(sectionIdx)
    }

    @Test
    fun `omits uploads section wrapper when builder returns empty`(): Unit = runBlocking {
        var capturedPrompt = ""
        val mock = object : FeatureProposalAgent(
            contextBuilderStub(category = "SaaS"),
            uploadBuilderStub(""),
            promptService,
        ) {
            override suspend fun runAgent(prompt: String): String {
                capturedPrompt = prompt
                return """{"features":[],"edges":[]}"""
            }
        }
        mock.proposeFeatures("p1")

        assertThat(capturedPrompt).doesNotContain("=== UPLOADED REFERENCE DOCUMENTS ===")
        assertThat(capturedPrompt).doesNotContain("=== END UPLOADED DOCUMENTS ===")
    }

    private fun uploadBuilderStub(uploadsSection: String): UploadPromptBuilder =
        object : UploadPromptBuilder(
            uploadStorage = UploadStorage(InMemoryObjectStore()),
            props = FeatureProposalUploadsProperties(),
        ) {
            override fun renderUploadsSection(projectId: String): String = uploadsSection
        }

    private fun contextBuilderStub(category: String): SpecContextBuilder {
        val dummyStorage = ProjectStorage(InMemoryObjectStore())
        val dummyProjectService = ProjectService(dummyStorage)
        return object : SpecContextBuilder(dummyProjectService) {
            override fun buildProposalContext(projectId: String): String =
                "Idea: Test project\nCategory: $category"
        }
    }
}
