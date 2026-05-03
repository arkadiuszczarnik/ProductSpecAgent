package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OpenAiModelResolverTest {

    @Test
    fun `resolves all GPT-5 family ids`() {
        assertThat(resolveOpenAiModel("gpt-5-nano")).isEqualTo(OpenAIModels.Chat.GPT5Nano)
        assertThat(resolveOpenAiModel("gpt-5-mini")).isEqualTo(OpenAIModels.Chat.GPT5Mini)
        assertThat(resolveOpenAiModel("gpt-5")).isEqualTo(OpenAIModels.Chat.GPT5)
        assertThat(resolveOpenAiModel("gpt-5-2")).isEqualTo(OpenAIModels.Chat.GPT5_2)
        assertThat(resolveOpenAiModel("gpt-5-2-pro")).isEqualTo(OpenAIModels.Chat.GPT5_2Pro)
        assertThat(resolveOpenAiModel("gpt-5.4-mini")).isEqualTo(OpenAIModels.Chat.GPT5_4Mini)
        assertThat(resolveOpenAiModel("gpt-5.4-nano")).isEqualTo(OpenAIModels.Chat.GPT5_4Nano)
    }

    @Test
    fun `resolves legacy GPT-4 ids`() {
        assertThat(resolveOpenAiModel("gpt-4o")).isEqualTo(OpenAIModels.Chat.GPT4o)
        assertThat(resolveOpenAiModel("gpt-4o-mini")).isEqualTo(OpenAIModels.Chat.GPT4oMini)
        assertThat(resolveOpenAiModel("gpt-4.1")).isEqualTo(OpenAIModels.Chat.GPT4_1)
        assertThat(resolveOpenAiModel("gpt-4.1-mini")).isEqualTo(OpenAIModels.Chat.GPT4_1Mini)
        assertThat(resolveOpenAiModel("gpt-4.1-nano")).isEqualTo(OpenAIModels.Chat.GPT4_1Nano)
    }

    @Test
    fun `throws on unknown model id`() {
        assertThatThrownBy { resolveOpenAiModel("gpt-99-turbo") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown OpenAI model id: gpt-99-turbo")
    }
}
