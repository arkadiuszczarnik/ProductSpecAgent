package com.agentwork.productspecagent.domain

data class CreateProjectRequest(
    val name: String
)

data class ProjectResponse(
    val project: Project,
    val flowState: FlowState
)

data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: String
)

data class SetGraphMeshEnabledRequest(val enabled: Boolean)
