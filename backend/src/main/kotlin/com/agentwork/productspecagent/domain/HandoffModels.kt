package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class HandoffPreview(
    val claudeMd: String,
    val agentsMd: String,
    val implementationOrder: String,
    val format: String = "claude-code",
    val syncUrl: String
)

@Serializable
data class HandoffExportRequest(
    val format: String = "claude-code",
    val claudeMd: String? = null,
    val agentsMd: String? = null,
    val implementationOrder: String? = null,
    val syncUrl: String? = null
)
