package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST creates project and returns 201`() {
        mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test Project"}""")
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.project.name").value("Test Project"))
            .andExpect(jsonPath("$.project.status").value("DRAFT"))
            .andExpect(jsonPath("$.flowState.currentStep").value("IDEA"))
            .andExpect(jsonPath("$.flowState.steps.length()").value(8))
    }

    @Test
    fun `GET list returns projects`() {
        // Create a project first
        mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "List Test"}""")
        )

        mockMvc.perform(get("/api/v1/projects"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
    }

    @Test
    fun `GET non-existent project returns 404 with error body`() {
        mockMvc.perform(get("/api/v1/projects/non-existent-id"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `full CRUD lifecycle works`() {
        // Create
        val createResult = mockMvc.perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "CRUD Test"}""")
        )
            .andExpect(status().isCreated())
            .andReturn()

        // Extract ID from response
        val body = createResult.response.contentAsString
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        val projectId = idRegex.find(body)!!.groupValues[1]

        // Get by ID
        mockMvc.perform(get("/api/v1/projects/$projectId"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.project.name").value("CRUD Test"))

        // Get flow state
        mockMvc.perform(get("/api/v1/projects/$projectId/flow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value("IDEA"))
            .andExpect(jsonPath("$.steps.length()").value(8))

        // Delete
        mockMvc.perform(delete("/api/v1/projects/$projectId"))
            .andExpect(status().isNoContent())

        // Verify deleted
        mockMvc.perform(get("/api/v1/projects/$projectId"))
            .andExpect(status().isNotFound())
    }
}
