package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.HandoffPreview
import com.agentwork.productspecagent.domain.Project
import com.agentwork.productspecagent.domain.SpecTask
import com.agentwork.productspecagent.domain.TaskType
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.TaskService
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import org.springframework.stereotype.Service
import java.io.StringWriter

data class HandoffSyncEndpoints(
    val livingSyncBaseUrl: String,
    val mcpUrl: String,
)

@Service
class HandoffContentFactory(
    private val projectService: ProjectService,
    private val taskService: TaskService,
) {
    private val mf: MustacheFactory = DefaultMustacheFactory("templates/handoff")

    fun generatePreview(projectId: String, format: HandoffFormat, syncUrl: String): HandoffPreview {
        val project = projectService.getProject(projectId).project
        val tasks = taskService.listTasks(projectId)
        val endpoints = endpoints(syncUrl)

        return HandoffPreview(
            claudeMd = generateClaudeMd(project.name, syncUrl, endpoints),
            agentsMd = generateAgentsMd(project, format, endpoints),
            implementationOrder = generateImplementationOrder(tasks),
            format = format.value,
            syncUrl = syncUrl,
        )
    }

    fun mergeOverrides(preview: HandoffPreview, options: HandoffPackageOptions): HandoffPreview =
        preview.copy(
            claudeMd = options.overrides.claudeMd ?: preview.claudeMd,
            agentsMd = options.overrides.agentsMd ?: preview.agentsMd,
            implementationOrder = options.overrides.implementationOrder ?: preview.implementationOrder,
            format = options.format.value,
        )

    fun livingSyncSettings(): String =
        render("living-sync-settings.json.mustache", emptyMap<String, Any>())

    fun livingSyncConfig(syncUrl: String): String {
        val endpoints = endpoints(syncUrl)
        return render(
            "living-sync-config.json.mustache",
            mapOf(
                "livingSyncBaseUrl" to endpoints.livingSyncBaseUrl,
                "mcpUrl" to endpoints.mcpUrl,
            ),
        )
    }

    private fun generateClaudeMd(projectName: String, syncUrl: String, endpoints: HandoffSyncEndpoints): String =
        render("handoff.md.mustache", handoffContext(projectName, HandoffFormat.ClaudeCode, endpoints, syncUrl))

    private fun generateAgentsMd(project: Project, format: HandoffFormat, endpoints: HandoffSyncEndpoints): String =
        render("agent-template.md.mustache", handoffContext(project.name, format, endpoints))

    private fun endpoints(syncUrl: String): HandoffSyncEndpoints =
        HandoffSyncEndpoints(
            livingSyncBaseUrl = syncUrl.removeSuffix("/handoff/handoff.zip") + "/living-sync/mcp",
            mcpUrl = syncUrl.substringBefore("/api/v1/projects/", syncUrl).removeSuffix("/") + "/mcp",
        )

    private fun generateImplementationOrder(tasks: List<SpecTask>): String {
        val epics = tasks.filter { it.type == TaskType.EPIC }.sortedBy { it.priority }
        var taskNumber = 1
        val context = mapOf(
            "hasTasks" to epics.isNotEmpty(),
            "epics" to epics.map { epic ->
                mapOf(
                    "title" to epic.title,
                    "stories" to tasks.filter { it.parentId == epic.id }.sortedBy { it.priority }.map { story ->
                        mapOf(
                            "title" to story.title,
                            "tasks" to tasks.filter { it.parentId == story.id }.sortedBy { it.priority }.map { task ->
                                mapOf(
                                    "number" to taskNumber++,
                                    "title" to task.title,
                                    "estimate" to task.estimate,
                                    "description" to task.description,
                                )
                            },
                        )
                    },
                )
            },
        )
        return render("implementation-order.md.mustache", context)
    }

    private fun handoffContext(
        projectName: String,
        format: HandoffFormat,
        endpoints: HandoffSyncEndpoints,
        syncUrl: String? = null,
    ): Map<String, Any?> = mapOf(
        "projectName" to projectName,
        "format" to format.value,
        "syncUrl" to syncUrl,
        "livingSyncBaseUrl" to endpoints.livingSyncBaseUrl,
        "mcpUrl" to endpoints.mcpUrl,
        "claudeCode" to (format == HandoffFormat.ClaudeCode),
        "codex" to (format == HandoffFormat.Codex),
        "custom" to (format != HandoffFormat.ClaudeCode && format != HandoffFormat.Codex),
    )

    private fun render(templatePath: String, scope: Any): String {
        val mustache = mf.compile(templatePath)
        val writer = StringWriter()
        mustache.execute(writer, scope).flush()
        return writer.toString()
    }
}
