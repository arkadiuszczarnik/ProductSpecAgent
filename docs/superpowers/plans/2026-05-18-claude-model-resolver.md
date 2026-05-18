# Claude Model Resolver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Claude model resolver and a global resolver switch so the backend can run all configured agent tiers on Anthropic models instead of OpenAI models.

**Architecture:** Introduce a provider enum in `AgentModelsProperties`, add a `ClaudeModelResolver` for Koog `AnthropicModels`, and update `AgentModelRegistry` to resolve all tier ids through the selected resolver. Keep repository defaults on OpenAI while documenting a commented Claude configuration in both application YAML files.

**Tech Stack:** Kotlin, Spring Boot configuration properties, Koog 0.8.0 model definitions, JUnit 5, AssertJ

---

### Task 1: Add failing tests for resolver selection

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistryTest.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/ClaudeModelResolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `claude resolver maps configured tiers`() {
    val props = AgentModelsProperties(
        resolver = AgentModelResolverType.CLAUDE,
        tiers = mapOf(
            AgentModelTier.SMALL to "claude-haiku-4-5",
            AgentModelTier.MEDIUM to "claude-sonnet-4-5",
            AgentModelTier.LARGE to "claude-sonnet-4-6",
        ),
        defaults = validDefaults,
    )

    val registry = AgentModelRegistry(props)

    assertThat(registry.modelIdFor(AgentModelTier.LARGE)).isEqualTo("claude-sonnet-4-6")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.AgentModelRegistryTest --tests com.agentwork.productspecagent.agent.ClaudeModelResolverTest`
Expected: FAIL because `AgentModelResolverType` and `resolveClaudeModel(...)` do not exist yet.

- [ ] **Step 3: Add unknown-id failing test**

```kotlin
@Test
fun `throws on unknown claude model id`() {
    assertThatThrownBy { resolveClaudeModel("claude-unknown") }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Unknown Claude model id: claude-unknown")
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.ClaudeModelResolverTest`
Expected: FAIL because `resolveClaudeModel(...)` is missing.

### Task 2: Implement Claude resolver and registry selection

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/ClaudeModelResolver.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelsProperties.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt`

- [ ] **Step 1: Add the provider enum and property**

```kotlin
enum class AgentModelResolverType {
    OPENAI,
    CLAUDE,
}

@ConfigurationProperties(prefix = "agent.models")
data class AgentModelsProperties(
    val resolver: AgentModelResolverType = AgentModelResolverType.OPENAI,
    val tiers: Map<AgentModelTier, String> = emptyMap(),
    val defaults: Map<String, AgentModelTier> = emptyMap(),
)
```

- [ ] **Step 2: Implement the Claude resolver**

```kotlin
fun resolveClaudeModel(name: String): LLModel = when (name) {
    "claude-3-haiku" -> AnthropicModels.Haiku_3
    "claude-haiku-4-5" -> AnthropicModels.Haiku_4_5
    "claude-sonnet-4-0" -> AnthropicModels.Sonnet_4
    "claude-sonnet-4-5" -> AnthropicModels.Sonnet_4_5
    "claude-sonnet-4-6" -> AnthropicModels.Sonnet_4_6
    "claude-opus-4-0" -> AnthropicModels.Opus_4
    "claude-opus-4-1" -> AnthropicModels.Opus_4_1
    "claude-opus-4-5" -> AnthropicModels.Opus_4_5
    "claude-opus-4-6" -> AnthropicModels.Opus_4_6
    else -> throw IllegalStateException("Unknown Claude model id: $name")
}
```

- [ ] **Step 3: Switch registry resolution by configured resolver**

```kotlin
tierMapping = properties.tiers.mapValues { (_, name) ->
    when (properties.resolver) {
        AgentModelResolverType.OPENAI -> resolveOpenAiModel(name)
        AgentModelResolverType.CLAUDE -> resolveClaudeModel(name)
    }
}
```

- [ ] **Step 4: Run focused tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.AgentModelRegistryTest --tests com.agentwork.productspecagent.agent.OpenAiModelResolverTest --tests com.agentwork.productspecagent.agent.ClaudeModelResolverTest`
Expected: PASS

### Task 3: Update runtime configuration examples

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`

- [ ] **Step 1: Add resolver selection and explicit provider enablement**

```yaml
ai:
  koog:
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY:not-set}
    anthropic:
      enabled: false
      api-key: ${ANTHROPIC_API_KEY:not-set}

agent:
  models:
    resolver: openai
```

- [ ] **Step 2: Add commented Claude example tiers**

```yaml
    tiers:
      SMALL: "gpt-5.4-nano"
      MEDIUM: "gpt-5.4-mini"
      LARGE: "gpt-5-2"
      # Claude example:
      # SMALL: "claude-haiku-4-5"
      # MEDIUM: "claude-sonnet-4-5"
      # LARGE: "claude-sonnet-4-6"
```

- [ ] **Step 3: Run focused tests again**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.AgentModelRegistryTest --tests com.agentwork.productspecagent.agent.OpenAiModelResolverTest --tests com.agentwork.productspecagent.agent.ClaudeModelResolverTest`
Expected: PASS
