package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.ExportRequest
import com.agentwork.productspecagent.export.ExportService
import com.agentwork.productspecagent.service.ProjectService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
class ExportController(
    private val exportService: ExportService,
    private val projectService: ProjectService
) {

    @PostMapping("/export")
    fun exportProject(
        @PathVariable projectId: String,
        @RequestBody(required = false) request: ExportRequest?
    ): ResponseEntity<ByteArray> {
        val project = projectService.getProject(projectId).project
        val slug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val zipBytes = exportService.exportProject(
            projectId = projectId,
            request = request ?: ExportRequest(),
            includeAgentTemplateFiles = true,
        )

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$slug.zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zipBytes)
    }
}
