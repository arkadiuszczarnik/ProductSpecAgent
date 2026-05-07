package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.*
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class ExportService(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService,
    private val wizardService: WizardService,
    private val assetBundleExporter: AssetBundleExporter,
) {
    private val mf: MustacheFactory = DefaultMustacheFactory("templates/export")

    fun exportProject(projectId: String, request: ExportRequest = ExportRequest()): ByteArray {
        val projectResponse = projectService.getProject(projectId)
        val project = projectResponse.project
        val flowState = projectResponse.flowState
        val prefix = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        val wizardData = wizardService.getWizardData(projectId)
        val matchedBundles = assetBundleExporter.matchedBundles(wizardData)

        val baos = ByteArrayOutputStream()
        val symlinkEntries = mutableListOf<String>()
        ZipOutputStream(baos).use { zip ->
            // README.md
            zip.addEntry("$prefix/README.md", generateReadme(project, flowState, matchedBundles))

            // SPEC.md — combine all spec steps
            zip.addEntry("$prefix/docs/SPEC.md", generateSpec(projectId, flowState))

            // Raw spec files used by the Product-Spec-Agent flow
            for ((relativePath, content) in projectService.listSpecFiles(projectId)) {
                zip.addBinaryEntry("$prefix/$relativePath", content)
            }

            // .gitignore
            zip.addEntry("$prefix/.gitignore", ".DS_Store\nnode_modules/\n.env\n")

            // Docs scaffold + Uploads (alle Dateien unter docs/ einschliesslich Binärdateien)
            for ((relativePath, content) in projectService.listDocsFiles(projectId)) {
                zip.addBinaryEntry("$prefix/$relativePath", content)
            }

            // Decisions
            if (request.includeDecisions) {
                val decisions = decisionService.listDecisions(projectId)
                if (decisions.isNotEmpty()) {
                    decisions.forEachIndexed { i, d ->
                        val slug = d.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
                        zip.addEntry("$prefix/docs/decisions/${String.format("%03d", i + 1)}-$slug.md", generateDecisionMd(d))
                    }
                }
            }

            // Clarifications
            if (request.includeClarifications) {
                val clarifications = clarificationService.listClarifications(projectId)
                if (clarifications.isNotEmpty()) {
                    clarifications.forEachIndexed { i, c ->
                        val slug = c.question.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
                        zip.addEntry("$prefix/docs/clarifications/${String.format("%03d", i + 1)}-$slug.md", generateClarificationMd(c))
                    }
                }
            }

            // Tasks / Plan
            if (request.includeTasks) {
                val tasks = taskService.listTasks(projectId)
                if (tasks.isNotEmpty()) {
                    zip.addEntry("$prefix/docs/PLAN.md", generatePlanMd(tasks))
                    tasks.forEachIndexed { i, t ->
                        val slug = t.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
                        val typePrefix = t.type.name.lowercase()
                        zip.addEntry("$prefix/docs/tasks/${String.format("%03d", i + 1)}-$typePrefix-$slug.md", generateTaskMd(t))
                    }
                }
            }

            // Asset bundles live under neutral .asset-bundles/ with Claude/Codex links.
            assetBundleExporter.writeToZip(zip, prefix, matchedBundles)
            symlinkEntries += assetBundleExporter.writeToolLinksToZip(zip, prefix)
        }

        return ZipSymlinkSupport.patchSymlinks(baos.toByteArray(), symlinkEntries.toSet())
    }

    private fun generateReadme(project: Project, flowState: FlowState, bundles: List<MatchedBundle>): String =
        render(
            "readme.md.mustache",
            mapOf(
                "projectName" to project.name,
                "currentStep" to flowState.currentStep.name,
                "steps" to flowState.steps.map { step ->
                    mapOf(
                        "icon" to when (step.status) {
                            FlowStepStatus.COMPLETED -> "[x]"
                            FlowStepStatus.IN_PROGRESS -> "[-]"
                            FlowStepStatus.OPEN -> "[ ]"
                        },
                        "name" to step.stepType.name,
                    )
                },
                "assetBundleSection" to assetBundleExporter.renderReadmeSection(bundles),
            ),
        )

    private fun generateSpec(projectId: String, flowState: FlowState): String {
        val sections = mutableListOf<Map<String, String>>()
        val autoSpec = projectService.readSpecFile(projectId, "spec.md")
        if (autoSpec != null) {
            sections.add(mapOf("content" to autoSpec))
        }

        for (step in flowState.steps) {
            val fileName = step.stepType.name.lowercase() + ".md"
            val content = projectService.readSpecFile(projectId, fileName)
            if (content != null) {
                sections.add(mapOf("content" to content))
            }
        }
        return render("spec.md.mustache", mapOf("sections" to sections))
    }

    private fun generateDecisionMd(d: Decision): String =
        render(
            "decision.md.mustache",
            mapOf(
                "status" to d.status.name,
                "step" to d.stepType.name,
                "createdAt" to d.createdAt,
                "resolvedAt" to d.resolvedAt,
                "title" to d.title,
                "options" to d.options.map {
                    mapOf(
                        "label" to it.label,
                        "recommended" to it.recommended,
                        "pros" to it.pros.joinToString(", "),
                        "cons" to it.cons.joinToString(", "),
                    )
                },
                "recommendation" to d.recommendation,
                "resolved" to (d.status == DecisionStatus.RESOLVED),
                "chosen" to (d.options.find { it.id == d.chosenOptionId }?.label ?: d.chosenOptionId),
                "rationale" to d.rationale,
            ),
        )

    private fun generateClarificationMd(c: Clarification): String =
        render(
            "clarification.md.mustache",
            mapOf(
                "status" to c.status.name,
                "step" to c.stepType.name,
                "createdAt" to c.createdAt,
                "answeredAt" to c.answeredAt,
                "question" to c.question,
                "reason" to c.reason,
                "answer" to c.answer,
            ),
        )

    private fun generatePlanMd(tasks: List<SpecTask>): String =
        render(
            "plan.md.mustache",
            mapOf(
                "epics" to tasks.filter { it.type == TaskType.EPIC }.map { epic ->
                    mapOf(
                        "title" to epic.title,
                        "description" to epic.description,
                        "stories" to tasks.filter { it.parentId == epic.id }.map { story ->
                            mapOf(
                                "title" to story.title,
                                "description" to story.description,
                                "tasks" to tasks.filter { it.parentId == story.id }.map { task ->
                                    mapOf(
                                        "title" to task.title,
                                        "estimate" to task.estimate,
                                        "description" to task.description,
                                    )
                                },
                            )
                        },
                    )
                },
            ),
        )

    private fun generateTaskMd(t: SpecTask): String =
        render(
            "task.md.mustache",
            mapOf(
                "type" to t.type.name,
                "status" to t.status.name,
                "estimate" to t.estimate,
                "priority" to t.priority,
                "parentId" to t.parentId,
                "specSection" to t.specSection?.name,
                "dependencies" to t.dependencies.joinToString(", ").ifBlank { null },
                "title" to t.title,
                "description" to t.description,
            ),
        )

    private fun render(templatePath: String, scope: Any): String {
        val mustache = mf.compile(templatePath)
        val writer = StringWriter()
        mustache.execute(writer, scope).flush()
        return writer.toString()
    }

    private fun ZipOutputStream.addEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.addBinaryEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }
}
