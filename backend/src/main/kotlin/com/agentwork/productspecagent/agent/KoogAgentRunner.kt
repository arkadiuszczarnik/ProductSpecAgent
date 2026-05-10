package com.agentwork.productspecagent.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import com.agentwork.productspecagent.service.AgentModelService

@Component
class KoogAgentRunner(
    @Qualifier("openAIExecutor") private val promptExecutor: PromptExecutor,
    private val modelService: AgentModelService,
    private val modelRegistry: AgentModelRegistry,
) {
    private val logger = LoggerFactory.getLogger(KoogAgentRunner::class.java)

    suspend fun run(agentId: String, systemPrompt: String, userMessage: String): String {
        val tier = modelService.getTier(agentId)
        val model = modelRegistry.modelFor(tier)
        logger.debug("Running Koog agent={} tier={} model={} promptLen={}", agentId, tier, modelRegistry.modelIdFor(tier), systemPrompt.length)

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            systemPrompt = systemPrompt,
            llmModel = model,
        )
        return agent.run(userMessage)
    }

    suspend fun runWithImage(
        agentId: String,
        systemPrompt: String,
        userMessage: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
    ): String {
        val tier = modelService.getTier(agentId)
        val model = modelRegistry.modelFor(tier)
        val format = contentType.substringAfter("/", "png").substringBefore(";").ifBlank { "png" }
        logger.debug(
            "Running Koog image agent={} tier={} model={} promptLen={} imageBytes={}",
            agentId,
            tier,
            modelRegistry.modelIdFor(tier),
            systemPrompt.length,
            imageBytes.size,
        )

        val prompt = prompt("${agentId}-image") {
            system(systemPrompt)
            user {
                text(userMessage)
                image(
                    ContentPart.Image(
                        content = AttachmentContent.Binary.Bytes(imageBytes),
                        format = format,
                        mimeType = contentType,
                        fileName = fileName,
                    )
                )
            }
        }
        return promptExecutor.execute(prompt = prompt, model = model, tools = emptyList()).last().content
    }
}
