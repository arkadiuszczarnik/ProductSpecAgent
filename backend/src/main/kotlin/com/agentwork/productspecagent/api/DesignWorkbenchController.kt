package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.DesignVariant
import com.agentwork.productspecagent.domain.DesignWorkbench
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.ProductCategory
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.DesignWorkbenchService
import com.agentwork.productspecagent.service.InvalidDesignPreviewException
import com.agentwork.productspecagent.service.InvalidDesignWorkbenchException
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardProgression
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.service.WizardStepNotCurrentException
import com.agentwork.productspecagent.service.WizardStepNotVisibleException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/api/v1/projects/{projectId}/design")
class DesignWorkbenchController(
    private val service: DesignWorkbenchService,
    private val wizardService: WizardService,
    private val projectService: ProjectService,
    private val wizardProgression: WizardProgression,
    @Value("\${app.frontend-origin:http://localhost:3001}") private val frontendOrigin: String,
) {

    data class TextInputRequest(val text: String = "")
    data class VariantRequest(val prompt: String? = null)
    data class ActiveVariantRequest(val variantId: String = "")
    data class CompleteResponse(val message: String, val nextStep: String?)

    @GetMapping("/workbench")
    fun get(@PathVariable projectId: String): DesignWorkbench =
        service.get(projectId)

    @PostMapping("/inputs/text")
    fun addTextInput(
        @PathVariable projectId: String,
        @RequestBody body: TextInputRequest,
    ): DesignWorkbench {
        validateDesignAccess(projectId)
        if (body.text.isBlank()) {
            throw badRequest("Design input text must not be blank.")
        }
        return mapInvalidWorkbench { service.addTextInput(projectId, body.text) }
    }

    @PostMapping("/analyze")
    fun analyzeInputs(@PathVariable projectId: String): DesignWorkbench {
        validateDesignAccess(projectId)
        return mapInvalidWorkbench { service.analyzeInputs(projectId) }
    }

    @PostMapping("/screens/propose")
    fun proposeScreens(@PathVariable projectId: String): DesignWorkbench {
        validateDesignAccess(projectId)
        return mapInvalidWorkbench { service.proposeScreens(projectId) }
    }

    @PostMapping("/screens/{screenId}/variants")
    fun generateVariant(
        @PathVariable projectId: String,
        @PathVariable screenId: String,
        @RequestBody body: VariantRequest,
    ): DesignWorkbench {
        validateDesignAccess(projectId)
        return mapInvalidWorkbench { service.generateVariant(projectId, screenId, body.prompt) }
    }

    @PatchMapping("/screens/{screenId}/active-variant")
    fun setActiveVariant(
        @PathVariable projectId: String,
        @PathVariable screenId: String,
        @RequestBody body: ActiveVariantRequest,
    ): DesignWorkbench {
        validateDesignAccess(projectId)
        if (body.variantId.isBlank()) {
            throw badRequest("Design variant ID must not be blank.")
        }
        return mapInvalidWorkbench { service.setActiveVariant(projectId, screenId, body.variantId) }
    }

    @GetMapping("/preview/{variantId}")
    fun preview(
        @PathVariable projectId: String,
        @PathVariable variantId: String,
    ): ResponseEntity<ByteArray> {
        val variant = service.get(projectId).findVariant(variantId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Design variant not found: $variantId")
        val bytes = mapInvalidWorkbench { service.readVariant(projectId, variant.htmlPath) }
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType("text/html; charset=utf-8")
        headers.set("X-Content-Type-Options", "nosniff")
        headers.cacheControl = "no-store"
        headers.set(
            "Content-Security-Policy",
            "default-src 'none'; img-src data:; style-src 'unsafe-inline'; " +
                "script-src 'unsafe-inline'; connect-src 'none'; frame-src 'none'; " +
                "object-src 'none'; base-uri 'none'; form-action 'none'; " +
                "frame-ancestors 'self' $frontendOrigin",
        )
        return ResponseEntity(bytes, headers, HttpStatus.OK)
    }

    @PostMapping("/complete")
    fun complete(@PathVariable projectId: String): CompleteResponse {
        validateDesignAccess(projectId)
        val workbench = mapInvalidWorkbench { service.complete(projectId) }
        val summary = renderDesignSummary(workbench)
        val fields: Map<String, JsonElement> = mapOf("summary" to JsonPrimitive(summary))

        wizardService.saveStepData(
            projectId,
            FlowStepType.DESIGN.name,
            WizardStepData(fields = fields, completedAt = Instant.now().toString()),
        )
        projectService.saveSpecFile(projectId, "design.md", summary)

        val nextStep = projectService.advanceStep(projectId, FlowStepType.DESIGN)
        return CompleteResponse(
            message = "Design workbench completed. Spec updated.",
            nextStep = nextStep?.name,
        )
    }

    private fun validateDesignAccess(projectId: String) {
        val beforeAdvance = wizardProgression.snapshot(projectId)
        if (beforeAdvance.steps.none { it.step == FlowStepType.DESIGN.name }) {
            throw WizardStepNotVisibleException(
                FlowStepType.DESIGN,
                ProductCategory.fromWire(beforeAdvance.category),
            )
        }
        if (beforeAdvance.currentStep != FlowStepType.DESIGN.name) {
            throw WizardStepNotCurrentException(FlowStepType.DESIGN, beforeAdvance.currentStep)
        }
    }

    private fun renderDesignSummary(workbench: DesignWorkbench): String {
        val activeScreens = workbench.screens.mapNotNull { screen ->
            val variant = screen.variants.firstOrNull { it.id == screen.activeVariantId }
                ?: return@mapNotNull null
            screen to variant
        }
        return buildString {
            appendLine("# Design")
            appendLine()
            appendLine("Selected design variants:")
            activeScreens.forEach { (screen, variant) ->
                appendLine()
                appendLine("## ${screen.name}")
                appendLine()
                appendLine("- Purpose: ${screen.purpose}")
                appendLine("- Variant: ${variant.title}")
                if (variant.rationale.isNotBlank()) {
                    appendLine("- Rationale: ${variant.rationale}")
                }
                appendLine("- Preview source: ${variant.htmlPath}")
            }
        }.trim()
    }

    private fun DesignWorkbench.findVariant(variantId: String): DesignVariant? =
        screens.asSequence()
            .flatMap { it.variants.asSequence() }
            .firstOrNull { it.id == variantId }

    private fun <T> mapInvalidWorkbench(block: () -> T): T =
        try {
            block()
        } catch (e: InvalidDesignWorkbenchException) {
            throw badRequest(e.message ?: "Invalid design workbench.")
        } catch (e: InvalidDesignPreviewException) {
            throw badRequest(e.message ?: "Invalid design preview.")
        } catch (e: IllegalArgumentException) {
            throw badRequest(e.message ?: "Invalid design workbench.")
        }

    private fun badRequest(message: String): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
