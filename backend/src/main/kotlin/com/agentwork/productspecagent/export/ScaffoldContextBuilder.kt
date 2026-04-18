package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.*
import org.springframework.stereotype.Component

@Component
class ScaffoldContextBuilder(
    private val projectService: ProjectService,
    private val taskService: TaskService,
    private val decisionService: DecisionService
) {
    private fun readNonBlankSpec(projectId: String, fileName: String): String? {
        val content = projectService.readSpecFile(projectId, fileName)?.trim()
        return if (content.isNullOrBlank()) null else content
    }

    fun build(projectId: String): ScaffoldContext {
        val projectResp = projectService.getProject(projectId)
        val project = projectResp.project
        val tasks = taskService.listTasks(projectId)
        val decisions = decisionService.listDecisions(projectId)

        val epics = tasks.filter { it.type == TaskType.EPIC }.sortedBy { it.priority }

        val features = epics.mapIndexed { i, epic ->
            val number = i + 1
            val slug = epic.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
            val stories = tasks.filter { it.parentId == epic.id && it.type == TaskType.STORY }
            val subtasks = tasks.filter { t ->
                stories.any { s -> s.id == t.parentId } && t.type == TaskType.TASK
            }

            FeatureContext(
                number = number,
                title = epic.title,
                slug = slug,
                filename = "${String.format("%02d", number)}-$slug.md",
                description = epic.description,
                estimate = epic.estimate,
                dependencies = if (i == 0) "—" else "Feature $i",
                stories = stories.mapIndexed { si, s ->
                    StoryContext(si + 1, s.title, s.description)
                },
                acceptanceCriteria = subtasks.map { TaskContext(it.title, it.description) },
                tasks = (stories + subtasks).map { TaskContext(it.title, it.description) }
            )
        }

        val resolvedDecisions = decisions
            .filter { it.status == DecisionStatus.RESOLVED }
            .map { d ->
                val chosen = d.options.find { it.id == d.chosenOptionId }
                DecisionContext(
                    title = d.title,
                    chosen = chosen?.label ?: "N/A",
                    rationale = d.rationale ?: ""
                )
            }

        val scopeContent = readNonBlankSpec(projectId, "scope.md")
        val mvpContent = readNonBlankSpec(projectId, "mvp.md")
        val problemContent = readNonBlankSpec(projectId, "problem.md")
        val targetAudienceContent = readNonBlankSpec(projectId, "target_audience.md")
        val architectureContent = readNonBlankSpec(projectId, "architecture.md")
        val backendContent = readNonBlankSpec(projectId, "backend.md")
        val frontendContent = readNonBlankSpec(projectId, "frontend.md")

        return ScaffoldContext(
            projectName = project.name,
            features = features,
            decisions = resolvedDecisions,
            scopeContent = scopeContent,
            mvpContent = mvpContent,
            techStack = "See SPEC.md for full tech stack details.",
            problemContent = problemContent,
            targetAudienceContent = targetAudienceContent,
            architectureContent = architectureContent,
            backendContent = backendContent,
            frontendContent = frontendContent
        )
    }
}
