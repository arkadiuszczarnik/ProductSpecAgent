package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.agent.GeneratedDesignVariant
import com.agentwork.productspecagent.agent.ReferenceAnalysisAgent
import com.agentwork.productspecagent.agent.ScreenProposal
import com.agentwork.productspecagent.agent.ScreenProposalAgent
import com.agentwork.productspecagent.domain.DesignInputCategory
import com.agentwork.productspecagent.domain.DesignInputClassification
import com.agentwork.productspecagent.domain.DesignInputKind
import com.agentwork.productspecagent.domain.DesignVariantStatus
import com.agentwork.productspecagent.storage.DesignWorkbenchStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesignWorkbenchServiceTest {
    private val objectStore = InMemoryObjectStore()
    private val storage = DesignWorkbenchStorage(objectStore)
    private val service = service()

    @Test
    fun `analyze assigns visible input classification`() {
        storage.addBinaryInput("p1", DesignInputKind.IMAGE, "dash.png", "x".toByteArray(), "image/png")

        val workbench = service.analyzeInputs("p1")

        assertEquals(DesignInputCategory.REFERENCE_IMAGE, workbench.inputs.single().classification?.category)
    }

    @Test
    fun `propose screens writes curated starting screens`() {
        val workbench = service.proposeScreens("p1")

        assertEquals("Landing", workbench.screens.single().name)
    }

    @Test
    fun `generate variant validates and stores variant`() {
        service.proposeScreens("p1")

        val workbench = service.generateVariant("p1", "landing", "compact SaaS")

        val variant = workbench.screens.single().variants.single()
        assertEquals(DesignVariantStatus.VALID, variant.status)
    }

    @Test
    fun `generate variant rejects missing screen before invoking agent`() {
        var generateCalls = 0
        val service = service(
            designVariantAgent = object : DesignVariantAgent(null) {
                override fun generate(projectId: String, screenId: String, prompt: String?): GeneratedDesignVariant {
                    generateCalls += 1
                    return validGeneratedVariant()
                }
            },
        )

        assertFailsWith<InvalidDesignWorkbenchException> {
            service.generateVariant("p1", "missing", null)
        }

        assertEquals(0, generateCalls)
    }

    @Test
    fun `read variant returns project owned variant html`() {
        service.proposeScreens("p1")
        val workbench = service.generateVariant("p1", "landing", null)
        val variant = workbench.screens.single().variants.single()

        val html = service.readVariant("p1", variant.htmlPath)

        assertContentEquals(validGeneratedVariant().html.toByteArray(), html)
    }

    @Test
    fun `read variant rejects variant from another project`() {
        service.proposeScreens("p1")
        val workbench = service.generateVariant("p1", "landing", null)
        val variant = workbench.screens.single().variants.single()

        assertFailsWith<InvalidDesignWorkbenchException> {
            service.readVariant("p2", variant.htmlPath)
        }
    }

    @Test
    fun `read variant rejects object path that is not a project variant`() {
        val objectPath = "projects/p1/design/inputs/input/content"
        objectStore.put(objectPath, "not a variant".toByteArray(), "text/plain")

        assertFailsWith<InvalidDesignWorkbenchException> {
            service.readVariant("p1", objectPath)
        }
    }

    @Test
    fun `set active variant converts storage lookup failures`() {
        service.proposeScreens("p1")

        assertFailsWith<InvalidDesignWorkbenchException> {
            service.setActiveVariant("p1", "missing", "variant")
        }
        assertFailsWith<InvalidDesignWorkbenchException> {
            service.setActiveVariant("p1", "landing", "missing")
        }
    }

    @Test
    fun `complete rejects missing active screen`() {
        assertFailsWith<InvalidDesignWorkbenchException> {
            service.complete("p1")
        }
    }

    @Test
    fun `complete writes active screen html to storage`() {
        service.proposeScreens("p1")
        val workbench = service.generateVariant("p1", "landing", null)
        val variant = workbench.screens.single().variants.single()
        service.setActiveVariant("p1", "landing", variant.id)

        service.complete("p1")

        val activeHtml = objectStore.get(storage.activeScreenKey("p1", "landing"))
        assertNotNull(activeHtml)
        assertContentEquals(validGeneratedVariant().html.toByteArray(), activeHtml)
    }

    @Test
    fun `invalid generated html does not persist variant`() {
        val service = service(
            designVariantAgent = object : DesignVariantAgent(null) {
                override fun generate(projectId: String, screenId: String, prompt: String?) =
                    GeneratedDesignVariant(
                        title = "Invalid",
                        html = """<!doctype html><html><body><img src="https://example.com/x.png"></body></html>""",
                        rationale = "Invalid remote image",
                    )
            },
        )
        service.proposeScreens("p1")

        assertFailsWith<InvalidDesignPreviewException> {
            service.generateVariant("p1", "landing", null)
        }

        val workbench = storage.load("p1")
        assertEquals(0, workbench.screens.single().variants.size)
        assertNull(objectStore.listKeys("projects/p1/design/variants/landing/").singleOrNull())
    }

    private fun service(
        designVariantAgent: DesignVariantAgent = object : DesignVariantAgent(null) {
            override fun generate(projectId: String, screenId: String, prompt: String?) = validGeneratedVariant()
        },
    ) = DesignWorkbenchService(
        storage = storage,
        previewValidator = DesignPreviewValidator(),
        referenceAnalysisAgent = object : ReferenceAnalysisAgent(null) {
            override fun analyze(projectId: String) = listOf(
                DesignInputClassification(
                    category = DesignInputCategory.REFERENCE_IMAGE,
                    summary = "Dashboard reference",
                    suggestedUse = "Use density and navigation pattern",
                    confidence = 0.8,
                ),
            )
        },
        screenProposalAgent = object : ScreenProposalAgent(null) {
            override fun propose(projectId: String) = listOf(
                ScreenProposal(id = "landing", name = "Landing", purpose = "Explain value"),
            )
        },
        designVariantAgent = designVariantAgent,
    )

    private fun validGeneratedVariant() =
        GeneratedDesignVariant(
            title = "Initial",
            html = "<!doctype html><html><body><main>Landing</main></body></html>",
            rationale = "Initial landing",
        )
}
