package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureProposalAgentTest {
    @Test
    fun `parses JSON response into graph with auto-assigned IDs and edge ID translation`() = runBlocking {
        val mock = object : FeatureProposalAgent(contextBuilderStub(category = "SaaS")) {
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
    fun `malformed JSON throws ProposalParseException`() = runBlocking {
        val mock = object : FeatureProposalAgent(contextBuilderStub(category = "SaaS")) {
            override suspend fun runAgent(prompt: String): String = "not a json"
        }
        val ex = runCatching { mock.proposeFeatures("p1") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(ProposalParseException::class.java)
    }

    @Test
    fun `respects category for default scopes when LLM omits them`() = runBlocking {
        val mock = object : FeatureProposalAgent(contextBuilderStub(category = "Library")) {
            override suspend fun runAgent(prompt: String): String = """
                {"features":[{"title":"Utils","description":""}],"edges":[]}
            """
        }
        val graph = mock.proposeFeatures("p1")
        assertThat(graph.features[0].scopes).isEmpty()  // Library default
    }

    /**
     * Stub: subclasses the now-open SpecContextBuilder with a dummy ProjectService.
     * buildProposalContext is overridden so ProjectService is never actually called.
     */
    private fun contextBuilderStub(category: String): SpecContextBuilder {
        val dummyStorage = ProjectStorage(InMemoryObjectStore())
        val dummyProjectService = ProjectService(dummyStorage)
        return object : SpecContextBuilder(dummyProjectService) {
            override fun buildProposalContext(projectId: String): String =
                "Idea: Test project\nCategory: $category"
        }
    }
}
