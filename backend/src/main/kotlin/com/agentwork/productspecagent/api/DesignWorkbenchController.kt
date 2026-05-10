package com.agentwork.productspecagent.api

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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
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

    data class CompleteResponse(val message: String, val nextStep: String?)

    @GetMapping("/workbench")
    fun get(@PathVariable projectId: String): DesignWorkbench =
        service.get(projectId)

    @PutMapping("/input", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun saveInput(
        @PathVariable projectId: String,
        @RequestParam("description", required = false) description: String?,
        @RequestParam("file", required = false) file: MultipartFile?,
    ): DesignWorkbench {
        validateDesignAccess(projectId)
        return mapInvalidWorkbench {
            service.saveInput(projectId, description, file?.originalFilename, file?.bytes, file?.contentType)
        }
    }

    @PostMapping("/generate")
    fun generate(@PathVariable projectId: String): DesignWorkbench {
        validateDesignAccess(projectId)
        return mapInvalidWorkbench { service.generate(projectId) }
    }

    @PostMapping("/image/analyze")
    fun analyzeImage(@PathVariable projectId: String): DesignWorkbench {
        validateDesignAccess(projectId)
        return mapInvalidWorkbench { service.analyzeImage(projectId) }
    }

    @GetMapping("/preview")
    fun preview(
        @PathVariable projectId: String,
    ): ResponseEntity<ByteArray> {
        if (service.get(projectId).currentDesign == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Current design not found.")
        }
        val bytes = try {
            service.readPreview(projectId)
        } catch (e: InvalidDesignWorkbenchException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message ?: "Current design not found.")
        }
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
        val design = workbench.currentDesign ?: throw badRequest("Generate a design before completing the DESIGN step.")
        return buildString {
            appendLine("# Design")
            appendLine()
            appendLine("Generated design:")
            appendLine()
            appendLine("## ${design.title}")
            appendLine()
            if (!workbench.description.isNullOrBlank()) {
                appendLine("- Description: ${workbench.description}")
            }
            workbench.imageInput?.let { image ->
                appendLine("- Reference image: ${image.originalName}")
            }
            workbench.analysis?.let { analysis ->
                appendLine("- Summary: ${analysis.summary}")
                appendLine("- Visual direction: ${analysis.visualDirection}")
            }
            if (design.rationale.isNotBlank()) {
                appendLine("- Rationale: ${design.rationale}")
            }
            appendLine("- Preview source: ${design.htmlPath}")
        }.trim()
    }

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
