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
class FileControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"File Test","idea":"An idea"}""")
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `GET files returns file tree`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/files"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
    }

    @Test
    fun `GET files includes spec directory`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/files"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'spec')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'spec')].isDirectory").value(true))
    }

    @Test
    fun `GET file content returns file with language`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/files/project.json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("project.json"))
            .andExpect(jsonPath("$.language").value("json"))
            .andExpect(jsonPath("$.content").isNotEmpty())
            .andExpect(jsonPath("$.lineCount").isNumber())
    }

    @Test
    fun `GET file content for spec file`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/files/spec/idea.md"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.language").value("markdown"))
            .andExpect(jsonPath("$.content").isNotEmpty())
    }

    @Test
    fun `GET non-existent file returns 404`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/files/nonexistent.txt"))
            .andExpect(status().isNotFound())
    }
}
