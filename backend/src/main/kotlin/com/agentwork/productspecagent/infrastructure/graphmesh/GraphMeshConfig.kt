package com.agentwork.productspecagent.infrastructure.graphmesh

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

@ConfigurationProperties(prefix = "graphmesh")
data class GraphMeshConfig(
    @DefaultValue("http://localhost:8083/graphql") val url: String,
    @DefaultValue("30s") val requestTimeout: Duration
)
