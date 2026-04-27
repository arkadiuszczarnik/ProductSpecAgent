package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ExportControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var uploadStorage: com.agentwork.productspecagent.storage.UploadStorage

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
                .content("""{"name":"Export Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        val pid = """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
        createdProjectIds.add(pid)
        return pid
    }

    @Test
    fun `POST export returns ZIP with README and SPEC`() {
        val pid = createProject()

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"includeDecisions":true,"includeClarifications":true,"includeTasks":true}""")
        )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".zip")))
            .andReturn()

        val zipBytes = result.response.contentAsByteArray
        assertTrue(zipBytes.isNotEmpty())

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.any { it.endsWith("README.md") }, "ZIP should contain README.md, got: $entries")
        assertTrue(entries.any { it.endsWith("SPEC.md") }, "ZIP should contain SPEC.md, got: $entries")
        assertTrue(entries.any { it.endsWith(".gitignore") }, "ZIP should contain .gitignore, got: $entries")
    }

    @Test
    fun `POST export without body uses defaults`() {
        val pid = createProject()

        mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk())
    }

    @Test
    fun `POST export ZIP includes docs scaffold directory`() {
        val pid = createProject()

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/export").contentType(MediaType.APPLICATION_JSON)
                .content("""{"includeDecisions":true,"includeClarifications":true,"includeTasks":true}""")
        )
            .andExpect(status().isOk())
            .andReturn()

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zis.nextEntry
            }
        }

        // Docs scaffold is always included, created on project creation
        assertTrue(
            entries.any { it.endsWith("docs/features/00-feature-set-overview.md") },
            "ZIP should contain docs/features/00-feature-set-overview.md, got: $entries"
        )
        assertTrue(
            entries.any { it.endsWith("docs/architecture/overview.md") },
            "ZIP should contain docs/architecture/overview.md, got: $entries"
        )
        assertTrue(
            entries.any { it.endsWith("docs/backend/api.md") },
            "ZIP should contain docs/backend/api.md, got: $entries"
        )
        assertTrue(
            entries.any { it.endsWith("docs/frontend/design.md") },
            "ZIP should contain docs/frontend/design.md, got: $entries"
        )
    }

    @Test
    fun `POST export with includeDocuments=true bundles uploads folder`() {
        val pid = createProject()
        uploadStorage.save(pid, "d1", "a.pdf", "application/pdf", byteArrayOf(1, 2, 3))

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/export").contentType(MediaType.APPLICATION_JSON)
                .content("""{"includeDocuments":true}""")
        ).andExpect(status().isOk()).andReturn()

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
            var e = zis.nextEntry
            while (e != null) { entries.add(e.name); e = zis.nextEntry }
        }
        assertTrue(entries.any { it.endsWith("uploads/a.pdf") }, "ZIP should contain uploads/a.pdf, got: $entries")
        assertTrue(entries.none { it.endsWith(".index.json") }, "ZIP must not contain .index.json, got: $entries")
    }

    @Test
    fun `POST export with includeDocuments=false skips uploads`() {
        val pid = createProject()
        uploadStorage.save(pid, "d1", "a.pdf", "application/pdf", byteArrayOf(1, 2, 3))

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/export").contentType(MediaType.APPLICATION_JSON)
                .content("""{"includeDocuments":false}""")
        ).andExpect(status().isOk()).andReturn()

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
            var e = zis.nextEntry
            while (e != null) { entries.add(e.name); e = zis.nextEntry }
        }
        assertTrue(entries.none { it.contains("uploads/") }, "ZIP must not contain uploads/, got: $entries")
    }

}
