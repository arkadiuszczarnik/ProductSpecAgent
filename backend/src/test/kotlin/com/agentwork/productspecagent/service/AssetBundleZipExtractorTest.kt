package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.assetBundleId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AssetBundleZipExtractorTest {

    private val extractor = AssetBundleZipExtractor()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sampleManifest(
        step: FlowStepType = FlowStepType.BACKEND,
        field: String = "framework",
        value: String = "kotlin-spring",
        id: String = assetBundleId(step, field, value),
        title: String = "Kotlin Spring",
        description: String = "A sample bundle",
        version: String = "1.0.0",
    ) = AssetBundleManifest(
        id = id,
        step = step,
        field = field,
        value = value,
        title = title,
        description = description,
        version = version,
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-01-01T00:00:00Z",
    )

    private fun buildZip(
        manifest: AssetBundleManifest? = sampleManifest(),
        files: Map<String, ByteArray> = emptyMap(),
        rawExtras: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            if (manifest != null) {
                val json = kotlinx.serialization.json.Json.encodeToString(
                    AssetBundleManifest.serializer(), manifest
                )
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(json.toByteArray())
                zos.closeEntry()
            }
            for ((path, bytes) in files) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
            for ((path, bytes) in rawExtras) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    // ── Happy-path tests (Task 2) ─────────────────────────────────────────────

    @Test
    fun `extract returns manifest and files for valid ZIP`() {
        val zip = buildZip(
            manifest = sampleManifest(),
            files = mapOf(
                "skills/spring-testing/SKILL.md" to "skill body".toByteArray(),
                "commands/gradle-build.md" to "command body".toByteArray(),
            )
        )

        val result = extractor.extract(zip)

        assertEquals("backend.framework.kotlin-spring", result.manifest.id)
        assertEquals(FlowStepType.BACKEND, result.manifest.step)
        assertEquals(2, result.files.size)
        assertEquals("skill body", result.files["skills/spring-testing/SKILL.md"]?.toString(Charsets.UTF_8))
    }

    @Test
    fun `extract preserves nested file paths`() {
        val zip = buildZip(
            files = mapOf(
                "skills/a/b/c/deep.md" to "x".toByteArray(),
                "agents/agent.md" to "y".toByteArray(),
            )
        )

        val result = extractor.extract(zip)

        assertEquals(setOf("skills/a/b/c/deep.md", "agents/agent.md"), result.files.keys)
    }

    // ── Validation tests (Task 3) ─────────────────────────────────────────────

    @Test
    fun `extract throws MissingManifestException when manifest_json missing`() {
        val zip = buildZip(manifest = null, files = mapOf("skills/x.md" to "x".toByteArray()))
        assertThrows(MissingManifestException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws MissingManifestException when manifest_json nested in folder`() {
        val zip = buildZip(
            manifest = null,
            rawExtras = mapOf("skills/manifest.json" to "{}".toByteArray())
        )
        assertThrows(MissingManifestException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws InvalidManifestException for malformed JSON`() {
        val zip = buildZip(manifest = null, rawExtras = mapOf("manifest.json" to "not json {".toByteArray()))
        assertThrows(InvalidManifestException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws InvalidManifestException for blank required field`() {
        val zip = buildZip(manifest = sampleManifest(title = ""))
        val ex = assertThrows(InvalidManifestException::class.java) { extractor.extract(zip) }
        assertTrue(ex.message!!.contains("title"))
    }

    @Test
    fun `extract throws UnsupportedStepException for IDEA step`() {
        val zip = buildZip(manifest = sampleManifest(step = FlowStepType.IDEA))
        assertThrows(UnsupportedStepException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws UnsupportedStepException for PROBLEM step`() {
        val zip = buildZip(manifest = sampleManifest(step = FlowStepType.PROBLEM))
        assertThrows(UnsupportedStepException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws ManifestIdMismatchException when id does not match triple`() {
        val zip = buildZip(manifest = sampleManifest(id = "backend.framework.wrong-slug"))
        val ex = assertThrows(ManifestIdMismatchException::class.java) { extractor.extract(zip) }
        assertTrue(ex.message!!.contains("backend.framework.kotlin-spring"))
    }

    @Test
    fun `extract throws IllegalBundleEntryException for top-level file outside allowlist`() {
        val zip = buildZip(
            manifest = sampleManifest(),
            rawExtras = mapOf("README.md" to "x".toByteArray())
        )
        val ex = assertThrows(IllegalBundleEntryException::class.java) { extractor.extract(zip) }
        assertTrue(ex.message!!.contains("README.md"))
    }

    @Test
    fun `extract throws IllegalBundleEntryException for top-level folder outside allowlist`() {
        val zip = buildZip(
            manifest = sampleManifest(),
            rawExtras = mapOf("rules/foo.md" to "x".toByteArray())
        )
        assertThrows(IllegalBundleEntryException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws IllegalBundleEntryException for path traversal entry`() {
        val zip = buildZip(
            manifest = sampleManifest(),
            rawExtras = mapOf("skills/../../etc/passwd" to "x".toByteArray())
        )
        assertThrows(IllegalBundleEntryException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws IllegalBundleEntryException for absolute path entry`() {
        val zip = buildZip(
            manifest = sampleManifest(),
            rawExtras = mapOf("/etc/passwd" to "x".toByteArray())
        )
        assertThrows(IllegalBundleEntryException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws IllegalBundleEntryException for invalid ZIP bytes`() {
        val notAZip = "this is not a zip".toByteArray()
        assertThrows(IllegalBundleEntryException::class.java) { extractor.extract(notAZip) }
    }

    @Test
    fun `extract silently skips DS_Store, __MACOSX, Thumbs_db entries`() {
        val zip = buildZip(
            manifest = sampleManifest(),
            files = mapOf("skills/x/SKILL.md" to "x".toByteArray()),
            rawExtras = mapOf(
                ".DS_Store" to "junk".toByteArray(),
                "__MACOSX/foo" to "junk".toByteArray(),
                "skills/x/.DS_Store" to "junk".toByteArray(),
                "Thumbs.db" to "junk".toByteArray(),
            )
        )

        val result = extractor.extract(zip)

        assertEquals(1, result.files.size)
        assertEquals(setOf("skills/x/SKILL.md"), result.files.keys)
    }
}
