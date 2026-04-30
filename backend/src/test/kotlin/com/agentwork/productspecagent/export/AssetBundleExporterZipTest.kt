package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.sampleManifest
import com.agentwork.productspecagent.storage.AssetBundleStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
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
}
