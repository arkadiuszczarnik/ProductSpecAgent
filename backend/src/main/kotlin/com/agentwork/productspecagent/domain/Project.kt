package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

enum class ProjectStatus {
    DRAFT, IN_PROGRESS, COMPLETED
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val ownerId: String,
    val status: ProjectStatus,
    val createdAt: String,
    val updatedAt: String,
    val collectionId: String? = null
)
