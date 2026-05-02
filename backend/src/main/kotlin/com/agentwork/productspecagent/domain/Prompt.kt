package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class PromptListItem(
    val id: String,
    val title: String,
    val description: String,
    val agent: String,
    val isOverridden: Boolean,
)

@Serializable
data class PromptDetail(
    val id: String,
    val title: String,
    val description: String,
    val agent: String,
    val content: String,
    val isOverridden: Boolean,
)

@Serializable
data class UpdatePromptRequest(val content: String)

@Serializable
data class PromptValidationError(val errors: List<String>)
