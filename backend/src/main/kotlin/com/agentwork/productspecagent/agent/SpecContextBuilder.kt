package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.Clarification
import com.agentwork.productspecagent.domain.ClarificationStatus
import com.agentwork.productspecagent.domain.Decision
import com.agentwork.productspecagent.domain.DecisionStatus
import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.FlowStepStatus
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardFeatureInput
import com.agentwork.productspecagent.service.WizardService
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.springframework.stereotype.Component

@Component
class SpecContextBuilder(
    private val projectService: ProjectService,
    private val wizardService: WizardService? = null
) {
    fun buildContext(projectId: String): String {
        val projectResponse = projectService.getProject(projectId)
        val project = projectResponse.project
        val flowState = projectResponse.flowState

        val completedSteps = flowState.steps
            .filter { it.status == FlowStepStatus.COMPLETED }

        val stepSummaries = completedSteps.mapNotNull { step ->
            val fileName = step.stepType.name.lowercase() + ".md"
            val content = projectService.readSpecFile(projectId, fileName)
            if (content != null) "### ${step.stepType.name}\n$content" else null
        }.joinToString("\n\n")

        return buildString {
            appendLine("=== PROJECT CONTEXT ===")
            appendLine("Project: ${project.name}")
            appendLine("Current Step: ${flowState.currentStep.name}")
            appendLine()
            if (stepSummaries.isNotBlank()) {
                appendLine("=== COMPLETED STEPS ===")
                appendLine(stepSummaries)
                appendLine()
            }
            appendLine("=== END CONTEXT ===")
        }.trim()
    }

    fun buildWizardContext(
        wizardData: WizardData,
        currentStep: String,
        currentFields: Map<String, Any>,
        existingDecisions: List<Decision> = emptyList(),
        existingClarifications: List<Clarification> = emptyList()
    ): String {
        return buildString {
            appendLine("=== WIZARD CONTEXT ===")

            val completedSteps = wizardData.steps.filter { it.value.completedAt != null }
            if (completedSteps.isNotEmpty()) {
                appendLine("=== COMPLETED STEPS ===")
                for ((stepName, stepData) in completedSteps) {
                    appendLine("### $stepName")
                    for ((key, value) in stepData.fields) {
                        appendLine("$key: $value")
                    }
                    appendLine()
                }
            }

            appendLine("=== CURRENT STEP: $currentStep ===")
            for ((key, value) in currentFields) {
                appendLine("$key: $value")
            }
            appendLine()

            if (currentStep == FlowStepType.FEATURES.name) {
                val category = extractCategoryFromIdea(wizardData, currentFields, currentStep)
                val parsedFeatures = IdeaToSpecAgent.parseWizardFeatures(currentFields, category)
                val graphBlock = renderFeaturesBlock(parsedFeatures, category)
                if (graphBlock.isNotBlank()) {
                    appendLine(graphBlock)
                    appendLine()
                }
            }

            val stepDecisions = existingDecisions.filter { it.stepType.name == currentStep }
            val stepClarifications = existingClarifications.filter { it.stepType.name == currentStep }

            if (stepClarifications.isNotEmpty() || stepDecisions.isNotEmpty()) {
                appendLine("=== PREVIOUS CLARIFICATIONS & DECISIONS FOR THIS STEP ===")
                appendLine("The following have already been raised for the current step. DO NOT repeat them. Do NOT emit a new [CLARIFICATION_NEEDED] or [DECISION_NEEDED] marker for the same topic. Only emit a new marker if the open question is genuinely different and not covered below.")
                appendLine()

                val answeredClars = stepClarifications.filter { it.status == ClarificationStatus.ANSWERED }
                val openClars = stepClarifications.filter { it.status == ClarificationStatus.OPEN }
                val resolvedDecs = stepDecisions.filter { it.status == DecisionStatus.RESOLVED }
                val pendingDecs = stepDecisions.filter { it.status == DecisionStatus.PENDING }

                if (answeredClars.isNotEmpty()) {
                    appendLine("ANSWERED CLARIFICATIONS (treat these facts as confirmed, build on them):")
                    for (c in answeredClars) {
                        appendLine("- Q: ${c.question}")
                        appendLine("  A: ${c.answer ?: "(empty)"}")
                    }
                    appendLine()
                }
                if (openClars.isNotEmpty()) {
                    appendLine("OPEN CLARIFICATIONS (already asked, still waiting for the user — do NOT ask again):")
                    for (c in openClars) {
                        appendLine("- ${c.question}")
                    }
                    appendLine()
                }
                if (resolvedDecs.isNotEmpty()) {
                    appendLine("RESOLVED DECISIONS (treat as final, do not re-open):")
                    for (d in resolvedDecs) {
                        val chosen = d.options.firstOrNull { it.id == d.chosenOptionId }?.label ?: d.chosenOptionId
                        appendLine("- ${d.title} -> ${chosen ?: "(unresolved)"}")
                    }
                    appendLine()
                }
                if (pendingDecs.isNotEmpty()) {
                    appendLine("PENDING DECISIONS (already raised, waiting for the user — do NOT raise again):")
                    for (d in pendingDecs) {
                        appendLine("- ${d.title}")
                    }
                    appendLine()
                }
            }

            appendLine("=== END WIZARD CONTEXT ===")
        }.trim()
    }

    /**
     * Assembles a prompt context for the [FeatureProposalAgent]. Reads the existing
     * idea/problem/target_audience/scope/mvp spec files (skipping any that are missing or empty)
     * and appends the project's category derived from the wizard IDEA step.
     */
    fun buildProposalContext(projectId: String): String {
        val sb = StringBuilder()
        val specFiles = listOf("idea.md", "problem.md", "target_audience.md", "scope.md", "mvp.md")
        for (fileName in specFiles) {
            val content = projectService.readSpecFile(projectId, fileName)?.trim()
            if (!content.isNullOrBlank()) {
                sb.appendLine("## $fileName")
                sb.appendLine(content)
                sb.appendLine()
            }
        }
        val category = wizardService?.let {
            runCatching { it.getWizardData(projectId) }.getOrNull()
        }?.let { wizardData ->
            extractCategoryFromIdea(wizardData, currentFields = emptyMap(), currentStep = null)
        }
        sb.appendLine("Category: ${category ?: "—"}")
        return sb.toString().trim()
    }

    /**
     * Extracts the project's category from wizard state. Prefers the `currentFields` map when the
     * caller is *currently* on the IDEA step (since those edits may not yet be persisted), and falls
     * back to the previously saved `WizardData.steps["IDEA"].fields["category"]` entry.
     */
    private fun extractCategoryFromIdea(
        wizardData: WizardData,
        currentFields: Map<String, Any>,
        currentStep: String?,
    ): String? {
        if (currentStep == FlowStepType.IDEA.name) {
            (currentFields["category"] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val ideaFields = wizardData.steps[FlowStepType.IDEA.name]?.fields ?: return null
        val raw = ideaFields["category"] ?: return null
        val value = (raw as? JsonPrimitive)?.contentOrNull ?: raw.toString()
        return value.trim().takeIf { it.isNotBlank() && it != "null" }
    }

    companion object {
        /**
         * Renders the normalized features list as a compact, prompt-friendly block used by the
         * FEATURES wizard step. Empty input produces an empty string; callers decide whether to
         * append surrounding whitespace.
         */
        fun renderFeaturesBlock(
            features: List<WizardFeatureInput>,
            category: String?,
        ): String {
            if (features.isEmpty()) return ""
            val sb = StringBuilder()
            sb.appendLine("Features & Dependencies (Category: ${category ?: "—"}):")

            val hasOutgoing = mutableSetOf<String>()
            features.forEach { f -> f.dependsOn.forEach { hasOutgoing.add(it) } }
            val isolated = features.filter { it.dependsOn.isEmpty() && it.id !in hasOutgoing }

            for (f in features) {
                val scopeLabel = when {
                    f.scopes.containsAll(setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)) ->
                        "Frontend + Backend"
                    f.scopes == setOf(FeatureScope.FRONTEND) -> "Frontend"
                    f.scopes == setOf(FeatureScope.BACKEND) -> "Backend"
                    f.scopes.isEmpty() -> "Core"
                    else -> f.scopes.joinToString("+") { it.name }
                }
                val deps = if (f.dependsOn.isEmpty()) "—" else f.dependsOn.joinToString(", ")
                sb.appendLine("- [${f.id}] ${f.title} ($scopeLabel) — depends on: $deps")
                f.scopeFields
                    .filter { it.value.isNotBlank() }
                    .forEach { (k, v) ->
                        sb.appendLine("  ${k.replaceFirstChar { it.uppercase() }}: $v")
                    }
            }

            sb.appendLine(
                if (isolated.isEmpty()) "Isolated nodes: —"
                else "Isolated nodes: ${isolated.joinToString(", ") { it.id }}"
            )
            return sb.toString().trimEnd()
        }
    }
}
