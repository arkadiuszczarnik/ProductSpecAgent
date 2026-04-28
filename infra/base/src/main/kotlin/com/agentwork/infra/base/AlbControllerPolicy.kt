package com.agentwork.infra.base

object AlbControllerPolicy {
    fun load(): String {
        val resource = AlbControllerPolicy::class.java
            .getResourceAsStream("/alb-controller-iam-policy.json")
            ?: error("alb-controller-iam-policy.json not on classpath")
        return resource.bufferedReader().use { it.readText() }
    }
}
