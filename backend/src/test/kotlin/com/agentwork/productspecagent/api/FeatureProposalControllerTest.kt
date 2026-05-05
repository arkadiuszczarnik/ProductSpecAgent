package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.FeatureProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.agent.UploadPromptBuilder
import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.domain.WizardFeatureGraph
import com.agentwork.productspecagent.service.PromptService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class FeatureProposalControllerTest {

    @TestConfiguration
    class TestAgentConfig {
        @Bean
        @Primary
        fun testFeatureProposalAgent(
            contextBuilder: SpecContextBuilder,
            uploadPromptBuilder: UploadPromptBuilder,
            promptService: PromptService,
        ): FeatureProposalAgent {
            return object : FeatureProposalAgent(contextBuilder, uploadPromptBuilder, promptService) {
                override suspend fun proposeFeatures(projectId: String): WizardFeatureGraph {
                    return when (projectId) {
                        "p-parse-error" -> throw ProposalParseException("bad JSON from LLM")
                        else -> WizardFeatureGraph(
                            features = listOf(
                                WizardFeature(
                                    id = "f-1",
                                    title = "A",
                                    scopes = setOf(FeatureScope.BACKEND)
                                )
                            ),
                            edges = emptyList()
                        )
                    }
                }
            }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST features propose returns 200 with graph`() {
        val projectId = createProject("Propose Test")

        mockMvc.perform(post("/api/v1/projects/$projectId/features/propose"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.features[0].title").value("A"))
    }

    @Test
    fun `POST features propose returns 422 on parse error`() {
        mockMvc.perform(post("/api/v1/projects/p-parse-error/features/propose"))
            .andExpect(status().isUnprocessableEntity())
    }

    private fun createProject(name: String): String {
        val result = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"name": "$name"}""")
        ).andExpect(status().isCreated()).andReturn()

        return """"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(result.response.contentAsString)!!
            .groupValues[1]
    }
}
