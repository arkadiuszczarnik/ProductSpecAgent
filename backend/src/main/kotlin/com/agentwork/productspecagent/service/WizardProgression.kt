package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.ProductCategory
import com.agentwork.productspecagent.domain.WizardClientActionDto
import com.agentwork.productspecagent.domain.WizardCreatedArtifacts
import com.agentwork.productspecagent.domain.WizardPrimaryActionDto
import com.agentwork.productspecagent.domain.WizardProgressionView

interface WizardProgression {
    fun snapshot(projectId: String): WizardProgressionView
    suspend fun complete(command: CompleteWizardStep): WizardStepCompletionResult
}

data class WizardProgressionPlan(
    val category: ProductCategory?,
    val visibleSteps: List<FlowStepType>,
) {
    fun isTerminal(step: FlowStepType): Boolean = visibleSteps.lastOrNull() == step

    fun nextAfter(step: FlowStepType): FlowStepType? =
        visibleSteps.dropWhile { it != step }.drop(1).firstOrNull()
}

data class WizardCompletionResult(
    val message: String,
    val progression: WizardProgressionView,
    val action: WizardClientActionDto,
    val artifacts: WizardCreatedArtifacts = WizardCreatedArtifacts(),
)

fun completeStepAction(step: FlowStepType): WizardPrimaryActionDto =
    WizardPrimaryActionDto(type = "COMPLETE_STEP", step = step.name)

fun showStepAction(step: FlowStepType): WizardClientActionDto =
    WizardClientActionDto(type = "SHOW_STEP", step = step.name)

val openExportPrimaryAction = WizardPrimaryActionDto(type = "OPEN_EXPORT")
val openExportClientAction = WizardClientActionDto(type = "OPEN_EXPORT")
val stayClientAction = WizardClientActionDto(type = "STAY")
val nonePrimaryAction = WizardPrimaryActionDto(type = "NONE")
