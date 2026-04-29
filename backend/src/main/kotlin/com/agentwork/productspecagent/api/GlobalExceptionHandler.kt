package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.ErrorResponse
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshException
import com.agentwork.productspecagent.service.AssetBundleNotFoundException
import com.agentwork.productspecagent.service.ClarificationNotFoundException
import com.agentwork.productspecagent.service.DecisionNotFoundException
import com.agentwork.productspecagent.service.DocumentNotFoundException
import com.agentwork.productspecagent.service.GraphMeshDisabledException
import com.agentwork.productspecagent.service.ProjectNotFoundException
import com.agentwork.productspecagent.service.TaskNotFoundException
import com.agentwork.productspecagent.service.UnsupportedMediaTypeException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleProjectNotFound(ex: ProjectNotFoundException): ErrorResponse {
        return ErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "Project not found",
            timestamp = Instant.now().toString()
        )
    }

    @ExceptionHandler(DecisionNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleDecisionNotFound(ex: DecisionNotFoundException): ErrorResponse {
        return ErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "Decision not found",
            timestamp = Instant.now().toString()
        )
    }

    @ExceptionHandler(ClarificationNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleClarificationNotFound(ex: ClarificationNotFoundException): ErrorResponse {
        return ErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "Clarification not found",
            timestamp = Instant.now().toString()
        )
    }

    @ExceptionHandler(DocumentNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleDocumentNotFound(ex: DocumentNotFoundException): ErrorResponse {
        return ErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "Document not found",
            timestamp = Instant.now().toString()
        )
    }

    @ExceptionHandler(TaskNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleTaskNotFound(ex: TaskNotFoundException): ErrorResponse {
        return ErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "Task not found",
            timestamp = Instant.now().toString()
        )
    }

    @ExceptionHandler(AssetBundleNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleAssetBundleNotFound(ex: AssetBundleNotFoundException): ErrorResponse {
        return ErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "Asset bundle not found",
            timestamp = Instant.now().toString()
        )
    }

    @ExceptionHandler(GraphMeshException.Unavailable::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleGraphMeshUnavailable(ex: GraphMeshException.Unavailable): ErrorResponse =
        ErrorResponse("GRAPHMESH_UNAVAILABLE", ex.message ?: "GraphMesh unreachable", Instant.now().toString())

    @ExceptionHandler(GraphMeshException.GraphQlError::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun handleGraphMeshGraphQlError(ex: GraphMeshException.GraphQlError): ErrorResponse =
        ErrorResponse("GRAPHMESH_ERROR", ex.detail, Instant.now().toString())

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    fun handleMaxUpload(ex: MaxUploadSizeExceededException): ErrorResponse =
        ErrorResponse("FILE_TOO_LARGE", "File exceeds maximum size of 10 MB", Instant.now().toString())

    @ExceptionHandler(UnsupportedMediaTypeException::class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    fun handleUnsupportedMime(ex: UnsupportedMediaTypeException): ErrorResponse =
        ErrorResponse("UNSUPPORTED_TYPE", ex.message ?: "Unsupported MIME type", Instant.now().toString())

    @ExceptionHandler(GraphMeshDisabledException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleGraphMeshDisabled(ex: GraphMeshDisabledException): ErrorResponse =
        ErrorResponse("GRAPHMESH_DISABLED_BACKEND", ex.message ?: "GraphMesh is disabled in backend config", Instant.now().toString())
}
