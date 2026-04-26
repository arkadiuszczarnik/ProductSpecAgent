package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val children: List<FileEntry>? = null
)

@Serializable
data class FileContent(
    val path: String,
    val name: String,
    val content: String,
    val language: String,
    val lineCount: Int,
    val binary: Boolean = false
)
