package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.storage.ObjectStore
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
    @Autowired lateinit var objectStore: ObjectStore

    private val createdProjectIds = mutableListOf<String>()

    @org.junit.jupiter.api.AfterEach
    fun cleanupProjects() {
        for (pid in createdProjectIds) {
            objectStore.deletePrefix("projects/$pid/")
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
    fun `GET files includes files inside subdirectories`() {
        val pid = createProject()
        // put a known file under docs/uploads/
        objectStore.put("projects/$pid/docs/uploads/notes.md", "# notes".toByteArray())

        mockMvc.perform(get("/api/v1/projects/$pid/files"))
            .andExpect(status().isOk())
            // docs.children must contain an uploads folder, which must contain notes.md
            .andExpect(jsonPath("$[?(@.name == 'docs')].children[?(@.name == 'uploads')].children[?(@.name == 'notes.md')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'docs')].children[?(@.name == 'uploads')].children[?(@.name == 'notes.md')].isDirectory").value(false))
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
        // content is irrelevant — detection is by extension
        objectStore.put("projects/$pid/docs/uploads/doc.pdf", byteArrayOf(0x25, 0x50, 0x44, 0x46))

        mockMvc.perform(get("/api/v1/projects/$pid/files/docs/uploads/doc.pdf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.binary").value(true))
            .andExpect(jsonPath("$.content").value(""))
            .andExpect(jsonPath("$.name").value("doc.pdf"))
    }
}
