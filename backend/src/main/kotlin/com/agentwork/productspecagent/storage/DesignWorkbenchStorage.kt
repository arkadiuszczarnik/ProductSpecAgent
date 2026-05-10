package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import com.agentwork.productspecagent.domain.DesignImageInput
import com.agentwork.productspecagent.domain.DesignWorkbench
import com.agentwork.productspecagent.domain.GeneratedDesign
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.time.Instant

class StaleDesignImageAnalysisException(message: String) : RuntimeException(message)

@Service
class DesignWorkbenchStorage(private val objectStore: ObjectStore) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun prefix(projectId: String) = "projects/$projectId/design/"
    private fun workbenchKey(projectId: String) = "${prefix(projectId)}workbench.json"
    private fun imageInputKey(projectId: String) = "${prefix(projectId)}input/reference-image"
    private fun designSummaryKey(projectId: String) = "${prefix(projectId)}design.md"
    fun currentDesignKey(projectId: String) = "${prefix(projectId)}current/index.html"
    fun activeScreenKey(projectId: String, screenSlug: String) = "${prefix(projectId)}screens/$screenSlug/index.html"

    fun load(projectId: String): DesignWorkbench {
        val existing = objectStore.get(workbenchKey(projectId))
            ?.toString(Charsets.UTF_8)
            ?.let { runCatching { json.decodeFromString<DesignWorkbench>(it) }.getOrNull() }
        return existing ?: DesignWorkbench(projectId = projectId, updatedAt = Instant.now().toString())
    }

    fun save(workbench: DesignWorkbench): DesignWorkbench {
        val updated = workbench.copy(updatedAt = Instant.now().toString())
        objectStore.put(workbenchKey(updated.projectId), json.encodeToString(updated).toByteArray(), "application/json")
        return updated
    }

    fun saveInput(projectId: String, description: String?, imageInput: DesignImageInput?): DesignWorkbench {
        val trimmed = description?.trim()?.takeIf { it.isNotBlank() }
        val existing = load(projectId)
        objectStore.delete(activeScreenKey(projectId, "design"))
        return save(
            existing.copy(
                description = trimmed,
                imageInput = imageInput ?: existing.imageInput,
                analysis = null,
                currentDesign = null,
            ),
        )
    }

    fun saveImageInput(projectId: String, originalName: String, bytes: ByteArray, contentType: String): DesignWorkbench {
        val existing = load(projectId)
        val key = imageInputKey(projectId)
        objectStore.put(key, bytes, contentType)
        val image = DesignImageInput(
            originalName = originalName,
            contentRef = key,
            contentType = contentType,
            sizeBytes = bytes.size.toLong(),
            uploadedAt = Instant.now().toString(),
        )
        objectStore.delete(activeScreenKey(projectId, "design"))
        return save(
            existing.copy(
                imageInput = image,
                imageAnalysis = null,
                imageAnalysisError = null,
                analysis = null,
                currentDesign = null,
            ),
        )
    }

    fun readImageInput(projectId: String): ByteArray {
        val image = load(projectId).imageInput ?: throw NoSuchElementException("design image input not found: $projectId")
        return objectStore.get(image.contentRef)
            ?: throw NoSuchElementException("design image object not found: ${image.contentRef}")
    }

    fun saveImageAnalysis(projectId: String, image: DesignImageInput, analysis: DesignImageAnalysis): DesignWorkbench =
        save(currentWorkbenchForImage(projectId, image).copy(imageAnalysis = analysis, imageAnalysisError = null))

    fun saveImageAnalysisError(projectId: String, image: DesignImageInput, message: String): DesignWorkbench =
        save(
            currentWorkbenchForImage(projectId, image).copy(
                imageAnalysisError = message.trim().ifBlank { "Image analysis failed." },
            ),
        )

    private fun currentWorkbenchForImage(projectId: String, expected: DesignImageInput): DesignWorkbench {
        val current = load(projectId)
        val actual = current.imageInput ?: throw StaleDesignImageAnalysisException(
            "design image input changed during analysis: $projectId",
        )
        if (actual.contentRef != expected.contentRef || actual.uploadedAt != expected.uploadedAt) {
            throw StaleDesignImageAnalysisException("design image input changed during analysis: $projectId")
        }
        return current
    }

    fun saveGeneratedDesign(
        projectId: String,
        analysis: DesignAnalysis,
        generated: GeneratedDesign,
        html: ByteArray,
    ): DesignWorkbench {
        val normalized = generated.copy(htmlPath = currentDesignKey(projectId))
        objectStore.delete(activeScreenKey(projectId, "design"))
        objectStore.put(normalized.htmlPath, html, "text/html")
        return save(load(projectId).copy(analysis = analysis, currentDesign = normalized))
    }

    fun readCurrentDesign(projectId: String): ByteArray =
        objectStore.get(currentDesignKey(projectId)) ?: throw NoSuchElementException("current design not found: $projectId")

    fun writeActiveScreen(projectId: String, html: ByteArray) {
        objectStore.put(activeScreenKey(projectId, "design"), html, "text/html")
    }

    fun writeDesignSummary(projectId: String, markdown: String) {
        objectStore.put(designSummaryKey(projectId), markdown.toByteArray(), "text/markdown")
    }

    fun readDesignSummary(projectId: String): String? =
        objectStore.get(designSummaryKey(projectId))?.toString(Charsets.UTF_8)

    fun listActiveOutputFiles(projectId: String): List<Pair<String, ByteArray>> {
        val workbench = load(projectId)
        if (workbench.currentDesign == null) return emptyList()
        val content = objectStore.get(activeScreenKey(projectId, "design")) ?: return emptyList()
        return buildList {
            add("design/screens/design/index.html" to content)
            objectStore.get(designSummaryKey(projectId))?.let { summary ->
                add("design/design.md" to summary)
            }
        }
    }
}
