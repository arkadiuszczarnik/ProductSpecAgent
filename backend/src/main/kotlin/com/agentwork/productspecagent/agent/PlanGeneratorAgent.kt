package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
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
        return parsePlanResponse(rawResponse, projectId)
    }

    protected open suspend fun runAgent(prompt: String): String {
        return koogRunner?.run("You are a product implementation planner. Generate structured plans in JSON.", prompt)
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
            // Fallback: single epic with a note
            listOf(SpecTask(
                id = UUID.randomUUID().toString(), projectId = projectId,
                type = TaskType.EPIC, title = "Implementation Plan",
                description = raw.take(500), estimate = "XL", priority = 0,
                createdAt = now, updatedAt = now
            ))
        }
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
