package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignInputKind
import com.agentwork.productspecagent.domain.DesignInputCategory
import com.agentwork.productspecagent.domain.DesignInputClassification
import com.agentwork.productspecagent.domain.DesignScreen
import com.agentwork.productspecagent.domain.DesignVariant
import com.agentwork.productspecagent.domain.DesignVariantStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignWorkbenchStorageTest {
    private val objectStore = InMemoryObjectStore()
    private val storage = DesignWorkbenchStorage(objectStore)

    @Test
    fun `load initializes empty workbench`() {
        val workbench = storage.load("p1")

        assertEquals("p1", workbench.projectId)
        assertTrue(workbench.inputs.isEmpty())
        assertTrue(workbench.screens.isEmpty())
    }

    @Test
    fun `stores text input and persists workbench`() {
        val input = storage.addTextInput("p1", "Make a compact SaaS dashboard")

        val loaded = storage.load("p1")
        assertEquals(DesignInputKind.TEXT, input.kind)
        assertEquals(input.id, loaded.inputs.single().id)
        assertEquals("Make a compact SaaS dashboard", objectStore.get(input.contentRef)!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `updates input classification and clears user label`() {
        val input = storage.addTextInput("p1", "Make a compact SaaS dashboard")
        val classification = DesignInputClassification(
            category = DesignInputCategory.HTML_CSS_REFERENCE,
            summary = "Dashboard direction",
            suggestedUse = "Use as layout guidance",
            confidence = 0.9,
        )
        storage.updateInputClassification("p1", input.id, classification, "Dashboard")

        val updated = storage.updateInputClassification("p1", input.id, classification, null)

        val loadedInput = updated.inputs.single()
        assertEquals(classification, loadedInput.classification)
        assertNull(loadedInput.userLabel)
    }

    @Test
    fun `stores variant html and sets active variant`() {
        val screen = DesignScreen(id = "landing", name = "Landing", purpose = "Explain value")
        storage.saveScreens("p1", listOf(screen))
        val variant = DesignVariant(
            id = "v1",
            screenId = "landing",
            version = 1,
            title = "Initial",
            htmlPath = "projects/p1/design/variants/landing/v1.html",
            status = DesignVariantStatus.VALID,
            rationale = "Initial variant",
            createdAt = "2026-05-09T00:00:00Z",
        )

        storage.saveVariant("p1", "landing", variant, "<html><body>Landing</body></html>".toByteArray())
        storage.setActiveVariant("p1", "landing", "v1")

        val loaded = storage.load("p1")
        assertEquals("v1", loaded.screens.single().activeVariantId)
        assertNotNull(objectStore.get("projects/p1/design/variants/landing/v1.html"))
    }

    @Test
    fun `save variant ignores caller path and persists normalized path`() {
        val screen = DesignScreen(id = "landing", name = "Landing", purpose = "Explain value")
        storage.saveScreens("p1", listOf(screen))
        val variant = DesignVariant(
            id = "v2",
            screenId = "landing",
            version = 2,
            title = "Second",
            htmlPath = "wrong/path.html",
            status = DesignVariantStatus.VALID,
            rationale = "Second variant",
            createdAt = "2026-05-09T00:00:00Z",
        )
        val expectedPath = storage.variantKey("p1", "landing", "v2")

        val updated = storage.saveVariant("p1", "landing", variant, "<html><body>Second</body></html>".toByteArray())

        assertNull(objectStore.get("wrong/path.html"))
        assertNotNull(objectStore.get(expectedPath))
        assertEquals(expectedPath, updated.screens.single().variants.single().htmlPath)
    }
}
