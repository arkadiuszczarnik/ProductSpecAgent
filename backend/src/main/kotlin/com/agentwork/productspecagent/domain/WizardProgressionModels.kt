package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class WizardProgressionView(
    val category: String?,
    val steps: List<WizardStepView>,
    val currentStep: String?,
    val status: String,
    val primaryAction: WizardPrimaryActionDto,
)

@Serializable
data class WizardStepView(
    val step: String,
    val status: String,
    val visible: Boolean = true,
    val finalVisibleStep: Boolean = false,
)

@Serializable
data class WizardPrimaryActionDto(
    val type: String,
    val step: String? = null,
)

@Serializable
data class WizardClientActionDto(
    val type: String,
    val step: String? = null,
)

@Serializable
data class WizardCreatedArtifacts(
    val decisionIds: List<String> = emptyList(),
    val clarificationIds: List<String> = emptyList(),
)

fun emptyWizardProgressionView(): WizardProgressionView =
    WizardProgressionView(
        category = null,
        steps = emptyList(),
        currentStep = null,
        status = "IN_PROGRESS",
        primaryAction = WizardPrimaryActionDto(type = "NONE"),
    )
