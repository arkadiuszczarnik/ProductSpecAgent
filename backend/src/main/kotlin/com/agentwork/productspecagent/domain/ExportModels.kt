package com.agentwork.productspecagent.domain

data class ExportRequest(
    val includeDecisions: Boolean = true,
    val includeClarifications: Boolean = true,
    val includeTasks: Boolean = true,
    val includeDocuments: Boolean = true
)
