package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.service.sampleManifest
import com.agentwork.productspecagent.storage.AssetBundleStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AssetBundleExporterReadmeTest {

    private fun newExporter(): AssetBundleExporter =
        AssetBundleExporter(AssetBundleStorage(InMemoryObjectStore()))

    @Test
    fun `renderReadmeSection returns empty string for empty bundle list`() {
        val exporter = newExporter()
        assertEquals("", exporter.renderReadmeSection(emptyList()))
    }

    @Test
    fun `renderReadmeSection lists single bundle with title id version triple description`() {
        val exporter = newExporter()
        val bundle = MatchedBundle(
            manifest = sampleManifest(
                field = "framework", value = "spring-boot",
                title = "Spring Boot Skills",
                description = "Curated skills for Spring",
                version = "1.2.0",
            ),
            files = emptyList(),
        )

        val md = exporter.renderReadmeSection(listOf(bundle))

        assertTrue(md.contains("## Included Asset Bundles"), "expected heading; got:\n$md")
        assertTrue(md.contains("**Spring Boot Skills**"), "expected title bold; got:\n$md")
        assertTrue(md.contains("`backend.framework.spring-boot`"), "expected id in code; got:\n$md")
        assertTrue(md.contains("v1.2.0"), "expected version; got:\n$md")
        assertTrue(md.contains("BACKEND.framework"), "expected step.field; got:\n$md")
        assertTrue(md.contains("spring-boot"), "expected value; got:\n$md")
        assertTrue(md.contains("Curated skills for Spring"), "expected description; got:\n$md")
    }

    @Test
    fun `renderReadmeSection sorts bundles alphabetically by id`() {
        val exporter = newExporter()
        val ktor = MatchedBundle(sampleManifest(field = "framework", value = "ktor", title = "Ktor"), emptyList())
        val spring = MatchedBundle(sampleManifest(field = "framework", value = "spring-boot", title = "Spring"), emptyList())

        val md = exporter.renderReadmeSection(listOf(spring, ktor))

        val ktorIdx = md.indexOf("backend.framework.ktor")
        val springIdx = md.indexOf("backend.framework.spring-boot")
        assertTrue(ktorIdx in 0..springIdx, "expected ktor before spring; md=\n$md")
    }
}
