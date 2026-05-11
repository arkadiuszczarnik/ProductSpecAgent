package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.ProductCategory
import com.agentwork.productspecagent.domain.WizardData
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
class WizardProgressionPolicy(
    private val wizardOptionCatalogService: WizardOptionCatalogService? = null,
) {

    private val fullFlowSteps = listOf(
        FlowStepType.IDEA,
        FlowStepType.PROBLEM,
        FlowStepType.FEATURES,
        FlowStepType.MVP,
        FlowStepType.DESIGN,
        FlowStepType.ARCHITECTURE,
        FlowStepType.BACKEND,
        FlowStepType.FRONTEND,
        FlowStepType.REVIEW,
    )

    fun planFor(wizardData: WizardData): WizardProgressionPlan {
        val category = ProductCategory.fromWire(
            wizardData.steps["IDEA"]?.fields?.get("category")
                ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() },
        )
        val categoryWireValue = category?.wireValue
        return WizardProgressionPlan(
            category = category,
            visibleSteps = visibleSteps(category, categoryWireValue),
        )
    }

    private fun visibleSteps(category: ProductCategory?, categoryWireValue: String?): List<FlowStepType> {
        val catalogSteps = categoryWireValue
            ?.let { wireValue ->
                (wizardOptionCatalogService?.getCatalog() ?: WizardOptionCatalogDefaults.create()).categories
                    .firstOrNull { it.id == wireValue }
                    ?.visibleSteps
                    ?.takeIf { it.isNotEmpty() }
            }
        if (catalogSteps != null) return withReviewStep(catalogSteps)

        return withReviewStep(
            when (category) {
                ProductCategory.LIBRARY -> listOf(
                    FlowStepType.IDEA,
                    FlowStepType.PROBLEM,
                    FlowStepType.FEATURES,
                    FlowStepType.MVP,
                )
                ProductCategory.CLI_TOOL -> listOf(
                    FlowStepType.IDEA,
                    FlowStepType.PROBLEM,
                    FlowStepType.FEATURES,
                    FlowStepType.MVP,
                    FlowStepType.ARCHITECTURE,
                )
                ProductCategory.API -> listOf(
                    FlowStepType.IDEA,
                    FlowStepType.PROBLEM,
                    FlowStepType.FEATURES,
                    FlowStepType.MVP,
                    FlowStepType.ARCHITECTURE,
                    FlowStepType.BACKEND,
                )
                ProductCategory.SAAS,
                ProductCategory.MOBILE_APP,
                ProductCategory.DESKTOP_APP,
                null -> fullFlowSteps
            }
        )
    }

    private fun withReviewStep(steps: List<FlowStepType>): List<FlowStepType> =
        if (steps.lastOrNull() == FlowStepType.REVIEW) steps
        else steps.filterNot { it == FlowStepType.REVIEW } + FlowStepType.REVIEW
}
