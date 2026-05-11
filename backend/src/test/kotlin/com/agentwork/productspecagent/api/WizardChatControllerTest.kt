package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.FlowStepStatus
import com.agentwork.productspecagent.domain.ProductCategory
import com.agentwork.productspecagent.domain.WizardClientActionDto
import com.agentwork.productspecagent.domain.WizardCreatedArtifacts
import com.agentwork.productspecagent.domain.WizardPrimaryActionDto
import com.agentwork.productspecagent.domain.WizardProgressionView
import com.agentwork.productspecagent.domain.WizardStepView
import com.agentwork.productspecagent.service.CompleteWizardStep
import com.agentwork.productspecagent.service.WizardStepCompletion
import com.agentwork.productspecagent.service.WizardStepCompletionResult
import com.agentwork.productspecagent.service.WizardStepNotVisibleException
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
class WizardChatControllerTest {

    @TestConfiguration
    class TestWizardStepCompletionConfig {
        @Bean
        @Primary
        fun testWizardStepCompletion(): WizardStepCompletion {
            return object : WizardStepCompletion {
                override suspend fun complete(command: CompleteWizardStep): WizardStepCompletionResult {
                    if (command.step == FlowStepType.DESIGN) {
                        throw WizardStepNotVisibleException(command.step, ProductCategory.LIBRARY)
                    }
                    return WizardStepCompletionResult(
                        message = "Great idea! Let's move on to define the problem.",
                        nextStep = if (command.step == FlowStepType.FRONTEND) FlowStepType.REVIEW else FlowStepType.PROBLEM,
                        exportTriggered = false,
                        progression = progressionFor(command.step),
                        action = actionFor(command.step),
                        artifacts = WizardCreatedArtifacts(),
                    )
                }

                private fun progressionFor(step: FlowStepType): WizardProgressionView {
                    val currentStep = if (step == FlowStepType.FRONTEND) FlowStepType.REVIEW else FlowStepType.PROBLEM

                    return WizardProgressionView(
                        category = ProductCategory.SAAS.wireValue,
                        steps = listOf(
                            WizardStepView(
                                step = FlowStepType.IDEA.name,
                                status = FlowStepStatus.COMPLETED.name,
                            ),
                            WizardStepView(
                                step = FlowStepType.PROBLEM.name,
                                status = if (step == FlowStepType.FRONTEND) FlowStepStatus.COMPLETED.name else FlowStepStatus.IN_PROGRESS.name,
                            ),
                            WizardStepView(
                                step = FlowStepType.FRONTEND.name,
                                status = if (step == FlowStepType.FRONTEND) FlowStepStatus.COMPLETED.name else FlowStepStatus.OPEN.name,
                            ),
                            WizardStepView(
                                step = FlowStepType.REVIEW.name,
                                status = if (step == FlowStepType.FRONTEND) FlowStepStatus.IN_PROGRESS.name else FlowStepStatus.OPEN.name,
                                finalVisibleStep = true,
                            ),
                        ),
                        currentStep = currentStep.name,
                        status = "IN_PROGRESS",
                        primaryAction = WizardPrimaryActionDto(type = "COMPLETE_STEP", step = currentStep.name),
                    )
                }

                private fun actionFor(step: FlowStepType): WizardClientActionDto =
                    if (step == FlowStepType.FRONTEND) {
                        WizardClientActionDto(type = "SHOW_STEP", step = FlowStepType.REVIEW.name)
                    } else {
                        WizardClientActionDto(type = "SHOW_STEP", step = FlowStepType.PROBLEM.name)
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
            .andExpect(jsonPath("$.progression.steps").isArray())
            .andExpect(jsonPath("$.progression.currentStep").value("PROBLEM"))
            .andExpect(jsonPath("$.progression.primaryAction.type").value("COMPLETE_STEP"))
            .andExpect(jsonPath("$.progression.primaryAction.step").value("PROBLEM"))
            .andExpect(jsonPath("$.action.type").value("SHOW_STEP"))
            .andExpect(jsonPath("$.action.step").value("PROBLEM"))
            .andExpect(jsonPath("$.artifacts.decisionIds").isArray())
            .andExpect(jsonPath("$.artifacts.clarificationIds").isArray())
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
    fun `POST wizard-step-complete with FRONTEND advances to REVIEW`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/api/v1/projects/$projectId/agent/wizard-step-complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"step": "FRONTEND", "fields": {"framework": "Next.js+React"}}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextStep").value("REVIEW"))
            .andExpect(jsonPath("$.exportTriggered").value(false))
            .andExpect(jsonPath("$.progression.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.progression.currentStep").value("REVIEW"))
            .andExpect(jsonPath("$.progression.primaryAction.type").value("COMPLETE_STEP"))
            .andExpect(jsonPath("$.progression.primaryAction.step").value("REVIEW"))
            .andExpect(jsonPath("$.progression.steps").isArray())
            .andExpect(jsonPath("$.action.type").value("SHOW_STEP"))
            .andExpect(jsonPath("$.action.step").value("REVIEW"))
            .andExpect(jsonPath("$.artifacts.decisionIds").isArray())
            .andExpect(jsonPath("$.artifacts.clarificationIds").isArray())
    }

    @Test
    fun `POST wizard-step-complete maps hidden wizard step to 400`() {
        val projectId = createProject()

        mockMvc.perform(
            post("/api/v1/projects/$projectId/agent/wizard-step-complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"step": "DESIGN", "fields": {"style": "Minimal"}}""")
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("WIZARD_STEP_NOT_VISIBLE"))
            .andExpect(jsonPath("$.message").value("Wizard step DESIGN is not visible for category Library"))
    }
}
