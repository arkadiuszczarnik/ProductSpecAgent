package com.agentwork.productspecagent.domain

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WizardFeatureGraphTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `serializes and deserializes a graph with nodes and edges`() {
        val graph = WizardFeatureGraph(
            features = listOf(
                WizardFeature(
                    id = "f-1",
                    title = "Login",
                    scopes = setOf(FeatureScope.BACKEND),
                    description = "User authentication",
                    scopeFields = mapOf("apiEndpoints" to "POST /auth/login"),
                    position = GraphPosition(0.0, 0.0)
                ),
                WizardFeature(
                    id = "f-2",
                    title = "Dashboard",
                    scopes = setOf(FeatureScope.FRONTEND),
                    description = "Main user view",
                    scopeFields = mapOf("screens" to "/dashboard"),
                    position = GraphPosition(320.0, 0.0)
                ),
            ),
            edges = listOf(WizardFeatureEdge(id = "e-1", from = "f-1", to = "f-2"))
        )
        val encoded = json.encodeToString(WizardFeatureGraph.serializer(), graph)
        val decoded = json.decodeFromString(WizardFeatureGraph.serializer(), encoded)
        assertThat(decoded).isEqualTo(graph)
    }

    @Test
    fun `empty scopes set represents Library-style feature`() {
        val feature = WizardFeature(
            id = "f-1",
            title = "Core",
            scopes = emptySet(),
            description = "",
            scopeFields = emptyMap(),
            position = GraphPosition(0.0, 0.0)
        )
        assertThat(feature.scopes).isEmpty()
    }
}
