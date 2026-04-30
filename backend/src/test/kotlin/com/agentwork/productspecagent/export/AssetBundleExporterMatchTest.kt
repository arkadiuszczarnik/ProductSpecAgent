package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.sampleManifest
import com.agentwork.productspecagent.storage.AssetBundleStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AssetBundleExporterMatchTest {

    private fun newExporter(): Pair<AssetBundleExporter, AssetBundleStorage> {
        val store = InMemoryObjectStore()
        val storage = AssetBundleStorage(store)
        val exporter = AssetBundleExporter(storage)
        return exporter to storage
    }

    @Test
    fun `matchedBundles returns one match for exact string match`() {
        val (exporter, storage) = newExporter()
        val manifest = sampleManifest(
            step = FlowStepType.BACKEND, field = "framework", value = "spring-boot",
        )
        storage.writeBundle(manifest, mapOf("skills/x.md" to "x".toByteArray()))

        val wizardData = WizardData(
            projectId = "p1",
            steps = mapOf(
                "BACKEND" to WizardStepData(fields = mapOf("framework" to JsonPrimitive("spring-boot")))
            )
        )

        val result = exporter.matchedBundles(wizardData)

        assertEquals(1, result.size)
        assertEquals("backend.framework.spring-boot", result[0].manifest.id)
    }

    @Test
    fun `matchedBundles returns empty list when storage is empty`() {
        val (exporter, _) = newExporter()
        val wizardData = WizardData(projectId = "p1")
        assertTrue(exporter.matchedBundles(wizardData).isEmpty())
    }

    @Test
    fun `matchedBundles returns empty list when wizard has no relevant fields`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "framework", value = "spring-boot"),
            mapOf("skills/x.md" to "x".toByteArray())
        )
        val wizardData = WizardData(projectId = "p1") // no steps

        assertTrue(exporter.matchedBundles(wizardData).isEmpty())
    }

    @Test
    fun `matchedBundles matches each string element in JsonArray`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "framework", value = "spring-boot"),
            mapOf("skills/x.md" to "x".toByteArray())
        )
        storage.writeBundle(
            sampleManifest(field = "framework", value = "ktor"),
            mapOf("skills/x.md" to "x".toByteArray())
        )

        val wizardData = WizardData(
            projectId = "p1",
            steps = mapOf(
                "BACKEND" to WizardStepData(fields = mapOf(
                    "framework" to Json.parseToJsonElement("""["spring-boot","ktor"]""")
                ))
            )
        )

        val result = exporter.matchedBundles(wizardData)

        assertEquals(2, result.size)
        val ids = result.map { it.manifest.id }.toSet()
        assertTrue(ids.contains("backend.framework.spring-boot"))
        assertTrue(ids.contains("backend.framework.ktor"))
    }

    @Test
    fun `matchedBundles coerces Number to string`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "replicas", value = "3"),
            mapOf("skills/x.md" to "x".toByteArray())
        )

        val wizardData = WizardData(
            projectId = "p1",
            steps = mapOf(
                "BACKEND" to WizardStepData(fields = mapOf("replicas" to JsonPrimitive(3)))
            )
        )

        assertEquals(1, exporter.matchedBundles(wizardData).size)
    }

    @Test
    fun `matchedBundles is slugify-tolerant`() {
        val (exporter, storage) = newExporter()
        // Bundle author wrote raw display string — gets slugified at id-time
        storage.writeBundle(
            sampleManifest(field = "framework", value = "Spring Boot"),
            mapOf("skills/x.md" to "x".toByteArray())
        )

        val wizardData = WizardData(
            projectId = "p1",
            steps = mapOf(
                "BACKEND" to WizardStepData(fields = mapOf("framework" to JsonPrimitive("spring-boot")))
            )
        )

        assertEquals(1, exporter.matchedBundles(wizardData).size)
    }

    @Test
    fun `matchedBundles skips JsonNull and missing values`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "framework", value = "spring-boot"),
            mapOf("skills/x.md" to "x".toByteArray())
        )

        val wizardData = WizardData(
            projectId = "p1",
            steps = mapOf(
                "BACKEND" to WizardStepData(fields = mapOf("framework" to JsonNull))
            )
        )

        assertTrue(exporter.matchedBundles(wizardData).isEmpty())
    }

    @Test
    fun `matchedBundles returns results sorted by bundle id`() {
        val (exporter, storage) = newExporter()
        storage.writeBundle(
            sampleManifest(field = "framework", value = "ktor"),
            mapOf("skills/x.md" to "x".toByteArray())
        )
        storage.writeBundle(
            sampleManifest(field = "framework", value = "spring-boot"),
            mapOf("skills/x.md" to "x".toByteArray())
        )

        val wizardData = WizardData(
            projectId = "p1",
            steps = mapOf(
                "BACKEND" to WizardStepData(fields = mapOf(
                    "framework" to Json.parseToJsonElement("""["spring-boot","ktor"]""")
                ))
            )
        )

        val ids = exporter.matchedBundles(wizardData).map { it.manifest.id }
        assertEquals(listOf("backend.framework.ktor", "backend.framework.spring-boot"), ids)
    }
}
