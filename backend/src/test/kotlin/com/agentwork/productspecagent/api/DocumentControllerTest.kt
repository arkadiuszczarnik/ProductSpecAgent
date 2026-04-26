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
        @Bean @Primary fun stubUploadStorage(): com.agentwork.productspecagent.storage.UploadStorage =
            object : com.agentwork.productspecagent.storage.UploadStorage("build/test-uploads-stub") {
                override fun save(projectId: String, docId: String, title: String, bytes: ByteArray) = title
                override fun delete(projectId: String, docId: String) {}
            }
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
