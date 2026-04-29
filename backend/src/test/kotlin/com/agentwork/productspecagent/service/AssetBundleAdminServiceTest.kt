package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.storage.AssetBundleStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AssetBundleAdminServiceTest {

    private fun newService(): Pair<AssetBundleAdminService, InMemoryObjectStore> {
        val store = InMemoryObjectStore()
        val storage = AssetBundleStorage(store)
        val service = AssetBundleAdminService(storage, AssetBundleZipExtractor())
        return service to store
    }

    @Test
    fun `upload extracts, writes, and returns result`() {
        val (service, store) = newService()
        val zip = buildZip(
            manifest = sampleManifest(),
            files = mapOf("skills/x/SKILL.md" to "x".toByteArray())
        )

        val result = service.upload(zip)

        assertEquals("backend.framework.kotlin-spring", result.manifest.id)
        assertEquals(1, result.fileCount)
        assertNotNull(store.get("asset-bundles/backend.framework.kotlin-spring/manifest.json"))
        assertNotNull(store.get("asset-bundles/backend.framework.kotlin-spring/skills/x/SKILL.md"))
    }

    @Test
    fun `upload of existing bundle wipes old files`() {
        val (service, store) = newService()
        // First upload with two files
        val firstZip = buildZip(
            manifest = sampleManifest(),
            files = mapOf(
                "skills/old1.md" to "x".toByteArray(),
                "skills/old2.md" to "x".toByteArray(),
            )
        )
        service.upload(firstZip)

        // Second upload with only one file
        val secondZip = buildZip(
            manifest = sampleManifest(),
            files = mapOf("skills/new.md" to "x".toByteArray())
        )
        service.upload(secondZip)

        assertNull(store.get("asset-bundles/backend.framework.kotlin-spring/skills/old1.md"))
        assertNull(store.get("asset-bundles/backend.framework.kotlin-spring/skills/old2.md"))
        assertNotNull(store.get("asset-bundles/backend.framework.kotlin-spring/skills/new.md"))
    }

    @Test
    fun `upload propagates extractor exceptions and does not modify storage`() {
        val (service, store) = newService()
        val invalidZip = "garbage".toByteArray()

        assertThrows(IllegalBundleEntryException::class.java) { service.upload(invalidZip) }

        // Storage must remain empty
        assertTrue(store.listKeys("asset-bundles/").isEmpty())
    }
}
