# Feature 31 — Project Storage auf S3 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Komplettumstellung der Backend-Persistence-Schicht von Filesystem (`data/projects/{id}/...`) auf S3-kompatibles Object Storage. Lokale Entwicklung und Tests laufen gegen MinIO, Produktion gegen AWS S3 oder beliebiges S3-kompatibles Backend per `S3_ENDPOINT`-Override.

**Architecture:** Neue `ObjectStore`-Abstraktion mit `S3ObjectStore`-Implementation (AWS SDK v2) zwischen den 5 Domain-Storage-Klassen und S3. Pfad-zu-Key-Mapping bleibt 1:1 zum heutigen Filesystem-Layout. Tests nutzen Testcontainers + MinIO über die gemeinsame `S3TestSupport`-Basisklasse mit JVM-weit geteiltem Container.

**Tech Stack:**
- AWS SDK for Java v2 (`software.amazon.awssdk:s3`, BOM 2.30.4)
- Testcontainers `org.testcontainers:minio:1.21.3` + `junit-jupiter:1.21.3`
- MinIO `RELEASE.2026-03-12T18-04-02Z` (lokal & Tests)
- Spring Boot 4 / Kotlin 2.3 / Java 21 (bestehender Stack)

**Approved Spec:** [docs/superpowers/specs/2026-04-28-project-storage-s3-design.md](../specs/2026-04-28-project-storage-s3-design.md)

**Sprache der Commit-Messages:** Englisch (matched bestehenden Repo-Stil — siehe `git log`).

---

## Vorbedingungen

- Branch: arbeite auf einem Feature-Branch (`feat/feature-31-s3-storage` oder Worktree).
- Kein lokales `data/projects/`-Verzeichnis mit echten Daten (Verzeichnis ist heute leer — bestätigt).
- Docker-Daemon läuft (für Testcontainers).

---

### Task 1: AWS SDK v2 + Testcontainers MinIO Dependencies hinzufügen

**Files:**
- Modify: `backend/build.gradle.kts`

- [ ] **Step 1: AWS BOM und S3 Dependency ergänzen**

In `backend/build.gradle.kts`, im `dependencies { ... }` Block — nach der bestehenden `mustache`-Zeile einfügen:

```kotlin
    // AWS S3
    implementation(platform("software.amazon.awssdk:bom:2.30.4"))
    implementation("software.amazon.awssdk:s3")
```

- [ ] **Step 2: Testcontainers MinIO Dependencies ergänzen**

Im selben `dependencies { ... }` Block, im Test-Bereich — nach `testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")` einfügen:

```kotlin
    // S3 Testcontainers
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:minio:1.21.3")
```

- [ ] **Step 3: Build verifizieren**

Run: `cd backend && ./gradlew compileKotlin compileTestKotlin --quiet`
Expected: BUILD SUCCESSFUL — Dependencies werden aufgelöst, Code kompiliert (noch keine Code-Änderungen).

- [ ] **Step 4: Commit**

```bash
git add backend/build.gradle.kts
git commit -m "chore(backend): add AWS SDK v2 S3 and Testcontainers-MinIO dependencies"
```

---

### Task 2: `S3StorageProperties` + `S3Config`

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/config/S3StorageProperties.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/config/S3Config.kt`

- [ ] **Step 1: S3StorageProperties anlegen**

Schreibe `backend/src/main/kotlin/com/agentwork/productspecagent/config/S3StorageProperties.kt`:

```kotlin
package com.agentwork.productspecagent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.storage")
data class S3StorageProperties(
    val bucket: String,
    val endpoint: String = "",
    val region: String = "us-east-1",
    val accessKey: String = "",
    val secretKey: String = "",
    val pathStyleAccess: Boolean = false,
)
```

- [ ] **Step 2: S3Config anlegen**

Schreibe `backend/src/main/kotlin/com/agentwork/productspecagent/config/S3Config.kt`:

```kotlin
package com.agentwork.productspecagent.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
@EnableConfigurationProperties(S3StorageProperties::class)
class S3Config {

    @Bean(destroyMethod = "close")
    fun s3Client(props: S3StorageProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(props.region))
            .forcePathStyle(props.pathStyleAccess)

        if (props.endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(props.endpoint))
        }
        if (props.accessKey.isNotBlank() && props.secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey, props.secretKey)
                )
            )
        }
        return builder.build()
    }
}
```

- [ ] **Step 3: `application.yml` provisorisch ergänzen, damit `s3Client`-Bean beim Start eine `bucket`-Property findet**

In `backend/src/main/resources/application.yml`, am Ende einfügen (das wird in Task 11 final umgebaut):

```yaml

app:
  storage:
    bucket: ${S3_BUCKET:productspec-data}
    endpoint: ${S3_ENDPOINT:}
    region: ${S3_REGION:us-east-1}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    path-style-access: ${S3_PATH_STYLE:false}
```

> **Wichtig:** `app.data-path: ./data` bleibt vorerst stehen — wird in Task 11 entfernt, sobald alle Storage-Klassen migriert sind.

- [ ] **Step 4: Build verifizieren**

Run: `cd backend && ./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/config/S3StorageProperties.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/config/S3Config.kt \
        backend/src/main/resources/application.yml
git commit -m "feat(config): add S3StorageProperties and S3Config bean"
```

---

### Task 3: `ObjectStore` Interface

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ObjectStore.kt`

- [ ] **Step 1: ObjectStore Interface schreiben**

Schreibe `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ObjectStore.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import java.time.Instant

interface ObjectStore {

    /** Schreibt Bytes unter [key]. Überschreibt vorhandene Keys. */
    fun put(key: String, bytes: ByteArray, contentType: String? = null)

    /** Liest Bytes; null wenn der Key nicht existiert. */
    fun get(key: String): ByteArray?

    /** Existiert der Key? */
    fun exists(key: String): Boolean

    /** Löscht einzelnen Key (idempotent — wirft nicht bei NoSuchKey). */
    fun delete(key: String)

    /** Löscht alle Keys mit [prefix]. Batched bis zu 1000 Keys pro Request. */
    fun deletePrefix(prefix: String)

    /** Listet alle Keys mit [prefix]. Voll paginiert. */
    fun listKeys(prefix: String): List<String>

    /** Listet Keys + lastModified. Voll paginiert. */
    fun listEntries(prefix: String): List<ObjectEntry>

    /**
     * Listet die ersten Pfad-Segmente unter [prefix], getrennt durch [delimiter].
     * Beispiel: prefix="projects/", delimiter="/" → ["projects/abc/", "projects/def/"]
     * Trailing-Delimiter wird gestrippt → ["abc", "def"].
     */
    fun listCommonPrefixes(prefix: String, delimiter: String): List<String>

    data class ObjectEntry(val key: String, val lastModified: Instant)
}
```

- [ ] **Step 2: Build verifizieren**

Run: `cd backend && ./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/ObjectStore.kt
git commit -m "feat(storage): add ObjectStore interface"
```

---

### Task 4: `S3TestSupport` Test-Basisklasse

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/S3TestSupport.kt`

- [ ] **Step 1: S3TestSupport schreiben**

Schreibe `backend/src/test/kotlin/com/agentwork/productspecagent/storage/S3TestSupport.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.config.S3StorageProperties
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import java.net.URI

@Testcontainers
abstract class S3TestSupport {

    companion object {
        @Container
        @JvmStatic
        val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2026-03-12T18-04-02Z")
            .withUserName("testuser")
            .withPassword("testpassword")

        @JvmStatic
        protected lateinit var s3: S3Client

        protected const val BUCKET = "test-bucket"

        @BeforeAll
        @JvmStatic
        fun initS3() {
            s3 = S3Client.builder()
                .endpointOverride(URI.create(minio.s3URL))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.userName, minio.password)
                    )
                )
                .build()
            // Bucket idempotent anlegen — wirft nicht, falls schon vorhanden
            try {
                s3.createBucket { it.bucket(BUCKET) }
            } catch (_: software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException) {
                // ignore
            }
        }
    }

    @BeforeEach
    fun clearBucket() {
        val keys = s3.listObjectsV2Paginator { it.bucket(BUCKET) }
            .stream()
            .flatMap { it.contents().stream() }
            .map { it.key() }
            .toList()
        keys.chunked(1000).forEach { batch ->
            s3.deleteObjects { req ->
                req.bucket(BUCKET).delete { d ->
                    d.objects(batch.map { ObjectIdentifier.builder().key(it).build() })
                }
            }
        }
    }

    /** Frische S3ObjectStore-Instanz pro Test mit dem Test-Bucket. */
    protected fun objectStore(): S3ObjectStore =
        S3ObjectStore(s3, S3StorageProperties(bucket = BUCKET))
}
```

- [ ] **Step 2: Build verifizieren** (Klasse referenziert noch nicht-existierende `S3ObjectStore` → Compile-Fehler erwartet)

Run: `cd backend && ./gradlew compileTestKotlin --quiet`
Expected: FAIL mit `Unresolved reference: S3ObjectStore` — das ist OK; wir lösen es in Task 5 auf.

> **Hinweis für den Subagenten:** Diesen Compile-Fehler **nicht beheben** durch einen Stub. Nächster Task erstellt `S3ObjectStore` und macht beide Klassen kompilierbar.

- [ ] **Step 3: Noch nicht committen** — wir committen `S3TestSupport.kt` zusammen mit `S3ObjectStore` in Task 5, sobald alles kompiliert.

---

### Task 5: `S3ObjectStore` Implementation + Tests (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/S3ObjectStore.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/S3ObjectStoreTest.kt`

- [ ] **Step 1: S3ObjectStoreTest mit failing tests schreiben**

Schreibe `backend/src/test/kotlin/com/agentwork/productspecagent/storage/S3ObjectStoreTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class S3ObjectStoreTest : S3TestSupport() {

    @Test
    fun `put then get round-trips bytes`() {
        val store = objectStore()
        store.put("a.txt", "hello".toByteArray())

        val bytes = store.get("a.txt")

        assertNotNull(bytes)
        assertEquals("hello", String(bytes!!))
    }

    @Test
    fun `get returns null for missing key`() {
        assertNull(objectStore().get("does-not-exist"))
    }

    @Test
    fun `exists returns false for missing key, true after put`() {
        val store = objectStore()

        assertFalse(store.exists("k"))
        store.put("k", byteArrayOf(1))
        assertTrue(store.exists("k"))
    }

    @Test
    fun `delete is idempotent for missing key`() {
        assertDoesNotThrow { objectStore().delete("ghost") }
    }

    @Test
    fun `delete removes key`() {
        val store = objectStore()
        store.put("k", byteArrayOf(1))

        store.delete("k")

        assertNull(store.get("k"))
    }

    @Test
    fun `deletePrefix removes only matching keys`() {
        val store = objectStore()
        store.put("projects/a/file1", byteArrayOf(1))
        store.put("projects/a/file2", byteArrayOf(2))
        store.put("projects/b/file1", byteArrayOf(3))

        store.deletePrefix("projects/a/")

        assertNull(store.get("projects/a/file1"))
        assertNull(store.get("projects/a/file2"))
        assertNotNull(store.get("projects/b/file1"))
    }

    @Test
    fun `listKeys returns all keys with prefix`() {
        val store = objectStore()
        store.put("p/a", byteArrayOf(1))
        store.put("p/b", byteArrayOf(2))
        store.put("other/c", byteArrayOf(3))

        val keys = store.listKeys("p/").toSet()

        assertEquals(setOf("p/a", "p/b"), keys)
    }

    @Test
    fun `listKeys returns empty for unknown prefix`() {
        assertEquals(emptyList<String>(), objectStore().listKeys("nope/"))
    }

    @Test
    fun `listEntries returns key plus lastModified`() {
        val store = objectStore()
        val before = Instant.now().minusSeconds(5)
        store.put("p/a", byteArrayOf(1))

        val entries = store.listEntries("p/")

        assertEquals(1, entries.size)
        assertEquals("p/a", entries[0].key)
        assertTrue(entries[0].lastModified.isAfter(before))
    }

    @Test
    fun `listCommonPrefixes returns directory-like segments`() {
        val store = objectStore()
        store.put("projects/a/file", byteArrayOf(1))
        store.put("projects/b/file", byteArrayOf(2))
        store.put("projects/c/sub/file", byteArrayOf(3))

        val prefixes = store.listCommonPrefixes("projects/", "/").toSet()

        assertEquals(setOf("a", "b", "c"), prefixes)
    }

    @Test
    fun `deletePrefix handles batches over 1000 keys`() {
        val store = objectStore()
        // 1100 Keys synthetisch erzeugen
        repeat(1100) { i -> store.put("bulk/$i", byteArrayOf(i.toByte())) }

        store.deletePrefix("bulk/")

        assertEquals(0, store.listKeys("bulk/").size)
    }
}
```

- [ ] **Step 2: Tests laufen lassen — alle FAIL erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.S3ObjectStoreTest" --quiet`
Expected: COMPILATION FAIL — `S3ObjectStore` existiert noch nicht.

- [ ] **Step 3: S3ObjectStore Implementation schreiben**

Schreibe `backend/src/main/kotlin/com/agentwork/productspecagent/storage/S3ObjectStore.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.config.S3StorageProperties
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

@Service
class S3ObjectStore(
    private val s3: S3Client,
    private val props: S3StorageProperties,
) : ObjectStore {

    override fun put(key: String, bytes: ByteArray, contentType: String?) {
        val req = PutObjectRequest.builder()
            .bucket(props.bucket)
            .key(key)
            .apply { if (contentType != null) contentType(contentType) }
            .build()
        s3.putObject(req, RequestBody.fromBytes(bytes))
    }

    override fun get(key: String): ByteArray? = try {
        val req = GetObjectRequest.builder().bucket(props.bucket).key(key).build()
        val resp: ResponseBytes<GetObjectResponse> =
            s3.getObject(req, ResponseTransformer.toBytes())
        resp.asByteArray()
    } catch (_: NoSuchKeyException) {
        null
    }

    override fun exists(key: String): Boolean = try {
        s3.headObject(HeadObjectRequest.builder().bucket(props.bucket).key(key).build())
        true
    } catch (_: NoSuchKeyException) {
        false
    } catch (e: S3Exception) {
        if (e.statusCode() == 404) false else throw e
    }

    override fun delete(key: String) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket).key(key).build())
    }

    override fun deletePrefix(prefix: String) {
        val keys = listKeys(prefix)
        if (keys.isEmpty()) return
        keys.chunked(1000).forEach { batch ->
            s3.deleteObjects { req ->
                req.bucket(props.bucket).delete { d ->
                    d.objects(batch.map { ObjectIdentifier.builder().key(it).build() })
                }
            }
        }
    }

    override fun listKeys(prefix: String): List<String> =
        s3.listObjectsV2Paginator { it.bucket(props.bucket).prefix(prefix) }
            .stream()
            .flatMap { it.contents().stream() }
            .map { it.key() }
            .toList()

    override fun listEntries(prefix: String): List<ObjectStore.ObjectEntry> =
        s3.listObjectsV2Paginator { it.bucket(props.bucket).prefix(prefix) }
            .stream()
            .flatMap { it.contents().stream() }
            .map { ObjectStore.ObjectEntry(it.key(), it.lastModified()) }
            .toList()

    override fun listCommonPrefixes(prefix: String, delimiter: String): List<String> =
        s3.listObjectsV2Paginator { it.bucket(props.bucket).prefix(prefix).delimiter(delimiter) }
            .stream()
            .flatMap { it.commonPrefixes().stream() }
            .map { it.prefix().removePrefix(prefix).removeSuffix(delimiter) }
            .toList()
}
```

- [ ] **Step 4: Tests laufen lassen — alle PASS erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.S3ObjectStoreTest" --quiet`
Expected: BUILD SUCCESSFUL — alle 11 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/S3ObjectStore.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/S3TestSupport.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/S3ObjectStoreTest.kt
git commit -m "feat(storage): add S3ObjectStore implementation with full test coverage"
```

---

### Task 6: `ProjectStorage` auf ObjectStore umstellen

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`

- [ ] **Step 1: ProjectStorageTest auf S3TestSupport portieren**

Ersetze die gesamte Datei `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ProjectStorageTest : S3TestSupport() {

    private lateinit var storage: ProjectStorage

    @BeforeEach
    fun setUpStorage() {
        storage = ProjectStorage(objectStore())
    }

    private fun makeProject(id: String = "proj-1") = Project(
        id = id,
        name = "Test Project",
        ownerId = "user-1",
        status = ProjectStatus.DRAFT,
        createdAt = Instant.now().toString(),
        updatedAt = Instant.now().toString()
    )

    @Test
    fun `saveProject and loadProject round-trips correctly`() {
        val project = makeProject()
        storage.saveProject(project)

        val loaded = storage.loadProject(project.id)
        assertNotNull(loaded)
        assertEquals(project.id, loaded!!.id)
        assertEquals(project.name, loaded.name)
        assertEquals(project.status, loaded.status)
    }

    @Test
    fun `loadProject returns null for non-existent project`() {
        val result = storage.loadProject("does-not-exist")
        assertNull(result)
    }

    @Test
    fun `deleteProject removes all project keys`() {
        val project = makeProject()
        storage.saveProject(project)
        assertNotNull(storage.loadProject(project.id))

        storage.deleteProject(project.id)
        assertNull(storage.loadProject(project.id))
    }

    @Test
    fun `deleteProject on non-existent project does not throw`() {
        assertDoesNotThrow { storage.deleteProject("ghost-project") }
    }

    @Test
    fun `listProjects returns all saved projects`() {
        storage.saveProject(makeProject("proj-1"))
        storage.saveProject(makeProject("proj-2"))

        val projects = storage.listProjects()
        assertEquals(2, projects.size)
        assertTrue(projects.any { it.id == "proj-1" })
        assertTrue(projects.any { it.id == "proj-2" })
    }

    @Test
    fun `listProjects returns empty list when no projects exist`() {
        assertEquals(emptyList<Project>(), storage.listProjects())
    }

    @Test
    fun `saveFlowState and loadFlowState round-trips correctly`() {
        val project = makeProject()
        storage.saveProject(project)
        val flowState = createInitialFlowState(project.id)
        storage.saveFlowState(flowState)

        val loaded = storage.loadFlowState(project.id)
        assertNotNull(loaded)
        assertEquals(flowState.projectId, loaded!!.projectId)
        assertEquals(flowState.currentStep, loaded.currentStep)
        assertEquals(7, loaded.steps.size)
    }

    @Test
    fun `loadFlowState returns null for non-existent project`() {
        assertNull(storage.loadFlowState("no-such-project"))
    }

    @Test
    fun `saveSpecStep writes file under spec prefix`() {
        val project = makeProject()
        storage.saveProject(project)
        storage.saveSpecStep(project.id, "idea.md", "# My Idea\nThis is a great idea.")

        val raw = objectStore().get("projects/${project.id}/spec/idea.md")
        assertNotNull(raw)
        assertEquals("# My Idea\nThis is a great idea.", String(raw!!))
    }

    @Test
    fun `project with collectionId saves and loads correctly`() {
        val project = Project(
            id = "p1",
            name = "Demo",
            ownerId = "u1",
            status = ProjectStatus.DRAFT,
            createdAt = "2026-04-24T10:00:00Z",
            updatedAt = "2026-04-24T10:00:00Z",
            collectionId = "col-abc-123"
        )
        storage.saveProject(project)
        val loaded = storage.loadProject("p1")!!
        assertEquals("col-abc-123", loaded.collectionId)
    }

    @Test
    fun `project loads correctly when collectionId is missing in JSON`() {
        val legacyJson = """{"id":"p1","name":"Old","ownerId":"u1","status":"DRAFT","createdAt":"x","updatedAt":"y"}"""
        objectStore().put("projects/p1/project.json", legacyJson.toByteArray())

        val loaded = storage.loadProject("p1")!!
        assertNull(loaded.collectionId)
    }

    @Test
    fun `loads project with graphmeshEnabled default false when missing in JSON`() {
        val legacy = """{"id":"p1","name":"Demo","ownerId":"u1","status":"DRAFT","createdAt":"x","updatedAt":"x"}"""
        objectStore().put("projects/p1/project.json", legacy.toByteArray())

        val loaded = storage.loadProject("p1")!!

        assertFalse(loaded.graphmeshEnabled)
    }

    @Test
    fun `roundtrips project with graphmeshEnabled=true`() {
        val project = Project(
            id = "p1", name = "Demo", ownerId = "u1",
            status = ProjectStatus.DRAFT,
            createdAt = "x", updatedAt = "x",
            graphmeshEnabled = true
        )
        storage.saveProject(project)

        val loaded = storage.loadProject("p1")!!

        assertTrue(loaded.graphmeshEnabled)
    }
}
```

- [ ] **Step 2: Tests laufen lassen — Compile-Fehler erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.ProjectStorageTest" --quiet`
Expected: FAIL — `ProjectStorage`-Konstruktor erwartet noch `String`, nicht `ObjectStore`.

- [ ] **Step 3: ProjectStorage refactoren**

Ersetze die gesamte Datei `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.FlowState
import com.agentwork.productspecagent.domain.Project
import com.agentwork.productspecagent.domain.WizardData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class ProjectStorage(private val objectStore: ObjectStore) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun projectPrefix(id: String) = "projects/$id/"
    private fun projectKey(id: String) = "projects/$id/project.json"
    private fun flowStateKey(id: String) = "projects/$id/flow-state.json"
    private fun wizardKey(id: String) = "projects/$id/wizard.json"
    private fun specKey(id: String, fileName: String) = "projects/$id/spec/$fileName"
    private fun docsKey(id: String, relativePath: String) = "projects/$id/$relativePath"
    private fun docsPrefix(id: String) = "projects/$id/docs/"

    fun saveProject(project: Project) {
        objectStore.put(
            projectKey(project.id),
            json.encodeToString(project).toByteArray(),
            "application/json"
        )
    }

    fun loadProject(projectId: String): Project? =
        objectStore.get(projectKey(projectId))
            ?.toString(Charsets.UTF_8)
            ?.let { json.decodeFromString<Project>(it) }

    fun deleteProject(projectId: String) {
        objectStore.deletePrefix(projectPrefix(projectId))
    }

    fun listProjects(): List<Project> =
        objectStore.listCommonPrefixes("projects/", "/")
            .mapNotNull { id -> loadProject(id) }

    fun saveFlowState(flowState: FlowState) {
        objectStore.put(
            flowStateKey(flowState.projectId),
            json.encodeToString(flowState).toByteArray(),
            "application/json"
        )
    }

    fun loadFlowState(projectId: String): FlowState? =
        objectStore.get(flowStateKey(projectId))
            ?.toString(Charsets.UTF_8)
            ?.let { json.decodeFromString<FlowState>(it) }

    fun saveSpecStep(projectId: String, fileName: String, content: String) {
        objectStore.put(specKey(projectId, fileName), content.toByteArray(), "text/markdown")
    }

    fun loadSpecStep(projectId: String, fileName: String): String? =
        objectStore.get(specKey(projectId, fileName))?.toString(Charsets.UTF_8)

    fun saveDocsFile(projectId: String, relativePath: String, content: String) {
        objectStore.put(docsKey(projectId, relativePath), content.toByteArray())
    }

    /** Returns every doc file as `(relativePath, bytes)` pairs. Excludes `.index.json` (UploadStorage internals). */
    fun listDocsFiles(projectId: String): List<Pair<String, ByteArray>> {
        val docsPrefix = docsPrefix(projectId)
        val projectPrefix = projectPrefix(projectId)
        return objectStore.listKeys(docsPrefix)
            .filter { !it.endsWith(".index.json") }
            .map { key ->
                val rel = key.removePrefix(projectPrefix)
                val bytes = objectStore.get(key) ?: ByteArray(0)
                rel to bytes
            }
    }

    fun saveWizardData(projectId: String, data: WizardData) {
        objectStore.put(
            wizardKey(projectId),
            json.encodeToString(data).toByteArray(),
            "application/json"
        )
    }

    fun loadWizardData(projectId: String): WizardData? =
        objectStore.get(wizardKey(projectId))
            ?.toString(Charsets.UTF_8)
            ?.let { json.decodeFromString<WizardData>(it) }
}
```

- [ ] **Step 4: Tests laufen lassen — alle PASS erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.ProjectStorageTest" --quiet`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify keine Filesystem-Imports mehr**

Run: `grep -n "java.nio.file\|app.data-path" backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt`
Expected: Keine Treffer.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt
git commit -m "refactor(storage): migrate ProjectStorage to ObjectStore"
```

---

### Task 7: `DecisionStorage` auf ObjectStore umstellen

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DecisionStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DecisionStorageTest.kt`

- [ ] **Step 1: DecisionStorageTest portieren**

Ersetze die gesamte Datei `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DecisionStorageTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.DecisionNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DecisionStorageTest : S3TestSupport() {

    private fun storage() = DecisionStorage(objectStore())

    private fun sampleDecision(id: String = "d1", projectId: String = "p1") = Decision(
        id = id, projectId = projectId, stepType = FlowStepType.FEATURES,
        title = "Test decision",
        options = listOf(
            DecisionOption("opt-1", "Option A", listOf("pro1"), listOf("con1"), true),
            DecisionOption("opt-2", "Option B", listOf("pro2"), listOf("con2"), false)
        ),
        recommendation = "Go with A",
        createdAt = "2026-03-30T12:00:00Z"
    )

    @Test
    fun `saveDecision and loadDecision round-trip`() {
        val s = storage()
        val d = sampleDecision()
        s.saveDecision(d)
        val loaded = s.loadDecision("p1", "d1")
        assertEquals("d1", loaded.id)
        assertEquals("Test decision", loaded.title)
        assertEquals(2, loaded.options.size)
    }

    @Test
    fun `loadDecision throws for non-existent decision`() {
        assertThrows(DecisionNotFoundException::class.java) {
            storage().loadDecision("p1", "nope")
        }
    }

    @Test
    fun `listDecisions returns all decisions for a project`() {
        val s = storage()
        s.saveDecision(sampleDecision("d1", "p1"))
        s.saveDecision(sampleDecision("d2", "p1"))
        assertEquals(2, s.listDecisions("p1").size)
    }

    @Test
    fun `listDecisions returns empty for project with no decisions`() {
        assertEquals(0, storage().listDecisions("p1").size)
    }

    @Test
    fun `deleteDecision removes the object`() {
        val s = storage()
        s.saveDecision(sampleDecision())
        s.deleteDecision("p1", "d1")
        assertThrows(DecisionNotFoundException::class.java) {
            s.loadDecision("p1", "d1")
        }
    }

    @Test
    fun `deleteDecision on non-existent does not throw`() {
        assertDoesNotThrow { storage().deleteDecision("p1", "nope") }
    }

    @Test
    fun `saveDecision writes to docs-decisions key prefix`() {
        storage().saveDecision(sampleDecision())
        assertTrue(objectStore().exists("projects/p1/docs/decisions/d1.json"))
    }
}
```

- [ ] **Step 2: Tests laufen — FAIL erwartet (Konstruktor-Mismatch)**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.DecisionStorageTest" --quiet`
Expected: FAIL.

- [ ] **Step 3: DecisionStorage refactoren**

Ersetze die gesamte Datei `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DecisionStorage.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Decision
import com.agentwork.productspecagent.service.DecisionNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class DecisionStorage(private val objectStore: ObjectStore) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun decisionsPrefix(projectId: String) = "projects/$projectId/docs/decisions/"
    private fun decisionKey(projectId: String, decisionId: String) =
        "${decisionsPrefix(projectId)}$decisionId.json"

    fun saveDecision(decision: Decision) {
        objectStore.put(
            decisionKey(decision.projectId, decision.id),
            json.encodeToString(decision).toByteArray(),
            "application/json"
        )
    }

    fun loadDecision(projectId: String, decisionId: String): Decision {
        val bytes = objectStore.get(decisionKey(projectId, decisionId))
            ?: throw DecisionNotFoundException(decisionId)
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    fun listDecisions(projectId: String): List<Decision> =
        objectStore.listKeys(decisionsPrefix(projectId))
            .filter { it.endsWith(".json") }
            .mapNotNull { key ->
                objectStore.get(key)
                    ?.toString(Charsets.UTF_8)
                    ?.let { json.decodeFromString<Decision>(it) }
            }
            .sortedBy { it.createdAt }

    fun deleteDecision(projectId: String, decisionId: String) {
        objectStore.delete(decisionKey(projectId, decisionId))
    }
}
```

- [ ] **Step 4: Tests laufen — PASS erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.DecisionStorageTest" --quiet`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/DecisionStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/DecisionStorageTest.kt
git commit -m "refactor(storage): migrate DecisionStorage to ObjectStore"
```

---

### Task 8: `ClarificationStorage` auf ObjectStore umstellen

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ClarificationStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ClarificationStorageTest.kt`

- [ ] **Step 1: ClarificationStorageTest portieren**

Ersetze die gesamte Datei `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ClarificationStorageTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ClarificationNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClarificationStorageTest : S3TestSupport() {

    private fun storage() = ClarificationStorage(objectStore())

    private fun sample(id: String = "c1", projectId: String = "p1") = Clarification(
        id = id, projectId = projectId, stepType = FlowStepType.FEATURES,
        question = "How handle offline?", reason = "Contradicting requirements",
        createdAt = "2026-03-30T12:00:00Z"
    )

    @Test
    fun `save and load round-trip`() {
        val s = storage()
        s.saveClarification(sample())
        val loaded = s.loadClarification("p1", "c1")
        assertEquals("c1", loaded.id)
        assertEquals("How handle offline?", loaded.question)
    }

    @Test
    fun `load throws for non-existent`() {
        assertThrows(ClarificationNotFoundException::class.java) {
            storage().loadClarification("p1", "nope")
        }
    }

    @Test
    fun `list returns all for project`() {
        val s = storage()
        s.saveClarification(sample("c1"))
        s.saveClarification(sample("c2"))
        assertEquals(2, s.listClarifications("p1").size)
    }

    @Test
    fun `list returns empty when none exist`() {
        assertEquals(0, storage().listClarifications("p1").size)
    }

    @Test
    fun `delete removes object`() {
        val s = storage()
        s.saveClarification(sample())
        s.deleteClarification("p1", "c1")
        assertThrows(ClarificationNotFoundException::class.java) {
            s.loadClarification("p1", "c1")
        }
    }

    @Test
    fun `delete on non-existent does not throw`() {
        assertDoesNotThrow { storage().deleteClarification("p1", "nope") }
    }

    @Test
    fun `saveClarification writes to docs-clarifications key prefix`() {
        storage().saveClarification(sample())
        assertTrue(objectStore().exists("projects/p1/docs/clarifications/c1.json"))
    }
}
```

- [ ] **Step 2: Tests laufen — FAIL erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.ClarificationStorageTest" --quiet`
Expected: FAIL.

- [ ] **Step 3: ClarificationStorage refactoren**

Ersetze die gesamte Datei `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ClarificationStorage.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Clarification
import com.agentwork.productspecagent.service.ClarificationNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class ClarificationStorage(private val objectStore: ObjectStore) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun clarificationsPrefix(projectId: String) = "projects/$projectId/docs/clarifications/"
    private fun clarificationKey(projectId: String, clarificationId: String) =
        "${clarificationsPrefix(projectId)}$clarificationId.json"

    fun saveClarification(clarification: Clarification) {
        objectStore.put(
            clarificationKey(clarification.projectId, clarification.id),
            json.encodeToString(clarification).toByteArray(),
            "application/json"
        )
    }

    fun loadClarification(projectId: String, clarificationId: String): Clarification {
        val bytes = objectStore.get(clarificationKey(projectId, clarificationId))
            ?: throw ClarificationNotFoundException(clarificationId)
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    fun listClarifications(projectId: String): List<Clarification> =
        objectStore.listKeys(clarificationsPrefix(projectId))
            .filter { it.endsWith(".json") }
            .mapNotNull { key ->
                objectStore.get(key)
                    ?.toString(Charsets.UTF_8)
                    ?.let { json.decodeFromString<Clarification>(it) }
            }
            .sortedBy { it.createdAt }

    fun deleteClarification(projectId: String, clarificationId: String) {
        objectStore.delete(clarificationKey(projectId, clarificationId))
    }
}
```

- [ ] **Step 4: Tests laufen — PASS erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.ClarificationStorageTest" --quiet`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/ClarificationStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/ClarificationStorageTest.kt
git commit -m "refactor(storage): migrate ClarificationStorage to ObjectStore"
```

---

### Task 9: `TaskStorage` auf ObjectStore umstellen

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/TaskStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/TaskStorageTest.kt`

- [ ] **Step 1: TaskStorageTest portieren**

Ersetze die gesamte Datei `backend/src/test/kotlin/com/agentwork/productspecagent/storage/TaskStorageTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.TaskNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TaskStorageTest : S3TestSupport() {

    private fun storage() = TaskStorage(objectStore())

    private fun sample(id: String = "t1", projectId: String = "p1", priority: Int = 0) = SpecTask(
        id = id, projectId = projectId, type = TaskType.EPIC,
        title = "Test task", description = "Description",
        priority = priority, createdAt = "2026-03-30T12:00:00Z", updatedAt = "2026-03-30T12:00:00Z"
    )

    @Test
    fun `save and load round-trip`() {
        val s = storage()
        s.saveTask(sample())
        val loaded = s.loadTask("p1", "t1")
        assertEquals("t1", loaded.id)
        assertEquals("Test task", loaded.title)
    }

    @Test
    fun `load throws for non-existent`() {
        assertThrows(TaskNotFoundException::class.java) {
            storage().loadTask("p1", "nope")
        }
    }

    @Test
    fun `list returns all sorted by priority`() {
        val s = storage()
        s.saveTask(sample("t2", priority = 2))
        s.saveTask(sample("t1", priority = 1))
        val list = s.listTasks("p1")
        assertEquals(2, list.size)
        assertEquals("t1", list[0].id)
        assertEquals("t2", list[1].id)
    }

    @Test
    fun `list returns empty when none exist`() {
        assertEquals(0, storage().listTasks("p1").size)
    }

    @Test
    fun `delete removes object`() {
        val s = storage()
        s.saveTask(sample())
        s.deleteTask("p1", "t1")
        assertThrows(TaskNotFoundException::class.java) { s.loadTask("p1", "t1") }
    }

    @Test
    fun `deleteAll removes all tasks`() {
        val s = storage()
        s.saveTask(sample("t1"))
        s.saveTask(sample("t2"))
        s.deleteAllTasks("p1")
        assertEquals(0, s.listTasks("p1").size)
    }

    @Test
    fun `saveTask writes to docs-tasks key prefix`() {
        storage().saveTask(sample())
        assertTrue(objectStore().exists("projects/p1/docs/tasks/t1.json"))
    }
}
```

- [ ] **Step 2: Tests laufen — FAIL erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.TaskStorageTest" --quiet`
Expected: FAIL.

- [ ] **Step 3: TaskStorage refactoren**

Ersetze die gesamte Datei `backend/src/main/kotlin/com/agentwork/productspecagent/storage/TaskStorage.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.SpecTask
import com.agentwork.productspecagent.service.TaskNotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class TaskStorage(private val objectStore: ObjectStore) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun tasksPrefix(projectId: String) = "projects/$projectId/docs/tasks/"
    private fun taskKey(projectId: String, taskId: String) =
        "${tasksPrefix(projectId)}$taskId.json"

    fun saveTask(task: SpecTask) {
        objectStore.put(
            taskKey(task.projectId, task.id),
            json.encodeToString(task).toByteArray(),
            "application/json"
        )
    }

    fun loadTask(projectId: String, taskId: String): SpecTask {
        val bytes = objectStore.get(taskKey(projectId, taskId))
            ?: throw TaskNotFoundException(taskId)
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    fun listTasks(projectId: String): List<SpecTask> =
        objectStore.listKeys(tasksPrefix(projectId))
            .filter { it.endsWith(".json") }
            .mapNotNull { key ->
                objectStore.get(key)
                    ?.toString(Charsets.UTF_8)
                    ?.let { json.decodeFromString<SpecTask>(it) }
            }
            .sortedBy { it.priority }

    fun deleteTask(projectId: String, taskId: String) {
        objectStore.delete(taskKey(projectId, taskId))
    }

    fun deleteAllTasks(projectId: String) {
        objectStore.deletePrefix(tasksPrefix(projectId))
    }
}
```

- [ ] **Step 4: Tests laufen — PASS erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.TaskStorageTest" --quiet`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/TaskStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/TaskStorageTest.kt
git commit -m "refactor(storage): migrate TaskStorage to ObjectStore"
```

---

### Task 10: `UploadStorage` auf ObjectStore umstellen

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt`

> **Spezialfälle**: `uniqueFilename` (Read-then-Write — Race akzeptiert), `readMtime` (via `listEntries`), Index-Migration (Legacy Map → IndexFile).

- [ ] **Step 1: UploadStorageTest portieren**

Ersetze die gesamte Datei `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UploadStorageTest : S3TestSupport() {

    private fun storage() = UploadStorage(objectStore())

    @Test
    fun `save writes file under uploads and returns sanitized filename`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1, 2, 3), "2026-04-27T10:00:00Z")

        assertEquals("spec.pdf", name)
        val bytes = objectStore().get("projects/p1/docs/uploads/spec.pdf")
        assertNotNull(bytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
    }

    @Test
    fun `save persists docId-filename mapping in index`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val raw = objectStore().get("projects/p1/docs/uploads/.index.json")
        assertNotNull(raw)
        val str = String(raw!!)
        assertTrue(str.contains("\"doc-1\""))
        assertTrue(str.contains("\"spec.pdf\""))
    }

    @Test
    fun `save with duplicate title appends auto-rename suffix`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")
        val second = s.save("p1", "doc-2", "spec.pdf", "application/pdf", byteArrayOf(2), "2026-04-27T10:00:00Z")
        val third = s.save("p1", "doc-3", "spec.pdf", "application/pdf", byteArrayOf(3), "2026-04-27T10:00:00Z")

        assertEquals("spec (2).pdf", second)
        assertEquals("spec (3).pdf", third)
        assertTrue(objectStore().exists("projects/p1/docs/uploads/spec.pdf"))
        assertTrue(objectStore().exists("projects/p1/docs/uploads/spec (2).pdf"))
        assertTrue(objectStore().exists("projects/p1/docs/uploads/spec (3).pdf"))
    }

    @Test
    fun `save sanitizes path-traversal characters`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "../../etc/passwd", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        assertFalse(name.contains(".."))
        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
    }

    @Test
    fun `save with blank title falls back to document`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        assertEquals("document", name)
    }

    @Test
    fun `delete removes object and index entry`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        s.delete("p1", "doc-1")

        assertFalse(objectStore().exists("projects/p1/docs/uploads/spec.pdf"))
        val raw = objectStore().get("projects/p1/docs/uploads/.index.json")
        assertNotNull(raw)
        assertFalse(String(raw!!).contains("\"doc-1\""))
    }

    @Test
    fun `delete is idempotent for missing docId`() {
        val s = storage()
        // Index existiert nicht — darf nicht werfen
        s.delete("p1", "missing")
    }

    @Test
    fun `list returns filenames excluding index`() {
        val s = storage()
        s.save("p1", "doc-1", "a.md", "text/markdown", byteArrayOf(1), "2026-04-27T10:00:00Z")
        s.save("p1", "doc-2", "b.pdf", "application/pdf", byteArrayOf(2), "2026-04-27T10:00:00Z")

        val files = s.list("p1")

        assertEquals(setOf("a.md", "b.pdf"), files.toSet())
    }

    @Test
    fun `list returns empty list when no uploads exist`() {
        val s = storage()
        assertEquals(emptyList<String>(), s.list("never-touched"))
    }

    @Test
    fun `read returns saved file bytes`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(7, 8, 9), "2026-04-27T10:00:00Z")

        val bytes = s.read("p1", "spec.pdf")

        assertArrayEquals(byteArrayOf(7, 8, 9), bytes)
    }

    @Test
    fun `save persists full metadata in index`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val raw = String(objectStore().get("projects/p1/docs/uploads/.index.json")!!)
        assertTrue(raw.contains("\"id\""))
        assertTrue(raw.contains("\"doc-1\""))
        assertTrue(raw.contains("\"spec.pdf\""))
        assertTrue(raw.contains("\"application/pdf\""))
        assertTrue(raw.contains("\"2026-04-27T10:00:00Z\""))
    }

    @Test
    fun `listAsDocuments returns document metadata in LOCAL state`() {
        val s = storage()
        s.save("p1", "d1", "a.md", "text/markdown", byteArrayOf(1), "2026-04-27T10:00:00Z")
        s.save("p1", "d2", "b.pdf", "application/pdf", byteArrayOf(2), "2026-04-27T11:00:00Z")

        val docs = s.listAsDocuments("p1")

        assertEquals(2, docs.size)
        assertEquals(setOf("d1", "d2"), docs.map { it.id }.toSet())
        assertTrue(docs.all { it.state == com.agentwork.productspecagent.domain.DocumentState.LOCAL })
        val d1 = docs.first { it.id == "d1" }
        assertEquals("a.md", d1.title)
        assertEquals("text/markdown", d1.mimeType)
        assertEquals("2026-04-27T10:00:00Z", d1.createdAt)
    }

    @Test
    fun `getDocument returns the matching document`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val d = s.getDocument("p1", "doc-1")

        assertNotNull(d)
        assertEquals("spec.pdf", d!!.title)
    }

    @Test
    fun `getDocument returns null for missing docId`() {
        val s = storage()
        s.save("p1", "d1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        assertNull(s.getDocument("p1", "missing"))
    }

    @Test
    fun `migrates legacy index format on first read`() {
        val s = storage()
        // Legacy-Setup direkt in S3 schreiben
        objectStore().put("projects/p1/docs/uploads/legacy.pdf", byteArrayOf(1, 2, 3))
        objectStore().put(
            "projects/p1/docs/uploads/.index.json",
            """{"old-doc-1":"legacy.pdf"}""".toByteArray()
        )

        val docs = s.listAsDocuments("p1")

        assertEquals(1, docs.size)
        val d = docs[0]
        assertEquals("old-doc-1", d.id)
        assertEquals("legacy.pdf", d.title)
        assertEquals("application/pdf", d.mimeType)

        val rawAfter = String(objectStore().get("projects/p1/docs/uploads/.index.json")!!)
        assertTrue(rawAfter.contains("\"documents\""))
        assertTrue(rawAfter.contains("\"old-doc-1\""))
    }

    @Test
    fun `migration infers mimeType for common extensions`() {
        val s = storage()
        objectStore().put("projects/p1/docs/uploads/a.md", byteArrayOf(1))
        objectStore().put("projects/p1/docs/uploads/b.txt", byteArrayOf(1))
        objectStore().put("projects/p1/docs/uploads/c.unknown", byteArrayOf(1))
        objectStore().put(
            "projects/p1/docs/uploads/.index.json",
            """{"d1":"a.md","d2":"b.txt","d3":"c.unknown"}""".toByteArray()
        )

        val docs = s.listAsDocuments("p1").associateBy { it.id }

        assertEquals("text/markdown", docs["d1"]!!.mimeType)
        assertEquals("text/plain", docs["d2"]!!.mimeType)
        assertEquals("application/octet-stream", docs["d3"]!!.mimeType)
    }
}
```

- [ ] **Step 2: Tests laufen — FAIL erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.UploadStorageTest" --quiet`
Expected: FAIL.

- [ ] **Step 3: UploadStorage refactoren**

Ersetze die gesamte Datei `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.domain.DocumentState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.time.Instant

@Service
open class UploadStorage(private val objectStore: ObjectStore) {

    @Serializable
    data class IndexEntry(
        val id: String,
        val filename: String,
        val title: String,
        val mimeType: String,
        val createdAt: String
    )

    @Serializable
    private data class IndexFile(val documents: List<IndexEntry> = emptyList())

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun uploadsPrefix(projectId: String) = "projects/$projectId/docs/uploads/"
    private fun fileKey(projectId: String, filename: String) =
        "${uploadsPrefix(projectId)}$filename"
    private fun indexKey(projectId: String) =
        "${uploadsPrefix(projectId)}.index.json"

    open fun save(
        projectId: String,
        docId: String,
        title: String,
        mimeType: String,
        bytes: ByteArray,
        createdAt: String = Instant.now().toString()
    ): String {
        val sanitized = sanitizeFilename(title)
        val filename = uniqueFilename(projectId, sanitized)
        objectStore.put(fileKey(projectId, filename), bytes, mimeType)

        val entries = readEntries(projectId).filter { it.id != docId }.toMutableList()
        entries += IndexEntry(docId, filename, title, mimeType, createdAt)
        writeEntries(projectId, entries)

        return filename
    }

    open fun delete(projectId: String, docId: String) {
        val entries = readEntries(projectId).toMutableList()
        val entry = entries.firstOrNull { it.id == docId } ?: return
        entries.remove(entry)
        objectStore.delete(fileKey(projectId, entry.filename))
        writeEntries(projectId, entries)
    }

    open fun read(projectId: String, filename: String): ByteArray =
        objectStore.get(fileKey(projectId, filename))
            ?: throw NoSuchElementException("Upload not found: $filename")

    open fun list(projectId: String): List<String> =
        objectStore.listKeys(uploadsPrefix(projectId))
            .map { it.removePrefix(uploadsPrefix(projectId)) }
            .filter { it != ".index.json" }
            .sorted()

    open fun listAsDocuments(projectId: String): List<Document> =
        readEntries(projectId).map { it.toDocument() }

    open fun getDocument(projectId: String, docId: String): Document? =
        readEntries(projectId).firstOrNull { it.id == docId }?.toDocument()

    private fun IndexEntry.toDocument() = Document(
        id = id,
        title = title,
        mimeType = mimeType,
        state = DocumentState.LOCAL,
        createdAt = createdAt
    )

    private fun readEntries(projectId: String): List<IndexEntry> {
        val raw = objectStore.get(indexKey(projectId))?.toString(Charsets.UTF_8) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        // Versuch: neues Format
        val newFormatAttempt = try {
            json.decodeFromString<IndexFile>(raw).documents
        } catch (_: Exception) {
            null
        }

        if (newFormatAttempt != null && raw.matches(Regex("(?s).*\"documents\"\\s*:.*"))) {
            return newFormatAttempt
        }

        // Legacy-Format (flat docId -> filename map) → migrieren
        val legacy = try {
            json.decodeFromString<Map<String, String>>(raw)
        } catch (_: Exception) {
            return newFormatAttempt ?: emptyList()
        }
        val migrated = legacy.map { (id, filename) ->
            IndexEntry(
                id = id,
                filename = filename,
                title = filename,
                mimeType = inferMimeType(filename),
                createdAt = readMtime(projectId, filename)
            )
        }
        writeEntries(projectId, migrated)
        return migrated
    }

    private fun writeEntries(projectId: String, entries: List<IndexEntry>) {
        objectStore.put(
            indexKey(projectId),
            json.encodeToString(IndexFile(entries)).toByteArray(),
            "application/json"
        )
    }

    private fun readMtime(projectId: String, filename: String): String {
        val targetKey = fileKey(projectId, filename)
        return objectStore.listEntries(uploadsPrefix(projectId))
            .firstOrNull { it.key == targetKey }
            ?.lastModified
            ?.toString()
            ?: Instant.now().toString()
    }

    private fun inferMimeType(filename: String): String {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".md") -> "text/markdown"
            lower.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun sanitizeFilename(title: String): String {
        val cleaned = title
            .replace("/", "")
            .replace("\\", "")
            .replace("..", "")
            .trim()
        if (cleaned.isEmpty()) return "document"
        return cleaned.take(255)
    }

    private fun uniqueFilename(projectId: String, sanitized: String): String {
        if (!objectStore.exists(fileKey(projectId, sanitized))) return sanitized
        val dotIdx = sanitized.lastIndexOf('.')
        val (base, ext) = if (dotIdx > 0) {
            sanitized.substring(0, dotIdx) to sanitized.substring(dotIdx)
        } else {
            sanitized to ""
        }
        var n = 2
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!objectStore.exists(fileKey(projectId, candidate))) return candidate
            n++
        }
    }
}
```

- [ ] **Step 4: Tests laufen — PASS erwartet**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.UploadStorageTest" --quiet`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt
git commit -m "refactor(storage): migrate UploadStorage to ObjectStore"
```

---

### Task 11: `application.yml` finalisieren

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: `app.data-path` entfernen**

In `backend/src/main/resources/application.yml`, entferne diese zwei Zeilen (sie stehen in der Mitte der Datei):

```yaml
app:
  data-path: ./data
```

> **Achtung:** In Task 2 wurde `app.storage:` am Ende der Datei eingefügt — das `app:`-Mapping existiert dort bereits. Diese alte `app:`-Sektion mit `data-path` wird entfernt; das `app.storage:`-Mapping bleibt.

> Falls beide `app:`-Mappings existieren (oben mit `data-path`, unten mit `storage`), konsolidiere zu einem einzigen `app:`-Block:

```yaml
app:
  storage:
    bucket: ${S3_BUCKET:productspec-data}
    endpoint: ${S3_ENDPOINT:}
    region: ${S3_REGION:us-east-1}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    path-style-access: ${S3_PATH_STYLE:false}
```

- [ ] **Step 2: Verify keine `data-path`-Referenzen mehr im main-Code**

Run: `grep -rn "data-path\|app.data-path" backend/src/main/`
Expected: Keine Treffer (nur ggf. in `application.yml` falls die Edit unvollständig war — dann Step 1 nachziehen).

- [ ] **Step 3: Backend startet (Smoke-Test ohne Daten)**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.*" --quiet`
Expected: BUILD SUCCESSFUL — alle 5 Storage-Tests + S3ObjectStoreTest grün.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application.yml
git commit -m "chore(config): remove app.data-path, S3 storage is now the only persistence"
```

---

### Task 12: `docker-compose.yml` umbauen

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: docker-compose.yml komplett ersetzen**

Ersetze die gesamte Datei `/Users/czarnik/IdeaProjects/ProductSpecAgent/docker-compose.yml`:

```yaml
services:
  minio:
    image: minio/minio:RELEASE.2026-03-12T18-04-02Z
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio-data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      timeout: 3s
      retries: 5
    restart: unless-stopped

  minio-init:
    image: minio/mc:latest
    depends_on:
      minio:
        condition: service_healthy
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 minioadmin minioadmin &&
      mc mb --ignore-existing local/productspec-data
      "

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    depends_on:
      minio-init:
        condition: service_completed_successfully
    environment:
      SPRING_PROFILES_ACTIVE: docker
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      S3_ENDPOINT: http://minio:9000
      S3_BUCKET: productspec-data
      S3_ACCESS_KEY: minioadmin
      S3_SECRET_KEY: minioadmin
      S3_PATH_STYLE: "true"
      S3_REGION: us-east-1
    restart: unless-stopped

  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    environment:
      NEXT_PUBLIC_API_URL: http://backend:8080
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  minio-data:
```

- [ ] **Step 2: docker-compose validieren**

Run: `docker compose -f docker-compose.yml config --quiet`
Expected: Keine Ausgabe (Konfiguration valide).

- [ ] **Step 3: Smoke-Test (manuell)**

Run: `OPENAI_API_KEY=sk-noop docker compose up -d minio minio-init`
Expected: minio-Container läuft healthy, minio-init beendet sich mit Exit-Code 0.

Verifikation Bucket existiert:
```bash
docker run --rm --network host minio/mc:latest \
  mc alias set local http://localhost:9000 minioadmin minioadmin && \
docker run --rm --network host minio/mc:latest \
  mc ls local/productspec-data
```
Expected: keine Fehler, leeres Bucket-Listing.

Tear-down: `docker compose down -v`

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml
git commit -m "feat(docker): replace data volume mount with MinIO and bucket-init service"
```

---

### Task 13: `start.sh` mit MinIO-Bootstrap

**Files:**
- Modify: `start.sh`

- [ ] **Step 1: start.sh ersetzen**

Ersetze die gesamte Datei `/Users/czarnik/IdeaProjects/ProductSpecAgent/start.sh`:

```bash
#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_PID=""
FRONTEND_PID=""
MINIO_STARTED_BY_US=0

cleanup() {
  echo ""
  echo "Stopping services..."
  [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null
  if [ "$MINIO_STARTED_BY_US" = "1" ]; then
    docker rm -f productspec-minio-dev >/dev/null 2>&1 || true
  fi
  wait 2>/dev/null
  echo "Done."
}
trap cleanup EXIT INT TERM

echo "=== Checking MinIO ==="
if curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1; then
  echo "MinIO already running on localhost:9000 — using it."
else
  echo "Starting MinIO container (productspec-minio-dev)..."
  docker run -d --rm \
    --name productspec-minio-dev \
    -p 9000:9000 -p 9001:9001 \
    -e MINIO_ROOT_USER=minioadmin \
    -e MINIO_ROOT_PASSWORD=minioadmin \
    minio/minio:RELEASE.2026-03-12T18-04-02Z \
    server /data --console-address ":9001" >/dev/null
  MINIO_STARTED_BY_US=1

  echo "Waiting for MinIO to become ready..."
  for i in $(seq 1 30); do
    if curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  echo "Creating bucket productspec-data..."
  docker run --rm --network host minio/mc:latest sh -c "
    mc alias set local http://localhost:9000 minioadmin minioadmin &&
    mc mb --ignore-existing local/productspec-data
  " >/dev/null
fi

export S3_ENDPOINT="http://localhost:9000"
export S3_BUCKET="productspec-data"
export S3_ACCESS_KEY="minioadmin"
export S3_SECRET_KEY="minioadmin"
export S3_PATH_STYLE="true"
export S3_REGION="us-east-1"

echo "=== Starting Backend (Spring Boot) ==="
cd "$ROOT_DIR/backend"
./gradlew bootRun --quiet &
BACKEND_PID=$!

echo "=== Starting Frontend (Next.js) ==="
cd "$ROOT_DIR/frontend"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "Backend:  http://localhost:8080"
echo "Frontend: http://localhost:3000"
echo "MinIO:    http://localhost:9001 (console)"
echo ""
echo "Press Ctrl+C to stop services."

wait
```

- [ ] **Step 2: start.sh executable lassen**

Run: `chmod +x start.sh`

- [ ] **Step 3: Skript syntaktisch validieren**

Run: `bash -n start.sh`
Expected: Keine Ausgabe.

- [ ] **Step 4: Commit**

```bash
git add start.sh
git commit -m "feat(scripts): bootstrap MinIO container for local backend dev"
```

---

### Task 14: `docs/architecture/persistence.md` aktualisieren

**Files:**
- Modify: `docs/architecture/persistence.md`

- [ ] **Step 1: persistence.md komplett ersetzen**

Ersetze die gesamte Datei `/Users/czarnik/IdeaProjects/ProductSpecAgent/docs/architecture/persistence.md`:

```markdown
# Architecture: Persistenz (S3 Object Storage)

## Überblick

Alle Projekt-Daten werden als Objekte in einem S3-kompatiblen Bucket gespeichert. Lokale Entwicklung und Tests laufen gegen MinIO; Produktion gegen AWS S3 oder ein anderes S3-kompatibles Backend (z. B. Cloudflare R2, Wasabi) per `S3_ENDPOINT`-Override.

Die Backend-Storage-Schicht nutzt eine `ObjectStore`-Abstraktion (`S3ObjectStore` Implementation, AWS SDK for Java v2). Es gibt keine Datenbank.

## Bucket-Layout

Ein Bucket pro Umgebung (Default-Name `productspec-data`, konfigurierbar via `S3_BUCKET`). Pro Projekt ein Key-Prefix `projects/{project-id}/`.

```
{bucket}/
└── projects/
    └── {project-id}/
        ├── project.json             # Metadaten (Name, Owner, Status, ...)
        ├── flow-state.json          # Aktueller Stand im Wizard-Graph
        ├── wizard.json              # Wizard-Form-Daten
        ├── spec/
        │   ├── idea.md
        │   ├── problem.md
        │   ├── target-audience.md
        │   ├── scope.md
        │   ├── mvp.md
        │   └── full-spec.md
        └── docs/
            ├── decisions/{id}.json
            ├── clarifications/{id}.json
            ├── tasks/{id}.json
            ├── uploads/
            │   ├── .index.json
            │   └── {filename}
            └── (weitere generierte Doku-Dateien)
```

## Konfiguration

```yaml
app:
  storage:
    bucket: ${S3_BUCKET:productspec-data}
    endpoint: ${S3_ENDPOINT:}            # leer = AWS-Default-Endpoint
    region: ${S3_REGION:us-east-1}
    access-key: ${S3_ACCESS_KEY:}        # leer = AWS Default-Credential-Chain
    secret-key: ${S3_SECRET_KEY:}
    path-style-access: ${S3_PATH_STYLE:false}  # true bei MinIO
```

## Komponenten

- `ObjectStore` (Interface) — kapselt put/get/exists/delete/listKeys/listEntries/listCommonPrefixes/deletePrefix
- `S3ObjectStore` (Implementation) — AWS SDK v2, übersetzt `NoSuchKeyException` zu `null`, paginiert via `listObjectsV2Paginator`, batched `deletePrefix` in 1000er-Chunks
- `S3Config` — registriert `S3Client` als Spring-Bean mit konfigurierbarem Endpoint und Credentials
- 5 Domain-Storage-Klassen (`ProjectStorage`, `DecisionStorage`, `ClarificationStorage`, `TaskStorage`, `UploadStorage`) — nutzen ausschließlich `ObjectStore`-API

## Datei-Formate

### project.json

```json
{
  "id": "uuid",
  "name": "Projektname",
  "ownerId": "user-uuid",
  "status": "in_progress",
  "createdAt": "2026-03-30T12:00:00Z",
  "updatedAt": "2026-03-30T14:00:00Z"
}
```

### Spec-Dateien (Markdown)

```markdown
# Problemdefinition
...
```

## Lokale Entwicklung

`./start.sh` startet eine MinIO-Instanz als Docker-Container, falls noch keine unter `localhost:9000` läuft, legt das Bucket an und exportiert die `S3_*`-Env-Vars vor dem Backend-Start.

`docker-compose up` startet einen vollständigen Stack (MinIO + Backend + Frontend); ein dedizierter `minio-init`-Container erzeugt das Bucket beim Hochfahren.

## Tests

Storage-Tests erben von `S3TestSupport`, das einen MinIO-Container per Testcontainers JVM-weit hochfährt. Vor jedem Test wird das Test-Bucket geleert; das sorgt für Isolation ohne Container-Restart-Overhead.
```

- [ ] **Step 2: Verify Datei-Inhalt**

Run: `head -3 docs/architecture/persistence.md`
Expected: Erste Zeile `# Architecture: Persistenz (S3 Object Storage)`.

- [ ] **Step 3: Commit**

```bash
git add docs/architecture/persistence.md
git commit -m "docs(architecture): update persistence doc to reflect S3 storage"
```

---

### Task 15: End-to-End Verifikation

**Files:** keine — nur Verifikation.

- [ ] **Step 1: Komplettes Test-Suite grün**

Run: `cd backend && ./gradlew test --quiet`
Expected: BUILD SUCCESSFUL — alle Tests grün, inkl. `S3ObjectStoreTest`, `ProjectStorageTest`, `DecisionStorageTest`, `ClarificationStorageTest`, `TaskStorageTest`, `UploadStorageTest`.

- [ ] **Step 2: Keine Filesystem-Imports mehr im Storage-Code**

Run: `grep -rn "java.nio.file\|app.data-path\|app\\.data-path" backend/src/main/kotlin/com/agentwork/productspecagent/storage/`
Expected: Keine Treffer.

- [ ] **Step 3: Keine `app.data-path`-Property mehr**

Run: `grep -rn "data-path" backend/src/main/`
Expected: Keine Treffer.

- [ ] **Step 4: Kein `./data:/app/data`-Mount mehr in docker-compose.yml**

Run: `grep -n "/app/data" docker-compose.yml`
Expected: Keine Treffer.

- [ ] **Step 5: docker-compose Smoke-Test**

Run: `OPENAI_API_KEY=sk-noop docker compose up -d`
Expected: Alle 4 Services (`minio`, `minio-init`, `backend`, `frontend`) starten erfolgreich. `minio-init` exitet mit Code 0.

Run: `curl -fsS http://localhost:8080/api/v1/projects`
Expected: HTTP 200, leere Liste `[]` (oder `{"projects":[]}` je nach REST-Vertrag).

Tear-down: `docker compose down -v`

- [ ] **Step 6: UI-Smoke (manuell, optional)**

Run lokal: `./start.sh`
- Öffne `http://localhost:3000`
- Lege ein neues Projekt an, durchlaufe einen Wizard-Schritt
- Verifiziere in MinIO Console (`http://localhost:9001`, Login `minioadmin`/`minioadmin`), dass Objekte unter `productspec-data/projects/{id}/` erscheinen

Falls UI-Smoke nicht durchführbar (z. B. kein OPENAI-Key), explizit notieren.

- [ ] **Step 7: Final Commit (kein Code-Diff, nur Trigger für Repository-Status)**

Falls keine Änderungen mehr offen: kein Commit nötig. Falls noch Untracked Files: `git status` prüfen und ggf. ergänzen.

- [ ] **Step 8: Feature-Done-Datei schreiben**

Schreibe `docs/features/31-project-storage-s3-done.md` mit:
- Kurze Zusammenfassung der Implementierung (max. 10 Zeilen)
- Abweichungen vom Plan oder von der Spec (z. B. wenn Testcontainers-Version geupgraded wurde)
- Offene Punkte / technische Schulden (z. B. UploadStorage Race-Condition, dokumentiert in Spec §7.4)

```bash
git add docs/features/31-project-storage-s3-done.md
git commit -m "docs(feature-31): mark S3 storage migration as done"
```

---

## Akzeptanzkriterien (Final Check)

| # | Kriterium | Wie verifiziert |
|---|---|---|
| 1 | docker-compose startet MinIO + Bucket-Init + Backend; Bucket `productspec-data` existiert | Task 15 Step 5 |
| 2 | UI-Smoke: Projekt anlegen, Daten in MinIO sichtbar | Task 15 Step 6 |
| 3 | Backend-Restart verliert keine Daten | implizit durch MinIO-Volume |
| 4 | `./gradlew test` grün | Task 15 Step 1 |
| 5 | Keine `java.nio.file.*` / `app.data-path` Referenzen mehr in Storage-Code | Task 15 Step 2/3 |
| 6 | `./data:/app/data`-Mount entfernt | Task 15 Step 4 |
| 7 | `application.yml` und `persistence.md` aktualisiert | Tasks 11 + 14 |
| 8 | REST-API-Verträge unverändert | implizit — keine Service/Controller-Änderungen |

---

## Hinweise für den Subagenten

- **Reihenfolge ist wichtig**: Tasks 1-3 müssen vor Tasks 4-5 fertig sein. Tasks 6-10 können in beliebiger Reihenfolge danach laufen, aber jeder Storage-Refactor ist eine geschlossene TDD-Schleife. Tasks 11-14 setzen Tasks 6-10 voraus. Task 15 ist final.
- **Keine Filesystem-Imports zurücklassen**: Wenn nach einem Refactor `import java.nio.file.Files` oder `import java.nio.file.Path` in einer Storage-Klasse bleiben, ist das ein Bug — entfernen.
- **Spring Boot 4 + AWS SDK v2**: Falls `S3Client.builder()`-Calls Compile-Probleme zeigen wegen API-Änderungen, prüfe die aktuelle BOM-Version (`software.amazon.awssdk:bom:2.30.4`) und ggf. neuere stable-Version verwenden.
- **MinIO Image Tag**: `RELEASE.2026-03-12T18-04-02Z` ist ein Beispiel. Wenn `docker pull` fehlschlägt, neueste verfügbare RELEASE.YYYY-... aus DockerHub `minio/minio` nehmen — und in `S3TestSupport`, `docker-compose.yml`, `start.sh` konsistent verwenden.
- **Bei Test-Fail in `deletePrefix handles batches over 1000 keys`**: Test erzeugt 1100 Objekte, kann ~10s brauchen. Bei sehr langsamen Hosts ggf. auf 1050 reduzieren — Aussage bleibt gleich.
- **Bei Test-Fail in `listEntries returns key plus lastModified`**: Wenn Container-Uhrzeit drift hat, Toleranz erhöhen — `Instant.now().minusSeconds(60)` statt `5`.
