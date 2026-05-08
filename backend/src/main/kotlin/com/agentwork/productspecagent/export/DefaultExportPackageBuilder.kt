package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.HandoffPreview
import com.agentwork.productspecagent.service.ProjectService
import org.springframework.stereotype.Service

@Service
class DefaultExportPackageBuilder(
    private val projectService: ProjectService,
    private val projectPackageAssembler: ProjectPackageAssembler,
    private val handoffContentFactory: HandoffContentFactory,
    private val handoffOverlayWriter: HandoffOverlayWriter,
) : ExportPackageBuilder {

    override fun exportProject(projectId: String, options: ProjectExportOptions): ZipPackage {
        val slug = projectSlug(projectId)
        val writer = ZipArchiveWriter(slug)
        projectPackageAssembler.writeProjectPackage(
            projectId = projectId,
            options = options,
            writer = writer,
            includeAgentTemplateFiles = true,
        )
        return ZipPackage(
            filename = "$slug.zip",
            bytes = writer.finish(),
        )
    }

    override fun previewHandoff(projectId: String, syncUrl: String, format: HandoffFormat): HandoffPreview =
        handoffContentFactory.generatePreview(projectId, format, syncUrl)

    override fun exportHandoff(projectId: String, syncUrl: String, options: HandoffPackageOptions): ZipPackage {
        val slug = projectSlug(projectId)
        val writer = ZipArchiveWriter()
        projectPackageAssembler.writeProjectPackage(
            projectId = projectId,
            options = ProjectExportOptions(
                includeDecisions = false,
                includeClarifications = false,
            ),
            writer = writer,
            includeAgentTemplateFiles = false,
            includeToolLinks = false,
        )
        val preview = handoffContentFactory.mergeOverrides(
            preview = handoffContentFactory.generatePreview(projectId, options.format, syncUrl),
            options = options,
        )
        handoffOverlayWriter.writeHandoffOverlay(writer, preview)
        return ZipPackage(
            filename = "$slug-handoff.zip",
            bytes = writer.finish(),
        )
    }

    private fun projectSlug(projectId: String): String =
        projectService.getProject(projectId).project.name.slug()
}

fun String.slug(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
