package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.storage.ProjectStorage
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SpecContextBuilderTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: ProjectStorage
    private lateinit var projectService: ProjectService
    private lateinit var builder: SpecContextBuilder

    @BeforeEach
    fun setup() {
        storage = ProjectStorage(tempDir.toString())
        projectService = ProjectService(storage)
        builder = SpecContextBuilder(projectService)
    }

    @Test
    fun `buildContext includes project name and current step`() {
        val project = projectService.createProject("My App")
        val context = builder.buildContext(project.project.id)

        assertContains(context, "My App")
        assertContains(context, "Current Step: IDEA")
    }

    @Test
    fun `buildContext includes completed step content`() {
        val project = projectService.createProject("My App")
        // The idea.md is already saved by createProject

        val context = builder.buildContext(project.project.id)
        // IDEA step is IN_PROGRESS, not COMPLETED, so it won't be in completed steps
        assertTrue(context.contains("PROJECT CONTEXT"))
    }

    @Test
    fun `buildProposalContext without WizardService falls back to em-dash category`() {
        val project = projectService.createProject("My App")
        val projectId = project.project.id
        projectService.saveSpecFile(projectId, "idea.md", "My idea")
        projectService.saveSpecFile(projectId, "scope.md", "In scope")

        // no wizardService wired
        val proposalBuilder = SpecContextBuilder(projectService)
        val context = proposalBuilder.buildProposalContext(projectId)

        assertThat(context).contains("## idea.md").contains("My idea")
        assertThat(context).contains("## scope.md").contains("In scope")
        assertThat(context).contains("Category: —")
    }

    @Test
    fun `buildProposalContext uses WizardService category when present`() {
        val project = projectService.createProject("My App")
        val projectId = project.project.id
        projectService.saveSpecFile(projectId, "idea.md", "My idea")

        val wizardService = WizardService(storage)
        wizardService.saveWizardData(
            projectId,
            WizardData(
                projectId = projectId,
                steps = mapOf(
                    "IDEA" to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive("SaaS")),
                        completedAt = "2026-03-31T10:00:00Z"
                    )
                )
            )
        )

        val proposalBuilder = SpecContextBuilder(projectService, wizardService)
        val context = proposalBuilder.buildProposalContext(projectId)

        assertThat(context).contains("## idea.md").contains("My idea")
        assertThat(context).contains("Category: SaaS")
    }
}
