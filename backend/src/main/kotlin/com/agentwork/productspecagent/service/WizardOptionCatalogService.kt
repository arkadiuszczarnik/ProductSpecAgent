package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardOptionCatalog
import com.agentwork.productspecagent.storage.WizardOptionCatalogStorage
import org.springframework.stereotype.Service
import java.time.Clock

class WizardOptionCatalogValidationException(message: String) : RuntimeException(message)

@Service
class WizardOptionCatalogService(
    private val storage: WizardOptionCatalogStorage,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun getCatalog(): WizardOptionCatalog =
        storage.load()?.also { validate(it) } ?: WizardOptionCatalogDefaults.create()

    fun saveCatalog(catalog: WizardOptionCatalog): WizardOptionCatalog {
        validate(catalog)
        return storage.save(catalog.copy(updatedAt = clock.instant().toString()))
    }

    fun resetCatalog(): WizardOptionCatalog =
        storage.save(WizardOptionCatalogDefaults.create().copy(updatedAt = clock.instant().toString()))

    private fun validate(catalog: WizardOptionCatalog) {
        if (catalog.version < 1) {
            throw WizardOptionCatalogValidationException("Catalog version must be at least 1")
        }

        val categoryIds = mutableSetOf<String>()
        catalog.categories.forEach { category ->
            if (category.id.isBlank()) {
                throw WizardOptionCatalogValidationException("Category id must not be blank")
            }
            if (category.label.isBlank()) {
                throw WizardOptionCatalogValidationException("Category '${category.id}' label must not be blank")
            }
            if (!categoryIds.add(category.id)) {
                throw WizardOptionCatalogValidationException("Duplicate category id '${category.id}'")
            }

            val missingBaseSteps = REQUIRED_VISIBLE_STEPS - category.visibleSteps.toSet()
            if (missingBaseSteps.isNotEmpty()) {
                throw WizardOptionCatalogValidationException(
                    "Category '${category.id}' visibleSteps must include ${missingBaseSteps.joinToString(", ")}"
                )
            }

            val fieldIdentities = mutableSetOf<Pair<FlowStepType, String>>()
            category.fields.forEach { field ->
                if (field.key.isBlank()) {
                    throw WizardOptionCatalogValidationException("Category '${category.id}' field key must not be blank")
                }
                if (field.label.isBlank()) {
                    throw WizardOptionCatalogValidationException("Category '${category.id}' field '${field.key}' label must not be blank")
                }
                val fieldIdentity = field.step to field.key
                if (!fieldIdentities.add(fieldIdentity)) {
                    throw WizardOptionCatalogValidationException(
                        "Duplicate field '${field.step}/${field.key}' in category '${category.id}'"
                    )
                }

                val optionIds = mutableSetOf<String>()
                field.options.forEach { option ->
                    if (option.id.isBlank()) {
                        throw WizardOptionCatalogValidationException(
                            "Option id must not be blank in category '${category.id}' field '${field.step}/${field.key}'"
                        )
                    }
                    if (option.label.isBlank()) {
                        throw WizardOptionCatalogValidationException(
                            "Option '${option.id}' label must not be blank in category '${category.id}' field '${field.step}/${field.key}'"
                        )
                    }
                    if (!optionIds.add(option.id)) {
                        throw WizardOptionCatalogValidationException(
                            "Duplicate option id '${option.id}' in category '${category.id}' field '${field.step}/${field.key}'"
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val REQUIRED_VISIBLE_STEPS = setOf(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
        )
    }
}
