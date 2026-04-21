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
class CheckControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Check Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `POST checks returns report with summary`() {
        val pid = createProject()
        mockMvc.perform(post("/api/v1/projects/$pid/checks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(pid))
            .andExpect(jsonPath("$.summary").exists())
            .andExpect(jsonPath("$.summary.errors").isNumber())
            .andExpect(jsonPath("$.summary.warnings").isNumber())
            .andExpect(jsonPath("$.results").isArray())
    }

    @Test
    fun `GET results returns same report`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/checks/results"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(pid))
            .andExpect(jsonPath("$.summary").exists())
    }
}
