package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLModel

@Suppress("DEPRECATION")
fun resolveClaudeModel(name: String): LLModel = when (name) {
    "claude-3-haiku" -> AnthropicModels.Haiku_3
    "claude-haiku-4-5" -> AnthropicModels.Haiku_4_5
    "claude-sonnet-4-0" -> AnthropicModels.Sonnet_4
    "claude-sonnet-4-5" -> AnthropicModels.Sonnet_4_5
    "claude-sonnet-4-6" -> AnthropicModels.Sonnet_4_6
    "claude-opus-4-0" -> AnthropicModels.Opus_4
    "claude-opus-4-1" -> AnthropicModels.Opus_4_1
    "claude-opus-4-5" -> AnthropicModels.Opus_4_5
    "claude-opus-4-6" -> AnthropicModels.Opus_4_6
    else -> throw IllegalStateException("Unknown Claude model id: $name")
}
