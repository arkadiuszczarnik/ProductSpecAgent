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
class LivingSyncMcpControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var featureDoneImportAgent: FeatureDoneImportAgent

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"MCP Sync"}"""),
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `tools list exposes living sync tools`() {
        mockMvc.perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jsonrpc").value("2.0"))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.result.tools[0].name").value("get_project_sync_context"))
            .andExpect(jsonPath("$.result.tools[1].name").value("report_feature_progress"))
            .andExpect(jsonPath("$.result.tools[6].name").value("import_feature_done_markdown"))
    }

    @Test
    fun `tools call report feature progress updates project summary`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": "call-1",
                      "method": "tools/call",
                      "params": {
                        "name": "report_feature_progress",
                        "arguments": {
                          "projectId": "$projectId",
                          "featureId": "feature-1",
                          "status": "DONE",
                          "summary": "Done through MCP"
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jsonrpc").value("2.0"))
            .andExpect(jsonPath("$.id").value("call-1"))
            .andExpect(jsonPath("$.result.content[0].type").value("text"))

        mockMvc.perform(get("/api/v1/projects/$projectId/living-sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.features[0].featureId").value("feature-1"))
            .andExpect(jsonPath("$.features[0].status").value("DONE"))
    }

    @Test
    fun `tools call get project sync context returns summary content`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/call",
                      "params": {
                        "name": "get_project_sync_context",
                        "arguments": { "projectId": "$projectId" }
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.content[0].text").isNotEmpty)
    }

    @Test
    fun `tools call import feature done markdown stores snapshot-backed summary`() {
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
                        implementedItems = listOf("MCP import"),
                        tests = listOf(FeatureCompletionTestEvidence(name = "LivingSyncMcpControllerTest", status = "PRESENT")),
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
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": "call-import",
                      "method": "tools/call",
                      "params": {
                        "name": "import_feature_done_markdown",
                        "arguments": {
                          "projectId": "$projectId",
                          "featureId": "feature-1",
                          "fileName": "feature-1-done.md",
                          "markdown": "# Feature 1\nImplemented and tested.",
                          "agentName": "codex"
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jsonrpc").value("2.0"))
            .andExpect(jsonPath("$.id").value("call-import"))
            .andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("Imported feature-1-done.md:")))

        mockMvc.perform(get("/api/v1/projects/$projectId/living-sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.featureCompletions[0].featureId").value("feature-1"))
            .andExpect(jsonPath("$.featureCompletions[0].derivedStatus").value("DONE"))
            .andExpect(jsonPath("$.features[0].featureId").value("feature-1"))
            .andExpect(jsonPath("$.features[0].status").value("DONE"))
    }
}
