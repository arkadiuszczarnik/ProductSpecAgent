package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.agent.GeneratedDesignVariant
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.service.ProjectService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class DesignWorkbenchControllerTest {

    @TestConfiguration
    class TestAgentConfig {
        @Bean
        @Primary
        fun testDesignVariantAgent(): DesignVariantAgent =
            object : DesignVariantAgent(null) {
                override fun generate(projectId: String, screenId: String, prompt: String?): GeneratedDesignVariant {
                    if (prompt == "invalid-preview") {
                        return GeneratedDesignVariant(
                            title = "Invalid",
                            html = """<!doctype html><html><body><img src="https://example.com/x.png"></body></html>""",
                            rationale = "Invalid remote image.",
                        )
                    }
                    return super.generate(projectId, screenId, prompt)
                }
            }
    }

    @Autowired private lateinit var ctx: WebApplicationContext
    @Autowired private lateinit var projectService: ProjectService
    private lateinit var mockMvc: MockMvc
    private lateinit var projectId: String

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build()
        projectId = createProject("Design Workbench Controller Test")
    }

    private fun createProject(name: String): String = projectService.createProject(name).project.id

    private fun advanceToDesign(projectId: String) {
        listOf(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
        ).forEach { step -> projectService.advanceStep(projectId, step) }
    }

    @Test
    fun `GET workbench returns empty workbench`() {
        mockMvc.perform(get("/api/v1/projects/$projectId/design/workbench"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andExpect(jsonPath("$.inputs").isArray)
    }

    @Test
    fun `POST complete rejects workbench without active variant`() {
        advanceToDesign(projectId)

        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest())
    }

    @Test
    fun `GET old design endpoint no longer exposes zip upload as primary state`() {
        mockMvc.perform(get("/api/v1/projects/$projectId/design"))
            .andExpect(status().isNotFound())
    }

    @Test
    fun `POST variant returns bad request when generated preview is invalid`() {
        mockMvc.perform(post("/api/v1/projects/$projectId/design/screens/propose"))
            .andExpect(status().isOk())

        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/screens/landing/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"prompt":"invalid-preview"}"""),
        )
            .andExpect(status().isBadRequest())
    }
}
