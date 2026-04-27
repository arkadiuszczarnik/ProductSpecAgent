package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

enum class DocumentState {
    UPLOADED, PROCESSING, EXTRACTED, FAILED, LOCAL
}

@Serializable
data class Document(
    val id: String,
    val title: String,
    val mimeType: String,
    val state: DocumentState,
    val createdAt: String
)
