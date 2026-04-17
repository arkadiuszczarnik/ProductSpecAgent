package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ClarificationService
import com.agentwork.productspecagent.service.DecisionService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.TaskService
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.storage.ClarificationStorage
import com.agentwork.productspecagent.storage.DecisionStorage
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.TaskStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.*

class IdeaToSpecAgentTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: ProjectStorage
    private lateinit var projectService: ProjectService
    private lateinit var contextBuilder: SpecContextBuilder
    private lateinit var decisionStorage: DecisionStorage
    private lateinit var decisionService: DecisionService
    private lateinit var clarificationStorage: ClarificationStorage
    private lateinit var clarificationService: ClarificationService
    private lateinit var wizardService: WizardService
    private lateinit var taskService: TaskService

    @BeforeEach
    fun setup() {
        storage = ProjectStorage(tempDir.toString())
        projectService = ProjectService(storage)
        contextBuilder = SpecContextBuilder(projectService)
        decisionStorage = DecisionStorage(tempDir.toString())
        val fakeDecisionAgent = object : DecisionAgent(contextBuilder) {
            override suspend fun runAgent(prompt: String): String {
                return """{"options":[{"label":"Yes","pros":["pro1"],"cons":[],"recommended":true},{"label":"No","pros":[],"cons":["con1"],"recommended":false}],"recommendation":"Go with Yes"}"""
            }
        }
        decisionService = DecisionService(decisionStorage, fakeDecisionAgent)
        clarificationStorage = ClarificationStorage(tempDir.toString())
        clarificationService = ClarificationService(clarificationStorage)
        wizardService = WizardService(storage)
        val taskStorage = TaskStorage(tempDir.toString())
        val fakePlanAgent = object : PlanGeneratorAgent(contextBuilder) {
            override suspend fun runAgent(prompt: String): String = """{"epics":[]}"""
        }
        taskService = TaskService(taskStorage, fakePlanAgent)
    }

    private fun createTestAgent(agentResponse: String): IdeaToSpecAgent {
        return object : IdeaToSpecAgent(contextBuilder, projectService, "You are IdeaToSpec.", decisionService, clarificationService, wizardService, taskService) {
            override suspend fun runAgent(systemPrompt: String, userMessage: String): String {
                return agentResponse
            }
        }
    }

    @Test
    fun `chat returns plain message when no STEP_COMPLETE marker`() = runBlocking {
        val project = projectService.createProject("Test")
        val agent = createTestAgent("What problem are you solving?")

        val response = agent.chat(project.project.id, "Hello")

        assertEquals("What problem are you solving?", response.message)
        assertFalse(response.flowStateChanged)
        assertEquals("IDEA", response.currentStep)
    }

    @Test
    fun `chat advances flow when STEP_COMPLETE marker present`() = runBlocking {
        val project = projectService.createProject("Test")
        val agent = createTestAgent(
            "Great idea!\n[STEP_COMPLETE]\n[STEP_SUMMARY]: The idea is about productivity tracking."
        )

        val response = agent.chat(project.project.id, "My idea is about tracking productivity")

        assertTrue(response.flowStateChanged)
        assertEquals("PROBLEM", response.currentStep)
        assertFalse(response.message.contains("[STEP_COMPLETE]"))
        assertFalse(response.message.contains("[STEP_SUMMARY]"))

        // Verify flow state was updated
        val flowState = projectService.getFlowState(project.project.id)
        assertEquals(FlowStepType.PROBLEM, flowState.currentStep)
        val ideaStep = flowState.steps.find { it.stepType == FlowStepType.IDEA }
        assertEquals(FlowStepStatus.COMPLETED, ideaStep?.status)
        val problemStep = flowState.steps.find { it.stepType == FlowStepType.PROBLEM }
        assertEquals(FlowStepStatus.IN_PROGRESS, problemStep?.status)

        // Verify spec file was saved
        val specContent = projectService.readSpecFile(project.project.id, "idea.md")
        assertNotNull(specContent)
    }

    @Test
    fun `chat does not advance beyond FRONTEND step`() = runBlocking {
        val project = projectService.createProject("Test")
        // Manually advance to FRONTEND step (the last step)
        val flowState = projectService.getFlowState(project.project.id)
        val allCompleted = flowState.steps.map { step ->
            if (step.stepType == FlowStepType.FRONTEND) step.copy(status = FlowStepStatus.IN_PROGRESS)
            else step.copy(status = FlowStepStatus.COMPLETED)
        }
        projectService.updateFlowState(project.project.id, flowState.copy(
            steps = allCompleted, currentStep = FlowStepType.FRONTEND
        ))

        val agent = createTestAgent("Here is your frontend spec.\n[STEP_COMPLETE]\n[STEP_SUMMARY]: Full frontend specification.")
        val response = agent.chat(project.project.id, "Finalize")

        assertTrue(response.flowStateChanged)
        assertEquals("FRONTEND", response.currentStep)
    }

    @Test
    fun `chat strips markers from message`() = runBlocking {
        val project = projectService.createProject("Test")
        val agent = createTestAgent("Summary done.\n[STEP_COMPLETE]\n[STEP_SUMMARY]: The summary.")

        val response = agent.chat(project.project.id, "Summarize")

        assertFalse(response.message.contains("[STEP_COMPLETE]"))
        assertFalse(response.message.contains("[STEP_SUMMARY]"))
        assertTrue(response.message.contains("Summary done."))
    }

    @Test
    fun `chat creates decision when DECISION_NEEDED marker present`() = runBlocking {
        val project = projectService.createProject("Test")
        val agent = createTestAgent(
            "I think we need to decide on the scope.\n[DECISION_NEEDED]: Should feature X be in MVP?"
        )

        val response = agent.chat(project.project.id, "What about the scope?")

        assertFalse(response.message.contains("[DECISION_NEEDED]"))
        assertNotNull(response.decisionId)

        // Verify decision was created
        val decisions = decisionService.listDecisions(project.project.id)
        assertEquals(1, decisions.size)
        assertEquals("Should feature X be in MVP?", decisions[0].title)
    }

    @Test
    fun `chat creates clarification when CLARIFICATION_NEEDED marker present`() = runBlocking {
        val project = projectService.createProject("Test")
        val agent = createTestAgent(
            "I noticed a gap.\n[CLARIFICATION_NEEDED]: How should offline users sync? | The spec mentions both online-first and offline support"
        )
        val response = agent.chat(project.project.id, "What about sync?")

        assertFalse(response.message.contains("[CLARIFICATION_NEEDED]"))
        assertNotNull(response.clarificationId)

        val clarifications = clarificationService.listClarifications(project.project.id)
        assertEquals(1, clarifications.size)
        assertEquals("How should offline users sync?", clarifications[0].question)
    }

    @Test
    fun `FEATURES step passes graph block with resolved category to agent prompt`() = runBlocking<Unit> {
        val project = projectService.createProject("Test")
        val projectId = project.project.id

        // Seed IDEA step category = "SaaS" in the persisted wizard state
        wizardService.saveWizardData(
            projectId,
            WizardData(
                projectId = projectId,
                steps = mapOf(
                    FlowStepType.IDEA.name to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive("SaaS")),
                        completedAt = "2026-04-17T10:00:00Z"
                    )
                )
            )
        )

        val capturedUserPrompts = mutableListOf<String>()
        val agent = object : IdeaToSpecAgent(
            contextBuilder, projectService, "You are IdeaToSpec.",
            decisionService, clarificationService, wizardService, taskService
        ) {
            override suspend fun runAgent(systemPrompt: String, userMessage: String): String {
                capturedUserPrompts.add(userMessage)
                return "OK."
            }
        }

        val fields = mapOf<String, Any>(
            "features" to listOf(
                mapOf(
                    "id" to "f-1",
                    "title" to "Login",
                    "scopes" to listOf("BACKEND"),
                    "scopeFields" to mapOf("apiEndpoints" to "POST /auth/login")
                )
            ),
            "edges" to emptyList<Any>()
        )
        agent.processWizardStep(projectId, FlowStepType.FEATURES.name, fields)

        assertThat(capturedUserPrompts).isNotEmpty
        val lastPrompt = capturedUserPrompts.last()
        assertThat(lastPrompt)
            .contains("Features & Dependencies (Category: SaaS)")
            .contains("[f-1] Login (Backend)")
    }

    @Test
    fun `FEATURES step with an isolated node triggers clarification`() = runBlocking<Unit> {
        val project = projectService.createProject("Test")
        val projectId = project.project.id

        // Seed IDEA step category = "SaaS" in the persisted wizard state
        wizardService.saveWizardData(
            projectId,
            WizardData(
                projectId = projectId,
                steps = mapOf(
                    FlowStepType.IDEA.name to WizardStepData(
                        fields = mapOf("category" to JsonPrimitive("SaaS")),
                        completedAt = "2026-04-17T10:00:00Z"
                    )
                )
            )
        )

        val agent = object : IdeaToSpecAgent(
            contextBuilder, projectService, "You are IdeaToSpec.",
            decisionService, clarificationService, wizardService, taskService
        ) {
            override suspend fun runAgent(systemPrompt: String, userMessage: String): String {
                // The Feature 22 FEATURES-step validator rules must mention "isolated" so the
                // LLM knows to flag lonely graph nodes.
                assertThat(systemPrompt).contains("isolated")
                return "Feature noted.\n[CLARIFICATION_NEEDED]: Is Loner truly independent? | It has no edges"
            }
        }

        val fields = mapOf<String, Any>(
            "features" to listOf(
                mapOf("id" to "a", "title" to "A", "scopes" to listOf("BACKEND")),
                mapOf("id" to "b", "title" to "B", "scopes" to listOf("BACKEND")),
                mapOf("id" to "loner", "title" to "Loner", "scopes" to listOf("BACKEND"))
            ),
            "edges" to listOf(mapOf("id" to "e1", "from" to "a", "to" to "b"))
        )
        agent.processWizardStep(projectId, FlowStepType.FEATURES.name, fields)

        val clarifications = clarificationService.listClarifications(projectId)
        assertThat(clarifications).isNotEmpty
        assertTrue(clarifications.any { it.question.contains("Loner") })
    }
}
