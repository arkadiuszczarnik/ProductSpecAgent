package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import kotlinx.serialization.json.JsonPrimitive
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
}
