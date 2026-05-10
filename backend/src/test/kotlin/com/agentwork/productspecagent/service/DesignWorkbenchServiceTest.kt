package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DesignGenerationInput
import com.agentwork.productspecagent.agent.DesignGenerationResult
import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import com.agentwork.productspecagent.storage.DesignWorkbenchStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignWorkbenchServiceTest {
    private val objectStore = InMemoryObjectStore()
    private val storage = DesignWorkbenchStorage(objectStore)
    private val service = service()

    @Test
    fun `save input rejects missing description and image`() {
        assertFailsWith<InvalidDesignWorkbenchException> {
            service.saveInput("p1", " ", null, null, null)
        }
    }

    @Test
    fun `save input accepts description only`() {
        val workbench = service.saveInput("p1", "A calm SaaS dashboard", null, null, null)

        assertEquals("A calm SaaS dashboard", workbench.description)
        assertNull(workbench.imageInput)
    }

    @Test
    fun `save input accepts image only`() {
        val workbench = service.saveInput("p1", null, "dash.png", byteArrayOf(1, 2), "image/png")

        assertEquals("dash.png", workbench.imageInput?.originalName)
        assertNull(workbench.description)
    }

    @Test
    fun `description only input preserves previous image and image analysis`() {
        service.saveInput("p1", null, "dash.png", byteArrayOf(1, 2), "image/png")
        storage.saveImageAnalysis(
            "p1",
            DesignImageAnalysis(
                summary = "Dashboard reference",
                palette = emptyList(),
                typography = emptyList(),
                layoutHierarchy = emptyList(),
                components = emptyList(),
                moodTags = emptyList(),
                brandSignals = emptyList(),
                designBrief = "Use the uploaded dashboard reference.",
            ),
        )

        val workbench = service.saveInput("p1", "Use text only", null, null, null)

        assertEquals("Use text only", workbench.description)
        assertEquals("dash.png", workbench.imageInput?.originalName)
        assertEquals("Dashboard reference", workbench.imageAnalysis?.summary)
    }

    @Test
    fun `generate creates current design from combined input`() {
        val service = service(
            designVariantAgent = object : DesignVariantAgent(null) {
                override fun generate(input: DesignGenerationInput): DesignGenerationResult =
                    DesignGenerationResult(
                        analysis = DesignAnalysis("Dashboard", "Dense operations UI", "Uses both inputs"),
                        title = "Dashboard",
                        html = validHtml("Dashboard"),
                        rationale = "Generated from description and image.",
                    )
            },
        )
        service.saveInput("p1", "Build dashboard", "dash.png", byteArrayOf(1), "image/png")

        val workbench = service.generate("p1")

        assertEquals("Dashboard", workbench.analysis?.summary)
        assertEquals("Dashboard", workbench.currentDesign?.title)
    }

    @Test
    fun `regenerate replaces current design`() {
        service.saveInput("p1", "Build dashboard", null, null, null)

        val first = service.generate("p1").currentDesign?.id
        val second = service.generate("p1").currentDesign?.id

        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first != second)
    }

    @Test
    fun `invalid generated html preserves previous current design`() {
        var invalid = false
        val service = service(
            designVariantAgent = object : DesignVariantAgent(null) {
                override fun generate(input: DesignGenerationInput): DesignGenerationResult =
                    DesignGenerationResult(
                        analysis = DesignAnalysis("Summary", "Direction", "Rationale"),
                        title = "Generated",
                        html = if (invalid) {
                            """<!doctype html><html><body><img src="https://example.com/x.png"></body></html>"""
                        } else {
                            validHtml("Safe")
                        },
                        rationale = "Result",
                    )
            },
        )
        service.saveInput("p1", "Build page", null, null, null)
        val previous = service.generate("p1").currentDesign
        invalid = true

        assertFailsWith<InvalidDesignPreviewException> {
            service.generate("p1")
        }

        assertEquals(previous, service.get("p1").currentDesign)
    }

    @Test
    fun `complete rejects missing generated design`() {
        service.saveInput("p1", "Build page", null, null, null)

        assertFailsWith<InvalidDesignWorkbenchException> {
            service.complete("p1")
        }
    }

    @Test
    fun `complete writes generated design to active output`() {
        service.saveInput("p1", "Build page", null, null, null)
        service.generate("p1")

        service.complete("p1")

        assertContentEquals(service.readPreview("p1"), objectStore.get(storage.activeScreenKey("p1", "design")))
    }

    @Test
    fun `read preview converts missing current design html to workbench exception`() {
        service.saveInput("p1", "Build page", null, null, null)
        service.generate("p1")
        objectStore.delete(storage.currentDesignKey("p1"))

        assertFailsWith<InvalidDesignWorkbenchException> {
            service.readPreview("p1")
        }
    }

    @Test
    fun `complete converts missing current design html to workbench exception`() {
        service.saveInput("p1", "Build page", null, null, null)
        service.generate("p1")
        objectStore.delete(storage.currentDesignKey("p1"))

        assertFailsWith<InvalidDesignWorkbenchException> {
            service.complete("p1")
        }
    }

    @Test
    fun `fallback generation escapes user text`() {
        val result = DesignVariantAgent(null).generate(
            DesignGenerationInput(
                projectId = "p1",
                description = "<strong>x</strong>",
                image = null,
            ),
        )

        assertTrue(result.html.contains("&lt;strong&gt;x&lt;/strong&gt;"))
        assertFalse(result.html.contains("<strong>x</strong>"))
    }

    private fun service(
        designVariantAgent: DesignVariantAgent = object : DesignVariantAgent(null) {
            override fun generate(input: DesignGenerationInput): DesignGenerationResult =
                DesignGenerationResult(
                    analysis = DesignAnalysis("Initial", "Clean", "Fallback"),
                    title = "Initial",
                    html = validHtml("Initial"),
                    rationale = "Initial design",
                )
        },
    ) = DesignWorkbenchService(
        storage = storage,
        previewValidator = DesignPreviewValidator(),
        designVariantAgent = designVariantAgent,
    )

    private fun validHtml(title: String) =
        "<!doctype html><html><head><meta charset=\"utf-8\"></head><body><main><h1>$title</h1></main></body></html>"
}
