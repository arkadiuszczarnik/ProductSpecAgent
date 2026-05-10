package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignAnalysis
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
}
