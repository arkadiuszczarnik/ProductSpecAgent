package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DecisionAgent
import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.DecisionStorage
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DecisionService(
    private val storage: DecisionStorage,
    private val agent: DecisionAgent
) {
    suspend fun createDecision(projectId: String, title: String, stepType: FlowStepType): Decision {
        val decision = agent.generateDecision(projectId, title, stepType)
        storage.saveDecision(decision)
        return decision
    }

    fun getDecision(projectId: String, decisionId: String): Decision {
        return storage.loadDecision(projectId, decisionId)
    }

    fun listDecisions(projectId: String): List<Decision> {
        return storage.listDecisions(projectId)
    }

    fun resolveDecision(projectId: String, decisionId: String, chosenOptionId: String, rationale: String): Decision {
        val decision = storage.loadDecision(projectId, decisionId)
        if (decision.status == DecisionStatus.RESOLVED) {
            throw IllegalStateException("Decision already resolved")
        }
        if (decision.options.none { it.id == chosenOptionId }) {
            throw IllegalArgumentException("Invalid option: $chosenOptionId")
        }
        val resolved = decision.copy(
            status = DecisionStatus.RESOLVED,
            chosenOptionId = chosenOptionId,
            rationale = rationale,
            resolvedAt = Instant.now().toString()
        )
        storage.saveDecision(resolved)
        return resolved
    }

    fun markApplied(projectId: String, decisionId: String, appliedFields: List<String>): Decision {
        val decision = storage.loadDecision(projectId, decisionId)
        val applied = decision.copy(
            appliedAt = Instant.now().toString(),
            appliedFields = appliedFields
        )
        storage.saveDecision(applied)
        return applied
    }
}
