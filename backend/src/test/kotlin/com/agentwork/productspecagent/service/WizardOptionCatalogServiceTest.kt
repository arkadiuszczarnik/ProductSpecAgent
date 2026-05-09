package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardOption
import com.agentwork.productspecagent.domain.WizardOptionCatalog
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.WizardOptionCatalogStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WizardOptionCatalogServiceTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC)

    private fun service(objectStore: InMemoryObjectStore = InMemoryObjectStore()): WizardOptionCatalogService =
        WizardOptionCatalogService(WizardOptionCatalogStorage(objectStore), fixedClock)

    @Test
    fun `returns default catalog when no catalog is persisted`() {
        val catalog = service().getCatalog()
        val saas = catalog.categories.first()

        assertEquals("SaaS", saas.id)
        assertTrue(saas.visibleSteps.contains(FlowStepType.ARCHITECTURE))
        assertTrue(saas.visibleSteps.contains(FlowStepType.BACKEND))
        assertTrue(saas.visibleSteps.contains(FlowStepType.FRONTEND))
        assertTrue(
            saas.fields
                .single { it.step == FlowStepType.BACKEND && it.key == "framework" }
                .options
                .any { it.label == "Kotlin+Spring" }
        )
    }

    @Test
    fun `persists and reloads updated catalog`() {
        val objectStore = InMemoryObjectStore()
        val original = service(objectStore).getCatalog()
        val updated = original.withOption("SaaS", FlowStepType.BACKEND, "framework", WizardOption("elixir-phoenix", "Elixir + Phoenix"))

        service(objectStore).saveCatalog(updated)

        val reloaded = service(objectStore).getCatalog()
        assertTrue(reloaded.hasOption("SaaS", FlowStepType.BACKEND, "framework", "Elixir + Phoenix"))
        assertEquals("2026-05-09T12:00:00Z", reloaded.updatedAt)
    }

    @Test
    fun `reset replaces persisted catalog with defaults`() {
        val objectStore = InMemoryObjectStore()
        val updated = service(objectStore)
            .getCatalog()
            .withOption("SaaS", FlowStepType.BACKEND, "framework", WizardOption("elixir-phoenix", "Elixir + Phoenix"))
        service(objectStore).saveCatalog(updated)

        val reset = service(objectStore).resetCatalog()

        assertFalse(reset.hasOption("SaaS", FlowStepType.BACKEND, "framework", "Elixir + Phoenix"))
        assertTrue(reset.hasOption("SaaS", FlowStepType.BACKEND, "framework", "Kotlin+Spring"))
        assertFalse(service(objectStore).getCatalog().hasOption("SaaS", FlowStepType.BACKEND, "framework", "Elixir + Phoenix"))
    }

    @Test
    fun `rejects duplicate category ids`() {
        val catalog = service().getCatalog()
        val invalid = catalog.copy(categories = catalog.categories + catalog.categories.first().copy(label = "Duplicate SaaS"))

        assertFailsWith<WizardOptionCatalogValidationException> {
            service().saveCatalog(invalid)
        }
    }

    @Test
    fun `rejects duplicate field keys inside a category step`() {
        val catalog = service().getCatalog()
        val invalid = catalog.copy(
            categories = catalog.categories.map { category ->
                if (category.id != "SaaS") {
                    category
                } else {
                    val duplicateField = category.fields.single { it.step == FlowStepType.BACKEND && it.key == "framework" }
                    category.copy(fields = category.fields + duplicateField.copy(label = "Duplicate framework"))
                }
            }
        )

        assertFailsWith<WizardOptionCatalogValidationException> {
            service().saveCatalog(invalid)
        }
    }

    @Test
    fun `rejects blank option labels`() {
        val catalog = service().getCatalog()
        val invalid = catalog.withOption("SaaS", FlowStepType.BACKEND, "framework", WizardOption("blank-label", "   "))

        assertFailsWith<WizardOptionCatalogValidationException> {
            service().saveCatalog(invalid)
        }
    }
}

private fun WizardOptionCatalog.withOption(
    categoryId: String,
    step: FlowStepType,
    fieldKey: String,
    option: WizardOption,
): WizardOptionCatalog =
    copy(
        categories = categories.map { category ->
            if (category.id != categoryId) {
                category
            } else {
                category.copy(
                    fields = category.fields.map { field ->
                        if (field.step == step && field.key == fieldKey) {
                            field.copy(options = field.options + option)
                        } else {
                            field
                        }
                    }
                )
            }
        }
    )

private fun WizardOptionCatalog.hasOption(
    categoryId: String,
    step: FlowStepType,
    fieldKey: String,
    optionLabel: String,
): Boolean =
    categories
        .single { it.id == categoryId }
        .fields
        .single { it.step == step && it.key == fieldKey }
        .options
        .any { it.label == optionLabel }
