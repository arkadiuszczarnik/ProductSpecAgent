package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.agent.GeneratedDesignVariant
import com.agentwork.productspecagent.domain.DesignInputCategory
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardService
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
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
    @Autowired private lateinit var wizardService: WizardService
    private lateinit var mockMvc: MockMvc
    private lateinit var projectId: String

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build()
        projectId = createProject("Design Workbench Controller Test")
    }

    private fun createProject(name: String): String = projectService.createProject(name).project.id

    private fun inputId(): String =
        """"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(
                mockMvc.perform(get("/api/v1/projects/$projectId/design/workbench"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .response
                    .contentAsString,
            )!!
            .groupValues[1]

    private fun advanceToDesign(projectId: String) {
        listOf(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
        ).forEach { step -> projectService.advanceStep(projectId, step) }
    }

    private fun createVariant(prompt: String = "valid-preview"): String {
        advanceToDesign(projectId)

        mockMvc.perform(post("/api/v1/projects/$projectId/design/screens/propose"))
            .andExpect(status().isOk())

        val result = mockMvc.perform(
            post("/api/v1/projects/$projectId/design/screens/landing/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"prompt":"$prompt"}"""),
        )
            .andExpect(status().isOk())
            .andReturn()

        return """"variants"\s*:\s*\[\s*\{[^}]*"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(result.response.contentAsString)!!
            .groupValues[1]
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
        advanceToDesign(projectId)

        mockMvc.perform(post("/api/v1/projects/$projectId/design/screens/propose"))
            .andExpect(status().isOk())

        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/screens/landing/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"prompt":"invalid-preview"}"""),
        )
            .andExpect(status().isBadRequest())
    }

    @Test
    fun `POST text input rejects before DESIGN is current`() {
        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/inputs/text")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Reference notes"}"""),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("WIZARD_STEP_NOT_CURRENT"))
    }

    @Test
    fun `POST image input adds image input`() {
        advanceToDesign(projectId)
        val image = MockMultipartFile("file", "dashboard.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc.perform(multipart("/api/v1/projects/$projectId/design/inputs/image").file(image))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inputs[0].kind").value("IMAGE"))
            .andExpect(jsonPath("$.inputs[0].originalName").value("dashboard.png"))
    }

    @Test
    fun `POST snippet input adds html css snippet input`() {
        advanceToDesign(projectId)

        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/inputs/snippet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"pricing.html","snippet":"<style>.hero{color:red}</style><section>Pricing</section>"}"""),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inputs[0].kind").value("HTML_CSS_SNIPPET"))
            .andExpect(jsonPath("$.inputs[0].originalName").value("pricing.html"))
    }

    @Test
    fun `PATCH input updates user label and classification`() {
        advanceToDesign(projectId)
        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/inputs/snippet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"snippet":"<button>Buy</button>"}"""),
        )
            .andExpect(status().isOk())

        mockMvc.perform(
            patch("/api/v1/projects/$projectId/design/inputs/{inputId}", inputId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userLabel":"Checkout reference",
                      "category":"HTML_CSS_REFERENCE",
                      "summary":"Compact checkout section",
                      "suggestedUse":"Use button density",
                      "confidence":0.9
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inputs[0].userLabel").value("Checkout reference"))
            .andExpect(jsonPath("$.inputs[0].classification.category").value(DesignInputCategory.HTML_CSS_REFERENCE.name))
            .andExpect(jsonPath("$.inputs[0].classification.summary").value("Compact checkout section"))
            .andExpect(jsonPath("$.inputs[0].classification.suggestedUse").value("Use button density"))
            .andExpect(jsonPath("$.inputs[0].classification.confidence").value(0.9))
    }

    @Test
    fun `manual screen add update and delete works`() {
        advanceToDesign(projectId)

        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/screens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Settings","purpose":"Manage account preferences"}"""),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.screens[0].id").value("settings"))
            .andExpect(jsonPath("$.screens[0].name").value("Settings"))

        mockMvc.perform(
            patch("/api/v1/projects/$projectId/design/screens/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Account Settings","purpose":"Tune workspace preferences"}"""),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.screens[0].name").value("Account Settings"))
            .andExpect(jsonPath("$.screens[0].purpose").value("Tune workspace preferences"))

        mockMvc.perform(delete("/api/v1/projects/$projectId/design/screens/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.screens").isEmpty)
    }

    @Test
    fun `POST image input rejects before DESIGN is current`() {
        val image = MockMultipartFile("file", "dashboard.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc.perform(multipart("/api/v1/projects/$projectId/design/inputs/image").file(image))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("WIZARD_STEP_NOT_CURRENT"))
    }

    @Test
    fun `POST analyze rejects when DESIGN is hidden`() {
        wizardService.saveStepData(
            projectId,
            FlowStepType.IDEA.name,
            WizardStepData(fields = mapOf("category" to JsonPrimitive("Library"))),
        )

        mockMvc.perform(post("/api/v1/projects/$projectId/design/analyze"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("WIZARD_STEP_NOT_VISIBLE"))
    }

    @Test
    fun `GET preview returns html with security headers`() {
        val variantId = createVariant()

        mockMvc.perform(get("/api/v1/projects/$projectId/design/preview/$variantId"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'self' http://localhost:3001")))
            .andExpect(content().string(containsString("valid-preview")))
    }

    @Test
    fun `GET preview returns not found for unknown variant`() {
        mockMvc.perform(get("/api/v1/projects/$projectId/design/preview/missing"))
            .andExpect(status().isNotFound())
    }

    @Test
    fun `POST complete saves active design summary and advances flow`() {
        val variantId = createVariant()
        mockMvc.perform(
            patch("/api/v1/projects/$projectId/design/screens/landing/active-variant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"variantId":"$variantId"}"""),
        )
            .andExpect(status().isOk())

        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextStep").value("ARCHITECTURE"))

        assertThat(projectService.readSpecFile(projectId, "design.md")).contains("Landing", "Initial")
        assertThat(wizardService.getWizardData(projectId).steps["DESIGN"]?.fields?.get("summary")).isNotNull()
    }
}
