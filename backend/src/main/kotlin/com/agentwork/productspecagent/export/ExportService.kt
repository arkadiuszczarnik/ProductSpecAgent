package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.ExportRequest
import com.agentwork.productspecagent.service.ProjectService
import org.springframework.stereotype.Service

@Service
class ExportService(
    private val projectService: ProjectService,
    private val projectPackageAssembler: ProjectPackageAssembler,
) {
    fun exportProject(
        projectId: String,
        request: ExportRequest = ExportRequest(),
        includeAgentTemplateFiles: Boolean = false,
    ): ByteArray {
        val slug = projectService.getProject(projectId).project.name.slug()
        val writer = ZipArchiveWriter(slug)
        projectPackageAssembler.writeProjectPackage(
            projectId = projectId,
            options = request.toProjectExportOptions(),
            writer = writer,
            includeAgentTemplateFiles = includeAgentTemplateFiles,
        )
        return writer.finish()
    }
}
