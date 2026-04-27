package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshConfig
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/config")
class ConfigController(private val graphMeshConfig: GraphMeshConfig) {

    @GetMapping("/features")
    fun features(): Map<String, Any> = mapOf(
        "graphmeshEnabled" to graphMeshConfig.enabled
    )
}
