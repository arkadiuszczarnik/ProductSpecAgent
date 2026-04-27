package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.HandoffExportRequest
import com.agentwork.productspecagent.domain.HandoffPreview
import com.agentwork.productspecagent.export.HandoffService
import com.agentwork.productspecagent.service.ProjectService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/v1/projects/{projectId}/handoff")
class HandoffController(
    private val handoffService: HandoffService,
    private val projectService: ProjectService
) {

    @PostMapping("/preview")
    fun preview(
        @PathVariable projectId: String,
        @RequestParam(defaultValue = "claude-code") format: String
    ): ResponseEntity<HandoffPreview> {
        val syncUrl = buildSyncUrl(projectId)
        val preview = handoffService.generatePreview(projectId, format, syncUrl)
        return ResponseEntity.ok(preview)
    }

    @PostMapping("/export")
    fun export(
        @PathVariable projectId: String,
        @RequestBody(required = false) request: HandoffExportRequest?
    ): ResponseEntity<ByteArray> {
        val project = projectService.getProject(projectId).project
        val slug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val syncUrl = buildSyncUrl(projectId)
        val zipBytes = handoffService.exportHandoff(projectId, request ?: HandoffExportRequest(), syncUrl)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$slug-handoff.zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zipBytes)
    }

    @GetMapping("/handoff.zip")
    fun downloadHandoffZip(
        @PathVariable projectId: String,
        request: HttpServletRequest
    ): ResponseEntity<ByteArray> {
        val project = projectService.getProject(projectId).project
        val slug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val syncUrl = ServletUriComponentsBuilder.fromRequest(request).build().toUriString()
        val zipBytes = handoffService.exportHandoff(projectId, HandoffExportRequest(), syncUrl, flat = true)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$slug-handoff.zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zipBytes)
    }

    private fun buildSyncUrl(projectId: String): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/projects/{projectId}/handoff/handoff.zip")
            .buildAndExpand(projectId)
            .toUriString()
}
