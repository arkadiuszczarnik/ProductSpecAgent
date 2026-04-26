# Feature 28 Erweiterung — Lokale Persistenz + Explorer + Export

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hochgeladene Dokumente parallel zur GraphMesh-Speicherung lokal in `data/projects/{id}/uploads/` ablegen, im Frontend-Explorer-Tree anzeigen, beim Klick auf Binärdateien einen Hinweis zeigen und im ZIP-Export optional einbeziehen.

**Architecture:** GraphMesh bleibt Source of Truth für Document-Liste/States. Eine neue `UploadStorage`-Klasse spiegelt die Datei-Bytes ins lokale Filesystem mit Auto-Rename bei Konflikten und einer Index-Datei `uploads/.index.json`, die `documentId → filename` mappt. Lokale Schreib-Fehler beim Upload bleiben tolerant (logged, kein Rollback). Der `FileController` erkennt Binärdateien per Extension-Whitelist, der `SpecFileViewer` zeigt einen Hinweis statt Code-Highlight. `ExportService` erhält einen optionalen `includeDocuments`-Branch.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, kotlinx.serialization (Map<String,String> für Index), Next.js 16, React 19, Tailwind 4. Tests: JUnit 5 mit `@TempDir` (Pure-Storage-Tests) und `@SpringBootTest`/`@AutoConfigureMockMvc` (Controller-Level).

---

## Task 1: UploadStorage — neue Klasse mit TDD

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt`

- [ ] **Step 1: Test-Datei mit allen Tests anlegen (failing)**

```kotlin
package com.agentwork.productspecagent.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UploadStorageTest {

    @TempDir lateinit var tempDir: Path

    private fun storage() = UploadStorage(tempDir.toString())

    @Test
    fun `save writes file under uploads and returns sanitized filename`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "spec.pdf", byteArrayOf(1, 2, 3))

        assertEquals("spec.pdf", name)
        val file = tempDir.resolve("projects/p1/uploads/spec.pdf")
        assertTrue(Files.exists(file))
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(file))
    }

    @Test
    fun `save persists docId-filename mapping in index`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", byteArrayOf(1))

        val index = tempDir.resolve("projects/p1/uploads/.index.json")
        assertTrue(Files.exists(index))
        assertTrue(Files.readString(index).contains("\"doc-1\""))
        assertTrue(Files.readString(index).contains("\"spec.pdf\""))
    }

    @Test
    fun `save with duplicate title appends auto-rename suffix`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", byteArrayOf(1))
        val second = s.save("p1", "doc-2", "spec.pdf", byteArrayOf(2))
        val third = s.save("p1", "doc-3", "spec.pdf", byteArrayOf(3))

        assertEquals("spec (2).pdf", second)
        assertEquals("spec (3).pdf", third)
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec.pdf")))
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec (2).pdf")))
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec (3).pdf")))
    }

    @Test
    fun `save sanitizes path-traversal characters`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "../../etc/passwd", byteArrayOf(1))

        assertFalse(name.contains(".."))
        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
    }

    @Test
    fun `save with blank title falls back to document`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "", byteArrayOf(1))

        assertEquals("document", name)
    }

    @Test
    fun `delete removes file and index entry`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", byteArrayOf(1))

        s.delete("p1", "doc-1")

        assertFalse(Files.exists(tempDir.resolve("projects/p1/uploads/spec.pdf")))
        val index = tempDir.resolve("projects/p1/uploads/.index.json")
        assertFalse(Files.readString(index).contains("\"doc-1\""))
    }

    @Test
    fun `delete is idempotent for missing docId`() {
        val s = storage()
        // No save first — index does not exist
        s.delete("p1", "missing")  // must not throw
    }

    @Test
    fun `list returns filenames excluding index`() {
        val s = storage()
        s.save("p1", "doc-1", "a.md", byteArrayOf(1))
        s.save("p1", "doc-2", "b.pdf", byteArrayOf(2))

        val files = s.list("p1")

        assertEquals(setOf("a.md", "b.pdf"), files.toSet())
    }

    @Test
    fun `list returns empty list when no uploads directory exists`() {
        val s = storage()
        assertEquals(emptyList<String>(), s.list("never-touched"))
    }
}
```

- [ ] **Step 2: Test laufen lassen — alle FAIL wegen fehlender Klasse**

Run from `/Users/czarnik/IdeaProjects/ProductSpecAgent`:
```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.storage.UploadStorageTest"
```
Expected: Compilation error (`UploadStorage` not defined).

- [ ] **Step 3: UploadStorage-Klasse implementieren**

```kotlin
package com.agentwork.productspecagent.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class UploadStorage(
    @Value("\${app.data-path}") private val dataPath: String
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun uploadsDir(projectId: String): Path =
        Paths.get(dataPath, "projects", projectId, "uploads")

    private fun indexFile(projectId: String): Path =
        uploadsDir(projectId).resolve(".index.json")

    fun save(projectId: String, docId: String, title: String, bytes: ByteArray): String {
        val dir = uploadsDir(projectId)
        Files.createDirectories(dir)

        val sanitized = sanitizeFilename(title)
        val filename = uniqueFilename(dir, sanitized)
        Files.write(dir.resolve(filename), bytes)

        val index = readIndex(projectId).toMutableMap()
        index[docId] = filename
        writeIndex(projectId, index)

        return filename
    }

    fun delete(projectId: String, docId: String) {
        val index = readIndex(projectId).toMutableMap()
        val filename = index.remove(docId) ?: return
        val file = uploadsDir(projectId).resolve(filename)
        Files.deleteIfExists(file)
        writeIndex(projectId, index)
    }

    fun list(projectId: String): List<String> {
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

    private fun readIndex(projectId: String): Map<String, String> {
        val file = indexFile(projectId)
        if (!Files.exists(file)) return emptyMap()
        return json.decodeFromString(Files.readString(file))
    }

    private fun writeIndex(projectId: String, index: Map<String, String>) {
        val file = indexFile(projectId)
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(index))
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

- [ ] **Step 4: Tests laufen lassen — alle PASS**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.storage.UploadStorageTest"
```
Expected: BUILD SUCCESSFUL, 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt
git commit -m "feat(storage): add UploadStorage for local document copies"
```

---

## Task 2: DocumentService — UploadStorage einbinden

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DocumentService.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/DocumentServiceTest.kt`

- [ ] **Step 1: Failing-Test für lokale Spiegelung beim Upload schreiben**

In `DocumentServiceTest.kt`: das `fixtures()`-Helper erzeugt aktuell nur `ProjectStorage` und `FakeClient`. Wir brauchen zusätzlich einen `FakeUploadStorage`, den wir injecten und assertieren können.

Ergänze in `DocumentServiceTest.kt` — füge nach der `FakeClient`-Klasse eine zweite Fake-Klasse hinzu und passe `fixtures()` an:

```kotlin
import com.agentwork.productspecagent.storage.UploadStorage

private class FakeUploadStorage : UploadStorage("unused-test-path") {
    val saved = mutableMapOf<String, Triple<String, String, ByteArray>>()  // docId → (projectId, filename, bytes)
    val deleted = mutableListOf<Pair<String, String>>()  // (projectId, docId)
    var throwOnSave: Boolean = false
    override fun save(projectId: String, docId: String, title: String, bytes: ByteArray): String {
        if (throwOnSave) throw java.io.IOException("disk full")
        saved[docId] = Triple(projectId, title, bytes)
        return title
    }
    override fun delete(projectId: String, docId: String) {
        deleted += projectId to docId
    }
}
```

Ändere `fixtures()` auf:

```kotlin
private fun fixtures(): Quad<ProjectStorage, FakeClient, FakeUploadStorage, DocumentService> {
    val storage = ProjectStorage(tempDir.toString())
    val project = Project(
        id = "p1", name = "Demo", ownerId = "u1",
        status = ProjectStatus.DRAFT,
        createdAt = "2026-04-24T10:00:00Z", updatedAt = "2026-04-24T10:00:00Z"
    )
    storage.saveProject(project)
    val client = FakeClient()
    val uploads = FakeUploadStorage()
    val service = DocumentService(storage, client, uploads)
    return Quad(storage, client, uploads, service)
}
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D) {
    operator fun component1() = a; operator fun component2() = b
    operator fun component3() = c; operator fun component4() = d
}
```

Passe alle bestehenden Test-Aufrufe von `fixtures()` an — vorher `Triple`, jetzt `Quad`.
Beispiel: `val (storage, client, service) = fixtures()` → `val (storage, client, _, service) = fixtures()`.

Füge die neuen Tests hinzu:

```kotlin
@Test
fun `upload mirrors document to local UploadStorage after GraphMesh success`() {
    val (_, client, uploads, service) = fixtures()
    client.nextCollectionId = "col-1"

    service.upload("p1", "spec.pdf", "application/pdf", byteArrayOf(7, 8, 9))

    val saved = uploads.saved["d1"]
    assertNotNull(saved)
    assertEquals("p1", saved!!.first)
    assertEquals("spec.pdf", saved.second)
    assertArrayEquals(byteArrayOf(7, 8, 9), saved.third)
}

@Test
fun `upload tolerates local storage failure (GraphMesh result still returned)`() {
    val (_, _, uploads, service) = fixtures()
    uploads.throwOnSave = true

    val doc = service.upload("p1", "spec.pdf", "application/pdf", byteArrayOf(1))

    assertEquals("d1", doc.id)  // Upload succeeded despite local failure
    assertTrue(uploads.saved.isEmpty())
}

@Test
fun `delete removes both GraphMesh entry and local copy`() {
    val (_, _, uploads, service) = fixtures()
    service.upload("p1", "spec.pdf", "application/pdf", byteArrayOf(1))

    service.delete("p1", "d1")

    assertEquals(listOf("p1" to "d1"), uploads.deleted)
}
```

- [ ] **Step 2: Tests laufen lassen — neue Tests FAIL (Compile-Error: DocumentService-Constructor erwartet keinen UploadStorage)**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.service.DocumentServiceTest"
```
Expected: Compilation error.

- [ ] **Step 3: DocumentService anpassen — UploadStorage injecten und nutzen**

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.domain.Project
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshClient
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshException
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.UploadStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64

@Service
class DocumentService(
    private val projectStorage: ProjectStorage,
    private val graphMeshClient: GraphMeshClient,
    private val uploadStorage: UploadStorage
) {
    private val log = LoggerFactory.getLogger(DocumentService::class.java)

    fun upload(projectId: String, title: String, mimeType: String, content: ByteArray): Document {
        val project = projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        val base64 = Base64.getEncoder().encodeToString(content)
        val existingId = project.collectionId
        val collectionId = existingId ?: createCollection(project)
        val document = try {
            graphMeshClient.uploadDocument(collectionId, title, mimeType, base64)
        } catch (e: GraphMeshException.GraphQlError) {
            if (existingId != null && "COLLECTION_NOT_FOUND" in e.detail) {
                graphMeshClient.uploadDocument(createCollection(project), title, mimeType, base64)
            } else throw e
        }
        try {
            uploadStorage.save(projectId, document.id, title, content)
        } catch (e: Exception) {
            log.warn("Local copy failed for project=$projectId doc=${document.id}: ${e.message}")
        }
        return document
    }

    fun list(projectId: String): List<Document> {
        val project = projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        val collectionId = project.collectionId ?: return emptyList()
        return graphMeshClient.listDocuments(collectionId)
    }

    fun get(projectId: String, documentId: String): Document {
        projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        return graphMeshClient.getDocument(documentId)
    }

    fun delete(projectId: String, documentId: String) {
        projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        graphMeshClient.deleteDocument(documentId)
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

- [ ] **Step 4: DocumentControllerTest anpassen — FakeUploadStorage als Bean registrieren, sonst schreibt der echte UploadStorage in `build/test-data`**

In `DocumentControllerTest.kt`, in der `TestConfig`-Klasse:

```kotlin
@TestConfiguration
class TestConfig {
    @Bean @Primary fun fakeGraphMeshClient(): GraphMeshClient = FakeGraphMeshClient()
    @Bean @Primary fun stubUploadStorage(): com.agentwork.productspecagent.storage.UploadStorage =
        object : com.agentwork.productspecagent.storage.UploadStorage("build/test-uploads-stub") {
            override fun save(projectId: String, docId: String, title: String, bytes: ByteArray) = title
            override fun delete(projectId: String, docId: String) {}
        }
}
```

- [ ] **Step 5: Tests laufen lassen — alle PASS**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.service.DocumentServiceTest" --tests "com.agentwork.productspecagent.api.DocumentControllerTest"
```
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/DocumentService.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/DocumentServiceTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/DocumentControllerTest.kt
git commit -m "feat(documents): mirror uploads to local UploadStorage"
```

---

## Task 3: Binär-Erkennung im FileController

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FileModels.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/FileController.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/FileControllerTest.kt`

- [ ] **Step 1: Failing-Test für Binär-Detection schreiben**

In `FileControllerTest.kt` ergänzen (vor der schließenden `}` der Klasse):

```kotlin
@Test
fun `GET binary file returns binary=true with empty content`() {
    val pid = createProject()
    // Manually place a fake PDF under data/projects/{pid}/uploads/
    val dataPath = java.nio.file.Paths.get("build/test-data/projects/$pid/uploads")
    java.nio.file.Files.createDirectories(dataPath)
    java.nio.file.Files.write(dataPath.resolve("doc.pdf"), byteArrayOf(0x25, 0x50, 0x44, 0x46))  // %PDF magic bytes

    mockMvc.perform(get("/api/v1/projects/$pid/files/uploads/doc.pdf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.binary").value(true))
        .andExpect(jsonPath("$.content").value(""))
        .andExpect(jsonPath("$.name").value("doc.pdf"))
}
```

- [ ] **Step 2: Test laufen lassen — FAIL (binary-Feld nicht in FileContent; FileController liest Binär als String)**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.api.FileControllerTest"
```
Expected: Test FAIL.

- [ ] **Step 3: `binary`-Feld zu `FileContent` hinzufügen**

In `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FileModels.kt`:

```kotlin
@Serializable
data class FileContent(
    val path: String,
    val name: String,
    val content: String,
    val language: String,
    val lineCount: Int,
    val binary: Boolean = false
)
```

- [ ] **Step 4: FileController erkennt Binär per Extension**

In `backend/src/main/kotlin/com/agentwork/productspecagent/api/FileController.kt`, ersetze die `readFile`-Methode:

```kotlin
@GetMapping("/**")
fun readFile(
    @PathVariable projectId: String,
    request: jakarta.servlet.http.HttpServletRequest
): FileContent {
    val dir = projectDir(projectId)
    val prefix = "/api/v1/projects/$projectId/files/"
    val cleanPath = request.requestURI.removePrefix(prefix).removePrefix("/")

    val file = dir.resolve(cleanPath)

    if (!Files.exists(file) || Files.isDirectory(file)) {
        throw ProjectNotFoundException("File not found: $cleanPath")
    }

    if (!file.normalize().startsWith(dir.normalize())) {
        throw ProjectNotFoundException("Invalid path: $cleanPath")
    }

    val name = file.fileName.toString()
    if (isBinary(name)) {
        return FileContent(
            path = cleanPath, name = name, content = "",
            language = "binary", lineCount = 0, binary = true
        )
    }

    val content = Files.readString(file)
    return FileContent(
        path = cleanPath, name = name, content = content,
        language = detectLanguage(name), lineCount = content.lines().size
    )
}

private fun isBinary(filename: String): Boolean {
    val lower = filename.lowercase()
    return BINARY_EXTENSIONS.any { lower.endsWith(it) }
}

companion object {
    private val BINARY_EXTENSIONS = setOf(
        ".pdf", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".zip"
    )
}
```

- [ ] **Step 5: Tests laufen lassen — PASS**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.api.FileControllerTest"
```
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/FileModels.kt backend/src/main/kotlin/com/agentwork/productspecagent/api/FileController.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/FileControllerTest.kt
git commit -m "feat(files): detect binary files by extension and skip readString"
```

---

## Task 4: Export — `includeDocuments`-Toggle

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`

- [ ] **Step 1: Failing-Tests für includeDocuments schreiben**

In `ExportControllerTest.kt` zwei neue Tests vor der schließenden `}` ergänzen:

```kotlin
@Test
fun `POST export with includeDocuments=true bundles uploads folder`() {
    val pid = createProject()
    val uploadsDir = java.nio.file.Paths.get("build/test-data/projects/$pid/uploads")
    java.nio.file.Files.createDirectories(uploadsDir)
    java.nio.file.Files.write(uploadsDir.resolve("a.pdf"), byteArrayOf(1, 2, 3))
    java.nio.file.Files.writeString(uploadsDir.resolve(".index.json"), """{"d1":"a.pdf"}""")

    val result = mockMvc.perform(
        post("/api/v1/projects/$pid/export").contentType(MediaType.APPLICATION_JSON)
            .content("""{"includeDocuments":true}""")
    ).andExpect(status().isOk()).andReturn()

    val entries = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
        var e = zis.nextEntry
        while (e != null) { entries.add(e.name); e = zis.nextEntry }
    }
    assertTrue(entries.any { it.endsWith("uploads/a.pdf") }, "ZIP should contain uploads/a.pdf, got: $entries")
    assertTrue(entries.none { it.endsWith(".index.json") }, "ZIP must not contain .index.json, got: $entries")
}

@Test
fun `POST export with includeDocuments=false skips uploads`() {
    val pid = createProject()
    val uploadsDir = java.nio.file.Paths.get("build/test-data/projects/$pid/uploads")
    java.nio.file.Files.createDirectories(uploadsDir)
    java.nio.file.Files.write(uploadsDir.resolve("a.pdf"), byteArrayOf(1, 2, 3))

    val result = mockMvc.perform(
        post("/api/v1/projects/$pid/export").contentType(MediaType.APPLICATION_JSON)
            .content("""{"includeDocuments":false}""")
    ).andExpect(status().isOk()).andReturn()

    val entries = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
        var e = zis.nextEntry
        while (e != null) { entries.add(e.name); e = zis.nextEntry }
    }
    assertTrue(entries.none { it.contains("uploads/") }, "ZIP must not contain uploads/, got: $entries")
}
```

- [ ] **Step 2: Tests laufen lassen — FAIL (ExportRequest hat kein includeDocuments; ExportService packt uploads/ nicht)**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.api.ExportControllerTest"
```
Expected: Compile-Error oder Test-FAIL.

- [ ] **Step 3: ExportRequest erweitern**

In `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt`:

```kotlin
package com.agentwork.productspecagent.domain

data class ExportRequest(
    val includeDecisions: Boolean = true,
    val includeClarifications: Boolean = true,
    val includeTasks: Boolean = true,
    val includeDocuments: Boolean = true
)
```

- [ ] **Step 4: ExportService.exportProject um uploads-Branch erweitern**

In `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`:

Imports oben ergänzen:
```kotlin
import com.agentwork.productspecagent.storage.UploadStorage
import org.springframework.beans.factory.annotation.Value
import java.nio.file.Files
import java.nio.file.Paths
```

Constructor erweitern:
```kotlin
@Service
class ExportService(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService,
    private val uploadStorage: UploadStorage,
    @Value("\${app.data-path}") private val dataPath: String
) {
```

Innerhalb von `exportProject`, **direkt vor** dem schließenden `}` des `ZipOutputStream.use { zip -> ... }`-Blocks, neuen Branch einfügen:

```kotlin
            // Documents (uploads)
            if (request.includeDocuments) {
                val uploadsDir = Paths.get(dataPath, "projects", projectId, "uploads")
                if (Files.exists(uploadsDir)) {
                    for (filename in uploadStorage.list(projectId)) {
                        val bytes = Files.readAllBytes(uploadsDir.resolve(filename))
                        zip.addBinaryEntry("$prefix/uploads/$filename", bytes)
                    }
                }
            }
```

Am Ende der Klasse zusätzliche Helper-Methode:

```kotlin
    private fun ZipOutputStream.addBinaryEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }
```

- [ ] **Step 5: Tests laufen lassen — alle PASS**

```bash
./gradlew -p backend test --tests "com.agentwork.productspecagent.api.ExportControllerTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt
git commit -m "feat(export): include uploads/ in ZIP via includeDocuments toggle"
```

---

## Task 5: Frontend `api.ts` — Types & Export-Param erweitern

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: `FileContent.binary` ergänzen**

In `frontend/src/lib/api.ts` Zeile ~226:

```ts
export interface FileContent {
  path: string;
  name: string;
  content: string;
  language: string;
  lineCount: number;
  binary?: boolean;
}
```

- [ ] **Step 2: `exportProject`-Funktion um `includeDocuments` erweitern**

In `frontend/src/lib/api.ts` Zeile ~418:

```ts
export async function exportProject(
  projectId: string,
  options: {
    includeDecisions?: boolean;
    includeClarifications?: boolean;
    includeTasks?: boolean;
    includeDocuments?: boolean;
  } = {}
): Promise<Blob> {
  const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      includeDecisions: options.includeDecisions ?? true,
      includeClarifications: options.includeClarifications ?? true,
      includeTasks: options.includeTasks ?? true,
      includeDocuments: options.includeDocuments ?? true,
    }),
  });
  if (!res.ok) throw new Error("Export failed");
  return res.blob();
}
```

- [ ] **Step 3: Frontend-Build checken**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run lint
```
Expected: lint clean.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(api): extend FileContent + exportProject for documents"
```

---

## Task 6: Frontend `SpecFileViewer` — Hinweis bei Binärdateien

**Files:**
- Modify: `frontend/src/components/explorer/SpecFileViewer.tsx`

- [ ] **Step 1: Binary-Branch im openTab + Render**

In `SpecFileViewer.tsx`, ersetze in der `openTab`-Funktion den `try`-Block:

```ts
    try {
      const content = await readProjectFile(projectId, path);
      const html = content.binary
        ? `<div class="flex h-full flex-col items-center justify-center gap-2 p-8 text-center text-muted-foreground"><div class="text-sm font-medium">Binärdatei</div><div class="text-xs">Keine Inline-Vorschau für ${escapeHtml(content.name)}.</div></div>`
        : await highlightCode(content.content, content.language);
      setTabs((prev) =>
        prev.map((t) => (t.path === path ? { ...t, content, html, loading: false } : t))
      );
    } catch {
      setTabs((prev) =>
        prev.map((t) => (t.path === path ? { ...t, loading: false, html: "<pre>Failed to load file</pre>" } : t))
      );
    }
```

Am Ende der Datei (außerhalb der Komponente, vor `highlightCode`) eine kleine Hilfsfunktion ergänzen:

```ts
function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
```

- [ ] **Step 2: Footer-Anzeige für Binär ausblenden (optional)**

Im JSX-Footer am Ende der Komponente, wo aktuell `{currentTab?.content && (...)}` steht, anpassen damit der Footer auch für Binär-Files sinnvoll ist:

```tsx
{currentTab?.content && (
  <div className="flex items-center gap-3 border-t px-4 py-1.5 text-[10px] text-muted-foreground shrink-0">
    <Badge variant="ghost">{currentTab.content.binary ? "binary" : currentTab.content.language}</Badge>
    {!currentTab.content.binary && <span>{currentTab.content.lineCount} lines</span>}
    {!currentTab.content.binary && <span>UTF-8</span>}
    <span className="ml-auto">{currentTab.content.path}</span>
  </div>
)}
```

- [ ] **Step 3: Lint + manuell verifizieren**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run lint
```
Expected: lint clean. Manueller Smoke-Test in Task 8.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/explorer/SpecFileViewer.tsx
git commit -m "feat(explorer): show binary-file hint instead of garbled code"
```

---

## Task 7: Frontend `ExportDialog` — Documents-Checkbox

**Files:**
- Modify: `frontend/src/components/export/ExportDialog.tsx`

- [ ] **Step 1: useState + Checkbox + Übergabe an exportProject**

State-Initializer ergänzen (nach `includeTasks`):

```tsx
const [includeDocuments, setIncludeDocuments] = useState(true);
```

In `handleExport()` den Call anpassen:

```tsx
const blob = await exportProject(projectId, {
  includeDecisions,
  includeClarifications,
  includeTasks,
  includeDocuments,
});
```

In den Card-Content nach der „Tasks & Plan"-Checkbox eine vierte Checkbox einfügen (vor `</CardContent>`):

```tsx
<label className="flex items-center gap-3 rounded-md border px-3 py-2.5 cursor-pointer hover:bg-muted/30 transition-colors">
  <input type="checkbox" checked={includeDocuments} onChange={(e) => setIncludeDocuments(e.target.checked)} className="accent-primary" />
  <div>
    <span className="text-sm font-medium">Documents</span>
    <p className="text-xs text-muted-foreground">Hochgeladene Dateien aus uploads/</p>
  </div>
</label>
```

- [ ] **Step 2: Lint**

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent/frontend && npm run lint
```
Expected: lint clean.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/export/ExportDialog.tsx
git commit -m "feat(export-dialog): add Documents checkbox (default on)"
```

---

## Task 8: Manueller End-to-End-Smoke-Test

Backend und Frontend neu starten:

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent
# Backend
(cd backend && ./gradlew bootRun --quiet) &
# Frontend
(cd frontend && npm run dev) &
```

Stelle sicher, dass GraphMesh läuft (`http://localhost:8083/graphql`).

Im Browser Schritt für Schritt durchgehen:

- [ ] **Step 1:** Projekt öffnen → Documents-Tab → PDF hochladen.
- [ ] **Step 2:** In Explorer-Tab klicken → Refresh-Button → `uploads/<filename>.pdf` ist sichtbar.
- [ ] **Step 3:** Auf die PDF im Tree klicken → Modal zeigt Hinweis „Binärdatei – Keine Inline-Vorschau".
- [ ] **Step 4:** Markdown-Datei hochladen → in Explorer Klick → Markdown wird wie gewohnt mit Syntax-Highlight angezeigt.
- [ ] **Step 5:** Zweites Mal dieselbe PDF hochladen → in Explorer erscheint zusätzlich `<name> (2).pdf`. Im Documents-Panel werden beide als gleicher Title gelistet (Bestand).
- [ ] **Step 6:** Documents-Panel: Trash-Icon einer PDF anklicken → File verschwindet aus Liste UND nach Refresh aus Explorer.
- [ ] **Step 7:** Export-Dialog öffnen → vierte Checkbox „Documents" sichtbar und an. Export starten → entpacktes ZIP enthält `<prefix>/uploads/...`, kein `.index.json`.
- [ ] **Step 8:** Export erneut, „Documents" diesmal abwählen → entpacktes ZIP enthält keinen `uploads/`-Ordner.
- [ ] **Step 9:** Projekt-Ordner auf Disk inspizieren: `data/projects/{id}/uploads/.index.json` existiert mit `{docId: filename}` für lebende Dokumente.

Wenn alle Schritte grün sind: Done.

---

## Self-Review-Checkliste

- **Spec-Coverage:** Alle ACs aus dem Erweiterungs-Abschnitt der Feature-Doc sind durch Tasks 1–8 abgedeckt: lokale Speicherung (T1+T2), GraphMesh-first/Fehler-Toleranz (T2), Auto-Rename (T1), Delete-Synchronität (T2), Explorer-Sichtbarkeit (T8), Binär-Hinweis (T3+T6), ExportDialog-Checkbox (T7), Index-Datei (T1), Sanitize (T1).
- **Type-Konsistenz:** `UploadStorage(projectId, docId, title, bytes) → String` durchgehend; `ExportRequest.includeDocuments` einheitlich; `FileContent.binary?: boolean` Backend default `false` / Frontend optional.
- **Reihenfolge:** UploadStorage (T1) wird in DocumentService (T2) und ExportService (T4) verwendet — beide kommen danach. Frontend (T5–T7) folgt auf abgeschlossenes Backend.
