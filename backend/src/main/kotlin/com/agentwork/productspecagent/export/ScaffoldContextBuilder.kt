package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.agent.IdeaToSpecAgent
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.springframework.stereotype.Component

@Component
class ScaffoldContextBuilder(
    private val projectService: ProjectService,
    private val taskService: TaskService,
    private val decisionService: DecisionService,
    private val wizardService: WizardService? = null
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

        // Feature 22: Resolve wizard-features (with scopes + scopeFields) so per-feature docs
        // can carry an accurate Scope label and scope-specific sections. Null-safe: if the
        // wizard service isn't wired or the FEATURES step wasn't completed yet, we fall back
        // to an empty list (scope=null, scopeFields=empty → template sections stay suppressed).
        val wizardFeatures: List<WizardFeatureInput> = loadWizardFeatures(projectId)
        val wizardByTitle: Map<String, WizardFeatureInput> =
            wizardFeatures.associateBy { it.title.trim().lowercase() }

        val idToTitle: Map<String, String> = epics.associate { it.id to it.title }

        val features = epics.mapIndexed { i, epic ->
            val number = i + 1
            val slug = epic.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
            val stories = tasks.filter { it.parentId == epic.id && it.type == TaskType.STORY }
            val subtasks = tasks.filter { t ->
                stories.any { s -> s.id == t.parentId } && t.type == TaskType.TASK
            }

            // Feature 22: Replace the naive "Feature N-1" placeholder with real feature titles
            // resolved via SpecTask.dependencies (task IDs). Unknown/dangling IDs are silently
            // dropped. Multiple deps are joined with ", ".
            val resolvedDeps = epic.dependencies.mapNotNull { idToTitle[it] }
            val dependenciesLabel = if (resolvedDeps.isEmpty()) "—" else resolvedDeps.joinToString(", ")

            val wizardMatch = wizardByTitle[epic.title.trim().lowercase()]
            val scopeFields: Map<String, String> = wizardMatch?.scopeFields
                ?.filterValues { it.isNotBlank() }
                ?: emptyMap()

            FeatureContext(
                number = number,
                title = epic.title,
                slug = slug,
                filename = "${String.format("%02d", number)}-$slug.md",
                description = epic.description,
                estimate = epic.estimate,
                dependencies = dependenciesLabel,
                stories = stories.mapIndexed { si, s ->
                    StoryContext(si + 1, s.title, s.description)
                },
                acceptanceCriteria = subtasks.map { TaskContext(it.title, it.description) },
                tasks = (stories + subtasks).map { TaskContext(it.title, it.description) },
                scope = scopeLabel(wizardMatch?.scopes),
                scopeFields = scopeFields,
                hasUiComponents = scopeFields["uiComponents"]?.isNotBlank() == true,
                hasScreens = scopeFields["screens"]?.isNotBlank() == true,
                hasUserInteractions = scopeFields["userInteractions"]?.isNotBlank() == true,
                hasApiEndpoints = scopeFields["apiEndpoints"]?.isNotBlank() == true,
                hasDataModel = scopeFields["dataModel"]?.isNotBlank() == true,
                hasSideEffects = scopeFields["sideEffects"]?.isNotBlank() == true,
                hasPublicApi = scopeFields["publicApi"]?.isNotBlank() == true,
                hasTypesExposed = scopeFields["typesExposed"]?.isNotBlank() == true,
                hasExamples = scopeFields["examples"]?.isNotBlank() == true,
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
     * Feature 22: Reads the wizard FEATURES step (if present) and normalizes it into a list of
     * [WizardFeatureInput] so scope + scopeFields can be surfaced in per-feature docs. Returns
     * an empty list when no wizard service is wired, no category is known, or the FEATURES step
     * hasn't been saved yet — the caller must treat this as "no scope info available" and emit
     * a feature doc without the Scope header or scope-specific sections.
     */
    private fun loadWizardFeatures(projectId: String): List<WizardFeatureInput> {
        val service = wizardService ?: return emptyList()
        val wizardData = runCatching { service.getWizardData(projectId) }.getOrNull() ?: return emptyList()
        val featuresStep = wizardData.steps[FlowStepType.FEATURES.name] ?: return emptyList()
        val rawFields: Map<String, Any> = featuresStep.fields.mapValues { (_, v) -> jsonElementToAny(v) }
        val category = extractCategoryFromWizard(wizardData)
        // Two shapes coexist in storage:
        //  (a) graph-form persisted by the wizard UI as a single "graph" field with {features, edges}
        //  (b) legacy flat form where keys map directly to a list of features
        // parseWizardFeatures accepts either the inner map or the raw fields map; we prefer the
        // explicit "graph" field when present.
        val graphField = rawFields["graph"]
        val parseInput: Any? = graphField ?: rawFields
        return IdeaToSpecAgent.parseWizardFeatures(parseInput, category)
    }

    private fun extractCategoryFromWizard(wizardData: WizardData): String? {
        val ideaFields = wizardData.steps[FlowStepType.IDEA.name]?.fields ?: return null
        val raw = ideaFields["category"] ?: return null
        val value = (raw as? JsonPrimitive)?.contentOrNull ?: raw.toString()
        return value.trim().takeIf { it.isNotBlank() && it != "null" }
    }

    /**
     * Converts a kotlinx.serialization [JsonElement] into a plain Kotlin value usable by
     * [IdeaToSpecAgent.parseWizardFeatures], which expects `Map<String, Any>` / `List<Any>` /
     * [String] / [Number] / [Boolean] / `null` — not raw [JsonElement]s (whose `toString()`
     * includes JSON quoting).
     */
    private fun jsonElementToAny(element: JsonElement): Any = when (element) {
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            element.isString -> element.content
            else -> element.contentOrNull ?: element.toString()
        }
        is JsonArray -> element.map { jsonElementToAny(it) }
        is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
    }

    /**
     * Feature 22: Maps the category-aware [FeatureScope] set persisted on the wizard to a
     * short, human-readable label for the feature doc header. Returns `null` when no wizard
     * match exists so the `**Scope:**` line is suppressed entirely for legacy projects.
     */
    private fun scopeLabel(scopes: Set<FeatureScope>?): String? = when {
        scopes == null -> null
        scopes.containsAll(setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)) -> "Frontend + Backend"
        scopes == setOf(FeatureScope.FRONTEND) -> "Frontend"
        scopes == setOf(FeatureScope.BACKEND) -> "Backend"
        scopes.isEmpty() -> "Core"
        else -> null
    }
}
