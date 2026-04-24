# GraphMesh an eine Koog-Anwendung anbinden

Leitfaden, wie eine bestehende Koog-basierte Applikation GraphMesh als
Wissens- und RAG-Backend nutzt. Zielbild: Dein Koog-Agent stellt Fragen
an den User, reichert die Prompts mit Fakten aus GraphMesh an und gibt
belegte Antworten zurueck.

> Sprache: Deutsch ohne Umlaute (Projekt-Konvention, siehe `CLAUDE.md`).

---

## 1. Was GraphMesh anbietet

GraphMesh exponiert drei Wege fuer externe Clients:

| Weg           | Endpoint (default)              | Wofuer gedacht                                        |
|---------------|---------------------------------|-------------------------------------------------------|
| **GraphQL**   | `POST http://localhost:8083/graphql` | Strukturierte RAG-Aufrufe, feingranulare Queries      |
| **MCP**       | `POST http://localhost:8083/mcp`     | Tool-Use in LLM-Agents (JSON-RPC, Transport STREAMABLE) |
| **GraphiQL**  | `GET  http://localhost:8083/graphiql`| Explorer / Schema-Dokumentation                        |

Quellen im Repo:

- MCP-Tools: `src/main/kotlin/com/agentwork/graphmesh/api/mcp/GraphMeshMcpTools.kt`
- RAG-Controller: `src/main/kotlin/com/agentwork/graphmesh/api/DocumentRagController.kt`, `...api/GraphRagController.kt`
- GraphQL-Schemas: `src/main/resources/graphql/*.graphqls`
- MCP-Config: `src/main/resources/application.yml` (`spring.ai.mcp.server.*`)

### Verfuegbare GraphQL-RAG-Entrypoints

- `documentRag(input: DocumentRagInput!): DocumentRagResponse!`
  - `question: String!`, `collectionId: ID!`, `topK: Int = 10`, `similarityThreshold: Float = 0.3`
  - Liefert `answer` + `sources[]` (chunkId, documentTitle, pageNumber, score, snippet)
- `graphRag(input: GraphRagInput!): GraphRagResponse!`
  - `question`, `collectionId`, `maxEdges = 150`, `maxDepth = 2`, `maxSelectedEdges = 30`
  - Liefert `answer` + `selectedEdges[]` (subject/predicate/objectValue/dataset/reasoning/relevanceScore)
- Ergaenzend: `collections(tags: [String!])`, `documents(...)`, `triples(...)`,
  Mutations `createCollection`, `uploadDocument` (siehe `src/main/resources/graphql/`).

---

## 2. Ansatz: GraphQL als Koog-Tool

Dein Koog-Agent ruft GraphMesh per GraphQL auf. Ein `ToolSet` in deiner
App kapselt die HTTP-Calls, der Agent bekommt ein paar scharfe,
dokumentierte Tools (`askGraphMeshDocuments`, `askGraphMeshGraph`,
`listCollections`) und entscheidet selbst, wann er sie aufruft.

Warum nicht MCP? Koogs Helper `McpToolRegistryProvider.defaultSseTransport`
spricht SSE; GraphMesh laeuft unter `/mcp` aber mit Transport
**STREAMABLE**, und der alte `/sse`-Pfad sendet kein `endpoint`-Event.
STREAMABLE ginge nur mit dem MCP-Kotlin-SDK-Client direkt — mehr Setup
als ein paar GraphQL-Calls, ohne erkennbaren Mehrwert, solange die
GraphMesh-Queries stabil sind.

---

## 3. Integration
`/graphql`. Der Agent bekommt so ein paar scharfe, dokumentierte Tools
(`askGraphMeshDocuments`, `askGraphMeshGraph`, `listCollections`) und
entscheidet selbst, wann er sie aufruft.

### 3.1 Dependency (Gradle, Kotlin-DSL)

```kotlin
dependencies {
    implementation("ai.koog:koog-agents:0.7.3")

    // HTTP + JSON (Ktor ist in Koog schon als transitive Dep dabei,
    // aber die expliziten Artefakte erleichtern das Debuggen)
    implementation("io.ktor:ktor-client-core:3.0.0")
    implementation("io.ktor:ktor-client-cio:3.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
}
```

### 3.2 Kleiner GraphQL-Client

```kotlin
package com.example.graphmesh

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class GraphMeshClient(
    private val endpoint: String = "http://localhost:8083/graphql",
) {
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Serializable
    private data class GraphQlRequest(val query: String, val variables: JsonObject)

    suspend fun query(query: String, variables: JsonObject): JsonObject {
        val resp: HttpResponse = http.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(GraphQlRequest(query, variables))
        }
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        body["errors"]?.let { error("GraphMesh GraphQL error: $it") }
        return body["data"]!!.jsonObject
    }
}
```

### 3.3 Koog-Tools gegen GraphMesh

```kotlin
package com.example.graphmesh

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.json.*

@LLMDescription("Tools zum Abfragen der GraphMesh-Wissensbasis (Dokumente und Graph).")
class GraphMeshTools(private val client: GraphMeshClient) : ToolSet {

    @Tool
    @LLMDescription(
        "Stellt eine Frage an die Dokumenten-RAG von GraphMesh. " +
        "Nutze diesen Tool fuer Fragen, die sich aus dem Volltext der Dokumente " +
        "beantworten lassen (Definitionen, Zitate, konkrete Passagen)."
    )
    suspend fun askGraphMeshDocuments(
        @LLMDescription("Die Frage in natuerlicher Sprache.") question: String,
        @LLMDescription("Collection-ID, in der gesucht werden soll.") collectionId: String,
        @LLMDescription("Anzahl der Chunks (default 10).") topK: Int = 10,
    ): String {
        val data = client.query(
            query = """
                query DocRag(${'$'}input: DocumentRagInput!) {
                  documentRag(input: ${'$'}input) {
                    answer
                    sources { documentTitle pageNumber score snippet }
                  }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("question", question)
                    put("collectionId", collectionId)
                    put("topK", topK)
                }
            }
        )
        return data["documentRag"].toString()
    }

    @Tool
    @LLMDescription(
        "Stellt eine Frage an den Wissensgraphen von GraphMesh. " +
        "Nutze diesen Tool fuer Fragen nach Beziehungen, Zusammenhaengen " +
        "zwischen Entitaeten oder fuer Fakten, die aus Triples abgeleitet sind."
    )
    suspend fun askGraphMeshGraph(
        @LLMDescription("Die Frage in natuerlicher Sprache.") question: String,
        @LLMDescription("Collection-ID, in der gesucht werden soll.") collectionId: String,
        @LLMDescription("Max. Kanten im Subgraphen (default 150).") maxEdges: Int = 150,
    ): String {
        val data = client.query(
            query = """
                query GraphRag(${'$'}input: GraphRagInput!) {
                  graphRag(input: ${'$'}input) {
                    answer
                    selectedEdges {
                      subject predicate objectValue dataset reasoning relevanceScore
                    }
                  }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("question", question)
                    put("collectionId", collectionId)
                    put("maxEdges", maxEdges)
                }
            }
        )
        return data["graphRag"].toString()
    }

    @Tool
    @LLMDescription("Listet alle Collections in GraphMesh, optional gefiltert nach Tags.")
    suspend fun listCollections(
        @LLMDescription("Komma-separierte Tags (optional).") tags: String = "",
    ): String {
        val tagList = if (tags.isBlank()) JsonArray(emptyList())
                     else JsonArray(tags.split(",").map { JsonPrimitive(it.trim()) })
        val data = client.query(
            query = """
                query Colls(${'$'}tags: [String!]) {
                  collections(tags: ${'$'}tags) { id name description tags }
                }
            """.trimIndent(),
            variables = buildJsonObject { put("tags", tagList) }
        )
        return data["collections"].toString()
    }
}
```

### 3.4 Im Koog-Agent registrieren

```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val graphMesh = GraphMeshTools(GraphMeshClient())

    val toolRegistry = ToolRegistry { tools(graphMesh.asTools()) }

    val agent = AIAgent(
        executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = """
            Du bist ein Assistent mit Zugriff auf GraphMesh.
            - Fuer Faktenfragen zu Dokumenten -> askGraphMeshDocuments.
            - Fuer Fragen nach Beziehungen / Entitaeten -> askGraphMeshGraph.
            - Wenn unklar welche Collection -> erst listCollections.
            Zitiere Quellen (documentTitle, pageNumber) in deiner Antwort.
        """.trimIndent(),
        toolRegistry = toolRegistry,
    )

    println(agent.run("Welche Vorgaben zum Datenschutz stehen in unserer Policy-Collection?"))
}
```

> Wenn deine App `resolveLlmModel(name)` (GraphMesh-interne Helper aus
> `com.agentwork.graphmesh.llm.ModelResolver`) schon nutzt: dieselbe
> Regel gilt auch client-seitig, falls du den Client-Code in GraphMesh
> spiegelst. In einer **externen** Koog-App nutzt du normalerweise die
> Koog-Executors direkt (`simpleOpenAIExecutor`, `simpleOllamaAIExecutor`, ...).

### 3.5 RAG-Kontext als Preamble (ohne Tool-Use)

Manchmal willst du GraphMesh **vor** dem LLM-Call anstossen und das
Ergebnis als Kontext in den Prompt kippen, ohne Tool-Loop. Dann:

```kotlin
val hit = graphMesh.askGraphMeshDocuments(
    question = userQuestion,
    collectionId = "policies",
    topK = 6,
)

val agent = AIAgent(
    executor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = """
        Kontext aus GraphMesh (nutze ausschliesslich diesen Kontext,
        sage "unbekannt", wenn er nicht reicht):
        ----------------------------------------
        $hit
        ----------------------------------------
    """.trimIndent(),
)

println(agent.run(userQuestion))
```

Das ist klassisches "retrieve-then-generate" ohne Agent-Autonomie.

### 3.6 Collections anlegen und Dokumente hochladen

Vor dem ersten RAG-Call braucht GraphMesh eine Collection und mindestens
ein bereits verarbeitetes Dokument darin. Beides geht ueber denselben
`GraphMeshClient` (Abschnitt 3.2).

#### GraphQL-Mutations (Referenz aus dem Schema)

```graphql
input CreateCollectionInput {
  name: String!
  description: String
  tags: [String!]
  metadata: [KeyValueInput!]
}

input UploadDocumentInput {
  collectionId: ID!
  title: String!
  mimeType: String!
  content: String!              # Base64-encoded Dateiinhalt
  metadata: [KeyValueInput!]
}

input KeyValueInput { key: String!  value: String! }
```

`Document` hat ein Feld `state: DocumentState` mit den Werten
`UPLOADED`, `PROCESSING`, `EXTRACTED`, `FAILED`. Ein Dokument ist erst
fuer RAG nutzbar, wenn `state == EXTRACTED`. Die Verarbeitung laeuft
asynchron ueber Kafka (`graphmesh.document.ingested`).

#### Kotlin-Helper auf dem bestehenden Client

```kotlin
package com.example.graphmesh

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class GraphMeshAdmin(private val client: GraphMeshClient) {

    /** Legt eine neue Collection an und liefert deren ID zurueck. */
    suspend fun createCollection(
        name: String,
        description: String? = null,
        tags: List<String> = emptyList(),
    ): String {
        val data = client.query(
            query = """
                mutation Create(${'$'}input: CreateCollectionInput!) {
                  createCollection(input: ${'$'}input) { id }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("name", name)
                    description?.let { put("description", it) }
                    put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
                }
            }
        )
        return data["createCollection"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    /** Laedt eine lokale Datei als Dokument in eine Collection. */
    suspend fun uploadDocument(
        collectionId: String,
        file: Path,
        title: String = file.fileName.toString(),
        mimeType: String = Files.probeContentType(file) ?: "application/octet-stream",
    ): String {
        val base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file))
        val data = client.query(
            query = """
                mutation Upload(${'$'}input: UploadDocumentInput!) {
                  uploadDocument(input: ${'$'}input) { id state }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("collectionId", collectionId)
                    put("title", title)
                    put("mimeType", mimeType)
                    put("content", base64)
                }
            }
        )
        return data["uploadDocument"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    /** Pollt den Dokumentstatus, bis er EXTRACTED oder FAILED ist. */
    suspend fun waitForExtraction(
        documentId: String,
        timeoutMs: Long = 120_000,
        pollIntervalMs: Long = 2_000,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val data = client.query(
                query = "query D(${'$'}id: ID!) { document(id: ${'$'}id) { state } }",
                variables = buildJsonObject { put("id", documentId) }
            )
            val state = data["document"]!!.jsonObject["state"]!!.jsonPrimitive.content
            if (state == "EXTRACTED" || state == "FAILED") return state
            delay(pollIntervalMs)
        }
        error("Document $documentId wurde nicht rechtzeitig verarbeitet")
    }
}
```

#### Typischer Bootstrap-Flow

```kotlin
suspend fun bootstrap() {
    val admin = GraphMeshAdmin(GraphMeshClient())

    val collectionId = admin.createCollection(
        name = "policies",
        description = "Interne Richtlinien",
        tags = listOf("policy", "internal"),
    )

    val docId = admin.uploadDocument(
        collectionId = collectionId,
        file = Path.of("./data/datenschutz.pdf"),
    )

    val state = admin.waitForExtraction(docId)
    check(state == "EXTRACTED") { "Extraktion fehlgeschlagen: $state" }

    println("Collection $collectionId bereit fuer RAG.")
}
```

Hinweise:

- `content` **muss** Base64 sein — der Controller dekodiert mit
  `Base64.getDecoder().decode()`. Plain-Text geht nicht.
- `mimeType` ist erforderlich. Typische Werte: `application/pdf`,
  `text/markdown`, `text/plain`.
- Nicht im Schema: `sourceUri`, `language`, `tags` auf `Document`.
  Solche Felder gehoeren in `metadata: [KeyValueInput!]`.
- Keine Batch-Upload-Mutation — pro Datei ein Call. Fuer groessere
  Mengen eine eigene Schleife mit Backoff bauen.
- Ohne `waitForExtraction` kann der erste RAG-Call leere Antworten
  liefern, weil Chunking/Embedding/Triple-Extraktion noch laeuft.

---

## 4. Setup-Checkliste vor dem ersten Request

1. **Infra hochfahren** (bei lokaler Entwicklung):
   ```bash
   docker-compose up -d   # Cassandra, Qdrant, MinIO, Kafka
   ./gradlew bootRun      # GraphMesh auf Port 8083
   ```
2. **Smoke-Test**:
   ```bash
   curl http://localhost:8083/graphiql       # UI erreichbar?
   curl -X POST http://localhost:8083/graphql \
        -H 'Content-Type: application/json' \
        -d '{"query":"{ collections { id name } }"}'
   ```
3. **Collection anlegen und Dokumente hochladen** — siehe Abschnitt 3.6.
   Ohne extrahierte Dokumente liefern `documentRag`/`graphRag` leere
   Antworten.
4. **Embedding-Dimension pruefen**: Qdrant-Collections werden nach
   Schema `{logicalName}_{dimension}` angelegt. Wechselst du auf
   Client-Seite nicht das Embedding-Modell, musst du nichts tun; aber
   wenn GraphMesh das Modell wechselt (z. B. `bge-m3` -> `nomic-embed-text`),
   muss die Collection neu befuellt werden. Detail: siehe `application.yml`
   und `ModelResolver`.
5. **Auth**: Aktuell keine. Wenn du GraphMesh ausserhalb von localhost
   exponierst, **zwingend** einen Reverse-Proxy mit API-Key-Check davor.
   CORS erlaubt per default nur `http://localhost:3002` (`CorsConfig.kt`) -
   fuer Server-to-Server-Calls ist das irrelevant.

---

## 5. Default-Ports (Referenz)

| Service           | Port | Quelle                         |
|-------------------|------|--------------------------------|
| GraphMesh Backend | 8083 | `application.yml`              |
| Frontend (Next)   | 3002 | `CorsConfig.kt`                |

---

## 6. Tuning & Fallstricke

- **`similarityThreshold`** ist provider-abhaengig. 0.3 ist ein
  neutraler Default; bei OpenAI-Embeddings passt oft hoeher (0.5+),
  bei `multilingual-e5` eher tiefer. Anpassung ueber `DocumentRagInput`.
- **`maxEdges` in Graph-RAG** ist der entscheidende Knopf fuer Latenz
  vs. Recall. Erhoehen, wenn Antworten unvollstaendig wirken.
- **Collections vorbereiten** kostet Zeit (LLM-Extraktion laeuft
  asynchron ueber Kafka). Die Query-API liefert bis dahin nur das,
  was bereits persistiert ist.
- **Keine native Streaming-Antwort** fuer RAG in GraphQL; wenn du
  Streaming im Frontend brauchst, ist das ein separates Feature.

---

## 7. Kurzentscheidung (TL;DR)

- **Agent mit Tool-Loop** → `GraphMeshTools` als `ToolSet` registrieren
  (Abschnitt 3.3 / 3.4).
- **Batch/Offline-RAG ohne Agent-Loop** → GraphQL direkt aufrufen und
  das Ergebnis als System-Message injizieren (Abschnitt 3.5).
