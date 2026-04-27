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

    private val createdProjectIds = mutableListOf<String>()

    @org.junit.jupiter.api.AfterEach
    fun cleanupProjects() {
        val root = java.nio.file.Paths.get("build/test-data/projects")
        for (pid in createdProjectIds) {
            val dir = root.resolve(pid)
            if (java.nio.file.Files.exists(dir)) {
                java.nio.file.Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(java.nio.file.Files::delete)
            }
        }
        createdProjectIds.clear()
    }

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"File Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        val pid = """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
        createdProjectIds.add(pid)
        return pid
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
        val dataPath = java.nio.file.Paths.get("build/test-data/projects/$pid/docs/uploads")
        java.nio.file.Files.createDirectories(dataPath)
        val testFile = dataPath.resolve("doc.pdf")
        // content is irrelevant — detection is by extension
        java.nio.file.Files.write(testFile, byteArrayOf(0x25, 0x50, 0x44, 0x46))

        mockMvc.perform(get("/api/v1/projects/$pid/files/docs/uploads/doc.pdf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.binary").value(true))
            .andExpect(jsonPath("$.content").value(""))
            .andExpect(jsonPath("$.name").value("doc.pdf"))
    }
}
