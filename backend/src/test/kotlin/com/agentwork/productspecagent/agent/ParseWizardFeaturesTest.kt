package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.service.WizardFeatureInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParseWizardFeaturesTest {
    @Test
    fun `parses legacy flat list without scopes`() {
        val raw = listOf(
            mapOf("title" to "Login", "description" to "Auth", "estimate" to "M"),
            mapOf("title" to "Dashboard", "description" to "Main view"),
        )
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "SaaS")
        assertThat(result).hasSize(2)
        assertThat(result[0].title).isEqualTo("Login")
        assertThat(result[0].scopes).containsExactlyInAnyOrder(FeatureScope.FRONTEND, FeatureScope.BACKEND)
        assertThat(result[0].id).isNotBlank() // auto-assigned UUID
        assertThat(result[0].dependsOn).isEmpty()
    }

    @Test
    fun `parses graph structure with scopes and edges`() {
        val raw = mapOf(
            "features" to listOf(
                mapOf("id" to "f-1", "title" to "Login",
                      "scopes" to listOf("BACKEND"),
                      "scopeFields" to mapOf("apiEndpoints" to "POST /auth/login")),
                mapOf("id" to "f-2", "title" to "Dashboard",
                      "scopes" to listOf("FRONTEND"),
                      "scopeFields" to mapOf("screens" to "/dashboard")),
            ),
            "edges" to listOf(
                mapOf("id" to "e-1", "from" to "f-1", "to" to "f-2"),
            ),
        )
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "SaaS")
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo("f-1")
        assertThat(result[0].scopes).containsExactly(FeatureScope.BACKEND)
        val dashboard = result.first { it.id == "f-2" }
        assertThat(dashboard.dependsOn).containsExactly("f-1")
    }

    @Test
    fun `library category defaults to empty scopes`() {
        val raw = listOf(mapOf("title" to "Core API"))
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "Library")
        assertThat(result[0].scopes).isEmpty()
    }

    @Test
    fun `api category defaults to backend scope`() {
        val raw = listOf(mapOf("title" to "Public API"))
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "API")
        assertThat(result[0].scopes).containsExactly(FeatureScope.BACKEND)
    }

    @Test
    fun `empty input returns empty list`() {
        assertThat(IdeaToSpecAgent.parseWizardFeatures(null, category = "SaaS")).isEmpty()
        assertThat(IdeaToSpecAgent.parseWizardFeatures(emptyList<Any>(), category = "SaaS")).isEmpty()
    }

    @Test
    fun `accumulates multiple edges to the same target feature`() {
        val raw = mapOf(
            "features" to listOf(
                mapOf("id" to "f-1", "title" to "Auth", "scopes" to listOf("BACKEND")),
                mapOf("id" to "f-2", "title" to "Profile", "scopes" to listOf("BACKEND")),
                mapOf("id" to "f-3", "title" to "Dashboard", "scopes" to listOf("FRONTEND")),
            ),
            "edges" to listOf(
                mapOf("id" to "e-1", "from" to "f-1", "to" to "f-3"),
                mapOf("id" to "e-2", "from" to "f-2", "to" to "f-3"),
            ),
        )
        val result = IdeaToSpecAgent.parseWizardFeatures(raw, category = "SaaS")
        val dashboard = result.first { it.id == "f-3" }
        assertThat(dashboard.dependsOn).containsExactlyInAnyOrder("f-1", "f-2")
    }
}
