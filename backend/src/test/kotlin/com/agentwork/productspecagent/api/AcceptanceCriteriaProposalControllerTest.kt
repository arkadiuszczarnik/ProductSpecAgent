package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.AcceptanceCriteriaProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.domain.AcceptanceCriterion
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.WizardService
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
class AcceptanceCriteriaProposalControllerTest {

    @TestConfiguration
    class TestAgentConfig {
        @Bean
        @Primary
        fun testAcceptanceCriteriaProposalAgent(
            contextBuilder: SpecContextBuilder,
            wizardService: WizardService,
            promptService: PromptService,
        ): AcceptanceCriteriaProposalAgent {
            return object : AcceptanceCriteriaProposalAgent(contextBuilder, wizardService, promptService) {
                override suspend fun propose(projectId: String, featureId: String): List<AcceptanceCriterion> {
                    return when (featureId) {
                        "f-parse-error" -> throw ProposalParseException("bad JSON from LLM")
                        "f-missing" -> throw IllegalArgumentException("Feature f-missing not found")
                        else -> listOf(
                            AcceptanceCriterion(id = "ac1", title = "User can log in", description = "with valid creds"),
                            AcceptanceCriterion(id = "ac2", title = "Wrong password rejected"),
                        )
                    }
                }
            }
        }
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `POST acceptance-criteria propose returns 200 with criteria list`() {
        val projectId = createProject("AC Test")

        mockMvc.perform(post("/api/v1/projects/$projectId/features/f-1/acceptance-criteria/propose"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("User can log in"))
            .andExpect(jsonPath("$[0].description").value("with valid creds"))
            .andExpect(jsonPath("$[1].title").value("Wrong password rejected"))
    }

    @Test
    fun `POST acceptance-criteria propose returns 422 on parse error`() {
        val projectId = createProject("AC Parse Err")

        mockMvc.perform(post("/api/v1/projects/$projectId/features/f-parse-error/acceptance-criteria/propose"))
            .andExpect(status().isUnprocessableEntity())
    }

    @Test
    fun `POST acceptance-criteria propose returns 404 on missing feature`() {
        val projectId = createProject("AC Missing")

        mockMvc.perform(post("/api/v1/projects/$projectId/features/f-missing/acceptance-criteria/propose"))
            .andExpect(status().isNotFound())
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
