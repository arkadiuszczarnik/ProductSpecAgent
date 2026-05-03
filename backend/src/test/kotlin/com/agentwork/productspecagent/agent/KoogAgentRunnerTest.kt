package com.agentwork.productspecagent.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.agentwork.productspecagent.service.AgentModelService
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class KoogAgentRunnerTest {

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
    fun `run uses tier from service to select model`() = runBlocking {
        val registry = AgentModelRegistry(validProps)
        val service = AgentModelService(registry, InMemoryObjectStore())
        val capturing = CapturingExecutor()
        val runner = KoogAgentRunner(capturing, service, registry)

        runner.run("decision", "you are a test", "hello")
        assertThat(capturing.lastModel).isEqualTo(OpenAIModels.Chat.GPT5Mini)

        service.setTier("decision", AgentModelTier.LARGE)
        runner.run("decision", "you are a test", "hello again")
        assertThat(capturing.lastModel).isEqualTo(OpenAIModels.Chat.GPT5_2)
    }

    @Test
    fun `run with unknown agentId throws`() {
        val registry = AgentModelRegistry(validProps)
        val service = AgentModelService(registry, InMemoryObjectStore())
        val runner = KoogAgentRunner(CapturingExecutor(), service, registry)
        assertThatThrownBy {
            runBlocking { runner.run("ghost", "x", "y") }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private class CapturingExecutor : PromptExecutor() {
        var lastModel: LLModel? = null

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> {
            lastModel = model
            return listOf(Message.Assistant(content = "ok", metaInfo = ResponseMetaInfo.Empty))
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> {
            lastModel = model
            return emptyFlow()
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            throw UnsupportedOperationException("not used in test")
        }

        override fun close() {}
    }
}
