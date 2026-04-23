package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.DecisionAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class DecisionControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun testDecisionAgent(contextBuilder: SpecContextBuilder): DecisionAgent {
            return object : DecisionAgent(contextBuilder) {
                override suspend fun runAgent(prompt: String): String {
                    return """{"options":[{"label":"Option A","pros":["Fast"],"cons":["Risky"],"recommended":true},{"label":"Option B","pros":["Safe"],"cons":["Slow"],"recommended":false}],"recommendation":"Go with A for speed."}"""
                }
            }
        }
    }

    @Autowired lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Decision Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        val body = result.response.contentAsString
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(body)!!.groupValues[1]
    }

    @Test
    fun `POST creates a decision and GET returns it`() {
        val projectId = createProject()

        val createResult = mockMvc.perform(
            post("/api/v1/projects/$projectId/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"MVP scope?","stepType":"FEATURES"}""")
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("MVP scope?"))
            .andExpect(jsonPath("$.options.length()").value(2))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn()

        val decisionId = """"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(createResult.response.contentAsString)!!.groupValues[1]

        mockMvc.perform(get("/api/v1/projects/$projectId/decisions/$decisionId"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(decisionId))
    }

    @Test
    fun `GET list returns all decisions`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/api/v1/projects/$projectId/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Decision 1","stepType":"FEATURES"}""")
        ).andExpect(status().isCreated())

        mockMvc.perform(get("/api/v1/projects/$projectId/decisions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
    }

    @Test
    fun `POST resolve updates decision status`() {
        val projectId = createProject()

        val createResult = mockMvc.perform(
            post("/api/v1/projects/$projectId/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Resolve test","stepType":"MVP"}""")
        ).andExpect(status().isCreated()).andReturn()

        val decisionId = """"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(createResult.response.contentAsString)!!.groupValues[1]

        mockMvc.perform(
            post("/api/v1/projects/$projectId/decisions/$decisionId/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chosenOptionId":"opt-1","rationale":"Best option for speed"}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.chosenOptionId").value("opt-1"))
    }

    @Test
    fun `GET non-existent decision returns 404`() {
        val projectId = createProject()
        mockMvc.perform(get("/api/v1/projects/$projectId/decisions/non-existent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }
}
