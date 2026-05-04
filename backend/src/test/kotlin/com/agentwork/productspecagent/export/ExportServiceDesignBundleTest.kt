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
    fun `export includes design manifest and files when bundle is uploaded`() {
        val projectId = createProject()
        val schedulerZip = java.io.File("../examples/Scheduler.zip").readBytes()

        designBundleStorage.save(projectId, "Scheduler.zip", schedulerZip)

        val zipBytes = exportService.exportProject(projectId)
        val entries = zipEntries(zipBytes)

        val entryNames = entries.keys.toList()
        assertTrue(
            entryNames.any { it.endsWith("design/manifest.json") },
            "ZIP should contain design/manifest.json, got: $entryNames"
        )
        assertTrue(
            entryNames.any { it.endsWith("design/files/Scheduler.html") },
            "ZIP should contain design/files/Scheduler.html, got: $entryNames"
        )
    }
}
