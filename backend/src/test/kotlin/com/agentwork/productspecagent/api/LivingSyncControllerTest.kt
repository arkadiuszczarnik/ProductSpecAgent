package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class LivingSyncControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Living Sync API"}"""),
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `GET living sync returns empty summary`() {
        val projectId = createProject()

        mockMvc.perform(get("/api/v1/projects/$projectId/living-sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.tests.totalRuns").value(0))
            .andExpect(jsonPath("$.tokens.totalTokens").value(0))
    }

    @Test
    fun `report feature progress stores event visible in summary`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/api/v1/projects/$projectId/living-sync/mcp/report-feature-progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"featureId":"feature-1","status":"DONE","summary":"Implemented"}"""),
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("FEATURE_PROGRESS"))

        mockMvc.perform(get("/api/v1/projects/$projectId/living-sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.features[0].featureId").value("feature-1"))
            .andExpect(jsonPath("$.features[0].status").value("DONE"))
    }

    @Test
    fun `report test tokens code changes and notes update summary`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/api/v1/projects/$projectId/living-sync/mcp/report-test-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"command":"npm test","status":"passed","summary":"ok","passed":5,"failed":0}"""),
        ).andExpect(status().isOk())

        mockMvc.perform(
            post("/api/v1/projects/$projectId/living-sync/mcp/report-token-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"agentName":"codex","model":"gpt-5.4","inputTokens":10,"outputTokens":4,"totalTokens":14}"""),
        ).andExpect(status().isOk())

        mockMvc.perform(
            post("/api/v1/projects/$projectId/living-sync/mcp/report-code-changes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"summary":"changed","files":["src/app.ts"],"commits":["abc123"]}"""),
        ).andExpect(status().isOk())

        mockMvc.perform(
            post("/api/v1/projects/$projectId/living-sync/mcp/report-sync-note")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"severity":"WARNING","message":"Needs review","suggestedAction":"Check auth"}"""),
        ).andExpect(status().isOk())

        mockMvc.perform(get("/api/v1/projects/$projectId/living-sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tests.totalRuns").value(1))
            .andExpect(jsonPath("$.tests.passed").value(5))
            .andExpect(jsonPath("$.tokens.totalTokens").value(14))
            .andExpect(jsonPath("$.changedFiles[0]").value("src/app.ts"))
            .andExpect(jsonPath("$.commits[0]").value("abc123"))
            .andExpect(jsonPath("$.notes[0].summary").value("Needs review Suggested action: Check auth"))
    }
}
