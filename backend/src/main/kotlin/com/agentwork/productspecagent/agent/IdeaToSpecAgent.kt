package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ClarificationService
import com.agentwork.productspecagent.service.DecisionService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.PromptService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
open class IdeaToSpecAgent(
    private val contextBuilder: SpecContextBuilder,
    private val projectService: ProjectService,
    private val promptService: PromptService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val koogRunner: KoogAgentRunner? = null,
) {

    private val logger = LoggerFactory.getLogger(IdeaToSpecAgent::class.java)
    private val stepOrder = FlowStepType.entries.toList()

    suspend fun chat(projectId: String, userMessage: String, locale: String = "en"): ChatResponse {
        val flowState = projectService.getFlowState(projectId)
        val context = contextBuilder.buildContext(projectId)

        val currentStep = flowState.currentStep

        val localeInstruction = buildLocaleInstruction(locale)
        val stepPrompt = buildStepPrompt(currentStep)
        val baseSystemPrompt = promptService.get("idea-base")
        val systemPromptWithContext = "$baseSystemPrompt\n\n$stepPrompt\n\n$localeInstruction\n\n$context"

        val rawResponse = runAgent(systemPromptWithContext, userMessage)

        val stepCompleted = rawResponse.contains("[STEP_COMPLETE]")
        val summaryMatch = Regex("""\[STEP_SUMMARY]\s*[:：]\s*(.+)""", RegexOption.DOT_MATCHES_ALL)
            .find(rawResponse)
        val summaryContent = summaryMatch?.groupValues?.get(1)?.trim()

        val decisionTitle = AgentResponseMarkers.extractDecisionTitle(rawResponse)
        val clarification = AgentResponseMarkers.extractClarification(rawResponse)
        val clarificationQuestion = clarification?.first
        val clarificationReason = clarification?.second

        logger.info("chat() markers – decision={}, clarification={}", decisionTitle, clarificationQuestion)

        val cleanMessage = AgentResponseMarkers.clean(rawResponse)

        var nextStep = currentStep
        var flowStateChanged = false
        var createdDecisionId: String? = null

        if (decisionTitle != null) {
            val decision = decisionService.createDecision(projectId, decisionTitle, currentStep)
            createdDecisionId = decision.id
        }

        var createdClarificationId: String? = null

        if (clarificationQuestion != null && clarificationReason != null) {
            val clarification = clarificationService.createClarification(
                projectId, clarificationQuestion, clarificationReason, currentStep
            )
            createdClarificationId = clarification.id
        }

        if (stepCompleted) {
            val fileName = currentStep.name.lowercase() + ".md"
            val title = currentStep.name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
            val markdownContent = "# $title\n\n${summaryContent ?: cleanMessage}"
            projectService.saveSpecFile(projectId, fileName, markdownContent)

            val now = Instant.now().toString()
            val updatedSteps = flowState.steps.map { step ->
                when (step.stepType) {
                    currentStep -> step.copy(status = FlowStepStatus.COMPLETED, updatedAt = now)
                    else -> step
                }
            }

            val currentIndex = stepOrder.indexOf(currentStep)
            if (currentIndex + 1 < stepOrder.size) {
                nextStep = stepOrder[currentIndex + 1]
                val finalSteps = updatedSteps.map { step ->
                    if (step.stepType == nextStep) step.copy(status = FlowStepStatus.IN_PROGRESS, updatedAt = now)
                    else step
                }
                projectService.updateFlowState(projectId, flowState.copy(
                    steps = finalSteps, currentStep = nextStep
                ))
            } else {
                projectService.updateFlowState(projectId, flowState.copy(steps = updatedSteps))
            }
            flowStateChanged = true
        }

        return ChatResponse(
            message = cleanMessage,
            flowStateChanged = flowStateChanged,
            currentStep = nextStep.name,
            decisionId = createdDecisionId,
            clarificationId = createdClarificationId
        )
    }

    private fun buildStepPrompt(step: FlowStepType): String = when (step) {
        FlowStepType.IDEA -> promptService.get("idea-step-IDEA")
        else -> ""
    }

    private fun buildLocaleInstruction(locale: String): String {
        val langCode = locale.split("-", "_").first().lowercase()
        val languageName = mapOf(
            "de" to "Deutsch", "en" to "English", "fr" to "Français",
            "es" to "Español", "it" to "Italiano", "pt" to "Português",
            "nl" to "Nederlands", "pl" to "Polski", "ja" to "日本語",
            "zh" to "中文", "ko" to "한국語", "ru" to "Русский"
        )[langCode]

        return if (languageName != null) {
            "IMPORTANT: Always respond in $languageName ($langCode). Do not switch languages."
        } else {
            "IMPORTANT: Always respond in the language with code '$langCode'. Do not switch languages."
        }
    }

    protected open suspend fun runAgent(systemPrompt: String, userMessage: String): String {
        val result = koogRunner?.run(AGENT_ID, systemPrompt, userMessage)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
        logger.info("Agent raw response (last 500 chars): ...{}", result.takeLast(500))
        return result
    }

    companion object {
        const val AGENT_ID = "idea-to-spec"
    }
}
