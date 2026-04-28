package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshClient
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshConfig
import com.agentwork.productspecagent.infrastructure.graphmesh.GraphMeshException
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ProjectStorage
import com.agentwork.productspecagent.storage.UploadStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentServiceTest {

    private fun fixtures(graphMeshConfigEnabled: Boolean = true, projectGraphMeshEnabled: Boolean = true): Quad<ProjectStorage, FakeClient, FakeUploadStorage, DocumentService> {
        val storage = ProjectStorage(InMemoryObjectStore())
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

    private class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D) {
        operator fun component1() = a; operator fun component2() = b
        operator fun component3() = c; operator fun component4() = d
    }

    private class FakeClient : GraphMeshClient(GraphMeshConfig(enabled = true, url = "http://unused", requestTimeout = java.time.Duration.ofSeconds(1))) {
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

    // Fresh instance per test via fixtures(); mutable state is safe.
    private class FakeUploadStorage : UploadStorage(InMemoryObjectStore()) {
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

    @Test
    fun `first upload creates collection and persists collectionId`() {
        val (storage, client, _, service) = fixtures()
        client.nextCollectionId = "col-FIRST"

        service.upload("p1", "spec.pdf", "application/pdf", ByteArray(4) { it.toByte() })

        assertEquals(1, client.createdCollections)
        assertEquals("col-FIRST", storage.loadProject("p1")!!.collectionId)
        assertEquals("col-FIRST", client.lastUploadCollectionId)
    }

    @Test
    fun `second upload reuses existing collectionId`() {
        val (_, client, _, service) = fixtures()
        service.upload("p1", "a.pdf", "application/pdf", ByteArray(2))
        service.upload("p1", "b.pdf", "application/pdf", ByteArray(2))

        assertEquals(1, client.createdCollections)
    }

    @Test
    fun `upload throws PROJECT_NOT_FOUND if project missing`() {
        val (_, _, _, service) = fixtures()
        assertThrows(ProjectNotFoundException::class.java) {
            service.upload("nope", "a.pdf", "application/pdf", ByteArray(1))
        }
    }

    @Test
    fun `upload recreates collection when GraphMesh reports COLLECTION_NOT_FOUND`() {
        val (storage, client, _, service) = fixtures()
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
        val (_, client, _, service) = fixtures()
        service.upload("p1", "first.pdf", "application/pdf", ByteArray(2)) // creates col-NEW
        client.failNextUploadWith = GraphMeshException.GraphQlError("[{message=boom}]")

        assertThrows(GraphMeshException.GraphQlError::class.java) {
            service.upload("p1", "second.pdf", "application/pdf", ByteArray(2))
        }
        assertEquals(1, client.createdCollections)
    }

    @Test
    fun `collectionId is persisted even when uploadDocument fails`() {
        val (storage, client, _, service) = fixtures()
        client.nextCollectionId = "col-PERSISTED"
        client.simulateUploadFailure = true

        assertThrows(RuntimeException::class.java) {
            service.upload("p1", "spec.pdf", "application/pdf", ByteArray(2))
        }

        assertEquals("col-PERSISTED", storage.loadProject("p1")!!.collectionId)
        assertEquals(1, client.createdCollections)
    }

    @Test
    fun `upload mirrors document to local UploadStorage after GraphMesh success`() {
        val (_, client, uploads, service) = fixtures()
        client.nextCollectionId = "col-1"

        service.upload("p1", "spec.pdf", "application/pdf", byteArrayOf(7, 8, 9))

        val saved = uploads.saved["d1"]
        assertNotNull(saved)
        assertEquals("p1", saved!!.projectId)
        assertEquals("spec.pdf", saved.title)
        assertArrayEquals(byteArrayOf(7, 8, 9), saved.bytes)
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
    }

    @Test
    fun `local-only get returns document from UploadStorage`() {
        val (_, _, uploads, service) = fixtures(graphMeshConfigEnabled = false, projectGraphMeshEnabled = false)
        uploads.localDocs += Document("dx", "x.md", "text/markdown", DocumentState.LOCAL, "2026-04-27T10:00:00Z")

        val doc = service.get("p1", "dx")

        assertEquals("dx", doc.id)
        assertEquals(DocumentState.LOCAL, doc.state)
    }

    @Test
    fun `local-only get throws when document missing`() {
        val (_, _, _, service) = fixtures(graphMeshConfigEnabled = false, projectGraphMeshEnabled = false)

        assertThrows(DocumentNotFoundException::class.java) {
            service.get("p1", "missing-doc")
        }
    }
}
