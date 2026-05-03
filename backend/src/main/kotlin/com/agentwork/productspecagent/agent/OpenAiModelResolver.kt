package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel

fun resolveOpenAiModel(name: String): LLModel = when (name) {
    "gpt-4o" -> OpenAIModels.Chat.GPT4o
    "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
    "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
    "gpt-4.1-mini" -> OpenAIModels.Chat.GPT4_1Mini
    "gpt-4.1-nano" -> OpenAIModels.Chat.GPT4_1Nano
    "gpt-5-nano" -> OpenAIModels.Chat.GPT5Nano
    "gpt-5-mini" -> OpenAIModels.Chat.GPT5Mini
    "gpt-5" -> OpenAIModels.Chat.GPT5
    "gpt-5-2" -> OpenAIModels.Chat.GPT5_2
    "gpt-5-2-pro" -> OpenAIModels.Chat.GPT5_2Pro
    "gpt-5.4-mini" -> OpenAIModels.Chat.GPT5_4Mini
    "gpt-5.4-nano" -> OpenAIModels.Chat.GPT5_4Nano
    else -> throw IllegalStateException("Unknown OpenAI model id: $name")
}
