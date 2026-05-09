package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.WizardOptionCatalog
import com.agentwork.productspecagent.service.WizardOptionCatalogService
import com.agentwork.productspecagent.service.WizardOptionCatalogValidationException
import kotlinx.serialization.Serializable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Serializable
data class WizardOptionCatalogErrorResponse(val message: String)

@RestController
class WizardOptionCatalogController(
    private val service: WizardOptionCatalogService,
) {

    @GetMapping("/api/v1/wizard-options")
    fun getPublicCatalog(): WizardOptionCatalog =
        service.getCatalog()

    @GetMapping("/api/v1/admin/wizard-options")
    fun getAdminCatalog(): WizardOptionCatalog =
        service.getCatalog()

    @PutMapping("/api/v1/admin/wizard-options")
    fun saveAdminCatalog(@RequestBody catalog: WizardOptionCatalog): WizardOptionCatalog =
        service.saveCatalog(catalog)

    @PostMapping("/api/v1/admin/wizard-options/reset")
    fun resetAdminCatalog(): WizardOptionCatalog =
        service.resetCatalog()

    @ExceptionHandler(WizardOptionCatalogValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: WizardOptionCatalogValidationException): WizardOptionCatalogErrorResponse =
        WizardOptionCatalogErrorResponse(ex.message ?: "Invalid wizard option catalog")
}
