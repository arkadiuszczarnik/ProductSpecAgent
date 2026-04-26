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
