package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.PromptDetail
import com.agentwork.productspecagent.domain.PromptListItem
import com.agentwork.productspecagent.domain.PromptValidationError
import com.agentwork.productspecagent.domain.UpdatePromptRequest
import com.agentwork.productspecagent.service.PromptNotFoundException
import com.agentwork.productspecagent.service.PromptService
import com.agentwork.productspecagent.service.PromptValidationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/prompts")
class PromptController(private val service: PromptService) {

    @GetMapping
    fun list(): List<PromptListItem> = service.list()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String): PromptDetail {
        val item = service.list().find { it.id == id }
            ?: throw PromptNotFoundException(id)
        return PromptDetail(
            id = item.id,
            title = item.title,
            description = item.description,
            agent = item.agent,
            content = service.get(id),
            isOverridden = item.isOverridden,
        )
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody body: UpdatePromptRequest) {
        service.put(id, body.content)
    }

    @DeleteMapping("/{id}")
    fun reset(@PathVariable id: String) {
        service.reset(id)
    }

    @ExceptionHandler(PromptValidationException::class)
    fun handleValidation(ex: PromptValidationException): ResponseEntity<PromptValidationError> =
        ResponseEntity.badRequest().body(PromptValidationError(ex.errors))

    @ExceptionHandler(PromptNotFoundException::class)
    fun handleNotFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()
}
