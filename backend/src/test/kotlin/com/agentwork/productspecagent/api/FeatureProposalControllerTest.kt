package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.FeatureProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.GraphPosition
import com.agentwork.productspecagent.domain.WizardFeature
import com.agentwork.productspecagent.domain.WizardFeatureEdge
import com.agentwork.productspecagent.domain.WizardFeatureGraph
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class FeatureProposalControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun testFeatureProposalAgent(contextBuilder: SpecContextBuilder): FeatureProposalAgent {
            return object : FeatureProposalAgent(contextBuilder) {
                override suspend fun proposeFeatures(projectId: String): WizardFeatureGraph {
                    if (projectId == "bad") {
                        throw ProposalParseException("Invalid JSON from LLM: unexpected token")
                    }
                    return WizardFeatureGraph(
                        features = listOf(
                            WizardFeature(
                                id = "feat-1",
                                title = "A",
                                scopes = setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND),
                                description = "Feature A",
                                scopeFields = emptyMap(),
                                position = GraphPosition(),
                            ),
                            WizardFeature(
                                id = "feat-2",
                                title = "B",
                                scopes = setOf(FeatureScope.BACKEND),
                                description = "Feature B",
                                scopeFields = emptyMap(),
                                position = GraphPosition(),
                            ),
                        ),
                        edges = listOf(
                            WizardFeatureEdge(id = "edge-1", from = "feat-1", to = "feat-2"),
                        ),
                    )
                }
            }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST features propose returns 200 and graph JSON`() {
        mockMvc.perform(post("/api/v1/projects/p1/features/propose"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.features.length()").value(2))
            .andExpect(jsonPath("$.features[0].title").value("A"))
            .andExpect(jsonPath("$.features[0].id").value("feat-1"))
            .andExpect(jsonPath("$.features[1].title").value("B"))
            .andExpect(jsonPath("$.edges.length()").value(1))
            .andExpect(jsonPath("$.edges[0].from").value("feat-1"))
            .andExpect(jsonPath("$.edges[0].to").value("feat-2"))
    }

    @Test
    fun `POST features propose returns 422 when agent throws ProposalParseException`() {
        mockMvc.perform(post("/api/v1/projects/bad/features/propose"))
            .andExpect(status().is4xxClientError())
            .andExpect(status().`is`(422))
            .andExpect(jsonPath("$.error").exists())
    }
}
