package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignImageInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesignImageAnalysisAgentTest {

    @Test
    fun `analyze falls back to metadata-only analysis without runner`() {
        val result = DesignImageAnalysisAgent(null, null).analyze(sampleInput())

        assertEquals("design-image-analysis", DesignImageAnalysisAgent.AGENT_ID)
        assertTrue(result.analysis.summary.contains("dashboard.png"))
        assertTrue(result.analysis.summary.contains("metadata"))
        assertTrue(result.analysis.designBrief.contains("does not inspect pixels"))
        assertEquals(listOf("metadata-only", "reference-image"), result.analysis.moodTags)
    }

    @Test
    fun `analyze parses and normalizes structured image response`() {
        var capturedPrompt = ""
        var capturedBytes = byteArrayOf()
        var capturedContentType = ""
        var capturedFileName = ""
        val agent = object : DesignImageAnalysisAgent(null, null) {
            override suspend fun runImageAgent(
                systemPrompt: String,
                prompt: String,
                imageBytes: ByteArray,
                contentType: String,
                fileName: String,
            ): String {
                capturedPrompt = prompt
                capturedBytes = imageBytes
                capturedContentType = contentType
                capturedFileName = fileName
                return """
                    ```json
                    {
                      "analysis": {
                        "summary": "  Dense SaaS dashboard  ",
                        "palette": [
                          {"hex":"#a1b2c3","role":" Primary ","weight":" dominant ","notes":" Blue action "},
                          {"hex":"not-a-color","role":" Bad ","weight":" accent ","notes":" Invalid color "}
                        ],
                        "typography": [
                          {"category":" ui-sans ","role":" body ","weight":" regular ","notes":" Clean text "}
                        ],
                        "layoutHierarchy": [
                          {"name":" Sidebar ","order":1,"priority":1,"description":" Navigation "}
                        ],
                        "components": [
                          {"name":" Card ","role":" summary ","description":" Metrics "}
                        ],
                        "moodTags": ["Calm", " calm ", "Enterprise", ""],
                        "brandSignals": [" Rounded cards ", " Blue actions "],
                        "designBrief": "   "
                      }
                    }
                    ```
                """.trimIndent()
            }
        }

        val result = agent.analyze(sampleInput())

        assertTrue(capturedPrompt.contains("dashboard.png"))
        assertEquals(listOf<Byte>(1, 2, 3), capturedBytes.toList())
        assertEquals("image/png", capturedContentType)
        assertEquals("dashboard.png", capturedFileName)
        assertEquals("Dense SaaS dashboard", result.analysis.summary)
        assertEquals("#A1B2C3", result.analysis.palette[0].hex)
        assertEquals("#000000", result.analysis.palette[1].hex)
        assertEquals("Primary", result.analysis.palette[0].role)
        assertEquals("ui-sans", result.analysis.typography.single().category)
        assertEquals("Sidebar", result.analysis.layoutHierarchy.single().name)
        assertEquals("Card", result.analysis.components.single().name)
        assertEquals(listOf("calm", "enterprise"), result.analysis.moodTags)
        assertEquals(listOf("Rounded cards", "Blue actions"), result.analysis.brandSignals)
        assertEquals("Dense SaaS dashboard", result.analysis.designBrief)
    }

    @Test
    fun `analyze throws typed exception for invalid agent JSON`() {
        val agent = object : DesignImageAnalysisAgent(null, null) {
            override suspend fun runImageAgent(
                systemPrompt: String,
                prompt: String,
                imageBytes: ByteArray,
                contentType: String,
                fileName: String,
            ): String = "not json"
        }

        assertFailsWith<InvalidDesignImageAnalysisException> {
            agent.analyze(sampleInput())
        }
    }

    private fun sampleInput() = DesignImageAnalysisInput(
        projectId = "p1",
        image = DesignImageInput(
            originalName = "dashboard.png",
            contentRef = "design/p1/input/dashboard.png",
            contentType = "image/png",
            sizeBytes = 3,
            uploadedAt = "2026-05-10T12:00:00Z",
        ),
        imageBytes = byteArrayOf(1, 2, 3),
    )
}
