package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.AssetBundleUploadResult
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.assetBundleId
import com.agentwork.productspecagent.service.AssetBundleAdminService
import com.agentwork.productspecagent.service.AssetBundleNotFoundException
import com.agentwork.productspecagent.service.BundleFileNotFoundException
import com.agentwork.productspecagent.service.IllegalBundleEntryException
import com.agentwork.productspecagent.storage.AssetBundleStorage
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class AssetBundleListItem(
    val id: String,
    val step: FlowStepType,
    val field: String,
    val value: String,
    val version: String,
    val title: String,
    val description: String,
    val fileCount: Int,
)

data class AssetBundleDetail(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

@RestController
@RequestMapping("/api/v1/asset-bundles")
class AssetBundleController(
    private val storage: AssetBundleStorage,
    private val adminService: AssetBundleAdminService,
) {

    @GetMapping
    fun list(): List<AssetBundleListItem> =
        storage.listAll().map { manifest ->
            val bundle = storage.find(manifest.step, manifest.field, manifest.value)
            AssetBundleListItem(
                id = manifest.id,
                step = manifest.step,
                field = manifest.field,
                value = manifest.value,
                version = manifest.version,
                title = manifest.title,
                description = manifest.description,
                fileCount = bundle?.files?.size ?: 0,
            )
        }

    @GetMapping("/{step}/{field}/{value}")
    fun detail(
        @PathVariable step: FlowStepType,
        @PathVariable field: String,
        @PathVariable value: String,
    ): AssetBundleDetail {
        val bundle = storage.find(step, field, value)
            ?: throw AssetBundleNotFoundException(assetBundleId(step, field, value))
        return AssetBundleDetail(manifest = bundle.manifest, files = bundle.files)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(@RequestParam("file") file: MultipartFile): AssetBundleUploadResult =
        adminService.upload(file.bytes)

    @DeleteMapping("/{step}/{field}/{value}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable step: FlowStepType,
        @PathVariable field: String,
        @PathVariable value: String,
    ) {
        storage.find(step, field, value) ?: throw AssetBundleNotFoundException(assetBundleId(step, field, value))
        storage.delete(step, field, value)
    }

    @GetMapping("/{step}/{field}/{value}/files/**")
    fun getFile(
        @PathVariable step: FlowStepType,
        @PathVariable field: String,
        @PathVariable value: String,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        // Anchor to the bundle-specific prefix so `/files/` substrings inside `value`
        // cannot mis-split the path. Path-variable `value` is URL-decoded by Spring;
        // the URI we read is the encoded form, so we re-encode value for matching.
        val uri = request.requestURI
        val encodedValue = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
        val bundleBase = "/api/v1/asset-bundles/${step.name}/$field/$encodedValue/files/"
        val rawSuffix = uri.substringAfter(bundleBase, "").ifEmpty {
            // Fallback for clients that don't URL-encode `+` etc.
            uri.substringAfter("/api/v1/asset-bundles/${step.name}/$field/$value/files/", "")
        }
        val relativePath = URLDecoder.decode(rawSuffix, StandardCharsets.UTF_8)
        if (relativePath.contains("../") || relativePath.contains("..\\") ||
            relativePath.startsWith("/") || relativePath.startsWith("\\") ||
            relativePath.isEmpty()
        ) {
            throw IllegalBundleEntryException(relativePath, "path traversal blocked")
        }

        storage.find(step, field, value) ?: throw AssetBundleNotFoundException(assetBundleId(step, field, value))

        val bytes = storage.loadFileBytes(step, field, value, relativePath)
            ?: throw BundleFileNotFoundException(assetBundleId(step, field, value), relativePath)

        val contentType = contentTypeForExt(relativePath)
        val headers = HttpHeaders().apply {
            this.contentType = MediaType.parseMediaType(contentType)
            set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${relativePath.substringAfterLast('/')}\"")
        }
        return ResponseEntity(bytes, headers, HttpStatus.OK)
    }

    private fun contentTypeForExt(relativePath: String): String =
        when (relativePath.substringAfterLast('.', "").lowercase()) {
            "md", "markdown" -> "text/markdown;charset=UTF-8"
            "txt" -> "text/plain;charset=UTF-8"
            "json" -> "application/json"
            "yaml", "yml" -> "application/yaml;charset=UTF-8"
            "py" -> "text/x-python;charset=UTF-8"
            "ts", "tsx" -> "application/typescript;charset=UTF-8"
            "js", "mjs" -> "application/javascript;charset=UTF-8"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
}
