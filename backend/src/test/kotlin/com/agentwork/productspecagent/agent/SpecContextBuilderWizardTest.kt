package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SpecContextBuilderWizardTest {

    @Autowired
    lateinit var contextBuilder: SpecContextBuilder

    @Test
    fun `buildWizardContext includes current step fields`() {
        val fields = mapOf(
            "productName" to "MeinTool",
            "vision" to "Ein SaaS fuer PM",
            "category" to "SaaS"
        )
        val context = contextBuilder.buildWizardContext(
            wizardData = WizardData(
                projectId = "test",
                steps = mapOf(
                    "IDEA" to WizardStepData(
                        fields = fields.mapValues { JsonPrimitive(it.value) },
                        completedAt = "2026-03-31T10:00:00Z"
                    )
                )
            ),
            currentStep = "PROBLEM",
            currentFields = mapOf("coreProblem" to "Zu viel manueller Aufwand")
        )

        assertTrue(context.contains("MeinTool"))
        assertTrue(context.contains("Ein SaaS fuer PM"))
        assertTrue(context.contains("Zu viel manueller Aufwand"))
        assertTrue(context.contains("CURRENT STEP: PROBLEM"))
    }

    @Test
    fun `buildWizardContext with empty wizard data works`() {
        val context = contextBuilder.buildWizardContext(
            wizardData = WizardData(projectId = "test"),
            currentStep = "IDEA",
            currentFields = mapOf("productName" to "Test")
        )

        assertTrue(context.contains("CURRENT STEP: IDEA"))
        assertTrue(context.contains("Test"))
    }

    @Test
    fun `buildWizardContext includes graph block on FEATURES step`() {
        val fields = mapOf(
            "features" to listOf(
                mapOf(
                    "id" to "f-1",
                    "title" to "Login",
                    "scopes" to listOf("BACKEND"),
                    "scopeFields" to mapOf("apiEndpoints" to "POST /auth/login")
                )
            )
        )
        val context = contextBuilder.buildWizardContext(
            wizardData = WizardData(
                projectId = "test",
                steps = mapOf(
                    "IDEA" to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive("SaaS")),
                        completedAt = "2026-03-31T10:00:00Z"
                    )
                )
            ),
            currentStep = "FEATURES",
            currentFields = fields,
        )

        assertThat(context).contains("Features & Dependencies")
    }

    @Test
    fun `buildWizardContext omits graph block on non-FEATURES step`() {
        val fields = mapOf(
            "features" to listOf(mapOf("title" to "X"))
        )
        val context = contextBuilder.buildWizardContext(
            wizardData = WizardData(projectId = "test"),
            currentStep = "PROBLEM",
            currentFields = fields,
        )

        assertThat(context).doesNotContain("Features & Dependencies")
    }
}
