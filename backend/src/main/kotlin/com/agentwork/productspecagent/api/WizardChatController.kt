package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardStepCompleteRequest
import com.agentwork.productspecagent.domain.WizardStepCompleteResponse
import com.agentwork.productspecagent.service.CompleteWizardStep
import com.agentwork.productspecagent.service.WizardStepCompletion
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects")
class WizardChatController(
    private val wizardStepCompletion: WizardStepCompletion
) {

    @PostMapping("/{id}/agent/wizard-step-complete")
    fun wizardStepComplete(
        @PathVariable id: String,
        @RequestBody request: WizardStepCompleteRequest
    ): ResponseEntity<WizardStepCompleteResponse> {
        if (request.step.isBlank()) {
            return ResponseEntity.badRequest().build()
        }

        val step = runCatching { FlowStepType.valueOf(request.step) }
            .getOrElse { return ResponseEntity.badRequest().build() }

        val result = runBlocking {
            wizardStepCompletion.complete(
                CompleteWizardStep(
                    projectId = id,
                    step = step,
                    fields = request.fields,
                    locale = request.locale,
                )
            )
        }

        return ResponseEntity.ok(
            WizardStepCompleteResponse(
                message = result.message,
                nextStep = result.nextStep?.name,
                exportTriggered = result.exportTriggered,
                decisionId = result.decisionId,
                clarificationId = result.clarificationId,
                progression = result.progression,
                action = result.action,
                artifacts = result.artifacts,
            )
        )
    }
}
