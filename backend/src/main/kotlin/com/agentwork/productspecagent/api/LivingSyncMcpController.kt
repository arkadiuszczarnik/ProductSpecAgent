package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.CheckSeverity
import com.agentwork.productspecagent.domain.LivingSyncCodeChangesRequest
import com.agentwork.productspecagent.domain.LivingSyncFeatureDoneImportRequest
import com.agentwork.productspecagent.domain.LivingSyncFeatureProgressRequest
import com.agentwork.productspecagent.domain.LivingSyncFeatureStatus
import com.agentwork.productspecagent.domain.LivingSyncNoteRequest
import com.agentwork.productspecagent.domain.LivingSyncTestRunRequest
import com.agentwork.productspecagent.domain.LivingSyncTokenUsageRequest
import com.agentwork.productspecagent.service.LivingSyncService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class LivingSyncMcpController(
    private val livingSyncService: LivingSyncService,
) {

    @PostMapping("/mcp")
    fun handle(@RequestBody request: Map<String, Any?>): Map<String, Any?> {
        val id = request["id"]
        return when (request["method"]) {
            "initialize" -> response(id, initializeResult())
            "tools/list" -> response(id, mapOf("tools" to tools()))
            "tools/call" -> response(id, callTool(argumentsMap(request["params"])))
            else -> errorResponse(id, -32601, "Method not found")
        }
    }

    private fun callTool(params: Map<String, Any?>): Map<String, Any?> {
        val name = params["name"] as? String ?: return toolText("Missing tool name.")
        val arguments = argumentsMap(params["arguments"])
        val projectId = arguments.string("projectId") ?: return toolText("Missing projectId.")

        val result = when (name) {
            "get_project_sync_context" -> livingSyncService.getSummary(projectId).toString()
            "report_feature_progress" -> livingSyncService.reportFeatureProgress(
                projectId,
                LivingSyncFeatureProgressRequest(
                    featureId = arguments.string("featureId") ?: return toolText("Missing featureId."),
                    status = LivingSyncFeatureStatus.valueOf(arguments.string("status") ?: "IN_PROGRESS"),
                    summary = arguments.string("summary") ?: return toolText("Missing summary."),
                    evidence = arguments.stringList("evidence"),
                    taskId = arguments.string("taskId"),
                    agentName = arguments.string("agentName"),
                ),
            ).summary
            "import_feature_done_markdown" -> livingSyncService.importFeatureDoneMarkdown(
                projectId,
                LivingSyncFeatureDoneImportRequest(
                    featureId = arguments.string("featureId") ?: return toolText("Missing featureId."),
                    fileName = arguments.string("fileName") ?: return toolText("Missing fileName."),
                    markdown = arguments.string("markdown") ?: return toolText("Missing markdown."),
                    agentName = arguments.string("agentName"),
                ),
            ).summary
            "report_test_run" -> livingSyncService.reportTestRun(
                projectId,
                LivingSyncTestRunRequest(
                    command = arguments.string("command") ?: return toolText("Missing command."),
                    status = arguments.string("status") ?: return toolText("Missing status."),
                    summary = arguments.string("summary") ?: return toolText("Missing summary."),
                    passed = arguments.int("passed"),
                    failed = arguments.int("failed"),
                    featureId = arguments.string("featureId"),
                    taskId = arguments.string("taskId"),
                    agentName = arguments.string("agentName"),
                ),
            ).summary
            "report_token_usage" -> livingSyncService.reportTokenUsage(
                projectId,
                LivingSyncTokenUsageRequest(
                    agentName = arguments.string("agentName") ?: return toolText("Missing agentName."),
                    model = arguments.string("model") ?: return toolText("Missing model."),
                    inputTokens = arguments.long("inputTokens"),
                    outputTokens = arguments.long("outputTokens"),
                    totalTokens = arguments.long("totalTokens").takeIf { it > 0 }
                        ?: (arguments.long("inputTokens") + arguments.long("outputTokens")),
                    summary = arguments.string("summary") ?: "Token usage reported.",
                    taskId = arguments.string("taskId"),
                    featureId = arguments.string("featureId"),
                ),
            ).summary
            "report_code_changes" -> livingSyncService.reportCodeChanges(
                projectId,
                LivingSyncCodeChangesRequest(
                    summary = arguments.string("summary") ?: return toolText("Missing summary."),
                    files = arguments.stringList("files"),
                    commits = arguments.stringList("commits"),
                    featureId = arguments.string("featureId"),
                    taskId = arguments.string("taskId"),
                    agentName = arguments.string("agentName"),
                ),
            ).summary
            "report_sync_note" -> livingSyncService.reportSyncNote(
                projectId,
                LivingSyncNoteRequest(
                    severity = CheckSeverity.valueOf(arguments.string("severity") ?: "INFO"),
                    message = arguments.string("message") ?: return toolText("Missing message."),
                    suggestedAction = arguments.string("suggestedAction"),
                    featureId = arguments.string("featureId"),
                    taskId = arguments.string("taskId"),
                    agentName = arguments.string("agentName"),
                ),
            ).summary
            else -> "Unknown tool: $name"
        }

        return toolText(result)
    }

    private fun initializeResult(): Map<String, Any?> =
        mapOf(
            "protocolVersion" to "2025-11-25",
            "serverInfo" to mapOf("name" to "product-spec-agent", "version" to "0.1.0"),
            "capabilities" to mapOf("tools" to mapOf<String, Any>()),
        )

    private fun tools(): List<Map<String, Any?>> =
        listOf(
            tool("get_project_sync_context", "Read Product-Spec-Agent Living-Sync context for a project."),
            tool("report_feature_progress", "Report feature progress from a Coding Agent."),
            tool("report_test_run", "Report a test run from the target application."),
            tool("report_token_usage", "Report token usage for an agent/model/task."),
            tool("report_code_changes", "Report changed files and optional commits."),
            tool("report_sync_note", "Report blockers, deviations, open questions, or technical debt."),
            tool(
                name = "import_feature_done_markdown",
                description = "Import a feature done markdown report into Living Sync.",
                properties = mapOf(
                    "projectId" to mapOf("type" to "string"),
                    "featureId" to mapOf("type" to "string"),
                    "fileName" to mapOf("type" to "string"),
                    "markdown" to mapOf("type" to "string"),
                    "agentName" to mapOf("type" to "string"),
                ),
                required = listOf("projectId", "featureId", "fileName", "markdown"),
            ),
        )

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, Any?> = mapOf("projectId" to mapOf("type" to "string")),
        required: List<String> = listOf("projectId"),
    ): Map<String, Any?> =
        mapOf(
            "name" to name,
            "description" to description,
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to required,
            ),
        )

    private fun response(id: Any?, result: Map<String, Any?>): Map<String, Any?> =
        mapOf("jsonrpc" to "2.0", "id" to id, "result" to result)

    private fun errorResponse(id: Any?, code: Int, message: String): Map<String, Any?> =
        mapOf("jsonrpc" to "2.0", "id" to id, "error" to mapOf("code" to code, "message" to message))

    private fun toolText(text: String): Map<String, Any?> =
        mapOf("content" to listOf(mapOf("type" to "text", "text" to text)))

    @Suppress("UNCHECKED_CAST")
    private fun argumentsMap(value: Any?): Map<String, Any?> =
        value as? Map<String, Any?> ?: emptyMap()

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.int(key: String): Int =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }

    private fun Map<String, Any?>.long(key: String): Long =
        when (val value = this[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0
            else -> 0
        }

    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
}
