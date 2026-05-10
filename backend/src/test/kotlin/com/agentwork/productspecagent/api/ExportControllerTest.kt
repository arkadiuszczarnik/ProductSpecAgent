package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ExportControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var uploadStorage: com.agentwork.productspecagent.storage.UploadStorage
    @Autowired lateinit var assetBundleStorage: com.agentwork.productspecagent.storage.AssetBundleStorage
    @Autowired lateinit var wizardService: com.agentwork.productspecagent.service.WizardService
    @Autowired lateinit var projectService: com.agentwork.productspecagent.service.ProjectService
    @Autowired lateinit var projectStorage: com.agentwork.productspecagent.storage.ProjectStorage
    @Autowired lateinit var designWorkbenchStorage: com.agentwork.productspecagent.storage.DesignWorkbenchStorage
    @Autowired lateinit var objectStore: com.agentwork.productspecagent.storage.ObjectStore

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
    fun `POST export returns ZIP with README at root and lowercase spec under docs`() {
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
        assertTrue(entries.any { it.endsWith("/docs/spec.md") },
            "ZIP should contain docs/spec.md, got: $entries")
        assertFalse(entries.any { it.endsWith("/docs/SPEC.md") },
            "ZIP should not contain docs/SPEC.md, got: $entries")
        assertTrue(entries.any { it.endsWith(".gitignore") },
            "ZIP should contain .gitignore, got: $entries")
        assertTrue(entries.none { it.matches(Regex(".*/[^/]+/SPEC\\.md$")) && !it.contains("/docs/") },
            "ZIP should NOT contain top-level SPEC.md anymore, got: $entries")
    }

    @Test
    fun `POST export includes agent handoff markdowns without implementation order`() {
        val pid = createProject()

        val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk())
            .andReturn()

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val claudeMd = entries.entries.firstOrNull { it.key.endsWith("/CLAUDE.md") }
        val agentsMd = entries.entries.firstOrNull { it.key.endsWith("/AGENTS.md") }
        val implementationOrder = entries.entries.firstOrNull { it.key.endsWith("/implementation-order.md") }

        assertNotNull(claudeMd, "ZIP should contain CLAUDE.md, got: ${entries.keys}")
        assertNotNull(agentsMd, "ZIP should contain AGENTS.md, got: ${entries.keys}")
        kotlin.test.assertNull(implementationOrder, "ZIP should not contain implementation-order.md, got: ${entries.keys}")
        assertTrue(
            agentsMd.value.contains("# AGENTS.md") && agentsMd.value.contains("## Arbeitsweise"),
            "AGENTS.md should use agent-template.md.mustache, got:\n${agentsMd.value}"
        )
        assertTrue(
            agentsMd.value.contains("feature-implementieren"),
            "AGENTS.md should mention the feature implementation asset bundle, got:\n${agentsMd.value}"
        )
        assertTrue(
            agentsMd.value.contains("global.living-sync-reporter"),
            "AGENTS.md should mention the Living Sync reporter asset bundle, got:\n${agentsMd.value}"
        )
        assertTrue(
            !claudeMd.value.contains("## How to Sync This Project"),
            "CLAUDE.md should not use handoff.md.mustache, got:\n${claudeMd.value}"
        )
    }

    @Test
    fun `POST export without body uses defaults`() {
        val pid = createProject()

        mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk())
    }

    @Test
    fun `POST export ZIP includes feature docs scaffold only`() {
        val pid = createProject()
        projectStorage.saveDocsFile(pid, "docs/architecture/overview.md", "stale")
        projectStorage.saveDocsFile(pid, "docs/backend/api.md", "stale")
        projectStorage.saveDocsFile(pid, "docs/frontend/design.md", "stale")

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

        // Feature docs scaffold is always included, created on project creation.
        assertTrue(
            entries.any { it.endsWith("docs/features/00-feature-set-overview.md") },
            "ZIP should contain docs/features/00-feature-set-overview.md, got: $entries"
        )
        assertFalse(
            entries.any { it.endsWith("docs/architecture/overview.md") },
            "ZIP should not contain docs/architecture/overview.md, got: $entries"
        )
        assertFalse(
            entries.any { it.endsWith("docs/backend/api.md") },
            "ZIP should not contain docs/backend/api.md, got: $entries"
        )
        assertFalse(
            entries.any { it.endsWith("docs/frontend/design.md") },
            "ZIP should not contain docs/frontend/design.md, got: $entries"
        )
    }

    @Test
    fun `POST export excludes option controlled docs when flags are false`() {
        val pid = createProject()
        projectStorage.saveDocsFile(pid, "docs/decisions/decision-1.json", """{"id":"d1"}""")
        projectStorage.saveDocsFile(pid, "docs/clarifications/clarification-1.json", """{"id":"c1"}""")
        projectStorage.saveDocsFile(pid, "docs/tasks/task-1.json", """{"id":"t1"}""")
        projectStorage.saveDocsFile(pid, "docs/PLAN.md", "# Plan")
        projectStorage.saveDocsFile(pid, "docs/plan.md", "# Plan")

        val result = mockMvc.perform(
            post("/api/v1/projects/$pid/export").contentType(MediaType.APPLICATION_JSON)
                .content("""{"includeDecisions":false,"includeClarifications":false,"includeTasks":false}""")
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

        assertFalse(entries.any { it.contains("/docs/decisions/") }, "ZIP should not contain decisions, got: $entries")
        assertFalse(entries.any { it.contains("/docs/clarifications/") }, "ZIP should not contain clarifications, got: $entries")
        assertFalse(entries.any { it.contains("/docs/tasks/") }, "ZIP should not contain tasks, got: $entries")
        assertFalse(entries.any { it.endsWith("/docs/PLAN.md") }, "ZIP should not contain PLAN.md, got: $entries")
        assertFalse(entries.any { it.endsWith("/docs/plan.md") }, "ZIP should not contain plan.md, got: $entries")
    }

    @Test
    fun `POST export ZIP includes generated docs SPEC only`() {
        val pid = createProject()
        projectService.saveSpecFile(pid, "problem.md", "# Problem\n\nRaw problem spec.")
        projectService.saveSpecFile(pid, "spec.md", "# Product Spec\n\nClean summary.")

        val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk())
            .andReturn()

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val rawStepEntry = entries.entries.firstOrNull { it.key.endsWith("/spec/problem.md") }
        kotlin.test.assertNull(rawStepEntry, "ZIP should not contain raw step specs, got: ${entries.keys}")

        val storageSpecEntry = entries.entries.firstOrNull { it.key.endsWith("/spec/spec.md") }
        kotlin.test.assertNull(storageSpecEntry, "ZIP should not contain spec/spec.md, got: ${entries.keys}")

        val docsSpecEntry = entries.entries.firstOrNull { it.key.endsWith("/docs/spec.md") }
        assertNotNull(docsSpecEntry, "ZIP should contain docs/spec.md, got: ${entries.keys}")
        kotlin.test.assertNull(
            entries.entries.firstOrNull { it.key.endsWith("/docs/SPEC.md") },
            "ZIP should not contain docs/SPEC.md, got: ${entries.keys}"
        )
        assertTrue(docsSpecEntry.value.contains("# Product Spec\n\nClean summary."))
    }

    @Test
    fun `POST export includes active design screens and docs spec`() {
        val pid = createProject()
        saveActiveDesignScreen(pid)
        designWorkbenchStorage.writeActiveScreen(pid, "<html><body>Landing design</body></html>".toByteArray())
        designWorkbenchStorage.writeDesignSummary(pid, "# Design\n\nLanding design summary.")

        val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray

        val designScreen = assertNotNull(readZipEntry(zipBytes) { it.endsWith("/design/screens/design/index.html") })
        assertTrue(designScreen.contains("Landing design"))
        val designSummary = assertNotNull(readZipEntry(zipBytes) { it.endsWith("/design/design.md") })
        assertTrue(designSummary.contains("Landing design summary."))
        assertNotNull(readZipEntry(zipBytes) { it.endsWith("/docs/spec.md") })
    }

    @Test
    fun `POST export does not fail for pre-completion active design variant`() {
        val pid = createProject()
        saveActiveDesignScreen(pid)

        val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray

        kotlin.test.assertNull(readZipEntry(zipBytes) { it.endsWith("/design/screens/design/index.html") })
        assertNotNull(readZipEntry(zipBytes) { it.endsWith("/docs/spec.md") })
    }

    @Test
    fun `POST export excludes stale design screens`() {
        val pid = createProject()
        saveActiveDesignScreen(pid)
        designWorkbenchStorage.writeActiveScreen(pid, "<html><body>Landing design</body></html>".toByteArray())
        objectStore.put(
            designWorkbenchStorage.activeScreenKey(pid, "stale"),
            "<html><body>Stale design</body></html>".toByteArray(),
            "text/html",
        )

        val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
            .andExpect(status().isOk())
            .andReturn()

        val zipBytes = result.response.contentAsByteArray

        assertNotNull(readZipEntry(zipBytes) { it.endsWith("/design/screens/design/index.html") })
        kotlin.test.assertNull(readZipEntry(zipBytes) { it.endsWith("/design/screens/stale/index.html") })
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
        assertTrue(readme.contains("`docs/spec.md`"), "README should reference docs/spec.md, got: $readme")
        assertFalse(readme.contains("`docs/SPEC.md`"), "README should not reference docs/SPEC.md")
        assertFalse(readme.contains("`spec/spec.md`"), "README should not reference spec/spec.md")
        assertTrue(readme.contains("`docs/plan.md`"), "README should reference docs/plan.md")
        assertFalse(readme.contains("`docs/PLAN.md`"), "README should not reference docs/PLAN.md")
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

        // Bundle file under neutral asset-bundles path
        val skillKey = entries.keys.firstOrNull {
            it.endsWith(".asset-bundles/skills/backend.framework.spring-boot/api-design/SKILL.md")
        }
        assertNotNull(skillKey, "expected bundle file under .asset-bundles/skills/backend.framework.spring-boot/, got: ${entries.keys}")
        assertEquals("# API Design", entries[skillKey]?.toString(Charsets.UTF_8))

        // README mentions the bundle
        val readmeKey = entries.keys.firstOrNull { it.endsWith("/README.md") }
        assertNotNull(readmeKey)
        val readme = entries[readmeKey]!!.toString(Charsets.UTF_8)
        assertTrue(readme.contains("## Included Asset Bundles"), "expected bundle section in README; got:\n$readme")
        assertTrue(readme.contains("Spring Boot Skills"), "expected bundle title in README; got:\n$readme")
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

    private fun saveActiveDesignScreen(projectId: String) {
        designWorkbenchStorage.saveGeneratedDesign(
            projectId = projectId,
            analysis = com.agentwork.productspecagent.domain.DesignAnalysis(
                summary = "Landing design",
                visualDirection = "Focused",
                rationale = "Ready",
            ),
            generated = com.agentwork.productspecagent.domain.GeneratedDesign(
                id = "design-1",
                title = "Landing",
                htmlPath = designWorkbenchStorage.currentDesignKey(projectId),
                rationale = "Ready",
                createdAt = "2026-05-10T00:00:00Z",
            ),
            html = "<html><body>Variant design</body></html>".toByteArray(),
        )
    }

}
