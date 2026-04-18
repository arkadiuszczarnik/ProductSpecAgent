package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.springframework.stereotype.Component

@Component
class ScaffoldContextBuilder(
    private val projectService: ProjectService,
    private val taskService: TaskService,
    private val decisionService: DecisionService,
    private val wizardService: WizardService? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

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

        // Build id→title map for dependency resolution
        val idToTitle = epics.associate { it.id to it.title }

        // Load wizard features for scope enrichment (best-effort, null if unavailable)
        val wizardFeaturesByTitle: Map<String, WizardFeature> = loadWizardFeaturesByTitle(projectId)

        val features = epics.mapIndexed { i, epic ->
            val number = i + 1
            val slug = epic.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
            val stories = tasks.filter { it.parentId == epic.id && it.type == TaskType.STORY }
            val subtasks = tasks.filter { t ->
                stories.any { s -> s.id == t.parentId } && t.type == TaskType.TASK
            }

            val dependencies = if (epic.dependencies.isEmpty()) {
                "—"
            } else {
                epic.dependencies
                    .mapNotNull { idToTitle[it] }
                    .joinToString(", ")
                    .ifBlank { "—" }
            }

            val wizardFeature = wizardFeaturesByTitle[epic.title]

            FeatureContext(
                number = number,
                title = epic.title,
                slug = slug,
                filename = "${String.format("%02d", number)}-$slug.md",
                description = epic.description,
                estimate = epic.estimate,
                dependencies = dependencies,
                stories = stories.mapIndexed { si, s ->
                    StoryContext(si + 1, s.title, s.description)
                },
                acceptanceCriteria = subtasks.map { TaskContext(it.title, it.description) },
                tasks = (stories + subtasks).map { TaskContext(it.title, it.description) },
                scope = scopeLabel(wizardFeature?.scopes),
                scopeFields = wizardFeature?.scopeFields ?: emptyMap(),
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

    /**
     * Reads wizard features from the FEATURES step and returns them indexed by title.
     * Returns an empty map if wizard service is unavailable or no features are stored.
     */
    private fun loadWizardFeaturesByTitle(projectId: String): Map<String, WizardFeature> {
        val svc = wizardService ?: return emptyMap()
        return try {
            val wizardData = svc.getWizardData(projectId)
            val featuresElement = wizardData.steps["FEATURES"]?.fields?.get("features")
                ?: return emptyMap()
            // The frontend store writes features as a flat JsonArray<WizardFeature>, NOT
            // as a WizardFeatureGraph object. Edges are stored as a separate sibling field.
            val features = json.decodeFromJsonElement<List<WizardFeature>>(featuresElement)
            features.associateBy { it.title }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun scopeLabel(scopes: Set<FeatureScope>?): String? = when {
        scopes == null -> null
        scopes == setOf(FeatureScope.FRONTEND) -> "Frontend"
        scopes == setOf(FeatureScope.BACKEND) -> "Backend"
        scopes.size == 2 && scopes.containsAll(setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)) -> "Frontend + Backend"
        scopes.isEmpty() -> "Core"
        else -> null
    }
}
