package com.agentwork.productspecagent.domain

import com.agentwork.productspecagent.agent.AgentModelTier

data class AgentModelInfo(
    val agentId: String,
    val displayName: String,
    val defaultTier: AgentModelTier,
    val currentTier: AgentModelTier,
    val isOverridden: Boolean,
    val tierMapping: Map<AgentModelTier, String>,
)

data class UpdateAgentModelRequest(val tier: AgentModelTier)
