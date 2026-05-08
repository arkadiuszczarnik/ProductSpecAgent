package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.ExportRequest
import com.agentwork.productspecagent.export.ExportPackageBuilder
import com.agentwork.productspecagent.export.ZipPackage
import com.agentwork.productspecagent.export.toProjectExportOptions
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
class ExportController(
    private val packageBuilder: ExportPackageBuilder,
) {

    @PostMapping("/export")
    fun exportProject(
        @PathVariable projectId: String,
        @RequestBody(required = false) request: ExportRequest?
    ): ResponseEntity<ByteArray> {
        val zip = packageBuilder.exportProject(projectId, (request ?: ExportRequest()).toProjectExportOptions())

        return zip.toResponseEntity()
    }
}

private fun ZipPackage.toResponseEntity(): ResponseEntity<ByteArray> =
    ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
        .contentType(MediaType.parseMediaType(mediaType))
        .body(bytes)
