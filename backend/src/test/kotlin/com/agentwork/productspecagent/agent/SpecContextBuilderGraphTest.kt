package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.WizardFeatureInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpecContextBuilderGraphTest {
    @Test
    fun `renders features block with scopes and dependencies`() {
        val inputs = listOf(
            WizardFeatureInput(id = "f-1", title = "Login", description = "",
                scopes = setOf(FeatureScope.BACKEND),
                scopeFields = mapOf("apiEndpoints" to "POST /auth/login"),
                dependsOn = emptyList()),
            WizardFeatureInput(id = "f-2", title = "Dashboard", description = "",
                scopes = setOf(FeatureScope.FRONTEND),
                scopeFields = mapOf("screens" to "/dashboard"),
                dependsOn = listOf("f-1")),
        )
        val rendered = SpecContextBuilder.renderFeaturesBlock(inputs, category = "SaaS")
        assertThat(rendered)
            .contains("Features & Dependencies (Category: SaaS):")
            .contains("[f-1] Login (Backend) — depends on: —")
            .contains("[f-2] Dashboard (Frontend) — depends on: f-1")
            .contains("ApiEndpoints: POST /auth/login")
            .contains("Screens: /dashboard")
    }

    @Test
    fun `library feature shows Core label`() {
        val input = WizardFeatureInput(id = "f-1", title = "Utils", description = "",
            scopes = emptySet(), scopeFields = emptyMap(), dependsOn = emptyList())
        val rendered = SpecContextBuilder.renderFeaturesBlock(listOf(input), category = "Library")
        assertThat(rendered).contains("(Core)")
    }

    @Test
    fun `isolated nodes are summarized`() {
        val inputs = listOf(
            WizardFeatureInput(id = "a", title = "A", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()),
            WizardFeatureInput(id = "b", title = "B", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = listOf("a")),
            WizardFeatureInput(id = "c", title = "Loner", description = "",
                scopes = setOf(FeatureScope.BACKEND), scopeFields = emptyMap(), dependsOn = emptyList()),
        )
        val rendered = SpecContextBuilder.renderFeaturesBlock(inputs, category = "SaaS")
        assertThat(rendered).contains("Isolated nodes: c")
    }
}
