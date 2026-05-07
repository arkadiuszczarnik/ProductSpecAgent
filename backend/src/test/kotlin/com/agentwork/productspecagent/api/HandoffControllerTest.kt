package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class HandoffControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var projectService: com.agentwork.productspecagent.service.ProjectService

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
    fun `POST preview claudeMd uses neutral handoff template`() {
        val pid = createProject()

        val result = mockMvc.perform(post("/api/v1/projects/$pid/handoff/preview"))
            .andExpect(status().isOk())
            .andReturn()

        val body = result.response.contentAsString
        assertTrue(body.contains("## How to Sync This Project"), "Preview should include neutral handoff sync section")
        assertFalse(body.contains("## Living-Sync Reporting"), "Neutral handoff preview should not use claude-specific template")
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

    @Test
    fun `POST export embeds neutral handoff template into CLAUDE md`() {
        val pid = createProject()

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/handoff/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"format":"claude-code"}""")
        )
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray
        val claudeContent = readZipEntry(zipBytes) { it.endsWith("CLAUDE.md") }
            ?: error("CLAUDE.md not found in handoff ZIP")

        assertTrue(claudeContent.startsWith("# Handoff Test"), "CLAUDE.md should start with project H1, got: ${claudeContent.take(80)}")
        assertTrue(
            claudeContent.contains("## How to Sync This Project"),
            "CLAUDE.md should contain 'How to Sync This Project' section"
        )
        assertTrue(
            claudeContent.contains("/handoff/handoff.zip"),
            "CLAUDE.md should embed sync URL pointing at the GET endpoint"
        )
        assertFalse(claudeContent.contains("## Living-Sync Reporting"), "CLAUDE.md should use neutral handoff template")
    }

    @Test
    fun `GET handoff zip returns ZIP and embeds the request URL into CLAUDE md`() {
        val pid = createProject()

        val result = mockMvc.perform(get("/api/v1/projects/$pid/handoff/handoff.zip"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("-handoff.zip")))
            .andExpect(header().string("Content-Type", "application/zip"))
            .andReturn()

        val zipBytes = result.response.contentAsByteArray
        assertTrue(zipBytes.isNotEmpty(), "GET should return non-empty ZIP")

        val claudeContent = readZipEntry(zipBytes) { it.endsWith("CLAUDE.md") }
            ?: error("CLAUDE.md not found in handoff ZIP")

        assertTrue(
            claudeContent.contains("/api/v1/projects/$pid/handoff/handoff.zip"),
            "CLAUDE.md from GET response should embed the original request URL"
        )
    }

    @Test
    fun `GET handoff zip serves a flat ZIP without slug prefix`() {
        val pid = createProject()
        projectService.saveSpecFile(pid, "problem.md", "# Problem\n\nRaw handoff spec.")

        val result = mockMvc.perform(get("/api/v1/projects/$pid/handoff/handoff.zip"))
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.contains("CLAUDE.md"), "CLAUDE.md should be at root, got: $entries")
        assertTrue(entries.contains("AGENTS.md"), "AGENTS.md should be at root, got: $entries")
        assertTrue(entries.contains("implementation-order.md"), "implementation-order.md should be at root, got: $entries")
        assertTrue(entries.contains("spec/problem.md"), "Raw spec file should be under spec/, got: $entries")
        assertTrue(
            entries.none { it.startsWith("handoff-test/") },
            "No entry should be under the slug folder, got: $entries"
        )
    }

    @Test
    fun `GET handoff zip embeds Living Sync asset bundle plus dynamic config`() {
        val pid = createProject()

        val result = mockMvc.perform(get("/api/v1/projects/$pid/handoff/handoff.zip"))
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray
        val skill = readZipEntry(zipBytes) { it == ".asset-bundles/skills/global.living-sync-reporter/living-sync-reporter/SKILL.md" }
            ?: error("Living Sync skill not found in handoff ZIP")
        val launcher = readZipEntry(zipBytes) { it == ".asset-bundles/skills/global.living-sync-reporter/living-sync-reporter/bin/living-sync-reporter" }
            ?: error("Living Sync launcher not found in handoff ZIP")
        val windowsLauncher = readZipEntry(zipBytes) { it == ".asset-bundles/skills/global.living-sync-reporter/living-sync-reporter/bin/living-sync-reporter.cmd" }
            ?: error("Living Sync Windows launcher not found in handoff ZIP")
        val linuxBinary = readZipEntry(zipBytes) { it == ".asset-bundles/skills/global.living-sync-reporter/living-sync-reporter/bin/linux-amd64/living-sync-reporter.gz" }
            ?: error("Living Sync Linux binary not found in handoff ZIP")
        val config = readZipEntry(zipBytes) { it == ".claude/living-sync.json" }
            ?: error("Living Sync config not found in handoff ZIP")
        val settings = readZipEntry(zipBytes) { it == ".claude/settings.json" }
            ?: error("Claude settings not found in handoff ZIP")

        assertTrue(skill.contains("living-sync-reporter"), "Skill should define the Living Sync reporter")
        assertFalse(skill.contains("/api/v1/projects/$pid/living-sync/mcp"), "Skill should stay static and not embed project-specific endpoint")
        assertTrue(config.contains("/api/v1/projects/$pid/living-sync/mcp"), "Config should embed project-specific endpoint")
        assertTrue(launcher.contains("uname -s"), "Launcher should detect Unix OS")
        assertTrue(launcher.contains("gzip -dc"), "Launcher should expand compressed Unix binary")
        assertTrue(windowsLauncher.contains("windows-amd64"), "Windows launcher should select Windows binary")
        assertTrue(linuxBinary.isNotEmpty(), "Linux binary should be bundled")
        assertTrue(settings.contains("\"PostToolUse\""), "Settings should configure PostToolUse hooks")
        assertTrue(settings.contains("\"Stop\""), "Settings should configure Stop hooks")
        assertTrue(settings.contains(".asset-bundles/skills/global.living-sync-reporter"), "Hooks should call the neutral asset-bundle reporter")
        assertZipSymlink(zipBytes, ".claude/skills", "../.asset-bundles/skills")
        assertZipSymlink(zipBytes, ".agents/skills", "../.asset-bundles/skills")
    }

    @Test
    fun `GET handoff zip embeds Product Spec Sync asset bundle`() {
        val pid = createProject()

        val result = mockMvc.perform(get("/api/v1/projects/$pid/handoff/handoff.zip"))
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray
        val skill = readZipEntry(zipBytes) { it == ".asset-bundles/skills/global.product-spec-sync/product-spec-sync/SKILL.md" }
            ?: error("Product Spec Sync skill not found in handoff ZIP")

        assertTrue(skill.contains("How to Sync This Project"), "Skill should include sync instructions")
        assertTrue(skill.contains("Product-Spec-Agent"), "Skill should mention Product-Spec-Agent")
        assertTrue(skill.contains("curl -L -o handoff.zip"), "Skill should include the sync command")
        assertFalse(skill.contains("{{{syncUrl}}}"), "Static skill should not contain Mustache placeholders")
    }

    private fun assertZipSymlink(zipBytes: ByteArray, name: String, target: String) {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == name) {
                    val mode = com.agentwork.productspecagent.export.ZipSymlinkSupport.centralDirectoryUnixMode(zipBytes, name)
                    assertTrue(mode != null && (mode and 0xF000) == 0xA000, "$name should be a symlink")
                    assertTrue(zis.readBytes().toString(Charsets.UTF_8) == target, "$name should point to $target")
                    return
                }
                entry = zis.nextEntry
            }
        }
        error("Symlink $name not found in handoff ZIP")
    }

    private fun readZipEntry(zipBytes: ByteArray, predicate: (String) -> Boolean): String? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (predicate(entry.name)) return zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        return null
    }
}
