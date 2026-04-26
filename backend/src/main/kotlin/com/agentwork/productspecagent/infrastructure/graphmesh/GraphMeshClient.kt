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
open class GraphMeshClient(private val config: GraphMeshConfig) {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(config.url)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(config.requestTimeout)
            setReadTimeout(config.requestTimeout)
        })
        .build()

    open fun createCollection(name: String): String {
        val resp = post("""
            mutation Create(${'$'}input: CreateCollectionInput!) {
              createCollection(input: ${'$'}input) { id }
            }
        """.trimIndent(), mapOf("input" to mapOf("name" to name)))
        return (resp["createCollection"] as Map<*, *>)["id"] as String
    }

    open fun uploadDocument(collectionId: String, title: String, mimeType: String, contentBase64: String): Document {
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

    open fun listDocuments(collectionId: String): List<Document> {
        val resp = post("""
            query Docs(${'$'}id: ID!) {
              documents(collectionId: ${'$'}id) {
                items { id title mimeType state createdAt }
              }
            }
        """.trimIndent(), mapOf("id" to collectionId))
        @Suppress("UNCHECKED_CAST")
        val page = resp["documents"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val list = page["items"] as List<Map<*, *>>
        return list.map(::toDocument)
    }

    open fun getDocument(documentId: String): Document {
        val resp = post("""
            query Doc(${'$'}id: ID!) {
              document(id: ${'$'}id) { id title mimeType state createdAt }
            }
        """.trimIndent(), mapOf("id" to documentId))
        return toDocument(resp["document"] as Map<*, *>)
    }

    open fun deleteDocument(documentId: String) {
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
