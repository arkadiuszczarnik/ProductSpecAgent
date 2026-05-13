package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType { EPIC, STORY, TASK }

@Serializable
enum class TaskStatus { TODO, IN_PROGRESS, DONE }

@Serializable
data class SpecTask(
    val id: String,
    val projectId: String,
    val parentId: String? = null,
    val type: TaskType,
    val title: String,
    val description: String = "",
    val estimate: String = "M",
    val priority: Int = 0,
    val status: TaskStatus = TaskStatus.TODO,
    val specSection: FlowStepType? = null,
    val featureId: String? = null,
    val dependencies: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val estimate: String? = null,
    val priority: Int? = null,
    val status: TaskStatus? = null,
    val parentId: String? = null,
    val dependencies: List<String>? = null
)
