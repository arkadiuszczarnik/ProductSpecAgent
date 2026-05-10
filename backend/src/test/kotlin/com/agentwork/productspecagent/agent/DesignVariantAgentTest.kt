package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.DesignColor
import com.agentwork.productspecagent.domain.DesignComponentSignal
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import com.agentwork.productspecagent.domain.DesignLayoutRegion
import com.agentwork.productspecagent.domain.DesignTypographySignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignVariantAgentTest {

    @Test
    fun `generate parses structured agent response`() {
        var capturedPrompt = ""
        val agent = object : DesignVariantAgent(null, null) {
            override suspend fun runAgent(prompt: String): String {
                capturedPrompt = prompt
                return """
                    ```json
                    {
                      "analysis": {
                        "summary": "SaaS dashboard",
                        "visualDirection": "Dense operational UI",
                        "rationale": "Matches the uploaded direction"
                      },
                      "title": "Operations Dashboard",
                      "html": "<!doctype html><html><body><main>Dashboard</main></body></html>",
                      "rationale": "Generated from agent response"
                    }
                    ```
                """.trimIndent()
            }
        }

        val result = agent.generate(
            DesignGenerationInput(
                projectId = "p1",
                description = "Build an operations dashboard",
                image = null,
                imageAnalysis = null,
            ),
        )

        assertTrue(capturedPrompt.contains("Build an operations dashboard"))
        assertEquals("SaaS dashboard", result.analysis.summary)
        assertEquals("Dense operational UI", result.analysis.visualDirection)
        assertEquals("Operations Dashboard", result.title)
        assertTrue(result.html.contains("Dashboard"))
    }

    @Test
    fun `generate falls back when no runner is configured`() {
        val result = DesignVariantAgent(null, null).generate(
            DesignGenerationInput(
                projectId = "p1",
                description = "<strong>x</strong>",
                image = null,
                imageAnalysis = null,
            ),
        )

        assertEquals(
            DesignAnalysis(
                summary = "Generated layout from description.",
                visualDirection = "Clean product interface with clear hierarchy.",
                rationale = "Fallback generation keeps local development deterministic.",
            ),
            result.analysis,
        )
        assertTrue(result.html.contains("&lt;strong&gt;x&lt;/strong&gt;"))
    }

    @Test
    fun `generate prompt includes image analysis brief and JSON`() {
        var capturedPrompt = ""
        val agent = object : DesignVariantAgent(null, null) {
            override suspend fun runAgent(prompt: String): String {
                capturedPrompt = prompt
                return """
                    {
                      "analysis": {
                        "summary": "SaaS dashboard",
                        "visualDirection": "Dense operational UI",
                        "rationale": "Uses visual analysis"
                      },
                      "title": "Operations Dashboard",
                      "html": "<!doctype html><html><body><main>Dashboard</main></body></html>",
                      "rationale": "Generated from image analysis"
                    }
                """.trimIndent()
            }
        }

        agent.generate(
            DesignGenerationInput(
                projectId = "p1",
                description = "Build an operations dashboard",
                image = null,
                imageAnalysis = imageAnalysis(),
            ),
        )

        assertTrue(capturedPrompt.contains("Use dark navigation and compact KPI cards."))
        assertTrue(capturedPrompt.contains("\"palette\""))
    }

    private fun imageAnalysis() = DesignImageAnalysis(
        summary = "Dashboard image",
        palette = listOf(DesignColor("#111827", "background", "dominant", "Dark shell")),
        typography = listOf(DesignTypographySignal("ui-sans", "body", "regular", "Clean labels")),
        layoutHierarchy = listOf(DesignLayoutRegion("Sidebar", 1, 1, "Left navigation")),
        components = listOf(DesignComponentSignal("Metric card", "summary", "KPI cards")),
        moodTags = listOf("enterprise"),
        brandSignals = listOf("blue actions"),
        designBrief = "Use dark navigation and compact KPI cards.",
    )
}
