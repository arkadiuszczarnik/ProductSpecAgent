package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.LivingSyncCodeChangesRequest
import com.agentwork.productspecagent.domain.LivingSyncEvent
import com.agentwork.productspecagent.domain.LivingSyncFeatureDoneImportRequest
import com.agentwork.productspecagent.domain.LivingSyncFeatureProgressRequest
import com.agentwork.productspecagent.domain.LivingSyncFeatureStatus
import com.agentwork.productspecagent.domain.LivingSyncNoteRequest
import com.agentwork.productspecagent.domain.LivingSyncSummary
import com.agentwork.productspecagent.domain.LivingSyncTestRunRequest
import com.agentwork.productspecagent.domain.LivingSyncTokenUsageRequest
import com.agentwork.productspecagent.service.LivingSyncService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/projects/{projectId}/living-sync")
class LivingSyncController(
    private val livingSyncService: LivingSyncService,
) {

    @GetMapping
    fun getSummary(@PathVariable projectId: String): LivingSyncSummary =
        livingSyncService.getSummary(projectId)

    @PostMapping("/mcp/report-feature-progress")
    fun reportFeatureProgress(
        @PathVariable projectId: String,
        @RequestBody request: Map<String, Any?>,
    ): LivingSyncEvent = livingSyncService.reportFeatureProgress(projectId, featureProgressRequest(request))

    @PostMapping("/mcp/import-feature-done-markdown")
    fun importFeatureDoneMarkdown(
        @PathVariable projectId: String,
        @RequestBody request: LivingSyncFeatureDoneImportRequest,
    ): LivingSyncEvent = livingSyncService.importFeatureDoneMarkdown(projectId, request)

    @PostMapping("/mcp/report-test-run")
    fun reportTestRun(
        @PathVariable projectId: String,
        @RequestBody request: LivingSyncTestRunRequest,
    ): LivingSyncEvent = livingSyncService.reportTestRun(projectId, request)

    @PostMapping("/mcp/report-token-usage")
    fun reportTokenUsage(
        @PathVariable projectId: String,
        @RequestBody request: LivingSyncTokenUsageRequest,
    ): LivingSyncEvent = livingSyncService.reportTokenUsage(projectId, request)

    @PostMapping("/mcp/report-code-changes")
    fun reportCodeChanges(
        @PathVariable projectId: String,
        @RequestBody request: LivingSyncCodeChangesRequest,
    ): LivingSyncEvent = livingSyncService.reportCodeChanges(projectId, request)

    @PostMapping("/mcp/report-sync-note")
    fun reportSyncNote(
        @PathVariable projectId: String,
        @RequestBody request: LivingSyncNoteRequest,
    ): LivingSyncEvent = livingSyncService.reportSyncNote(projectId, request)

    private fun featureProgressRequest(request: Map<String, Any?>): LivingSyncFeatureProgressRequest =
        LivingSyncFeatureProgressRequest(
            featureId = request.string("featureId") ?: request.string("feature") ?: badRequest("Missing featureId."),
            status = featureStatus(request.string("status") ?: "IN_PROGRESS"),
            summary = request.string("summary") ?: request.string("message") ?: badRequest("Missing summary."),
            evidence = request.stringList("evidence"),
            taskId = request.string("taskId"),
            agentName = request.string("agentName"),
        )

    private fun featureStatus(value: String): LivingSyncFeatureStatus =
        when (val normalized = value.trim().uppercase().replace("-", "_")) {
            "STARTED" -> LivingSyncFeatureStatus.IN_PROGRESS
            else -> runCatching { LivingSyncFeatureStatus.valueOf(normalized) }
                .getOrElse { badRequest("Invalid status: $value.") }
        }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
