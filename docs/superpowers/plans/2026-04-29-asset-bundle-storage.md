# Asset-Bundle-Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backend-Foundation für kuratierte Asset-Bundles (Claude-Code-Skills/Commands/Agents) in S3: Domain-Modell, Storage-Klasse, read-only REST-API.

**Architecture:** Neue Storage-Klasse `AssetBundleStorage` baut auf bestehender `ObjectStore`-Abstraktion (Feature 31) auf, liest Bundles aus dem Prefix `asset-bundles/` im bestehenden Bucket `productspec-data`. Bundle-Identität ist ein Triple `(FlowStepType, field, value)`, jedes Bundle ist polymorph (skills/commands/agents in einem Folder). Read-only Controller liefert Liste und Detail. Kein Schreib-Pfad in dieser Sub-Feature — Upload kommt in Sub-Feature B.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, kotlinx.serialization, AWS SDK v2 (S3Client via existierendem `ObjectStore`), JUnit 5, Testcontainers (MinIO), MockMvc.

**Spec:** `docs/superpowers/specs/2026-04-29-asset-bundle-storage-design.md`

---

## File Structure

| Datei | Verantwortung |
|---|---|
| `backend/src/main/kotlin/com/agentwork/productspecagent/domain/AssetBundle.kt` | Datentypen `AssetBundleManifest`, `AssetBundleFile`, `AssetBundle` + Helper `assetBundleId()` |
| `backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt` | Liest Bundles aus `asset-bundles/`-Prefix; `listAll()`, `find()` |
| `backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleNotFoundException.kt` | Exception für 404-Mapping |
| `backend/src/main/kotlin/com/agentwork/productspecagent/api/AssetBundleController.kt` | REST-Endpoints + Response-DTOs `AssetBundleListItem`, `AssetBundleDetail` |
| `backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt` | **Modify** — Handler für `AssetBundleNotFoundException` ergänzen |
| `backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt` | Unit-Tests gegen `InMemoryObjectStore` |
| `backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageIntegrationTest.kt` | Integration-Test gegen Testcontainers-MinIO |
| `backend/src/test/kotlin/com/agentwork/productspecagent/api/AssetBundleControllerTest.kt` | Controller-Tests (`@SpringBootTest`) |
| `docs/architecture/persistence.md` | **Modify** — Abschnitt zu Asset-Bundle-Layout ergänzen |

**Pattern-Notiz:** Bestehende Storage-Tests (z. B. `DecisionStorageTest`) extenden `S3TestSupport` und laufen gegen MinIO. Diese Sub-Feature weicht **bewusst** ab und nutzt `InMemoryObjectStore` für Unit-Tests (schneller). Ein einzelner Integration-Test gegen MinIO sichert das echte S3-Verhalten ab.

---

## Pre-Flight

- [ ] **Branch erstellen**

```bash
git checkout -b feat/asset-bundle-storage
```

- [ ] **Verify clean state**

```bash
cd backend && ./gradlew test --quiet
```

Expected: alle Tests grün, Baseline.

---

## Task 1: Domain-Typen + ID-Helper

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/AssetBundle.kt`

- [ ] **Step 1: Datei anlegen**

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class AssetBundleManifest(
    val id: String,
    val step: FlowStepType,
    val field: String,
    val value: String,
    val version: String,
    val title: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AssetBundleFile(
    val relativePath: String,
    val size: Long,
    val contentType: String,
)

@Serializable
data class AssetBundle(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

/** Berechnet die deterministische Bundle-ID aus Triple (step, field, value). */
fun assetBundleId(step: FlowStepType, field: String, value: String): String =
    "${step.name.lowercase()}.$field.${assetBundleSlug(value)}"

/** Slugify: lowercase, [^a-z0-9]+ → "-", trim "-" an den Rändern. */
fun assetBundleSlug(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
```

- [ ] **Step 2: Compile prüfen**

```bash
cd backend && ./gradlew compileKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/AssetBundle.kt
git commit -m "feat(asset-bundles): add AssetBundle domain types and id helper"
```

---

## Task 2: AssetBundleStorage.listAll() — Happy Path

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt`

- [ ] **Step 1: Failing test schreiben**

`backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt`:

```kotlin
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
}
```

- [ ] **Step 2: Test ausführen, Failure verifizieren**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageTest" --quiet
```

Expected: FAIL — `AssetBundleStorage` nicht gefunden.

- [ ] **Step 3: Storage implementieren**

`backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.AssetBundle
import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.assetBundleId
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AssetBundleStorage(private val objectStore: ObjectStore) {

    private val log = LoggerFactory.getLogger(AssetBundleStorage::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val rootPrefix = "asset-bundles/"

    fun listAll(): List<AssetBundleManifest> {
        val folders = objectStore.listCommonPrefixes(rootPrefix, "/")
        return folders.mapNotNull { folder -> readManifest(folder) }
    }

    private fun readManifest(folder: String): AssetBundleManifest? {
        val key = "$rootPrefix$folder/manifest.json"
        val bytes = objectStore.get(key) ?: return null
        return json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
    }
}
```

- [ ] **Step 4: Tests ausführen, Pass verifizieren**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageTest" --quiet
```

Expected: PASS, 2 Tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt
git commit -m "feat(asset-bundles): AssetBundleStorage.listAll happy path"
```

---

## Task 3: AssetBundleStorage.listAll() — Error Handling

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt`

- [ ] **Step 1: Failing tests ergänzen**

In `AssetBundleStorageTest` (am Ende der Klasse anhängen):

```kotlin
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
```

- [ ] **Step 2: Tests ausführen, Failure verifizieren**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageTest" --quiet
```

Expected: FAIL — Tests werfen Exception statt zu skippen.

- [ ] **Step 3: Error Handling im Storage einbauen**

`backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt` — `readManifest` ersetzen durch:

```kotlin
    private fun readManifest(folder: String): AssetBundleManifest? {
        val key = "$rootPrefix$folder/manifest.json"
        val bytes = objectStore.get(key)
        if (bytes == null) {
            log.warn("Asset bundle folder '{}' has no manifest.json — skipping", folder)
            return null
        }
        return try {
            json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            log.warn("Asset bundle '{}' has invalid manifest.json: {} — skipping", folder, e.message)
            null
        }
    }
```

- [ ] **Step 4: Tests ausführen, Pass verifizieren**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageTest" --quiet
```

Expected: PASS, 5 Tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt
git commit -m "feat(asset-bundles): listAll skips invalid manifests with warning"
```

---

## Task 4: AssetBundleStorage.find() mit File-Listing

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt`

- [ ] **Step 1: Failing tests ergänzen**

In `AssetBundleStorageTest` anhängen:

```kotlin
    @Test
    fun `find returns null for unknown bundle`() {
        val storage = newStorage(newStore())
        val result = storage.find(FlowStepType.BACKEND, "framework", "Kotlin+Spring")
        assertNull(result)
    }

    @Test
    fun `find returns bundle with files, manifest_json filtered out`() {
        val store = newStore()
        store.putBundle(
            manifest(id = "backend.framework.kotlin-spring"),
            files = mapOf(
                "skills/spring-testing/SKILL.md" to "skill content".toByteArray(),
                "commands/gradle-build.md" to "command content".toByteArray(),
                "agents/spring-debug.md" to "agent content".toByteArray(),
            )
        )

        val bundle = newStorage(store).find(FlowStepType.BACKEND, "framework", "Kotlin+Spring")

        assertNotNull(bundle)
        assertEquals("backend.framework.kotlin-spring", bundle!!.manifest.id)
        assertEquals(3, bundle.files.size)
        assertTrue(bundle.files.none { it.relativePath == "manifest.json" })
        assertEquals(setOf("skills/spring-testing/SKILL.md", "commands/gradle-build.md", "agents/spring-debug.md"),
                     bundle.files.map { it.relativePath }.toSet())
    }

    @Test
    fun `find returns relative paths, not full S3 keys`() {
        val store = newStore()
        store.putBundle(
            manifest(id = "frontend.framework.stitch", step = FlowStepType.FRONTEND, value = "Stitch"),
            files = mapOf("skills/stitch-components/SKILL.md" to "x".toByteArray())
        )

        val bundle = newStorage(store).find(FlowStepType.FRONTEND, "framework", "Stitch")!!

        assertEquals(1, bundle.files.size)
        assertEquals("skills/stitch-components/SKILL.md", bundle.files[0].relativePath)
    }

    @Test
    fun `find derives content type from file extension`() {
        val store = newStore()
        store.putBundle(
            manifest(),
            files = mapOf(
                "skills/x/SKILL.md" to "md".toByteArray(),
                "skills/x/example.py" to "py".toByteArray(),
                "skills/x/screenshot.png" to "img".toByteArray(),
                "skills/x/data.json" to "json".toByteArray(),
                "skills/x/unknown.xyz" to "?".toByteArray(),
            )
        )

        val bundle = newStorage(store).find(FlowStepType.BACKEND, "framework", "Kotlin+Spring")!!
        val byPath = bundle.files.associateBy { it.relativePath }

        assertEquals("text/markdown", byPath["skills/x/SKILL.md"]?.contentType)
        assertEquals("text/x-python", byPath["skills/x/example.py"]?.contentType)
        assertEquals("image/png", byPath["skills/x/screenshot.png"]?.contentType)
        assertEquals("application/json", byPath["skills/x/data.json"]?.contentType)
        assertEquals("application/octet-stream", byPath["skills/x/unknown.xyz"]?.contentType)
    }

    @Test
    fun `assetBundleId computes deterministic id from triple`() {
        assertEquals("backend.framework.kotlin-spring",
                     com.agentwork.productspecagent.domain.assetBundleId(FlowStepType.BACKEND, "framework", "Kotlin+Spring"))
        assertEquals("frontend.framework.stitch",
                     com.agentwork.productspecagent.domain.assetBundleId(FlowStepType.FRONTEND, "framework", "Stitch"))
        assertEquals("architecture.architecture.microservices",
                     com.agentwork.productspecagent.domain.assetBundleId(FlowStepType.ARCHITECTURE, "architecture", "Microservices"))
    }
```

- [ ] **Step 2: Tests ausführen, Failure verifizieren**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageTest" --quiet
```

Expected: FAIL — `find` Methode existiert nicht.

- [ ] **Step 3: find() implementieren**

In `backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt` ergänzen (vor dem schließenden `}` der Klasse):

```kotlin
    fun find(step: FlowStepType, field: String, value: String): AssetBundle? {
        val id = assetBundleId(step, field, value)
        val bundlePrefix = "$rootPrefix$id/"
        val manifest = readManifestByKey("${bundlePrefix}manifest.json") ?: return null

        val files = objectStore.listKeys(bundlePrefix)
            .filter { it != "${bundlePrefix}manifest.json" }
            .map { fullKey ->
                val relativePath = fullKey.removePrefix(bundlePrefix)
                AssetBundleFile(
                    relativePath = relativePath,
                    size = (objectStore.get(fullKey)?.size ?: 0).toLong(),
                    contentType = contentTypeFor(relativePath),
                )
            }

        return AssetBundle(manifest = manifest, files = files)
    }

    private fun readManifestByKey(key: String): AssetBundleManifest? {
        val bytes = objectStore.get(key) ?: return null
        return try {
            json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            log.warn("Manifest at '{}' is invalid: {}", key, e.message)
            null
        }
    }

    private fun contentTypeFor(relativePath: String): String =
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
```

`readManifest(folder)` aus Task 3 kann als Wrapper bestehen bleiben. Refactor zur Konsolidierung:

```kotlin
    private fun readManifest(folder: String): AssetBundleManifest? =
        readManifestByKey("$rootPrefix$folder/manifest.json")
```

(Ersetzt die bisherige Implementation von `readManifest`.)

`assetBundleId` Import in AssetBundleStorage.kt prüfen — sollte bereits vorhanden sein durch:
```kotlin
import com.agentwork.productspecagent.domain.assetBundleId
```

- [ ] **Step 4: Tests ausführen, Pass verifizieren**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageTest" --quiet
```

Expected: PASS, 10 Tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageTest.kt
git commit -m "feat(asset-bundles): AssetBundleStorage.find with file listing and content-type detection"
```

---

## Task 5: Integration-Test gegen MinIO

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageIntegrationTest.kt`

- [ ] **Step 1: Test schreiben**

```kotlin
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
}
```

- [ ] **Step 2: Test ausführen**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.AssetBundleStorageIntegrationTest" --quiet
```

Expected: PASS (1 Test). Testcontainers startet MinIO automatisch.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorageIntegrationTest.kt
git commit -m "test(asset-bundles): integration test against Testcontainers MinIO"
```

---

## Task 6: AssetBundleNotFoundException + Handler

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleNotFoundException.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt`

- [ ] **Step 1: Exception-Klasse anlegen**

`backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleNotFoundException.kt`:

```kotlin
package com.agentwork.productspecagent.service

class AssetBundleNotFoundException(id: String) : RuntimeException("Asset bundle not found: $id")
```

- [ ] **Step 2: Handler im GlobalExceptionHandler ergänzen**

`backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt` — Import ergänzen (bei den anderen Exception-Imports):

```kotlin
import com.agentwork.productspecagent.service.AssetBundleNotFoundException
```

Und Handler-Method nach `handleTaskNotFound` einfügen:

```kotlin
    @ExceptionHandler(AssetBundleNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleAssetBundleNotFound(ex: AssetBundleNotFoundException): ErrorResponse {
        return ErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "Asset bundle not found",
            timestamp = Instant.now().toString()
        )
    }
```

- [ ] **Step 3: Compile prüfen**

```bash
cd backend && ./gradlew compileKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleNotFoundException.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt
git commit -m "feat(asset-bundles): AssetBundleNotFoundException + 404 handler"
```

---

## Task 7: AssetBundleController + DTOs + Tests

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/AssetBundleController.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/api/AssetBundleControllerTest.kt`

- [ ] **Step 1: Failing controller test schreiben**

`backend/src/test/kotlin/com/agentwork/productspecagent/api/AssetBundleControllerTest.kt`:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.storage.ObjectStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class AssetBundleControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectStore: ObjectStore

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun cleanBundles() {
        objectStore.deletePrefix("asset-bundles/")
    }

    private fun putBundle(id: String, step: FlowStepType, field: String, value: String, files: Map<String, String> = emptyMap()) {
        val m = AssetBundleManifest(
            id = id, step = step, field = field, value = value,
            version = "1.0.0", title = "T", description = "D",
            createdAt = "2026-04-29T12:00:00Z", updatedAt = "2026-04-29T12:00:00Z",
        )
        objectStore.put("asset-bundles/$id/manifest.json", json.encodeToString(m).toByteArray())
        files.forEach { (rel, content) ->
            objectStore.put("asset-bundles/$id/$rel", content.toByteArray())
        }
    }

    @Test
    fun `GET asset-bundles returns empty array when none exist`() {
        mockMvc.perform(get("/api/v1/asset-bundles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET asset-bundles lists all bundles with file count`() {
        putBundle("backend.framework.kotlin-spring", FlowStepType.BACKEND, "framework", "Kotlin+Spring",
                  files = mapOf("skills/x/SKILL.md" to "a", "commands/y.md" to "b"))
        putBundle("frontend.framework.stitch", FlowStepType.FRONTEND, "framework", "Stitch",
                  files = mapOf("skills/z/SKILL.md" to "c"))

        mockMvc.perform(get("/api/v1/asset-bundles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.id == 'backend.framework.kotlin-spring')].fileCount").value(2))
            .andExpect(jsonPath("$[?(@.id == 'frontend.framework.stitch')].fileCount").value(1))
    }

    @Test
    fun `GET asset-bundle detail returns manifest and files`() {
        putBundle("backend.framework.kotlin-spring", FlowStepType.BACKEND, "framework", "Kotlin+Spring",
                  files = mapOf("skills/x/SKILL.md" to "a"))

        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.manifest.id").value("backend.framework.kotlin-spring"))
            .andExpect(jsonPath("$.files.length()").value(1))
            .andExpect(jsonPath("$.files[0].relativePath").value("skills/x/SKILL.md"))
            .andExpect(jsonPath("$.files[0].contentType").value("text/markdown"))
    }

    @Test
    fun `GET asset-bundle detail returns 404 for unknown triple`() {
        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Nonexistent"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }

    @Test
    fun `GET asset-bundle detail returns 400 for invalid step enum`() {
        mockMvc.perform(get("/api/v1/asset-bundles/INVALID_STEP/framework/Kotlin+Spring"))
            .andExpect(status().isBadRequest)
    }
}
```

- [ ] **Step 2: Test ausführen, Failure verifizieren**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.AssetBundleControllerTest" --quiet
```

Expected: FAIL — Controller nicht gefunden, alle Endpoints liefern 404.

- [ ] **Step 3: Controller mit DTOs implementieren**

`backend/src/main/kotlin/com/agentwork/productspecagent/api/AssetBundleController.kt`:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.service.AssetBundleNotFoundException
import com.agentwork.productspecagent.storage.AssetBundleStorage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AssetBundleListItem(
    val id: String,
    val step: FlowStepType,
    val field: String,
    val value: String,
    val version: String,
    val title: String,
    val description: String,
    val fileCount: Int,
)

data class AssetBundleDetail(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

@RestController
@RequestMapping("/api/v1/asset-bundles")
class AssetBundleController(private val storage: AssetBundleStorage) {

    @GetMapping
    fun list(): List<AssetBundleListItem> =
        storage.listAll().map { manifest ->
            val bundle = storage.find(manifest.step, manifest.field, manifest.value)
            AssetBundleListItem(
                id = manifest.id,
                step = manifest.step,
                field = manifest.field,
                value = manifest.value,
                version = manifest.version,
                title = manifest.title,
                description = manifest.description,
                fileCount = bundle?.files?.size ?: 0,
            )
        }

    @GetMapping("/{step}/{field}/{value}")
    fun detail(
        @PathVariable step: FlowStepType,
        @PathVariable field: String,
        @PathVariable value: String,
    ): AssetBundleDetail {
        val bundle = storage.find(step, field, value)
            ?: throw AssetBundleNotFoundException("${step.name.lowercase()}.$field.$value")
        return AssetBundleDetail(manifest = bundle.manifest, files = bundle.files)
    }
}
```

- [ ] **Step 4: Tests ausführen, Pass verifizieren**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.AssetBundleControllerTest" --quiet
```

Expected: PASS, 5 Tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/AssetBundleController.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/AssetBundleControllerTest.kt
git commit -m "feat(asset-bundles): REST controller for list and detail endpoints"
```

---

## Task 8: Doku-Update

**Files:**
- Modify: `docs/architecture/persistence.md`

- [ ] **Step 1: Existierende Doku lesen**

```bash
cat docs/architecture/persistence.md | head -50
```

Inspect-Schritt — verstehe die existierende Struktur, um den neuen Abschnitt passend einzufügen.

- [ ] **Step 2: Abschnitt "Asset-Bundles in S3" anhängen**

Am Ende der Datei `docs/architecture/persistence.md` ergänzen:

```markdown
## Asset-Bundles in S3

Vorkurierte Claude-Code-Skills, -Commands und -Agents leben unter dem Prefix `asset-bundles/` im selben Bucket (`productspec-data`). Sie sind global geteilt — nicht per-Projekt.

### Layout

```
asset-bundles/
  backend.framework.kotlin-spring/
    manifest.json
    skills/spring-testing/SKILL.md
    commands/gradle-build.md
    agents/spring-debug.md
  frontend.framework.stitch/
    manifest.json
    skills/...
  architecture.architecture.microservices/
    manifest.json
    ...
```

Bundle-Folder-Name = Bundle-ID = `${step.lower()}.${field}.${slug(value)}`.

### Manifest-Schema (`manifest.json`)

```json
{
  "id": "backend.framework.kotlin-spring",
  "step": "BACKEND",
  "field": "framework",
  "value": "Kotlin+Spring",
  "version": "1.0.0",
  "title": "Kotlin + Spring Boot Essentials",
  "description": "Skills für Spring-Boot-Backend",
  "createdAt": "2026-04-29T12:00:00Z",
  "updatedAt": "2026-04-29T12:00:00Z"
}
```

### Bundles befüllen

Bundles werden manuell verwaltet (Sub-Feature A — kein UI). Beispiel-Sync aus separatem Repo:

```bash
aws s3 sync ./asset-bundles/ s3://productspec-data/asset-bundles/ --delete --exclude ".git/*"
```

Backend liest Bundles live aus S3 — kein Restart nötig nach Sync.

### Read-API

- `GET /api/v1/asset-bundles` — Liste aller Bundles (Manifest + fileCount)
- `GET /api/v1/asset-bundles/{step}/{field}/{value}` — Detail mit File-Liste oder 404
```

- [ ] **Step 3: Commit**

```bash
git add docs/architecture/persistence.md
git commit -m "docs(persistence): document asset-bundle layout in S3"
```

---

## Task 9: Final Verification

- [ ] **Step 1: Komplette Test-Suite ausführen**

```bash
cd backend && ./gradlew test --quiet
```

Expected: alle Tests grün, inkl. der neuen ~16 Asset-Bundle-Tests.

- [ ] **Step 2: Verifizieren, dass keine Schreibwege existieren (Sub-Feature A ist read-only)**

```bash
grep -rn "objectStore.put\|objectStore.delete" backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt backend/src/main/kotlin/com/agentwork/productspecagent/api/AssetBundleController.kt 2>/dev/null
```

Expected: keine Treffer — read-only Storage und Controller.

- [ ] **Step 3: Commit-Log prüfen**

```bash
git log --oneline main..HEAD
```

Expected: 8 Commits (Task 2–8), saubere Reihenfolge.

- [ ] **Step 4: Final-Commit (falls Doku-Updates nachgezogen werden müssen)**

Nichts mehr commiten, falls alles oben grün. Sonst: Hotfix-Commit mit aussagekräftiger Message.

---

## Acceptance Criteria — Checkliste

| # | Kriterium | Verifiziert in |
|---|---|---|
| 1 | `AssetBundle*`-Domain-Klassen vorhanden | Task 1 Commit |
| 2 | `AssetBundleStorage.listAll()` liefert alle Manifeste | Task 2 + 3 Tests |
| 3 | `AssetBundleStorage.find(triple)` liefert Bundle inkl. Files mit relativen Pfaden | Task 4 Tests |
| 4 | Korrupte Manifeste werden geloggt und übersprungen | Task 3 Tests |
| 5 | `GET /api/v1/asset-bundles` listet alle Bundles | Task 7 Tests |
| 6 | `GET /api/v1/asset-bundles/{step}/{field}/{value}` liefert Detail oder 404 | Task 7 Tests |
| 7 | Invalid `step`-Enum → 400 | Task 7 Tests |
| 8 | Integration-Test gegen MinIO grün | Task 5 |
| 9 | `docs/architecture/persistence.md` ergänzt | Task 8 |
| 10 | `./gradlew test` komplett grün | Task 9 |

---

## Self-Review Findings

(Nach Plan-Erstellung manuell geprüft)

- ✅ Spec-Coverage: alle 10 Akzeptanzkriterien aus dem Spec haben mindestens einen Task.
- ✅ Keine Placeholder-Strings (`TBD`, `TODO`, „später ergänzen").
- ✅ Type-Konsistenz: `FlowStepType`, `AssetBundleManifest`, `assetBundleId()` werden über alle Tasks identisch verwendet.
- ✅ Spec verwendet `StepType` als verkürzte Schreibweise; Plan benutzt durchgehend den korrekten Enum-Namen `FlowStepType` aus `domain/FlowState.kt`.
- ✅ Test-Granularität: 5 Unit-Tests in Task 2/3, 5 in Task 4, 1 Integration-Test, 5 Controller-Tests = 16 Tests, deckt sich mit Spec-Erwartung.
- ⚠ `find()` liest Files via `objectStore.get(key)` für Größe-Bestimmung — N GET-Requests pro Bundle. Vergleichbar mit existierendem `listDocsFiles`-Pattern in `ProjectService` (Spec dokumentiert das als bekannte Schuld). Akzeptabel für aktuellen Scale.
