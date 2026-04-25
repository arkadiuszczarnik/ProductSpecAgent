package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.service.DocumentService
import com.agentwork.productspecagent.service.UnsupportedMediaTypeException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/projects/{projectId}/documents")
class DocumentController(private val service: DocumentService) {

    private val allowedMimeTypes = setOf("application/pdf", "text/markdown", "text/plain")

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @PathVariable projectId: String,
        @RequestParam("file") file: MultipartFile
    ): Document {
        val mime = file.contentType ?: throw UnsupportedMediaTypeException("(none)")
        if (mime !in allowedMimeTypes) throw UnsupportedMediaTypeException(mime)
        val title = file.originalFilename ?: "untitled"
        return service.upload(projectId, title, mime, file.bytes)
    }

    @GetMapping
    fun list(@PathVariable projectId: String): List<Document> = service.list(projectId)

    @GetMapping("/{documentId}")
    fun get(@PathVariable projectId: String, @PathVariable documentId: String): Document =
        service.get(projectId, documentId)

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable projectId: String, @PathVariable documentId: String) {
        service.delete(projectId, documentId)
    }
}
