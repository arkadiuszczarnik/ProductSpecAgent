package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardOption
import com.agentwork.productspecagent.domain.WizardOptionCatalog
import com.agentwork.productspecagent.service.WizardOptionCatalogService
import com.agentwork.productspecagent.storage.ObjectStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class WizardOptionCatalogControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectStore: ObjectStore
    @Autowired lateinit var service: WizardOptionCatalogService

    private val json = Json { encodeDefaults = true }

    @BeforeEach
    fun cleanCatalog() {
        objectStore.deletePrefix("config/wizard-options/")
    }

    @Test
    @WithAnonymousUser
    fun `GET public wizard-options returns catalog`() {
        mockMvc.perform(get("/api/v1/wizard-options"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories[0].id").value("SaaS"))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET admin wizard-options returns catalog`() {
        mockMvc.perform(get("/api/v1/admin/wizard-options"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories[0].id").exists())
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `PUT admin wizard-options persists catalog`() {
        val updated = service.getCatalog()
            .withOption("SaaS", FlowStepType.BACKEND, "framework", WizardOption("elixir-phoenix", "Elixir + Phoenix"))

        mockMvc.perform(
            put("/api/v1/admin/wizard-options")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.encodeToString(updated))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories[0].fields[?(@.key == 'framework')].options[?(@.id == 'elixir-phoenix')]").exists())

        mockMvc.perform(get("/api/v1/wizard-options"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Elixir + Phoenix")))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `POST admin wizard-options reset restores defaults`() {
        val updated = service.getCatalog()
            .withOption("SaaS", FlowStepType.BACKEND, "framework", WizardOption("elixir-phoenix", "Elixir + Phoenix"))
        service.saveCatalog(updated)

        mockMvc.perform(post("/api/v1/admin/wizard-options/reset"))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("Elixir + Phoenix"))))
            .andExpect(content().string(containsString("Kotlin+Spring")))

        mockMvc.perform(get("/api/v1/wizard-options"))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("Elixir + Phoenix"))))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `PUT admin wizard-options returns 400 for invalid catalog`() {
        val catalog = service.getCatalog()
        val invalid = catalog.copy(categories = catalog.categories + catalog.categories.first().copy(label = "Duplicate SaaS"))

        mockMvc.perform(
            put("/api/v1/admin/wizard-options")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.encodeToString(invalid))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("Duplicate category id")))
    }

    @WithAnonymousUser
    @Test
    fun `PUT admin wizard-options rejects unauthenticated requests`() {
        mockMvc.perform(
            put("/api/v1/admin/wizard-options")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"version":1,"categories":[],"updatedAt":"2026-05-09T00:00:00Z"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @WithMockUser
    @Test
    fun `PUT admin wizard-options rejects non-admin users`() {
        mockMvc.perform(
            put("/api/v1/admin/wizard-options")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"version":1,"categories":[],"updatedAt":"2026-05-09T00:00:00Z"}""")
        )
            .andExpect(status().isForbidden)
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
