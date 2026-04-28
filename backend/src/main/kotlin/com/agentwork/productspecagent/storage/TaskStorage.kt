package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.SpecTask
import com.agentwork.productspecagent.service.TaskNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class TaskStorage(private val objectStore: ObjectStore) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun tasksPrefix(projectId: String) = "projects/$projectId/docs/tasks/"
    private fun taskKey(projectId: String, taskId: String) =
        "${tasksPrefix(projectId)}$taskId.json"

    fun saveTask(task: SpecTask) {
        objectStore.put(
            taskKey(task.projectId, task.id),
            json.encodeToString(task).toByteArray(),
            "application/json"
        )
    }

    fun loadTask(projectId: String, taskId: String): SpecTask {
        val bytes = objectStore.get(taskKey(projectId, taskId))
            ?: throw TaskNotFoundException(taskId)
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    fun listTasks(projectId: String): List<SpecTask> =
        objectStore.listKeys(tasksPrefix(projectId))
            .filter { it.endsWith(".json") }
            .mapNotNull { key ->
                objectStore.get(key)
                    ?.toString(Charsets.UTF_8)
                    ?.let { json.decodeFromString<SpecTask>(it) }
            }
            .sortedBy { it.priority }

    fun deleteTask(projectId: String, taskId: String) {
        objectStore.delete(taskKey(projectId, taskId))
    }

    fun deleteAllTasks(projectId: String) {
        objectStore.deletePrefix(tasksPrefix(projectId))
    }
}
