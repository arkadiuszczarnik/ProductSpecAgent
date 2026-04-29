package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AssetBundleStorageIntegrationTest : S3TestSupport() {

    private val json = Json { ignoreUnknownKeys = true }

    private fun manifest(id: String, step: FlowStepType, field: String, value: String) =
        AssetBundleManifest(
            id = id, step = step, field = field, value = value,
            version = "1.0.0",
            title = "T", description = "D",
            createdAt = "2026-04-29T12:00:00Z",
            updatedAt = "2026-04-29T12:00:00Z",
        )

    @Test
    fun `listAll and find work against real MinIO`() {
        val store = objectStore()
        val storage = AssetBundleStorage(store)

        val m = manifest("backend.framework.kotlin-spring", FlowStepType.BACKEND, "framework", "Kotlin+Spring")
        store.put("asset-bundles/${m.id}/manifest.json", json.encodeToString(m).toByteArray())
        store.put("asset-bundles/${m.id}/skills/spring-testing/SKILL.md", "content".toByteArray())
        store.put("asset-bundles/${m.id}/commands/gradle-build.md", "build".toByteArray())

        val all = storage.listAll()
        assertEquals(1, all.size)
        assertEquals(m.id, all[0].id)

        val found = storage.find(FlowStepType.BACKEND, "framework", "Kotlin+Spring")
        assertNotNull(found)
        assertEquals(2, found!!.files.size)
        assertTrue(found.files.any { it.relativePath == "skills/spring-testing/SKILL.md" })
    }

    @Test
    fun `writeBundle and delete work against real MinIO`() {
        val store = objectStore()
        val storage = AssetBundleStorage(store)
        val m = AssetBundleManifest(
            id = "frontend.framework.stitch", step = FlowStepType.FRONTEND, field = "framework", value = "Stitch",
            version = "1.0.0", title = "T", description = "D",
            createdAt = "2026-04-29T12:00:00Z", updatedAt = "2026-04-29T12:00:00Z",
        )

        storage.writeBundle(m, mapOf(
            "skills/stitch-components/SKILL.md" to "skill content".toByteArray(),
            "commands/stitch-init.md" to "init".toByteArray(),
        ))

        val found = storage.find(FlowStepType.FRONTEND, "framework", "Stitch")
        assertNotNull(found)
        assertEquals(2, found!!.files.size)

        val bytes = storage.loadFileBytes(FlowStepType.FRONTEND, "framework", "Stitch", "skills/stitch-components/SKILL.md")
        assertEquals("skill content", bytes!!.toString(Charsets.UTF_8))

        storage.delete(FlowStepType.FRONTEND, "framework", "Stitch")
        assertNull(storage.find(FlowStepType.FRONTEND, "framework", "Stitch"))
    }
}
