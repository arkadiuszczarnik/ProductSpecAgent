package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.KoogAgentRunner
import com.agentwork.productspecagent.domain.Clarification
import com.agentwork.productspecagent.domain.Decision
import com.agentwork.productspecagent.domain.FlowStepType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Component

data class ApplyWizardBlockers(
    val projectId: String,
    val step: FlowStepType,
    val fields: Map<String, JsonElement>,
    val decisions: List<Decision>,
    val clarifications: List<Clarification>,
    val locale: String,
)

data class WizardBlockerApplyResult(
    val message: String,
    val fieldUpdates: Map<String, JsonElement>,
    val appliedFields: List<String>,
)

interface WizardBlockerApplyAgent {
    suspend fun apply(command: ApplyWizardBlockers): WizardBlockerApplyResult
}

@Component
class KoogWizardBlockerApplyAgent(
    private val koogRunner: KoogAgentRunner,
    private val promptService: PromptService,
) : WizardBlockerApplyAgent {
    override suspend fun apply(command: ApplyWizardBlockers): WizardBlockerApplyResult {
        val allowedFields = WizardStepFieldSchema.allowedFields(command.step)
        val raw = koogRunner.run(AGENT_ID, promptService.get("wizard-blocker-apply-system"), buildPrompt(command, allowedFields))
        return WizardBlockerApplyJson.parseResult(raw, allowedFields)
    }

    private fun buildPrompt(command: ApplyWizardBlockers, allowedFields: Set<String>): String = buildString {
        appendLine("Project: ${command.projectId}")
        appendLine("Step: ${command.step.name}")
        appendLine("Locale: ${command.locale}")
        appendLine("Allowed fields: ${allowedFields.joinToString(", ")}")
        appendLine()
        appendLine("Current fields:")
        command.fields.forEach { (key, value) -> appendLine("- $key: ${WizardMarkdown.renderValue(value)}") }
        appendLine()
        appendLine("Resolved decisions:")
        command.decisions.forEach { decision ->
            appendLine("- ${decision.title}")
            appendLine("  chosenOptionId: ${decision.chosenOptionId ?: "none"}")
            appendLine("  chosenLabel: ${decision.options.find { it.id == decision.chosenOptionId }?.label ?: "none"}")
            appendLine("  rationale: ${decision.rationale ?: ""}")
        }
        appendLine()
        appendLine("Answered clarifications:")
        command.clarifications.forEach { clarification ->
            appendLine("- question: ${clarification.question}")
            appendLine("  reason: ${clarification.reason}")
            appendLine("  answer: ${clarification.answer ?: ""}")
        }
    }

    private companion object {
        const val AGENT_ID = "wizard-blocker-apply"
    }
}

object WizardStepFieldSchema {
    private val fieldsByStep: Map<FlowStepType, Set<String>> = mapOf(
        FlowStepType.IDEA to setOf("productName", "vision", "category"),
        FlowStepType.PROBLEM to setOf("coreProblem", "primaryAudience", "painPoints"),
        FlowStepType.FEATURES to setOf("features", "edges"),
        FlowStepType.MVP to setOf("goal", "mvpFeatures", "successCriteria"),
        FlowStepType.DESIGN to setOf("summary", "bundleName", "pageCount"),
        FlowStepType.ARCHITECTURE to setOf("architecture", "database", "deployment", "notes"),
        FlowStepType.BACKEND to setOf("framework", "apiStyle", "auth"),
        FlowStepType.FRONTEND to setOf("framework", "uiLibrary", "styling", "theme"),
        FlowStepType.REVIEW to setOf("confirmed"),
    )

    fun allowedFields(step: FlowStepType): Set<String> = fieldsByStep.getValue(step)
}

object WizardBlockerApplyJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseResult(raw: String, allowedFields: Set<String>): WizardBlockerApplyResult {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        return runCatching {
            val parsed = json.decodeFromString<ApplyAgentResponse>(cleaned)
            val updates = parsed.fieldUpdates
                .filterKeys { it in allowedFields }
            WizardBlockerApplyResult(
                message = parsed.message.ifBlank { "Die Antwort wurde beruecksichtigt." },
                fieldUpdates = updates,
                appliedFields = updates.keys.toList(),
            )
        }.getOrElse {
            WizardBlockerApplyResult(
                message = "Die Antwort wurde beruecksichtigt.",
                fieldUpdates = emptyMap(),
                appliedFields = emptyList(),
            )
        }
    }
}

@Serializable
private data class ApplyAgentResponse(
    val message: String = "Die Antwort wurde beruecksichtigt.",
    val fieldUpdates: Map<String, JsonElement> = emptyMap(),
)
