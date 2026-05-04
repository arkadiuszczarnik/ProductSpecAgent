package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.AgentModelRegistry
import com.agentwork.productspecagent.agent.AgentModelTier
import com.agentwork.productspecagent.domain.AgentModelInfo
import com.agentwork.productspecagent.storage.ObjectStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class AgentModelService(
    private val registry: AgentModelRegistry,
    private val objectStore: ObjectStore,
) {
    private val logger = LoggerFactory.getLogger(AgentModelService::class.java)
    private val cache = ConcurrentHashMap<String, AgentModelTier>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadFromStore()
    }

    fun getTier(agentId: String): AgentModelTier {
        cache[agentId]?.let { return it }
        return registry.defaultTier(agentId)
    }

    fun setTier(agentId: String, tier: AgentModelTier) {
        require(agentId in registry.agentIds()) { "Unknown agent id: $agentId" }
        val newSelections = (cache.toMap() + (agentId to tier))
        objectStore.put(SELECTIONS_KEY, encode(newSelections), "application/json")
        cache[agentId] = tier
    }

    fun reset(agentId: String) {
        require(agentId in registry.agentIds()) { "Unknown agent id: $agentId" }
        val newSelections = cache.toMap() - agentId
        if (newSelections.isEmpty()) objectStore.delete(SELECTIONS_KEY)
        else objectStore.put(SELECTIONS_KEY, encode(newSelections), "application/json")
        cache.remove(agentId)
    }

    fun listAll(): List<AgentModelInfo> = registry.agentIds().map { agentId ->
        val current = getTier(agentId)
        val default = registry.defaultTier(agentId)
        AgentModelInfo(
            agentId = agentId,
            displayName = displayNameFor(agentId),
            defaultTier = default,
            currentTier = current,
            isOverridden = cache.containsKey(agentId),
            tierMapping = registry.tierMappingView(),
        )
    }

    private fun loadFromStore() {
        val bytes = try {
            objectStore.get(SELECTIONS_KEY)
        } catch (e: Exception) {
            logger.warn("ObjectStore unavailable while loading agent-model selections: ${e.message}")
            null
        } ?: return
        try {
            val parsed = json.decodeFromString<SelectionsFile>(bytes.toString(Charsets.UTF_8))
            parsed.selections.forEach { (id, tier) ->
                if (id in registry.agentIds()) cache[id] = tier
            }
        } catch (e: Exception) {
            logger.warn("Could not parse agent-model selections.json — falling back to defaults: ${e.message}")
        }
    }

    private fun encode(selections: Map<String, AgentModelTier>): ByteArray =
        json.encodeToString(SelectionsFile(selections)).toByteArray(Charsets.UTF_8)

    @Serializable
    private data class SelectionsFile(val selections: Map<String, AgentModelTier> = emptyMap())

    companion object {
        private const val SELECTIONS_KEY = "agent-models/selections.json"

        fun displayNameFor(agentId: String): String = when (agentId) {
            "idea-to-spec" -> "Idea-to-Spec"
            "decision" -> "Decision"
            "feature-proposal" -> "Feature Proposal"
            "plan-generator" -> "Plan Generator"
            "design-summary" -> "Design-Summary"
            else -> agentId
        }
    }
}
