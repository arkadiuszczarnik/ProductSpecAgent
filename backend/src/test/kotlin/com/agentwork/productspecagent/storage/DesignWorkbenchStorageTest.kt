package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignInputKind
import com.agentwork.productspecagent.domain.DesignScreen
import com.agentwork.productspecagent.domain.DesignVariant
import com.agentwork.productspecagent.domain.DesignVariantStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}
