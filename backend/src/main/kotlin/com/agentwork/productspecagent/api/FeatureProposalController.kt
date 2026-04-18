package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.FeatureProposalAgent
import com.agentwork.productspecagent.agent.ProposalParseException
import com.agentwork.productspecagent.domain.WizardFeatureGraph
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/projects/{projectId}/features")
class FeatureProposalController(private val agent: FeatureProposalAgent) {

    @PostMapping("/propose")
    fun propose(@PathVariable projectId: String): ResponseEntity<Any> = runBlocking {
        try {
            val graph: WizardFeatureGraph = agent.proposeFeatures(projectId)
            ResponseEntity.ok<Any>(graph)
        } catch (e: ProposalParseException) {
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("error" to (e.message ?: "Parsing failed")))
        }
    }
}
