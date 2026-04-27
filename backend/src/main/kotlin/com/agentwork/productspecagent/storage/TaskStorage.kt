package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.SpecTask
import com.agentwork.productspecagent.service.TaskNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

@Service
class TaskStorage(
    @Value("\${app.data-path}") private val dataPath: String
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun tasksDir(projectId: String): Path =
        Path.of(dataPath, "projects", projectId, "docs", "tasks")

    private fun taskFile(projectId: String, taskId: String): Path =
        tasksDir(projectId).resolve("$taskId.json")

    fun saveTask(task: SpecTask) {
        val dir = tasksDir(task.projectId)
        Files.createDirectories(dir)
        taskFile(task.projectId, task.id).writeText(json.encodeToString(task))
    }

    fun loadTask(projectId: String, taskId: String): SpecTask {
        val file = taskFile(projectId, taskId)
        if (!file.exists()) throw TaskNotFoundException(taskId)
        return json.decodeFromString(file.readText())
    }

    fun listTasks(projectId: String): List<SpecTask> {
        val dir = tasksDir(projectId)
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries("*.json")
            .map { json.decodeFromString<SpecTask>(it.readText()) }
            .sortedBy { it.priority }
    }

    fun deleteTask(projectId: String, taskId: String) {
        taskFile(projectId, taskId).deleteIfExists()
    }

    fun deleteAllTasks(projectId: String) {
        val dir = tasksDir(projectId)
        if (dir.exists()) dir.toFile().deleteRecursively()
    }
}
