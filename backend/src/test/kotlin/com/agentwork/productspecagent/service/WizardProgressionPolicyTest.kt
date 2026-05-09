package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.WizardOptionCatalogStorage
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WizardProgressionPolicyTest {

    private val catalogService = WizardOptionCatalogService(
        WizardOptionCatalogStorage(InMemoryObjectStore()),
        Clock.fixed(Instant.parse("2026-05-09T00:00:00Z"), ZoneOffset.UTC),
    )
    private val policy = WizardProgressionPolicy(catalogService)
    private val fullFlowSteps = listOf(
        FlowStepType.IDEA,
        FlowStepType.PROBLEM,
        FlowStepType.FEATURES,
        FlowStepType.MVP,
        FlowStepType.DESIGN,
        FlowStepType.ARCHITECTURE,
        FlowStepType.BACKEND,
        FlowStepType.FRONTEND,
    )

    @Test
    fun `library ends at MVP`() {
        val plan = policy.planFor(wizardData("Library"))

        assertThat(plan.visibleSteps).containsExactly(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
        )
        assertThat(plan.isTerminal(FlowStepType.MVP)).isTrue()
        assertThat(plan.nextAfter(FlowStepType.MVP)).isNull()
    }

    @Test
    fun `cli tool ends at architecture`() {
        val plan = policy.planFor(wizardData("CLI Tool"))

        assertThat(plan.visibleSteps).containsExactly(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.ARCHITECTURE,
        )
        assertThat(plan.isTerminal(FlowStepType.ARCHITECTURE)).isTrue()
        assertThat(plan.nextAfter(FlowStepType.MVP)).isEqualTo(FlowStepType.ARCHITECTURE)
    }

    @Test
    fun `api ends at backend`() {
        val plan = policy.planFor(wizardData("API"))

        assertThat(plan.visibleSteps).containsExactly(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.ARCHITECTURE,
            FlowStepType.BACKEND,
        )
        assertThat(plan.isTerminal(FlowStepType.BACKEND)).isTrue()
        assertThat(plan.nextAfter(FlowStepType.ARCHITECTURE)).isEqualTo(FlowStepType.BACKEND)
    }

    @Test
    fun `saas uses all visible steps and ends at frontend`() {
        val plan = policy.planFor(wizardData("SaaS"))

        assertThat(plan.visibleSteps).containsExactlyElementsOf(fullFlowSteps)
        assertThat(plan.isTerminal(FlowStepType.FRONTEND)).isTrue()
    }

    @Test
    fun `catalog visible steps override category defaults`() {
        val catalog = catalogService.getCatalog()
        catalogService.saveCatalog(
            catalog.copy(
                categories = catalog.categories.map { category ->
                    if (category.id == "SaaS") {
                        category.copy(
                            visibleSteps = listOf(
                                FlowStepType.IDEA,
                                FlowStepType.PROBLEM,
                                FlowStepType.FEATURES,
                                FlowStepType.MVP,
                                FlowStepType.BACKEND,
                            )
                        )
                    } else {
                        category
                    }
                }
            )
        )

        val plan = policy.planFor(wizardData("SaaS"))

        assertThat(plan.visibleSteps).containsExactly(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.BACKEND,
        )
        assertThat(plan.isTerminal(FlowStepType.BACKEND)).isTrue()
    }

    @Test
    fun `mobile app uses all visible steps and ends at frontend`() {
        val plan = policy.planFor(wizardData("Mobile App"))

        assertThat(plan.visibleSteps).containsExactlyElementsOf(fullFlowSteps)
        assertThat(plan.isTerminal(FlowStepType.FRONTEND)).isTrue()
    }

    @Test
    fun `desktop app uses all visible steps and ends at frontend`() {
        val plan = policy.planFor(wizardData("Desktop App"))

        assertThat(plan.visibleSteps).containsExactlyElementsOf(fullFlowSteps)
        assertThat(plan.isTerminal(FlowStepType.FRONTEND)).isTrue()
    }

    @Test
    fun `no category falls back to full flow`() {
        val plan = policy.planFor(WizardData(projectId = "p1"))

        assertThat(plan.visibleSteps).containsExactlyElementsOf(fullFlowSteps)
        assertThat(plan.isTerminal(FlowStepType.FRONTEND)).isTrue()
    }

    @Test
    fun `unknown category falls back to full flow`() {
        val plan = policy.planFor(wizardData("Marketplace"))

        assertThat(plan.visibleSteps).containsExactlyElementsOf(fullFlowSteps)
        assertThat(plan.isTerminal(FlowStepType.FRONTEND)).isTrue()
    }

    private fun wizardData(category: String): WizardData =
        WizardData(
            projectId = "p1",
            steps = mapOf(
                "IDEA" to WizardStepData(
                    fields = mapOf("category" to JsonPrimitive(category)),
                )
            ),
        )
}
