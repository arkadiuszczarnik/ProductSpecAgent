package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.TaskStorage
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(TaskService::class.java)
    suspend fun generatePlan(projectId: String): List<SpecTask> {
        storage.deleteAllTasks(projectId)
        val tasks = agent.generatePlan(projectId)
        tasks.forEach { storage.saveTask(it) }
        return tasks
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

    suspend fun replaceWizardFeatureTasks(
        projectId: String,
        features: List<WizardFeatureInput>,
    ): List<SpecTask> {
        require(features.map { it.id }.toSet().size == features.size) {
            val dupes = features.groupBy { it.id }.filter { it.value.size > 1 }.keys
            "Duplicate feature IDs in input: $dupes"
        }

        // Delete existing wizard-generated tasks (specSection == FEATURES) before regenerating
        val existing = storage.listTasks(projectId)
        for (t in existing) {
            if (t.specSection == FlowStepType.FEATURES) {
                storage.deleteTask(projectId, t.id)
            }
        }

        var nextPriority = existing.filter { it.specSection != FlowStepType.FEATURES }
            .maxOfOrNull { it.priority + 1 } ?: 0

        // Phase 1: generate EPIC + stories + tasks per wizard feature (no dependencies yet)
        val byWizardId = LinkedHashMap<String, SpecTask>()  // feature.id -> epic SpecTask
        val created = mutableListOf<SpecTask>()
        for (feature in features) {
            val tasks = agent.generatePlanForFeature(
                projectId = projectId,
                input = feature,
                startPriority = nextPriority,
            )
            val epic = tasks.firstOrNull { it.type == TaskType.EPIC }
                ?: error("Agent returned no EPIC for feature '${feature.id}' ('${feature.title}')")
            byWizardId[feature.id] = epic
            created.addAll(tasks)
            nextPriority += tasks.size
        }

        // Phase 2: map dependsOn (wizard-feature-ids) to generated EPIC task-ids
        for (feature in features) {
            val depsAsTaskIds = feature.dependsOn.mapNotNull { byWizardId[it]?.id }
            if (depsAsTaskIds.isEmpty()) continue
            val epic = byWizardId[feature.id] ?: run {
                logger.warn("byWizardId missing entry for feature '{}' during Phase 2 — skipping dependency mapping", feature.id)
                continue
            }
            val updatedEpic = epic.copy(dependencies = depsAsTaskIds, updatedAt = Instant.now().toString())
            val idx = created.indexOfFirst { it.id == epic.id }
            if (idx >= 0) created[idx] = updatedEpic
            byWizardId[feature.id] = updatedEpic
        }

        // Persist AFTER mapping (single write per task, avoids double-write)
        for (t in created) storage.saveTask(t)
        return created
    }
}
