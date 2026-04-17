package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.TaskStorage
import org.springframework.stereotype.Service
import java.time.Instant

data class WizardFeatureInput(
    val id: String,
    val title: String,
    val description: String,
    val scopes: Set<com.agentwork.productspecagent.domain.FeatureScope>,
    val scopeFields: Map<String, String>,
    val dependsOn: List<String> = emptyList(),
)

@Service
class TaskService(
    private val storage: TaskStorage,
    private val agent: PlanGeneratorAgent
) {
    suspend fun generatePlan(projectId: String): List<SpecTask> {
        storage.deleteAllTasks(projectId)
        val tasks = agent.generatePlan(projectId)
        tasks.forEach { storage.saveTask(it) }
        return tasks
    }

    /**
     * Idempotently regenerate the wizard-derived task tree from the FEATURES wizard step.
     * Deletes only existing tasks marked with [TaskSource.WIZARD] (and any of their descendants),
     * then creates a fresh EPIC + Stories + Tasks tree per feature using [PlanGeneratorAgent].
     * Tasks created via [generatePlan] (PLAN_GENERATOR) or manually (source == null) are preserved.
     */
    suspend fun replaceWizardFeatureTasks(
        projectId: String,
        features: List<WizardFeatureInput>
    ): List<SpecTask> {
        // 1. Find all wizard-sourced tasks plus any descendants of wizard-sourced parents.
        val all = storage.listTasks(projectId)
        val wizardIds = all.filter { it.source == TaskSource.WIZARD }.map { it.id }.toMutableSet()
        // Descendants by parentId (handles the case where a story/task points to a wizard epic
        // even if the descendant itself somehow has a different/null source).
        var changed = true
        while (changed) {
            changed = false
            for (t in all) {
                if (t.parentId != null && t.parentId in wizardIds && t.id !in wizardIds) {
                    wizardIds.add(t.id)
                    changed = true
                }
            }
        }
        wizardIds.forEach { storage.deleteTask(projectId, it) }

        // 2. For each wizard feature, ask the LLM to derive Stories + Tasks under a new EPIC.
        val highestExistingPriority = all
            .filter { it.id !in wizardIds }
            .maxOfOrNull { it.priority } ?: -1
        var nextPriority = highestExistingPriority + 1

        val created = mutableListOf<SpecTask>()
        for (feature in features) {
            val tasks = agent.generatePlanForFeature(
                projectId = projectId,
                featureTitle = feature.title,
                featureDescription = feature.description,
                featureEstimate = "M",  // FEATURE-22-TODO Task 4: replace with epic.estimate returned from PlanGeneratorAgent (refactored in Task 3)
                startPriority = nextPriority
            )
            tasks.forEach { storage.saveTask(it) }
            nextPriority += tasks.size
            created.addAll(tasks)
        }
        return created
    }

    fun listTasks(projectId: String): List<SpecTask> = storage.listTasks(projectId)

    fun getTask(projectId: String, taskId: String): SpecTask = storage.loadTask(projectId, taskId)

    fun updateTask(projectId: String, taskId: String, request: UpdateTaskRequest): SpecTask {
        val task = storage.loadTask(projectId, taskId)
        val now = Instant.now().toString()
        val updated = task.copy(
            title = request.title ?: task.title,
            description = request.description ?: task.description,
            estimate = request.estimate ?: task.estimate,
            priority = request.priority ?: task.priority,
            status = request.status ?: task.status,
            parentId = if (request.parentId !== null) request.parentId else task.parentId,
            dependencies = request.dependencies ?: task.dependencies,
            updatedAt = now
        )
        storage.saveTask(updated)
        return updated
    }

    fun deleteTask(projectId: String, taskId: String) {
        storage.loadTask(projectId, taskId) // verify exists
        storage.deleteTask(projectId, taskId)
    }

    fun getCoverage(projectId: String): Map<String, Boolean> {
        val tasks = storage.listTasks(projectId)
        val coveredSections = tasks.mapNotNull { it.specSection }.toSet()
        return FlowStepType.entries.associate { it.name to (it in coveredSections) }
    }
}
