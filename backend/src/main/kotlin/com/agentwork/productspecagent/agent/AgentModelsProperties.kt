package com.agentwork.productspecagent.agent

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agent.models")
data class AgentModelsProperties(
    val tiers: Map<AgentModelTier, String> = emptyMap(),
    val defaults: Map<String, AgentModelTier> = emptyMap(),
)
