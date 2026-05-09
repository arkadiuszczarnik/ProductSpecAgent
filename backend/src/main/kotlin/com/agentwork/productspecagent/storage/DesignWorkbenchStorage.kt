package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignInput
import com.agentwork.productspecagent.domain.DesignInputClassification
import com.agentwork.productspecagent.domain.DesignInputKind
import com.agentwork.productspecagent.domain.DesignScreen
import com.agentwork.productspecagent.domain.DesignVariant
import com.agentwork.productspecagent.domain.DesignWorkbench
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class DesignWorkbenchStorage(private val objectStore: ObjectStore) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun prefix(projectId: String) = "projects/$projectId/design/"
    private fun workbenchKey(projectId: String) = "${prefix(projectId)}workbench.json"
    private fun inputKey(projectId: String, inputId: String) = "${prefix(projectId)}inputs/$inputId/content"

    fun variantKey(projectId: String, screenId: String, variantId: String) =
        "${prefix(projectId)}variants/$screenId/$variantId.html"

    fun activeScreenKey(projectId: String, screenSlug: String) =
        "${prefix(projectId)}screens/$screenSlug/index.html"

    fun load(projectId: String): DesignWorkbench {
        val existing = objectStore.get(workbenchKey(projectId))
            ?.toString(Charsets.UTF_8)
            ?.let { json.decodeFromString<DesignWorkbench>(it) }
        return existing ?: DesignWorkbench(projectId = projectId, updatedAt = Instant.now().toString())
    }

    fun save(workbench: DesignWorkbench): DesignWorkbench {
        val updated = workbench.copy(updatedAt = Instant.now().toString())
        objectStore.put(workbenchKey(updated.projectId), json.encodeToString(updated).toByteArray(), "application/json")
        return updated
    }

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

    fun saveScreens(projectId: String, screens: List<DesignScreen>): DesignWorkbench =
        save(load(projectId).copy(screens = screens))

    fun saveVariant(projectId: String, screenId: String, variant: DesignVariant, html: ByteArray): DesignWorkbench {
        val normalizedVariant = variant.copy(htmlPath = variantKey(projectId, screenId, variant.id))
        objectStore.put(normalizedVariant.htmlPath, html, "text/html")
        val workbench = load(projectId)
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

    fun setActiveVariant(projectId: String, screenId: String, variantId: String): DesignWorkbench {
        val workbench = load(projectId)
        return save(
            workbench.copy(
                screens = workbench.screens.map { screen ->
                    if (screen.id == screenId) screen.copy(activeVariantId = variantId) else screen
                },
            ),
        )
    }

    fun readByKey(key: String): ByteArray =
        objectStore.get(key) ?: throw NoSuchElementException("design object not found: $key")

    fun writeActiveScreen(projectId: String, screenSlug: String, html: ByteArray) {
        objectStore.put(activeScreenKey(projectId, screenSlug), html, "text/html")
    }
}
