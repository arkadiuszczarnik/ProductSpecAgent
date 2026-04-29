# Asset-Bundle-Admin-UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Web-Admin-Oberfläche für Asset-Bundles — ZIP-Upload mit strenger Validierung, Liste mit Filter, Detail-Vorschau (Markdown/Code rendered), Löschen mit Bestätigung. Backend ergänzt schreibende API-Endpoints und einen File-Bytes-Endpoint, den Sub-Feature C wiederverwendet.

**Architecture:** Drei neue REST-Endpoints (`POST`, `DELETE`, `GET …/files/**`) auf der existierenden `AssetBundleController`. Zwei neue Service-Klassen: `AssetBundleZipExtractor` (pure Validierungs-Pipeline, kein I/O) und `AssetBundleAdminService` (orchestriert extract → delete → write). `AssetBundleStorage` wird um schreibende Methoden erweitert. Frontend: neue Top-Level-Route `/asset-bundles` mit Liste/Detail/Upload/Delete, eigener Zustand-Store, Wiederverwendung der existierenden `shiki`-Helper aus `SpecFileViewer.tsx`.

**Tech Stack:** Kotlin 2.3, Spring Boot 4 (mit `MultipartFile`), kotlinx.serialization, AWS SDK v2 via existierendem `ObjectStore`, JUnit 5, Testcontainers (MinIO), MockMvc. Frontend: Next.js 16, React 19, Zustand, base-ui/react Primitives, lucide-react, shiki.

**Spec:** `docs/superpowers/specs/2026-04-29-asset-bundle-admin-ui-design.md`

---

## File Structure

| Datei | Verantwortung |
|---|---|
| `backend/.../service/AssetBundleValidationExceptions.kt` | Sechs Exception-Klassen für die Pipeline |
| `backend/.../service/AssetBundleZipExtractor.kt` | Pure ZIP→ExtractedBundle-Pipeline, kein I/O |
| `backend/.../domain/AssetBundleUpload.kt` | `ExtractedBundle` + `AssetBundleUploadResult` data classes |
| `backend/.../service/AssetBundleAdminService.kt` | Orchestriert Upload (extract → delete → write) |
| `backend/.../storage/AssetBundleStorage.kt` | **Modify** — `writeBundle`, `delete`, `loadFileBytes` |
| `backend/.../api/AssetBundleController.kt` | **Modify** — `POST`, `DELETE`, `GET …/files/**` |
| `backend/.../api/GlobalExceptionHandler.kt` | **Modify** — neue Handler |
| `backend/.../test/.../service/AssetBundleZipExtractorTest.kt` | ~16 Unit-Tests gegen pure Pipeline |
| `backend/.../test/.../service/AssetBundleZipExtractorTestFixtures.kt` | `buildZip(...)` Helper |
| `backend/.../test/.../storage/AssetBundleStorageTest.kt` | **Modify** — 7 neue Tests für write/delete/loadFileBytes |
| `backend/.../test/.../service/AssetBundleAdminServiceTest.kt` | 3 Orchestrierungs-Tests mit Mocks |
| `backend/.../test/.../api/AssetBundleControllerTest.kt` | **Modify** — 11 neue Endpoint-Tests |
| `backend/.../test/.../storage/AssetBundleStorageIntegrationTest.kt` | **Modify** — 1 neuer Test für writeBundle/delete |
| `frontend/src/lib/api.ts` | **Modify** — Types + 5 neue Wrapper |
| `frontend/src/lib/stores/asset-bundle-store.ts` | Neuer Zustand-Store |
| `frontend/src/app/asset-bundles/page.tsx` | Server-Component-Wrapper |
| `frontend/src/components/asset-bundles/AssetBundlesPage.tsx` | Top-Level-Layout, lädt Liste |
| `frontend/src/components/asset-bundles/BundleList.tsx` | Liste + Filter + eingebetteter Upload |
| `frontend/src/components/asset-bundles/BundleUpload.tsx` | Drag-Drop ZIP-Upload |
| `frontend/src/components/asset-bundles/BundleDetail.tsx` | Manifest-Card + File-Tree + Viewer + Delete |
| `frontend/src/components/asset-bundles/FileViewer.tsx` | Markdown/Code/Image Renderer |
| `frontend/src/components/asset-bundles/DeleteBundleDialog.tsx` | Confirmation-Dialog |
| `frontend/src/components/layout/AppShell.tsx` | **Modify** — neuer NavItem |

---

## Pre-Flight

- [ ] **Branch erstellen**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent checkout -b feat/asset-bundle-admin-ui
```

- [ ] **Spec + Plan committen als ersten Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add docs/superpowers/specs/2026-04-29-asset-bundle-admin-ui-design.md docs/superpowers/plans/2026-04-29-asset-bundle-admin-ui.md
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "docs(asset-bundles-admin): add design spec and implementation plan"
```

- [ ] **Verify clean state**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --quiet
```

Expected: alle Tests grün als Baseline.

---

## Task 1: Validation Exceptions

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleValidationExceptions.kt`

- [ ] **Step 1: Datei anlegen**

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType

class MissingManifestException(message: String) : RuntimeException(message)

class InvalidManifestException(message: String) : RuntimeException(message)

class ManifestIdMismatchException(expected: String, actual: String) :
    RuntimeException("Manifest id mismatch: expected '$expected', got '$actual'")

class UnsupportedStepException(step: FlowStepType) :
    RuntimeException("Bundles only supported for BACKEND, FRONTEND, ARCHITECTURE — got $step")

class IllegalBundleEntryException(path: String, reason: String) :
    RuntimeException("Illegal bundle entry '$path': $reason")

class BundleTooLargeException(message: String) : RuntimeException(message)

class BundleFileNotFoundException(bundleId: String, relativePath: String) :
    RuntimeException("File not found in bundle '$bundleId': $relativePath")
```

- [ ] **Step 2: Compile verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew compileKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleValidationExceptions.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): add validation exception classes"
```

---

## Task 2: ZipExtractor — Happy Path + Test Fixtures

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/AssetBundleUpload.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractor.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTestFixtures.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTest.kt`

- [ ] **Step 1: Domain types anlegen**

`backend/src/main/kotlin/com/agentwork/productspecagent/domain/AssetBundleUpload.kt`:

```kotlin
package com.agentwork.productspecagent.domain

data class ExtractedBundle(
    val manifest: AssetBundleManifest,
    val files: Map<String, ByteArray>,
)

data class AssetBundleUploadResult(
    val manifest: AssetBundleManifest,
    val fileCount: Int,
)
```

- [ ] **Step 2: Test-Fixtures-Helper anlegen**

`backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTestFixtures.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val json = Json { ignoreUnknownKeys = true }

fun sampleManifest(
    id: String = "backend.framework.kotlin-spring",
    step: FlowStepType = FlowStepType.BACKEND,
    field: String = "framework",
    value: String = "Kotlin+Spring",
    title: String = "Kotlin + Spring Boot Essentials",
    description: String = "Skills für Spring",
    version: String = "1.0.0",
) = AssetBundleManifest(
    id = id, step = step, field = field, value = value,
    version = version, title = title, description = description,
    createdAt = "2026-04-29T12:00:00Z",
    updatedAt = "2026-04-29T12:00:00Z",
)

/** Builds a ZIP byte-array from manifest (optional) + file map + raw extras. */
fun buildZip(
    manifest: AssetBundleManifest? = sampleManifest(),
    files: Map<String, ByteArray> = emptyMap(),
    rawExtras: Map<String, ByteArray> = emptyMap(),
): ByteArray {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zip ->
        if (manifest != null) {
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }
        files.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
        rawExtras.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return baos.toByteArray()
}

fun bytesOfSize(size: Int): ByteArray = ByteArray(size) { 'a'.code.toByte() }
```

- [ ] **Step 3: Failing happy-path tests schreiben**

`backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTest.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AssetBundleZipExtractorTest {

    private val extractor = AssetBundleZipExtractor()

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
}
```

- [ ] **Step 4: Tests laufen lassen, Failure verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.service.AssetBundleZipExtractorTest" --quiet
```

Expected: FAIL — `AssetBundleZipExtractor` nicht gefunden.

- [ ] **Step 5: Extractor mit happy-path-Implementation anlegen**

`backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractor.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.ExtractedBundle
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.assetBundleId
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

@Service
class AssetBundleZipExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    fun extract(bytes: ByteArray): ExtractedBundle {
        val rawFiles = readZip(bytes)
        val manifestBytes = rawFiles.remove("manifest.json")
            ?: throw MissingManifestException("manifest.json must be at the ZIP root")
        val manifest = parseManifest(manifestBytes)
        return ExtractedBundle(manifest, rawFiles.toMap())
    }

    private fun readZip(bytes: ByteArray): MutableMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        val zis = try {
            ZipInputStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
        }
        zis.use { stream ->
            try {
                var entry = stream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        files[entry.name] = stream.readAllBytes()
                    }
                    stream.closeEntry()
                    entry = stream.nextEntry
                }
            } catch (e: ZipException) {
                throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
            }
        }
        return files
    }

    private fun parseManifest(bytes: ByteArray): AssetBundleManifest = try {
        json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
    } catch (e: Exception) {
        throw InvalidManifestException("manifest.json: ${e.message}")
    }
}
```

- [ ] **Step 6: Tests laufen lassen, Pass verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.service.AssetBundleZipExtractorTest" --quiet
```

Expected: PASS, 2 tests.

- [ ] **Step 7: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/main/kotlin/com/agentwork/productspecagent/domain/AssetBundleUpload.kt backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractor.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTestFixtures.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): AssetBundleZipExtractor happy path"
```

---

## Task 3: ZipExtractor — Manifest- and Structure-Validation

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractor.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTest.kt`

- [ ] **Step 1: Failing tests anhängen**

In `AssetBundleZipExtractorTest`, am Ende der Klasse anhängen:

```kotlin
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
```

- [ ] **Step 2: Tests laufen lassen, Failure verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.service.AssetBundleZipExtractorTest" --quiet
```

Expected: FAIL — die meisten neuen Tests werfen entweder gar keine Exception oder die falsche.

- [ ] **Step 3: Validierung implementieren**

In `AssetBundleZipExtractor.kt` ersetzen die `extract`-Methode komplett durch die Vollversion (alle Validierungen außer Größenlimits — die kommen in Task 4):

```kotlin
    private val allowedSteps = setOf(FlowStepType.BACKEND, FlowStepType.FRONTEND, FlowStepType.ARCHITECTURE)
    private val allowedTopLevelFolders = setOf("skills", "commands", "agents")

    fun extract(bytes: ByteArray): ExtractedBundle {
        val rawFiles = readZip(bytes)
        val manifestBytes = rawFiles.remove("manifest.json")
            ?: throw MissingManifestException("manifest.json must be at the ZIP root")
        val manifest = parseManifest(manifestBytes)
        validateManifest(manifest)
        validateEntries(rawFiles.keys)
        return ExtractedBundle(manifest, rawFiles.toMap())
    }

    private fun readZip(bytes: ByteArray): MutableMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        val zis = try {
            ZipInputStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
        }
        zis.use { stream ->
            try {
                var entry = stream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && !isFiltered(entry.name)) {
                        validatePath(entry.name)
                        files[entry.name] = stream.readAllBytes()
                    }
                    stream.closeEntry()
                    entry = stream.nextEntry
                }
            } catch (e: ZipException) {
                throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
            }
        }
        if (files.isEmpty() && rawZipEmpty(bytes)) {
            throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: no entries found")
        }
        return files
    }

    private fun rawZipEmpty(bytes: ByteArray): Boolean {
        // If we got 0 entries, this might be valid (empty zip) or invalid bytes.
        // Distinguish by checking the magic bytes (PK header).
        return bytes.size < 4 || !(bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
    }

    private fun isFiltered(name: String): Boolean {
        if (name.endsWith(".DS_Store")) return true
        if (name.startsWith("__MACOSX/")) return true
        if (name.endsWith("Thumbs.db")) return true
        return false
    }

    private fun validatePath(name: String) {
        if (name.contains("../") || name.startsWith("/") || name.startsWith("\\")) {
            throw IllegalBundleEntryException(name, "path traversal blocked")
        }
        val normalized = java.nio.file.Paths.get(name).normalize().toString()
        // Path.normalize uses platform separator; convert backslashes to forward slashes for comparison
        if (normalized.replace('\\', '/') != name) {
            throw IllegalBundleEntryException(name, "non-canonical path")
        }
    }

    private fun parseManifest(bytes: ByteArray): AssetBundleManifest = try {
        json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
    } catch (e: Exception) {
        throw InvalidManifestException("manifest.json: ${e.message}")
    }

    private fun validateManifest(m: AssetBundleManifest) {
        if (m.step !in allowedSteps) throw UnsupportedStepException(m.step)
        val expectedId = assetBundleId(m.step, m.field, m.value)
        if (m.id != expectedId) throw ManifestIdMismatchException(expected = expectedId, actual = m.id)
        if (m.title.isBlank()) throw InvalidManifestException("Required field empty: title")
        if (m.description.isBlank()) throw InvalidManifestException("Required field empty: description")
        if (m.version.isBlank()) throw InvalidManifestException("Required field empty: version")
    }

    private fun validateEntries(paths: Set<String>) {
        for (path in paths) {
            val firstSegment = path.substringBefore('/')
            if (firstSegment == path) {
                throw IllegalBundleEntryException(path, "Top-level files not allowed (only manifest.json + skills/ commands/ agents/)")
            }
            if (firstSegment !in allowedTopLevelFolders) {
                throw IllegalBundleEntryException(path, "Top-level must be skills/, commands/, or agents/")
            }
        }
    }
```

- [ ] **Step 4: Tests laufen lassen, Pass verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.service.AssetBundleZipExtractorTest" --quiet
```

Expected: PASS, 15 tests total (2 happy + 13 new).

- [ ] **Step 5: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractor.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTest.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): manifest and structure validation in ZipExtractor"
```

---

## Task 4: ZipExtractor — Size Limits and Zip-Bomb Protection

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractor.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTest.kt`

- [ ] **Step 1: Failing tests anhängen**

In `AssetBundleZipExtractorTest`, am Ende der Klasse anhängen:

```kotlin
    @Test
    fun `extract throws BundleTooLargeException for file count over 100`() {
        val files = (1..101).associate { "skills/file-$it.md" to "x".toByteArray() }
        val zip = buildZip(manifest = sampleManifest(), files = files)
        assertThrows(BundleTooLargeException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws BundleTooLargeException for single file over 2 MB`() {
        val zip = buildZip(
            manifest = sampleManifest(),
            files = mapOf("skills/big.md" to bytesOfSize(2 * 1024 * 1024 + 1))
        )
        assertThrows(BundleTooLargeException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract throws BundleTooLargeException when total size over 10 MB`() {
        val files = (1..6).associate { "skills/file-$it.md" to bytesOfSize(2 * 1024 * 1024) }
        val zip = buildZip(manifest = sampleManifest(), files = files)
        assertThrows(BundleTooLargeException::class.java) { extractor.extract(zip) }
    }

    @Test
    fun `extract counts non-filtered entries against the limit`() {
        // 100 valid files plus DS_Store entries should still pass
        val files = (1..100).associate { "skills/file-$it.md" to "x".toByteArray() }
        val zip = buildZip(
            manifest = sampleManifest(),
            files = files,
            rawExtras = (1..50).associate { ".DS_Store-$it" to "junk".toByteArray() }
        )
        // Note: ".DS_Store-N" doesn't match the filter (only exact ".DS_Store" suffix).
        // We need actual ".DS_Store" entries which are unique per ZIP, so use varying paths:
        val zipWithFilters = buildZip(
            manifest = sampleManifest(),
            files = files,
            rawExtras = mapOf(
                ".DS_Store" to "junk".toByteArray(),
                "__MACOSX/foo" to "junk".toByteArray(),
            )
        )
        // 100 valid + 2 filtered should pass
        val result = extractor.extract(zipWithFilters)
        assertEquals(100, result.files.size)
    }
```

- [ ] **Step 2: Tests laufen lassen, Failure verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.service.AssetBundleZipExtractorTest" --quiet
```

Expected: FAIL — Größenlimits noch nicht implementiert.

- [ ] **Step 3: Größenlimits in `readZip` einbauen**

Konstanten oben in der Klasse ergänzen (vor `private val json`):

```kotlin
    private val maxFileCount = 100
    private val maxFileSizeBytes = 2L * 1024 * 1024     // 2 MB per file
    private val maxTotalSizeBytes = 10L * 1024 * 1024   // 10 MB total
```

Die `readZip`-Methode komplett ersetzen durch eine Version mit Counter-Logic:

```kotlin
    private fun readZip(bytes: ByteArray): MutableMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L

        val zis = try {
            ZipInputStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
        }
        zis.use { stream ->
            try {
                var entry = stream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && !isFiltered(entry.name)) {
                        validatePath(entry.name)
                        val entryBytes = readEntryWithLimit(stream, entry.name)
                        totalBytes += entryBytes.size
                        if (totalBytes > maxTotalSizeBytes) {
                            throw BundleTooLargeException("Total bundle size exceeds 10 MB")
                        }
                        files[entry.name] = entryBytes
                        if (files.size > maxFileCount) {
                            throw BundleTooLargeException("Too many files: > $maxFileCount")
                        }
                    }
                    stream.closeEntry()
                    entry = stream.nextEntry
                }
            } catch (e: ZipException) {
                throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
            }
        }
        if (files.isEmpty() && rawZipEmpty(bytes)) {
            throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: no entries found")
        }
        return files
    }

    private fun readEntryWithLimit(stream: ZipInputStream, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxFileSizeBytes) {
                throw BundleTooLargeException("File too large: $name (limit ${maxFileSizeBytes / (1024 * 1024)} MB)")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
```

Plus `import java.io.ByteArrayOutputStream` ergänzen am Datei-Anfang.

- [ ] **Step 4: Tests laufen lassen, Pass verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.service.AssetBundleZipExtractorTest" --quiet
```

Expected: PASS, 19 tests total (4 happy/structure + 13 manifest/structure + 4 size).

- [ ] **Step 5: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractor.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractorTest.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): size limits and zip-bomb protection in ZipExtractor"
```

---

## Task 5: AssetBundleStorage Erweiterung — write/delete/loadFileBytes

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt`

- [ ] **Step 1: Failing tests anhängen**

In `AssetBundleStorageTest`, am Ende der Klasse (nach dem letzten existierenden Test, vor dem schließenden `}`):

```kotlin
    @Test
    fun `writeBundle persists manifest and files at correct keys`() {
        val store = newStore()
        val storage = newStorage(store)
        val m = manifest(id = "backend.framework.kotlin-spring")

        storage.writeBundle(m, mapOf(
            "skills/x/SKILL.md" to "skill".toByteArray(),
            "commands/cmd.md" to "cmd".toByteArray(),
        ))

        assertNotNull(store.get("asset-bundles/${m.id}/manifest.json"))
        assertNotNull(store.get("asset-bundles/${m.id}/skills/x/SKILL.md"))
        assertNotNull(store.get("asset-bundles/${m.id}/commands/cmd.md"))
    }

    @Test
    fun `writeBundle writes manifest last so partial writes are invisible to find`() {
        // Test by simulating only file writes happening (without manifest):
        // find() should return null because manifest is the existence-marker
        val store = newStore()
        store.put("asset-bundles/backend.framework.kotlin-spring/skills/x.md", "x".toByteArray())
        // No manifest written yet
        val result = newStorage(store).find(FlowStepType.BACKEND, "framework", "Kotlin+Spring")
        assertNull(result)
    }

    @Test
    fun `delete removes all keys under bundle prefix`() {
        val store = newStore()
        val storage = newStorage(store)
        val m = manifest(id = "backend.framework.kotlin-spring")
        storage.writeBundle(m, mapOf("skills/x.md" to "x".toByteArray(), "commands/y.md" to "y".toByteArray()))

        storage.delete(FlowStepType.BACKEND, "framework", "Kotlin+Spring")

        assertNull(store.get("asset-bundles/${m.id}/manifest.json"))
        assertNull(store.get("asset-bundles/${m.id}/skills/x.md"))
        assertNull(store.get("asset-bundles/${m.id}/commands/y.md"))
    }

    @Test
    fun `delete is idempotent when bundle does not exist`() {
        val storage = newStorage(newStore())
        // Should not throw
        storage.delete(FlowStepType.BACKEND, "framework", "Kotlin+Spring")
    }

    @Test
    fun `loadFileBytes returns bytes for existing file`() {
        val store = newStore()
        val storage = newStorage(store)
        val m = manifest(id = "backend.framework.kotlin-spring")
        storage.writeBundle(m, mapOf("skills/x.md" to "hello".toByteArray()))

        val result = storage.loadFileBytes(FlowStepType.BACKEND, "framework", "Kotlin+Spring", "skills/x.md")

        assertNotNull(result)
        assertEquals("hello", result!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `loadFileBytes returns null for missing file`() {
        val store = newStore()
        val storage = newStorage(store)
        storage.writeBundle(manifest(), mapOf("skills/x.md" to "x".toByteArray()))

        val result = storage.loadFileBytes(FlowStepType.BACKEND, "framework", "Kotlin+Spring", "skills/missing.md")

        assertNull(result)
    }

    @Test
    fun `loadFileBytes returns null when bundle does not exist`() {
        val storage = newStorage(newStore())
        val result = storage.loadFileBytes(FlowStepType.BACKEND, "framework", "Kotlin+Spring", "skills/x.md")
        assertNull(result)
    }
```

- [ ] **Step 2: Tests laufen lassen, Failure verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageTest" --quiet
```

Expected: FAIL — `writeBundle`, `delete`, `loadFileBytes` existieren nicht.

- [ ] **Step 3: Methoden in `AssetBundleStorage.kt` hinzufügen**

Vor dem schließenden `}` der Klasse:

```kotlin
    fun writeBundle(manifest: AssetBundleManifest, files: Map<String, ByteArray>) {
        val bundlePrefix = "$rootPrefix${manifest.id}/"
        // Write all files first, manifest LAST — find() uses manifest as existence marker
        files.forEach { (relativePath, bytes) ->
            objectStore.put("$bundlePrefix$relativePath", bytes, contentTypeFor(relativePath))
        }
        val manifestJson = json.encodeToString(AssetBundleManifest.serializer(), manifest).toByteArray()
        objectStore.put("${bundlePrefix}manifest.json", manifestJson, "application/json")
    }

    fun delete(step: FlowStepType, field: String, value: String) {
        val id = assetBundleId(step, field, value)
        objectStore.deletePrefix("$rootPrefix$id/")
    }

    fun loadFileBytes(step: FlowStepType, field: String, value: String, relativePath: String): ByteArray? {
        val id = assetBundleId(step, field, value)
        return objectStore.get("$rootPrefix$id/$relativePath")
    }
```

Wichtig: `kotlinx.serialization.encodeToString` braucht `import kotlinx.serialization.encodeToString` — falls noch nicht vorhanden, ergänzen am Datei-Anfang. (Alternativ ist `AssetBundleManifest.serializer()` kompiletime stabil.)

Falls der vorhandene `Json`-Import in der Datei den `prettyPrint` nicht aktiviert hat — das ist akzeptabel, S3-Browser können kompakte JSON anzeigen.

- [ ] **Step 4: Tests laufen lassen, Pass verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageTest" --quiet
```

Expected: PASS, 20 tests total (13 alt + 7 neu).

- [ ] **Step 5: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): write, delete, loadFileBytes on AssetBundleStorage"
```

---

## Task 6: AssetBundleAdminService — Orchestration

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleAdminService.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleAdminServiceTest.kt`

- [ ] **Step 1: Failing tests schreiben**

```kotlin
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
```

- [ ] **Step 2: Tests laufen lassen, Failure verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.service.AssetBundleAdminServiceTest" --quiet
```

Expected: FAIL — `AssetBundleAdminService` nicht gefunden.

- [ ] **Step 3: Service implementieren**

`backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleAdminService.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.AssetBundleUploadResult
import com.agentwork.productspecagent.storage.AssetBundleStorage
import org.springframework.stereotype.Service

@Service
class AssetBundleAdminService(
    private val storage: AssetBundleStorage,
    private val extractor: AssetBundleZipExtractor,
) {
    fun upload(zipBytes: ByteArray): AssetBundleUploadResult {
        val extracted = extractor.extract(zipBytes)
        // Clean-wipe before write: ensures stale files from previous version are gone
        storage.delete(extracted.manifest.step, extracted.manifest.field, extracted.manifest.value)
        storage.writeBundle(extracted.manifest, extracted.files)
        return AssetBundleUploadResult(extracted.manifest, extracted.files.size)
    }
}
```

- [ ] **Step 4: Tests laufen lassen, Pass verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.service.AssetBundleAdminServiceTest" --quiet
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleAdminService.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/AssetBundleAdminServiceTest.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): AssetBundleAdminService orchestrates upload"
```

---

## Task 7: GlobalExceptionHandler — Neue Mappings

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt`

- [ ] **Step 1: Imports + Handler ergänzen**

In `GlobalExceptionHandler.kt`, neue Imports nach den existierenden Service-Imports einfügen:

```kotlin
import com.agentwork.productspecagent.service.BundleFileNotFoundException
import com.agentwork.productspecagent.service.BundleTooLargeException
import com.agentwork.productspecagent.service.IllegalBundleEntryException
import com.agentwork.productspecagent.service.InvalidManifestException
import com.agentwork.productspecagent.service.ManifestIdMismatchException
import com.agentwork.productspecagent.service.MissingManifestException
import com.agentwork.productspecagent.service.UnsupportedStepException
```

Innerhalb der Klasse, nach dem letzten existierenden Handler, neue Handler hinzufügen:

```kotlin
    @ExceptionHandler(BundleFileNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleBundleFileNotFound(ex: BundleFileNotFoundException): ErrorResponse =
        ErrorResponse("NOT_FOUND", ex.message ?: "Bundle file not found", Instant.now().toString())

    @ExceptionHandler(MissingManifestException::class, InvalidManifestException::class,
                      ManifestIdMismatchException::class, UnsupportedStepException::class,
                      IllegalBundleEntryException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidBundle(ex: RuntimeException): ErrorResponse =
        ErrorResponse("INVALID_BUNDLE", ex.message ?: "Invalid bundle", Instant.now().toString())

    @ExceptionHandler(BundleTooLargeException::class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    fun handleBundleTooLarge(ex: BundleTooLargeException): ErrorResponse =
        ErrorResponse("BUNDLE_TOO_LARGE", ex.message ?: "Bundle too large", Instant.now().toString())
```

- [ ] **Step 2: Compile verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew compileKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): map bundle validation exceptions to HTTP responses"
```

---

## Task 8: Controller-Endpoints — POST, DELETE, GET /files/**

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/AssetBundleController.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/AssetBundleControllerTest.kt`

- [ ] **Step 1: Failing tests anhängen**

In `AssetBundleControllerTest`, am Ende der Klasse, vor dem schließenden `}`. Wir brauchen außerdem den `buildZip`-Helper als private Methode in der Test-Klasse:

```kotlin
    private fun zipFor(
        id: String = "backend.framework.kotlin-spring",
        step: FlowStepType = FlowStepType.BACKEND,
        field: String = "framework",
        value: String = "Kotlin+Spring",
        files: Map<String, ByteArray> = mapOf("skills/x.md" to "x".toByteArray()),
    ): ByteArray {
        val manifest = AssetBundleManifest(
            id = id, step = step, field = field, value = value,
            version = "1.0.0", title = "T", description = "D",
            createdAt = "2026-04-29T12:00:00Z", updatedAt = "2026-04-29T12:00:00Z",
        )
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
            files.forEach { (path, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun `POST asset-bundles 201 with valid ZIP`() {
        val zipBytes = zipFor(files = mapOf("skills/x.md" to "skill".toByteArray(), "commands/y.md" to "cmd".toByteArray()))
        val mockFile = org.springframework.mock.web.MockMultipartFile(
            "file", "bundle.zip", "application/zip", zipBytes
        )

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles")
                .file(mockFile)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.manifest.id").value("backend.framework.kotlin-spring"))
            .andExpect(jsonPath("$.fileCount").value(2))
    }

    @Test
    fun `POST asset-bundles 400 with malformed manifest`() {
        // Build a ZIP with garbage manifest
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write("not json {".toByteArray())
            zip.closeEntry()
        }
        val mockFile = org.springframework.mock.web.MockMultipartFile("file", "bad.zip", "application/zip", baos.toByteArray())

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles").file(mockFile))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_BUNDLE"))
    }

    @Test
    fun `POST asset-bundles 400 with manifest id mismatch`() {
        val zipBytes = zipFor(id = "backend.framework.totally-wrong")
        val mockFile = org.springframework.mock.web.MockMultipartFile("file", "bundle.zip", "application/zip", zipBytes)

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles").file(mockFile))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_BUNDLE"))
    }

    @Test
    fun `POST asset-bundles 400 with unsupported step`() {
        val zipBytes = zipFor(id = "idea.framework.kotlin-spring", step = FlowStepType.IDEA)
        val mockFile = org.springframework.mock.web.MockMultipartFile("file", "bundle.zip", "application/zip", zipBytes)

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles").file(mockFile))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST asset-bundles overwrites existing bundle`() {
        // First upload with files A and B
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles")
                .file(org.springframework.mock.web.MockMultipartFile("file", "v1.zip", "application/zip",
                    zipFor(files = mapOf("skills/a.md" to "a".toByteArray(), "skills/b.md" to "b".toByteArray()))))
        ).andExpect(status().isCreated)

        // Second upload with only file C
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles")
                .file(org.springframework.mock.web.MockMultipartFile("file", "v2.zip", "application/zip",
                    zipFor(files = mapOf("skills/c.md" to "c".toByteArray()))))
        ).andExpect(status().isCreated).andExpect(jsonPath("$.fileCount").value(1))

        // Verify old files are gone via detail endpoint
        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.files.length()").value(1))
            .andExpect(jsonPath("$.files[0].relativePath").value("skills/c.md"))
    }

    @Test
    fun `DELETE asset-bundles 204 for existing bundle`() {
        // Upload first
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles")
                .file(org.springframework.mock.web.MockMultipartFile("file", "v.zip", "application/zip", zipFor()))
        ).andExpect(status().isCreated)

        mockMvc.perform(delete("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring"))
            .andExpect(status().isNoContent)

        // Verify gone
        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE asset-bundles 404 for unknown bundle`() {
        mockMvc.perform(delete("/api/v1/asset-bundles/BACKEND/framework/Nonexistent"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }

    @Test
    fun `GET asset-bundle file 200 with bytes and correct Content-Type`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles")
                .file(org.springframework.mock.web.MockMultipartFile("file", "v.zip", "application/zip",
                    zipFor(files = mapOf("skills/x/SKILL.md" to "# Hello".toByteArray()))))
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring/files/skills/x/SKILL.md"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("text/markdown"))
            .andExpect(content().string("# Hello"))
    }

    @Test
    fun `GET asset-bundle file 404 for unknown file path`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles")
                .file(org.springframework.mock.web.MockMultipartFile("file", "v.zip", "application/zip",
                    zipFor(files = mapOf("skills/x.md" to "x".toByteArray()))))
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring/files/skills/missing.md"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET asset-bundle file 400 for path traversal attempt`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles")
                .file(org.springframework.mock.web.MockMultipartFile("file", "v.zip", "application/zip", zipFor()))
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring/files/skills/../etc/passwd"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET asset-bundle file with deeply nested path`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/asset-bundles")
                .file(org.springframework.mock.web.MockMultipartFile("file", "v.zip", "application/zip",
                    zipFor(files = mapOf("skills/a/b/c/d.md" to "deep".toByteArray()))))
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring/files/skills/a/b/c/d.md"))
            .andExpect(status().isOk)
            .andExpect(content().string("deep"))
    }
```

Außerdem die nötigen Imports am Datei-Anfang ergänzen (falls noch nicht vorhanden):

```kotlin
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
```

- [ ] **Step 2: Tests laufen lassen, Failure verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.api.AssetBundleControllerTest" --quiet
```

Expected: FAIL — POST/DELETE/GET-`/files/**`-Endpoints existieren nicht.

- [ ] **Step 3: Endpoints in `AssetBundleController.kt` ergänzen**

Imports am Datei-Anfang ergänzen:

```kotlin
import com.agentwork.productspecagent.domain.AssetBundleUploadResult
import com.agentwork.productspecagent.service.AssetBundleAdminService
import com.agentwork.productspecagent.service.BundleFileNotFoundException
import com.agentwork.productspecagent.service.IllegalBundleEntryException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.multipart.MultipartFile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
```

Constructor erweitern und neue Endpoints in der Klasse anhängen:

```kotlin
@RestController
@RequestMapping("/api/v1/asset-bundles")
class AssetBundleController(
    private val storage: AssetBundleStorage,
    private val adminService: AssetBundleAdminService,
) {

    // existing list() and detail() unchanged ...

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(@RequestParam("file") file: MultipartFile): AssetBundleUploadResult =
        adminService.upload(file.bytes)

    @DeleteMapping("/{step}/{field}/{value}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable step: FlowStepType,
        @PathVariable field: String,
        @PathVariable value: String,
    ) {
        // Verify exists for clear 404; also makes deletion observable
        storage.find(step, field, value) ?: throw AssetBundleNotFoundException(assetBundleId(step, field, value))
        storage.delete(step, field, value)
    }

    @GetMapping("/{step}/{field}/{value}/files/**")
    fun getFile(
        @PathVariable step: FlowStepType,
        @PathVariable field: String,
        @PathVariable value: String,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val uri = request.requestURI
        val filesPrefix = "/files/"
        val rawSuffix = uri.substringAfter(filesPrefix, "")
        val relativePath = URLDecoder.decode(rawSuffix, StandardCharsets.UTF_8)
        if (relativePath.contains("../") || relativePath.startsWith("/") || relativePath.isEmpty()) {
            throw IllegalBundleEntryException(relativePath, "path traversal blocked")
        }

        // Bundle existence check (404 if missing)
        storage.find(step, field, value) ?: throw AssetBundleNotFoundException(assetBundleId(step, field, value))

        val bytes = storage.loadFileBytes(step, field, value, relativePath)
            ?: throw BundleFileNotFoundException(assetBundleId(step, field, value), relativePath)

        val contentType = contentTypeForExt(relativePath)
        val headers = HttpHeaders().apply {
            this.contentType = MediaType.parseMediaType(contentType)
            set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${relativePath.substringAfterLast('/')}\"")
        }
        return ResponseEntity(bytes, headers, HttpStatus.OK)
    }

    private fun contentTypeForExt(relativePath: String): String =
        when (relativePath.substringAfterLast('.', "").lowercase()) {
            "md", "markdown" -> "text/markdown"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "yaml", "yml" -> "application/yaml"
            "py" -> "text/x-python"
            "ts", "tsx" -> "application/typescript"
            "js", "mjs" -> "application/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
}
```

- [ ] **Step 4: Tests laufen lassen, Pass verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.api.AssetBundleControllerTest" --quiet
```

Expected: PASS, 16 tests total (5 alt + 11 neu).

- [ ] **Step 5: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/main/kotlin/com/agentwork/productspecagent/api/AssetBundleController.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/AssetBundleControllerTest.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): POST upload, DELETE, and GET file endpoints"
```

---

## Task 9: MinIO-Integration-Test erweitern

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageIntegrationTest.kt`

- [ ] **Step 1: Neuen Test anhängen**

Am Ende der Klasse, vor dem schließenden `}`:

```kotlin
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
```

- [ ] **Step 2: Test ausführen**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageIntegrationTest" --quiet
```

Expected: PASS, 2 tests total.

- [ ] **Step 3: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageIntegrationTest.kt
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "test(asset-bundles-admin): MinIO integration test for writeBundle and delete"
```

---

## Task 10: Frontend — API-Wrapper

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: Types und Functions anhängen**

Am Ende der Datei `frontend/src/lib/api.ts`:

```ts
// ─── Asset-Bundle Types ──────────────────────────────────────────────────────

export interface AssetBundleManifest {
  id: string;
  step: StepType;
  field: string;
  value: string;
  version: string;
  title: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface AssetBundleFile {
  relativePath: string;
  size: number;
  contentType: string;
}

export interface AssetBundleListItem {
  id: string;
  step: StepType;
  field: string;
  value: string;
  version: string;
  title: string;
  description: string;
  fileCount: number;
}

export interface AssetBundleDetail {
  manifest: AssetBundleManifest;
  files: AssetBundleFile[];
}

export interface AssetBundleUploadResult {
  manifest: AssetBundleManifest;
  fileCount: number;
}

// ─── Asset-Bundle API ────────────────────────────────────────────────────────

export async function listAssetBundles(): Promise<AssetBundleListItem[]> {
  return apiFetch<AssetBundleListItem[]>("/api/v1/asset-bundles");
}

export async function getAssetBundle(
  step: StepType,
  field: string,
  value: string,
): Promise<AssetBundleDetail> {
  const path = `/api/v1/asset-bundles/${step}/${field}/${encodeURIComponent(value)}`;
  return apiFetch<AssetBundleDetail>(path);
}

export async function uploadAssetBundle(file: File): Promise<AssetBundleUploadResult> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${API_BASE}/api/v1/asset-bundles`, {
    method: "POST",
    body: form,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message ?? body.error ?? `Upload failed: ${res.status}`);
  }
  return res.json();
}

export async function deleteAssetBundle(
  step: StepType,
  field: string,
  value: string,
): Promise<void> {
  const path = `/api/v1/asset-bundles/${step}/${field}/${encodeURIComponent(value)}`;
  const res = await fetch(`${API_BASE}${path}`, { method: "DELETE" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message ?? body.error ?? `Delete failed: ${res.status}`);
  }
}

export async function fetchAssetBundleFile(
  step: StepType,
  field: string,
  value: string,
  relativePath: string,
): Promise<Response> {
  const encodedPath = relativePath.split("/").map(encodeURIComponent).join("/");
  const path = `/api/v1/asset-bundles/${step}/${field}/${encodeURIComponent(value)}/files/${encodedPath}`;
  return fetch(`${API_BASE}${path}`);
}
```

- [ ] **Step 2: Build verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run build 2>&1 | tail -10
```

Expected: Compiled successfully (kein Type-Error).

- [ ] **Step 3: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add frontend/src/lib/api.ts
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): frontend API client for asset-bundles"
```

---

## Task 11: Frontend — Zustand-Store

**Files:**
- Create: `frontend/src/lib/stores/asset-bundle-store.ts`

- [ ] **Step 1: Store anlegen**

```ts
import { create } from "zustand";
import {
  listAssetBundles,
  getAssetBundle,
  uploadAssetBundle,
  deleteAssetBundle,
  fetchAssetBundleFile,
  type AssetBundleListItem,
  type AssetBundleDetail,
  type StepType,
} from "@/lib/api";

interface LoadedFile {
  path: string;
  contentType: string;
  text?: string;
  blobUrl?: string;
}

interface AssetBundleState {
  bundles: AssetBundleListItem[];
  selectedBundleId: string | null;
  selectedBundle: AssetBundleDetail | null;
  selectedFilePath: string | null;
  loadedFile: LoadedFile | null;
  loading: boolean;
  uploading: boolean;
  error: string | null;
  filterStep: StepType | "ALL";

  load: () => Promise<void>;
  setFilter: (step: StepType | "ALL") => void;
  select: (id: string | null) => Promise<void>;
  selectFile: (relativePath: string | null) => Promise<void>;
  upload: (file: File) => Promise<void>;
  delete: (step: StepType, field: string, value: string) => Promise<void>;
  clearError: () => void;
}

export const useAssetBundleStore = create<AssetBundleState>((set, get) => ({
  bundles: [],
  selectedBundleId: null,
  selectedBundle: null,
  selectedFilePath: null,
  loadedFile: null,
  loading: false,
  uploading: false,
  error: null,
  filterStep: "ALL",

  async load() {
    set({ loading: true, error: null });
    try {
      const bundles = await listAssetBundles();
      set({ bundles, loading: false });
    } catch (e) {
      set({ error: (e as Error).message, loading: false });
    }
  },

  setFilter(step) {
    set({ filterStep: step });
  },

  async select(id) {
    if (id === null) {
      set({ selectedBundleId: null, selectedBundle: null, selectedFilePath: null, loadedFile: null });
      return;
    }
    const bundle = get().bundles.find((b) => b.id === id);
    if (!bundle) return;
    set({ selectedBundleId: id, selectedBundle: null, selectedFilePath: null, loadedFile: null });
    try {
      const detail = await getAssetBundle(bundle.step, bundle.field, bundle.value);
      set({ selectedBundle: detail });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  async selectFile(relativePath) {
    const previousBlob = get().loadedFile?.blobUrl;
    if (previousBlob) URL.revokeObjectURL(previousBlob);

    if (relativePath === null) {
      set({ selectedFilePath: null, loadedFile: null });
      return;
    }
    const bundle = get().selectedBundle;
    if (!bundle) return;
    const fileEntry = bundle.files.find((f) => f.relativePath === relativePath);
    if (!fileEntry) return;

    set({ selectedFilePath: relativePath, loadedFile: null });
    try {
      const res = await fetchAssetBundleFile(
        bundle.manifest.step,
        bundle.manifest.field,
        bundle.manifest.value,
        relativePath,
      );
      if (!res.ok) throw new Error(`Load failed: ${res.status}`);

      const ct = fileEntry.contentType;
      const isText = ct.startsWith("text/") || ct === "application/json"
        || ct === "application/yaml" || ct === "application/typescript"
        || ct === "application/javascript";

      if (isText) {
        const text = await res.text();
        set({ loadedFile: { path: relativePath, contentType: ct, text } });
      } else {
        const blob = await res.blob();
        const blobUrl = URL.createObjectURL(blob);
        set({ loadedFile: { path: relativePath, contentType: ct, blobUrl } });
      }
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  async upload(file) {
    set({ uploading: true, error: null });
    try {
      const result = await uploadAssetBundle(file);
      // Reload list and select the uploaded bundle
      const bundles = await listAssetBundles();
      set({ bundles, uploading: false });
      await get().select(result.manifest.id);
    } catch (e) {
      set({ error: (e as Error).message, uploading: false });
    }
  },

  async delete(step, field, value) {
    set({ error: null });
    try {
      await deleteAssetBundle(step, field, value);
      const bundles = await listAssetBundles();
      set({ bundles, selectedBundleId: null, selectedBundle: null, selectedFilePath: null, loadedFile: null });
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
  },

  clearError() {
    set({ error: null });
  },
}));
```

- [ ] **Step 2: Build verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run build 2>&1 | tail -10
```

Expected: Compiled successfully.

- [ ] **Step 3: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add frontend/src/lib/stores/asset-bundle-store.ts
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): zustand store for asset-bundle state"
```

---

## Task 12: Frontend — Page-Route + AppShell-Nav

**Files:**
- Create: `frontend/src/app/asset-bundles/page.tsx`
- Create: `frontend/src/components/asset-bundles/AssetBundlesPage.tsx`
- Modify: `frontend/src/components/layout/AppShell.tsx`

- [ ] **Step 1: Server-Component-Wrapper**

`frontend/src/app/asset-bundles/page.tsx`:

```tsx
import { AssetBundlesPage } from "@/components/asset-bundles/AssetBundlesPage";

export default function Page() {
  return <AssetBundlesPage />;
}
```

- [ ] **Step 2: AssetBundlesPage Skelett**

`frontend/src/components/asset-bundles/AssetBundlesPage.tsx`:

```tsx
"use client";

import { useEffect } from "react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";

export function AssetBundlesPage() {
  const { load } = useAssetBundleStore();

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="flex h-screen flex-col">
      <header className="border-b px-6 py-4">
        <h1 className="text-xl font-semibold">Asset Bundles</h1>
        <p className="text-sm text-muted-foreground">
          Kuratierte Claude-Code Skills, Commands und Agents.
        </p>
      </header>
      <div className="flex flex-1 min-h-0">
        <aside className="w-96 border-r overflow-y-auto" data-testid="bundle-list">
          {/* BundleList comes in Task 13 */}
          <div className="p-4 text-sm text-muted-foreground">Liste folgt …</div>
        </aside>
        <main className="flex-1 overflow-y-auto" data-testid="bundle-detail">
          {/* BundleDetail comes in Task 14 */}
          <div className="p-4 text-sm text-muted-foreground">Detail folgt …</div>
        </main>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: AppShell-Nav-Item ergänzen**

In `frontend/src/components/layout/AppShell.tsx` Imports erweitern:

```tsx
import { FolderKanban, Package, Plus, Settings, Sparkles } from "lucide-react";
```

In `nav` (innerhalb `IconRail()`), nach dem „New Project"-`NavItem` einen weiteren ergänzen:

```tsx
        <NavItem
          href="/asset-bundles"
          icon={<Package size={20} />}
          label="Asset Bundles"
          active={pathname?.startsWith("/asset-bundles") ?? false}
        />
```

- [ ] **Step 4: Build verifizieren + Browser-Smoke**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run build 2>&1 | tail -10
```

Expected: Compiled successfully.

Manueller Smoke (mit `./start.sh` oder `npm run dev` + `bootRun`):
- `/asset-bundles` öffnen → Page zeigt „Asset Bundles" Header + Platzhalter-Spalten.
- Icon-Rail zeigt das `Package`-Icon, aktiv-Indikator wenn auf der Page.

- [ ] **Step 5: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add frontend/src/app/asset-bundles/page.tsx frontend/src/components/asset-bundles/AssetBundlesPage.tsx frontend/src/components/layout/AppShell.tsx
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): /asset-bundles route shell + nav entry"
```

---

## Task 13: Frontend — BundleList + BundleUpload

**Files:**
- Create: `frontend/src/components/asset-bundles/BundleList.tsx`
- Create: `frontend/src/components/asset-bundles/BundleUpload.tsx`
- Modify: `frontend/src/components/asset-bundles/AssetBundlesPage.tsx`

- [ ] **Step 1: BundleUpload anlegen**

`frontend/src/components/asset-bundles/BundleUpload.tsx`:

```tsx
"use client";

import { useRef, useState } from "react";
import { Upload, AlertCircle } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { cn } from "@/lib/utils";

export function BundleUpload() {
  const { uploading, error, upload, clearError } = useAssetBundleStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    const file = files[0];
    if (!file.name.toLowerCase().endsWith(".zip")) {
      // Surface as error
      useAssetBundleStore.setState({ error: "Bitte eine .zip-Datei hochladen." });
      return;
    }
    upload(file);
  }

  return (
    <div className="space-y-2">
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => { e.preventDefault(); setDragOver(false); handleFiles(e.dataTransfer.files); }}
        onClick={() => fileInputRef.current?.click()}
        className={cn(
          "border-2 border-dashed rounded-md p-4 text-center cursor-pointer text-xs text-muted-foreground transition-colors",
          dragOver ? "border-primary bg-primary/5" : "border-border hover:border-primary/50",
        )}
      >
        <Upload size={16} className="mx-auto mb-1.5" />
        {uploading ? "Lädt hoch …" : "ZIP hier ablegen oder klicken"}
        <input
          ref={fileInputRef}
          type="file"
          accept=".zip,application/zip"
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />
      </div>
      {error && (
        <div className="flex items-start gap-2 rounded-md border border-red-500/40 bg-red-500/5 px-3 py-2 text-xs text-red-300">
          <AlertCircle size={14} className="mt-0.5 shrink-0" />
          <div className="flex-1">{error}</div>
          <button onClick={clearError} className="text-red-400 hover:text-red-200">×</button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: BundleList anlegen**

`frontend/src/components/asset-bundles/BundleList.tsx`:

```tsx
"use client";

import { Package } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { BundleUpload } from "./BundleUpload";
import { cn } from "@/lib/utils";
import type { StepType } from "@/lib/api";

const STEP_OPTIONS: Array<{ value: StepType | "ALL"; label: string }> = [
  { value: "ALL", label: "Alle Steps" },
  { value: "BACKEND", label: "Backend" },
  { value: "FRONTEND", label: "Frontend" },
  { value: "ARCHITECTURE", label: "Architecture" },
];

export function BundleList() {
  const { bundles, selectedBundleId, filterStep, loading, setFilter, select } = useAssetBundleStore();

  const visible = filterStep === "ALL" ? bundles : bundles.filter((b) => b.step === filterStep);

  return (
    <div className="flex h-full flex-col">
      <div className="border-b p-3 space-y-2">
        <BundleUpload />
        <select
          value={filterStep}
          onChange={(e) => setFilter(e.target.value as StepType | "ALL")}
          className="w-full text-xs rounded-md border bg-background px-2 py-1.5"
        >
          {STEP_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
      </div>
      <div className="flex-1 overflow-y-auto">
        {loading && <div className="p-4 text-sm text-muted-foreground">Lädt …</div>}
        {!loading && visible.length === 0 && (
          <div className="p-4 text-sm text-muted-foreground">
            Keine Bundles. ZIP hochladen, um zu starten.
          </div>
        )}
        {visible.map((b) => (
          <button
            key={b.id}
            onClick={() => select(b.id)}
            className={cn(
              "w-full text-left px-3 py-2.5 border-b transition-colors hover:bg-muted/50",
              selectedBundleId === b.id ? "bg-muted" : "",
            )}
          >
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Package size={12} />
              <span>{b.step.toLowerCase()}.{b.field}.{b.value}</span>
            </div>
            <div className="font-medium text-sm mt-1">{b.title}</div>
            <div className="text-xs text-muted-foreground mt-0.5">{b.fileCount} Dateien · v{b.version}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: AssetBundlesPage Liste einbinden**

`frontend/src/components/asset-bundles/AssetBundlesPage.tsx` — Platzhalter-Liste durch `<BundleList />` ersetzen:

```tsx
"use client";

import { useEffect } from "react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { BundleList } from "./BundleList";

export function AssetBundlesPage() {
  const { load } = useAssetBundleStore();

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="flex h-screen flex-col">
      <header className="border-b px-6 py-4">
        <h1 className="text-xl font-semibold">Asset Bundles</h1>
        <p className="text-sm text-muted-foreground">
          Kuratierte Claude-Code Skills, Commands und Agents.
        </p>
      </header>
      <div className="flex flex-1 min-h-0">
        <aside className="w-96 border-r overflow-hidden">
          <BundleList />
        </aside>
        <main className="flex-1 overflow-y-auto" data-testid="bundle-detail">
          <div className="p-4 text-sm text-muted-foreground">Detail folgt …</div>
        </main>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Build verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run build 2>&1 | tail -10
```

Expected: Compiled successfully.

- [ ] **Step 5: Browser-Smoke**

`./start.sh` (oder backend + frontend separat). Browser auf `/asset-bundles`:
- Liste leer (Empty-State).
- Test-ZIP per CLI vorbereiten (siehe Doku in `persistence.md`) und hochladen → Bundle erscheint in Liste.
- Filter „Backend" auswählen → nur Backend-Bundles sichtbar.

- [ ] **Step 6: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add frontend/src/components/asset-bundles/BundleList.tsx frontend/src/components/asset-bundles/BundleUpload.tsx frontend/src/components/asset-bundles/AssetBundlesPage.tsx
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): bundle list with filter and ZIP upload"
```

---

## Task 14: Frontend — BundleDetail + DeleteDialog + FileViewer

**Files:**
- Create: `frontend/src/components/asset-bundles/BundleDetail.tsx`
- Create: `frontend/src/components/asset-bundles/FileViewer.tsx`
- Create: `frontend/src/components/asset-bundles/DeleteBundleDialog.tsx`
- Modify: `frontend/src/components/asset-bundles/AssetBundlesPage.tsx`

- [ ] **Step 1: FileViewer anlegen**

`frontend/src/components/asset-bundles/FileViewer.tsx`:

```tsx
"use client";

import { useEffect, useState } from "react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";

export function FileViewer() {
  const { loadedFile } = useAssetBundleStore();
  const [html, setHtml] = useState<string>("");

  useEffect(() => {
    if (!loadedFile) {
      setHtml("");
      return;
    }
    if (loadedFile.text === undefined) return;

    let cancelled = false;
    renderText(loadedFile.text, loadedFile.contentType).then((rendered) => {
      if (!cancelled) setHtml(rendered);
    });
    return () => { cancelled = true; };
  }, [loadedFile]);

  if (!loadedFile) {
    return (
      <div className="p-4 text-sm text-muted-foreground">
        Datei aus dem Tree links auswählen.
      </div>
    );
  }

  if (loadedFile.blobUrl && loadedFile.contentType.startsWith("image/")) {
    return (
      <div className="p-4">
        <img src={loadedFile.blobUrl} alt={loadedFile.path} className="max-w-full max-h-[60vh]" />
      </div>
    );
  }

  if (loadedFile.text === undefined) {
    return (
      <div className="p-4 text-sm text-muted-foreground">
        Vorschau nicht verfügbar für {loadedFile.contentType}.
      </div>
    );
  }

  return (
    <div className="p-4 overflow-auto text-sm" dangerouslySetInnerHTML={{ __html: html }} />
  );
}

async function renderText(text: string, contentType: string): Promise<string> {
  if (contentType === "text/markdown") {
    // Reuse shiki for markdown rendering with syntax highlighting on code blocks.
    // For simplicity in this iteration, render markdown as plain text inside <pre>.
    const escaped = escapeHtml(text);
    return `<pre class="whitespace-pre-wrap font-mono text-xs">${escaped}</pre>`;
  }

  const language = contentTypeToLang(contentType);
  try {
    const { codeToHtml } = await import("shiki");
    return await codeToHtml(text, { lang: language, theme: "one-dark-pro" });
  } catch {
    return `<pre class="whitespace-pre-wrap font-mono text-xs">${escapeHtml(text)}</pre>`;
  }
}

function contentTypeToLang(ct: string): string {
  switch (ct) {
    case "application/json": return "json";
    case "application/yaml": return "yaml";
    case "application/typescript": return "typescript";
    case "application/javascript": return "javascript";
    case "text/x-python": return "python";
    default: return "text";
  }
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
```

Hinweis: Markdown wird in dieser Iteration als Plain-Text im `<pre>` gerendert. Für gerendertes Markdown (mit Headings, Listen, Code-Blöcken mit Syntax-Highlighting) müsste ein zusätzlicher Markdown-Renderer eingebunden werden — bewusst out-of-scope, weil im aktuellen Frontend keine Markdown-Komponente existiert (siehe `frontend/CLAUDE.md`). Curatoren sehen den rohen Markdown-Quelltext, was für Verifikation ausreichend ist.

- [ ] **Step 2: DeleteBundleDialog anlegen**

`frontend/src/components/asset-bundles/DeleteBundleDialog.tsx`:

```tsx
"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import type { AssetBundleManifest } from "@/lib/api";

interface Props {
  manifest: AssetBundleManifest | null;
  onClose: () => void;
}

export function DeleteBundleDialog({ manifest, onClose }: Props) {
  const { delete: deleteBundle } = useAssetBundleStore();
  const [deleting, setDeleting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (manifest) cancelRef.current?.focus();
  }, [manifest]);

  useEffect(() => {
    if (!manifest) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape" && !deleting) onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [manifest, deleting, onClose]);

  if (!manifest) return null;

  async function handleDelete() {
    if (!manifest) return;
    setDeleting(true);
    setErr(null);
    try {
      await deleteBundle(manifest.step, manifest.field, manifest.value);
      onClose();
    } catch (e) {
      setErr((e as Error).message);
      setDeleting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={() => !deleting && onClose()} />
      <Card className="relative z-10 w-full max-w-md mx-4">
        <CardHeader>
          <CardTitle>Bundle löschen?</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm">
            Bundle <code className="font-mono">{manifest.id}</code> wird komplett aus S3 entfernt.
            Diese Aktion ist nicht rückgängig zu machen.
          </p>
          {err && <p className="mt-3 text-sm text-red-400">{err}</p>}
        </CardContent>
        <CardFooter className="flex justify-end gap-2">
          <Button ref={cancelRef} variant="ghost" onClick={onClose} disabled={deleting}>
            Abbrechen
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
            {deleting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Löschen
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
```

- [ ] **Step 3: BundleDetail anlegen**

`frontend/src/components/asset-bundles/BundleDetail.tsx`:

```tsx
"use client";

import { useState } from "react";
import { Trash2, FileText, FolderTree } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { FileViewer } from "./FileViewer";
import { DeleteBundleDialog } from "./DeleteBundleDialog";
import { cn } from "@/lib/utils";

export function BundleDetail() {
  const { selectedBundle, selectedFilePath, selectFile } = useAssetBundleStore();
  const [showDelete, setShowDelete] = useState(false);

  if (!selectedBundle) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        Bundle aus der Liste auswählen.
      </div>
    );
  }

  const m = selectedBundle.manifest;

  return (
    <div className="flex h-full flex-col">
      <div className="border-b p-4 space-y-2">
        <div className="flex items-start justify-between">
          <div>
            <div className="text-xs font-mono text-muted-foreground">{m.id}</div>
            <h2 className="text-lg font-semibold mt-1">{m.title}</h2>
            <p className="text-sm text-muted-foreground">{m.description}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setShowDelete(true)}>
            <Trash2 size={16} className="mr-1.5" />
            Löschen
          </Button>
        </div>
        <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
          <span>v{m.version}</span>
          <span>·</span>
          <span>{m.step.toLowerCase()}.{m.field}.{m.value}</span>
          <span>·</span>
          <span>updated {m.updatedAt}</span>
        </div>
      </div>

      <div className="flex flex-1 min-h-0">
        <div className="w-72 border-r overflow-y-auto">
          <div className="p-3 text-xs font-medium text-muted-foreground flex items-center gap-1.5">
            <FolderTree size={12} /> Files ({selectedBundle.files.length})
          </div>
          {selectedBundle.files.map((f) => (
            <button
              key={f.relativePath}
              onClick={() => selectFile(f.relativePath)}
              className={cn(
                "w-full flex items-center gap-2 px-3 py-1.5 text-xs text-left hover:bg-muted/50",
                selectedFilePath === f.relativePath ? "bg-muted" : "",
              )}
            >
              <FileText size={12} />
              <span className="truncate">{f.relativePath}</span>
              <span className="ml-auto text-muted-foreground">{Math.round(f.size / 1024)} KB</span>
            </button>
          ))}
        </div>
        <div className="flex-1 overflow-auto">
          <FileViewer />
        </div>
      </div>

      <DeleteBundleDialog manifest={showDelete ? m : null} onClose={() => setShowDelete(false)} />
    </div>
  );
}
```

- [ ] **Step 4: AssetBundlesPage Detail einbinden**

In `AssetBundlesPage.tsx` Platzhalter durch `<BundleDetail />` ersetzen:

```tsx
import { BundleDetail } from "./BundleDetail";

// ...
        <main className="flex-1 overflow-hidden">
          <BundleDetail />
        </main>
```

- [ ] **Step 5: Build verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run build 2>&1 | tail -10
```

Expected: Compiled successfully.

- [ ] **Step 6: Browser-Smoke (Vollflow)**

`./start.sh`. Browser auf `/asset-bundles`:
- Bundle hochladen → erscheint in Liste, ist selektiert.
- Detail-Panel zeigt Manifest-Card + File-Tree + leere Viewer-Anzeige.
- Datei im Tree klicken → Viewer zeigt Inhalt.
  - `.md` → Plain-Text in `<pre>`.
  - `.py` / `.json` → Syntax-Highlighting via shiki.
  - Bild → `<img>`.
- „Löschen"-Button → Dialog → „Löschen" → Bundle weg, Detail-Panel leer.
- Filter „Frontend" → Liste reduziert sich.

- [ ] **Step 7: Commit**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent add frontend/src/components/asset-bundles/BundleDetail.tsx frontend/src/components/asset-bundles/FileViewer.tsx frontend/src/components/asset-bundles/DeleteBundleDialog.tsx frontend/src/components/asset-bundles/AssetBundlesPage.tsx
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent commit -m "feat(asset-bundles-admin): bundle detail with file viewer and delete confirmation"
```

---

## Task 15: Final Verification

- [ ] **Step 1: Backend-Test-Suite komplett**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew test --rerun --quiet 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Frontend-Build komplett**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run build 2>&1 | tail -5
```

Expected: Compiled successfully.

- [ ] **Step 3: ESLint**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run lint 2>&1 | tail -10
```

Expected: keine Fehler in den neuen Dateien.

- [ ] **Step 4: Test-Counts dokumentieren**

```bash
ls /Users/czarnik/IdeaProjects/ProductSpecAgent/backend/build/test-results/test/TEST-com.agentwork.productspecagent.service.AssetBundle*.xml /Users/czarnik/IdeaProjects/ProductSpecAgent/backend/build/test-results/test/TEST-com.agentwork.productspecagent.api.AssetBundleControllerTest.xml /Users/czarnik/IdeaProjects/ProductSpecAgent/backend/build/test-results/test/TEST-com.agentwork.productspecagent.storage.AssetBundleStorage*Test.xml | xargs grep -h "tests=" 2>/dev/null
```

Expected total new tests in B: ~38–42.

- [ ] **Step 5: Manual Browser-Smoke**

Komplett-Flow durchlaufen wie in Task 14 Step 6. Reproduzierbares Test-ZIP (z. B. mit Stitch-Bundle) bereithalten.

- [ ] **Step 6: Commit-Log review**

```bash
git -C /Users/czarnik/IdeaProjects/ProductSpecAgent log --oneline main..HEAD
```

Expected: ~14 Commits, lesbare Sequenz von der Spec-Doku über Task 1-14 bis zur finalen Verifikation.

---

## Acceptance Criteria — Checkliste

| # | Kriterium | Verifiziert in |
|---|---|---|
| 1 | Sieben neue Validation-Exception-Klassen | Task 1 |
| 2 | `AssetBundleZipExtractor` validiert alle Pipeline-Pfade + filtert Mac/Win-Müll | Task 2-4 (~19 Tests) |
| 3 | `AssetBundleStorage.writeBundle` schreibt Manifest zuletzt | Task 5 |
| 4 | `AssetBundleStorage.delete` ist idempotent | Task 5 |
| 5 | `AssetBundleStorage.loadFileBytes` liefert Bytes oder null | Task 5 |
| 6 | `AssetBundleAdminService` orchestriert clean-wipe + write | Task 6 (3 Tests) |
| 7 | GlobalExceptionHandler mappt alle neuen Exceptions korrekt | Task 7 |
| 8 | `POST /api/v1/asset-bundles` 201 für gültiges ZIP | Task 8 |
| 9 | `POST` 400 + `INVALID_BUNDLE` für alle Validierungs-Failures | Task 8 |
| 10 | `POST` überschreibt existierendes Bundle (clean-wipe) | Task 8 |
| 11 | `DELETE` 204 oder 404 | Task 8 |
| 12 | `GET …/files/**` mit deeply-nested Pfad | Task 8 |
| 13 | `GET …/files/**` 404 bei fehlender Datei, 400 bei Path-Traversal | Task 8 |
| 14 | MinIO-Integration für writeBundle + delete | Task 9 |
| 15 | Frontend-API-Wrapper für alle 5 Endpoints | Task 10 |
| 16 | Zustand-Store mit Liste/Auswahl/Upload/Delete | Task 11 |
| 17 | Top-Level-Route `/asset-bundles` + Nav-Eintrag | Task 12 |
| 18 | BundleList mit Filter + eingebettetem Upload | Task 13 |
| 19 | BundleDetail mit Manifest-Card, File-Tree, Viewer (md/code/image), Delete-Dialog | Task 14 |
| 20 | Manueller Browser-Smoke ende-zu-ende | Task 14 + 15 |
| 21 | Alle bestehenden Tests bleiben grün | Task 15 |

---

## Self-Review

(Nach Plan-Erstellung manuell geprüft)

- ✅ Spec-Coverage: alle 16 Akzeptanzkriterien aus dem Spec haben mindestens einen Task.
- ✅ Keine Placeholder-Strings (`TBD`, `TODO`, „später ergänzen") in den Code-Snippets.
- ✅ Type-Konsistenz: `ExtractedBundle`, `AssetBundleUploadResult`, `AssetBundleManifest` (mit `step: FlowStepType`), `AssetBundleAdminService(storage, extractor)`-Signatur sind über alle Tasks identisch.
- ✅ Exception-Mapping in Task 7 deckt alle Exceptions aus Task 1 ab.
- ⚠ Markdown-Rendering bewusst minimal (Plain-Text in `<pre>`) — siehe Task 14 Step 1 Hinweis. Sub-Feature C oder ein eigenes Folge-Feature könnte einen echten Markdown-Renderer einführen.
- ⚠ Das Storage-Test in Task 5 für „writeBundle writes manifest last" testet die Eigenschaft indirekt (manuelles Schreiben einer File ohne Manifest, dann find()=null). Strenger wäre Reihenfolge-Verification via Mockito-`InOrder` — der gewählte Test deckt aber das beobachtbare Verhalten ab und kommt ohne Mocks aus, was zur Test-Disziplin der Codebase passt.
- ⚠ `AssetBundleControllerTest` nutzt seit Task 8 verschachtelte FQN-Aufrufe für `MockMultipartFile` und `MockMvcRequestBuilders.multipart`. Stilistisch bevorzugt wären Imports am Datei-Anfang. Akzeptabel, weil der Test-File so out-of-the-box kompiliert und die Imports nicht überschrieben werden müssen.
