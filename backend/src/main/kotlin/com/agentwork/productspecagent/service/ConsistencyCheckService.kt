package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.*
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ConsistencyCheckService(
    private val projectService: ProjectService,
    private val taskService: TaskService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService
) {
    fun runChecks(projectId: String): CheckReport {
        val results = mutableListOf<CheckResult>()
        var checkId = 0
        fun nextId() = "chk-${++checkId}"

        // 1. Coverage check
        val coverage = taskService.getCoverage(projectId)
        val uncovered = coverage.filter { !it.value }.keys
        for (step in uncovered) {
            results.add(CheckResult(
                id = nextId(), severity = CheckSeverity.WARNING,
                category = "coverage",
                message = "Step '$step' has no tasks assigned.",
                suggestedFix = "Generate tasks covering the $step specification."
            ))
        }

        // 2. Orphan tasks
        val tasks = taskService.listTasks(projectId)
        val taskIds = tasks.map { it.id }.toSet()
        for (task in tasks) {
            if (task.parentId != null && task.parentId !in taskIds) {
                results.add(CheckResult(
                    id = nextId(), severity = CheckSeverity.ERROR,
                    category = "orphan-task",
                    message = "Task '${task.title}' references non-existent parent '${task.parentId}'.",
                    relatedArtifact = task.id,
                    suggestedFix = "Remove the parent reference or reassign to an existing epic/story."
                ))
            }
        }

        // 3. Unresolved decisions
        val decisions = decisionService.listDecisions(projectId)
        val pending = decisions.filter { it.status == DecisionStatus.PENDING }
        for (d in pending) {
            results.add(CheckResult(
                id = nextId(), severity = CheckSeverity.WARNING,
                category = "unresolved-decision",
                message = "Decision '${d.title}' is still pending.",
                relatedArtifact = d.id,
                suggestedFix = "Review and resolve this decision before proceeding."
            ))
        }

        // 4. Open clarifications
        val clarifications = clarificationService.listClarifications(projectId)
        val openClars = clarifications.filter { it.status == ClarificationStatus.OPEN }
        for (c in openClars) {
            results.add(CheckResult(
                id = nextId(), severity = CheckSeverity.WARNING,
                category = "open-clarification",
                message = "Clarification '${c.question}' is still open.",
                relatedArtifact = c.id,
                suggestedFix = "Answer this clarification to remove ambiguity."
            ))
        }

        // 5. Tasks without description
        for (task in tasks) {
            if (task.description.isBlank()) {
                results.add(CheckResult(
                    id = nextId(), severity = CheckSeverity.INFO,
                    category = "missing-description",
                    message = "Task '${task.title}' has no description.",
                    relatedArtifact = task.id,
                    suggestedFix = "Add a description to clarify what this task involves."
                ))
            }
        }

        val summary = CheckSummary(
            errors = results.count { it.severity == CheckSeverity.ERROR },
            warnings = results.count { it.severity == CheckSeverity.WARNING },
            infos = results.count { it.severity == CheckSeverity.INFO },
            passed = results.none { it.severity == CheckSeverity.ERROR }
        )

        return CheckReport(
            projectId = projectId,
            results = results.sortedBy { it.severity.ordinal },
            checkedAt = Instant.now().toString(),
            summary = summary
        )
    }
}
