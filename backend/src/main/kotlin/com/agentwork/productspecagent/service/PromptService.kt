package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.PromptListItem
import com.agentwork.productspecagent.storage.ObjectStore
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

class PromptValidationException(val errors: List<String>) : RuntimeException(errors.joinToString("; "))

@Service
class PromptService(
    private val registry: PromptRegistry,
    private val objectStore: ObjectStore,
) {
    private val cache = ConcurrentHashMap<String, String>()

    fun get(id: String): String {
        registry.byId(id) // wirft PromptNotFoundException für unbekannte IDs
        return cache.computeIfAbsent(id) { loadFromStoreOrResource(id) }
    }

    fun list(): List<PromptListItem> = registry.definitions.map { def ->
        PromptListItem(
            id = def.id,
            title = def.title,
            description = def.description,
            agent = def.agent,
            isOverridden = objectStore.exists(s3Key(def.id)),
        )
    }

    fun put(id: String, content: String) {
        val def = registry.byId(id)
        val errors = def.validators.flatMap { it.validate(content) }
        if (errors.isNotEmpty()) throw PromptValidationException(errors)

        objectStore.put(s3Key(id), content.toByteArray(Charsets.UTF_8), "text/markdown")
        cache[id] = content
    }

    fun reset(id: String) {
        registry.byId(id)
        objectStore.delete(s3Key(id))
        cache.remove(id)
    }

    private fun loadFromStoreOrResource(id: String): String {
        val def = registry.byId(id)
        objectStore.get(s3Key(id))?.toString(Charsets.UTF_8)?.let { return it }
        return ClassPathResource(def.resourcePath).inputStream
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun s3Key(id: String) = "prompts/$id.md"
}
