package com.agentwork.productspecagent.export

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

@SpringBootTest
class ExportPackageBuilderTest {

    @Autowired lateinit var packageBuilder: ExportPackageBuilder
    @Autowired lateinit var projectService: com.agentwork.productspecagent.service.ProjectService

    @Test
    fun `project export returns named zip package with agent markdowns`() {
        val projectId = projectService.createProject("My App").project.id

        val zip = packageBuilder.exportProject(projectId)
        val entries = zipEntries(zip.bytes)

        assertThat(zip.filename).isEqualTo("my-app.zip")
        assertThat(zip.mediaType).isEqualTo("application/zip")
        assertThat(entries.keys).contains(
            "my-app/README.md",
            "my-app/CLAUDE.md",
            "my-app/AGENTS.md",
        )
    }

    @Test
    fun `handoff export returns flat package with generated markdowns and bundled sync skills`() {
        val projectId = projectService.createProject("Handoff App").project.id

        val zip = packageBuilder.exportHandoff(
            projectId,
            "http://localhost/api/v1/projects/$projectId/handoff/handoff.zip",
        )
        val entries = zipEntries(zip.bytes)

        assertThat(zip.filename).isEqualTo("handoff-app-handoff.zip")
        assertThat(entries.keys).contains(
            "README.md",
            "CLAUDE.md",
            "AGENTS.md",
            "implementation-order.md",
            ".claude/settings.json",
            ".claude/living-sync.json",
            ".asset-bundles/skills/global.living-sync-reporter/living-sync-reporter/SKILL.md",
            ".asset-bundles/skills/global.product-spec-sync/product-spec-sync/SKILL.md",
        )
        assertThat(entries.keys.none { it.startsWith("handoff-app/") }).isTrue()
    }

    @Test
    fun `handoff export applies overrides without changing generated preview defaults`() {
        val projectId = projectService.createProject("Override App").project.id
        val syncUrl = "http://localhost/api/v1/projects/$projectId/handoff/handoff.zip"
        val preview = packageBuilder.previewHandoff(projectId, syncUrl)

        val zip = packageBuilder.exportHandoff(
            projectId,
            syncUrl,
            HandoffPackageOptions(
                overrides = HandoffOverrides(
                    claudeMd = "# Custom CLAUDE",
                    agentsMd = "# Custom AGENTS",
                    implementationOrder = "# Custom Order",
                ),
            ),
        )
        val entries = zipEntries(zip.bytes)

        assertThat(preview.claudeMd).contains("How to Sync This Project")
        assertThat(entries["CLAUDE.md"]!!.toString(Charsets.UTF_8)).contains("# Custom CLAUDE")
        assertThat(entries["AGENTS.md"]!!.toString(Charsets.UTF_8)).contains("# Custom AGENTS")
        assertThat(entries["implementation-order.md"]!!.toString(Charsets.UTF_8)).contains("# Custom Order")
    }

    private fun zipEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                result[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }
        return result
    }
}
