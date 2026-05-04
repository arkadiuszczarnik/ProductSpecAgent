package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.storage.DesignBundleStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

@SpringBootTest
class ExportServiceDesignBundleTest {

    @Autowired lateinit var exportService: ExportService
    @Autowired lateinit var designBundleStorage: DesignBundleStorage
    @Autowired lateinit var projectService: com.agentwork.productspecagent.service.ProjectService

    private val createdProjectIds = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        val root = java.nio.file.Paths.get("build/test-data/projects")
        for (pid in createdProjectIds) {
            designBundleStorage.delete(pid)
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
        val project = projectService.createProject("Export Design Test")
        createdProjectIds.add(project.project.id)
        return project.project.id
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

    @Test
    fun `export includes design manifest and files under docs design`() {
        val projectId = createProject()
        val schedulerZip = java.io.File("../examples/Scheduler.zip").readBytes()

        designBundleStorage.save(projectId, "Scheduler.zip", schedulerZip)

        val zipBytes = exportService.exportProject(projectId)
        val entries = zipEntries(zipBytes)

        val entryNames = entries.keys.toList()
        assertTrue(
            entryNames.any { it.endsWith("docs/design/manifest.json") },
            "ZIP should contain docs/design/manifest.json, got: $entryNames"
        )
        assertTrue(
            entryNames.any { it.endsWith("docs/design/Scheduler.html") },
            "ZIP should contain docs/design/Scheduler.html, got: $entryNames"
        )
    }

    @Test
    fun `export does not duplicate design files under legacy design prefix`() {
        val projectId = createProject()
        val schedulerZip = java.io.File("../examples/Scheduler.zip").readBytes()

        designBundleStorage.save(projectId, "Scheduler.zip", schedulerZip)

        val zipBytes = exportService.exportProject(projectId)
        val entries = zipEntries(zipBytes)

        // Compute the prefix that ExportService applies (slugified project name).
        // The project name "Export Design Test" slugifies to "export-design-test".
        val legacyPrefixedEntries = entries.keys.filter {
            it.matches(Regex("[^/]+/design/.*"))
        }
        assertTrue(
            legacyPrefixedEntries.isEmpty(),
            "No entry should live under <prefix>/design/ (legacy layout). Got: $legacyPrefixedEntries"
        )
    }
}
