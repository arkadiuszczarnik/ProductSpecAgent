package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.*

class ConsistencyCheckServiceTest {

    @TempDir lateinit var tempDir: Path

    private lateinit var projectService: ProjectService
    private lateinit var taskService: TaskService
    private lateinit var decisionService: DecisionService
    private lateinit var clarificationService: ClarificationService
    private lateinit var checkService: ConsistencyCheckService

    @BeforeEach
    fun setup() {
        val projectStorage = ProjectStorage(tempDir.toString())
        val taskStorage = TaskStorage(tempDir.toString())
        val decisionStorage = DecisionStorage(tempDir.toString())
        val clarificationStorage = ClarificationStorage(tempDir.toString())

        projectService = ProjectService(projectStorage)

        // Create fake agents that won't be called (we create decisions/clarifications directly via storage)
        val fakeDecisionAgent = object : com.agentwork.productspecagent.agent.DecisionAgent(
            com.agentwork.productspecagent.agent.SpecContextBuilder(projectService)
        ) {
            override suspend fun runAgent(prompt: String) = "{}"
        }
        val fakePlanAgent = object : com.agentwork.productspecagent.agent.PlanGeneratorAgent(
            com.agentwork.productspecagent.agent.SpecContextBuilder(projectService)
        ) {
            override suspend fun runAgent(prompt: String) = "{}"
        }

        taskService = TaskService(taskStorage, fakePlanAgent)
        decisionService = DecisionService(decisionStorage, fakeDecisionAgent)
        clarificationService = ClarificationService(clarificationStorage)
        checkService = ConsistencyCheckService(projectService, taskService, decisionService, clarificationService)
    }

    @Test
    fun `clean project returns passed report`() {
        val project = projectService.createProject("Clean")
        val report = checkService.runChecks(project.project.id)

        assertTrue(report.summary.passed)
        assertEquals(0, report.summary.errors)
    }

    @Test
    fun `detects unresolved decisions`() {
        val project = projectService.createProject("Test")
        val pid = project.project.id

        // Create a pending decision directly via storage
        val decision = Decision(
            id = "d1", projectId = pid, stepType = FlowStepType.FEATURES,
            title = "MVP scope?", options = emptyList(), recommendation = "TBD",
            createdAt = "2026-03-30T00:00:00Z"
        )
        DecisionStorage(tempDir.toString()).saveDecision(decision)

        val report = checkService.runChecks(pid)
        assertTrue(report.results.any { it.category == "unresolved-decision" })
    }

    @Test
    fun `detects open clarifications`() {
        val project = projectService.createProject("Test")
        val pid = project.project.id

        val clarification = Clarification(
            id = "c1", projectId = pid, stepType = FlowStepType.FEATURES,
            question = "How handle offline?", reason = "Contradiction",
            createdAt = "2026-03-30T00:00:00Z"
        )
        ClarificationStorage(tempDir.toString()).saveClarification(clarification)

        val report = checkService.runChecks(pid)
        assertTrue(report.results.any { it.category == "open-clarification" })
    }

    @Test
    fun `detects coverage gaps`() {
        val project = projectService.createProject("Test")
        val report = checkService.runChecks(project.project.id)

        // No tasks at all → all steps uncovered
        assertTrue(report.results.any { it.category == "coverage" })
    }

    @Test
    fun `summary counts are correct`() {
        val project = projectService.createProject("Test")
        val report = checkService.runChecks(project.project.id)

        assertEquals(
            report.results.count { it.severity == CheckSeverity.ERROR },
            report.summary.errors
        )
        assertEquals(
            report.results.count { it.severity == CheckSeverity.WARNING },
            report.summary.warnings
        )
    }
}
