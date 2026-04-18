package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.FlowStepStatus
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardFeatureInput
import com.agentwork.productspecagent.service.WizardService
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
open class SpecContextBuilder(
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
        currentFields: Map<String, Any>
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

            // FEATURES step: render graph block when applicable
            if (currentStep == FlowStepType.FEATURES.name) {
                val rawFeatures = currentFields["features"]
                val category = (completedSteps["IDEA"]?.fields?.get("category")
                    ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() })
                val parsedFeatures = IdeaToSpecAgent.parseWizardFeatures(rawFeatures, category)
                if (parsedFeatures.isNotEmpty()) {
                    appendLine()
                    appendLine(renderFeaturesBlock(parsedFeatures, category))
                }
            }

            appendLine("=== END WIZARD CONTEXT ===")
        }.trim()
    }

    open fun buildProposalContext(projectId: String): String {
        val svc = wizardService ?: throw IllegalStateException("WizardService not available")
        val sb = StringBuilder()
        listOf("idea.md", "problem.md", "target_audience.md", "scope.md", "mvp.md").forEach { f ->
            projectService.readSpecFile(projectId, f)?.let {
                sb.appendLine("## $f").appendLine(it).appendLine()
            }
        }
        val wizardData = svc.getWizardData(projectId)
        val category = wizardData.steps["IDEA"]?.fields?.get("category")
            ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: "—"
        sb.appendLine("Category: $category")
        return sb.toString().trim()
    }

    companion object {
        fun renderFeaturesBlock(
            features: List<WizardFeatureInput>,
            category: String?,
        ): String {
            if (features.isEmpty()) return ""
            val sb = StringBuilder()
            sb.appendLine("Features & Dependencies (Category: ${category ?: "—"}):")

            // Outgoing edges: a feature has an outgoing edge if another feature depends on it
            val hasOutgoing = mutableSetOf<String>()
            features.forEach { f -> f.dependsOn.forEach { hasOutgoing.add(it) } }

            val isolated = features.filter { it.dependsOn.isEmpty() && it.id !in hasOutgoing }

            for (f in features) {
                val scopeLabel = when {
                    f.scopes == setOf(FeatureScope.FRONTEND) -> "Frontend"
                    f.scopes == setOf(FeatureScope.BACKEND) -> "Backend"
                    f.scopes.containsAll(setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)) -> "Frontend + Backend"
                    f.scopes.isEmpty() -> "Core"
                    else -> f.scopes.joinToString("+")
                }
                val deps = if (f.dependsOn.isEmpty()) "—" else f.dependsOn.joinToString(", ")
                sb.appendLine("- [${f.id}] ${f.title} ($scopeLabel) — depends on: $deps")
                f.scopeFields.filter { it.value.isNotBlank() }.forEach { (k, v) ->
                    sb.appendLine("  ${k.replaceFirstChar { it.uppercase() }}: $v")
                }
            }
            if (isolated.isNotEmpty()) {
                sb.appendLine("Isolated nodes: ${isolated.joinToString(", ") { it.id }}")
            } else {
                sb.appendLine("Isolated nodes: —")
            }
            return sb.toString().trimEnd()
        }
    }
}
