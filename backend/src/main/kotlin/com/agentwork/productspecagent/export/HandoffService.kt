package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.*
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Service
class HandoffService(
    private val projectService: ProjectService,
    private val taskService: TaskService,
    private val exportService: ExportService
) {

    private val mf: MustacheFactory = DefaultMustacheFactory("templates/handoff")

    fun generatePreview(projectId: String, format: String, syncUrl: String): HandoffPreview {
        val projectResponse = projectService.getProject(projectId)
        val project = projectResponse.project
        val tasks = taskService.listTasks(projectId)

        return HandoffPreview(
            claudeMd = generateClaudeMd(project.name, syncUrl),
            agentsMd = generateAgentsMd(project, format),
            implementationOrder = generateImplementationOrder(tasks),
            format = format,
            syncUrl = syncUrl
        )
    }

    fun exportHandoff(projectId: String, request: HandoffExportRequest, syncUrl: String, flat: Boolean = false): ByteArray {
        val projectResponse = projectService.getProject(projectId)
        val project = projectResponse.project
        val slug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        val effectiveSyncUrl = request.syncUrl ?: syncUrl

        val preview = if (request.claudeMd != null || request.agentsMd != null || request.implementationOrder != null) {
            val defaults = generatePreview(projectId, request.format, effectiveSyncUrl)
            HandoffPreview(
                claudeMd = request.claudeMd ?: defaults.claudeMd,
                agentsMd = request.agentsMd ?: defaults.agentsMd,
                implementationOrder = request.implementationOrder ?: defaults.implementationOrder,
                format = request.format,
                syncUrl = effectiveSyncUrl
            )
        } else {
            generatePreview(projectId, request.format, effectiveSyncUrl)
        }

        val baseZip = exportService.exportProject(projectId)
        val entryPrefix = if (flat) "" else "$slug/"

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            ZipInputStream(ByteArrayInputStream(baseZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val targetName = if (flat) entry.name.removePrefix("$slug/") else entry.name
                    if (targetName.isNotEmpty()) {
                        zip.putNextEntry(ZipEntry(targetName))
                        zis.copyTo(zip)
                        zip.closeEntry()
                    }
                    entry = zis.nextEntry
                }
            }

            zip.addEntry("${entryPrefix}CLAUDE.md", preview.claudeMd)
            zip.addEntry("${entryPrefix}AGENTS.md", preview.agentsMd)
            zip.addEntry("${entryPrefix}implementation-order.md", preview.implementationOrder)
        }

        return baos.toByteArray()
    }

    private fun generateClaudeMd(projectName: String, syncUrl: String): String {
        val mustache = mf.compile("claude.md.mustache")
        val writer = StringWriter()
        mustache.execute(writer, mapOf("projectName" to projectName, "syncUrl" to syncUrl)).flush()
        return writer.toString()
    }

    private fun generateAgentsMd(project: Project, format: String): String = buildString {
        appendLine("# AI Coding Agent Instructions")
        appendLine()
        appendLine("Project: ${project.name}")
        appendLine()
        appendLine("## General Guidelines")
        appendLine()
        appendLine("- Read `CLAUDE.md` for project context before starting")
        appendLine("- Follow `implementation-order.md` for task sequencing")
        appendLine("- Implement one task at a time, commit after each")
        appendLine("- Write tests for all new functionality")
        appendLine()

        when (format) {
            "claude-code" -> {
                appendLine("## Claude Code")
                appendLine()
                appendLine("- Use `CLAUDE.md` as the project brief")
                appendLine("- Reference `implementation-order.md` for task priority")
                appendLine("- Commit after completing each task")
            }
            "codex" -> {
                appendLine("## Codex")
                appendLine()
                appendLine("- Use the specification files as context")
                appendLine("- Follow the implementation order strictly")
                appendLine("- Validate each task against the spec before moving on")
            }
            else -> {
                appendLine("## Custom Agent ($format)")
                appendLine()
                appendLine("- Adapt the project files to your agent's workflow")
                appendLine("- Use `SPEC.md` and `PLAN.md` as primary references")
            }
        }
    }

    private fun generateImplementationOrder(tasks: List<SpecTask>): String = buildString {
        appendLine("# Implementation Order")
        appendLine()

        val epics = tasks.filter { it.type == TaskType.EPIC }.sortedBy { it.priority }
        if (epics.isEmpty()) {
            appendLine("No tasks defined yet.")
            return@buildString
        }

        var taskNumber = 1
        for (epic in epics) {
            appendLine("## ${epic.title}")
            appendLine()
            val stories = tasks.filter { it.parentId == epic.id }.sortedBy { it.priority }
            for (story in stories) {
                appendLine("### ${story.title}")
                appendLine()
                val subtasks = tasks.filter { it.parentId == story.id }.sortedBy { it.priority }
                for (task in subtasks) {
                    appendLine("$taskNumber. **${task.title}** (${task.estimate}) — ${task.description}")
                    taskNumber++
                }
                appendLine()
            }
        }
    }

    private fun ZipOutputStream.addEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }
}
