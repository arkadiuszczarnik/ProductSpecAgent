package com.agentwork.productspecagent.domain

data class WizardStepCompleteRequest(
    val step: String,
    val fields: Map<String, Any>,
    val locale: String = "en"
)

data class WizardStepCompleteResponse(
    val message: String,
    val nextStep: String?,
    val exportTriggered: Boolean = false,
    val decisionId: String? = null,
    val clarificationId: String? = null,
    val progression: WizardProgressionView = emptyWizardProgressionView(),
    val action: WizardClientActionDto = WizardClientActionDto(type = "STAY"),
    val artifacts: WizardCreatedArtifacts = WizardCreatedArtifacts(),
    val appliedDecisionIds: List<String> = emptyList(),
    val appliedClarificationIds: List<String> = emptyList(),
    val wizardDataChanged: Boolean = false,
)
