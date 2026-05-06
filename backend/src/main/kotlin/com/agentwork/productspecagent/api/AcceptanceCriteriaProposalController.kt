package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.AcceptanceCriteriaProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/projects/{projectId}/features/{featureId}/acceptance-criteria")
class AcceptanceCriteriaProposalController(
    private val agent: AcceptanceCriteriaProposalAgent,
) {
    @PostMapping("/propose")
    fun propose(
        @PathVariable projectId: String,
        @PathVariable featureId: String,
    ): ResponseEntity<Any> = runBlocking {
        try {
            ResponseEntity.ok<Any>(agent.propose(projectId, featureId))
        } catch (e: ProposalParseException) {
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("error" to (e.message ?: "Parsing failed")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to (e.message ?: "Feature not found")))
        }
    }
}
