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
