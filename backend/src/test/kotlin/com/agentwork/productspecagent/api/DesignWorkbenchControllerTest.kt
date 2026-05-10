package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.DesignImageAnalysisAgent
import com.agentwork.productspecagent.agent.DesignImageAnalysisInput
import com.agentwork.productspecagent.agent.DesignImageAnalysisResult
import com.agentwork.productspecagent.agent.DesignGenerationInput
import com.agentwork.productspecagent.agent.DesignGenerationResult
import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.DesignColor
import com.agentwork.productspecagent.domain.DesignComponentSignal
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import com.agentwork.productspecagent.domain.DesignLayoutRegion
import com.agentwork.productspecagent.domain.DesignTypographySignal
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.storage.DesignWorkbenchStorage
import com.agentwork.productspecagent.storage.ObjectStore
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
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
                override fun generate(input: DesignGenerationInput): DesignGenerationResult =
                    DesignGenerationResult(
                        analysis = DesignAnalysis(
                            summary = "Generated layout from test input.",
                            visualDirection = "Focused product UI.",
                            rationale = "Matches the submitted material.",
                        ),
                        title = "Controller Test Design",
                        html = """
                            <!doctype html>
                            <html>
                              <head><meta charset="utf-8"><style>body{font-family:system-ui}</style></head>
                              <body><main>${input.description ?: input.image?.originalName}</main></body>
                            </html>
                        """.trimIndent(),
                        rationale = "Generated in controller test.",
                    )
            }

        @Bean
        @Primary
        fun testDesignImageAnalysisAgent(): DesignImageAnalysisAgent =
            object : DesignImageAnalysisAgent(null, null) {
                override fun analyze(input: DesignImageAnalysisInput): DesignImageAnalysisResult =
                    DesignImageAnalysisResult(
                        DesignImageAnalysis(
                            summary = "Controller image analysis",
                            palette = listOf(DesignColor("#111827", "background", "dominant", "Dark shell")),
                            typography = listOf(DesignTypographySignal("ui-sans", "body", "regular", "Clean labels")),
                            layoutHierarchy = listOf(DesignLayoutRegion("Sidebar", 1, 1, "Left navigation")),
                            components = listOf(DesignComponentSignal("Metric card", "summary", "KPI cards")),
                            moodTags = listOf("enterprise"),
                            brandSignals = listOf("blue actions"),
                            designBrief = "Use dark navigation and compact KPI cards.",
                        ),
                    )
            }
    }

    @Autowired private lateinit var ctx: WebApplicationContext
    @Autowired private lateinit var projectService: ProjectService
    @Autowired private lateinit var wizardService: WizardService
    @Autowired private lateinit var designWorkbenchStorage: DesignWorkbenchStorage
    @Autowired private lateinit var objectStore: ObjectStore
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

    private fun putInput(
        description: String? = null,
        file: MockMultipartFile? = null,
    ) = multipart("/api/v1/projects/$projectId/design/input")
        .apply {
            description?.let { param("description", it) }
            file?.let { file(it) }
        }
        .with { request ->
            request.method = "PUT"
            request
        }

    @Test
    fun `GET workbench returns simplified workbench`() {
        mockMvc.perform(get("/api/v1/projects/$projectId/design/workbench"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andExpect(jsonPath("$.description").doesNotExist())
            .andExpect(jsonPath("$.imageInput").doesNotExist())
            .andExpect(jsonPath("$.currentDesign").doesNotExist())
    }

    @Test
    fun `PUT input rejects empty description and missing image`() {
        advanceToDesign(projectId)

        mockMvc.perform(putInput(description = "  "))
            .andExpect(status().isBadRequest())
    }

    @Test
    fun `PUT input accepts description only`() {
        advanceToDesign(projectId)

        mockMvc.perform(putInput(description = "Build a compact pricing page"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Build a compact pricing page"))
            .andExpect(jsonPath("$.imageInput").doesNotExist())
            .andExpect(jsonPath("$.currentDesign").doesNotExist())
    }

    @Test
    fun `PUT input accepts image only`() {
        advanceToDesign(projectId)
        val image = MockMultipartFile("file", "dashboard.png", "image/png", byteArrayOf(1, 2, 3))

        mockMvc.perform(putInput(file = image))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").doesNotExist())
            .andExpect(jsonPath("$.imageInput.originalName").value("dashboard.png"))
            .andExpect(jsonPath("$.imageInput.contentType").value("image/png"))
            .andExpect(jsonPath("$.imageInput.sizeBytes").value(3))
            .andExpect(jsonPath("$.currentDesign").doesNotExist())
    }

    @Test
    fun `PUT input description only preserves previous image`() {
        advanceToDesign(projectId)
        val image = MockMultipartFile("file", "dashboard.png", "image/png", byteArrayOf(1, 2, 3))
        mockMvc.perform(putInput(file = image))
            .andExpect(status().isOk())

        mockMvc.perform(putInput(description = "Switch to text only"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Switch to text only"))
            .andExpect(jsonPath("$.imageInput.originalName").value("dashboard.png"))
    }

    @Test
    fun `PUT input rejects before DESIGN is current`() {
        mockMvc.perform(putInput(description = "Reference notes"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("WIZARD_STEP_NOT_CURRENT"))
    }

    @Test
    fun `POST generate creates current design`() {
        advanceToDesign(projectId)
        mockMvc.perform(putInput(description = "Build a compact pricing page"))
            .andExpect(status().isOk())

        mockMvc.perform(post("/api/v1/projects/$projectId/design/generate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentDesign.title").value("Controller Test Design"))
            .andExpect(jsonPath("$.currentDesign.rationale").value("Generated in controller test."))
            .andExpect(jsonPath("$.analysis.summary").value("Generated layout from test input."))
    }

    @Test
    fun `POST image analyze stores image analysis`() {
        advanceToDesign(projectId)
        val image = MockMultipartFile("file", "dashboard.png", "image/png", byteArrayOf(1, 2, 3))
        mockMvc.perform(putInput(file = image))
            .andExpect(status().isOk())

        mockMvc.perform(post("/api/v1/projects/$projectId/design/image/analyze"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageAnalysis.summary").value("Controller image analysis"))
            .andExpect(jsonPath("$.imageAnalysis.palette[0].hex").value("#111827"))
            .andExpect(jsonPath("$.imageAnalysisError").doesNotExist())
    }

    @Test
    fun `POST image analyze rejects missing image`() {
        advanceToDesign(projectId)
        mockMvc.perform(putInput(description = "Text only"))
            .andExpect(status().isOk())

        mockMvc.perform(post("/api/v1/projects/$projectId/design/image/analyze"))
            .andExpect(status().isBadRequest())
    }

    @Test
    fun `POST generate rejects when DESIGN is hidden`() {
        wizardService.saveStepData(
            projectId,
            FlowStepType.IDEA.name,
            WizardStepData(fields = mapOf("category" to JsonPrimitive("Library"))),
        )

        mockMvc.perform(post("/api/v1/projects/$projectId/design/generate"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("WIZARD_STEP_NOT_VISIBLE"))
    }

    @Test
    fun `GET preview returns active generated html with CSP`() {
        advanceToDesign(projectId)
        mockMvc.perform(putInput(description = "Build a compact pricing page"))
            .andExpect(status().isOk())
        mockMvc.perform(post("/api/v1/projects/$projectId/design/generate"))
            .andExpect(status().isOk())

        mockMvc.perform(get("/api/v1/projects/$projectId/design/preview"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'self' http://localhost:3001")))
            .andExpect(header().string("Content-Security-Policy", containsString("img-src data:")))
            .andExpect(header().string("Content-Security-Policy", containsString("connect-src 'none'")))
            .andExpect(header().string("Content-Security-Policy", containsString("form-action 'none'")))
            .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
            .andExpect(content().string(containsString("Build a compact pricing page")))
    }

    @Test
    fun `GET preview returns not found when no current design exists`() {
        mockMvc.perform(get("/api/v1/projects/$projectId/design/preview"))
            .andExpect(status().isNotFound())
    }

    @Test
    fun `GET preview returns not found when generated html object is missing`() {
        advanceToDesign(projectId)
        mockMvc.perform(putInput(description = "Build a compact pricing page"))
            .andExpect(status().isOk())
        mockMvc.perform(post("/api/v1/projects/$projectId/design/generate"))
            .andExpect(status().isOk())

        objectStore.delete(designWorkbenchStorage.currentDesignKey(projectId))

        mockMvc.perform(get("/api/v1/projects/$projectId/design/preview"))
            .andExpect(status().isNotFound())
    }

    @Test
    fun `POST legacy text input endpoint returns not found`() {
        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/inputs/text")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Reference notes"}"""),
        )
            .andExpect(status().isNotFound())
    }

    @Test
    fun `POST complete rejects before generate`() {
        advanceToDesign(projectId)
        mockMvc.perform(putInput(description = "Build a compact pricing page"))
            .andExpect(status().isOk())

        mockMvc.perform(post("/api/v1/projects/$projectId/design/complete"))
            .andExpect(status().isBadRequest())
    }

    @Test
    fun `POST complete saves current design summary and advances flow`() {
        advanceToDesign(projectId)
        mockMvc.perform(putInput(description = "Build a compact pricing page"))
            .andExpect(status().isOk())
        mockMvc.perform(post("/api/v1/projects/$projectId/design/generate"))
            .andExpect(status().isOk())

        mockMvc.perform(post("/api/v1/projects/$projectId/design/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Design workbench completed. Spec updated."))
            .andExpect(jsonPath("$.nextStep").value("ARCHITECTURE"))

        assertThat(projectService.readSpecFile(projectId, "design.md"))
            .contains("Controller Test Design", "Generated in controller test.")
        assertThat(wizardService.getWizardData(projectId).steps["DESIGN"]?.fields?.get("summary")).isNotNull()
    }
}
