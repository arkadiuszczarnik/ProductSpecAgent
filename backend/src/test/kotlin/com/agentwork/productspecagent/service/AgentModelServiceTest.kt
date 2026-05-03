package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.AgentModelRegistry
import com.agentwork.productspecagent.agent.AgentModelTier
import com.agentwork.productspecagent.agent.AgentModelsProperties
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ObjectStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AgentModelServiceTest {

    private val validProps = AgentModelsProperties(
        tiers = mapOf(
            AgentModelTier.SMALL to "gpt-5-nano",
            AgentModelTier.MEDIUM to "gpt-5-mini",
            AgentModelTier.LARGE to "gpt-5-2",
        ),
        defaults = mapOf(
            "idea-to-spec" to AgentModelTier.LARGE,
            "decision" to AgentModelTier.MEDIUM,
            "feature-proposal" to AgentModelTier.MEDIUM,
            "plan-generator" to AgentModelTier.LARGE,
            "design-summary" to AgentModelTier.MEDIUM,
        ),
    )

    private lateinit var registry: AgentModelRegistry
    private lateinit var store: InMemoryObjectStore
    private lateinit var service: AgentModelService

    @BeforeEach
    fun setUp() {
        registry = AgentModelRegistry(validProps)
        store = InMemoryObjectStore()
        service = AgentModelService(registry, store)
    }

    @Test
    fun `getTier returns default when no override exists`() {
        assertThat(service.getTier("decision")).isEqualTo(AgentModelTier.MEDIUM)
        assertThat(service.getTier("idea-to-spec")).isEqualTo(AgentModelTier.LARGE)
    }

    @Test
    fun `setTier persists to objectStore and updates cache`() {
        service.setTier("decision", AgentModelTier.SMALL)
        assertThat(service.getTier("decision")).isEqualTo(AgentModelTier.SMALL)
        assertThat(store.exists("agent-models/selections.json")).isTrue()
    }

    @Test
    fun `setTier rejects unknown agentId`() {
        assertThatThrownBy { service.setTier("ghost", AgentModelTier.SMALL) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `reset removes override and falls back to default`() {
        service.setTier("decision", AgentModelTier.SMALL)
        service.reset("decision")
        assertThat(service.getTier("decision")).isEqualTo(AgentModelTier.MEDIUM)
    }

    @Test
    fun `listAll returns one entry per known agent`() {
        service.setTier("decision", AgentModelTier.SMALL)
        val list = service.listAll()
        assertThat(list).hasSize(5)
        val decision = list.first { it.agentId == "decision" }
        assertThat(decision.currentTier).isEqualTo(AgentModelTier.SMALL)
        assertThat(decision.defaultTier).isEqualTo(AgentModelTier.MEDIUM)
        assertThat(decision.isOverridden).isTrue()
        assertThat(decision.tierMapping[AgentModelTier.SMALL]).isEqualTo("gpt-5-nano")
        val ideaToSpec = list.first { it.agentId == "idea-to-spec" }
        assertThat(ideaToSpec.isOverridden).isFalse()
        assertThat(ideaToSpec.currentTier).isEqualTo(AgentModelTier.LARGE)
    }

    @Test
    fun `service survives restart by reloading from objectStore`() {
        service.setTier("decision", AgentModelTier.SMALL)
        // Simulate fresh service with same store
        val freshService = AgentModelService(registry, store)
        assertThat(freshService.getTier("decision")).isEqualTo(AgentModelTier.SMALL)
    }

    @Test
    fun `corrupt selections-json falls back to defaults without throwing`() {
        store.put("agent-models/selections.json", "{not valid json".toByteArray())
        val freshService = AgentModelService(registry, store)
        assertThat(freshService.getTier("decision")).isEqualTo(AgentModelTier.MEDIUM)
    }

    @Test
    fun `setTier failure leaves cache unchanged`() {
        val failingStore = object : ObjectStore by store {
            override fun put(key: String, bytes: ByteArray, contentType: String?) {
                throw RuntimeException("S3 down")
            }
        }
        val failingService = AgentModelService(registry, failingStore)
        assertThatThrownBy { failingService.setTier("decision", AgentModelTier.SMALL) }
            .isInstanceOf(RuntimeException::class.java)
        assertThat(failingService.getTier("decision")).isEqualTo(AgentModelTier.MEDIUM)
    }
}
