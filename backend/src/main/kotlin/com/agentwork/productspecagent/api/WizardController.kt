package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardProgressionView
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.WizardProgression
import com.agentwork.productspecagent.service.WizardService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}/wizard")
class WizardController(
    private val wizardService: WizardService,
    private val wizardProgression: WizardProgression,
) {

    @GetMapping
    fun getWizardData(@PathVariable projectId: String): WizardData {
        return wizardService.getWizardData(projectId)
    }

    @GetMapping("/progression")
    fun progression(@PathVariable projectId: String): WizardProgressionView {
        return wizardProgression.snapshot(projectId)
    }

    @PutMapping
    fun saveWizardData(
        @PathVariable projectId: String,
        @RequestBody data: WizardData
    ): WizardData {
        return wizardService.saveWizardData(projectId, data)
    }

    @PutMapping("/{step}")
    fun saveStepData(
        @PathVariable projectId: String,
        @PathVariable step: String,
        @RequestBody stepData: WizardStepData
    ): WizardData {
        return wizardService.saveStepData(projectId, step, stepData)
    }
}
