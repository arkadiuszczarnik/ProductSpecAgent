package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class WizardOptionCatalog(
    val version: Int = 1,
    val categories: List<WizardOptionCategory>,
    val updatedAt: String
)

@Serializable
data class WizardOptionCategory(
    val id: String,
    val label: String,
    val visibleSteps: List<FlowStepType>,
    val allowedScopes: List<FlowStepType>,
    val fields: List<WizardOptionField> = emptyList()
)

@Serializable
data class WizardOptionField(
    val step: FlowStepType,
    val key: String,
    val label: String,
    val options: List<WizardOption> = emptyList()
)

@Serializable
data class WizardOption(
    val id: String,
    val label: String,
    val enabled: Boolean = true
)
