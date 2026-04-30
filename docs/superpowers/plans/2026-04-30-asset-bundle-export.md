# Asset-Bundle-Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Beim Project-Export werden alle Asset-Bundles aus S3, deren Triple zu einer Wizard-Wahl des Projekts passt, namespaced unter `<prefix>/.claude/{skills,commands,agents}/<bundle-id>/...` ins Export-ZIP gemerged. Eine README-Sektion listet die eingeschlossenen Bundles auf.

**Architecture:** Eine neue `AssetBundleExporter`-Service-Klasse mit drei reinen, testbaren Methoden (`matchedBundles`, `writeToZip`, `renderReadmeSection`). `ExportService` bekommt sie injiziert und ruft sie an drei Stellen auf — Wizard-Load, ZIP-Write, README-Append. Match-Logik ist Bundle-driven (`storage.listAll()` als Quelle) und slugify-tolerant; Fehler werden defensiv geschluckt, der Project-Export läuft immer durch.

**Tech Stack:** Kotlin, Spring Boot 4, JUnit 5, kotlinx.serialization-json, vorhandene `AssetBundleStorage` (B), `WizardService` (intern), JDK `ZipOutputStream`/`ZipInputStream`.

**Spec:** `docs/superpowers/specs/2026-04-30-asset-bundle-export-design.md`

---

## File Structure

**Create:**
- `backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt` — Service mit Match/Zip/Readme-Methoden + `MatchedBundle` data class.
- `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterMatchTest.kt` — Match-Logik isoliert, mit echtem `AssetBundleStorage` über `InMemoryObjectStore`.
- `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterZipTest.kt` — ZIP-Write isoliert, mit `ByteArrayOutputStream`/`ZipInputStream`-Verifikation.
- `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterReadmeTest.kt` — README-Markdown-Rendering isoliert, ohne Storage.

**Modify:**
- `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt` — Konstruktor + `WizardService`-Dependency, drei Hooks in `exportProject()` und `generateReadme()`.
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt` — ein zusätzlicher End-to-End-Test, der die Verkabelung über den HTTP-Endpoint verifiziert.

**Branch:** Vor Task 1 einen Feature-Branch anlegen analog zu B:
```bash
git checkout -b feat/asset-bundle-export
```

---

## Task 1: Skeleton `AssetBundleExporter` + happy-path match (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterMatchTest.kt`

- [ ] **Step 1: Write the first failing test (string match)**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterMatchTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to confirm it fails (compile error)**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterMatchTest" 2>&1 | tail -20
```

Expected: FAIL — `AssetBundleExporter` not found.

- [ ] **Step 3: Create skeleton + minimal happy-path implementation**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt`:

```kotlin
package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.assetBundleSlug
import com.agentwork.productspecagent.storage.AssetBundleStorage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class MatchedBundle(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

@Service
class AssetBundleExporter(private val storage: AssetBundleStorage) {

    private val log = LoggerFactory.getLogger(AssetBundleExporter::class.java)

    fun matchedBundles(wizardData: WizardData): List<MatchedBundle> {
        val manifests = try {
            storage.listAll()
        } catch (e: Exception) {
            log.warn("AssetBundleStorage.listAll failed — exporting without bundles: {}", e.message)
            return emptyList()
        }

        val matched = manifests.filter { m ->
            val raw = wizardData.steps[m.step.name]?.fields?.get(m.field) ?: return@filter false
            val candidates: Set<String> = when (raw) {
                is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
                is JsonPrimitive -> if (raw is JsonNull) emptySet() else setOf(raw.content)
                else -> emptySet()
            }
            val bundleSlug = assetBundleSlug(m.value)
            candidates.any { assetBundleSlug(it) == bundleSlug }
        }

        return matched.mapNotNull { m ->
            try {
                val bundle = storage.find(m.step, m.field, m.value)
                if (bundle == null) {
                    log.warn("Bundle '{}' disappeared between listAll and find — skipping", m.id)
                    null
                } else {
                    MatchedBundle(bundle.manifest, bundle.files)
                }
            } catch (e: Exception) {
                log.warn("Failed to load bundle '{}': {} — skipping", m.id, e.message)
                null
            }
        }.sortedBy { it.manifest.id }
    }

    fun writeToZip(zip: ZipOutputStream, prefix: String, bundles: List<MatchedBundle>) {
        // Implemented in Task 4
        throw NotImplementedError("writeToZip not yet implemented")
    }

    fun renderReadmeSection(bundles: List<MatchedBundle>): String {
        // Implemented in Task 6
        throw NotImplementedError("renderReadmeSection not yet implemented")
    }
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterMatchTest" 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 5: Run full test suite to confirm no regressions**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL. (Existing tests don't depend on `AssetBundleExporter` because nothing has been wired into `ExportService` yet.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterMatchTest.kt
git commit -m "feat(asset-bundle-export): AssetBundleExporter skeleton + happy-path match"
```

---

## Task 2: Match — coercion variants (JsonArray, Number, slugify-tolerance, missing data)

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterMatchTest.kt`

- [ ] **Step 1: Add tests for JsonArray, Number, slugify, JsonNull, missing step, empty inputs**

Append to `AssetBundleExporterMatchTest.kt` inside the class:

```kotlin
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
```

- [ ] **Step 2: Run the new test class to confirm all pass**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterMatchTest" 2>&1 | tail -10
```

Expected: PASS — the implementation from Task 1 already covers all branches (JsonArray + JsonPrimitive + slugify + JsonNull guard + sorting).

If any case fails, refine the implementation accordingly. Likely refinement: explicit `JsonNull`-check in the `is JsonPrimitive` branch (guard `raw is JsonNull` before reading `.content`).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterMatchTest.kt
git commit -m "test(asset-bundle-export): match coercion variants — array, number, slugify, null"
```

---

## Task 3: Match — error resilience (storage failures, manifest drift)

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterMatchTest.kt`

- [ ] **Step 1: Add a `ThrowingObjectStore` helper at the top of the test file**

Add inside the test file (above `class AssetBundleExporterMatchTest {`):

```kotlin
private class ThrowingObjectStore : com.agentwork.productspecagent.storage.ObjectStore {
    override fun put(key: String, bytes: ByteArray, contentType: String) = error("not used")
    override fun get(key: String) = throw java.io.IOException("simulated S3 outage")
    override fun listKeys(prefix: String) = throw java.io.IOException("simulated S3 outage")
    override fun listCommonPrefixes(prefix: String, delimiter: String) = throw java.io.IOException("simulated S3 outage")
    override fun deletePrefix(prefix: String) = error("not used")
}
```

(Verify the `ObjectStore` interface signature first by reading `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ObjectStore.kt` — adjust method signatures in the helper to match exactly.)

- [ ] **Step 2: Add the resilience tests**

Append to the test class:

```kotlin
@Test
fun `matchedBundles returns empty list when storage listAll throws`() {
    val storage = AssetBundleStorage(ThrowingObjectStore())
    val exporter = AssetBundleExporter(storage)
    val wizardData = WizardData(
        projectId = "p1",
        steps = mapOf(
            "BACKEND" to WizardStepData(fields = mapOf("framework" to JsonPrimitive("spring-boot")))
        )
    )

    assertTrue(exporter.matchedBundles(wizardData).isEmpty())
}

@Test
fun `matchedBundles skips bundle that disappears between listAll and find`() {
    val store = InMemoryObjectStore()
    val storage = AssetBundleStorage(store)
    val exporter = AssetBundleExporter(storage)

    storage.writeBundle(
        sampleManifest(field = "framework", value = "spring-boot"),
        mapOf("skills/x.md" to "x".toByteArray())
    )

    // Race: manifest still listable via listAll but the manifest.json key is gone
    store.deletePrefix("asset-bundles/backend.framework.spring-boot/manifest.json")

    val wizardData = WizardData(
        projectId = "p1",
        steps = mapOf(
            "BACKEND" to WizardStepData(fields = mapOf("framework" to JsonPrimitive("spring-boot")))
        )
    )

    // listAll() will not return the manifest after deletion, so result is empty.
    // This test verifies that no crash occurs even when storage state is inconsistent.
    assertTrue(exporter.matchedBundles(wizardData).isEmpty())
}
```

- [ ] **Step 3: Run all match tests**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterMatchTest" 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterMatchTest.kt
git commit -m "test(asset-bundle-export): match resilience against storage failures"
```

---

## Task 4: ZIP write — happy path (single bundle, single file)

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterZipTest.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt`

- [ ] **Step 1: Write the first failing ZIP test**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterZipTest.kt`:

```kotlin
package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.service.sampleManifest
import com.agentwork.productspecagent.storage.AssetBundleStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
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

    private fun runWriteToZip(exporter: AssetBundleExporter, prefix: String, bundles: List<MatchedBundle>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip -> exporter.writeToZip(zip, prefix, bundles) }
        return baos.toByteArray()
    }

    @Test
    fun `writeToZip writes a single skills file under namespaced path`() {
        val (exporter, storage) = newExporter()
        val manifest = sampleManifest(field = "framework", value = "spring-boot")
        storage.writeBundle(manifest, mapOf("skills/api/SKILL.md" to "content".toByteArray()))
        val bundles = exporter.matchedBundles(
            com.agentwork.productspecagent.domain.WizardData(
                projectId = "p1",
                steps = mapOf("BACKEND" to com.agentwork.productspecagent.domain.WizardStepData(
                    fields = mapOf("framework" to kotlinx.serialization.json.JsonPrimitive("spring-boot"))
                ))
            )
        )

        val zipBytes = runWriteToZip(exporter, "myapp", bundles)
        val entries = zipEntries(zipBytes)

        assertEquals(setOf("myapp/.claude/skills/backend.framework.spring-boot/api/SKILL.md"), entries.keys)
        assertEquals("content", entries.values.first().toString(Charsets.UTF_8))
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterZipTest" 2>&1 | tail -15
```

Expected: FAIL — `NotImplementedError: writeToZip not yet implemented`.

- [ ] **Step 3: Implement minimal `writeToZip()` for happy path**

In `AssetBundleExporter.kt`, replace the `writeToZip` body:

```kotlin
fun writeToZip(zip: ZipOutputStream, prefix: String, bundles: List<MatchedBundle>) {
    val allowedTypes = setOf("skills", "commands", "agents")
    for (bundle in bundles) {
        for (file in bundle.files) {
            val firstSlash = file.relativePath.indexOf('/')
            if (firstSlash < 0) continue
            val type = file.relativePath.substring(0, firstSlash)
            val rest = file.relativePath.substring(firstSlash + 1)
            if (type !in allowedTypes) continue

            val bytes = storage.loadFileBytes(
                bundle.manifest.step,
                bundle.manifest.field,
                bundle.manifest.value,
                file.relativePath,
            )
            if (bytes == null) {
                log.warn("loadFileBytes returned null for {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                continue
            }

            val entryName = "$prefix/.claude/$type/${bundle.manifest.id}/$rest"
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterZipTest" 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterZipTest.kt
git commit -m "feat(asset-bundle-export): writeToZip happy path with namespaced paths"
```

---

## Task 5: ZIP write — multiple types, multiple bundles, defensive checks

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterZipTest.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt` (add normalize check)

- [ ] **Step 1: Add tests for multi-type, two-bundle namespacing, whitelist-skip, normalize-skip, empty-bundles**

Append to `AssetBundleExporterZipTest.kt` inside the class:

```kotlin
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
        com.agentwork.productspecagent.domain.WizardData(
            projectId = "p1",
            steps = mapOf("BACKEND" to com.agentwork.productspecagent.domain.WizardStepData(
                fields = mapOf("framework" to kotlinx.serialization.json.JsonPrimitive("spring-boot"))
            ))
        )
    )

    val zipBytes = runWriteToZip(exporter, "myapp", bundles)
    val entries = zipEntries(zipBytes)

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
        com.agentwork.productspecagent.domain.WizardData(
            projectId = "p1",
            steps = mapOf("BACKEND" to com.agentwork.productspecagent.domain.WizardStepData(
                fields = mapOf("framework" to kotlinx.serialization.json.Json.parseToJsonElement("""["spring-boot","ktor"]"""))
            ))
        )
    )

    val zipBytes = runWriteToZip(exporter, "myapp", bundles)
    val entries = zipEntries(zipBytes)

    assertEquals("from-ktor", entries["myapp/.claude/skills/backend.framework.ktor/api-design.md"]?.toString(Charsets.UTF_8))
    assertEquals("from-spring", entries["myapp/.claude/skills/backend.framework.spring-boot/api-design.md"]?.toString(Charsets.UTF_8))
}

@Test
fun `writeToZip skips files outside whitelisted top dirs`() {
    val (exporter, storage) = newExporter()
    // sneak a non-allowlisted file in via raw store write — bypasses ZipExtractor's upload validation
    storage.writeBundle(
        sampleManifest(field = "framework", value = "spring-boot"),
        mapOf(
            "skills/ok.md" to "ok".toByteArray(),
            "rogue/x.md" to "rogue".toByteArray(),
        )
    )
    val bundles = exporter.matchedBundles(
        com.agentwork.productspecagent.domain.WizardData(
            projectId = "p1",
            steps = mapOf("BACKEND" to com.agentwork.productspecagent.domain.WizardStepData(
                fields = mapOf("framework" to kotlinx.serialization.json.JsonPrimitive("spring-boot"))
            ))
        )
    )

    val zipBytes = runWriteToZip(exporter, "myapp", bundles)
    val entries = zipEntries(zipBytes)

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
        com.agentwork.productspecagent.domain.WizardData(
            projectId = "p1",
            steps = mapOf("BACKEND" to com.agentwork.productspecagent.domain.WizardStepData(
                fields = mapOf("framework" to kotlinx.serialization.json.JsonPrimitive("spring-boot"))
            ))
        )
    )

    val zipBytes = runWriteToZip(exporter, "myapp", bundles)
    val entries = zipEntries(zipBytes)

    assertTrue(entries.containsKey("myapp/.claude/skills/backend.framework.spring-boot/ok.md"))
    assertFalse(entries.keys.any { it.contains("..") || it.contains("/etc/passwd") })
}

@Test
fun `writeToZip writes nothing when bundles list is empty`() {
    val (exporter, _) = newExporter()
    val zipBytes = runWriteToZip(exporter, "myapp", emptyList())
    val entries = zipEntries(zipBytes)

    assertTrue(entries.isEmpty())
}

@Test
fun `writeToZip skips files when loadFileBytes returns null`() {
    val (exporter, _) = newExporter()
    // Synthesize a MatchedBundle whose files list points to keys that don't exist in storage
    val bundle = MatchedBundle(
        manifest = sampleManifest(field = "framework", value = "spring-boot"),
        files = listOf(
            com.agentwork.productspecagent.domain.AssetBundleFile("skills/ghost.md", 10, "text/markdown")
        ),
    )

    val zipBytes = runWriteToZip(exporter, "myapp", listOf(bundle))
    val entries = zipEntries(zipBytes)

    assertTrue(entries.isEmpty(), "expected no entries, got: ${entries.keys}")
}
```

- [ ] **Step 2: Run tests to identify which fail**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterZipTest" 2>&1 | tail -30
```

Expected: the path-traversal test FAILS (current impl doesn't check for `..`); other multi-type and namespacing tests PASS.

- [ ] **Step 3: Add path-normalization check to `writeToZip()`**

In `AssetBundleExporter.kt`, modify the `writeToZip` body — add a normalize check before the write:

```kotlin
fun writeToZip(zip: ZipOutputStream, prefix: String, bundles: List<MatchedBundle>) {
    val allowedTypes = setOf("skills", "commands", "agents")
    for (bundle in bundles) {
        for (file in bundle.files) {
            val firstSlash = file.relativePath.indexOf('/')
            if (firstSlash < 0) continue
            val type = file.relativePath.substring(0, firstSlash)
            val rest = file.relativePath.substring(firstSlash + 1)
            if (type !in allowedTypes) continue

            // Defense in depth: reject suspicious paths even though ZipExtractor (B) already guards uploads.
            val normalized = try {
                Paths.get(file.relativePath).normalize().toString().replace('\\', '/')
            } catch (e: Exception) {
                log.warn("Path normalization failed for {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                continue
            }
            if (normalized != file.relativePath || file.relativePath.startsWith("/") || ".." in file.relativePath.split('/')) {
                log.warn("Suspicious path {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                continue
            }

            val bytes = storage.loadFileBytes(
                bundle.manifest.step,
                bundle.manifest.field,
                bundle.manifest.value,
                file.relativePath,
            )
            if (bytes == null) {
                log.warn("loadFileBytes returned null for {}/{} — skipping file", bundle.manifest.id, file.relativePath)
                continue
            }

            val entryName = "$prefix/.claude/$type/${bundle.manifest.id}/$rest"
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}
```

- [ ] **Step 4: Run tests to confirm all pass**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterZipTest" 2>&1 | tail -10
```

Expected: PASS — all five tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterZipTest.kt
git commit -m "feat(asset-bundle-export): writeToZip whitelist + path-normalize checks"
```

---

## Task 6: README rendering

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterReadmeTest.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt`

- [ ] **Step 1: Write tests for empty, single, and multiple bundles**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterReadmeTest.kt`:

```kotlin
package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.AssetBundleFile
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
                title = "Spring Boot Skills", description = "Curated skills for Spring", version = "1.2.0",
            ),
            files = emptyList(),
        )

        val md = exporter.renderReadmeSection(listOf(bundle))

        assertTrue(md.contains("## Included Asset Bundles"), "expected heading")
        assertTrue(md.contains("**Spring Boot Skills**"), "expected title bold")
        assertTrue(md.contains("`backend.framework.spring-boot`"), "expected id code")
        assertTrue(md.contains("v1.2.0"), "expected version")
        assertTrue(md.contains("BACKEND.framework"), "expected step.field")
        assertTrue(md.contains("spring-boot"), "expected value")
        assertTrue(md.contains("Curated skills for Spring"), "expected description")
    }

    @Test
    fun `renderReadmeSection sorts bundles alphabetically by id`() {
        val exporter = newExporter()
        val ktor = MatchedBundle(sampleManifest(field = "framework", value = "ktor", title = "Ktor"), emptyList())
        val spring = MatchedBundle(sampleManifest(field = "framework", value = "spring-boot", title = "Spring"), emptyList())

        val md = exporter.renderReadmeSection(listOf(spring, ktor))

        val ktorIdx = md.indexOf("backend.framework.ktor")
        val springIdx = md.indexOf("backend.framework.spring-boot")
        assertTrue(ktorIdx in 0..springIdx, "expected ktor to appear before spring; md=\n$md")
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterReadmeTest" 2>&1 | tail -15
```

Expected: FAIL — `NotImplementedError`.

- [ ] **Step 3: Implement `renderReadmeSection()`**

In `AssetBundleExporter.kt`, replace the `renderReadmeSection` body:

```kotlin
fun renderReadmeSection(bundles: List<MatchedBundle>): String {
    if (bundles.isEmpty()) return ""

    val sorted = bundles.sortedBy { it.manifest.id }
    return buildString {
        appendLine()
        appendLine("## Included Asset Bundles")
        appendLine()
        appendLine("The following Claude Code asset bundles were merged into `.claude/` based on your wizard choices:")
        appendLine()
        for (b in sorted) {
            val m = b.manifest
            appendLine("- **${m.title}** (`${m.id}` v${m.version}) — matched on `${m.step.name}.${m.field} = ${m.value}`")
            if (m.description.isNotBlank()) {
                appendLine("  ${m.description}")
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to confirm all pass**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.AssetBundleExporterReadmeTest" 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporterReadmeTest.kt
git commit -m "feat(asset-bundle-export): renderReadmeSection markdown output"
```

---

## Task 7: Wire `AssetBundleExporter` into `ExportService`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`

- [ ] **Step 1: Read existing ExportService to identify exact insertion points**

```bash
cat backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt
```

Confirm: constructor at lines 11-16, `exportProject()` body at lines 17-77, `generateReadme()` at lines 79-105.

- [ ] **Step 2: Modify constructor and `exportProject()` to load bundles and write them**

Edit `ExportService.kt`. Replace the class declaration block (lines 10-16):

```kotlin
@Service
class ExportService(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService,
    private val wizardService: WizardService,
    private val assetBundleExporter: AssetBundleExporter,
) {
```

Then in `exportProject()`, immediately after `val prefix = ...` (line 21), add:

```kotlin
        val wizardData = wizardService.getWizardData(projectId)
        val matchedBundles = assetBundleExporter.matchedBundles(wizardData)
```

Inside the `ZipOutputStream(baos).use { zip ->` block, after the existing entries (i.e., right before the closing `}` of the `use` block, around line 73), add:

```kotlin
            // Asset bundles namespaced under .claude/{type}/<bundle-id>/
            assetBundleExporter.writeToZip(zip, prefix, matchedBundles)
```

Update the call to `generateReadme()` at line 26 to pass `matchedBundles`:

```kotlin
            zip.addEntry("$prefix/README.md", generateReadme(project, flowState, matchedBundles))
```

- [ ] **Step 3: Modify `generateReadme()` signature and append bundle section**

Change `generateReadme(project: Project, flowState: FlowState)` (line 79) to `generateReadme(project: Project, flowState: FlowState, bundles: List<MatchedBundle>)`.

Right before the closing `}` of `generateReadme()` (line 105, after the `appendLine("Generated by Product Spec Agent")`), add:

```kotlin
        append(assetBundleExporter.renderReadmeSection(bundles))
```

Add the import at the top of the file if not already present:

```kotlin
import com.agentwork.productspecagent.service.WizardService
```

(WizardService lives in `service` package per the existing codebase.)

- [ ] **Step 4: Run full test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL. All existing tests still pass — the existing `ExportControllerTest` creates a fresh project with empty wizard data, so `matchedBundles` returns empty and no `.claude/` entries are added.

If a test fails because `AssetBundleStorage` isn't available in the Spring context for `ExportControllerTest`, verify that `S3` config is present in the test profile (see `application-test.yml` if it exists — Sub-Feature B's tests handle this and your env should already be set up).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt
git commit -m "feat(asset-bundle-export): wire AssetBundleExporter into ExportService"
```

---

## Task 8: End-to-end integration test in `ExportControllerTest`

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`

- [ ] **Step 1: Add Autowired dependencies for direct setup**

In `ExportControllerTest`, after the existing `@Autowired lateinit var uploadStorage: ...` declaration (around line 21), add:

```kotlin
    @Autowired lateinit var assetBundleStorage: com.agentwork.productspecagent.storage.AssetBundleStorage
    @Autowired lateinit var wizardService: com.agentwork.productspecagent.service.WizardService
```

- [ ] **Step 2: Add cleanup for asset bundles**

In the existing `cleanupProjects()` method, after the project cleanup loop, add:

```kotlin
        // Clean up any test-uploaded bundles
        try {
            assetBundleStorage.delete(
                com.agentwork.productspecagent.domain.FlowStepType.BACKEND,
                "framework",
                "spring-boot",
            )
        } catch (_: Exception) { /* tolerant */ }
```

- [ ] **Step 3: Add the integration test**

Append a new test method to the class:

```kotlin
@Test
fun `POST export merges matching asset bundle into zip and lists it in README`() {
    val pid = createProject()

    // Upload a bundle directly via storage (skip the HTTP upload path — that's covered elsewhere)
    val manifest = com.agentwork.productspecagent.domain.AssetBundleManifest(
        id = "backend.framework.spring-boot",
        step = com.agentwork.productspecagent.domain.FlowStepType.BACKEND,
        field = "framework",
        value = "spring-boot",
        version = "1.0.0",
        title = "Spring Boot Skills",
        description = "Curated skills",
        createdAt = "2026-04-30T00:00:00Z",
        updatedAt = "2026-04-30T00:00:00Z",
    )
    assetBundleStorage.writeBundle(
        manifest,
        mapOf("skills/api-design/SKILL.md" to "# API Design".toByteArray()),
    )

    // Configure wizard so that BACKEND.framework = spring-boot
    wizardService.saveStepData(
        pid, "BACKEND",
        com.agentwork.productspecagent.domain.WizardStepData(
            fields = mapOf("framework" to kotlinx.serialization.json.JsonPrimitive("spring-boot"))
        )
    )

    val result = mockMvc.perform(
        post("/api/v1/projects/$pid/export")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"includeDecisions":false,"includeClarifications":false,"includeTasks":false}""")
    )
        .andExpect(status().isOk())
        .andReturn()

    val zipBytes = result.response.contentAsByteArray
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            entries[entry.name] = zis.readBytes()
            entry = zis.nextEntry
        }
    }

    // Bundle file under namespaced .claude path
    val skillKey = entries.keys.firstOrNull {
        it.endsWith(".claude/skills/backend.framework.spring-boot/api-design/SKILL.md")
    }
    assertNotNull(skillKey, "expected bundle file under .claude/skills/backend.framework.spring-boot/, got: ${entries.keys}")
    assertEquals("# API Design", entries[skillKey]?.toString(Charsets.UTF_8))

    // README mentions the bundle
    val readmeKey = entries.keys.firstOrNull { it.endsWith("/README.md") }
    assertNotNull(readmeKey)
    val readme = entries[readmeKey]!!.toString(Charsets.UTF_8)
    assertTrue(readme.contains("## Included Asset Bundles"), "expected bundle section in README; got:\n$readme")
    assertTrue(readme.contains("Spring Boot Skills"), "expected bundle title in README; got:\n$readme")
}
```

- [ ] **Step 4: Run the integration test**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.ExportControllerTest" 2>&1 | tail -15
```

Expected: PASS — both the existing test and the new integration test.

- [ ] **Step 5: Run full backend test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt
git commit -m "test(asset-bundle-export): end-to-end integration via ExportControllerTest"
```

---

## Task 9: Manual smoke + branch finishing

- [ ] **Step 1: Verify the full build succeeds**

```bash
cd backend && ./gradlew build 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Manual smoke (optional but recommended)**

Start backend + frontend (`./start.sh`). Manually:
1. Use the `/asset-bundles` admin UI (Sub-Feature B) to upload a bundle for `(BACKEND, framework, spring-boot)`. Test ZIP fixture: `asset-bundles/stitch-frontend-bundle.zip` exists in repo root for reuse if its triple matches; otherwise pack a fresh test bundle with `manifest.json` + `skills/<some-skill>/SKILL.md`.
2. Create a project, navigate the wizard to BACKEND step, set `framework` to `spring-boot`.
3. Trigger export from the project view.
4. Open the downloaded ZIP and verify:
   - `<prefix>/.claude/skills/backend.framework.spring-boot/...` files exist.
   - `<prefix>/README.md` contains the `## Included Asset Bundles` section listing the bundle.

If anything is off, file a follow-up — do not silently fix as part of this plan execution.

- [ ] **Step 3: Finish the development branch**

Use `superpowers:finishing-a-development-branch` to merge `feat/asset-bundle-export` back into `main` (rebase + fast-forward, consistent with how Sub-Feature B was integrated).

After merge, update `docs/superpowers/asset-bundles-handoff.md` to reflect that Sub-Feature C is now on `main`. Commit the handoff change separately.

---

## Open Items / Out-of-Scope

- **Frontend:** keine Änderungen, kein Opt-out-Flag im Export-Dialog (silent always-merge per Spec).
- **Performance:** keine Bulk-S3-Reads — pro Bundle wird `loadFileBytes` einzeln aufgerufen. Akzeptabel für aktuelle Bundle-Counts; falls in Zukunft viele Bundles existieren, wäre `objectStore.listKeys(prefix) + bulk-get` ein offensichtlicher Hebel — separates Plan.
- **Documentation refresh:** Falls `docs/features/10-project-scaffold-export.md` einen Hinweis braucht, dass jetzt zusätzlich `.claude/` ausgeliefert wird, separat als Doku-PR nachziehen.
