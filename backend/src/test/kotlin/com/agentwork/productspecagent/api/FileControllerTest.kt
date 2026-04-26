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
                .content("""{"name":"File Test"}""")
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
    fun `GET files includes docs directory`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/files"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'docs')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'docs')].isDirectory").value(true))
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
        mockMvc.perform(get("/api/v1/projects/$pid/files/docs/features/00-feature-set-overview.md"))
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

    @Test
    fun `GET binary file returns binary=true with empty content`() {
        val pid = createProject()
        // Manually place a fake PDF under data/projects/{pid}/uploads/
        val dataPath = java.nio.file.Paths.get("build/test-data/projects/$pid/uploads")
        java.nio.file.Files.createDirectories(dataPath)
        java.nio.file.Files.write(dataPath.resolve("doc.pdf"), byteArrayOf(0x25, 0x50, 0x44, 0x46))  // %PDF magic bytes

        mockMvc.perform(get("/api/v1/projects/$pid/files/uploads/doc.pdf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.binary").value(true))
            .andExpect(jsonPath("$.content").value(""))
            .andExpect(jsonPath("$.name").value("doc.pdf"))
    }
}
