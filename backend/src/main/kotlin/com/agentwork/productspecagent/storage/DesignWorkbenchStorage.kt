package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.DesignInput
import com.agentwork.productspecagent.domain.DesignInputClassification
import com.agentwork.productspecagent.domain.DesignInputKind
import com.agentwork.productspecagent.domain.DesignImageInput
import com.agentwork.productspecagent.domain.DesignScreen
import com.agentwork.productspecagent.domain.DesignVariant
import com.agentwork.productspecagent.domain.DesignWorkbench
import com.agentwork.productspecagent.domain.GeneratedDesign
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private const val LEGACY_DESIGN_COMPAT_MESSAGE =
    "Temporary compatibility for legacy design workbench flow until Simple Design Generator V1 service/controller migration removes callers."

@Service
class DesignWorkbenchStorage(private val objectStore: ObjectStore) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun prefix(projectId: String) = "projects/$projectId/design/"
    private fun workbenchKey(projectId: String) = "${prefix(projectId)}workbench.json"
    private fun imageInputKey(projectId: String) = "${prefix(projectId)}input/reference-image"
    private fun inputKey(projectId: String, inputId: String) = "${prefix(projectId)}inputs/$inputId/content"
    fun currentDesignKey(projectId: String) = "${prefix(projectId)}current/index.html"
    fun activeScreenKey(projectId: String, screenSlug: String) = "${prefix(projectId)}screens/$screenSlug/index.html"
    fun variantKey(projectId: String, screenId: String, variantId: String) =
        "${prefix(projectId)}variants/$screenId/$variantId.html"

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
                analysis = null,
                currentDesign = null,
            ),
        )
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

    // Transitional compile compatibility for the legacy screen/input flow. Task 2/3 remove these callers.
    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun addTextInput(projectId: String, text: String): DesignInput {
        val id = UUID.randomUUID().toString()
        val key = inputKey(projectId, id)
        objectStore.put(key, text.toByteArray(), "text/plain")
        val input = DesignInput(
            id = id,
            kind = DesignInputKind.TEXT,
            contentRef = key,
            createdAt = Instant.now().toString(),
        )
        val workbench = load(projectId)
        save(workbench.copy(inputs = workbench.inputs + input))
        return input
    }

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun addBinaryInput(
        projectId: String,
        kind: DesignInputKind,
        originalName: String,
        bytes: ByteArray,
        contentType: String,
    ): DesignInput {
        val id = UUID.randomUUID().toString()
        val key = inputKey(projectId, id)
        objectStore.put(key, bytes, contentType)
        val input = DesignInput(
            id = id,
            kind = kind,
            originalName = originalName,
            contentRef = key,
            createdAt = Instant.now().toString(),
        )
        val workbench = load(projectId)
        save(workbench.copy(inputs = workbench.inputs + input))
        return input
    }

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun updateInputClassification(
        projectId: String,
        inputId: String,
        classification: DesignInputClassification,
        userLabel: String?,
    ): DesignWorkbench {
        val workbench = load(projectId)
        return save(
            workbench.copy(
                inputs = workbench.inputs.map {
                    if (it.id == inputId) {
                        it.copy(classification = classification, userLabel = userLabel)
                    } else {
                        it
                    }
                },
            ),
        )
    }

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun saveScreens(projectId: String, screens: List<DesignScreen>): DesignWorkbench =
        save(load(projectId).copy(screens = screens))

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun saveVariant(projectId: String, screenId: String, variant: DesignVariant, html: ByteArray): DesignWorkbench {
        val workbench = load(projectId)
        if (workbench.screens.none { it.id == screenId }) {
            throw NoSuchElementException("design screen not found: $screenId")
        }
        val normalizedVariant = variant.copy(
            screenId = screenId,
            htmlPath = variantKey(projectId, screenId, variant.id),
        )
        objectStore.put(normalizedVariant.htmlPath, html, "text/html")
        return save(
            workbench.copy(
                screens = workbench.screens.map { screen ->
                    if (screen.id == screenId) {
                        screen.copy(variants = screen.variants.filterNot { it.id == normalizedVariant.id } + normalizedVariant)
                    } else {
                        screen
                    }
                },
            ),
        )
    }

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun setActiveVariant(projectId: String, screenId: String, variantId: String): DesignWorkbench {
        val workbench = load(projectId)
        val screen = workbench.screens.firstOrNull { it.id == screenId }
            ?: throw NoSuchElementException("design screen not found: $screenId")
        require(screen.variants.any { it.id == variantId }) {
            "design variant not found on screen $screenId: $variantId"
        }
        return save(
            workbench.copy(
                screens = workbench.screens.map { screen ->
                    if (screen.id == screenId) screen.copy(activeVariantId = variantId) else screen
                },
            ),
        )
    }

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun readByKey(key: String): ByteArray =
        objectStore.get(key) ?: throw NoSuchElementException("design object not found: $key")

    @Deprecated(LEGACY_DESIGN_COMPAT_MESSAGE)
    fun writeActiveScreen(projectId: String, screenSlug: String, html: ByteArray) {
        objectStore.put(activeScreenKey(projectId, screenSlug), html, "text/html")
    }

    fun listActiveOutputFiles(projectId: String): List<Pair<String, ByteArray>> {
        val workbench = load(projectId)
        if (workbench.currentDesign == null) return emptyList()
        val content = objectStore.get(activeScreenKey(projectId, "design")) ?: return emptyList()
        return listOf("design/screens/design/index.html" to content)
    }
}
