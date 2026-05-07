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
        val livingSyncBaseUrl = livingSyncBaseUrl(syncUrl)
        val mcpUrl = mcpUrl(syncUrl)

        return HandoffPreview(
            claudeMd = generateClaudeMd(project.name, syncUrl, livingSyncBaseUrl, mcpUrl),
            agentsMd = generateAgentsMd(project, format, livingSyncBaseUrl, mcpUrl),
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
        val livingSyncBaseUrl = livingSyncBaseUrl(effectiveSyncUrl)
        val mcpUrl = mcpUrl(effectiveSyncUrl)

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
        val symlinkEntries = mutableListOf<String>()
        ZipOutputStream(baos).use { zip ->
            ZipInputStream(ByteArrayInputStream(baseZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val targetName = if (flat) entry.name.removePrefix("$slug/") else entry.name
                    if (targetName.isNotEmpty() && !isToolSymlinkPath(targetName)) {
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
            zip.addEntry("${entryPrefix}.claude/settings.json", generateLivingSyncSettings())
            zip.addEntry("${entryPrefix}.claude/living-sync.json", generateLivingSyncConfig(livingSyncBaseUrl, mcpUrl))
            zip.addLivingSyncAssetBundle(entryPrefix)
            symlinkEntries += zip.addToolSymlinks(entryPrefix)
        }

        return ZipSymlinkSupport.patchSymlinks(baos.toByteArray(), symlinkEntries.toSet())
    }

    private fun generateClaudeMd(projectName: String, syncUrl: String, livingSyncBaseUrl: String, mcpUrl: String): String {
        return render("claude.md.mustache", handoffContext(projectName, "claude-code", livingSyncBaseUrl, mcpUrl, syncUrl))
    }

    private fun generateAgentsMd(project: Project, format: String, livingSyncBaseUrl: String, mcpUrl: String): String =
        render("agents.md.mustache", handoffContext(project.name, format, livingSyncBaseUrl, mcpUrl))

    private fun livingSyncBaseUrl(syncUrl: String): String =
        syncUrl.removeSuffix("/handoff/handoff.zip") + "/living-sync/mcp"

    private fun mcpUrl(syncUrl: String): String =
        syncUrl.substringBefore("/api/v1/projects/", syncUrl).removeSuffix("/") + "/mcp"

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
            }
        )
        return render("implementation-order.md.mustache", context)
    }

    private fun generateLivingSyncSettings(): String =
        render("living-sync-settings.json.mustache", emptyMap<String, Any>())

    private fun generateLivingSyncConfig(livingSyncBaseUrl: String, mcpUrl: String): String =
        render(
            "living-sync-config.json.mustache",
            mapOf(
                "livingSyncBaseUrl" to livingSyncBaseUrl,
                "mcpUrl" to mcpUrl,
            ),
        )

    private fun handoffContext(
        projectName: String,
        format: String,
        livingSyncBaseUrl: String,
        mcpUrl: String,
        syncUrl: String? = null,
    ): Map<String, Any?> = mapOf(
        "projectName" to projectName,
        "format" to format,
        "syncUrl" to syncUrl,
        "livingSyncBaseUrl" to livingSyncBaseUrl,
        "mcpUrl" to mcpUrl,
        "claudeCode" to (format == "claude-code"),
        "codex" to (format == "codex"),
        "custom" to (format != "claude-code" && format != "codex"),
    )

    private fun render(templatePath: String, scope: Any): String {
        val mustache = mf.compile(templatePath)
        val writer = StringWriter()
        mustache.execute(writer, scope).flush()
        return writer.toString()
    }

    private fun isToolSymlinkPath(path: String): Boolean {
        val relative = if (path.startsWith(".")) path else path.substringAfter('/', path)
        return relative in setOf(
            ".claude/skills",
            ".claude/commands",
            ".claude/agents",
            ".agents/skills",
            ".agents/commands",
            ".agents/agents",
        )
    }

    private fun ZipOutputStream.addEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.addEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    private fun ZipOutputStream.addLivingSyncAssetBundle(entryPrefix: String) {
        val bundleId = "global.living-sync-reporter"
        val textFiles = mapOf(
            "SKILL.md" to "SKILL.md",
            "bin/living-sync-reporter" to "bin/living-sync-reporter",
            "bin/living-sync-reporter.cmd" to "bin/living-sync-reporter.cmd",
        )
        val binaryFiles = listOf(
            "bin/linux-amd64/living-sync-reporter.gz",
            "bin/linux-arm64/living-sync-reporter.gz",
            "bin/darwin-amd64/living-sync-reporter.gz",
            "bin/darwin-arm64/living-sync-reporter.gz",
            "bin/windows-amd64/living-sync-reporter.exe.gz",
        )

        val resourcePrefix = "asset-bundles/living-sync-reporter-bundle/skills/living-sync-reporter"
        val zipPrefix = "${entryPrefix}.asset-bundles/skills/$bundleId/living-sync-reporter"

        for ((resourceName, entryName) in textFiles) {
            addEntry("$zipPrefix/$entryName", resourceText("$resourcePrefix/$resourceName"))
        }
        for (resourceName in binaryFiles) {
            addEntry("$zipPrefix/$resourceName", resourceBytes("$resourcePrefix/$resourceName"))
        }
    }

    private fun ZipOutputStream.addToolSymlinks(entryPrefix: String): List<String> {
        val links = mapOf(
            "${entryPrefix}.claude/skills" to "../.asset-bundles/skills",
            "${entryPrefix}.claude/commands" to "../.asset-bundles/commands",
            "${entryPrefix}.claude/agents" to "../.asset-bundles/agents",
            "${entryPrefix}.agents/skills" to "../.asset-bundles/skills",
            "${entryPrefix}.agents/commands" to "../.asset-bundles/commands",
            "${entryPrefix}.agents/agents" to "../.asset-bundles/agents",
        )
        for ((name, target) in links) {
            with(ZipSymlinkSupport) {
                this@addToolSymlinks.addSymlinkEntry(name, target)
            }
        }
        return links.keys.toList()
    }

    private fun resourceText(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("Missing classpath resource: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun resourceBytes(path: String): ByteArray {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("Missing classpath resource: $path")
        return stream.use { it.readBytes() }
    }
}
