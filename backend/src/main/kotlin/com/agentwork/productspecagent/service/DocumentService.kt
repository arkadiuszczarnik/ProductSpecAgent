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

    /** Both backend and project must opt in for GraphMesh to be used. */
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
