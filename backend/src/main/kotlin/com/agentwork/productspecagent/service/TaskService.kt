package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.TaskStorage
import org.springframework.stereotype.Service
import java.time.Instant

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
