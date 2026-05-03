package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.DesignSummaryAgent
import com.agentwork.productspecagent.config.DesignBundleProperties
import com.agentwork.productspecagent.domain.DesignBundle
import com.agentwork.productspecagent.domain.DesignBundleFile
import com.agentwork.productspecagent.domain.DesignPage
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.service.DesignBundleExtractor
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.storage.DesignBundleStorage
import kotlinx.serialization.Serializable
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/projects/{projectId}/design")
class DesignBundleController(
    private val storage: DesignBundleStorage,
    private val props: DesignBundleProperties,
    private val designSummaryAgent: DesignSummaryAgent,
    private val projectService: ProjectService,
    @Value("\${app.frontend-origin:http://localhost:3001}") private val frontendOrigin: String,
) {

    @Serializable
    data class DesignBundleResponse(
        val projectId: String,
        val originalFilename: String,
        val uploadedAt: String,
        val sizeBytes: Long,
        val entryHtml: String,
        val pages: List<DesignPage>,
        val files: List<DesignBundleFile>,
        val entryUrl: String,
        val bundleUrl: String,
    )

    private fun toResponse(bundle: DesignBundle) = DesignBundleResponse(
        projectId = bundle.projectId,
        originalFilename = bundle.originalFilename,
        uploadedAt = bundle.uploadedAt,
        sizeBytes = bundle.sizeBytes,
        entryHtml = bundle.entryHtml,
        pages = bundle.pages,
        files = bundle.files,
        entryUrl = "/api/v1/projects/${bundle.projectId}/design/files/${bundle.entryHtml}",
        bundleUrl = "/api/v1/projects/${bundle.projectId}/design/files/",
    )

    @PostMapping("/upload")
    fun upload(
        @PathVariable projectId: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<DesignBundleResponse> {
        val bytes = file.bytes
        if (bytes.size > props.maxZipBytes) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Bundle zu groß: ${bytes.size / 1024} KB. Maximum ist ${props.maxZipBytes / 1024} KB.",
            )
        }
        val bundle = try {
            storage.save(projectId, file.originalFilename ?: "bundle.zip", bytes)
        } catch (e: DesignBundleExtractor.InvalidBundleException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
        return ResponseEntity.ok(toResponse(bundle))
    }

    @GetMapping
    fun get(@PathVariable projectId: String): ResponseEntity<DesignBundleResponse> {
        val bundle = storage.get(projectId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toResponse(bundle))
    }

    @DeleteMapping
    fun delete(@PathVariable projectId: String): ResponseEntity<Void> {
        storage.delete(projectId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/files/**")
    fun serveFile(
        @PathVariable projectId: String,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val filesPrefix = "/api/v1/projects/$projectId/design/files/"
        val raw = request.requestURI.substringAfter(filesPrefix, "")
        val relPath = URLDecoder.decode(raw, StandardCharsets.UTF_8)
        if (relPath.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "empty path")
        }
        if (relPath.contains("../") || relPath.contains("..\\") ||
            relPath.startsWith("/") || relPath.startsWith("\\")
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "path traversal rejected: $relPath")
        }
        val bytes = try {
            storage.readFile(projectId, relPath)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        }

        val contentType = mimeTypeFor(relPath)
        val headers = HttpHeaders()
        headers.set(HttpHeaders.CONTENT_TYPE, contentType)
        headers.set("X-Content-Type-Options", "nosniff")
        headers.set(
            "Content-Security-Policy",
            "frame-ancestors 'self' $frontendOrigin",
        )
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store")
        return ResponseEntity(bytes, headers, HttpStatus.OK)
    }

    data class CompleteRequest(val locale: String = "de")

    data class CompleteResponse(
        val message: String,
        val nextStep: String?,
    )

    @PostMapping("/complete")
    fun complete(
        @PathVariable projectId: String,
        @RequestBody body: CompleteRequest,
    ): ResponseEntity<CompleteResponse> {
        val bundle = storage.get(projectId)
        val message: String
        if (bundle != null) {
            try {
                designSummaryAgent.summarize(projectId)
                message = "Design-Bundle '${bundle.originalFilename}' analysiert. Spec aktualisiert."
            } catch (e: Exception) {
                log.warn("design summarize unexpectedly threw for $projectId", e)
                return ResponseEntity.ok(
                    CompleteResponse(
                        message = "Design-Summary konnte nicht generiert werden, Page-Liste wurde übernommen.",
                        nextStep = projectService.advanceStep(projectId, FlowStepType.DESIGN)?.name,
                    )
                )
            }
        } else {
            message = "Design-Step übersprungen — kein Bundle hochgeladen."
        }
        val nextStep = projectService.advanceStep(projectId, FlowStepType.DESIGN)
        return ResponseEntity.ok(CompleteResponse(message, nextStep?.name))
    }

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    private fun mimeTypeFor(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".html") -> "text/html; charset=utf-8"
            lower.endsWith(".css")  -> "text/css; charset=utf-8"
            lower.endsWith(".js") || lower.endsWith(".jsx") -> "text/javascript; charset=utf-8"
            lower.endsWith(".json") -> "application/json; charset=utf-8"
            lower.endsWith(".png")  -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif")  -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".svg")  -> "image/svg+xml"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".ttf")  -> "font/ttf"
            lower.endsWith(".md") || lower.endsWith(".txt") -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}
