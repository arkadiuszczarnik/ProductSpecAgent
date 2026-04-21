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
class WizardControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Wizard Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `GET wizard returns empty wizard data`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/wizard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(pid))
            .andExpect(jsonPath("$.steps").isMap())
    }

    @Test
    fun `PUT wizard step saves data`() {
        val pid = createProject()
        mockMvc.perform(
            put("/api/v1/projects/$pid/wizard/IDEA")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fields":{"productName":"My App","vision":"A great app"},"completedAt":"2026-03-30T00:00:00Z"}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps.IDEA.fields.productName").value("My App"))
            .andExpect(jsonPath("$.steps.IDEA.completedAt").value("2026-03-30T00:00:00Z"))
    }

    @Test
    fun `PUT wizard saves full wizard data`() {
        val pid = createProject()
        mockMvc.perform(
            put("/api/v1/projects/$pid/wizard")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"projectId":"$pid","steps":{"IDEA":{"fields":{"name":"Test"},"completedAt":null}}}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps.IDEA.fields.name").value("Test"))
    }

    @Test
    fun `GET wizard after save returns saved data`() {
        val pid = createProject()
        mockMvc.perform(
            put("/api/v1/projects/$pid/wizard/PROBLEM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fields":{"problem":"Users lose track"},"completedAt":null}""")
        ).andExpect(status().isOk())

        mockMvc.perform(get("/api/v1/projects/$pid/wizard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps.PROBLEM.fields.problem").value("Users lose track"))
    }
}
