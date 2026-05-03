package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.AgentModelInfo
import com.agentwork.productspecagent.domain.UpdateAgentModelRequest
import com.agentwork.productspecagent.service.AgentModelService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/agent-models")
class AgentModelController(private val service: AgentModelService) {

    @GetMapping
    fun list(): List<AgentModelInfo> = service.listAll()

    @PutMapping("/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(@PathVariable agentId: String, @RequestBody body: UpdateAgentModelRequest) {
        service.setTier(agentId, body.tier)
    }

    @DeleteMapping("/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@PathVariable agentId: String) {
        service.reset(agentId)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleNotFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()
}
