package com.agentwork.productspecagent.infrastructure.graphmesh

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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
    fun tearDown() { server.close() }

    @Test
    fun `createCollection returns collection id from GraphQL response`() {
        server.enqueue(MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body("""{"data":{"createCollection":{"id":"col-42"}}}""")
            .build())

        val id = client.createCollection("My Project")

        assertEquals("col-42", id)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body!!.utf8().contains("\"name\":\"My Project\""))
    }

    @Test
    fun `uploadDocument returns parsed Document`() {
        server.enqueue(MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body("""{"data":{"uploadDocument":{"id":"d1","title":"spec.pdf","mimeType":"application/pdf","state":"UPLOADED","createdAt":"2026-04-24T10:00:00Z"}}}""")
            .build())

        val doc = client.uploadDocument("col-1", "spec.pdf", "application/pdf", "Zm9v")

        assertEquals("d1", doc.id)
        assertEquals(com.agentwork.productspecagent.domain.DocumentState.UPLOADED, doc.state)
        val req = server.takeRequest().body!!.utf8()
        assertTrue(req.contains("\"collectionId\":\"col-1\""))
        assertTrue(req.contains("\"content\":\"Zm9v\""))
    }

    @Test
    fun `listDocuments returns parsed list`() {
        server.enqueue(MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body("""{"data":{"documents":{"items":[
            {"id":"d1","title":"a","mimeType":"text/plain","state":"EXTRACTED","createdAt":"2026-04-24T10:00:00Z"},
            {"id":"d2","title":"b","mimeType":"application/pdf","state":"PROCESSING","createdAt":"2026-04-24T11:00:00Z"}
        ]}}}""")
            .build())

        val docs = client.listDocuments("col-1")

        assertEquals(2, docs.size)
        assertEquals("d1", docs[0].id)
        assertEquals(com.agentwork.productspecagent.domain.DocumentState.PROCESSING, docs[1].state)
    }

    @Test
    fun `deleteDocument sends mutation with id`() {
        server.enqueue(MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body("""{"data":{"deleteDocument":true}}""")
            .build())

        client.deleteDocument("d1")

        val req = server.takeRequest().body!!.utf8()
        assertTrue(req.contains("\"id\":\"d1\""))
    }

    @Test
    fun `GraphQL errors throw GraphQlError`() {
        server.enqueue(MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body("""{"errors":[{"message":"collection not found"}],"data":null}""")
            .build())

        val ex = assertThrows(GraphMeshException.GraphQlError::class.java) {
            client.listDocuments("nope")
        }
        assertTrue(ex.detail.contains("collection not found"))
    }

    @Test
    fun `unreachable server throws Unavailable`() {
        server.close()
        val ex = assertThrows(GraphMeshException.Unavailable::class.java) {
            client.createCollection("x")
        }
        assertNotNull(ex.cause)
    }
}
