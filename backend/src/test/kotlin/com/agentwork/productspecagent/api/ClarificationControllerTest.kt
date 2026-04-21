package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ClarificationService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class ClarificationControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var clarificationService: ClarificationService

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Clarification Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `GET list returns empty initially`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/clarifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `create and get clarification`() {
        val pid = createProject()
        val c = clarificationService.createClarification(pid, "How handle offline?", "Contradicting reqs", FlowStepType.SCOPE)

        mockMvc.perform(get("/api/v1/projects/$pid/clarifications/${c.id}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.question").value("How handle offline?"))
            .andExpect(jsonPath("$.status").value("OPEN"))
    }

    @Test
    fun `answer clarification updates status`() {
        val pid = createProject()
        val c = clarificationService.createClarification(pid, "Question?", "Reason", FlowStepType.MVP)

        mockMvc.perform(
            post("/api/v1/projects/$pid/clarifications/${c.id}/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"answer":"We go with online-first only."}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANSWERED"))
            .andExpect(jsonPath("$.answer").value("We go with online-first only."))
    }

    @Test
    fun `GET non-existent clarification returns 404`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/clarifications/non-existent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }
}
