package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.HandoffExportRequest
import com.agentwork.productspecagent.domain.HandoffPreview
import com.agentwork.productspecagent.export.ExportPackageBuilder
import com.agentwork.productspecagent.export.HandoffFormat
import com.agentwork.productspecagent.export.ZipPackage
import com.agentwork.productspecagent.export.toHandoffPackageOptions
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/v1/projects/{projectId}/handoff")
class HandoffController(
    private val packageBuilder: ExportPackageBuilder,
) {

    @PostMapping("/preview")
    fun preview(
        @PathVariable projectId: String,
        @RequestParam(defaultValue = "claude-code") format: String
    ): ResponseEntity<HandoffPreview> {
        val syncUrl = buildSyncUrl(projectId)
        val preview = packageBuilder.previewHandoff(projectId, syncUrl, HandoffFormat.fromWire(format))
        return ResponseEntity.ok(preview)
    }

    @PostMapping("/export")
    fun export(
        @PathVariable projectId: String,
        @RequestBody(required = false) request: HandoffExportRequest?
    ): ResponseEntity<ByteArray> {
        val requestBody = request ?: HandoffExportRequest()
        val syncUrl = requestBody.syncUrl ?: buildSyncUrl(projectId)
        val zip = packageBuilder.exportHandoff(projectId, syncUrl, requestBody.toHandoffPackageOptions())

        return zip.toResponseEntity()
    }

    @GetMapping("/handoff.zip")
    fun downloadHandoffZip(
        @PathVariable projectId: String,
        request: HttpServletRequest
    ): ResponseEntity<ByteArray> {
        val syncUrl = ServletUriComponentsBuilder.fromRequest(request).build().toUriString()
        val zip = packageBuilder.exportHandoff(projectId, syncUrl)

        return zip.toResponseEntity()
    }

    private fun buildSyncUrl(projectId: String): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/projects/{projectId}/handoff/handoff.zip")
            .buildAndExpand(projectId)
            .toUriString()
}

private fun ZipPackage.toResponseEntity(): ResponseEntity<ByteArray> =
    ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
        .contentType(MediaType.parseMediaType(mediaType))
        .body(bytes)
