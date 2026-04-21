package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.export.DocsScaffoldGenerator
import com.agentwork.productspecagent.export.ScaffoldContext
import com.agentwork.productspecagent.export.ScaffoldContextBuilder
import com.agentwork.productspecagent.export.FeatureContext
import com.agentwork.productspecagent.export.DecisionContext
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

class ProjectNotFoundException(id: String) : RuntimeException("Project not found: $id")

@Service
class ProjectService(
    private val storage: ProjectStorage,
    private val scaffoldGenerator: DocsScaffoldGenerator? = null,
    @Lazy private val scaffoldContextBuilder: ScaffoldContextBuilder? = null
) {

    fun createProject(name: String): ProjectResponse {
        val now = Instant.now().toString()
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            ownerId = "anonymous",
            status = ProjectStatus.DRAFT,
            createdAt = now,
            updatedAt = now
        )
        val flowState = createInitialFlowState(project.id)

        storage.saveProject(project)
        storage.saveFlowState(flowState)

        // Prefill wizard IDEA.productName so the user doesn't re-type the name.
        val initialWizard = WizardData(
            projectId = project.id,
            steps = mapOf(
                "IDEA" to WizardStepData(
                    fields = mapOf("productName" to JsonPrimitive(name))
                )
            )
        )
        storage.saveWizardData(project.id, initialWizard)

        storage.saveSpecStep(project.id, "idea.md", "# Idea\n\n")

        // Generate initial docs scaffold
        generateDocsScaffold(project.id, name)

        return ProjectResponse(project = project, flowState = flowState)
    }

    fun regenerateDocsScaffold(projectId: String) {
        val project = storage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        generateDocsScaffold(projectId, project.name)
    }

    private fun generateDocsScaffold(projectId: String, projectName: String) {
        val generator = scaffoldGenerator ?: return
        val context = if (scaffoldContextBuilder != null) {
            scaffoldContextBuilder.build(projectId)
        } else {
            ScaffoldContext(
                projectName = projectName,
                features = emptyList(),
                decisions = emptyList(),
                scopeContent = null,
                mvpContent = null,
                techStack = "See SPEC.md for tech stack details.",
                problemContent = null,
                targetAudienceContent = null,
                architectureContent = null,
                backendContent = null,
                frontendContent = null
            )
        }
        val entries = generator.generate(context)
        for ((path, content) in entries) {
            storage.saveDocsFile(projectId, path, content)
        }
    }

    fun getProject(id: String): ProjectResponse {
        val project = storage.loadProject(id) ?: throw ProjectNotFoundException(id)
        val flowState = storage.loadFlowState(id) ?: throw ProjectNotFoundException(id)
        return ProjectResponse(project = project, flowState = flowState)
    }

    fun deleteProject(id: String) {
        storage.loadProject(id) ?: throw ProjectNotFoundException(id)
        storage.deleteProject(id)
    }

    fun listProjects(): List<Project> = storage.listProjects()

    fun getFlowState(id: String): FlowState {
        storage.loadProject(id) ?: throw ProjectNotFoundException(id)
        return storage.loadFlowState(id) ?: throw ProjectNotFoundException(id)
    }

    fun updateFlowState(id: String, flowState: FlowState) {
        storage.loadProject(id) ?: throw ProjectNotFoundException(id)
        storage.saveFlowState(flowState)
    }

    fun saveSpecFile(projectId: String, fileName: String, content: String) {
        storage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        storage.saveSpecStep(projectId, fileName, content)
        regenerateDocsScaffold(projectId)
    }

    fun readSpecFile(projectId: String, fileName: String): String? {
        return storage.loadSpecStep(projectId, fileName)
    }

    fun listDocsFiles(projectId: String): List<Pair<String, String>> {
        storage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        return storage.listDocsFiles(projectId)
    }
}
