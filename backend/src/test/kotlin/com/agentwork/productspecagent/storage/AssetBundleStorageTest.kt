package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AssetBundleStorageTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun newStore() = InMemoryObjectStore()
    private fun newStorage(store: ObjectStore) = AssetBundleStorage(store)

    private fun manifest(
        id: String = "backend.framework.kotlin-spring",
        step: FlowStepType = FlowStepType.BACKEND,
        field: String = "framework",
        value: String = "Kotlin+Spring",
    ) = AssetBundleManifest(
        id = id, step = step, field = field, value = value,
        version = "1.0.0",
        title = "Kotlin + Spring Boot Essentials",
        description = "Skills für Spring-Boot-Backend",
        createdAt = "2026-04-29T12:00:00Z",
        updatedAt = "2026-04-29T12:00:00Z",
    )

    private fun ObjectStore.putBundle(m: AssetBundleManifest, files: Map<String, ByteArray> = emptyMap()) {
        put("asset-bundles/${m.id}/manifest.json", json.encodeToString(m).toByteArray())
        files.forEach { (relPath, bytes) ->
            put("asset-bundles/${m.id}/$relPath", bytes)
        }
    }

    @Test
    fun `listAll returns empty list when no bundles exist`() {
        val storage = newStorage(newStore())
        assertEquals(emptyList<AssetBundleManifest>(), storage.listAll())
    }

    @Test
    fun `listAll returns manifest per bundle folder`() {
        val store = newStore()
        store.putBundle(manifest(id = "backend.framework.kotlin-spring"))
        store.putBundle(manifest(id = "frontend.framework.stitch", step = FlowStepType.FRONTEND, value = "Stitch"))

        val result = newStorage(store).listAll()

        assertEquals(2, result.size)
        assertEquals(setOf("backend.framework.kotlin-spring", "frontend.framework.stitch"),
                     result.map { it.id }.toSet())
    }

    @Test
    fun `listAll skips folders without manifest`() {
        val store = newStore()
        // Folder existiert (durch File), aber keine manifest.json
        store.put("asset-bundles/orphan/skills/foo.md", "content".toByteArray())
        store.putBundle(manifest(id = "backend.framework.kotlin-spring"))

        val result = newStorage(store).listAll()

        assertEquals(1, result.size)
        assertEquals("backend.framework.kotlin-spring", result[0].id)
    }

    @Test
    fun `listAll skips folders with invalid JSON manifest`() {
        val store = newStore()
        store.put("asset-bundles/broken/manifest.json", "not valid json {".toByteArray())
        store.putBundle(manifest(id = "backend.framework.kotlin-spring"))

        val result = newStorage(store).listAll()

        assertEquals(1, result.size)
        assertEquals("backend.framework.kotlin-spring", result[0].id)
    }

    @Test
    fun `listAll skips manifest with unknown step enum value`() {
        val store = newStore()
        // Manifest mit step="UNKNOWN" — kotlinx.serialization wirft beim Decode
        val invalidJson = """{"id":"x","step":"UNKNOWN","field":"f","value":"v","version":"1","title":"t","description":"d","createdAt":"2026","updatedAt":"2026"}"""
        store.put("asset-bundles/bad-step/manifest.json", invalidJson.toByteArray())
        store.putBundle(manifest(id = "backend.framework.kotlin-spring"))

        val result = newStorage(store).listAll()

        assertEquals(1, result.size)
        assertEquals("backend.framework.kotlin-spring", result[0].id)
    }
}
