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
        var simulateUploadFailure: Boolean = false
        var failNextUploadWith: GraphMeshException.GraphQlError? = null
        var uploadCalls = 0
        override fun createCollection(name: String): String {
            createdCollections++
            return nextCollectionId
        }
        override fun uploadDocument(collectionId: String, title: String, mimeType: String, contentBase64: String): Document {
            uploadCalls++
            lastUploadCollectionId = collectionId
            failNextUploadWith?.let { failNextUploadWith = null; throw it }
            if (simulateUploadFailure) throw RuntimeException("simulated upload failure")
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

    @Test
    fun `upload recreates collection when GraphMesh reports COLLECTION_NOT_FOUND`() {
        val (storage, client, service) = fixtures()
        storage.saveProject(storage.loadProject("p1")!!.copy(collectionId = "col-STALE"))
        client.nextCollectionId = "col-FRESH"
        client.failNextUploadWith = GraphMeshException.GraphQlError(
            "[{message=Collection not found: col-STALE, extensions={code=COLLECTION_NOT_FOUND}}]"
        )

        service.upload("p1", "spec.pdf", "application/pdf", ByteArray(2))

        assertEquals(1, client.createdCollections)
        assertEquals(2, client.uploadCalls)
        assertEquals("col-FRESH", client.lastUploadCollectionId)
        assertEquals("col-FRESH", storage.loadProject("p1")!!.collectionId)
    }

    @Test
    fun `upload propagates non-collection GraphQlError without retry`() {
        val (_, client, service) = fixtures()
        service.upload("p1", "first.pdf", "application/pdf", ByteArray(2)) // creates col-NEW
        client.failNextUploadWith = GraphMeshException.GraphQlError("[{message=boom}]")

        assertThrows(GraphMeshException.GraphQlError::class.java) {
            service.upload("p1", "second.pdf", "application/pdf", ByteArray(2))
        }
        assertEquals(1, client.createdCollections)
    }

    @Test
    fun `collectionId is persisted even when uploadDocument fails`() {
        val (storage, client, service) = fixtures()
        client.nextCollectionId = "col-PERSISTED"
        client.simulateUploadFailure = true

        assertThrows(RuntimeException::class.java) {
            service.upload("p1", "spec.pdf", "application/pdf", ByteArray(2))
        }

        assertEquals("col-PERSISTED", storage.loadProject("p1")!!.collectionId)
        assertEquals(1, client.createdCollections)
    }
}
