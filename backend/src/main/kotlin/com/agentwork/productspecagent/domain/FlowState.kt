package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable
import java.time.Instant

enum class FlowStepType {
    IDEA, PROBLEM, SCOPE, MVP,
    FEATURES, ARCHITECTURE, BACKEND, FRONTEND
}

enum class FlowStepStatus {
    OPEN, IN_PROGRESS, COMPLETED
}

@Serializable
data class FlowStep(
    val stepType: FlowStepType,
    val status: FlowStepStatus,
    val updatedAt: String
)

@Serializable
data class FlowState(
    val projectId: String,
    val steps: List<FlowStep>,
    val currentStep: FlowStepType
)

fun createInitialFlowState(projectId: String): FlowState {
    val now = Instant.now().toString()
    val steps = FlowStepType.entries.map { stepType ->
        FlowStep(
            stepType = stepType,
            status = if (stepType == FlowStepType.IDEA) FlowStepStatus.IN_PROGRESS else FlowStepStatus.OPEN,
            updatedAt = now
        )
    }
    return FlowState(
        projectId = projectId,
        steps = steps,
        currentStep = FlowStepType.IDEA
    )
}
