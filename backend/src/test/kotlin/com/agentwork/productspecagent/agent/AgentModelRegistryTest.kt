package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AgentModelRegistryTest {

    private val validProps = AgentModelsProperties(
        tiers = mapOf(
            AgentModelTier.SMALL to "gpt-5-nano",
            AgentModelTier.MEDIUM to "gpt-5-mini",
            AgentModelTier.LARGE to "gpt-5-2",
        ),
        defaults = mapOf(
            "idea-to-spec" to AgentModelTier.LARGE,
            "decision" to AgentModelTier.MEDIUM,
            "feature-proposal" to AgentModelTier.MEDIUM,
            "plan-generator" to AgentModelTier.LARGE,
        ),
    )

    @Test
    fun `valid properties produce working registry`() {
        val reg = AgentModelRegistry(validProps)
        assertThat(reg.agentIds()).containsExactlyInAnyOrder(
            "idea-to-spec", "decision", "feature-proposal", "plan-generator"
        )
        assertThat(reg.defaultTier("idea-to-spec")).isEqualTo(AgentModelTier.LARGE)
        assertThat(reg.modelFor(AgentModelTier.SMALL)).isEqualTo(OpenAIModels.Chat.GPT5Nano)
        assertThat(reg.modelIdFor(AgentModelTier.LARGE)).isEqualTo("gpt-5-2")
    }

    @Test
    fun `missing tier mapping fails fast`() {
        val props = validProps.copy(tiers = validProps.tiers - AgentModelTier.SMALL)
        assertThatThrownBy { AgentModelRegistry(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Tier mapping incomplete: missing SMALL")
    }

    @Test
    fun `unknown model id fails fast`() {
        val props = validProps.copy(
            tiers = validProps.tiers + (AgentModelTier.LARGE to "gpt-99-turbo")
        )
        assertThatThrownBy { AgentModelRegistry(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown OpenAI model id: gpt-99-turbo")
    }

    @Test
    fun `missing default for known agent fails fast`() {
        val props = validProps.copy(defaults = validProps.defaults - "decision")
        assertThatThrownBy { AgentModelRegistry(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Missing default tier for agent: decision")
    }

    @Test
    fun `unknown agent id in defaults fails fast`() {
        val props = validProps.copy(
            defaults = validProps.defaults + ("ghost-agent" to AgentModelTier.SMALL)
        )
        assertThatThrownBy { AgentModelRegistry(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown agent id in defaults: ghost-agent")
    }

    @Test
    fun `defaultTier throws for unknown agentId at runtime`() {
        val reg = AgentModelRegistry(validProps)
        assertThatThrownBy { reg.defaultTier("ghost") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ghost")
    }
}
