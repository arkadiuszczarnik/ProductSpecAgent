package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.Clarification
import com.agentwork.productspecagent.domain.Decision
import com.agentwork.productspecagent.domain.DecisionStatus
import com.agentwork.productspecagent.domain.FlowState
import com.agentwork.productspecagent.domain.FlowStepStatus
import com.agentwork.productspecagent.domain.Project
import com.agentwork.productspecagent.domain.SpecTask
import com.agentwork.productspecagent.domain.TaskType
import com.agentwork.productspecagent.service.ClarificationService
import com.agentwork.productspecagent.service.DecisionService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.TaskService
import com.agentwork.productspecagent.service.WizardMarkdown
import com.agentwork.productspecagent.service.WizardService
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import org.springframework.stereotype.Service
import java.io.StringWriter

private val deprecatedScaffoldDocs = setOf(
    "docs/architecture/overview.md",
    "docs/backend/api.md",
    "docs/frontend/design.md",
)

private fun isGeneratedExportDoc(relativePath: String): Boolean =
    relativePath == "docs/SPEC.md" ||
        relativePath == "docs/spec.md" ||
        relativePath == "docs/PLAN.md" ||
        relativePath == "docs/plan.md" ||
        relativePath.startsWith("docs/decisions/") ||
        relativePath.startsWith("docs/clarifications/") ||
        relativePath.startsWith("docs/tasks/")

@Service
class ProjectPackageAssembler(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService,
    private val wizardService: WizardService,
    private val assetBundleExporter: AssetBundleExporter,
) {
    private val mf: MustacheFactory = DefaultMustacheFactory("templates/export")
    private val handoffMf: MustacheFactory = DefaultMustacheFactory("templates/handoff")

    fun writeProjectPackage(
        projectId: String,
        options: ProjectExportOptions,
        writer: ZipArchiveWriter,
        includeAgentTemplateFiles: Boolean,
        includeToolLinks: Boolean = true,
    ) {
        val projectResponse = projectService.getProject(projectId)
        val project = projectResponse.project
        val flowState = projectResponse.flowState
        val wizardData = wizardService.getWizardData(projectId)
        val matchedBundles = assetBundleExporter.matchedBundles(wizardData)

        writer.addText("README.md", generateReadme(project, flowState, matchedBundles))

        if (includeAgentTemplateFiles) {
            val agentTemplateMarkdown = generateAgentTemplateMarkdown()
            writer.addText("CLAUDE.md", agentTemplateMarkdown)
            writer.addText("AGENTS.md", agentTemplateMarkdown)
        }

        writer.addText("docs/spec.md", generateSpec(projectId, flowState))

        writer.addText(".gitignore", ".DS_Store\nnode_modules/\n.env\n")

        val docsFiles = projectService.listDocsFiles(projectId)
            .filterNot { it.first in deprecatedScaffoldDocs }
            .filterNot { isGeneratedExportDoc(it.first) }
        for ((relativePath, content) in docsFiles) {
            writer.addBytes(relativePath, content)
        }

        if (options.includeDecisions) {
            val decisions = decisionService.listDecisions(projectId)
            decisions.forEachIndexed { i, decision ->
                val slug = decision.title.slug().take(50)
                writer.addText("docs/decisions/${String.format("%03d", i + 1)}-$slug.md", generateDecisionMd(decision))
            }
        }

        if (options.includeClarifications) {
            val clarifications = clarificationService.listClarifications(projectId)
            clarifications.forEachIndexed { i, clarification ->
                val slug = clarification.question.slug().take(50)
                writer.addText("docs/clarifications/${String.format("%03d", i + 1)}-$slug.md", generateClarificationMd(clarification))
            }
        }

        if (options.includeTasks) {
            val tasks = taskService.listTasks(projectId)
            if (tasks.isNotEmpty()) {
                writer.addText("docs/plan.md", generatePlanMd(tasks))
                tasks.forEachIndexed { i, task ->
                    val slug = task.title.slug().take(50)
                    val typePrefix = task.type.name.lowercase()
                    writer.addText("docs/tasks/${String.format("%03d", i + 1)}-$typePrefix-$slug.md", generateTaskMd(task))
                }
            }
        }

        assetBundleExporter.writeToArchive(writer, matchedBundles)
        if (includeToolLinks) {
            assetBundleExporter.writeToolLinksToArchive(writer)
        }
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
            return render("spec.md.mustache", mapOf("sections" to sections))
        }

        val wizardData = wizardService.getWizardData(projectId)
        for (step in flowState.steps) {
            val content = wizardData.steps[step.stepType.name]
                ?.fields
                ?.let { WizardMarkdown.renderStep(step.stepType.name, it) }
            if (content != null) {
                sections.add(mapOf("content" to content))
            }
        }
        return render("spec.md.mustache", mapOf("sections" to sections))
    }

    private fun generateDecisionMd(decision: Decision): String =
        render(
            "decision.md.mustache",
            mapOf(
                "status" to decision.status.name,
                "step" to decision.stepType.name,
                "createdAt" to decision.createdAt,
                "resolvedAt" to decision.resolvedAt,
                "title" to decision.title,
                "options" to decision.options.map {
                    mapOf(
                        "label" to it.label,
                        "recommended" to it.recommended,
                        "pros" to it.pros.joinToString(", "),
                        "cons" to it.cons.joinToString(", "),
                    )
                },
                "recommendation" to decision.recommendation,
                "resolved" to (decision.status == DecisionStatus.RESOLVED),
                "chosen" to (decision.options.find { it.id == decision.chosenOptionId }?.label ?: decision.chosenOptionId),
                "rationale" to decision.rationale,
            ),
        )

    private fun generateClarificationMd(clarification: Clarification): String =
        render(
            "clarification.md.mustache",
            mapOf(
                "status" to clarification.status.name,
                "step" to clarification.stepType.name,
                "createdAt" to clarification.createdAt,
                "answeredAt" to clarification.answeredAt,
                "question" to clarification.question,
                "reason" to clarification.reason,
                "answer" to clarification.answer,
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

    private fun generateTaskMd(task: SpecTask): String =
        render(
            "task.md.mustache",
            mapOf(
                "type" to task.type.name,
                "status" to task.status.name,
                "estimate" to task.estimate,
                "priority" to task.priority,
                "parentId" to task.parentId,
                "specSection" to task.specSection?.name,
                "dependencies" to task.dependencies.joinToString(", ").ifBlank { null },
                "title" to task.title,
                "description" to task.description,
            ),
        )

    private fun generateAgentTemplateMarkdown(): String =
        renderHandoff("agent-template.md.mustache", emptyMap<String, Any>())

    private fun render(templatePath: String, scope: Any): String {
        val mustache = mf.compile(templatePath)
        val writer = StringWriter()
        mustache.execute(writer, scope).flush()
        return writer.toString()
    }

    private fun renderHandoff(templatePath: String, scope: Any): String {
        val mustache = handoffMf.compile(templatePath)
        val writer = StringWriter()
        mustache.execute(writer, scope).flush()
        return writer.toString()
    }
}
