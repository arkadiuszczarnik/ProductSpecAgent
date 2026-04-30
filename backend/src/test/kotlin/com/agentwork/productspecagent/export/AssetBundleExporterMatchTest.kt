package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import com.agentwork.productspecagent.service.sampleManifest
import com.agentwork.productspecagent.storage.AssetBundleStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
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
}
