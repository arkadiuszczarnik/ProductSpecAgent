package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class DesignBundle(
    val projectId: String,
    val originalFilename: String,
    val uploadedAt: String,         // ISO-8601 Instant
    val sizeBytes: Long,
    val entryHtml: String,          // relative to bundle root, e.g. "Scheduler.html"
    val pages: List<DesignPage>,
    val files: List<DesignBundleFile>,
)

@Serializable
data class DesignPage(
    val id: String,                 // DCArtboard id
    val label: String,              // DCArtboard label (full text incl. emoji prefixes)
    val sectionId: String,          // DCSection id
    val sectionTitle: String,       // DCSection title
    val width: Int,
    val height: Int,
)

@Serializable
data class DesignBundleFile(
    val path: String,               // relative path from bundle root, e.g. "view-login.jsx"
    val sizeBytes: Long,
    val mimeType: String,
)
