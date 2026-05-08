package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.KoogAgentRunner
import com.agentwork.productspecagent.agent.SpecContextBuilder
import com.agentwork.productspecagent.agent.AgentResponseMarkers
import com.agentwork.productspecagent.domain.FlowStepStatus
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.ProductCategory
import com.agentwork.productspecagent.domain.WizardClientActionDto
import com.agentwork.productspecagent.domain.WizardCreatedArtifacts
import com.agentwork.productspecagent.domain.WizardProgressionView
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.domain.WizardStepView
import com.agentwork.productspecagent.domain.emptyWizardProgressionView
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Instant

interface WizardStepCompletion {
    suspend fun complete(command: CompleteWizardStep): WizardStepCompletionResult
}

data class CompleteWizardStep(
    val projectId: String,
    val step: FlowStepType,
    val fields: Map<String, Any>,
    val locale: String = "en",
)

data class WizardStepCompletionResult(
    val message: String,
    val nextStep: FlowStepType?,
    val exportTriggered: Boolean,
    val decisionId: String? = null,
    val clarificationId: String? = null,
    val progression: WizardProgressionView = emptyWizardProgressionView(),
    val action: WizardClientActionDto = stayClientAction,
    val artifacts: WizardCreatedArtifacts = WizardCreatedArtifacts(),
)

class WizardStepNotVisibleException(
    step: FlowStepType,
    category: ProductCategory?,
) : RuntimeException("Wizard step ${step.name} is not visible for category ${category?.wireValue ?: "default"}")

class WizardStepNotCurrentException(
    step: FlowStepType,
    currentStep: String?,
) : RuntimeException("Wizard step ${step.name} is not the current wizard step (${currentStep ?: "none"})")

interface WizardCompletionAgent {
    suspend fun respond(systemPrompt: String, userPrompt: String): String
}

@Component
class KoogWizardCompletionAgent(
    private val koogRunner: KoogAgentRunner,
) : WizardCompletionAgent {
    override suspend fun respond(systemPrompt: String, userPrompt: String): String =
        koogRunner.run(AGENT_ID, systemPrompt, userPrompt)

    private companion object {
        const val AGENT_ID = "idea-to-spec"
    }
}

@Service
class WizardStepCompletionService(
    private val contextBuilder: SpecContextBuilder,
    private val projectService: ProjectService,
    private val promptService: PromptService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val wizardService: WizardService,
    private val completionAgent: WizardCompletionAgent,
    private val taskService: TaskService? = null,
    private val progressionPolicy: WizardProgressionPolicy = WizardProgressionPolicy(),
) : WizardStepCompletion, WizardProgression {

    private val logger = LoggerFactory.getLogger(WizardStepCompletionService::class.java)

    override suspend fun complete(command: CompleteWizardStep): WizardStepCompletionResult {
        val wizardData = wizardService.getWizardData(command.projectId)
        val plan = progressionPolicy.planFor(wizardData)
        if (command.step !in plan.visibleSteps) {
            throw WizardStepNotVisibleException(command.step, plan.category)
        }
        val flowState = projectService.getFlowState(command.projectId)
        val statusesByStep = flowState.steps.associate { it.stepType to it.status }
        val currentStep = currentVisibleStep(plan, statusesByStep, flowState.currentStep)
        if (command.step != currentStep) {
            throw WizardStepNotCurrentException(command.step, currentStep?.name)
        }
        val isLastStep = plan.isTerminal(command.step)
        val nextStep = if (!isLastStep) plan.nextAfter(command.step) else null
        val stepName = command.step.name
        val wizardContext = contextBuilder.buildWizardContext(wizardData, stepName, command.fields)

        val wizardCategory = wizardData.steps["IDEA"]?.fields?.get("category")
            ?.let { runCatching { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.getOrNull() }

        val prompt = buildWizardStepFeedbackPrompt(stepName, command.fields, wizardCategory, isLastStep)

        val baseSystemPrompt = promptService.get("idea-base")
        val stepPrompt = buildStepPrompt(command.step)
        val localeInstruction = buildLocaleInstruction(command.locale)
        val systemPrompt = "$baseSystemPrompt\n\n$stepPrompt\n\n$localeInstruction\n\n$wizardContext"

        val rawResponse = completionAgent.respond(systemPrompt, prompt)
        val decisionTitle = AgentResponseMarkers.extractDecisionTitle(rawResponse)
        val clarification = AgentResponseMarkers.extractClarification(rawResponse)
        val cleanMessage = AgentResponseMarkers.clean(rawResponse)

        logger.info(
            "completeWizardStep({}) markers - decision={}, clarification={}, isLastStep={}",
            stepName, decisionTitle, clarification?.first, isLastStep
        )

        var createdDecisionId: String? = null
        if (!isLastStep && decisionTitle != null) {
            createdDecisionId = decisionService.createDecision(command.projectId, decisionTitle, command.step).id
        }

        var createdClarificationId: String? = null
        if (!isLastStep && clarification != null) {
            createdClarificationId = clarificationService.createClarification(
                command.projectId,
                clarification.first,
                clarification.second,
                command.step,
            ).id
        }

        if (command.step == FlowStepType.FEATURES && taskService != null) {
            val parsedFeatures = WizardFeatureParser.parse(command.fields, wizardCategory)
            if (parsedFeatures.isNotEmpty()) {
                logger.info("completeWizardStep(FEATURES) syncing {} wizard feature tasks", parsedFeatures.size)
                taskService.replaceWizardFeatureTasks(command.projectId, parsedFeatures)
            }
        }

        val now = Instant.now().toString()
        val updatedSteps = flowState.steps.map { step ->
            when (step.stepType) {
                command.step -> step.copy(status = FlowStepStatus.COMPLETED, updatedAt = now)
                nextStep -> step.copy(status = FlowStepStatus.IN_PROGRESS, updatedAt = now)
                else -> step
            }
        }
        projectService.updateFlowState(
            command.projectId,
            flowState.copy(steps = updatedSteps, currentStep = nextStep ?: command.step),
        )

        wizardService.saveStepData(
            command.projectId,
            stepName,
            WizardStepData(
                fields = command.fields.mapValues { (_, value) -> WizardMarkdown.toJsonElement(value) },
                completedAt = now,
            ),
        )

        if (isLastStep) {
            val fullContext = contextBuilder.buildContext(command.projectId)
            val summaryPrompt = buildString {
                appendLine("Based on all the information gathered in the wizard steps below, generate a complete product specification summary in markdown format.")
                appendLine("Include sections for: Product Overview, Problem, Target Audience, Scope, MVP, and any technical decisions made.")
                appendLine()
                appendLine(fullContext)
            }
            val summarySystemPrompt = "$baseSystemPrompt\n\n$localeInstruction"
            val specContent = completionAgent.respond(summarySystemPrompt, summaryPrompt)
            projectService.saveSpecFile(command.projectId, "spec.md", specContent)
        } else {
            projectService.regenerateDocsScaffold(command.projectId)
        }

        val progression = snapshotFor(command.projectId)
        val action = when {
            isLastStep -> openExportClientAction
            nextStep != null -> showStepAction(nextStep)
            else -> stayClientAction
        }

        return WizardStepCompletionResult(
            message = cleanMessage,
            nextStep = nextStep,
            exportTriggered = isLastStep,
            decisionId = createdDecisionId,
            clarificationId = createdClarificationId,
            progression = progression,
            action = action,
            artifacts = WizardCreatedArtifacts(
                decisionIds = listOfNotNull(createdDecisionId),
                clarificationIds = listOfNotNull(createdClarificationId),
            ),
        )
    }

    override fun snapshot(projectId: String): WizardProgressionView =
        snapshotFor(projectId)

    private fun snapshotFor(projectId: String): WizardProgressionView {
        val wizardData = wizardService.getWizardData(projectId)
        val plan = progressionPolicy.planFor(wizardData)
        val flowState = projectService.getFlowState(projectId)
        val statusesByStep = flowState.steps.associate { it.stepType to it.status }
        val completedVisibleSteps = plan.visibleSteps.isNotEmpty() &&
            plan.visibleSteps.all { statusesByStep[it] == FlowStepStatus.COMPLETED }
        val current = currentVisibleStep(plan, statusesByStep, flowState.currentStep)

        return WizardProgressionView(
            category = plan.category?.wireValue,
            steps = plan.visibleSteps.map { step ->
                WizardStepView(
                    step = step.name,
                    status = statusesByStep[step]?.name ?: FlowStepStatus.OPEN.name,
                    finalVisibleStep = plan.isTerminal(step),
                )
            },
            currentStep = current?.name,
            status = if (completedVisibleSteps) "READY_FOR_EXPORT" else "IN_PROGRESS",
            primaryAction = if (completedVisibleSteps) {
                openExportPrimaryAction
            } else {
                current?.let(::completeStepAction) ?: nonePrimaryAction
            },
        )
    }

    private fun currentVisibleStep(
        plan: WizardProgressionPlan,
        statusesByStep: Map<FlowStepType, FlowStepStatus>,
        flowCurrentStep: FlowStepType,
    ): FlowStepType? =
        flowCurrentStep
            .takeIf { it in plan.visibleSteps && statusesByStep[it] != FlowStepStatus.COMPLETED }
            ?: plan.visibleSteps.firstOrNull { statusesByStep[it] != FlowStepStatus.COMPLETED }
            ?: plan.visibleSteps.lastOrNull()

    private fun buildWizardStepFeedbackPrompt(
        step: String,
        fields: Map<String, Any>,
        category: String? = null,
        isLastStep: Boolean = false,
    ): String {
        val fieldsDescription = fields.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }

        if (isLastStep) {
            return buildString {
                appendLine("The user just completed the FINAL wizard step: $step")
                appendLine("Their input for this step:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("This is the last step of the wizard. The specification is now complete.")
                appendLine("Provide a short, positive closing message (2-3 sentences) acknowledging the user's input.")
                appendLine("Do NOT ask for clarifications. Do NOT propose decisions. Do NOT emit any markers.")
            }
        }

        return when (step) {
            "FEATURES" -> buildString {
                appendLine("The user just completed the FEATURES wizard step.")
                appendLine()

                val parsedFeatures = WizardFeatureParser.parse(fields, category)
                if (parsedFeatures.isNotEmpty()) {
                    appendLine(SpecContextBuilder.renderFeaturesBlock(parsedFeatures, category))
                    appendLine()
                } else {
                    appendLine(fieldsDescription)
                    appendLine()
                }

                appendLine("Validator rules for the FEATURES step:")
                appendLine("- If the graph contains isolated nodes (no incoming and no outgoing edges), ask the user whether that is intentional.")
                appendLine("- If a feature's scope seems inconsistent with its title (e.g. 'Login UI' with BACKEND only), emit a clarification.")
                appendLine("- If the category is SaaS / Mobile / Desktop and obvious core features are missing (e.g. Auth, Registration), emit a clarification.")
                appendLine("Otherwise remain silent on those points.")
                appendLine()
                appendLine("Provide brief, helpful feedback about the feature graph.")
                appendLine("Be encouraging and mention any suggestions for improvement if applicable.")
                appendLine()
                appendLine(promptService.get("idea-marker-reminder"))
            }
            "IDEA" -> buildString {
                appendLine("The user just completed the IDEA wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("IMPORTANT: This is the IDEA step. Focus ONLY on the idea itself. Do NOT discuss problem statement, target audience, value proposition, or technical details – these are handled in later steps (PROBLEM, FEATURES, etc.).")
                appendLine()
                appendLine("Analyze ONLY the idea:")
                appendLine("1. Is the product idea clearly described? Can you understand what the product is supposed to DO?")
                appendLine("2. Does the chosen category fit?")
                appendLine("3. Is the product name clear and fitting?")
                appendLine("4. Is the vision concrete enough to work with, or is it too vague?")
                appendLine()
                appendLine("If the vision is too vague (e.g. just a few words), ask the user to elaborate on WHAT the product does – not WHY or for WHOM (those come later).")
                appendLine("If the idea is clear enough, acknowledge it and confirm it as a solid starting point.")
                appendLine()
                appendLine(promptService.get("idea-marker-reminder"))
            }
            "PROBLEM" -> buildString {
                appendLine("The user just completed the PROBLEM wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("This step now covers BOTH the core problem AND the primary target audience + their pain points.")
                appendLine("Analyze the combined problem-and-audience definition:")
                appendLine("1. Is the core problem clearly defined and specific enough?")
                appendLine("2. Is the primary audience concrete (not 'everyone' or 'users')?")
                appendLine("3. Are the pain points tied to the stated audience and problem?")
                appendLine()
                appendLine("The generated problem section should document problem, audience, and pain points together in one coherent section.")
                appendLine("If the audience is too broad or a strategic choice is needed (e.g., B2B vs B2C), use [DECISION_NEEDED].")
                appendLine("If there are contradictions or missing aspects, use [CLARIFICATION_NEEDED].")
                appendLine("Be encouraging and constructive.")
                appendLine()
                appendLine(promptService.get("idea-marker-reminder"))
            }
            "ARCHITECTURE" -> buildString {
                appendLine("The user just completed the ARCHITECTURE wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("Analyze the architecture:")
                appendLine("1. Does the tech stack fit the requirements and team skills?")
                appendLine("2. Is the system design appropriate for the scale?")
                appendLine("3. Are there important architectural trade-offs to consider?")
                appendLine()
                appendLine("If there is a major architectural choice (e.g., monolith vs. microservices, SQL vs. NoSQL), use [DECISION_NEEDED].")
                appendLine("If details are unclear, use [CLARIFICATION_NEEDED].")
                appendLine("Be encouraging and constructive.")
                appendLine()
                appendLine(promptService.get("idea-marker-reminder"))
            }
            else -> buildString {
                appendLine("The user just completed wizard step: $step")
                appendLine("Their input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("Please provide brief, helpful feedback about their input for this step.")
                appendLine("Be encouraging and mention any suggestions for improvement if applicable.")
                appendLine()
                appendLine(promptService.get("idea-marker-reminder"))
            }
        }
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
            "zh" to "中文", "ko" to "한국語", "ru" to "Русский",
        )[langCode]

        return if (languageName != null) {
            "IMPORTANT: Always respond in $languageName ($langCode). Do not switch languages."
        } else {
            "IMPORTANT: Always respond in the language with code '$langCode'. Do not switch languages."
        }
    }

}
