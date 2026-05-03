# Per-Agent Model Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pro Agent eine von drei Modell-Stufen (`SMALL` / `MEDIUM` / `LARGE`) wählbar machen, mit S3-First / Resource-Fallback und Admin-UI unter `/agent-models`.

**Architecture:** Zwei-Schichten-Trennung — Tier-zu-Modell-Mapping ist statische Deployment-Konfig (`application.yml`, fail-fast beim Boot validiert). Per-Agent-Selection (Agent → Tier) ist dynamische Produkt-Entscheidung (S3-persistiert in `agent-models/selections.json`, ConcurrentHashMap-Cache). `KoogAgentRunner` bekommt eine neue Signatur `run(agentId, systemPrompt, userMessage)`, die 4 Agents übergeben ihre `AGENT_ID`-Konstante.

**Tech Stack:** Kotlin 2.3 / Spring Boot 4 / JetBrains Koog 0.7.3 / Next.js 16 / React 19 / shadcn/ui (Style `base-nova`, auf `@base-ui/react`).

**Spec:** `docs/superpowers/specs/2026-05-03-per-agent-model-selection-design.md`
**Feature:** `docs/features/38-per-agent-model-selection.md`
**Branch:** `feat/per-agent-model-selection` (bereits ausgechecked, Docs-Commit existiert)

---

## File Structure

**Backend (neu):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelTier.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/OpenAiModelResolver.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelsProperties.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/AgentModelService.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/AgentModel.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/api/AgentModelController.kt`
- Tests: `OpenAiModelResolverTest`, `AgentModelRegistryTest`, `AgentModelServiceTest`, `AgentModelControllerTest`, `KoogAgentRunnerTest`

**Backend (geändert):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunner.kt` — neue Signatur, Service-Injection
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/{IdeaToSpec,Decision,FeatureProposal,PlanGenerator}Agent.kt` — `AGENT_ID` companion + Aufrufe
- `backend/src/main/kotlin/com/agentwork/productspecagent/ProductSpecAgentApplication.kt` — `@ConfigurationPropertiesScan` falls nicht vorhanden
- `backend/src/main/resources/application.yml` + `backend/src/test/resources/application.yml`

**Frontend (neu):**
- `frontend/src/components/ui/radio-group.tsx` (shadcn add)
- `frontend/src/components/agent-models/AgentModelList.tsx`
- `frontend/src/components/agent-models/AgentModelDetail.tsx`
- `frontend/src/app/agent-models/page.tsx`

**Frontend (geändert):**
- `frontend/src/lib/api.ts` — Types + Wrapper-Funktionen
- `frontend/src/components/layout/AppShell.tsx` — Rail + full-bleed

---

## Task 1: AgentModelTier Enum + OpenAiModelResolver (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelTier.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/OpenAiModelResolver.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/OpenAiModelResolverTest.kt`

- [ ] **Step 1: Write failing test for OpenAiModelResolver**

```kotlin
package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OpenAiModelResolverTest {

    @Test
    fun `resolves all GPT-5 family ids`() {
        assertThat(resolveOpenAiModel("gpt-5-nano")).isEqualTo(OpenAIModels.Chat.GPT5Nano)
        assertThat(resolveOpenAiModel("gpt-5-mini")).isEqualTo(OpenAIModels.Chat.GPT5Mini)
        assertThat(resolveOpenAiModel("gpt-5")).isEqualTo(OpenAIModels.Chat.GPT5)
        assertThat(resolveOpenAiModel("gpt-5-2")).isEqualTo(OpenAIModels.Chat.GPT5_2)
        assertThat(resolveOpenAiModel("gpt-5-2-pro")).isEqualTo(OpenAIModels.Chat.GPT5_2Pro)
    }

    @Test
    fun `resolves legacy GPT-4 ids`() {
        assertThat(resolveOpenAiModel("gpt-4o")).isEqualTo(OpenAIModels.Chat.GPT4o)
        assertThat(resolveOpenAiModel("gpt-4o-mini")).isEqualTo(OpenAIModels.Chat.GPT4oMini)
        assertThat(resolveOpenAiModel("gpt-4.1")).isEqualTo(OpenAIModels.Chat.GPT4_1)
        assertThat(resolveOpenAiModel("gpt-4.1-mini")).isEqualTo(OpenAIModels.Chat.GPT4_1Mini)
        assertThat(resolveOpenAiModel("gpt-4.1-nano")).isEqualTo(OpenAIModels.Chat.GPT4_1Nano)
    }

    @Test
    fun `throws on unknown model id`() {
        assertThatThrownBy { resolveOpenAiModel("gpt-99-turbo") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown OpenAI model id: gpt-99-turbo")
    }
}
```

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.OpenAiModelResolverTest"`
Expected: FAIL — compilation error (`resolveOpenAiModel` does not exist).

- [ ] **Step 3: Create AgentModelTier enum**

```kotlin
package com.agentwork.productspecagent.agent

enum class AgentModelTier { SMALL, MEDIUM, LARGE }
```

- [ ] **Step 4: Create OpenAiModelResolver**

```kotlin
package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel

fun resolveOpenAiModel(name: String): LLModel = when (name) {
    "gpt-4o" -> OpenAIModels.Chat.GPT4o
    "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
    "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
    "gpt-4.1-mini" -> OpenAIModels.Chat.GPT4_1Mini
    "gpt-4.1-nano" -> OpenAIModels.Chat.GPT4_1Nano
    "gpt-5-nano" -> OpenAIModels.Chat.GPT5Nano
    "gpt-5-mini" -> OpenAIModels.Chat.GPT5Mini
    "gpt-5" -> OpenAIModels.Chat.GPT5
    "gpt-5-2" -> OpenAIModels.Chat.GPT5_2
    "gpt-5-2-pro" -> OpenAIModels.Chat.GPT5_2Pro
    else -> throw IllegalStateException("Unknown OpenAI model id: $name")
}
```

- [ ] **Step 5: Run test, expect pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.OpenAiModelResolverTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelTier.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/agent/OpenAiModelResolver.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/agent/OpenAiModelResolverTest.kt
git commit -m "feat(agent-models): add AgentModelTier enum + OpenAiModelResolver"
```

---

## Task 2: AgentModelsProperties (`@ConfigurationProperties`)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelsProperties.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/ProductSpecAgentApplication.kt` (verify `@ConfigurationPropertiesScan`)

- [ ] **Step 1: Create AgentModelsProperties**

```kotlin
package com.agentwork.productspecagent.agent

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agent.models")
data class AgentModelsProperties(
    val tiers: Map<AgentModelTier, String> = emptyMap(),
    val defaults: Map<String, AgentModelTier> = emptyMap(),
)
```

- [ ] **Step 2: Verify @ConfigurationPropertiesScan**

Read `backend/src/main/kotlin/com/agentwork/productspecagent/ProductSpecAgentApplication.kt`. If `@ConfigurationPropertiesScan` is missing, add it directly under `@SpringBootApplication`. If `@EnableConfigurationProperties(AgentModelsProperties::class)` is the existing project pattern, prefer that on the class instead — be consistent with how `app.storage` properties are wired today.

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelsProperties.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/ProductSpecAgentApplication.kt
git commit -m "feat(agent-models): add AgentModelsProperties for application.yml binding"
```

---

## Task 3: AgentModelRegistry with Boot-Validation (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistryTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AgentModelRegistryTest {

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
        ),
    )

    @Test
    fun `valid properties produce working registry`() {
        val reg = AgentModelRegistry(validProps)
        assertThat(reg.agentIds()).containsExactlyInAnyOrder(
            "idea-to-spec", "decision", "feature-proposal", "plan-generator"
        )
        assertThat(reg.defaultTier("idea-to-spec")).isEqualTo(AgentModelTier.LARGE)
        assertThat(reg.modelFor(AgentModelTier.SMALL)).isEqualTo(OpenAIModels.Chat.GPT5Nano)
        assertThat(reg.modelIdFor(AgentModelTier.LARGE)).isEqualTo("gpt-5-2")
    }

    @Test
    fun `missing tier mapping fails fast`() {
        val props = validProps.copy(tiers = validProps.tiers - AgentModelTier.SMALL)
        assertThatThrownBy { AgentModelRegistry(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Tier mapping incomplete: missing SMALL")
    }

    @Test
    fun `unknown model id fails fast`() {
        val props = validProps.copy(
            tiers = validProps.tiers + (AgentModelTier.LARGE to "gpt-99-turbo")
        )
        assertThatThrownBy { AgentModelRegistry(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown OpenAI model id: gpt-99-turbo")
    }

    @Test
    fun `missing default for known agent fails fast`() {
        val props = validProps.copy(defaults = validProps.defaults - "decision")
        assertThatThrownBy { AgentModelRegistry(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Missing default tier for agent: decision")
    }

    @Test
    fun `unknown agent id in defaults fails fast`() {
        val props = validProps.copy(
            defaults = validProps.defaults + ("ghost-agent" to AgentModelTier.SMALL)
        )
        assertThatThrownBy { AgentModelRegistry(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown agent id in defaults: ghost-agent")
    }

    @Test
    fun `defaultTier throws for unknown agentId at runtime`() {
        val reg = AgentModelRegistry(validProps)
        assertThatThrownBy { reg.defaultTier("ghost") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ghost")
    }
}
```

- [ ] **Step 2: Run tests, expect failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.AgentModelRegistryTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Create AgentModelRegistry**

```kotlin
package com.agentwork.productspecagent.agent

import ai.koog.prompt.llm.LLModel
import org.springframework.stereotype.Component

@Component
class AgentModelRegistry(private val properties: AgentModelsProperties) {

    companion object {
        val KNOWN_AGENT_IDS: Set<String> = setOf(
            "idea-to-spec",
            "decision",
            "feature-proposal",
            "plan-generator",
        )
    }

    private val tierMapping: Map<AgentModelTier, LLModel>
    private val tierIdMapping: Map<AgentModelTier, String>
    private val agentDefaults: Map<String, AgentModelTier>

    init {
        AgentModelTier.entries.forEach { tier ->
            check(properties.tiers.containsKey(tier)) {
                "Tier mapping incomplete: missing $tier"
            }
        }
        tierIdMapping = properties.tiers.toMap()
        tierMapping = properties.tiers.mapValues { (_, name) -> resolveOpenAiModel(name) }

        KNOWN_AGENT_IDS.forEach { id ->
            check(properties.defaults.containsKey(id)) {
                "Missing default tier for agent: $id"
            }
        }
        properties.defaults.keys.forEach { id ->
            check(id in KNOWN_AGENT_IDS) {
                "Unknown agent id in defaults: $id"
            }
        }
        agentDefaults = properties.defaults.toMap()
    }

    fun agentIds(): Set<String> = KNOWN_AGENT_IDS

    fun defaultTier(agentId: String): AgentModelTier =
        agentDefaults[agentId] ?: throw IllegalArgumentException("Unknown agent id: $agentId")

    fun modelFor(tier: AgentModelTier): LLModel =
        tierMapping.getValue(tier)

    fun modelIdFor(tier: AgentModelTier): String =
        tierIdMapping.getValue(tier)

    fun tierMappingView(): Map<AgentModelTier, String> = tierIdMapping
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.AgentModelRegistryTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistryTest.kt
git commit -m "feat(agent-models): add AgentModelRegistry with boot-validation"
```

---

## Task 4: Domain Types (AgentModelInfo + UpdateAgentModelRequest)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/AgentModel.kt`

- [ ] **Step 1: Create domain types**

```kotlin
package com.agentwork.productspecagent.domain

import com.agentwork.productspecagent.agent.AgentModelTier

data class AgentModelInfo(
    val agentId: String,
    val displayName: String,
    val defaultTier: AgentModelTier,
    val currentTier: AgentModelTier,
    val isOverridden: Boolean,
    val tierMapping: Map<AgentModelTier, String>,
)

data class UpdateAgentModelRequest(val tier: AgentModelTier)
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/AgentModel.kt
git commit -m "feat(agent-models): add AgentModelInfo + UpdateAgentModelRequest domain types"
```

---

## Task 5: AgentModelService with S3-First Cache (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/AgentModelService.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/AgentModelServiceTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
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
        assertThat(list).hasSize(4)
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
```

- [ ] **Step 2: Run tests, expect failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.service.AgentModelServiceTest"`
Expected: FAIL — compilation error.

- [ ] **Step 3: Create AgentModelService**

```kotlin
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
            else -> agentId
        }
    }
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.service.AgentModelServiceTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/AgentModelService.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/service/AgentModelServiceTest.kt
git commit -m "feat(agent-models): add AgentModelService with S3-first cache"
```

---

## Task 6: AgentModelController with REST + Exception-Mapping (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/AgentModelController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/AgentModelControllerTest.kt`
- (Optional) Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt` if `IllegalArgumentException` is not yet mapped to 404 globally — prefer a local `@ExceptionHandler` in this controller instead, mirroring `PromptController.handleNotFound`.

- [ ] **Step 1: Write failing MockMvc tests**

Test file requires the same `application.yml` migration as later tasks; for now we add the test against a `@SpringBootTest` so it picks up `TestStorageConfig`. The `application.yml` migration in Task 10 must complete before this test runs cleanly — but writing it first is fine because it will FAIL initially (the goal of TDD).

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.AgentModelRegistry
import com.agentwork.productspecagent.service.AgentModelService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class AgentModelControllerTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var service: AgentModelService
    @Autowired private lateinit var registry: AgentModelRegistry

    @AfterEach
    fun cleanup() {
        registry.agentIds().forEach { service.reset(it) }
    }

    @Test
    fun `GET lists all four agents with default tiers`() {
        mvc.perform(get("/api/v1/agent-models"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[?(@.agentId == 'decision')].defaultTier").value("MEDIUM"))
            .andExpect(jsonPath("$[?(@.agentId == 'decision')].currentTier").value("MEDIUM"))
            .andExpect(jsonPath("$[?(@.agentId == 'decision')].isOverridden").value(false))
            .andExpect(jsonPath("$[?(@.agentId == 'decision')].tierMapping.MEDIUM").exists())
    }

    @Test
    fun `PUT updates tier and GET reflects override`() {
        mvc.perform(
            put("/api/v1/agent-models/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tier":"SMALL"}""")
        ).andExpect(status().isNoContent)

        assertThat(service.getTier("decision")).isEqualTo(com.agentwork.productspecagent.agent.AgentModelTier.SMALL)

        mvc.perform(get("/api/v1/agent-models"))
            .andExpect(jsonPath("$[?(@.agentId == 'decision')].currentTier").value("SMALL"))
            .andExpect(jsonPath("$[?(@.agentId == 'decision')].isOverridden").value(true))
    }

    @Test
    fun `PUT rejects invalid tier with 400`() {
        mvc.perform(
            put("/api/v1/agent-models/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tier":"XL"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `PUT for unknown agent returns 404`() {
        mvc.perform(
            put("/api/v1/agent-models/ghost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tier":"SMALL"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE removes override and returns 204`() {
        service.setTier("decision", com.agentwork.productspecagent.agent.AgentModelTier.SMALL)
        mvc.perform(delete("/api/v1/agent-models/decision"))
            .andExpect(status().isNoContent)
        assertThat(service.getTier("decision")).isEqualTo(com.agentwork.productspecagent.agent.AgentModelTier.MEDIUM)
    }

    @Test
    fun `DELETE for unknown agent returns 404`() {
        mvc.perform(delete("/api/v1/agent-models/ghost"))
            .andExpect(status().isNotFound)
    }
}
```

- [ ] **Step 2: Run tests, expect failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.api.AgentModelControllerTest"`
Expected: FAIL — compilation error (controller missing) AND/OR Spring-Boot-Bootstrap-Failure (because application.yml does not yet contain `agent.models`). Both failures confirm we are in the right direction; Task 10 will fix the YAML.

- [ ] **Step 3: Create AgentModelController**

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.AgentModelInfo
import com.agentwork.productspecagent.domain.UpdateAgentModelRequest
import com.agentwork.productspecagent.service.AgentModelService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/agent-models")
class AgentModelController(private val service: AgentModelService) {

    @GetMapping
    fun list(): List<AgentModelInfo> = service.listAll()

    @PutMapping("/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(@PathVariable agentId: String, @RequestBody body: UpdateAgentModelRequest) {
        service.setTier(agentId, body.tier)
    }

    @DeleteMapping("/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@PathVariable agentId: String) {
        service.reset(agentId)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleNotFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()
}
```

- [ ] **Step 4: Verify (later, after YAML migration in Task 10) that all 6 tests pass**

The test suite will pass only after `application.yml` is migrated (Task 10). Until then, leave the test as-is — the failing test enforces that we complete the migration.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/AgentModelController.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/AgentModelControllerTest.kt
git commit -m "feat(agent-models): add REST controller + MockMvc tests (yml migration follows)"
```

---

## Task 7: KoogAgentRunner Refactor (TDD)

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunner.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunnerTest.kt`

- [ ] **Step 1: Write failing test**

The test bypasses the real OpenAI executor by providing a stub `PromptExecutor` that captures the `LLModel` it receives. Koog's `AIAgent.run` will call `executor.execute(...)` with the configured `llmModel` — that's our verification point.

```kotlin
package com.agentwork.productspecagent.agent

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMConnectionParams
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import com.agentwork.productspecagent.domain.AgentModelInfo
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class KoogAgentRunnerTest {

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
        ),
    )

    @Test
    fun `run uses tier from service to select model`() = runBlocking {
        val registry = AgentModelRegistry(validProps)
        val service = com.agentwork.productspecagent.service.AgentModelService(registry, InMemoryObjectStore())
        val capturing = CapturingExecutor()
        val runner = KoogAgentRunner(capturing, service, registry)

        runner.run("decision", "you are a test", "hello")
        assertThat(capturing.lastModel).isEqualTo(OpenAIModels.Chat.GPT5Mini)

        service.setTier("decision", AgentModelTier.LARGE)
        runner.run("decision", "you are a test", "hello again")
        assertThat(capturing.lastModel).isEqualTo(OpenAIModels.Chat.GPT5_2)
    }

    @Test
    fun `run with unknown agentId throws`() = runBlocking {
        val registry = AgentModelRegistry(validProps)
        val service = com.agentwork.productspecagent.service.AgentModelService(registry, InMemoryObjectStore())
        val runner = KoogAgentRunner(CapturingExecutor(), service, registry)
        assertThatThrownBy {
            runBlocking { runner.run("ghost", "x", "y") }
        }.isInstanceOf(IllegalArgumentException::class.java)
        Unit
    }

    private class CapturingExecutor : PromptExecutor {
        var lastModel: LLModel? = null

        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<Any>): List<Message.Response> {
            lastModel = model
            return listOf(Message.Assistant("ok", null))
        }

        override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            lastModel = model
            return flowOf("ok")
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): Any? = null
    }
}
```

> **Note for the implementer:** Koog 0.7.3's `PromptExecutor` interface signature has evolved across versions. If the exact method signatures above do not match, open `prompt-executor-model-jvm-0.7.3-sources.jar` (under `~/.gradle/caches/modules-2/files-2.1/ai.koog/prompt-executor-model-jvm/0.7.3/`) and adjust the `CapturingExecutor` overrides to match. The test intent stays the same: assert the `LLModel` argument passed to the executor.

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.KoogAgentRunnerTest"`
Expected: FAIL — current `KoogAgentRunner` has different signature.

- [ ] **Step 3: Refactor KoogAgentRunner**

```kotlin
package com.agentwork.productspecagent.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import com.agentwork.productspecagent.service.AgentModelService

@Component
class KoogAgentRunner(
    @Qualifier("openAIExecutor") private val promptExecutor: PromptExecutor,
    private val modelService: AgentModelService,
    private val modelRegistry: AgentModelRegistry,
) {
    private val logger = LoggerFactory.getLogger(KoogAgentRunner::class.java)

    suspend fun run(agentId: String, systemPrompt: String, userMessage: String): String {
        val tier = modelService.getTier(agentId)
        val model = modelRegistry.modelFor(tier)
        logger.debug("Running Koog agent={} tier={} model={} promptLen={}", agentId, tier, modelRegistry.modelIdFor(tier), systemPrompt.length)

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            systemPrompt = systemPrompt,
            llmModel = model,
        )
        return agent.run(userMessage)
    }
}
```

- [ ] **Step 4: Run test, expect pass**

Run: `./gradlew test --tests "com.agentwork.productspecagent.agent.KoogAgentRunnerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunner.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunnerTest.kt
git commit -m "refactor(KoogAgentRunner): take agentId, resolve model via AgentModelService"
```

---

## Task 8: Add AGENT_ID + Update 4 Agent Call-Sites

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DecisionAgent.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgent.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgent.kt`

- [ ] **Step 1: Update IdeaToSpecAgent**

Add `AGENT_ID` constant and update `runAgent` to pass it through:

```kotlin
class IdeaToSpecAgent(/* unchanged */) {
    companion object {
        const val AGENT_ID = "idea-to-spec"
        // existing companion members (uuid, parseWizardFeatures, ...) stay below
    }

    protected open suspend fun runAgent(systemPrompt: String, userMessage: String): String {
        val result = koogRunner?.run(AGENT_ID, systemPrompt, userMessage)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
        logger.info("Agent raw response (last 500 chars): ...{}", result.takeLast(500))
        return result
    }
    // rest unchanged
}
```

> Important: do NOT remove the existing `companion object`'s `uuid` field or `parseWizardFeatures` method — only ADD the `AGENT_ID` constant inside it.

- [ ] **Step 2: Update DecisionAgent**

```kotlin
class DecisionAgent(/* unchanged */) {
    companion object {
        const val AGENT_ID = "decision"
    }

    protected open suspend fun runAgent(prompt: String): String {
        return koogRunner?.run(AGENT_ID, promptService.get("decision-system"), prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
    }
    // rest unchanged
}
```

- [ ] **Step 3: Update FeatureProposalAgent**

```kotlin
class FeatureProposalAgent(/* unchanged */) {
    companion object {
        const val AGENT_ID = "feature-proposal"
    }

    protected open suspend fun runAgent(prompt: String): String =
        koogRunner?.run(AGENT_ID, promptService.get("feature-proposal-system"), prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
    // rest unchanged
}
```

- [ ] **Step 4: Update PlanGeneratorAgent**

```kotlin
class PlanGeneratorAgent(/* unchanged */) {
    companion object {
        const val AGENT_ID = "plan-generator"
    }

    protected open suspend fun runAgent(prompt: String): String {
        return koogRunner?.run(AGENT_ID, promptService.get("plan-system"), prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
    }
    // rest unchanged
}
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/agent/DecisionAgent.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgent.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgent.kt
git commit -m "feat(agent-models): pass AGENT_ID from each agent into KoogAgentRunner"
```

---

## Task 9: application.yml Migration (main + test)

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`

- [ ] **Step 1: Update main application.yml**

Find the existing block:

```yaml
agent:
  model: gpt-5.5
```

Replace with:

```yaml
agent:
  models:
    tiers:
      SMALL: "gpt-5-nano"
      MEDIUM: "gpt-5-mini"
      LARGE: "gpt-5-2"
    defaults:
      idea-to-spec: LARGE
      decision: MEDIUM
      feature-proposal: MEDIUM
      plan-generator: LARGE
```

- [ ] **Step 2: Update test application.yml**

Find the `agent:` block in `backend/src/test/resources/application.yml` and replace it with:

```yaml
agent:
  models:
    tiers:
      SMALL: "gpt-4o-mini"
      MEDIUM: "gpt-4o-mini"
      LARGE: "gpt-4o-mini"
    defaults:
      idea-to-spec: LARGE
      decision: MEDIUM
      feature-proposal: MEDIUM
      plan-generator: LARGE
```

> Rationale: tests don't actually call OpenAI — `runAgent` is overridden in test subclasses. The mapping just needs to pass boot-validation. All three tiers map to `gpt-4o-mini` for simplicity.

- [ ] **Step 3: Run full suite**

Run: `./gradlew test`
Expected: PASS — all suites including the previously failing `AgentModelControllerTest` (Task 6) now boot cleanly.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application.yml \
        backend/src/test/resources/application.yml
git commit -m "feat(agent-models): migrate application.yml from agent.model to agent.models.{tiers,defaults}"
```

---

## Task 10: Update Existing Agent Tests for New `koogRunner.run` Signature

**Files:**
- Modify: bestehende Test-Klassen, die `KoogAgentRunner` per Stub-Subklasse oder Mock direkt aufrufen.

- [ ] **Step 1: Identify affected test files**

Run: `grep -rn "koogRunner\?.run\|KoogAgentRunner(" backend/src/test/`
Expected output: list of files (likely `IdeaToSpecAgentTest`, `DecisionAgentTest`, `FeatureProposalAgentTest`, `PlanGeneratorAgentTest`, `ChatControllerTest`, `WizardChatControllerTest`).

- [ ] **Step 2: Adjust each occurrence**

Two patterns to expect:

**Pattern A** — anonymous subclass overriding `runAgent(prompt: String)` or `runAgent(systemPrompt, userMessage)`. These DO NOT call `koogRunner.run` directly — they intercept at the agent layer. **No change needed**.

**Pattern B** — explicit construction of `KoogAgentRunner` (for example in a `@TestConfiguration` bean). The constructor signature changed:

```kotlin
// before
KoogAgentRunner(stubExecutor, "gpt-4o-mini")

// after
KoogAgentRunner(stubExecutor, agentModelService, agentModelRegistry)
```

If a test directly invokes `runner.run(systemPrompt, userMessage)` (old 2-arg form), update to `runner.run("decision", systemPrompt, userMessage)` (or whichever `AGENT_ID` matches the test scope).

- [ ] **Step 3: Run full backend suite**

Run: `./gradlew test`
Expected: PASS — all suites green.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/
git commit -m "test(agent-models): adjust existing tests to new KoogAgentRunner signature"
```

> Note: if Pattern B occurs and requires constructing a real `AgentModelService` + `AgentModelRegistry` in a test, the boilerplate is identical to `AgentModelServiceTest.setUp` from Task 5 — copy that pattern directly. Avoid creating MockK or other mocking-library beans; the project uses real services + hand-rolled stubs.

---

## Task 11: Frontend — API Client (Types + Wrappers)

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: Add types and endpoint wrappers**

Find the end of the existing `// Prompts`-related section in `api.ts` (around the `listPrompts`, `getPrompt`, `updatePrompt`, `resetPrompt` block) and append:

```typescript
// Agent Models

export type AgentModelTier = "SMALL" | "MEDIUM" | "LARGE";

export interface AgentModelInfo {
  agentId: string;
  displayName: string;
  defaultTier: AgentModelTier;
  currentTier: AgentModelTier;
  isOverridden: boolean;
  tierMapping: Record<AgentModelTier, string>;
}

export async function listAgentModels(): Promise<AgentModelInfo[]> {
  return apiFetch<AgentModelInfo[]>("/api/v1/agent-models");
}

export async function updateAgentModel(agentId: string, tier: AgentModelTier): Promise<void> {
  await apiFetch<void>(`/api/v1/agent-models/${agentId}`, {
    method: "PUT",
    body: JSON.stringify({ tier }),
  });
}

export async function resetAgentModel(agentId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/agent-models/${agentId}`, { method: "DELETE" });
}
```

- [ ] **Step 2: Verify lint**

Run from `frontend/`: `npm run lint`
Expected: PASS (no lint errors on changed file).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(agent-models): add frontend API client types + wrappers"
```

---

## Task 12: Frontend — Install shadcn radio-group + Build List/Detail Components

**Files:**
- Create (via shadcn CLI): `frontend/src/components/ui/radio-group.tsx`
- Create: `frontend/src/components/agent-models/AgentModelList.tsx`
- Create: `frontend/src/components/agent-models/AgentModelDetail.tsx`

- [ ] **Step 1: Install radio-group via shadcn**

Run from `frontend/`: `npx shadcn@latest add radio-group`
Expected: `frontend/src/components/ui/radio-group.tsx` created. The CLI uses style `base-nova` (configured in `components.json`) which is built on `@base-ui/react` — verify the generated file imports from `@base-ui/react`, not Radix. If it imports from `@radix-ui`, manually rewrite the imports to the base-ui equivalent (the base-ui Radio component API is compatible).

- [ ] **Step 2: Create AgentModelList**

```tsx
"use client";
import { Circle } from "lucide-react";
import type { AgentModelInfo } from "@/lib/api";
import { cn } from "@/lib/utils";

interface AgentModelListProps {
  items: AgentModelInfo[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function AgentModelList({ items, selectedId, onSelect }: AgentModelListProps) {
  return (
    <div className="overflow-y-auto py-2">
      {items.map((it) => (
        <button
          key={it.agentId}
          onClick={() => onSelect(it.agentId)}
          className={cn(
            "w-full px-4 py-2 text-left text-sm flex items-center gap-2 hover:bg-muted/50 transition-colors",
            selectedId === it.agentId && "bg-muted",
          )}
        >
          <span className="flex-1 truncate">{it.displayName}</span>
          {it.isOverridden && (
            <Circle size={8} className="fill-primary text-primary" aria-label="Überschrieben" />
          )}
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 3: Create AgentModelDetail**

```tsx
"use client";
import { useEffect, useState } from "react";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import {
  type AgentModelInfo,
  type AgentModelTier,
  listAgentModels,
  resetAgentModel,
  updateAgentModel,
} from "@/lib/api";

interface AgentModelDetailProps {
  agentId: string;
  onChange: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

const TIERS: AgentModelTier[] = ["SMALL", "MEDIUM", "LARGE"];

export function AgentModelDetail({ agentId, onChange, onDirtyChange }: AgentModelDetailProps) {
  const [item, setItem] = useState<AgentModelInfo | null>(null);
  const [tier, setTier] = useState<AgentModelTier | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listAgentModels()
      .then((all) => {
        const found = all.find((it) => it.agentId === agentId) ?? null;
        setItem(found);
        setTier(found?.currentTier ?? null);
      })
      .catch((e) => setError(String(e)));
  }, [agentId]);

  useEffect(() => {
    if (!item || !tier) onDirtyChange(false);
    else onDirtyChange(tier !== item.currentTier);
  }, [item, tier, onDirtyChange]);

  if (!item || !tier) return <div className="p-6 text-sm text-muted-foreground">Lädt …</div>;

  const dirty = tier !== item.currentTier;

  const handleSave = async () => {
    if (!dirty) return;
    setSaving(true);
    setError(null);
    try {
      await updateAgentModel(agentId, tier);
      onChange();
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    if (!window.confirm("Auf Default zurücksetzen?")) return;
    setSaving(true);
    setError(null);
    try {
      await resetAgentModel(agentId);
      onChange();
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div>
        <h2 className="text-lg font-semibold">{item.displayName}</h2>
        <p className="text-sm text-muted-foreground mt-1">
          Wähle das Modell für diesen Agent. Default: <strong>{item.defaultTier}</strong>.
        </p>
      </div>

      {error && (
        <div className="text-sm text-destructive border border-destructive/50 rounded p-3">
          {error}
        </div>
      )}

      <RadioGroup value={tier} onValueChange={(v) => setTier(v as AgentModelTier)}>
        {TIERS.map((t) => (
          <div key={t} className="flex items-center gap-3 py-2">
            <RadioGroupItem id={`tier-${t}`} value={t} />
            <Label htmlFor={`tier-${t}`} className="cursor-pointer">
              <span className="font-medium">{t}</span>
              <span className="ml-2 text-muted-foreground">— {item.tierMapping[t]}</span>
            </Label>
          </div>
        ))}
      </RadioGroup>

      <div className="flex gap-2">
        <Button onClick={handleSave} disabled={!dirty || saving}>
          Speichern
        </Button>
        <Button variant="outline" onClick={handleReset} disabled={!item.isOverridden || saving}>
          Auf Default zurücksetzen
        </Button>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Verify lint**

Run from `frontend/`: `npm run lint`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/ui/radio-group.tsx \
        frontend/src/components/agent-models/AgentModelList.tsx \
        frontend/src/components/agent-models/AgentModelDetail.tsx \
        frontend/components.json \
        frontend/package.json frontend/package-lock.json
git commit -m "feat(agent-models): add list + detail components with shadcn radio-group"
```

> If shadcn add modified `components.json` or installed new dependencies, include those changes. If not, omit them.

---

## Task 13: Frontend — `/agent-models` Page + Layout Wiring

**Files:**
- Create: `frontend/src/app/agent-models/page.tsx`
- Modify: `frontend/src/components/layout/AppShell.tsx`

- [ ] **Step 1: Create agent-models page**

```tsx
"use client";
import { useCallback, useEffect, useState } from "react";
import { listAgentModels, type AgentModelInfo } from "@/lib/api";
import { AgentModelList } from "@/components/agent-models/AgentModelList";
import { AgentModelDetail } from "@/components/agent-models/AgentModelDetail";

export default function AgentModelsPage() {
  const [items, setItems] = useState<AgentModelInfo[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reloadTick, setReloadTick] = useState(0);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    listAgentModels().then(setItems).catch(() => setItems([]));
  }, [reloadTick]);

  const handleSelect = useCallback(
    (id: string) => {
      if (id === selectedId) return;
      if (dirty && !window.confirm("Änderungen verwerfen?")) return;
      setDirty(false);
      setSelectedId(id);
    },
    [selectedId, dirty],
  );

  return (
    <div className="h-full flex flex-col">
      <div className="px-8 py-6 border-b">
        <h1 className="text-xl font-semibold">Agent-Modelle</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Wähle pro Agent das verwendete OpenAI-Modell. Änderungen werden direkt nach dem Speichern wirksam.
        </p>
      </div>
      <div className="flex-1 grid grid-cols-[320px_1fr] min-h-0">
        <AgentModelList
          items={items}
          selectedId={selectedId}
          onSelect={handleSelect}
        />
        <div className="border-l overflow-y-auto">
          {selectedId ? (
            <AgentModelDetail
              key={selectedId}
              agentId={selectedId}
              onChange={() => setReloadTick((t) => t + 1)}
              onDirtyChange={setDirty}
            />
          ) : (
            <div className="h-full flex items-center justify-center text-sm text-muted-foreground">
              Wähle einen Agent aus der Liste, um sein Modell zu konfigurieren.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Update AppShell — add rail entry + full-bleed**

In `frontend/src/components/layout/AppShell.tsx`:

1. Extend the `lucide-react` import to include `Cpu`:

```tsx
import { Cpu, FolderKanban, MessageSquareText, Package, Plus, Settings, Sparkles } from "lucide-react";
```

2. Add a new rail entry directly after the `/prompts` entry (around lines 73-77):

```tsx
<RailItem
  href="/agent-models"
  icon={<Cpu size={20} />}
  label="Agent-Modelle"
  active={pathname?.startsWith("/agent-models") ?? false}
/>
```

(Match the exact `RailItem` prop names used by the existing entries — copy the `/prompts` entry as the template.)

3. Extend the `isFullBleed` check (around line 95):

```tsx
const isFullBleed =
  isWorkspace ||
  (pathname?.startsWith("/prompts") ?? false) ||
  (pathname?.startsWith("/agent-models") ?? false);
```

- [ ] **Step 3: Verify lint**

Run from `frontend/`: `npm run lint`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/agent-models/page.tsx \
        frontend/src/components/layout/AppShell.tsx
git commit -m "feat(agent-models): add /agent-models page and AppShell rail entry"
```

---

## Task 14: End-to-End Browser Verification

**Files:** none (manual verification)

- [ ] **Step 1: Start backend and frontend**

```bash
./start.sh
```

Wait for `Started ProductSpecAgentApplication` (backend) and `Local: http://localhost:3001` (frontend, port may vary).

- [ ] **Step 2: Verify the GET endpoint**

```bash
curl -s http://localhost:8080/api/v1/agent-models | jq '.[].agentId'
```

Expected: 4 entries: `idea-to-spec`, `decision`, `feature-proposal`, `plan-generator`.

- [ ] **Step 3: Browser flow**

Open `http://localhost:3001/agent-models` and verify:

1. Rail shows new `Cpu` icon next to existing `MessageSquareText` (Prompts).
2. List shows 4 entries with display names: „Idea-to-Spec", „Decision", „Feature Proposal", „Plan Generator".
3. Click „Decision" → detail panel shows three radio options with model IDs (`SMALL — gpt-5-nano`, `MEDIUM — gpt-5-mini`, `LARGE — gpt-5-2`). Default radio = MEDIUM.
4. Switch to SMALL, click „Speichern" → no error, override-badge `●` appears next to „Decision" in list.
5. Hard-reload page → SMALL is still selected, badge still there (proving S3 persistence).
6. Click „Auf Default zurücksetzen", confirm dialog → tier resets to MEDIUM, badge gone.
7. Switch tier to LARGE, then click another list entry without saving → confirm-dialog „Änderungen verwerfen?".

- [ ] **Step 4: Verify a real agent call uses the new tier**

Open an existing project (or create one), trigger any wizard step that calls `DecisionAgent` or `IdeaToSpecAgent`, and confirm in the backend logs:

```
DEBUG ... KoogAgentRunner — Running Koog agent=decision tier=MEDIUM model=gpt-5-mini ...
```

- [ ] **Step 5: Stop services**

`Ctrl-C` on `./start.sh`.

- [ ] **Step 6: Commit (no code change — just confirm clean tree)**

```bash
git status
```

Expected: `working tree clean`. No commit needed.

---

## Acceptance Criteria Mapping

| # | Spec-Kriterium | Task |
|---|---|---|
| 1 | `application.yml` enthält `agent.models.tiers` + `defaults` | Task 9 |
| 2 | `agent.model` entfernt; `KoogAgentRunner` nutzt Service+Registry | Task 7, 9 |
| 3 | Resolver kennt mind. `gpt-5-nano`/`mini`/`gpt-5`/`gpt-5-2` + GPT-4-Familie; unbekannt → fail-fast | Task 1 |
| 4 | YAML-Validierung beim Boot bricht bei fehlenden / unbekannten Werten ab | Task 3 |
| 5 | `GET /api/v1/agent-models` mit 4 Agents + Tier-Mapping | Task 6 |
| 6 | `PUT /{agentId}` 204, persistiert + cached | Task 5, 6 |
| 7 | `PUT` invalid Tier → 400, unknown Agent → 404 | Task 6 |
| 8 | `DELETE /{agentId}` → 204, GET danach `isOverridden=false` | Task 6 |
| 9 | 4 Agents übergeben `AGENT_ID`; bestehende Tests bleiben grün | Task 8, 10 |
| 10 | Frontend-Route `/agent-models` + Save/Reset E2E + Rail-Eintrag | Task 11, 12, 13, 14 |

---

## Self-Review

**Spec coverage:** Alle 10 Akzeptanzkriterien sind in der Mapping-Tabelle einem Task zugeordnet. Out-of-Scope-Punkte (Pro-Projekt-Override, Edit-History, Multi-Replica-Cache) sind im Spec dokumentiert und nicht im Plan — bewusst weggelassen.

**Placeholder scan:** Kein „TBD" / „TODO" / „implement later". Code-Snippets sind vollständig. Hinweise auf Anpassungen (z. B. Koog-Executor-Signatur in Task 7, shadcn-Imports in Task 12) sind explizit beschrieben mit Ort und Verifizierung.

**Type consistency:**
- `AgentModelTier` Enum überall identisch (3 Werte).
- `agentId`-Strings: 4 Konstanten (`idea-to-spec`, `decision`, `feature-proposal`, `plan-generator`) konsistent in `KNOWN_AGENT_IDS` (Task 3), `displayNameFor` (Task 5), Test-Daten (Task 3, 5, 6, 7), Agent-`AGENT_ID`-Konstanten (Task 8) und YAML (Task 9).
- `tierMapping` Field-Name in `AgentModelInfo` (Task 4), Service (Task 5), MockMvc-Assertion (Task 6) und Frontend-Type (Task 11) identisch.
- `selections.json`-Pfad `agent-models/selections.json` konsistent in Service (Task 5) und Test (Task 5).
- `KoogAgentRunner.run(agentId, systemPrompt, userMessage)`-Signatur konsistent in Test (Task 7), Implementation (Task 7) und 4 Agent-Aufrufen (Task 8).
- `Label` und `Button` aus `@/components/ui/{label,button}` — beide bereits installiert (siehe `frontend/src/components/ui/`-Listing).

Plan ist DRY (kein Pattern-Code zweimal aufgeführt — Verweise auf Task 5 wo nötig), YAGNI (keine Out-of-Scope-Features), TDD (Tasks 1, 3, 5, 6, 7 starten mit failing test) und committet feingranular (14 Commits insgesamt).
