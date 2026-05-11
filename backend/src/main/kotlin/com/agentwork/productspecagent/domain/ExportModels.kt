package com.agentwork.productspecagent.domain

data class ExportRequest(
    val includeDecisions: Boolean = false,
    val includeClarifications: Boolean = false,
    val includeTasks: Boolean = true,
    val includeDesign: Boolean = true
)
