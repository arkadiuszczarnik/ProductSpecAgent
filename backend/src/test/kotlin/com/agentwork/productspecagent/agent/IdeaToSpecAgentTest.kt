package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ClarificationService
import com.agentwork.productspecagent.service.DecisionService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.PromptRegistry
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.TaskService
import com.agentwork.productspecagent.service.WizardFeatureInput
import com.agentwork.productspecagent.storage.ClarificationStorage
import com.agentwork.productspecagent.storage.DecisionStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.TaskStorage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import kotlin.test.*

class IdeaToSpecAgentTest {

    private lateinit var storage: ProjectStorage
    private lateinit var projectService: ProjectService
    private lateinit var contextBuilder: SpecContextBuilder
    private lateinit var decisionStorage: DecisionStorage
    private lateinit var decisionService: DecisionService
    private lateinit var clarificationStorage: ClarificationStorage
    private lateinit var clarificationService: ClarificationService
    private lateinit var taskStorage: TaskStorage
    private lateinit var taskService: TaskService
    private lateinit var promptService: PromptService

    @BeforeEach
    fun setup() {
        storage = ProjectStorage(InMemoryObjectStore())
        projectService = ProjectService(storage)
        contextBuilder = SpecContextBuilder(projectService)
        promptService = PromptService(PromptRegistry(), InMemoryObjectStore())
        decisionStorage = DecisionStorage(InMemoryObjectStore())
        val fakeDecisionAgent = object : DecisionAgent(contextBuilder, promptService) {
            override suspend fun runAgent(prompt: String): String {
                return """{"options":[{"label":"Yes","pros":["pro1"],"cons":[],"recommended":true},{"label":"No","pros":[],"cons":["con1"],"recommended":false}],"recommendation":"Go with Yes"}"""
            }
        }
        decisionService = DecisionService(decisionStorage, fakeDecisionAgent)
        clarificationStorage = ClarificationStorage(InMemoryObjectStore())
        clarificationService = ClarificationService(clarificationStorage)
        taskStorage = TaskStorage(InMemoryObjectStore())
        val fakePlanAgent = object : PlanGeneratorAgent(contextBuilder, promptService) {
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
        return object : IdeaToSpecAgent(contextBuilder, projectService, promptService, decisionService, clarificationService) {
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

        // Step files are no longer written; final output lives in spec/spec.md.
        val specContent = projectService.readSpecFile(project.project.id, "idea.md")
        assertNull(specContent)
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
}
