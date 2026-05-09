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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesignWorkbenchServiceTest {
    private val objectStore = InMemoryObjectStore()
    private val storage = DesignWorkbenchStorage(objectStore)
    private val service = DesignWorkbenchService(
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
        designVariantAgent = object : DesignVariantAgent(null) {
            override fun generate(projectId: String, screenId: String, prompt: String?) =
                GeneratedDesignVariant(
                    title = "Initial",
                    html = "<!doctype html><html><body><main>Landing</main></body></html>",
                    rationale = "Initial landing",
                )
        },
    )

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
    fun `complete rejects missing active screen`() {
        assertFailsWith<InvalidDesignWorkbenchException> {
            service.complete("p1")
        }
    }
}
