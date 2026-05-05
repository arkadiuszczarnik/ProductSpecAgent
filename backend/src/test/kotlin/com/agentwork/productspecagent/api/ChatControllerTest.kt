package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.IdeaToSpecAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.service.ClarificationService
import com.agentwork.productspecagent.service.DecisionService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.WizardService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ChatControllerTest {

    @TestConfiguration
    class TestAgentConfig {
        @Bean
        @Primary
        fun testAgent(
            contextBuilder: SpecContextBuilder,
            projectService: ProjectService,
            promptService: PromptService,
            decisionService: DecisionService,
            clarificationService: ClarificationService,
            wizardService: WizardService
        ): IdeaToSpecAgent {
            return object : IdeaToSpecAgent(contextBuilder, projectService, promptService, decisionService, clarificationService, wizardService) {
                override suspend fun runAgent(systemPrompt: String, userMessage: String): String {
                    return if (userMessage.contains("complete")) {
                        "Step is done.\n[STEP_COMPLETE]\n[STEP_SUMMARY]: Summary of the step."
                    } else {
                        "Tell me more about your idea."
                    }
                }
            }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST chat returns 200 with agent response`() {
        // First create a project
        val createResult = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Chat Test"}""")
        )
            .andExpect(status().isCreated())
            .andReturn()

        val body = createResult.response.contentAsString
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        val projectId = idRegex.find(body)!!.groupValues[1]

        // Send a chat message
        mockMvc.perform(
            post("/api/v1/projects/$projectId/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message": "Hello, tell me about the process"}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Tell me more about your idea."))
            .andExpect(jsonPath("$.flowStateChanged").value(false))
            .andExpect(jsonPath("$.currentStep").value("IDEA"))
    }

    @Test
    fun `POST chat advances flow when agent completes step`() {
        val createResult = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Flow Test"}""")
        )
            .andExpect(status().isCreated())
            .andReturn()

        val body = createResult.response.contentAsString
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        val projectId = idRegex.find(body)!!.groupValues[1]

        mockMvc.perform(
            post("/api/v1/projects/$projectId/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message": "Please complete this step"}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flowStateChanged").value(true))
            .andExpect(jsonPath("$.currentStep").value("PROBLEM"))
    }

    @Test
    fun `POST chat with blank message returns 400`() {
        val createResult = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Blank Test"}""")
        )
            .andExpect(status().isCreated())
            .andReturn()

        val body = createResult.response.contentAsString
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        val projectId = idRegex.find(body)!!.groupValues[1]

        mockMvc.perform(
            post("/api/v1/projects/$projectId/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message": ""}""")
        )
            .andExpect(status().isBadRequest())
    }
}
