package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FeatureProposalAgentTest {

    /**
     * Builds a real [ProjectService]/[WizardService] stack rooted at [tmp] (same @TempDir fixture
     * pattern as [PlanGeneratorAgentScopeTest]/[SpecContextBuilderTest]). Seeds idea/problem/scope/mvp
     * spec files so [SpecContextBuilder.buildProposalContext] has non-empty content, and persists
     * the IDEA step's `category` field so category-dependent tests can observe it.
     */
    private data class Fixture(
        val projectId: String,
        val projectService: ProjectService,
        val wizardService: WizardService,
    )

    private fun buildFixture(tmp: Path, category: String = "SaaS"): Fixture {
        val storage = ProjectStorage(tmp.toString())
        val projectService = ProjectService(storage)
        val wizardService = WizardService(storage)

        val response = projectService.createProject("Test")
        val projectId = response.project.id

        projectService.saveSpecFile(projectId, "idea.md", "A SaaS platform idea")
        projectService.saveSpecFile(projectId, "problem.md", "Users need X")
        projectService.saveSpecFile(projectId, "scope.md", "In scope: X, Y")
        projectService.saveSpecFile(projectId, "mvp.md", "MVP: X only")

        wizardService.saveWizardData(
            projectId,
            WizardData(
                projectId = projectId,
                steps = mapOf(
                    "IDEA" to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive(category)),
                        completedAt = "2026-04-17T10:00:00Z",
                    )
                )
            )
        )

        return Fixture(projectId, projectService, wizardService)
    }

    @Test
    fun `parses JSON response into graph with auto-assigned IDs and edge title-to-id mapping`(@TempDir tmp: Path) = runBlocking<Unit> {
        val f = buildFixture(tmp, category = "SaaS")
        val contextBuilder = SpecContextBuilder(f.projectService, f.wizardService)
        val agent = object : FeatureProposalAgent(contextBuilder) {
            override suspend fun runAgent(prompt: String): String = """
                {"features":[
                  {"title":"Login","scopes":["BACKEND"],"description":"Auth","scopeFields":{"apiEndpoints":"POST /auth/login"}},
                  {"title":"Dashboard","scopes":["FRONTEND"],"description":"Main","scopeFields":{"screens":"/dashboard"}}
                ],"edges":[{"fromTitle":"Login","toTitle":"Dashboard"}]}
            """
        }
        val graph = agent.proposeFeatures(f.projectId)

        assertThat(graph.features).hasSize(2)
        assertThat(graph.features[0].id).isNotBlank()
        assertThat(graph.edges).hasSize(1)
        val login = graph.features.single { it.title == "Login" }
        val dashboard = graph.features.single { it.title == "Dashboard" }
        assertThat(graph.edges[0].from).isEqualTo(login.id)
        assertThat(graph.edges[0].to).isEqualTo(dashboard.id)
        assertThat(login.scopes).containsExactly(FeatureScope.BACKEND)
        assertThat(dashboard.scopes).containsExactly(FeatureScope.FRONTEND)
        assertThat(login.scopeFields["apiEndpoints"]).isEqualTo("POST /auth/login")
    }

    @Test
    fun `malformed JSON throws ProposalParseException`(@TempDir tmp: Path) = runBlocking<Unit> {
        val f = buildFixture(tmp, category = "SaaS")
        val contextBuilder = SpecContextBuilder(f.projectService, f.wizardService)
        val agent = object : FeatureProposalAgent(contextBuilder) {
            override suspend fun runAgent(prompt: String): String = "not json"
        }
        val ex = runCatching { agent.proposeFeatures(f.projectId) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(ProposalParseException::class.java)
    }

    @Test
    fun `respects category for default scopes when LLM omits them`(@TempDir tmp: Path) = runBlocking<Unit> {
        val f = buildFixture(tmp, category = "Library")
        val contextBuilder = SpecContextBuilder(f.projectService, f.wizardService)
        val agent = object : FeatureProposalAgent(contextBuilder) {
            override suspend fun runAgent(prompt: String): String = """
                {"features":[{"title":"Utils","description":""}],"edges":[]}
            """
        }
        val graph = agent.proposeFeatures(f.projectId)
        assertThat(graph.features[0].scopes).isEmpty()  // Library default = empty
    }

    @Test
    fun `unresolvable edge title is silently dropped`(@TempDir tmp: Path) = runBlocking<Unit> {
        val f = buildFixture(tmp, category = "SaaS")
        val contextBuilder = SpecContextBuilder(f.projectService, f.wizardService)
        val agent = object : FeatureProposalAgent(contextBuilder) {
            override suspend fun runAgent(prompt: String): String = """
                {"features":[{"title":"A","scopes":["BACKEND"]}],"edges":[{"fromTitle":"A","toTitle":"Ghost"}]}
            """
        }
        val graph = agent.proposeFeatures(f.projectId)
        assertThat(graph.edges).isEmpty()
        assertThat(graph.features).hasSize(1)
    }
}
