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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ExportControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var uploadStorage: com.agentwork.productspecagent.storage.UploadStorage
    @Autowired lateinit var assetBundleStorage: com.agentwork.productspecagent.storage.AssetBundleStorage
    @Autowired lateinit var wizardService: com.agentwork.productspecagent.service.WizardService

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
        // Clean up any test-uploaded bundles
        try {
            assetBundleStorage.delete(
                com.agentwork.productspecagent.domain.FlowStepType.BACKEND,
                "framework",
                "spring-boot",
            )
        } catch (_: Exception) { /* tolerant */ }
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
    fun `POST export returns ZIP with README at root and SPEC under docs`() {
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

        assertTrue(entries.any { it.endsWith("/README.md") && !it.contains("/docs/") },
            "ZIP should contain README.md at root, got: $entries")
        assertTrue(entries.any { it.endsWith("/docs/SPEC.md") },
            "ZIP should contain docs/SPEC.md, got: $entries")
        assertTrue(entries.any { it.endsWith(".gitignore") },
            "ZIP should contain .gitignore, got: $entries")
        assertTrue(entries.none { it.matches(Regex(".*/[^/]+/SPEC\\.md$")) && !it.contains("/docs/") },
            "ZIP should NOT contain top-level SPEC.md anymore, got: $entries")
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
    fun `POST export bundles uploads under docs-uploads`() {
        val pid = createProject()
        uploadStorage.save(pid, "d1", "a.pdf", "application/pdf", byteArrayOf(1, 2, 3), "2026-04-27T10:00:00Z")

        val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk()).andReturn()

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
            var e = zis.nextEntry
            while (e != null) { entries.add(e.name); e = zis.nextEntry }
        }
        assertTrue(entries.any { it.endsWith("/docs/uploads/a.pdf") },
            "ZIP should contain docs/uploads/a.pdf, got: $entries")
        assertTrue(entries.none { it.endsWith(".index.json") },
            "ZIP must not contain .index.json, got: $entries")
        assertTrue(entries.none { it.matches(Regex(".*/[^/]*/uploads/a\\.pdf$")) && !it.contains("/docs/") },
            "ZIP must not contain top-level uploads/a.pdf, got: $entries")
    }

    @Test
    fun `README references new docs paths`() {
        val pid = createProject()

        val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk()).andReturn()

        val readme = ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
            var entry = zis.nextEntry
            var content: String? = null
            while (entry != null) {
                if (entry.name.endsWith("/README.md")) {
                    content = String(zis.readAllBytes())
                    break
                }
                entry = zis.nextEntry
            }
            content
        }

        assertNotNull(readme, "README.md should exist in ZIP")
        assertTrue(readme!!.contains("`docs/SPEC.md`"), "README should reference docs/SPEC.md, got: $readme")
        assertTrue(readme.contains("`docs/PLAN.md`"), "README should reference docs/PLAN.md")
        assertTrue(readme.contains("`docs/decisions/`"), "README should reference docs/decisions/")
        assertTrue(readme.contains("`docs/clarifications/`"), "README should reference docs/clarifications/")
        assertTrue(readme.contains("`docs/tasks/`"), "README should reference docs/tasks/")
    }

    @Test
    fun `POST export merges matching asset bundle into zip and lists it in README`() {
        val pid = createProject()

        // Upload a bundle directly via storage (skip the HTTP upload path — that's covered elsewhere)
        val manifest = com.agentwork.productspecagent.domain.AssetBundleManifest(
            id = "backend.framework.spring-boot",
            step = com.agentwork.productspecagent.domain.FlowStepType.BACKEND,
            field = "framework",
            value = "spring-boot",
            version = "1.0.0",
            title = "Spring Boot Skills",
            description = "Curated skills",
            createdAt = "2026-04-30T00:00:00Z",
            updatedAt = "2026-04-30T00:00:00Z",
        )
        assetBundleStorage.writeBundle(
            manifest,
            mapOf("skills/api-design/SKILL.md" to "# API Design".toByteArray()),
        )

        // Configure wizard so that BACKEND.framework = spring-boot
        wizardService.saveStepData(
            pid, "BACKEND",
            com.agentwork.productspecagent.domain.WizardStepData(
                fields = mapOf("framework" to kotlinx.serialization.json.JsonPrimitive("spring-boot"))
            )
        )

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"includeDecisions":false,"includeClarifications":false,"includeTasks":false}""")
        )
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }

        // Bundle file under namespaced .claude path
        val skillKey = entries.keys.firstOrNull {
            it.endsWith(".claude/skills/backend.framework.spring-boot/api-design/SKILL.md")
        }
        assertNotNull(skillKey, "expected bundle file under .claude/skills/backend.framework.spring-boot/, got: ${entries.keys}")
        assertEquals("# API Design", entries[skillKey]?.toString(Charsets.UTF_8))

        // README mentions the bundle
        val readmeKey = entries.keys.firstOrNull { it.endsWith("/README.md") }
        assertNotNull(readmeKey)
        val readme = entries[readmeKey]!!.toString(Charsets.UTF_8)
        assertTrue(readme.contains("## Included Asset Bundles"), "expected bundle section in README; got:\n$readme")
        assertTrue(readme.contains("Spring Boot Skills"), "expected bundle title in README; got:\n$readme")
    }

}
