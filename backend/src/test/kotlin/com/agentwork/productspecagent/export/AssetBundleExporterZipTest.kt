package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.sampleManifest
import com.agentwork.productspecagent.storage.AssetBundleStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class AssetBundleExporterZipTest {

    private fun newExporter(): Pair<AssetBundleExporter, AssetBundleStorage> {
        val store = InMemoryObjectStore()
        val storage = AssetBundleStorage(store)
        val exporter = AssetBundleExporter(storage)
        return exporter to storage
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

    private fun runWriteToZip(
        exporter: AssetBundleExporter,
        prefix: String,
        bundles: List<MatchedBundle>,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip -> exporter.writeToZip(zip, prefix, bundles) }
        return baos.toByteArray()
    }

    @Test
    fun `writeToZip writes a single skills file under namespaced path`() {
        val (exporter, storage) = newExporter()
        val manifest = sampleManifest(field = "framework", value = "spring-boot")
        storage.writeBundle(manifest, mapOf("skills/api/SKILL.md" to "content".toByteArray()))

        val wizardData = WizardData(
            projectId = "p1",
            steps = mapOf(
                "BACKEND" to WizardStepData(fields = mapOf("framework" to JsonPrimitive("spring-boot")))
            )
        )
        val bundles = exporter.matchedBundles(wizardData)

        val zipBytes = runWriteToZip(exporter, "myapp", bundles)
        val entries = zipEntries(zipBytes)

        assertEquals(setOf("myapp/.claude/skills/backend.framework.spring-boot/api/SKILL.md"), entries.keys)
        assertEquals("content", entries.values.first().toString(Charsets.UTF_8))
    }

    @Test
    fun `writeToZip writes files from all three top-level dirs`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "framework", value = "spring-boot"),
            mapOf(
                "skills/s.md" to "s".toByteArray(),
                "commands/c.md" to "c".toByteArray(),
                "agents/a.md" to "a".toByteArray(),
            )
        )
        val bundles = exporter.matchedBundles(
            WizardData(
                projectId = "p1",
                steps = mapOf("BACKEND" to WizardStepData(
                    fields = mapOf("framework" to JsonPrimitive("spring-boot"))
                ))
            )
        )

        val entries = zipEntries(runWriteToZip(exporter, "myapp", bundles))

        assertTrue(entries.containsKey("myapp/.claude/skills/backend.framework.spring-boot/s.md"))
        assertTrue(entries.containsKey("myapp/.claude/commands/backend.framework.spring-boot/c.md"))
        assertTrue(entries.containsKey("myapp/.claude/agents/backend.framework.spring-boot/a.md"))
    }

    @Test
    fun `writeToZip namespaces files from two bundles with same relative path`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "framework", value = "spring-boot"),
            mapOf("skills/api-design.md" to "from-spring".toByteArray())
        )
        storage.writeBundle(
            sampleManifest(field = "framework", value = "ktor"),
            mapOf("skills/api-design.md" to "from-ktor".toByteArray())
        )
        val bundles = exporter.matchedBundles(
            WizardData(
                projectId = "p1",
                steps = mapOf("BACKEND" to WizardStepData(
                    fields = mapOf("framework" to Json.parseToJsonElement("""["spring-boot","ktor"]"""))
                ))
            )
        )

        val entries = zipEntries(runWriteToZip(exporter, "myapp", bundles))

        assertEquals("from-ktor", entries["myapp/.claude/skills/backend.framework.ktor/api-design.md"]?.toString(Charsets.UTF_8))
        assertEquals("from-spring", entries["myapp/.claude/skills/backend.framework.spring-boot/api-design.md"]?.toString(Charsets.UTF_8))
    }

    @Test
    fun `writeToZip skips files outside whitelisted top dirs`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "framework", value = "spring-boot"),
            mapOf(
                "skills/ok.md" to "ok".toByteArray(),
                "rogue/x.md" to "rogue".toByteArray(),
            )
        )
        val bundles = exporter.matchedBundles(
            WizardData(
                projectId = "p1",
                steps = mapOf("BACKEND" to WizardStepData(
                    fields = mapOf("framework" to JsonPrimitive("spring-boot"))
                ))
            )
        )

        val entries = zipEntries(runWriteToZip(exporter, "myapp", bundles))

        assertTrue(entries.containsKey("myapp/.claude/skills/backend.framework.spring-boot/ok.md"))
        assertFalse(entries.keys.any { it.contains("rogue") })
    }

    @Test
    fun `writeToZip skips files with path traversal attempts`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "framework", value = "spring-boot"),
            mapOf(
                "skills/ok.md" to "ok".toByteArray(),
                "skills/../../etc/passwd" to "evil".toByteArray(),
            )
        )
        val bundles = exporter.matchedBundles(
            WizardData(
                projectId = "p1",
                steps = mapOf("BACKEND" to WizardStepData(
                    fields = mapOf("framework" to JsonPrimitive("spring-boot"))
                ))
            )
        )

        val entries = zipEntries(runWriteToZip(exporter, "myapp", bundles))

        assertTrue(entries.containsKey("myapp/.claude/skills/backend.framework.spring-boot/ok.md"))
        assertFalse(entries.keys.any { it.contains("..") || it.contains("/etc/passwd") })
    }

    @Test
    fun `writeToZip writes nothing when bundles list is empty`() {
        val (exporter, _) = newExporter()
        val entries = zipEntries(runWriteToZip(exporter, "myapp", emptyList()))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `writeToZip skips files when loadFileBytes returns null`() {
        val (exporter, _) = newExporter()
        // Synthesize a MatchedBundle whose files list points to keys that don't exist in storage
        val bundle = MatchedBundle(
            manifest = sampleManifest(field = "framework", value = "spring-boot"),
            files = listOf(
                AssetBundleFile("skills/ghost.md", 10, "text/markdown")
            ),
        )

        val entries = zipEntries(runWriteToZip(exporter, "myapp", listOf(bundle)))

        assertTrue(entries.isEmpty(), "expected no entries, got: ${entries.keys}")
    }
}
