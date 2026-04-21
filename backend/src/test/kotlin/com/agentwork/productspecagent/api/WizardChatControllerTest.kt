package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.IdeaToSpecAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.service.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class WizardChatControllerTest {

    @TestConfiguration
    class TestAgentConfig {
        @Bean
        @Primary
        fun testAgent(
            contextBuilder: SpecContextBuilder,
            projectService: ProjectService,
            @Value("\${agent.system-prompt}") systemPrompt: String,
            decisionService: DecisionService,
            clarificationService: ClarificationService,
            wizardService: WizardService
        ): IdeaToSpecAgent {
            return object : IdeaToSpecAgent(contextBuilder, projectService, systemPrompt, decisionService, clarificationService, wizardService) {
                override suspend fun runAgent(systemPrompt: String, userMessage: String): String {
                    return "Great idea! Let's move on to define the problem."
                }
            }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun createProject(name: String = "Test Project"): String {
        val result = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$name"}""")
        ).andExpect(status().isCreated()).andReturn()

        val body = result.response.contentAsString
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(body)!!.groupValues[1]
    }

    @Test
    fun `POST wizard-step-complete returns agent response with nextStep`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/api/v1/projects/$projectId/agent/wizard-step-complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"step": "IDEA", "fields": {"productName": "MeinTool", "vision": "SaaS tool", "category": "SaaS"}}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Great idea! Let's move on to define the problem."))
            .andExpect(jsonPath("$.nextStep").value("PROBLEM"))
            .andExpect(jsonPath("$.exportTriggered").value(false))
    }

    @Test
    fun `POST wizard-step-complete with empty step returns 400`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/api/v1/projects/$projectId/agent/wizard-step-complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"step": "", "fields": {}}""")
        )
            .andExpect(status().isBadRequest())
    }

    @Test
    fun `POST wizard-step-complete with FRONTEND sets exportTriggered`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/api/v1/projects/$projectId/agent/wizard-step-complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"step": "FRONTEND", "fields": {"framework": "Next.js+React"}}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextStep").isEmpty())
            .andExpect(jsonPath("$.exportTriggered").value(true))
    }
}
