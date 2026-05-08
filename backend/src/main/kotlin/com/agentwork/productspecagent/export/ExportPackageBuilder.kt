package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.ExportRequest
import com.agentwork.productspecagent.domain.HandoffExportRequest
import com.agentwork.productspecagent.domain.HandoffPreview

interface ExportPackageBuilder {
    fun exportProject(projectId: String, options: ProjectExportOptions = ProjectExportOptions()): ZipPackage

    fun previewHandoff(
        projectId: String,
        syncUrl: String,
        format: HandoffFormat = HandoffFormat.ClaudeCode,
    ): HandoffPreview

    fun exportHandoff(
        projectId: String,
        syncUrl: String,
        options: HandoffPackageOptions = HandoffPackageOptions(),
    ): ZipPackage
}

data class ZipPackage(
    val filename: String,
    val bytes: ByteArray,
    val mediaType: String = "application/zip",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZipPackage) return false

        if (filename != other.filename) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mediaType != other.mediaType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + mediaType.hashCode()
        return result
    }
}

data class ProjectExportOptions(
    val includeDecisions: Boolean = true,
    val includeClarifications: Boolean = true,
    val includeTasks: Boolean = true,
)

data class HandoffPackageOptions(
    val format: HandoffFormat = HandoffFormat.ClaudeCode,
    val overrides: HandoffOverrides = HandoffOverrides(),
)

data class HandoffOverrides(
    val claudeMd: String? = null,
    val agentsMd: String? = null,
    val implementationOrder: String? = null,
)

@JvmInline
value class HandoffFormat private constructor(val value: String) {
    companion object {
        val ClaudeCode = HandoffFormat("claude-code")
        val Codex = HandoffFormat("codex")

        fun custom(value: String): HandoffFormat = HandoffFormat(value)

        fun fromWire(value: String): HandoffFormat =
            when (value) {
                "", ClaudeCode.value -> ClaudeCode
                Codex.value -> Codex
                else -> custom(value)
            }
    }
}

fun ProjectExportOptions.toExportRequest(): ExportRequest =
    ExportRequest(
        includeDecisions = includeDecisions,
        includeClarifications = includeClarifications,
        includeTasks = includeTasks,
    )

fun ExportRequest.toProjectExportOptions(): ProjectExportOptions =
    ProjectExportOptions(
        includeDecisions = includeDecisions,
        includeClarifications = includeClarifications,
        includeTasks = includeTasks,
    )

fun HandoffExportRequest.toHandoffPackageOptions(): HandoffPackageOptions =
    HandoffPackageOptions(
        format = HandoffFormat.fromWire(format),
        overrides = HandoffOverrides(
            claudeMd = claudeMd,
            agentsMd = agentsMd,
            implementationOrder = implementationOrder,
        ),
    )
