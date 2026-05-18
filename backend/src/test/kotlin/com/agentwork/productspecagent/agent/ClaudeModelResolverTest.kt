package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class ClaudeModelResolverTest {

    @Test
    fun `resolves claude model ids`() {
        assertThat(resolveClaudeModel("claude-3-haiku")).isEqualTo(AnthropicModels.Haiku_3)
        assertThat(resolveClaudeModel("claude-haiku-4-5")).isEqualTo(AnthropicModels.Haiku_4_5)
        assertThat(resolveClaudeModel("claude-sonnet-4-0")).isEqualTo(AnthropicModels.Sonnet_4)
        assertThat(resolveClaudeModel("claude-sonnet-4-5")).isEqualTo(AnthropicModels.Sonnet_4_5)
        assertThat(resolveClaudeModel("claude-sonnet-4-6")).isEqualTo(AnthropicModels.Sonnet_4_6)
        assertThat(resolveClaudeModel("claude-opus-4-0")).isEqualTo(AnthropicModels.Opus_4)
        assertThat(resolveClaudeModel("claude-opus-4-1")).isEqualTo(AnthropicModels.Opus_4_1)
        assertThat(resolveClaudeModel("claude-opus-4-5")).isEqualTo(AnthropicModels.Opus_4_5)
        assertThat(resolveClaudeModel("claude-opus-4-6")).isEqualTo(AnthropicModels.Opus_4_6)
    }

    @Test
    fun `throws on unknown claude model id`() {
        assertThatThrownBy { resolveClaudeModel("claude-unknown") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown Claude model id: claude-unknown")
    }
}
