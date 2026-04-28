package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ClarificationService
import com.agentwork.productspecagent.service.DecisionService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.TaskService
import com.agentwork.productspecagent.service.WizardFeatureInput
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.storage.ClarificationStorage
import com.agentwork.productspecagent.storage.DecisionStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.TaskStorage
import kotlinx.coroutines.runBlocking
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
    private lateinit var taskStorage: TaskStorage
    private lateinit var taskService: TaskService

    @BeforeEach
    fun setup() {
        storage = ProjectStorage(InMemoryObjectStore())
        projectService = ProjectService(storage)
        contextBuilder = SpecContextBuilder(projectService)
        decisionStorage = DecisionStorage(InMemoryObjectStore())
        val fakeDecisionAgent = object : DecisionAgent(contextBuilder) {
            override suspend fun runAgent(prompt: String): String {
                return """{"options":[{"label":"Yes","pros":["pro1"],"cons":[],"recommended":true},{"label":"No","pros":[],"cons":["con1"],"recommended":false}],"recommendation":"Go with Yes"}"""
            }
        }
        decisionService = DecisionService(decisionStorage, fakeDecisionAgent)
        clarificationStorage = ClarificationStorage(InMemoryObjectStore())
        clarificationService = ClarificationService(clarificationStorage)
        wizardService = WizardService(storage)
        taskStorage = TaskStorage(tempDir.toString())
        val fakePlanAgent = object : PlanGeneratorAgent(contextBuilder) {
            override suspend fun generatePlanForFeature(
                projectId: String,
                input: WizardFeatureInput,
                startPriority: Int,
            ): List<SpecTask> {
                val now = java.time.Instant.now().toString()
                return listOf(
                    SpecTask(
                        id = "epic-${input.id}",
                        projectId = projectId,
                        type = TaskType.EPIC,
                        title = input.title,
                        estimate = "M",
                        priority = startPriority,
                        specSection = FlowStepType.FEATURES,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        taskService = TaskService(taskStorage, fakePlanAgent)
    }

    private fun createTestAgent(agentResponse: String): IdeaToSpecAgent {
        return object : IdeaToSpecAgent(contextBuilder, projectService, "You are IdeaToSpec.", decisionService, clarificationService, wizardService, taskService = taskService) {
            override suspend fun runAgent(systemPrompt: String, userMessage: String): String {
                return agentResponse
            }
        }
    }

    /** Helper: Agent, der alle empfangenen User-Prompts aufzeichnet */
    private fun createCapturingAgent(
        agentResponse: String = "OK.",
        capturedUserPrompts: MutableList<String>
    ): IdeaToSpecAgent {
        return object : IdeaToSpecAgent(contextBuilder, projectService, "You are IdeaToSpec.", decisionService, clarificationService, wizardService, taskService = taskService) {
            override suspend fun runAgent(systemPrompt: String, userMessage: String): String {
                capturedUserPrompts.add(userMessage)
                return agentResponse
            }
        }
    }

    /** Spy-TaskService: zeichnet Aufrufe von replaceWizardFeatureTasks auf */
    private class SpyTaskService(
        storage: TaskStorage,
        agent: PlanGeneratorAgent,
    ) : TaskService(storage, agent) {
        val capturedCalls = mutableListOf<List<WizardFeatureInput>>()

        override suspend fun replaceWizardFeatureTasks(
            projectId: String,
            features: List<WizardFeatureInput>,
        ): List<SpecTask> {
            capturedCalls.add(features)
            return super.replaceWizardFeatureTasks(projectId, features)
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
        Unit
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

    // ─── T6: FEATURES step validator tests ───────────────────────────────────

    @Test
    fun `FEATURES step passes graph block to agent prompt`() = runBlocking {
        val project = projectService.createProject("Test")
        val captured = mutableListOf<String>()
        val agent = createCapturingAgent(agentResponse = "OK.", capturedUserPrompts = captured)

        agent.processWizardStep(
            projectId = project.project.id,
            step = "FEATURES",
            fields = mapOf(
                "features" to listOf(
                    mapOf(
                        "id" to "f-1",
                        "title" to "Login",
                        "scopes" to listOf("BACKEND"),
                        "scopeFields" to mapOf("apiEndpoints" to "POST /auth/login"),
                    )
                ),
                "edges" to emptyList<Any>(),
            ),
        )

        assertThat(captured).isNotEmpty
        // Der User-Prompt muss den Graph-Block enthalten (wird von buildWizardStepFeedbackPrompt erzeugt)
        assertThat(captured.last())
            .contains("Features & Dependencies")
            .contains("[f-1] Login (Backend)")
        Unit
    }

    // ─── Last-step finalization: agent must not emit blockers on FRONTEND ──────

    @Test
    fun `processWizardStep on FRONTEND ignores CLARIFICATION_NEEDED marker`() = runBlocking {
        val project = projectService.createProject("Test")
        val agent = createTestAgent(
            "Alles sieht gut aus!\n[CLARIFICATION_NEEDED]: Welches Theme? | Theme ist unklar"
        )

        val response = agent.processWizardStep(
            projectId = project.project.id,
            step = "FRONTEND",
            fields = mapOf("framework" to "Next.js+React", "theme" to "Both"),
        )

        // Last step must not create a clarification even if the agent emits the marker
        assertThat(response.clarificationId).isNull()
        assertThat(clarificationService.listClarifications(project.project.id)).isEmpty()
        assertThat(response.exportTriggered).isTrue()
        assertThat(response.nextStep).isNull()
    }

    @Test
    fun `processWizardStep on FRONTEND ignores DECISION_NEEDED marker`() = runBlocking {
        val project = projectService.createProject("Test")
        val agent = createTestAgent(
            "Looks great!\n[DECISION_NEEDED]: Light vs. dark?"
        )

        val response = agent.processWizardStep(
            projectId = project.project.id,
            step = "FRONTEND",
            fields = mapOf("framework" to "Next.js+React"),
        )

        assertThat(response.decisionId).isNull()
        assertThat(decisionService.listDecisions(project.project.id)).isEmpty()
        assertThat(response.exportTriggered).isTrue()
        Unit
    }

    @Test
    fun `processWizardStep on FRONTEND does not include MARKER_REMINDER in prompt`() = runBlocking {
        val project = projectService.createProject("Test")
        val captured = mutableListOf<String>()
        val agent = createCapturingAgent(agentResponse = "Done.", capturedUserPrompts = captured)

        agent.processWizardStep(
            projectId = project.project.id,
            step = "FRONTEND",
            fields = mapOf("framework" to "Next.js+React"),
        )

        // The feedback call is the FIRST invocation; a second call generates the summary.
        // Neither should push markers on the final step.
        assertThat(captured).isNotEmpty
        for (prompt in captured) {
            assertThat(prompt).doesNotContain("MANDATORY OUTPUT REQUIREMENT")
            assertThat(prompt).doesNotContain("Err on the side of including a marker")
        }
    }

    @Test
    fun `processWizardStep on non-last step still creates clarification from marker`() = runBlocking {
        val project = projectService.createProject("Test")
        val agent = createTestAgent(
            "Gut!\n[CLARIFICATION_NEEDED]: Wer ist die Zielgruppe? | Grundlage fuer alles weitere"
        )

        val response = agent.processWizardStep(
            projectId = project.project.id,
            step = "PROBLEM",
            fields = mapOf("description" to "Kurz"),
        )

        assertThat(response.clarificationId).isNotNull()
        assertThat(clarificationService.listClarifications(project.project.id)).hasSize(1)
        assertThat(response.exportTriggered).isFalse()
        Unit
    }

    @Test
    fun `FEATURES step calls replaceWizardFeatureTasks with parsed input`() = runBlocking {
        val project = projectService.createProject("Test")
        val fakePlanAgentForSpy = object : PlanGeneratorAgent(contextBuilder) {
            override suspend fun generatePlanForFeature(
                projectId: String,
                input: WizardFeatureInput,
                startPriority: Int,
            ): List<SpecTask> {
                val now = java.time.Instant.now().toString()
                return listOf(
                    SpecTask(
                        id = "epic-${input.id}",
                        projectId = projectId,
                        type = TaskType.EPIC,
                        title = input.title,
                        estimate = "M",
                        priority = startPriority,
                        specSection = FlowStepType.FEATURES,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        val spyStorage = TaskStorage(tempDir.toString())
        val spyTaskService = SpyTaskService(spyStorage, fakePlanAgentForSpy)

        val agent = object : IdeaToSpecAgent(
            contextBuilder, projectService, "You are IdeaToSpec.",
            decisionService, clarificationService, wizardService,
            taskService = spyTaskService,
        ) {
            override suspend fun runAgent(systemPrompt: String, userMessage: String): String = "OK."
        }

        agent.processWizardStep(
            projectId = project.project.id,
            step = "FEATURES",
            fields = mapOf(
                "features" to listOf(
                    mapOf(
                        "id" to "f-1",
                        "title" to "Login",
                        "scopes" to listOf("BACKEND"),
                        "scopeFields" to emptyMap<String, String>(),
                    )
                ),
                "edges" to emptyList<Any>(),
            ),
        )

        assertThat(spyTaskService.capturedCalls).hasSize(1)
        val parsedList = spyTaskService.capturedCalls.first()
        assertThat(parsedList).hasSize(1)
        assertThat(parsedList.first().id).isEqualTo("f-1")
        assertThat(parsedList.first().scopes).isEqualTo(setOf(FeatureScope.BACKEND))
        Unit
    }
}
