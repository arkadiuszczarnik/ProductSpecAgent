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
class HandoffControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Handoff Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `POST preview returns handoff content with claudeMd agentsMd implementationOrder`() {
        val pid = createProject()

        mockMvc.perform(post("/api/v1/projects/$pid/handoff/preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.claudeMd").isNotEmpty)
            .andExpect(jsonPath("$.agentsMd").isNotEmpty)
            .andExpect(jsonPath("$.implementationOrder").isNotEmpty)
            .andExpect(jsonPath("$.format").value("claude-code"))
    }

    @Test
    fun `POST preview with codex format returns format codex`() {
        val pid = createProject()

        mockMvc.perform(post("/api/v1/projects/$pid/handoff/preview?format=codex"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.format").value("codex"))
            .andExpect(jsonPath("$.agentsMd").isNotEmpty)
    }

    @Test
    fun `POST export returns ZIP containing CLAUDE md AGENTS md implementation-order md README md`() {
        val pid = createProject()

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/handoff/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"format":"claude-code"}""")
        )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("-handoff.zip")))
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

        assertTrue(entries.any { it.endsWith("CLAUDE.md") }, "ZIP should contain CLAUDE.md, got: $entries")
        assertTrue(entries.any { it.endsWith("AGENTS.md") }, "ZIP should contain AGENTS.md, got: $entries")
        assertTrue(entries.any { it.endsWith("implementation-order.md") }, "ZIP should contain implementation-order.md, got: $entries")
        assertTrue(entries.any { it.endsWith("README.md") }, "ZIP should contain README.md, got: $entries")
    }

    @Test
    fun `POST export with custom overrides works`() {
        val pid = createProject()

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/handoff/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"format":"claude-code","claudeMd":"# Custom CLAUDE","agentsMd":"# Custom AGENTS","implementationOrder":"# Custom Order"}""")
        )
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray
        assertTrue(zipBytes.isNotEmpty())

        val fileContents = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                fileContents[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val claudeEntry = fileContents.entries.find { it.key.endsWith("CLAUDE.md") }
        assertTrue(claudeEntry != null && claudeEntry.value.contains("# Custom CLAUDE"), "CLAUDE.md should have custom content")

        val agentsEntry = fileContents.entries.find { it.key.endsWith("AGENTS.md") }
        assertTrue(agentsEntry != null && agentsEntry.value.contains("# Custom AGENTS"), "AGENTS.md should have custom content")

        val orderEntry = fileContents.entries.find { it.key.endsWith("implementation-order.md") }
        assertTrue(orderEntry != null && orderEntry.value.contains("# Custom Order"), "implementation-order.md should have custom content")
    }
}
