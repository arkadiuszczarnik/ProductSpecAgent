package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.GeneratedDesign
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesignWorkbenchStorageTest {
    private val objectStore = InMemoryObjectStore()
    private val storage = DesignWorkbenchStorage(objectStore)

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
}
