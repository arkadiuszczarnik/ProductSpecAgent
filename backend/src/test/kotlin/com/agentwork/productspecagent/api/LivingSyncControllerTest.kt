package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.FeatureDoneImportAgent
import com.agentwork.productspecagent.agent.FeatureDoneImportHeaderCheck
import com.agentwork.productspecagent.agent.FeatureDoneImportResult
import com.agentwork.productspecagent.domain.FeatureCompletionTestEvidence
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class LivingSyncControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var featureDoneImportAgent: FeatureDoneImportAgent

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
            .andExpect(jsonPath("$.featureCompletions").isArray())
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
            post("/api/v1/projects/$projectId/living-sync/mcp/report-code-changes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"summary":"changed without commits","files":["src/other.ts"],"commits":null}"""),
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

    @Test
    fun `report done markdown import stores completion snapshot and projects feature summary`() {
        val projectId = createProject()
        runBlocking {
            whenever(featureDoneImportAgent.importDoneReport(eq(projectId), eq("feature-1"), eq("feature-1-done.md"), any()))
                .thenReturn(
                    FeatureDoneImportResult(
                        featureId = "feature-1",
                        headerCheck = FeatureDoneImportHeaderCheck(
                            matchesExpectedFeature = true,
                            reportedFeatureLabel = "Feature 1",
                        ),
                        derivedStatus = com.agentwork.productspecagent.domain.LivingSyncFeatureStatus.DONE,
                        summary = "Imported and tested.",
                        implementedItems = listOf("REST import"),
                        tests = listOf(FeatureCompletionTestEvidence(name = "LivingSyncControllerTest", status = "PRESENT")),
                    ),
                )
        }

        mockMvc.perform(
            put("/api/v1/projects/$projectId/wizard/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "projectId": "$projectId",
                      "steps": {
                        "FEATURES": {
                          "fields": {
                            "features": [
                              {
                                "id": "feature-1",
                                "title": "Living Sync via MCP",
                                "description": "Import done reports",
                                "scopes": ["BACKEND"]
                              }
                            ]
                          }
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk())

        mockMvc.perform(
            post("/api/v1/projects/$projectId/living-sync/mcp/import-feature-done-markdown")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "featureId": "feature-1",
                      "fileName": "feature-1-done.md",
                      "markdown": "# Feature 1\nImplemented and tested.",
                      "agentName": "codex"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("FEATURE_DONE_IMPORT"))
            .andExpect(jsonPath("$.featureId").value("feature-1"))
            .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Imported feature-1-done.md:")))

        mockMvc.perform(get("/api/v1/projects/$projectId/living-sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.featureCompletions[0].featureId").value("feature-1"))
            .andExpect(jsonPath("$.featureCompletions[0].derivedStatus").value("DONE"))
            .andExpect(jsonPath("$.featureCompletions[0].sourceFileName").value("feature-1-done.md"))
            .andExpect(jsonPath("$.features[0].featureId").value("feature-1"))
            .andExpect(jsonPath("$.features[0].status").value("DONE"))
            .andExpect(jsonPath("$.features[0].summary").isNotEmpty)
    }
}
