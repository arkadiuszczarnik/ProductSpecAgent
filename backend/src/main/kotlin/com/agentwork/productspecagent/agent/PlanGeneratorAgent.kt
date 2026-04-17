package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.WizardFeatureInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
open class PlanGeneratorAgent(
    private val contextBuilder: SpecContextBuilder,
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
            appendLine("""{"epics":[{"title":"Epic title","description":"desc","estimate":"L","specSection":"SCOPE","stories":[{"title":"Story","description":"desc","estimate":"M","tasks":[{"title":"Task","description":"desc","estimate":"S"}]}]}]}""")
            appendLine("Generate 2-4 epics, each with 1-3 stories, each story with 1-3 tasks.")
        }

        val rawResponse = runAgent(prompt)
        return parsePlanResponse(rawResponse, projectId, source = TaskSource.PLAN_GENERATOR)
    }

    /**
     * Generates a plan tree (1 EPIC + Stories + Tasks) for a single feature defined by the user
     * in the wizard FEATURES step. Marks all created tasks with [TaskSource.WIZARD] so the
     * caller can replace them idempotently on re-run.
     *
     * The prompt is scope-aware (frontend-only, backend-only, fullstack, library) and the epic
     * estimate is returned by the LLM via the `epicEstimate` field (validated against XS/S/M/L/XL,
     * falls back to "M").
     */
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
            appendLine("- Title: ${input.title}")
            appendLine("- Description: ${input.description}")
            appendLine("- Scopes: ${input.scopes.joinToString(", ") { it.name }.ifBlank { "(Library / Core)" }}")
            if (input.scopeFields.any { it.value.isNotBlank() }) {
                appendLine("- Scope fields:")
                for ((k, v) in input.scopeFields) {
                    if (v.isNotBlank()) appendLine("  - $k: $v")
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
            appendLine("Generate 1-3 stories, each with 1-3 tasks. epicEstimate must be one of XS, S, M, L, XL.")
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
            else -> return  // both scopes → no hint
        }
        appendLine(hint)
    }

    protected open suspend fun runAgent(prompt: String): String {
        return koogRunner?.run("You are a product implementation planner. Generate structured plans in JSON.", prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
    }

    private fun parsePlanResponse(raw: String, projectId: String, source: TaskSource): List<SpecTask> {
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
                    source = source,
                    createdAt = now, updatedAt = now
                ))

                for (storyDef in epicDef.stories) {
                    val storyId = UUID.randomUUID().toString()
                    tasks.add(SpecTask(
                        id = storyId, projectId = projectId, parentId = epicId,
                        type = TaskType.STORY, title = storyDef.title,
                        description = storyDef.description, estimate = storyDef.estimate,
                        priority = priority++, source = source,
                        createdAt = now, updatedAt = now
                    ))

                    for (taskDef in storyDef.tasks) {
                        tasks.add(SpecTask(
                            id = UUID.randomUUID().toString(), projectId = projectId,
                            parentId = storyId, type = TaskType.TASK,
                            title = taskDef.title, description = taskDef.description,
                            estimate = taskDef.estimate, priority = priority++,
                            source = source,
                            createdAt = now, updatedAt = now
                        ))
                    }
                }
            }
            tasks
        } catch (e: Exception) {
            // Fallback: single epic with a note
            listOf(SpecTask(
                id = UUID.randomUUID().toString(), projectId = projectId,
                type = TaskType.EPIC, title = "Implementation Plan",
                description = raw.take(500), estimate = "XL", priority = 0,
                source = source,
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
        val validEstimates = setOf("XS", "S", "M", "L", "XL")
        val epicEstimate = parsed?.epicEstimate?.takeIf { it in validEstimates } ?: "M"

        val epicId = UUID.randomUUID().toString()
        tasks.add(SpecTask(
            id = epicId, projectId = projectId, type = TaskType.EPIC,
            title = input.title, description = input.description,
            estimate = epicEstimate, priority = priority++,
            specSection = FlowStepType.FEATURES,
            source = TaskSource.WIZARD,
            createdAt = now, updatedAt = now
        ))

        for (storyDef in parsed?.stories ?: emptyList()) {
            val storyId = UUID.randomUUID().toString()
            tasks.add(SpecTask(
                id = storyId, projectId = projectId, parentId = epicId,
                type = TaskType.STORY, title = storyDef.title,
                description = storyDef.description, estimate = storyDef.estimate,
                priority = priority++, source = TaskSource.WIZARD,
                createdAt = now, updatedAt = now
            ))
            for (taskDef in storyDef.tasks) {
                tasks.add(SpecTask(
                    id = UUID.randomUUID().toString(), projectId = projectId,
                    parentId = storyId, type = TaskType.TASK,
                    title = taskDef.title, description = taskDef.description,
                    estimate = taskDef.estimate, priority = priority++,
                    source = TaskSource.WIZARD,
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
private data class StoryDef(
    val title: String, val description: String = "", val estimate: String = "M",
    val tasks: List<TaskDef> = emptyList()
)

@Serializable
private data class TaskDef(
    val title: String, val description: String = "", val estimate: String = "S"
)

@Serializable
private data class FeaturePlanResponse(
    val epicEstimate: String = "M",
    val stories: List<StoryDef> = emptyList(),
)
