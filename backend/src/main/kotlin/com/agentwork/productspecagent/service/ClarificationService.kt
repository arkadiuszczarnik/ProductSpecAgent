package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.storage.ClarificationStorage
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ClarificationService(private val storage: ClarificationStorage) {

    fun createClarification(projectId: String, question: String, reason: String, stepType: FlowStepType): Clarification {
        val clarification = Clarification(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            stepType = stepType,
            question = question,
            reason = reason,
            createdAt = Instant.now().toString()
        )
        storage.saveClarification(clarification)
        return clarification
    }

    fun answerClarification(projectId: String, clarificationId: String, answer: String): Clarification {
        val clarification = storage.loadClarification(projectId, clarificationId)
        if (clarification.status == ClarificationStatus.ANSWERED) {
            throw IllegalStateException("Clarification already answered")
        }
        val answered = clarification.copy(
            status = ClarificationStatus.ANSWERED,
            answer = answer,
            answeredAt = Instant.now().toString()
        )
        storage.saveClarification(answered)
        return answered
    }

    fun markApplied(projectId: String, clarificationId: String, appliedFields: List<String>): Clarification {
        val clarification = storage.loadClarification(projectId, clarificationId)
        val applied = clarification.copy(
            appliedAt = Instant.now().toString(),
            appliedFields = appliedFields
        )
        storage.saveClarification(applied)
        return applied
    }

    fun getClarification(projectId: String, clarificationId: String): Clarification {
        return storage.loadClarification(projectId, clarificationId)
    }

    fun listClarifications(projectId: String): List<Clarification> {
        return storage.listClarifications(projectId)
    }
}
