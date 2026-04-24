# Feature 28: Projekt-Dokumente in GraphMesh hochladen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pro Projekt PDF-/Markdown-/Text-Dokumente per Drag-and-Drop hochladen, die in einer GraphMesh-Collection landen; `collectionId` lazy beim ersten Upload erzeugen und im Projekt persistieren; Status (`UPLOADED → PROCESSING → EXTRACTED/FAILED`) im Frontend per Polling anzeigen; Liste und Löschen.

**Architecture:** Backend ist Single Point of Contact zu GraphMesh. Spring `RestClient` schickt GraphQL-Mutations/Queries an `${graphmesh.url}` (default `http://localhost:8083/graphql`). `Project.collectionId` wird in `data/projects/{id}/project.json` persistiert; alle Document-Metadaten leben ausschließlich in GraphMesh (kein lokaler Cache). Frontend zeigt einen neuen "Documents"-Tab in `app/projects/[id]/page.tsx`, polled alle 3 s solange Dokumente nicht-terminal sind.

**Tech Stack:** Backend Kotlin 2.3 + Spring Boot 4 + Spring `RestClient` + kotlinx.serialization. Frontend Next.js 16 + React 19 + Zustand + Tailwind 4. GraphMesh extern via GraphQL.

**Verifizierte Schema-Fakten (per Live-Introspection am 2026-04-24):**
- `Mutation.createCollection(input: CreateCollectionInput!)` — `name!`, `description?`, `tags?`, `metadata?`
- `Mutation.uploadDocument(input: UploadDocumentInput!)` — `collectionId!`, `title!`, `mimeType!`, `content!` (Base64-String), `metadata?`
- `Mutation.deleteDocument(id: ID!)` — direkt `id`, keine Input-Wrapper
- `Query.documents(collectionId: ID!)` und `Query.document(id: ID!)` existieren
- `Document` hat `id, collectionId, parentId?, type: DocumentType, state: DocumentState, title, mimeType, children, metadata, createdAt` — **das Datumsfeld heißt `createdAt`, nicht `uploadedAt`**
- `DocumentState`-Enum: `UPLOADED, PROCESSING, EXTRACTED, FAILED`

---

## Phase 0: Vorab-Verifikation (vor Code)

### Task 0.1: GraphMesh erreichbar prüfen

**Files:** keine

- [ ] **Step 1: GraphMesh starten (manuell)**

Falls nicht laufend: im GraphMesh-Repo `docker-compose up -d && ./gradlew bootRun`. Verifizieren:

```bash
curl -sS http://localhost:8083/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ __schema { mutationType { name } } }"}'
```
Expected: `{"data":{"__schema":{"mutationType":{"name":"Mutation"}}}}`

- [ ] **Step 2: Verifizieren, dass `deleteDocument` weiterhin existiert**

```bash
curl -sS http://localhost:8083/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ __type(name: \"Mutation\") { fields { name } } }"}' \
  | grep -o '"deleteDocument"'
```
Expected: `"deleteDocument"` (eine Zeile). Falls nichts: STOP — Delete-Endpoint aus diesem Plan streichen, in done-doc als Folge-Feature in GraphMesh notieren. Alle Tasks zum Delete-Endpoint im Plan überspringen.

---

## Phase 1: Backend — Domain & Storage

### Task 1.1: `Project.collectionId` hinzufügen + ProjectStorage-Round-Trip-Test

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Project.kt`
- Modify (test): `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`

- [ ] **Step 1: Failing test schreiben — `collectionId` round-trip**

In `ProjectStorageTest.kt` einen Test ergänzen:

```kotlin
@Test
fun `project with collectionId saves and loads correctly`() {
    val storage = ProjectStorage(tempDir.toString())
    val project = Project(
        id = "p1", name = "Demo", ownerId = "u1",
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
    val storage = ProjectStorage(tempDir.toString())
    val dir = tempDir.resolve("projects/p1")
    java.nio.file.Files.createDirectories(dir)
    val legacyJson = """{"id":"p1","name":"Old","ownerId":"u1","status":"DRAFT","createdAt":"x","updatedAt":"y"}"""
    java.nio.file.Files.writeString(dir.resolve("project.json"), legacyJson)
    val loaded = storage.loadProject("p1")!!
    assertNull(loaded.collectionId)
}
```

- [ ] **Step 2: Tests laufen lassen → fail (Compile-Error: `collectionId` not in Project)**

```bash
cd backend && ./gradlew test --tests "*.ProjectStorageTest" -q
```
Expected: COMPILATION FAILED — `Cannot access 'collectionId'`.

- [ ] **Step 3: `Project.collectionId` ergänzen**

`domain/Project.kt`:

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
    val collectionId: String? = null
)
```

- [ ] **Step 4: Tests laufen lassen → pass**

```bash
cd backend && ./gradlew test --tests "*.ProjectStorageTest" -q
```
Expected: BUILD SUCCESSFUL, all green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/Project.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt
git commit -m "feat(domain): add optional Project.collectionId for GraphMesh

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.2: `Document`-Domain-Class

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Document.kt`

- [ ] **Step 1: Datei anlegen**

`domain/Document.kt`:

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

enum class DocumentState {
    UPLOADED, PROCESSING, EXTRACTED, FAILED
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

- [ ] **Step 2: Compile-Check**

```bash
cd backend && ./gradlew compileKotlin -q
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/Document.kt
git commit -m "feat(domain): add Document and DocumentState

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 2: Backend — GraphMesh-Client (Infrastructure)

### Task 2.1: `GraphMeshConfig` (`@ConfigurationProperties`)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshConfig.kt`

- [ ] **Step 1: Datei anlegen**

```kotlin
package com.agentwork.productspecagent.infrastructure.graphmesh

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

@ConfigurationProperties(prefix = "graphmesh")
data class GraphMeshConfig(
    @DefaultValue("http://localhost:8083/graphql") val url: String,
    @DefaultValue("30s") val requestTimeout: Duration
)
```

- [ ] **Step 2: Properties-Class registrieren**

In der Spring-Boot-Hauptklasse (typischerweise `ProductSpecAgentApplication.kt` — verifizieren mit `find backend/src/main -name "*Application.kt"`) sicherstellen, dass `@EnableConfigurationProperties(GraphMeshConfig::class)` oder `@ConfigurationPropertiesScan` aktiv ist. Falls beides fehlt:

```kotlin
@SpringBootApplication
@ConfigurationPropertiesScan
class ProductSpecAgentApplication
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshConfig.kt
# falls Application.kt geändert:
git add backend/src/main/kotlin/com/agentwork/productspecagent/ProductSpecAgentApplication.kt 2>/dev/null
git commit -m "feat(graphmesh): add GraphMeshConfig with default URL and timeout

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 2.2: `GraphMeshException` Sealed Class

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshException.kt`

- [ ] **Step 1: Datei anlegen**

```kotlin
package com.agentwork.productspecagent.infrastructure.graphmesh

sealed class GraphMeshException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class Unavailable(cause: Throwable) : GraphMeshException("GraphMesh is not reachable", cause)
    class GraphQlError(val detail: String) : GraphMeshException("GraphMesh returned errors: $detail")
}
```

- [ ] **Step 2: Compile-Check**

```bash
cd backend && ./gradlew compileKotlin -q
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshException.kt
git commit -m "feat(graphmesh): add GraphMeshException sealed hierarchy

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 2.3: `GraphMeshClient` mit Spring `RestClient` (TDD)

**Test-Strategie:** MockWebServer aus OkHttp. Vorher als Test-Dependency aufnehmen.

**Files:**
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshClient.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshClientTest.kt`

- [ ] **Step 1: Test-Dependency `mockwebserver` hinzufügen**

In `backend/build.gradle.kts` im `dependencies { … }`-Block:

```kotlin
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

```bash
cd backend && ./gradlew dependencies --configuration testRuntimeClasspath -q | grep -i mockwebserver
```
Expected: zeigt `mockwebserver-4.12.0`.

- [ ] **Step 2: Failing test — `createCollection` happy path**

`backend/src/test/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshClientTest.kt`:

```kotlin
package com.agentwork.productspecagent.infrastructure.graphmesh

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class GraphMeshClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GraphMeshClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = GraphMeshClient(GraphMeshConfig(
            url = server.url("/graphql").toString(),
            requestTimeout = Duration.ofSeconds(5)
        ))
    }

    @AfterEach
    fun tearDown() { server.shutdown() }

    @Test
    fun `createCollection returns collection id from GraphQL response`() {
        server.enqueue(MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"data":{"createCollection":{"id":"col-42"}}}"""))

        val id = client.createCollection("My Project")

        assertEquals("col-42", id)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body.readUtf8().contains("\"name\":\"My Project\""))
    }
}
```

- [ ] **Step 3: Test laufen lassen → fail (Compile-Error: GraphMeshClient nicht da)**

```bash
cd backend && ./gradlew test --tests "*.GraphMeshClientTest" -q
```
Expected: COMPILATION FAILED.

- [ ] **Step 4: Minimal-Implementierung `GraphMeshClient`**

`backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshClient.kt`:

```kotlin
package com.agentwork.productspecagent.infrastructure.graphmesh

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.domain.DocumentState
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.ResourceAccessException

@Component
class GraphMeshClient(private val config: GraphMeshConfig) {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(config.url)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(config.requestTimeout)
            setReadTimeout(config.requestTimeout)
        })
        .build()

    fun createCollection(name: String): String {
        val resp = post("""
            mutation Create(${'$'}input: CreateCollectionInput!) {
              createCollection(input: ${'$'}input) { id }
            }
        """.trimIndent(), mapOf("input" to mapOf("name" to name)))
        return (resp["createCollection"] as Map<*, *>)["id"] as String
    }

    fun uploadDocument(collectionId: String, title: String, mimeType: String, contentBase64: String): Document {
        val resp = post("""
            mutation Upload(${'$'}input: UploadDocumentInput!) {
              uploadDocument(input: ${'$'}input) { id title mimeType state createdAt }
            }
        """.trimIndent(), mapOf("input" to mapOf(
            "collectionId" to collectionId,
            "title" to title,
            "mimeType" to mimeType,
            "content" to contentBase64
        )))
        return toDocument(resp["uploadDocument"] as Map<*, *>)
    }

    fun listDocuments(collectionId: String): List<Document> {
        val resp = post("""
            query Docs(${'$'}id: ID!) {
              documents(collectionId: ${'$'}id) { id title mimeType state createdAt }
            }
        """.trimIndent(), mapOf("id" to collectionId))
        @Suppress("UNCHECKED_CAST")
        val list = resp["documents"] as List<Map<*, *>>
        return list.map(::toDocument)
    }

    fun getDocument(documentId: String): Document {
        val resp = post("""
            query Doc(${'$'}id: ID!) {
              document(id: ${'$'}id) { id title mimeType state createdAt }
            }
        """.trimIndent(), mapOf("id" to documentId))
        return toDocument(resp["document"] as Map<*, *>)
    }

    fun deleteDocument(documentId: String) {
        post("""
            mutation Del(${'$'}id: ID!) { deleteDocument(id: ${'$'}id) }
        """.trimIndent(), mapOf("id" to documentId))
    }

    @Suppress("UNCHECKED_CAST")
    private fun post(query: String, variables: Map<String, Any>): Map<String, Any> {
        val body = mapOf("query" to query, "variables" to variables)
        val response = try {
            restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, resp ->
                    throw GraphMeshException.GraphQlError("HTTP ${resp.statusCode.value()}")
                }
                .body(Map::class.java) as Map<String, Any>
        } catch (e: ResourceAccessException) {
            throw GraphMeshException.Unavailable(e)
        }
        response["errors"]?.let { throw GraphMeshException.GraphQlError(it.toString()) }
        @Suppress("UNCHECKED_CAST")
        return response["data"] as Map<String, Any>
    }

    private fun toDocument(m: Map<*, *>) = Document(
        id = m["id"] as String,
        title = m["title"] as String,
        mimeType = m["mimeType"] as String,
        state = DocumentState.valueOf(m["state"] as String),
        createdAt = m["createdAt"] as String
    )
}
```

- [ ] **Step 5: Test laufen lassen → pass**

```bash
cd backend && ./gradlew test --tests "*.GraphMeshClientTest" -q
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Weitere Tests ergänzen — `uploadDocument`, `listDocuments`, `deleteDocument`, Fehlerpfade**

In `GraphMeshClientTest.kt` ergänzen:

```kotlin
@Test
fun `uploadDocument returns parsed Document`() {
    server.enqueue(MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"data":{"uploadDocument":{"id":"d1","title":"spec.pdf","mimeType":"application/pdf","state":"UPLOADED","createdAt":"2026-04-24T10:00:00Z"}}}"""))

    val doc = client.uploadDocument("col-1", "spec.pdf", "application/pdf", "Zm9v")

    assertEquals("d1", doc.id)
    assertEquals(com.agentwork.productspecagent.domain.DocumentState.UPLOADED, doc.state)
    val req = server.takeRequest().body.readUtf8()
    assertTrue(req.contains("\"collectionId\":\"col-1\""))
    assertTrue(req.contains("\"content\":\"Zm9v\""))
}

@Test
fun `listDocuments returns parsed list`() {
    server.enqueue(MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"data":{"documents":[
            {"id":"d1","title":"a","mimeType":"text/plain","state":"EXTRACTED","createdAt":"2026-04-24T10:00:00Z"},
            {"id":"d2","title":"b","mimeType":"application/pdf","state":"PROCESSING","createdAt":"2026-04-24T11:00:00Z"}
        ]}}"""))

    val docs = client.listDocuments("col-1")

    assertEquals(2, docs.size)
    assertEquals("d1", docs[0].id)
    assertEquals(com.agentwork.productspecagent.domain.DocumentState.PROCESSING, docs[1].state)
}

@Test
fun `deleteDocument sends mutation with id`() {
    server.enqueue(MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"data":{"deleteDocument":true}}"""))

    client.deleteDocument("d1")

    val req = server.takeRequest().body.readUtf8()
    assertTrue(req.contains("\"id\":\"d1\""))
}

@Test
fun `GraphQL errors throw GraphQlError`() {
    server.enqueue(MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"errors":[{"message":"collection not found"}],"data":null}"""))

    val ex = assertThrows(GraphMeshException.GraphQlError::class.java) {
        client.listDocuments("nope")
    }
    assertTrue(ex.detail.contains("collection not found"))
}

@Test
fun `unreachable server throws Unavailable`() {
    server.shutdown()
    val ex = assertThrows(GraphMeshException.Unavailable::class.java) {
        client.createCollection("x")
    }
    assertNotNull(ex.cause)
}
```

- [ ] **Step 7: Tests laufen lassen → alle pass**

```bash
cd backend && ./gradlew test --tests "*.GraphMeshClientTest" -q
```
Expected: BUILD SUCCESSFUL — 5 Tests grün.

- [ ] **Step 8: Commit**

```bash
git add backend/build.gradle.kts \
        backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshClient.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshClientTest.kt
git commit -m "feat(graphmesh): add GraphMeshClient with RestClient + MockWebServer tests

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 3: Backend — Service

### Task 3.1: `DocumentService` (TDD mit gemocktem Client)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DocumentService.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/service/DocumentServiceTest.kt`

- [ ] **Step 1: Failing tests schreiben**

`backend/src/test/kotlin/com/agentwork/productspecagent/service/DocumentServiceTest.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshClient
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshConfig
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshException
import com.agentwork.productspecagent.storage.ProjectStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DocumentServiceTest {

    @TempDir lateinit var tempDir: Path

    private fun fixtures(): Triple<ProjectStorage, FakeClient, DocumentService> {
        val storage = ProjectStorage(tempDir.toString())
        val project = Project(
            id = "p1", name = "Demo", ownerId = "u1",
            status = ProjectStatus.DRAFT,
            createdAt = "2026-04-24T10:00:00Z", updatedAt = "2026-04-24T10:00:00Z"
        )
        storage.saveProject(project)
        val client = FakeClient()
        val service = DocumentService(storage, client)
        return Triple(storage, client, service)
    }

    private class FakeClient : GraphMeshClient(GraphMeshConfig("http://unused", java.time.Duration.ofSeconds(1))) {
        var createdCollections = 0
        var lastUploadCollectionId: String? = null
        var nextCollectionId: String = "col-NEW"
        override fun createCollection(name: String): String {
            createdCollections++
            return nextCollectionId
        }
        override fun uploadDocument(collectionId: String, title: String, mimeType: String, contentBase64: String): Document {
            lastUploadCollectionId = collectionId
            return Document("d1", title, mimeType, DocumentState.UPLOADED, "2026-04-24T10:00:00Z")
        }
        override fun listDocuments(collectionId: String): List<Document> = emptyList()
        override fun deleteDocument(documentId: String) {}
        override fun getDocument(documentId: String): Document =
            Document(documentId, "x", "text/plain", DocumentState.EXTRACTED, "z")
    }

    @Test
    fun `first upload creates collection and persists collectionId`() {
        val (storage, client, service) = fixtures()
        client.nextCollectionId = "col-FIRST"

        service.upload("p1", "spec.pdf", "application/pdf", ByteArray(4) { it.toByte() })

        assertEquals(1, client.createdCollections)
        assertEquals("col-FIRST", storage.loadProject("p1")!!.collectionId)
        assertEquals("col-FIRST", client.lastUploadCollectionId)
    }

    @Test
    fun `second upload reuses existing collectionId`() {
        val (_, client, service) = fixtures()
        service.upload("p1", "a.pdf", "application/pdf", ByteArray(2))
        service.upload("p1", "b.pdf", "application/pdf", ByteArray(2))

        assertEquals(1, client.createdCollections)
    }

    @Test
    fun `upload throws PROJECT_NOT_FOUND if project missing`() {
        val (_, _, service) = fixtures()
        assertThrows(ProjectNotFoundException::class.java) {
            service.upload("nope", "a.pdf", "application/pdf", ByteArray(1))
        }
    }
}
```

> Hinweis: Damit `FakeClient` `GraphMeshClient` überschreiben kann, muss `GraphMeshClient` Methoden `open` haben oder `GraphMeshClient` selbst `open class` sein. In Task 2.3 nicht gemacht — hier nachziehen.

- [ ] **Step 2: `GraphMeshClient` zu `open` machen**

In `GraphMeshClient.kt`: `class GraphMeshClient(...)` → `open class GraphMeshClient(...)`. Methoden mit `open fun` versehen. Spring-`@Component` funktioniert auch mit `open`-Klassen (Kotlin-`spring`-Plugin macht `data class`/`@Service` automatisch open, aber Sicherheit zuerst).

- [ ] **Step 3: Test laufen lassen → fail (DocumentService/ProjectNotFoundException nicht da)**

```bash
cd backend && ./gradlew test --tests "*.DocumentServiceTest" -q
```
Expected: COMPILATION FAILED.

- [ ] **Step 4: Falls noch nicht vorhanden, `ProjectNotFoundException` prüfen**

```bash
grep -rn "class ProjectNotFoundException" backend/src/main/kotlin
```
Falls nicht vorhanden, Datei anlegen `service/ProjectNotFoundException.kt`:

```kotlin
package com.agentwork.productspecagent.service

class ProjectNotFoundException(projectId: String) : RuntimeException("Project not found: $projectId")
```

(In `GlobalExceptionHandler` ist diese Exception bereits referenziert — also müsste sie irgendwo existieren. Falls nicht: Klasse anlegen.)

- [ ] **Step 5: `DocumentService` implementieren**

`backend/src/main/kotlin/com/agentwork/productspecagent/service/DocumentService.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshClient
import com.agentwork.productspecagent.storage.ProjectStorage
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64

@Service
class DocumentService(
    private val projectStorage: ProjectStorage,
    private val graphMeshClient: GraphMeshClient
) {

    fun upload(projectId: String, title: String, mimeType: String, content: ByteArray): Document {
        val project = projectStorage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
        val collectionId = project.collectionId ?: run {
            val newId = graphMeshClient.createCollection(project.name)
            projectStorage.saveProject(project.copy(collectionId = newId, updatedAt = Instant.now().toString()))
            newId
        }
        val base64 = Base64.getEncoder().encodeToString(content)
        return graphMeshClient.uploadDocument(collectionId, title, mimeType, base64)
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
    }
}
```

- [ ] **Step 6: Tests laufen lassen → pass**

```bash
cd backend && ./gradlew test --tests "*.DocumentServiceTest" -q
```
Expected: BUILD SUCCESSFUL — 3 Tests grün.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/infrastructure/graphmesh/GraphMeshClient.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/service/DocumentService.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/service/DocumentServiceTest.kt
# falls ProjectNotFoundException neu erstellt:
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectNotFoundException.kt 2>/dev/null
git commit -m "feat(service): add DocumentService with lazy collection creation

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 4: Backend — Controller + Exception-Mappings + Config

### Task 4.1: `application.yml`-Erweiterungen

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`

- [ ] **Step 1: GraphMesh- und Multipart-Konfig hinzufügen (main)**

In `backend/src/main/resources/application.yml` am Ende:

```yaml
graphmesh:
  url: ${GRAPHMESH_URL:http://localhost:8083/graphql}
  request-timeout: 30s

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
```

> Hinweis: Falls `spring:` schon mehrfach im YAML vorkommt, in den existierenden `spring:`-Block einsortieren (YAML duldet keine doppelten Top-Level-Keys mit gleicher Hierarchie).

- [ ] **Step 2: Test-yaml spiegeln (mit MockServer-tauglicher URL)**

In `backend/src/test/resources/application.yml`:

```yaml
graphmesh:
  url: http://localhost:0/graphql  # not used in unit tests; controller test injects mock
  request-timeout: 5s

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
```

- [ ] **Step 3: Build + Tests grün**

```bash
cd backend && ./gradlew test -q
```
Expected: BUILD SUCCESSFUL — alle bisherigen Tests bleiben grün.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application.yml backend/src/test/resources/application.yml
git commit -m "feat(config): add graphmesh.url and multipart limits

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 4.2: `GlobalExceptionHandler`-Erweiterungen

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt`

- [ ] **Step 1: Handler für `GraphMeshException`-Subtypen + Validierungs-Exceptions ergänzen**

Am Ende der Klasse hinzufügen:

```kotlin
@ExceptionHandler(GraphMeshException.Unavailable::class)
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
fun handleGraphMeshUnavailable(ex: GraphMeshException.Unavailable): ErrorResponse =
    ErrorResponse("GRAPHMESH_UNAVAILABLE", ex.message ?: "GraphMesh unreachable", Instant.now().toString())

@ExceptionHandler(GraphMeshException.GraphQlError::class)
@ResponseStatus(HttpStatus.BAD_GATEWAY)
fun handleGraphMeshGraphQlError(ex: GraphMeshException.GraphQlError): ErrorResponse =
    ErrorResponse("GRAPHMESH_ERROR", ex.detail, Instant.now().toString())

@ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException::class)
@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
fun handleMaxUpload(ex: org.springframework.web.multipart.MaxUploadSizeExceededException): ErrorResponse =
    ErrorResponse("FILE_TOO_LARGE", "File exceeds maximum size of 10 MB", Instant.now().toString())

@ExceptionHandler(UnsupportedMediaTypeException::class)
@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
fun handleUnsupportedMime(ex: UnsupportedMediaTypeException): ErrorResponse =
    ErrorResponse("UNSUPPORTED_TYPE", ex.message ?: "Unsupported MIME type", Instant.now().toString())
```

Imports oben ergänzen:

```kotlin
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshException
import com.agentwork.productspecagent.service.UnsupportedMediaTypeException
```

- [ ] **Step 2: `UnsupportedMediaTypeException` anlegen**

`backend/src/main/kotlin/com/agentwork/productspecagent/service/UnsupportedMediaTypeException.kt`:

```kotlin
package com.agentwork.productspecagent.service

class UnsupportedMediaTypeException(actualMime: String) :
    RuntimeException("Unsupported MIME type: $actualMime. Allowed: application/pdf, text/markdown, text/plain")
```

- [ ] **Step 3: Compile-Check**

```bash
cd backend && ./gradlew compileKotlin -q
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/service/UnsupportedMediaTypeException.kt
git commit -m "feat(api): add exception handlers for GraphMesh and upload validation

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 4.3: `DocumentController` (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/DocumentController.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/api/DocumentControllerTest.kt`

- [ ] **Step 1: Failing tests schreiben (FakeClient als Top-Level-Class, damit der Unavailable-Test ihn umkonfigurieren kann)**

`backend/src/test/kotlin/com/agentwork/productspecagent/api/DocumentControllerTest.kt`:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.domain.DocumentState
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshClient
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshConfig
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Duration

class FakeGraphMeshClient : GraphMeshClient(GraphMeshConfig("http://unused", Duration.ofSeconds(1))) {
    var simulateUnavailable = false
    override fun createCollection(name: String): String {
        if (simulateUnavailable) throw GraphMeshException.Unavailable(RuntimeException("down"))
        return "col-test"
    }
    override fun uploadDocument(collectionId: String, title: String, mimeType: String, contentBase64: String) =
        Document("d1", title, mimeType, DocumentState.UPLOADED, "2026-04-24T10:00:00Z")
    override fun listDocuments(collectionId: String) = listOf(
        Document("d1", "a.pdf", "application/pdf", DocumentState.EXTRACTED, "x")
    )
    override fun getDocument(documentId: String) = Document(documentId, "x", "text/plain", DocumentState.EXTRACTED, "z")
    override fun deleteDocument(documentId: String) {}
}

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean @Primary fun fakeGraphMeshClient(): GraphMeshClient = FakeGraphMeshClient()
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var graphMeshClient: GraphMeshClient

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Doc Test"}""")
        ).andExpect(status().isCreated).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `POST upload PDF returns 201 and document body`() {
        val projectId = createProject()
        val file = MockMultipartFile("file", "spec.pdf", "application/pdf", "PDF-CONTENT".toByteArray())
        mockMvc.perform(multipart("/api/v1/projects/$projectId/documents").file(file))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("d1"))
            .andExpect(jsonPath("$.state").value("UPLOADED"))
    }

    @Test
    fun `POST upload with disallowed MIME returns 415`() {
        val projectId = createProject()
        val file = MockMultipartFile("file", "img.png", "image/png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        mockMvc.perform(multipart("/api/v1/projects/$projectId/documents").file(file))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.error").value("UNSUPPORTED_TYPE"))
    }

    @Test
    fun `POST upload without file returns 400`() {
        val projectId = createProject()
        mockMvc.perform(multipart("/api/v1/projects/$projectId/documents"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET list returns documents`() {
        val projectId = createProject()
        val file = MockMultipartFile("file", "spec.pdf", "application/pdf", "x".toByteArray())
        mockMvc.perform(multipart("/api/v1/projects/$projectId/documents").file(file))
            .andExpect(status().isCreated)
        mockMvc.perform(get("/api/v1/projects/$projectId/documents"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].id").value("d1"))
    }

    @Test
    fun `DELETE returns 204`() {
        val projectId = createProject()
        val file = MockMultipartFile("file", "spec.pdf", "application/pdf", "x".toByteArray())
        mockMvc.perform(multipart("/api/v1/projects/$projectId/documents").file(file))
            .andExpect(status().isCreated)
        mockMvc.perform(delete("/api/v1/projects/$projectId/documents/d1"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `POST upload returns 503 when GraphMesh is unavailable`() {
        val fake = graphMeshClient as FakeGraphMeshClient
        fake.simulateUnavailable = true
        try {
            val projectId = createProject()
            val file = MockMultipartFile("file", "spec.pdf", "application/pdf", "x".toByteArray())
            mockMvc.perform(multipart("/api/v1/projects/$projectId/documents").file(file))
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.error").value("GRAPHMESH_UNAVAILABLE"))
        } finally {
            fake.simulateUnavailable = false
        }
    }
}
```

- [ ] **Step 2: Tests laufen lassen → fail (Controller fehlt)**

```bash
cd backend && ./gradlew test --tests "*.DocumentControllerTest" -q
```
Expected: COMPILATION FAILED.

- [ ] **Step 3: `DocumentController` implementieren**

`backend/src/main/kotlin/com/agentwork/productspecagent/api/DocumentController.kt`:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.Document
import com.agentwork.productspecagent.service.DocumentService
import com.agentwork.productspecagent.service.UnsupportedMediaTypeException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/projects/{projectId}/documents")
class DocumentController(private val service: DocumentService) {

    private val allowedMimeTypes = setOf("application/pdf", "text/markdown", "text/plain")

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @PathVariable projectId: String,
        @RequestParam("file") file: MultipartFile
    ): Document {
        val mime = file.contentType ?: throw UnsupportedMediaTypeException("(none)")
        if (mime !in allowedMimeTypes) throw UnsupportedMediaTypeException(mime)
        val title = file.originalFilename ?: "untitled"
        return service.upload(projectId, title, mime, file.bytes)
    }

    @GetMapping
    fun list(@PathVariable projectId: String): List<Document> = service.list(projectId)

    @GetMapping("/{documentId}")
    fun get(@PathVariable projectId: String, @PathVariable documentId: String): Document =
        service.get(projectId, documentId)

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable projectId: String, @PathVariable documentId: String) {
        service.delete(projectId, documentId)
    }
}
```

- [ ] **Step 4: Tests laufen lassen → pass**

```bash
cd backend && ./gradlew test --tests "*.DocumentControllerTest" -q
```
Expected: BUILD SUCCESSFUL — 6 Tests grün.

- [ ] **Step 5: Volle Test-Suite zur Sicherheit**

```bash
cd backend && ./gradlew test -q
```
Expected: BUILD SUCCESSFUL — keine Regression in existierenden Tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/DocumentController.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/DocumentControllerTest.kt
git commit -m "feat(api): add DocumentController with multipart upload, list, delete

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 4.4: CORS für Frontend-Port verifizieren (falls nötig)

**Files:** evtl. `backend/src/main/kotlin/com/agentwork/productspecagent/config/CorsConfig.kt`

- [ ] **Step 1: Aktuelle CORS-Konfig prüfen**

```bash
grep -rn "allowedOrigins\|cors" backend/src/main/kotlin/com/agentwork/productspecagent/config/
cat backend/src/main/resources/application.yml | grep -A2 cors
```

Falls `cors.allowed-origins` nicht den tatsächlichen Frontend-Dev-Port enthält (laut `frontend/CLAUDE.md` läuft Next.js auf `3001`, `application.yml` listet aber `http://localhost:3000` — verifizieren, was tatsächlich verwendet wird), Eintrag ggf. anpassen oder beide Origins erlauben:

```yaml
cors:
  allowed-origins: "http://localhost:3000,http://localhost:3001"
```

> Wenn die manuelle Verifikation in Phase 6 zeigt, dass CORS blockiert: hier nachjustieren. Für jetzt nur dokumentieren.

---

## Phase 5: Frontend — API-Client + Store

### Task 5.1: Types und API-Funktionen in `lib/api.ts`

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: Types und Funktionen ergänzen**

Am Ende von `lib/api.ts`:

```typescript
// ------- Documents -------

export type DocumentState = "UPLOADED" | "PROCESSING" | "EXTRACTED" | "FAILED";

export interface ProjectDocument {
  id: string;
  title: string;
  mimeType: string;
  state: DocumentState;
  createdAt: string;
}

export async function uploadDocument(projectId: string, file: File): Promise<ProjectDocument> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/documents`, {
    method: "POST",
    body: form,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error ?? `Upload failed: ${res.status}`);
  }
  return res.json();
}

export async function listDocuments(projectId: string): Promise<ProjectDocument[]> {
  return apiFetch<ProjectDocument[]>(`/api/v1/projects/${projectId}/documents`);
}

export async function deleteDocument(projectId: string, documentId: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/documents/${documentId}`, {
    method: "DELETE",
  });
  if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
}
```

> Nutzt die bestehende `API_BASE`-Konstante aus dem oberen Teil von `lib/api.ts`. `apiFetch<T>()` setzt JSON-Content-Type, was Multipart killen würde — daher direkter `fetch` für `upload` und `delete`. Type-Name `ProjectDocument`, weil `Document` mit DOM-Globals kollidiert.

- [ ] **Step 2: TS-Compile prüfen**

```bash
cd frontend && npm run lint
```
Expected: Keine neuen Lint-Fehler in api.ts (lint-Baseline beachten — nur die geänderten Bereiche bewerten).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(api): add document upload/list/delete client functions

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 5.2: `document-store.ts` mit Polling-Logik

**Files:**
- Create: `frontend/src/lib/stores/document-store.ts`

- [ ] **Step 1: Datei anlegen**

```typescript
import { create } from "zustand";
import {
  listDocuments as apiListDocuments,
  uploadDocument as apiUploadDocument,
  deleteDocument as apiDeleteDocument,
  type ProjectDocument,
} from "@/lib/api";

const TERMINAL_STATES: ProjectDocument["state"][] = ["EXTRACTED", "FAILED"];
const POLL_INTERVAL_MS = 3000;

interface DocumentState {
  documents: ProjectDocument[];
  loading: boolean;
  uploading: boolean;
  error: string | null;
  pollingTimer: number | null;

  loadDocuments: (projectId: string) => Promise<void>;
  uploadDocument: (projectId: string, file: File) => Promise<void>;
  deleteDocument: (projectId: string, documentId: string) => Promise<void>;
  startPolling: (projectId: string) => void;
  stopPolling: () => void;
  reset: () => void;
}

export const useDocumentStore = create<DocumentState>((set, get) => ({
  documents: [],
  loading: false,
  uploading: false,
  error: null,
  pollingTimer: null,

  loadDocuments: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const docs = await apiListDocuments(projectId);
      set({ documents: docs, loading: false });
    } catch (e) {
      set({ error: e instanceof Error ? e.message : "Load failed", loading: false });
    }
  },

  uploadDocument: async (projectId, file) => {
    set({ uploading: true, error: null });
    try {
      const doc = await apiUploadDocument(projectId, file);
      set((s) => ({ documents: [...s.documents, doc], uploading: false }));
      get().startPolling(projectId);
    } catch (e) {
      set({ error: e instanceof Error ? e.message : "Upload failed", uploading: false });
    }
  },

  deleteDocument: async (projectId, documentId) => {
    try {
      await apiDeleteDocument(projectId, documentId);
      set((s) => ({ documents: s.documents.filter((d) => d.id !== documentId) }));
    } catch (e) {
      set({ error: e instanceof Error ? e.message : "Delete failed" });
    }
  },

  startPolling: (projectId) => {
    const { pollingTimer } = get();
    if (pollingTimer != null) return;
    const tick = async () => {
      if (typeof document !== "undefined" && document.hidden) return;
      try {
        const docs = await apiListDocuments(projectId);
        set({ documents: docs });
        const allTerminal = docs.every((d) => TERMINAL_STATES.includes(d.state));
        if (allTerminal) get().stopPolling();
      } catch {
        // silently ignore poll errors; keep polling
      }
    };
    const timer = window.setInterval(tick, POLL_INTERVAL_MS);
    set({ pollingTimer: timer });
  },

  stopPolling: () => {
    const { pollingTimer } = get();
    if (pollingTimer != null) {
      window.clearInterval(pollingTimer);
      set({ pollingTimer: null });
    }
  },

  reset: () => {
    get().stopPolling();
    set({ documents: [], loading: false, uploading: false, error: null });
  },
}));
```

- [ ] **Step 2: Lint-Check**

```bash
cd frontend && npm run lint
```
Expected: keine neuen Fehler.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/stores/document-store.ts
git commit -m "feat(stores): add document-store with polling for non-terminal states

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 6: Frontend — UI-Tab

### Task 6.1: `DocumentsPanel`-Komponente

**Files:**
- Create: `frontend/src/components/documents/DocumentsPanel.tsx`

- [ ] **Step 1: Komponente anlegen**

```tsx
"use client";

import { useEffect, useRef, useState } from "react";
import { Trash2, Upload, FileText } from "lucide-react";
import { useDocumentStore } from "@/lib/stores/document-store";
import type { DocumentState } from "@/lib/api";
import { cn } from "@/lib/utils";

interface Props { projectId: string }

const STATE_STYLES: Record<DocumentState, string> = {
  UPLOADED: "bg-muted text-muted-foreground",
  PROCESSING: "bg-blue-500/20 text-blue-300",
  EXTRACTED: "bg-emerald-500/20 text-emerald-300",
  FAILED: "bg-red-500/20 text-red-300",
};

export function DocumentsPanel({ projectId }: Props) {
  const { documents, loading, uploading, error, loadDocuments, uploadDocument, deleteDocument, startPolling, stopPolling, reset } = useDocumentStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  useEffect(() => {
    loadDocuments(projectId).then(() => startPolling(projectId));
    return () => { stopPolling(); reset(); };
  }, [projectId]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleFiles = (files: FileList | null) => {
    if (!files) return;
    Array.from(files).forEach((f) => uploadDocument(projectId, f));
  };

  return (
    <div className="flex h-full flex-col p-3 gap-3">
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => { e.preventDefault(); setDragOver(false); handleFiles(e.dataTransfer.files); }}
        onClick={() => fileInputRef.current?.click()}
        className={cn(
          "border-2 border-dashed rounded-md p-4 text-center cursor-pointer text-xs text-muted-foreground transition-colors",
          dragOver ? "border-primary bg-primary/5" : "border-border hover:border-primary/50"
        )}
      >
        <Upload size={16} className="mx-auto mb-1.5" />
        {uploading ? "Uploading..." : "PDF, Markdown oder Text hier ablegen oder klicken"}
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.md,.txt,application/pdf,text/markdown,text/plain"
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />
      </div>

      {error && <div className="text-xs text-destructive">{error}</div>}

      <div className="flex-1 overflow-y-auto space-y-1">
        {loading && documents.length === 0 && <div className="text-xs text-muted-foreground">Lade...</div>}
        {!loading && documents.length === 0 && <div className="text-xs text-muted-foreground">Noch keine Dokumente.</div>}
        {documents.map((doc) => (
          <div key={doc.id} className="flex items-center gap-2 rounded-md border border-border p-2">
            <FileText size={14} className="shrink-0 text-muted-foreground" />
            <div className="flex-1 min-w-0">
              <div className="text-xs truncate">{doc.title}</div>
              <div className="text-[10px] text-muted-foreground">{new Date(doc.createdAt).toLocaleString("de-DE")}</div>
            </div>
            <span className={cn("rounded-full px-2 py-0.5 text-[10px]", STATE_STYLES[doc.state])}>
              {doc.state}
            </span>
            <button
              onClick={() => deleteDocument(projectId, doc.id)}
              className="text-muted-foreground hover:text-destructive transition-colors"
              title="Löschen"
            >
              <Trash2 size={13} />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Lint-Check**

```bash
cd frontend && npm run lint
```
Expected: Keine neuen Fehler.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/documents/DocumentsPanel.tsx
git commit -m "feat(documents): add DocumentsPanel with drop-zone and status list

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 6.2: Tab-Integration in `app/projects/[id]/page.tsx`

**Files:**
- Modify: `frontend/src/app/projects/[id]/page.tsx`

- [ ] **Step 1: Imports ergänzen (Z. 5–17)**

```typescript
import { ArrowLeft, ChevronRight, Loader2, Scale, MessageSquare, HelpCircle, Layers, Download, ShieldCheck, Bot, FolderTree, FileText } from "lucide-react";
// ...
import { DocumentsPanel } from "@/components/documents/DocumentsPanel";
```

- [ ] **Step 2: `rightTab`-State-Union erweitern (Z. 41)**

```typescript
const [rightTab, setRightTab] = useState<"chat" | "decisions" | "clarifications" | "tasks" | "checks" | "documents">("chat");
```

- [ ] **Step 3: Tab-Button hinzufügen (nach dem `checks`-Button, vor dem schließenden Div bei Z. 185)**

```tsx
<button
  onClick={() => setRightTab("documents")}
  className={cn(
    "flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium transition-colors",
    rightTab === "documents" ? "border-b-2 border-primary text-primary" : "text-muted-foreground hover:text-foreground"
  )}
>
  <FileText size={13} /> Documents
</button>
```

- [ ] **Step 4: Tab-Content-Branch ergänzen (im Ternary bei Z. 188–198)**

Den letzten Branch von `: (` `<CheckResultsPanel ... />` `)` ändern:

```tsx
) : rightTab === "checks" ? (
  <CheckResultsPanel projectId={id} />
) : (
  <DocumentsPanel projectId={id} />
)}
```

- [ ] **Step 5: Lint + manueller Build-Check**

```bash
cd frontend && npm run lint && npm run build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/projects/[id]/page.tsx
git commit -m "feat(workspace): add Documents tab to project workspace sidebar

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 7: Manuelle Verifikation + Done-Doc

### Task 7.1: Browser-Smoke-Test

**Files:** keine (manueller Test)

- [ ] **Step 1: Beide Services starten**

```bash
# Terminal 1 — GraphMesh (im GraphMesh-Repo):
docker-compose up -d && ./gradlew bootRun

# Terminal 2 — Backend:
cd backend && ./gradlew bootRun --quiet

# Terminal 3 — Frontend:
cd frontend && npm run dev
```

- [ ] **Step 2: Im Browser auf `http://localhost:3001` navigieren, neues Projekt erstellen**

Verifizieren: `data/projects/{id}/project.json` enthält initial **kein** `collectionId` oder `null`.

- [ ] **Step 3: In das neue Projekt navigieren, "Documents"-Tab öffnen**

Verifizieren: Drop-zone sichtbar, Liste leer.

- [ ] **Step 4: PDF-Datei (< 10 MB) per Drag-and-Drop hochladen**

Verifizieren:
- Dokument erscheint in Liste mit `UPLOADED`- oder `PROCESSING`-Badge
- Im Network-Tab: Polling alle 3 s zu `GET /api/v1/projects/{id}/documents`
- Nach einigen Sekunden Wechsel zu `EXTRACTED` (oder `FAILED`)
- Polling stoppt, sobald alle Docs terminal sind
- `data/projects/{id}/project.json` enthält jetzt `collectionId`

- [ ] **Step 5: Zweite Datei hochladen**

Verifizieren in Backend-Logs (oder GraphMesh-Logs): **kein** zweiter `createCollection`-Call.

- [ ] **Step 6: Datei > 10 MB versuchen**

Verifizieren: 413-Response, Toast/Fehlermeldung sichtbar.

- [ ] **Step 7: PNG-Datei versuchen**

Verifizieren: 415-Response, klare Fehlermeldung.

- [ ] **Step 8: Trash-Icon klicken**

Verifizieren: Dokument verschwindet aus Liste, in GraphMesh nicht mehr unter `documents(collectionId: …)` zu finden.

- [ ] **Step 9: Tab wechseln (z. B. zu Chat) und 5 s warten**

Verifizieren: Im Network-Tab kein Polling-Request mehr (sofern alle Docs terminal sind oder der Document-Store unmounted ist — beachten: Tab-Wechsel unmounted DocumentsPanel, was `stopPolling` triggert).

- [ ] **Step 10: GraphMesh stoppen, Upload versuchen**

```bash
# in GraphMesh-Terminal: Ctrl-C
```
Im Frontend nochmal Datei hochladen → Verifizieren: 503-Toast/Fehlermeldung. Restliches Wizard (Chat, Decisions, etc.) funktioniert weiter.

### Task 7.2: Done-Doc schreiben

**Files:**
- Create: `docs/features/28-project-document-upload-done.md`

- [ ] **Step 1: Datei anlegen**

```markdown
# Feature 28: Project Document Upload — Done

## Zusammenfassung

Pro Projekt können nun PDF-, Markdown- und Plain-Text-Dokumente (max. 10 MB) per Drag-and-Drop in den neuen "Documents"-Tab hochgeladen werden. Beim ersten Upload erzeugt das Backend lazy eine GraphMesh-Collection, persistiert die ID im Projekt und reicht alle weiteren Dokumente in dieselbe Collection. Status-Updates kommen per 3-Sekunden-Polling vom Frontend.

## Abweichungen vom Plan

- (Hier eintragen, was anders gelaufen ist als im Plan dokumentiert.)

## Manueller Smoke-Test (durchgeführt am YYYY-MM-DD)

- [x] Upload PDF < 10 MB → erscheint in Liste, State-Übergang sichtbar
- [x] Zweiter Upload → kein zweiter createCollection
- [x] Datei > 10 MB → 413
- [x] PNG → 415
- [x] Delete → Dokument weg
- [x] Tab-Wechsel → Polling stoppt
- [x] GraphMesh down → 503, Wizard läuft weiter

## Offene Punkte / Technische Schulden

- (Hier eintragen, falls etwas verschoben wurde.)
```

- [ ] **Step 2: Commit + Push**

```bash
git add docs/features/28-project-document-upload-done.md
git commit -m "docs: Feature 28 done-notes

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Risiken / Hinweise

- **GraphMesh muss laufen** für manuellen Smoke-Test. Tests sind komplett isoliert (MockWebServer / FakeClient).
- **Spring Boot 4 + Kotlin**: `@ConfigurationProperties` braucht `@ConfigurationPropertiesScan` oder explizit `@EnableConfigurationProperties` an einer `@Configuration`-Klasse — ohne wird die Bean nicht angelegt.
- **`open class GraphMeshClient`**: Spring-`spring`-Plugin macht `@Component`-Klassen automatisch open, aber das gilt nicht für die Methoden-Signaturen, die wir im FakeClient overriden. Daher explizit `open fun` an die Methoden des `GraphMeshClient`.
- **CORS**: `application.yml` listet `localhost:3000`, Frontend läuft laut `frontend/CLAUDE.md` auf `3001`. Falls Browser-Tests Frontend-CORS-Errors zeigen: `cors.allowed-origins` um `http://localhost:3001` erweitern.
- **`apiFetch<T>`-Wrapper** nicht für Multipart benutzbar (setzt JSON-Header). Daher direkter `fetch` für `uploadDocument` und `deleteDocument`.
- **Type-Name `Document`** kollidiert mit DOM-Globals im Frontend → bewusst `ProjectDocument` als TS-Type.

---

## Anzahl Commits (Erwartung)

13 Commits, gruppiert nach Backend-Domain (2), GraphMesh-Infrastruktur (3), Service (1), Config (1), API/Exception-Handler (2), Controller (1), Frontend (3), Done-Doc (1).
