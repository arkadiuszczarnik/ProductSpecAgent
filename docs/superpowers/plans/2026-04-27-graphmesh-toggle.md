# GraphMesh Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two-stage on/off switch for GraphMesh: backend `application.yml` flag plus per-project `graphmeshEnabled`. When disabled, the documents feature falls back to a local-only mode (UploadStorage as source of truth, no GraphMesh calls, new `LOCAL` state, polling pauses).

**Architecture:** `DocumentService.isGraphMeshActive(project) = config.enabled && project.graphmeshEnabled` decides per operation whether to take the existing GraphMesh path or the local-only path. `UploadStorage` already mirrors files; it just needs an extended index (with title/mimeType/createdAt per document) so it can serve as the metadata source when GraphMesh is off. A new `GET /api/v1/config/features` exposes the backend flag; a new `PATCH /api/v1/projects/{id}/graphmesh-enabled` flips the project flag. The workspace header gets a settings button that opens a small popover with the toggle.

**Tech Stack:** Kotlin 2.3 / Spring Boot 4 / kotlinx.serialization (Backend); Next.js 16 / React 19 / Zustand 5 / Tailwind 4 (Frontend). Tests: JUnit 5 with `@TempDir` and `@SpringBootTest @AutoConfigureMockMvc`.

---

## Task 1: Backend `graphmesh.enabled` config flag

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshConfig.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`

- [ ] **Step 1: Add `enabled` field to `GraphMeshConfig`**

Replace the entire file `backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshConfig.kt`:

```kotlin
package com.agentwork.productspecagent.infrastructure.graphmesh

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

@ConfigurationProperties(prefix = "graphmesh")
data class GraphMeshConfig(
    @DefaultValue("false") val enabled: Boolean = false,
    @DefaultValue("http://localhost:8083/graphql") val url: String,
    @DefaultValue("30s") val requestTimeout: Duration
)
```

- [ ] **Step 2: Update production `application.yml`**

In `backend/src/main/resources/application.yml`, find the existing `graphmesh:` block and add the `enabled` line at the top of it. The block should look like:

```yaml
graphmesh:
  enabled: ${GRAPHMESH_ENABLED:false}
  url: ${GRAPHMESH_URL:http://localhost:8083/graphql}
  request-timeout: 30s
```

- [ ] **Step 3: Update test `application.yml` (keeps existing tests green)**

In `backend/src/test/resources/application.yml`, add `enabled: true` to the `graphmesh:` block so existing tests that exercise GraphMesh paths keep working:

```yaml
graphmesh:
  enabled: true
  url: http://localhost:8083/graphql
  request-timeout: 5s
```

(If the existing test yml does not have a `graphmesh:` block at all, add one with these three lines.)

- [ ] **Step 4: Verify the project still compiles and existing tests pass**

```bash
./gradlew -p backend test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshConfig.kt backend/src/main/resources/application.yml backend/src/test/resources/application.yml
git commit -m "feat(graphmesh): add enabled flag (default off)"
```

Sign with `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` via HEREDOC.

---

## Task 2: Domain — `DocumentState.LOCAL`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Document.kt`

- [ ] **Step 1: Add `LOCAL` to `DocumentState`**

Replace the entire `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Document.kt` with:

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

enum class DocumentState {
    UPLOADED, PROCESSING, EXTRACTED, FAILED, LOCAL
}

@Serializable
data class Document(
    val id: String,
    val title: String,
    val mimeType: String,
    val state: DocumentState,
    val createdAt: String
)
```

- [ ] **Step 2: Run tests to verify enum extension does not break existing serialization**

```bash
./gradlew -p backend test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/Document.kt
git commit -m "feat(domain): add DocumentState.LOCAL terminal state"
```

---

## Task 3: Domain — `Project.graphmeshEnabled`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Project.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`

- [ ] **Step 1: Failing test for default + roundtrip**

Locate `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`. Add this test method inside the class (before the closing `}`):

```kotlin
    @Test
    fun `loads project with graphmeshEnabled default false when missing in JSON`() {
        val storage = ProjectStorage(tempDir.toString())
        val pid = "p1"
        val dir = tempDir.resolve("projects/$pid")
        java.nio.file.Files.createDirectories(dir)
        java.nio.file.Files.writeString(
            dir.resolve("project.json"),
            """{"id":"p1","name":"Demo","ownerId":"u1","status":"DRAFT","createdAt":"x","updatedAt":"x"}"""
        )

        val loaded = storage.loadProject(pid)!!

        assertFalse(loaded.graphmeshEnabled)
    }

    @Test
    fun `roundtrips project with graphmeshEnabled=true`() {
        val storage = ProjectStorage(tempDir.toString())
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
```

If the imports in that test file don't already include `Project`, `ProjectStatus`, `assertTrue`, `assertFalse`, add them. Look at the existing imports at the top to confirm.

- [ ] **Step 2: Run tests — they fail (compile error: Project has no `graphmeshEnabled`)**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.storage.ProjectStorageTest"
```
Expected: compile error.

- [ ] **Step 3: Add `graphmeshEnabled` field to `Project`**

Replace `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Project.kt` with:

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

enum class ProjectStatus {
    DRAFT, IN_PROGRESS, COMPLETED
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val ownerId: String,
    val status: ProjectStatus,
    val createdAt: String,
    val updatedAt: String,
    val collectionId: String? = null,
    val graphmeshEnabled: Boolean = false
)
```

- [ ] **Step 4: Run tests**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.storage.ProjectStorageTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/Project.kt backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt
git commit -m "feat(domain): add Project.graphmeshEnabled (default false)"
```

---

## Task 4: UploadStorage v2 — metadata index + migration

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt`

- [ ] **Step 1: Failing tests for new behaviors**

Add these tests to `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt` (before the closing `}` of the class):

```kotlin
    @Test
    fun `save persists full metadata in index`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val raw = java.nio.file.Files.readString(tempDir.resolve("projects/p1/uploads/.index.json"))
        assertTrue(raw.contains("\"id\":\"doc-1\""))
        assertTrue(raw.contains("\"filename\":\"spec.pdf\""))
        assertTrue(raw.contains("\"mimeType\":\"application/pdf\""))
        assertTrue(raw.contains("\"createdAt\":\"2026-04-27T10:00:00Z\""))
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
        val dir = tempDir.resolve("projects/p1/uploads")
        java.nio.file.Files.createDirectories(dir)
        java.nio.file.Files.write(dir.resolve("legacy.pdf"), byteArrayOf(1, 2, 3))
        // Old-format index: flat docId -> filename map
        java.nio.file.Files.writeString(dir.resolve(".index.json"), """{"old-doc-1":"legacy.pdf"}""")

        val docs = s.listAsDocuments("p1")

        assertEquals(1, docs.size)
        val d = docs[0]
        assertEquals("old-doc-1", d.id)
        assertEquals("legacy.pdf", d.title)
        assertEquals("application/pdf", d.mimeType)  // inferred from extension
        // After migration, index should be in new format
        val rawAfter = java.nio.file.Files.readString(dir.resolve(".index.json"))
        assertTrue(rawAfter.contains("\"documents\""))
        assertTrue(rawAfter.contains("\"old-doc-1\""))
    }

    @Test
    fun `migration infers mimeType for common extensions`() {
        val s = storage()
        val dir = tempDir.resolve("projects/p1/uploads")
        java.nio.file.Files.createDirectories(dir)
        java.nio.file.Files.write(dir.resolve("a.md"), byteArrayOf(1))
        java.nio.file.Files.write(dir.resolve("b.txt"), byteArrayOf(1))
        java.nio.file.Files.write(dir.resolve("c.unknown"), byteArrayOf(1))
        java.nio.file.Files.writeString(dir.resolve(".index.json"),
            """{"d1":"a.md","d2":"b.txt","d3":"c.unknown"}""")

        val docs = s.listAsDocuments("p1").associateBy { it.id }

        assertEquals("text/markdown", docs["d1"]!!.mimeType)
        assertEquals("text/plain", docs["d2"]!!.mimeType)
        assertEquals("application/octet-stream", docs["d3"]!!.mimeType)
    }
```

Also update the existing tests that call the old `save(projectId, docId, title, bytes)` 4-arg signature — they need the new 6-arg signature. Find these existing tests in the same file:
- `save writes file under uploads and returns sanitized filename`
- `save persists docId-filename mapping in index`
- `save with duplicate title appends auto-rename suffix`
- `save sanitizes path-traversal characters`
- `save with blank title falls back to document`
- `delete removes file and index entry`
- `list returns filenames excluding index`
- `read returns saved file bytes`

For each, replace `s.save("p1", "doc-1", "spec.pdf", byteArrayOf(...))` with `s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(...), "2026-04-27T10:00:00Z")`. Keep the same docIds/filenames/byte values — only the signature changes.

For the existing test `save persists docId-filename mapping in index`, also update the assertions because the new index format is different. Replace its body with:

```kotlin
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val index = tempDir.resolve("projects/p1/uploads/.index.json")
        assertTrue(Files.exists(index))
        val raw = Files.readString(index)
        assertTrue(raw.contains("\"doc-1\""))
        assertTrue(raw.contains("\"spec.pdf\""))
```

For the existing test `delete removes file and index entry`, the assertion `assertFalse(Files.readString(index).contains("\"doc-1\""))` still works because the docId string disappears from the new format too.

Make sure all other existing tests that call `s.save(...)` use the new 6-arg signature.

- [ ] **Step 2: Run tests — many fail (signature change + new tests fail)**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.storage.UploadStorageTest"
```
Expected: failures + compile errors.

- [ ] **Step 3: Rewrite `UploadStorage.kt`**

Replace `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt` entirely with:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.domain.DocumentState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

@Service
open class UploadStorage(
    @Value("\${app.data-path}") private val dataPath: String
) {

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

    private fun uploadsDir(projectId: String): Path =
        Paths.get(dataPath, "projects", projectId, "uploads")

    private fun indexFile(projectId: String): Path =
        uploadsDir(projectId).resolve(".index.json")

    open fun save(
        projectId: String,
        docId: String,
        title: String,
        mimeType: String,
        bytes: ByteArray,
        createdAt: String = Instant.now().toString()
    ): String {
        val dir = uploadsDir(projectId)
        Files.createDirectories(dir)

        val sanitized = sanitizeFilename(title)
        val filename = uniqueFilename(dir, sanitized)
        Files.write(dir.resolve(filename), bytes)

        val entries = readEntries(projectId).filter { it.id != docId }.toMutableList()
        entries += IndexEntry(docId, filename, title, mimeType, createdAt)
        writeEntries(projectId, entries)

        return filename
    }

    open fun delete(projectId: String, docId: String) {
        val entries = readEntries(projectId).toMutableList()
        val entry = entries.firstOrNull { it.id == docId } ?: return
        entries.remove(entry)
        Files.deleteIfExists(uploadsDir(projectId).resolve(entry.filename))
        writeEntries(projectId, entries)
    }

    open fun read(projectId: String, filename: String): ByteArray =
        Files.readAllBytes(uploadsDir(projectId).resolve(filename))

    open fun list(projectId: String): List<String> {
        val dir = uploadsDir(projectId)
        if (!Files.exists(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream
                .filter { !Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .filter { it != ".index.json" }
                .sorted()
                .toList()
        }
    }

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
        val file = indexFile(projectId)
        if (!Files.exists(file)) return emptyList()
        val raw = Files.readString(file)
        if (raw.isBlank()) return emptyList()

        return try {
            json.decodeFromString<IndexFile>(raw).documents
        } catch (_: Exception) {
            // Legacy format: { "docId": "filename" }
            val legacy = try {
                json.decodeFromString<Map<String, String>>(raw)
            } catch (_: Exception) {
                return emptyList()
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
            migrated
        }
    }

    private fun writeEntries(projectId: String, entries: List<IndexEntry>) {
        val file = indexFile(projectId)
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(IndexFile(entries)))
    }

    private fun readMtime(projectId: String, filename: String): String {
        val file = uploadsDir(projectId).resolve(filename)
        return try {
            Files.getLastModifiedTime(file).toInstant().toString()
        } catch (_: Exception) {
            Instant.now().toString()
        }
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

    private fun uniqueFilename(dir: Path, sanitized: String): String {
        if (!Files.exists(dir.resolve(sanitized))) return sanitized
        val dotIdx = sanitized.lastIndexOf('.')
        val (base, ext) = if (dotIdx > 0) sanitized.substring(0, dotIdx) to sanitized.substring(dotIdx)
        else sanitized to ""
        var n = 2
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!Files.exists(dir.resolve(candidate))) return candidate
            n++
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.storage.UploadStorageTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt
git commit -m "feat(storage): UploadStorage v2 with metadata index + legacy migration"
```

---

## Task 5: DocumentService — path branching by `isGraphMeshActive`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DocumentService.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/DocumentServiceTest.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/DocumentControllerTest.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt` (already takes `UploadStorage`; no changes if its method signatures hold — but verify)

- [ ] **Step 1: Failing tests for both modes**

In `backend/src/test/kotlin/com/agentwork/productspecagent/service/DocumentServiceTest.kt`, FIRST update the `FakeUploadStorage` to match the new `save(...)` signature. Replace its definition (private inner class) with:

```kotlin
    private class FakeUploadStorage : UploadStorage("unused-test-path") {
        val saved = mutableMapOf<String, FakeSave>()
        val deleted = mutableListOf<Pair<String, String>>()
        var throwOnSave: Boolean = false
        val localDocs = mutableListOf<Document>()

        data class FakeSave(val projectId: String, val title: String, val mimeType: String, val bytes: ByteArray, val createdAt: String)

        override fun save(projectId: String, docId: String, title: String, mimeType: String, bytes: ByteArray, createdAt: String): String {
            if (throwOnSave) throw java.io.IOException("disk full")
            saved[docId] = FakeSave(projectId, title, mimeType, bytes, createdAt)
            return title
        }
        override fun delete(projectId: String, docId: String) {
            deleted += projectId to docId
        }
        override fun listAsDocuments(projectId: String): List<Document> = localDocs.toList()
        override fun getDocument(projectId: String, docId: String): Document? = localDocs.firstOrNull { it.id == docId }
    }
```

Update all existing assertions on `uploads.saved` accordingly: `saved["d1"]?.first` becomes `saved["d1"]?.projectId`, `.second` → `.title`, `.third` → `.bytes`. Concretely, in the existing tests:

- `upload mirrors document to local UploadStorage after GraphMesh success`: change `saved!!.first` → `saved!!.projectId`, `saved.second` → `saved.title`, `saved.third` → `saved.bytes`.

Now update the `fixtures()` to inject a `GraphMeshConfig` with `enabled = true`. Replace the existing `fixtures()` body with:

```kotlin
    private fun fixtures(graphMeshConfigEnabled: Boolean = true, projectGraphMeshEnabled: Boolean = true): Quad<ProjectStorage, FakeClient, FakeUploadStorage, DocumentService> {
        val storage = ProjectStorage(tempDir.toString())
        val project = Project(
            id = "p1", name = "Demo", ownerId = "u1",
            status = ProjectStatus.DRAFT,
            createdAt = "2026-04-24T10:00:00Z", updatedAt = "2026-04-24T10:00:00Z",
            graphmeshEnabled = projectGraphMeshEnabled
        )
        storage.saveProject(project)
        val client = FakeClient()
        val uploads = FakeUploadStorage()
        val config = GraphMeshConfig(enabled = graphMeshConfigEnabled, url = "http://unused", requestTimeout = java.time.Duration.ofSeconds(1))
        val service = DocumentService(storage, client, uploads, config)
        return Quad(storage, client, uploads, service)
    }
```

Now add these new tests at the end of the class (before the closing `}`):

```kotlin
    @Test
    fun `local-only upload uses LOCAL state and skips GraphMesh`() {
        val (_, client, uploads, service) = fixtures(graphMeshConfigEnabled = false, projectGraphMeshEnabled = false)

        val doc = service.upload("p1", "spec.pdf", "application/pdf", byteArrayOf(1, 2, 3))

        assertEquals(DocumentState.LOCAL, doc.state)
        assertEquals(0, client.uploadCalls)
        assertEquals(0, client.createdCollections)
        assertNotNull(uploads.saved[doc.id])
    }

    @Test
    fun `backend-disabled overrides project-enabled (local-only)`() {
        val (_, client, _, service) = fixtures(graphMeshConfigEnabled = false, projectGraphMeshEnabled = true)

        val doc = service.upload("p1", "spec.pdf", "application/pdf", byteArrayOf(1))

        assertEquals(DocumentState.LOCAL, doc.state)
        assertEquals(0, client.uploadCalls)
    }

    @Test
    fun `project-disabled overrides backend-enabled (local-only)`() {
        val (_, client, _, service) = fixtures(graphMeshConfigEnabled = true, projectGraphMeshEnabled = false)

        val doc = service.upload("p1", "spec.pdf", "application/pdf", byteArrayOf(1))

        assertEquals(DocumentState.LOCAL, doc.state)
        assertEquals(0, client.uploadCalls)
    }

    @Test
    fun `list returns local docs in local-only mode`() {
        val (_, client, uploads, service) = fixtures(graphMeshConfigEnabled = false, projectGraphMeshEnabled = false)
        uploads.localDocs += Document("dx", "x.md", "text/markdown", DocumentState.LOCAL, "2026-04-27T10:00:00Z")

        val list = service.list("p1")

        assertEquals(1, list.size)
        assertEquals("dx", list[0].id)
        assertEquals(DocumentState.LOCAL, list[0].state)
        assertEquals(0, client.uploadCalls)
    }

    @Test
    fun `delete in local-only mode skips GraphMesh`() {
        val (_, client, uploads, service) = fixtures(graphMeshConfigEnabled = false, projectGraphMeshEnabled = false)

        service.delete("p1", "doc-x")

        assertEquals(listOf("p1" to "doc-x"), uploads.deleted)
        // GraphMesh should not have been touched
    }
```

You also need to update `GraphMeshConfig` import + `Document.kt` import in the test file. Add at the top:

```kotlin
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshConfig
```

If it's already there, leave it. The `DocumentState` import via `com.agentwork.productspecagent.domain.*` should already cover it.

- [ ] **Step 2: Run — fails (DocumentService doesn't accept `GraphMeshConfig`, doesn't branch)**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.service.DocumentServiceTest"
```
Expected: compile error / test failures.

- [ ] **Step 3: Rewrite `DocumentService`**

Replace `backend/src/main/kotlin/com/agentwork/productspecagent/service/DocumentService.kt` entirely with:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.domain.DocumentState
import com.agentwork.productspecagent.domain.Project
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshClient
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshConfig
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshException
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.UploadStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class DocumentService(
    private val projectStorage: ProjectStorage,
    private val graphMeshClient: GraphMeshClient,
    private val uploadStorage: UploadStorage,
    private val graphMeshConfig: GraphMeshConfig
) {
    private val log = LoggerFactory.getLogger(DocumentService::class.java)

    private fun isGraphMeshActive(project: Project): Boolean =
        graphMeshConfig.enabled && project.graphmeshEnabled

    fun upload(projectId: String, title: String, mimeType: String, content: ByteArray): Document {
        val project = projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        val createdAt = Instant.now().toString()

        val document = if (isGraphMeshActive(project)) {
            val base64 = Base64.getEncoder().encodeToString(content)
            val existingId = project.collectionId
            val collectionId = existingId ?: createCollection(project)
            try {
                graphMeshClient.uploadDocument(collectionId, title, mimeType, base64)
            } catch (e: GraphMeshException.GraphQlError) {
                if (existingId != null && "COLLECTION_NOT_FOUND" in e.detail) {
                    graphMeshClient.uploadDocument(createCollection(project), title, mimeType, base64)
                } else throw e
            }
        } else {
            Document(
                id = UUID.randomUUID().toString(),
                title = title,
                mimeType = mimeType,
                state = DocumentState.LOCAL,
                createdAt = createdAt
            )
        }

        try {
            uploadStorage.save(projectId, document.id, title, mimeType, content, document.createdAt)
        } catch (e: Exception) {
            log.warn("Local copy failed for project=$projectId doc=${document.id}: ${e.message}")
        }
        return document
    }

    fun list(projectId: String): List<Document> {
        val project = projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        if (!isGraphMeshActive(project)) {
            return uploadStorage.listAsDocuments(projectId)
        }
        val collectionId = project.collectionId ?: return emptyList()
        return graphMeshClient.listDocuments(collectionId)
    }

    fun get(projectId: String, documentId: String): Document {
        val project = projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        return if (isGraphMeshActive(project)) {
            graphMeshClient.getDocument(documentId)
        } else {
            uploadStorage.getDocument(projectId, documentId)
                ?: throw DocumentNotFoundException(documentId)
        }
    }

    fun delete(projectId: String, documentId: String) {
        val project = projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        if (isGraphMeshActive(project)) {
            graphMeshClient.deleteDocument(documentId)
        }
        try {
            uploadStorage.delete(projectId, documentId)
        } catch (e: Exception) {
            log.warn("Local delete failed for project=$projectId doc=$documentId: ${e.message}")
        }
    }

    private fun createCollection(project: Project): String {
        val newId = graphMeshClient.createCollection(project.name)
        projectStorage.saveProject(project.copy(collectionId = newId, updatedAt = Instant.now().toString()))
        return newId
    }
}
```

If `DocumentNotFoundException` doesn't already exist as a class, find it. Search:

```bash
grep -rn "class DocumentNotFoundException" backend/src/main/kotlin
```

If it does not exist, add it to `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectNotFoundException.kt` (or wherever the other service exceptions live — check that file). The class definition:

```kotlin
class DocumentNotFoundException(documentId: String) : RuntimeException("Document not found: $documentId")
```

Make sure the `GlobalExceptionHandler` already maps `DocumentNotFoundException` to 404 — if not, that's a pre-existing bug, leave it for a separate fix and just throw `ProjectNotFoundException(documentId)` for now (matches the existing pattern in `FileController`).

Actually verify by reading the existing exception handler. If `DocumentNotFoundException` is not in there, use `ProjectNotFoundException(documentId)` instead — that's what the existing code does for missing files in `FileController`.

- [ ] **Step 4: Update `DocumentControllerTest` test config to inject the right `GraphMeshConfig`**

In `backend/src/test/kotlin/com/agentwork/productspecagent/api/DocumentControllerTest.kt`, the test currently has a `TestConfig` with `@Bean @Primary fun fakeGraphMeshClient()` and `@Bean @Primary fun stubUploadStorage()`. The `DocumentService` is auto-wired by Spring; with the new `GraphMeshConfig` parameter, Spring should resolve it automatically from `application.yml` (which sets `graphmesh.enabled: true` in test resources).

Existing tests should still pass because:
- Test profile has `graphmesh.enabled: true`
- Tests use `createProject()` then upload — fresh project has `graphmeshEnabled=false` (new default!)

That last point will break the existing tests. They need to set `graphmeshEnabled=true` on the project before uploading.

Update the existing `createProject()` helper in `DocumentControllerTest.kt`. Find:

```kotlin
private fun createProject(): String {
    val result = mockMvc.perform(
        post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Doc Test"}""")
    ).andExpect(status().isCreated).andReturn()
    return """"id"\s*:\s*"([^"]+)"""".toRegex()
        .find(result.response.contentAsString)!!.groupValues[1]
}
```

After project creation, also enable graphmesh on it. Update the function:

```kotlin
@Autowired lateinit var projectStorage: com.agentwork.productspecagent.storage.ProjectStorage

private fun createProject(): String {
    val result = mockMvc.perform(
        post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Doc Test"}""")
    ).andExpect(status().isCreated).andReturn()
    val pid = """"id"\s*:\s*"([^"]+)"""".toRegex()
        .find(result.response.contentAsString)!!.groupValues[1]
    val project = projectStorage.loadProject(pid)!!
    projectStorage.saveProject(project.copy(graphmeshEnabled = true))
    return pid
}
```

Add the `@Autowired lateinit var projectStorage` field next to the existing `@Autowired lateinit var mockMvc` and `@Autowired lateinit var graphMeshClient` fields.

- [ ] **Step 5: Run all DocumentService + DocumentController tests**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.service.DocumentServiceTest" --tests "com.agentwork.productspecagent.api.DocumentControllerTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the FULL backend suite to catch knock-on effects**

```bash
./gradlew -p backend test
```
Expected: BUILD SUCCESSFUL. (If `ExportControllerTest` breaks because of UploadStorage signature change — its `uploadStorage.save(...)` call needs the new 6-arg form; update accordingly: `uploadStorage.save(pid, "d1", "a.pdf", "application/pdf", byteArrayOf(1, 2, 3), "2026-04-27T10:00:00Z")`.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/DocumentService.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/DocumentServiceTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/DocumentControllerTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt
git commit -m "feat(documents): branch upload/list/get/delete by isGraphMeshActive"
```

(If you also touched a file with `DocumentNotFoundException`, add it to the same commit.)

---

## Task 6: ProjectService — `setGraphMeshEnabled` + 409 endpoint

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceGraphMeshToggleTest.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerTest.kt` (or whichever existing controller test file covers PATCH on projects)

- [ ] **Step 1: Find the existing exception handler and ProjectService structure**

Read the relevant files:

```bash
cat backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt
cat backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt | head -50
cat backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt
```

The new `GraphMeshDisabledException` class will live alongside the existing `ProjectNotFoundException` (find that file with `grep -rn "class ProjectNotFoundException" backend/src/main/kotlin`). Add it to the same file.

- [ ] **Step 2: Failing test for service-level validation**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceGraphMeshToggleTest.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshConfig
import com.agentwork.productspecagent.storage.ProjectStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import java.nio.file.Path
import java.time.Duration

class ProjectServiceGraphMeshToggleTest {

    @TempDir lateinit var tempDir: Path

    @Test
    fun `setGraphMeshEnabled true succeeds when backend enabled`() {
        val (storage, service) = build(backendEnabled = true)
        val pid = createProject(storage)

        val updated = service.setGraphMeshEnabled(pid, true)

        assertTrue(updated.graphmeshEnabled)
        assertTrue(storage.loadProject(pid)!!.graphmeshEnabled)
    }

    @Test
    fun `setGraphMeshEnabled false always succeeds`() {
        val (storage, service) = build(backendEnabled = false)
        val pid = createProject(storage)
        // First force-flag it on via storage
        val p = storage.loadProject(pid)!!
        storage.saveProject(p.copy(graphmeshEnabled = true))

        val updated = service.setGraphMeshEnabled(pid, false)

        assertFalse(updated.graphmeshEnabled)
    }

    @Test
    fun `setGraphMeshEnabled true throws when backend disabled`() {
        val (storage, service) = build(backendEnabled = false)
        val pid = createProject(storage)

        assertThrows(GraphMeshDisabledException::class.java) {
            service.setGraphMeshEnabled(pid, true)
        }
    }

    @Test
    fun `setGraphMeshEnabled throws ProjectNotFoundException for unknown id`() {
        val (_, service) = build(backendEnabled = true)

        assertThrows(ProjectNotFoundException::class.java) {
            service.setGraphMeshEnabled("missing", true)
        }
    }

    private fun build(backendEnabled: Boolean): Pair<ProjectStorage, ProjectService> {
        val storage = ProjectStorage(tempDir.toString())
        val config = GraphMeshConfig(enabled = backendEnabled, url = "http://unused", requestTimeout = Duration.ofSeconds(1))
        // ProjectService likely has more dependencies; the test will need to pass nulls or test doubles for them.
        // Inspect the ProjectService constructor and provide minimum viable arguments.
        // Placeholder: use a minimal ProjectService that only needs storage + config for setGraphMeshEnabled.
        val service = ProjectService(storage, config /* + other dependencies as needed */)
        return storage to service
    }

    private fun createProject(storage: ProjectStorage): String {
        val pid = "p-test"
        val project = com.agentwork.productspecagent.domain.Project(
            id = pid, name = "Demo", ownerId = "u1",
            status = com.agentwork.productspecagent.domain.ProjectStatus.DRAFT,
            createdAt = "x", updatedAt = "x"
        )
        storage.saveProject(project)
        return pid
    }
}
```

**Important:** the `ProjectService` constructor likely has more parameters than just `storage` and `config`. Before running tests, READ the existing `ProjectService` constructor and adjust the test's `build(...)` helper to pass all required dependencies. If most of those dependencies are not used by `setGraphMeshEnabled`, pass simple stubs (or look at how other tests construct `ProjectService`).

If wiring `ProjectService` directly is too painful, switch this test to a `@SpringBootTest` integration style that exercises through `@Autowired ProjectService`, and use `@TestPropertySource` with `graphmesh.enabled=false` or `=true` per test class. Two test classes (one per config value) is acceptable.

Choose the simpler path. Document the choice in the test class docstring.

- [ ] **Step 3: Run — fails (method doesn't exist)**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.service.ProjectServiceGraphMeshToggleTest"
```
Expected: compile error or method-not-found.

- [ ] **Step 4: Add the exception class**

In the file containing `ProjectNotFoundException` (e.g., `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectNotFoundException.kt`), add at the end:

```kotlin
class GraphMeshDisabledException : RuntimeException("GraphMesh is disabled in backend config")
```

- [ ] **Step 5: Add `setGraphMeshEnabled` to `ProjectService`**

Open `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`. The class already has a constructor — add `private val graphMeshConfig: GraphMeshConfig` to it (add to existing parameter list, ideally at the end with a default-less style consistent with the rest).

Add this method to the class body:

```kotlin
    fun setGraphMeshEnabled(projectId: String, enabled: Boolean): Project {
        if (enabled && !graphMeshConfig.enabled) {
            throw GraphMeshDisabledException()
        }
        val project = projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        val updated = project.copy(graphmeshEnabled = enabled, updatedAt = java.time.Instant.now().toString())
        projectStorage.saveProject(updated)
        return updated
    }
```

(`Project` import + `GraphMeshConfig` import + `ProjectStorage` field are presumably already there. If not, add them.)

- [ ] **Step 6: Add the PATCH endpoint to `ProjectController`**

In `backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt`, add this method (and the request DTO) inside the controller class:

```kotlin
    data class SetGraphMeshEnabledRequest(val enabled: Boolean)

    @PatchMapping("/{id}/graphmesh-enabled")
    fun setGraphMeshEnabled(
        @PathVariable id: String,
        @RequestBody body: SetGraphMeshEnabledRequest
    ): Project = projectService.setGraphMeshEnabled(id, body.enabled)
```

Add `org.springframework.web.bind.annotation.PatchMapping` and `RequestBody` imports if not already present.

- [ ] **Step 7: Map `GraphMeshDisabledException` → 409 in the global handler**

In `backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt`, add:

```kotlin
    @ExceptionHandler(GraphMeshDisabledException::class)
    fun handleGraphMeshDisabled(e: GraphMeshDisabledException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(409).body(mapOf("error" to "GRAPHMESH_DISABLED_BACKEND"))
```

Imports: `org.springframework.http.ResponseEntity`, `org.springframework.web.bind.annotation.ExceptionHandler`, and the `GraphMeshDisabledException` class. Pattern-match the existing handlers in the file.

- [ ] **Step 8: Add a controller-level integration test**

Find or create `backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerTest.kt`. (If it doesn't exist, create one based on the pattern from `DocumentControllerTest`.) Add these tests:

```kotlin
    @Test
    fun `PATCH graphmesh-enabled succeeds when backend enabled`() {
        val pid = createProject()
        mockMvc.perform(
            patch("/api/v1/projects/$pid/graphmesh-enabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.graphmeshEnabled").value(true))
    }
```

Remember to import `patch` from `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch`.

For the 409 case, since the test profile sets `graphmesh.enabled=true`, you can use `@TestPropertySource(properties = ["graphmesh.enabled=false"])` on a separate test class:

```kotlin
package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["graphmesh.enabled=false"])
class ProjectControllerGraphMeshDisabledTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"GM Off Test"}""")
        ).andExpect(status().isCreated).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `PATCH graphmesh-enabled returns 409 when backend disabled`() {
        val pid = createProject()
        mockMvc.perform(
            patch("/api/v1/projects/$pid/graphmesh-enabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("GRAPHMESH_DISABLED_BACKEND"))
    }
}
```

Save it as `backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerGraphMeshDisabledTest.kt`.

- [ ] **Step 9: Run all affected tests**

```bash
./gradlew -p backend test --tests "*ProjectService*" --tests "*ProjectController*"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectNotFoundException.kt backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceGraphMeshToggleTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/ProjectControllerGraphMeshDisabledTest.kt
git commit -m "feat(projects): PATCH /graphmesh-enabled with backend validation"
```

If any of those files does not exist (e.g., `ProjectControllerTest.kt`), drop it from the `git add` line. Use `git status` first to see what changed.

---

## Task 7: ConfigController — `GET /api/v1/config/features`

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/ConfigController.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ConfigControllerTest.kt`

- [ ] **Step 1: Failing test**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/api/ConfigControllerTest.kt`:

```kotlin
package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class ConfigControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `GET features returns graphmeshEnabled true (test profile)`() {
        mockMvc.perform(get("/api/v1/config/features"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.graphmeshEnabled").value(true))
    }
}
```

(Test profile yml has `graphmesh.enabled: true` after Task 1.)

- [ ] **Step 2: Run — fails (404)**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.api.ConfigControllerTest"
```
Expected: 404 / failure.

- [ ] **Step 3: Create the controller**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/api/ConfigController.kt`:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshConfig
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/config")
class ConfigController(private val graphMeshConfig: GraphMeshConfig) {

    @GetMapping("/features")
    fun features(): Map<String, Any> = mapOf(
        "graphmeshEnabled" to graphMeshConfig.enabled
    )
}
```

- [ ] **Step 4: Run — passes**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.api.ConfigControllerTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/ConfigController.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/ConfigControllerTest.kt
git commit -m "feat(config): GET /api/v1/config/features endpoint"
```

---

## Task 8: Frontend api.ts — types and new functions

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: Extend `Project`, `DocumentState`, add `FeatureFlags`, add two new functions**

In `frontend/src/lib/api.ts`:

**A) Replace `Project` interface (around line 67):**

```ts
export interface Project {
  id: string;
  name: string;
  ownerId: string;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
  graphmeshEnabled?: boolean;
}
```

**B) Replace `DocumentState` (around line 470):**

```ts
export type DocumentState = "UPLOADED" | "PROCESSING" | "EXTRACTED" | "FAILED" | "LOCAL";
```

**C) Add `FeatureFlags` interface and new functions** (after the `exportProject` function, near the end of the file but BEFORE `// ─── Wizard Chat Types ───` if such a separator exists):

```ts
export interface FeatureFlags {
  graphmeshEnabled: boolean;
}

export async function getFeatures(): Promise<FeatureFlags> {
  return apiFetch<FeatureFlags>("/api/v1/config/features");
}

export async function setProjectGraphMeshEnabled(projectId: string, enabled: boolean): Promise<Project> {
  return apiFetch<Project>(`/api/v1/projects/${projectId}/graphmesh-enabled`, {
    method: "PATCH",
    body: JSON.stringify({ enabled }),
  });
}
```

- [ ] **Step 2: Lint**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run lint
```
Expected: no NEW errors (pre-existing errors are out of scope).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(api): types and functions for graphmesh toggle"
```

---

## Task 9: Frontend stores — feature flag + LOCAL state handling

**Files:**
- Create: `frontend/src/lib/stores/feature-store.ts`
- Modify: `frontend/src/lib/stores/document-store.ts`
- Modify: `frontend/src/components/documents/DocumentsPanel.tsx`

- [ ] **Step 1: Create `feature-store.ts`**

Create `frontend/src/lib/stores/feature-store.ts`:

```ts
import { create } from "zustand";
import { getFeatures, type FeatureFlags } from "@/lib/api";

interface FeatureState {
  flags: FeatureFlags | null;
  loading: boolean;
  loadFeatures: () => Promise<void>;
}

export const useFeatureStore = create<FeatureState>((set, get) => ({
  flags: null,
  loading: false,
  loadFeatures: async () => {
    if (get().flags || get().loading) return;
    set({ loading: true });
    try {
      const flags = await getFeatures();
      set({ flags, loading: false });
    } catch {
      set({ flags: { graphmeshEnabled: false }, loading: false });
    }
  },
}));
```

(Fail-closed default: if backend can't be reached, treat features as disabled.)

- [ ] **Step 2: Update `document-store.ts` polling to treat `LOCAL` as terminal**

In `frontend/src/lib/stores/document-store.ts`, locate the line:

```ts
const TERMINAL_STATES: ProjectDocument["state"][] = ["EXTRACTED", "FAILED"];
```

Replace with:

```ts
const TERMINAL_STATES: ProjectDocument["state"][] = ["EXTRACTED", "FAILED", "LOCAL"];
```

- [ ] **Step 3: Add `LOCAL` style to `DocumentsPanel`**

In `frontend/src/components/documents/DocumentsPanel.tsx`, locate `STATE_STYLES`:

```ts
const STATE_STYLES: Record<DocumentState, string> = {
  UPLOADED: "bg-muted text-muted-foreground",
  PROCESSING: "bg-blue-500/20 text-blue-300",
  EXTRACTED: "bg-emerald-500/20 text-emerald-300",
  FAILED: "bg-red-500/20 text-red-300",
};
```

Replace with:

```ts
const STATE_STYLES: Record<DocumentState, string> = {
  UPLOADED: "bg-muted text-muted-foreground",
  PROCESSING: "bg-blue-500/20 text-blue-300",
  EXTRACTED: "bg-emerald-500/20 text-emerald-300",
  FAILED: "bg-red-500/20 text-red-300",
  LOCAL: "bg-muted text-muted-foreground",
};
```

Also locate the line that renders `{doc.state}` and replace it with one that shows "Lokal" for the new state:

```tsx
<span className={cn("rounded-full px-2 py-0.5 text-[10px]", STATE_STYLES[doc.state])}>
  {doc.state === "LOCAL" ? "Lokal" : doc.state}
</span>
```

- [ ] **Step 4: Lint**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run lint
```
Expected: no new errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/stores/feature-store.ts frontend/src/lib/stores/document-store.ts frontend/src/components/documents/DocumentsPanel.tsx
git commit -m "feat(frontend): feature-store + LOCAL terminal-state handling"
```

---

## Task 10: Workspace header settings popover with GraphMesh toggle

**Files:**
- Modify: `frontend/src/app/projects/[id]/page.tsx`
- Create: `frontend/src/components/workspace/GraphMeshToggle.tsx`

- [ ] **Step 1: Create the toggle component**

Create `frontend/src/components/workspace/GraphMeshToggle.tsx`:

```tsx
"use client";

import { useEffect, useRef, useState } from "react";
import { Settings } from "lucide-react";
import { useFeatureStore } from "@/lib/stores/feature-store";
import { setProjectGraphMeshEnabled, type Project } from "@/lib/api";
import { cn } from "@/lib/utils";

interface Props {
  project: Project;
  onProjectUpdate: (project: Project) => void;
}

export function GraphMeshToggle({ project, onProjectUpdate }: Props) {
  const { flags, loadFeatures } = useFeatureStore();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => { loadFeatures(); }, [loadFeatures]);

  useEffect(() => {
    if (!open) return;
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    function onEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    document.addEventListener("keydown", onEscape);
    return () => {
      document.removeEventListener("mousedown", onClickOutside);
      document.removeEventListener("keydown", onEscape);
    };
  }, [open]);

  const backendEnabled = flags?.graphmeshEnabled ?? false;
  const checked = project.graphmeshEnabled ?? false;
  const disabled = !backendEnabled || saving;

  async function handleToggle(next: boolean) {
    setError(null);
    setSaving(true);
    try {
      const updated = await setProjectGraphMeshEnabled(project.id, next);
      onProjectUpdate(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Update failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center justify-center rounded-md p-1.5 text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
        title="Project settings"
      >
        <Settings size={14} />
      </button>
      {open && (
        <div className="absolute right-0 top-full mt-1 z-50 w-72 rounded-md border bg-card shadow-md p-3">
          <div className="text-xs font-medium mb-2">Projekt-Einstellungen</div>
          <label className={cn("flex items-start gap-2 text-xs", disabled && "opacity-60")}>
            <input
              type="checkbox"
              checked={checked}
              disabled={disabled}
              onChange={(e) => handleToggle(e.target.checked)}
              className="mt-0.5 accent-primary"
            />
            <div>
              <div className="font-medium">GraphMesh aktivieren</div>
              <div className="text-[10px] text-muted-foreground mt-0.5">
                {backendEnabled
                  ? "Dokumente werden zusätzlich an GraphMesh gesendet (RAG)."
                  : "Im Backend deaktiviert (application.yml). GraphMesh kann nicht aktiviert werden."}
              </div>
            </div>
          </label>
          {error && <div className="text-[10px] text-destructive mt-2">{error}</div>}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Wire it into the workspace header**

In `frontend/src/app/projects/[id]/page.tsx`, find the header block:

```tsx
<header className="flex shrink-0 items-center gap-3 border-b border-border bg-card px-4 py-2.5">
  ...
  <span className="text-sm font-medium truncate max-w-xs">{project?.name ?? "..."}</span>
  <div className="ml-auto flex items-center gap-2">
    <Button variant="ghost" size="sm" onClick={() => setShowExplorer(!showExplorer)} className="gap-1.5" title="Toggle Explorer">
      <FolderTree size={14} />
    </Button>
    ...
```

After the `<span>{project?.name ...}` and before the closing `</div>` (the `ml-auto` div), insert the toggle. The simplest place is *inside* the `ml-auto` div as the FIRST child:

```tsx
<div className="ml-auto flex items-center gap-2">
  {project && <GraphMeshToggle project={project} onProjectUpdate={(p) => useProjectStore.setState({ project: p })} />}
  <Button variant="ghost" size="sm" onClick={() => setShowExplorer(!showExplorer)} ...
```

Add the import at the top of the file:

```tsx
import { GraphMeshToggle } from "@/components/workspace/GraphMeshToggle";
```

- [ ] **Step 3: Lint**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run lint
```
Expected: no new errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/workspace/GraphMeshToggle.tsx frontend/src/app/projects/\[id\]/page.tsx
git commit -m "feat(workspace): GraphMesh toggle popover in header"
```

---

## Task 11: Manual end-to-end smoke test

Backend and frontend need a fresh start so all new beans, controllers and endpoints are in place. GraphMesh container must be running on `localhost:8083` for full coverage; without it, only the local-only path can be exercised.

```bash
# Restart backend (kill any prior bootRun process first)
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/backend && ./gradlew bootRun --quiet

# Frontend in second terminal
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run dev
```

Walk through these in the browser:

- [ ] **Step 1: Backend defaults to off — verify endpoint**

`GET http://localhost:8081/api/v1/config/features` → `{"graphmeshEnabled": false}` (because the production yml defaults to `false` unless `GRAPHMESH_ENABLED=true` is set in env).

If you want to test the on-mode, restart backend with `GRAPHMESH_ENABLED=true ./gradlew bootRun --quiet`. The smoke test below assumes the on-mode for the full path.

- [ ] **Step 2: New project starts with `graphmeshEnabled=false`**

Open the app, create a new project, click the gear icon in the workspace header → popover shows the GraphMesh checkbox unchecked.

- [ ] **Step 3: Local-only upload**

With the toggle still off, open the Documents tab, upload a `.pdf` and a `.md`. Both appear instantly with a grey "Lokal" badge. No polling requests in the Network tab.

- [ ] **Step 4: Files visible in explorer**

Open the Explorer tab, hit refresh. Both files appear under `uploads/`. The `.md` opens with markdown highlighting; the `.pdf` opens with the "Binärdatei" hint.

- [ ] **Step 5: Delete in local-only mode**

Click the trash icon on one of the documents. It disappears from the panel, and after an explorer refresh, also from `uploads/`.

- [ ] **Step 6: Switch to GraphMesh mode**

Click the gear, check "GraphMesh aktivieren". Toggle saves immediately. Upload another `.pdf` — this time a non-Lokal state shows (e.g. `UPLOADED` → `PROCESSING` → `EXTRACTED`). Polling visible in Network tab.

- [ ] **Step 7: Backend off mode**

Stop backend, restart with `GRAPHMESH_ENABLED=false ./gradlew bootRun --quiet`. Reload the project workspace. The gear popover now shows the toggle as **disabled** with the `"Im Backend deaktiviert"` hint, regardless of project state.

- [ ] **Step 8: Migration of legacy index**

Stop everything. Find an existing project that was uploaded to before this PR (i.e. has a `.index.json` in old `{docId: filename}` format). Confirm via:

```bash
cat data/projects/<id>/uploads/.index.json
```

Restart backend with `GRAPHMESH_ENABLED=false`, set the project's `graphmeshEnabled=true` via PATCH (curl or via UI), then call `GET /api/v1/projects/<id>/documents` — the response is the migrated GraphMesh list. Now switch the project back to local-only by unchecking, call list again — the response is the local-only list and the index.json on disk has been upgraded to the new `{ "documents": [...] }` shape. Verify with `cat`.

- [ ] **Step 9: Export with mixed history**

In a project with mixed-mode documents, run an export with the "Documents" checkbox enabled. The unzipped archive should contain `<prefix>/uploads/<filename>` for every saved upload regardless of mode.

If all steps green, the feature is done.

---

## Self-Review

**1. Spec coverage:**
- Backend `graphmesh.enabled` (default false) → Task 1 ✓
- `Project.graphmeshEnabled` (default false) → Task 3 ✓
- Backwards-compat for old project.json → Task 3 (test included) ✓
- `GET /api/v1/config/features` → Task 7 ✓
- `PATCH /api/v1/projects/{id}/graphmesh-enabled` with 409 → Task 6 ✓
- Workspace-header toggle, disabled state when backend off → Task 10 ✓
- Local-only upload writes UUID + LOCAL state, no GraphMesh call → Task 5 ✓
- List/Get/Delete in local-only mode → Task 5 ✓
- DocumentState `LOCAL` (terminal, no polling) → Task 2 + Task 9 ✓
- DocumentsPanel "Lokal" badge → Task 9 ✓
- `.index.json` schema upgrade with metadata + legacy migration → Task 4 ✓
- Project that switches modes keeps its `collectionId` → Task 5 (existing logic, unchanged) ✓

**2. Placeholder scan:** No TBD/TODO/"add error handling" placeholders found. Each step has either a concrete code block, a precise edit instruction, or a verification command.

**3. Type consistency:**
- `UploadStorage.save` 6-arg `(projectId, docId, title, mimeType, bytes, createdAt)` is consistent across Task 4 (definition), Task 5 (DocumentService caller, FakeUploadStorage), and the existing test fix-up in Task 5 step 6.
- `DocumentState.LOCAL` is added in Task 2, used as terminal state in `DocumentsPanel` and polling logic (Task 9), and as the marker in `UploadStorage.IndexEntry.toDocument()` (Task 4).
- `Project.graphmeshEnabled` (Kotlin Boolean default false) ↔ `Project.graphmeshEnabled?: boolean` (TS optional) — TS optional matches the Kotlin default-aware deserialization for older project.json files.
- `GraphMeshConfig.enabled` introduced in Task 1, consumed by `DocumentService` (Task 5), `ProjectService.setGraphMeshEnabled` (Task 6), and `ConfigController` (Task 7).
- `GraphMeshDisabledException` introduced in Task 6 with mapping to 409, tested in `ProjectControllerGraphMeshDisabledTest`.
- `FeatureFlags` shape `{ graphmeshEnabled: boolean }` is consistent between backend response (Task 7) and frontend type (Task 8) and store (Task 9).
