package com.agentwork.productspecagent.agent

import org.springframework.boot.context.properties.ConfigurationProperties

enum class AgentModelResolverType {
    OPENAI,
    CLAUDE,
}

@ConfigurationProperties(prefix = "agent.models")
data class AgentModelsProperties(
    val resolver: AgentModelResolverType = AgentModelResolverType.OPENAI,
    val tiers: Map<AgentModelTier, String> = emptyMap(),
    val defaults: Map<String, AgentModelTier> = emptyMap(),
)
