package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.WizardFeatureInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private val VALID_ESTIMATES = linkedSetOf("XS", "S", "M", "L", "XL")

private fun validEstimate(raw: String?): String = raw?.takeIf { it in VALID_ESTIMATES } ?: "M"

private fun sanitizeForPrompt(raw: String, maxLen: Int = 500): String =
    raw.replace(Regex("\\s+"), " ").trim().take(maxLen)

@Service
open class PlanGeneratorAgent(
    private val contextBuilder: SpecContextBuilder,
    private val promptService: PromptService,
    private val koogRunner: KoogAgentRunner? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generatePlan(projectId: String): List<SpecTask> {
        val context = contextBuilder.buildContext(projectId)
        val prompt = buildString {
            appendLine("Based on this project context, generate a structured implementation plan.")
            appendLine()
            appendLine(context)
            appendLine()
            appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
            appendLine("""{"epics":[{"title":"Epic title","description":"desc","estimate":"L","specSection":"FEATURES","stories":[{"title":"Story","description":"desc","estimate":"M","tasks":[{"title":"Task","description":"desc","estimate":"S"}]}]}]}""")
            appendLine("Generate 2-4 epics, each with 1-3 stories, each story with 1-3 tasks.")
        }

        val rawResponse = runAgent(prompt)
        return parsePlanResponse(rawResponse, projectId)
    }

    suspend fun generatePlanForFeature(
        projectId: String,
        input: WizardFeatureInput,
        startPriority: Int,
    ): List<SpecTask> {
        val context = contextBuilder.buildContext(projectId)
        val prompt = buildString {
            appendLine("Break down a single product feature into a small implementation plan.")
            appendLine()
            appendLine("Feature:")
            appendLine("- Title: ${sanitizeForPrompt(input.title, 200)}")
            appendLine("- Description: ${sanitizeForPrompt(input.description, 500)}")
            appendLine("- Scopes: ${input.scopes.joinToString(", ") { it.name }.ifBlank { "(Library / Core)" }}")
            if (input.scopeFields.isNotEmpty()) {
                appendLine("- Scope fields:")
                for ((k, v) in input.scopeFields) {
                    if (v.isNotBlank()) appendLine("  - $k: ${sanitizeForPrompt(v, 200)}")
                }
            }
            appendLine()
            appendScopeHint(input.scopes)
            appendLine()
            appendLine("Project context:")
            appendLine(context)
            appendLine()
            appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
            appendLine("""{"epicEstimate":"M","stories":[{"title":"Story title","description":"desc","estimate":"M","tasks":[{"title":"Task","description":"desc","estimate":"S"}]}]}""")
            appendLine("Generate 1-3 stories, each with 1-3 tasks. epicEstimate must be one of ${VALID_ESTIMATES.joinToString(", ")}.")
            appendLine("Use the same language as the feature title/description.")
        }
        val rawResponse = runAgent(prompt)
        return parseFeaturePlanResponse(rawResponse, projectId, input, startPriority)
    }

    private fun StringBuilder.appendScopeHint(scopes: Set<FeatureScope>) {
        val hint = when {
            scopes == setOf(FeatureScope.FRONTEND) ->
                "This feature is Frontend-only. Generate ONLY UI-focused stories (Components, Screens, State, User-Interactions). No API or DB stories."
            scopes == setOf(FeatureScope.BACKEND) ->
                "This feature is Backend-only. Generate ONLY API / Data / Service stories. No UI stories."
            scopes.isEmpty() ->
                "This feature is a Library-Komponente. Focus stories on Public API, Types and Usage Examples."
            else -> return // beide Scopes → kein spezifischer Hint
        }
        appendLine(hint)
    }

    protected open suspend fun runAgent(prompt: String): String {
        return koogRunner?.run(promptService.get("plan-system"), prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
    }

    private fun parsePlanResponse(raw: String, projectId: String): List<SpecTask> {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val now = Instant.now().toString()
        val tasks = mutableListOf<SpecTask>()
        var priority = 0

        return try {
            val plan = json.decodeFromString<PlanAgentResponse>(jsonStr)

            for (epicDef in plan.epics) {
                val epicId = UUID.randomUUID().toString()
                tasks.add(SpecTask(
                    id = epicId, projectId = projectId, type = TaskType.EPIC,
                    title = epicDef.title, description = epicDef.description,
                    estimate = epicDef.estimate, priority = priority++,
                    specSection = parseSection(epicDef.specSection),
                    createdAt = now, updatedAt = now
                ))

                for (storyDef in epicDef.stories) {
                    val storyId = UUID.randomUUID().toString()
                    tasks.add(SpecTask(
                        id = storyId, projectId = projectId, parentId = epicId,
                        type = TaskType.STORY, title = storyDef.title,
                        description = storyDef.description, estimate = storyDef.estimate,
                        priority = priority++, createdAt = now, updatedAt = now
                    ))

                    for (taskDef in storyDef.tasks) {
                        tasks.add(SpecTask(
                            id = UUID.randomUUID().toString(), projectId = projectId,
                            parentId = storyId, type = TaskType.TASK,
                            title = taskDef.title, description = taskDef.description,
                            estimate = taskDef.estimate, priority = priority++,
                            createdAt = now, updatedAt = now
                        ))
                    }
                }
            }
            tasks
        } catch (e: Exception) {
            // Fallback: einzelner Epic mit Hinweis
            listOf(SpecTask(
                id = UUID.randomUUID().toString(), projectId = projectId,
                type = TaskType.EPIC, title = "Implementation Plan",
                description = raw.take(500), estimate = "XL", priority = 0,
                createdAt = now, updatedAt = now
            ))
        }
    }

    private fun parseFeaturePlanResponse(
        raw: String,
        projectId: String,
        input: WizardFeatureInput,
        startPriority: Int,
    ): List<SpecTask> {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val now = Instant.now().toString()
        val tasks = mutableListOf<SpecTask>()
        var priority = startPriority

        val parsed = runCatching { json.decodeFromString<FeaturePlanResponse>(jsonStr) }.getOrNull()
        val epicEstimate = validEstimate(parsed?.epicEstimate)

        val epicId = UUID.randomUUID().toString()
        tasks.add(SpecTask(
            id = epicId, projectId = projectId, type = TaskType.EPIC,
            title = input.title, description = input.description,
            estimate = epicEstimate, priority = priority++,
            specSection = FlowStepType.FEATURES,
            createdAt = now, updatedAt = now
        ))

        for (storyDef in parsed?.stories ?: emptyList()) {
            val storyId = UUID.randomUUID().toString()
            tasks.add(SpecTask(
                id = storyId, projectId = projectId, parentId = epicId,
                type = TaskType.STORY, title = storyDef.title,
                description = storyDef.description, estimate = validEstimate(storyDef.estimate),
                priority = priority++,
                createdAt = now, updatedAt = now
            ))
            for (taskDef in storyDef.tasks) {
                tasks.add(SpecTask(
                    id = UUID.randomUUID().toString(), projectId = projectId,
                    parentId = storyId, type = TaskType.TASK,
                    title = taskDef.title, description = taskDef.description,
                    estimate = validEstimate(taskDef.estimate), priority = priority++,
                    createdAt = now, updatedAt = now
                ))
            }
        }
        return tasks
    }

    private fun parseSection(section: String?): FlowStepType? {
        return try { section?.let { FlowStepType.valueOf(it) } } catch (_: Exception) { null }
    }
}

@Serializable
private data class PlanAgentResponse(val epics: List<EpicDef>)

@Serializable
private data class EpicDef(
    val title: String, val description: String = "", val estimate: String = "L",
    val specSection: String? = null, val stories: List<StoryDef> = emptyList()
)

@Serializable
private data class FeaturePlanResponse(
    val epicEstimate: String = "M",
    val stories: List<StoryDef> = emptyList(),
)

@Serializable
private data class StoryDef(
    val title: String, val description: String = "", val estimate: String = "M",
    val tasks: List<TaskDef> = emptyList()
)

@Serializable
private data class TaskDef(
    val title: String, val description: String = "", val estimate: String = "S"
)
