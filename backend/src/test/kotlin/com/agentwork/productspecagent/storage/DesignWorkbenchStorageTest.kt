package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.DesignColor
import com.agentwork.productspecagent.domain.DesignComponentSignal
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import com.agentwork.productspecagent.domain.DesignLayoutRegion
import com.agentwork.productspecagent.domain.DesignTypographySignal
import com.agentwork.productspecagent.domain.GeneratedDesign
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignWorkbenchStorageTest {
    private val objectStore = InMemoryObjectStore()
    private val storage = DesignWorkbenchStorage(objectStore)

    private fun sampleImageAnalysis() = DesignImageAnalysis(
        summary = "Dense SaaS dashboard with a calm operational layout.",
        palette = listOf(
            DesignColor("#111827", "background", "dominant", "Dark shell"),
            DesignColor("#2F80ED", "primary-action", "accent", "Blue buttons"),
        ),
        typography = listOf(
            DesignTypographySignal("ui-sans", "body", "regular", "Clean interface text"),
        ),
        layoutHierarchy = listOf(
            DesignLayoutRegion("Sidebar", 1, 1, "Primary navigation"),
            DesignLayoutRegion("KPI row", 2, 2, "Top metrics"),
        ),
        components = listOf(
            DesignComponentSignal("Card", "summary", "Metric cards"),
        ),
        moodTags = listOf("enterprise", "calm"),
        brandSignals = listOf("rounded cards", "blue action color"),
        designBrief = "Create a calm enterprise dashboard with dark navigation and blue actions.",
    )

    @Test
    fun `save input replaces description and clears current design`() {
        storage.saveInput("p1", "First", null)
        val generated = GeneratedDesign(
            id = "design-1",
            title = "Landing",
            htmlPath = storage.currentDesignKey("p1"),
            rationale = "Initial",
            createdAt = "now",
        )
        storage.saveGeneratedDesign(
            projectId = "p1",
            analysis = DesignAnalysis("Summary", "Clean", "Because"),
            generated = generated,
            html = "<!doctype html><html><body>First</body></html>".toByteArray(),
        )

        val updated = storage.saveInput("p1", "Second", null)

        assertEquals("Second", updated.description)
        assertNull(updated.currentDesign)
        assertNull(updated.analysis)
    }

    @Test
    fun `save input trims blank descriptions to null`() {
        val updated = storage.saveInput("p1", "   ", null)

        assertNull(updated.description)
    }

    @Test
    fun `save input preserves existing image when no replacement image is supplied`() {
        storage.saveImageInput(
            projectId = "p1",
            originalName = "reference.png",
            bytes = byteArrayOf(1, 2, 3),
            contentType = "image/png",
        )

        val updated = storage.saveInput("p1", "Use the same reference", null)

        assertEquals("reference.png", updated.imageInput?.originalName)
    }

    @Test
    fun `save image input records metadata`() {
        val image = storage.saveImageInput(
            projectId = "p1",
            originalName = "reference.png",
            bytes = byteArrayOf(1, 2, 3),
            contentType = "image/png",
        )

        assertEquals("reference.png", image.imageInput?.originalName)
        assertEquals("image/png", image.imageInput?.contentType)
        assertEquals(3, image.imageInput?.sizeBytes)
    }

    @Test
    fun `save image input preserves existing description`() {
        storage.saveInput("p1", "Keep this direction", null)

        val image = storage.saveImageInput(
            projectId = "p1",
            originalName = "reference.png",
            bytes = byteArrayOf(1, 2, 3),
            contentType = "image/png",
        )

        assertEquals("Keep this direction", image.description)
    }

    @Test
    fun `save generated design writes active html`() {
        storage.saveInput("p1", "Build a pricing page", null)

        val workbench = storage.saveGeneratedDesign(
            projectId = "p1",
            analysis = DesignAnalysis("Pricing page", "Focused SaaS", "Matches prompt"),
            generated = GeneratedDesign(
                id = "design-1",
                title = "Pricing",
                htmlPath = storage.currentDesignKey("p1"),
                rationale = "Focused",
                createdAt = "now",
            ),
            html = "<!doctype html><html><body>Pricing</body></html>".toByteArray(),
        )

        assertEquals("Pricing", workbench.currentDesign?.title)
        assertContentEquals(
            "<!doctype html><html><body>Pricing</body></html>".toByteArray(),
            storage.readCurrentDesign("p1"),
        )
    }

    @Test
    fun `list active output files returns design screen after active output is written`() {
        val activeHtml = "<!doctype html><html><body>Completed</body></html>".toByteArray()
        storage.saveGeneratedDesign(
            projectId = "p1",
            analysis = DesignAnalysis("Completed", "Focused", "Ready"),
            generated = GeneratedDesign(
                id = "design-1",
                title = "Completed",
                htmlPath = storage.currentDesignKey("p1"),
                rationale = "Ready",
                createdAt = "now",
            ),
            html = "<!doctype html><html><body>Current</body></html>".toByteArray(),
        )
        storage.writeActiveScreen("p1", activeHtml)

        val files = storage.listActiveOutputFiles("p1")

        assertEquals("design/screens/design/index.html", files.single().first)
        assertContentEquals(activeHtml, files.single().second)
    }

    @Test
    fun `new input and generated design do not expose stale active output`() {
        storage.saveInput("p1", "Design A", null)
        storage.saveGeneratedDesign(
            projectId = "p1",
            analysis = DesignAnalysis("A", "Direction A", "Rationale A"),
            generated = GeneratedDesign(
                id = "design-a",
                title = "A",
                htmlPath = storage.currentDesignKey("p1"),
                rationale = "Rationale A",
                createdAt = "now",
            ),
            html = "<!doctype html><html><body>Current A</body></html>".toByteArray(),
        )
        storage.writeActiveScreen("p1", "<!doctype html><html><body>Completed A</body></html>".toByteArray())
        storage.saveInput("p1", "Design B", null)

        storage.saveGeneratedDesign(
            projectId = "p1",
            analysis = DesignAnalysis("B", "Direction B", "Rationale B"),
            generated = GeneratedDesign(
                id = "design-b",
                title = "B",
                htmlPath = storage.currentDesignKey("p1"),
                rationale = "Rationale B",
                createdAt = "now",
            ),
            html = "<!doctype html><html><body>Current B</body></html>".toByteArray(),
        )

        assertTrue(storage.listActiveOutputFiles("p1").isEmpty())
    }

    @Test
    fun `save image analysis persists detailed JSON`() {
        storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")

        val updated = storage.saveImageAnalysis("p1", sampleImageAnalysis())

        assertEquals("Dense SaaS dashboard with a calm operational layout.", updated.imageAnalysis?.summary)
        assertEquals("#111827", updated.imageAnalysis?.palette?.first()?.hex)
        assertNull(updated.imageAnalysisError)
        assertEquals(updated.imageAnalysis, storage.load("p1").imageAnalysis)
    }

    @Test
    fun `save image analysis error stores retryable message`() {
        storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")

        val updated = storage.saveImageAnalysisError("p1", "Vision provider unavailable")

        assertEquals("Vision provider unavailable", updated.imageAnalysisError)
        assertNull(updated.imageAnalysis)
    }

    @Test
    fun `new image upload clears image analysis and error`() {
        storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")
        storage.saveImageAnalysis("p1", sampleImageAnalysis())
        storage.saveImageAnalysisError("p1", "Old error")

        val updated = storage.saveImageInput("p1", "new.png", byteArrayOf(4, 5, 6), "image/png")

        assertNull(updated.imageAnalysis)
        assertNull(updated.imageAnalysisError)
    }

    @Test
    fun `description update preserves existing image analysis`() {
        storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")
        storage.saveImageAnalysis("p1", sampleImageAnalysis())

        val updated = storage.saveInput("p1", "Use this dashboard style", null)

        assertEquals("Use this dashboard style", updated.description)
        assertEquals("dashboard.png", updated.imageInput?.originalName)
        assertEquals("Dense SaaS dashboard with a calm operational layout.", updated.imageAnalysis?.summary)
    }

    @Test
    fun `read image input returns stored bytes`() {
        storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")

        assertContentEquals(byteArrayOf(1, 2, 3), storage.readImageInput("p1"))
    }
}
