# Design Image Analysis Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated image analysis agent that converts uploaded design images into structured design context and feeds that context into the existing single-canvas design generator.

**Architecture:** Extend the simplified `DesignWorkbench` with persisted image-analysis state, add a `DesignImageAnalysisAgent` and `/design/image/analyze` endpoint, and make `generate` fill missing image analysis before calling `DesignVariantAgent`. Keep upload synchronous and simple; the frontend explicitly calls analysis after image upload and only shows compact cards plus retry on failure.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, kotlinx.serialization, Koog 0.8 multimodal prompt parts, JUnit 5/MockMvc, Next.js App Router, React 19, Zustand, TypeScript, Tailwind CSS 4.

---

## File Structure

Backend:

- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt`
  - Add `imageAnalysis`, `imageAnalysisError`, and detailed image-analysis serializable models.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt`
  - Persist image analysis, persist retryable error state, read stored image bytes, and preserve image analysis when only description changes.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunner.kt`
  - Add a multimodal image runner method using Koog `ContentPart.Image`.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignImageAnalysisAgent.kt`
  - Parse structured JSON from the vision model, provide deterministic fallback when no runner is configured, and throw a typed exception on malformed model JSON.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt`
  - Include image analysis in `DesignGenerationInput` and in the design-generation prompt.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt`
  - Add `analyzeImage`, call it from `generate` when needed, and pass image analysis into `DesignVariantAgent`.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt`
  - Add `POST /image/analyze`.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt`
  - Register `design-image-analysis`.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelsProperties.kt` only if constructor defaults require no change after config additions.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`
  - Register `design-image-analysis-system`.
- Add `backend/src/main/resources/prompts/design-image-analysis-system.md`
  - System prompt for image-to-design-JSON analysis.
- Modify `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-dev.yml`, `backend/src/test/resources/application.yml`
  - Add default tier for `design-image-analysis`.
- Modify backend tests:
  - `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunnerTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/agent/DesignImageAnalysisAgentTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgentTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistryTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/service/AgentModelServiceTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/service/PromptRegistryTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/service/PromptServiceTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/api/PromptControllerTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/api/AgentModelControllerTest.kt`

Frontend:

- Modify `frontend/src/lib/api.ts`
  - Add image-analysis types and `analyzeDesignImage`.
- Modify `frontend/src/lib/stores/design-workbench-store.ts`
  - Add `analyzingImage` and `analyzeImage`.
- Modify `frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx`
  - Analyze after image upload, show compact analysis, stop generate on analysis failure, show retry only on `imageAnalysisError`.
- Modify `frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx`
  - Include `analyzingImage` in the loading and disabled state passed to the panel.

Docs:

- Modify `docs/features/47-agentic-design-workbench.md`
  - Mark the image-analysis iteration as planned/implemented after code lands.

---

### Task 1: Backend Domain And Storage State

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt`

- [ ] **Step 1: Add failing storage tests**

Append these helpers and tests to `DesignWorkbenchStorageTest.kt`:

```kotlin
private fun sampleImageAnalysis() = com.agentwork.productspecagent.domain.DesignImageAnalysis(
    summary = "Dense SaaS dashboard with a calm operational layout.",
    palette = listOf(
        com.agentwork.productspecagent.domain.DesignColor("#111827", "background", "dominant", "Dark shell"),
        com.agentwork.productspecagent.domain.DesignColor("#2F80ED", "primary-action", "accent", "Blue buttons"),
    ),
    typography = listOf(
        com.agentwork.productspecagent.domain.DesignTypographySignal("ui-sans", "body", "regular", "Clean interface text"),
    ),
    layoutHierarchy = listOf(
        com.agentwork.productspecagent.domain.DesignLayoutRegion("Sidebar", 1, 1, "Primary navigation"),
        com.agentwork.productspecagent.domain.DesignLayoutRegion("KPI row", 2, 2, "Top metrics"),
    ),
    components = listOf(
        com.agentwork.productspecagent.domain.DesignComponentSignal("Card", "summary", "Metric cards"),
    ),
    moodTags = listOf("enterprise", "calm"),
    brandSignals = listOf("rounded cards", "blue action color"),
    designBrief = "Create a calm enterprise dashboard with dark navigation and blue actions.",
)

@Test
fun `save image analysis persists detailed JSON`() {
    storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")

    val updated = storage.saveImageAnalysis("p1", sampleImageAnalysis())

    assertEquals("Dense SaaS dashboard with a calm operational layout.", updated.imageAnalysis?.summary)
    assertEquals("#111827", updated.imageAnalysis?.palette?.first()?.hex)
    assertNull(updated.imageAnalysisError)
    assertEquals(updated.imageAnalysis, storage.load("p1").imageAnalysis)
}

@Test
fun `save image analysis error stores retryable message`() {
    storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")

    val updated = storage.saveImageAnalysisError("p1", "Vision provider unavailable")

    assertEquals("Vision provider unavailable", updated.imageAnalysisError)
    assertNull(updated.imageAnalysis)
}

@Test
fun `new image upload clears image analysis and error`() {
    storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")
    storage.saveImageAnalysis("p1", sampleImageAnalysis())
    storage.saveImageAnalysisError("p1", "Old error")

    val updated = storage.saveImageInput("p1", "new.png", byteArrayOf(4, 5, 6), "image/png")

    assertNull(updated.imageAnalysis)
    assertNull(updated.imageAnalysisError)
}

@Test
fun `description update preserves existing image analysis`() {
    storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")
    storage.saveImageAnalysis("p1", sampleImageAnalysis())

    val updated = storage.saveInput("p1", "Use this dashboard style", null)

    assertEquals("Use this dashboard style", updated.description)
    assertEquals("dashboard.png", updated.imageInput?.originalName)
    assertEquals("Dense SaaS dashboard with a calm operational layout.", updated.imageAnalysis?.summary)
}

@Test
fun `read image input returns stored bytes`() {
    storage.saveImageInput("p1", "dashboard.png", byteArrayOf(1, 2, 3), "image/png")

    assertContentEquals(byteArrayOf(1, 2, 3), storage.readImageInput("p1"))
}
```

- [ ] **Step 2: Run storage tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.storage.DesignWorkbenchStorageTest
```

Expected: FAIL with unresolved references for `DesignImageAnalysis`, `DesignColor`, `imageAnalysis`, `saveImageAnalysis`, `saveImageAnalysisError`, and `readImageInput`.

- [ ] **Step 3: Extend domain model**

Modify `DesignWorkbench.kt` so the `DesignWorkbench` constructor includes the two new fields immediately after `imageInput`:

```kotlin
@Serializable
data class DesignWorkbench(
    val projectId: String,
    val description: String? = null,
    val imageInput: DesignImageInput? = null,
    val imageAnalysis: DesignImageAnalysis? = null,
    val imageAnalysisError: String? = null,
    val analysis: DesignAnalysis? = null,
    val currentDesign: GeneratedDesign? = null,
    val updatedAt: String,
)
```

Add these serializable data classes below `DesignImageInput`:

```kotlin
@Serializable
data class DesignImageAnalysis(
    val summary: String,
    val palette: List<DesignColor>,
    val typography: List<DesignTypographySignal>,
    val layoutHierarchy: List<DesignLayoutRegion>,
    val components: List<DesignComponentSignal>,
    val moodTags: List<String>,
    val brandSignals: List<String>,
    val designBrief: String,
)

@Serializable
data class DesignColor(
    val hex: String,
    val role: String,
    val weight: String,
    val notes: String,
)

@Serializable
data class DesignTypographySignal(
    val category: String,
    val role: String,
    val weight: String,
    val notes: String,
)

@Serializable
data class DesignLayoutRegion(
    val name: String,
    val order: Int,
    val priority: Int,
    val description: String,
)

@Serializable
data class DesignComponentSignal(
    val name: String,
    val role: String,
    val description: String,
)
```

- [ ] **Step 4: Implement storage helpers and invalidation**

Modify `DesignWorkbenchStorage.kt`:

```kotlin
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
```

Update `saveImageInput` copy block so it clears image analysis:

```kotlin
return save(
    existing.copy(
        imageInput = image,
        imageAnalysis = null,
        imageAnalysisError = null,
        analysis = null,
        currentDesign = null,
    ),
)
```

Add helpers:

```kotlin
fun readImageInput(projectId: String): ByteArray {
    val image = load(projectId).imageInput ?: throw NoSuchElementException("design image input not found: $projectId")
    return objectStore.get(image.contentRef) ?: throw NoSuchElementException("design image object not found: ${image.contentRef}")
}

fun saveImageAnalysis(projectId: String, analysis: DesignImageAnalysis): DesignWorkbench =
    save(load(projectId).copy(imageAnalysis = analysis, imageAnalysisError = null))

fun saveImageAnalysisError(projectId: String, message: String): DesignWorkbench =
    save(load(projectId).copy(imageAnalysisError = message.trim().ifBlank { "Image analysis failed." }))
```

Add import:

```kotlin
import com.agentwork.productspecagent.domain.DesignImageAnalysis
```

- [ ] **Step 5: Update tests that expected description-only input to clear images**

In `DesignWorkbenchStorageTest.kt`, replace `save input clears existing image when no replacement image is supplied` with:

```kotlin
@Test
fun `save input preserves existing image when no replacement image is supplied`() {
    storage.saveImageInput(
        projectId = "p1",
        originalName = "reference.png",
        bytes = byteArrayOf(1, 2, 3),
        contentType = "image/png",
    )

    val updated = storage.saveInput("p1", "Use the same reference", null)

    assertEquals("reference.png", updated.imageInput?.originalName)
}
```

- [ ] **Step 6: Run storage tests to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.storage.DesignWorkbenchStorageTest
```

Expected: PASS.

- [ ] **Step 7: Commit domain and storage**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt
git commit -m "feat(design): persist image analysis state"
```

---

### Task 2: Koog Multimodal Runner

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunner.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunnerTest.kt`

- [ ] **Step 1: Add failing multimodal runner test**

In `KoogAgentRunnerTest.kt`, add `design-image-analysis` to `validProps.defaults`:

```kotlin
"design-image-analysis" to AgentModelTier.MEDIUM,
```

Extend `CapturingExecutor`:

```kotlin
var lastPrompt: Prompt? = null

override suspend fun execute(
    prompt: Prompt,
    model: LLModel,
    tools: List<ToolDescriptor>,
): List<Message.Response> {
    lastPrompt = prompt
    lastModel = model
    return listOf(Message.Assistant(content = "ok", metaInfo = ResponseMetaInfo.Empty))
}
```

Add test:

```kotlin
@Test
fun `runWithImage attaches image bytes to user message`(): Unit = runBlocking {
    val registry = AgentModelRegistry(validProps)
    val service = AgentModelService(registry, InMemoryObjectStore())
    val capturing = CapturingExecutor()
    val runner = KoogAgentRunner(capturing, service, registry)

    val response = runner.runWithImage(
        agentId = "design-image-analysis",
        systemPrompt = "Analyze visual design.",
        userMessage = "Extract design signals.",
        imageBytes = byteArrayOf(1, 2, 3),
        contentType = "image/png",
        fileName = "dashboard.png",
    )

    assertThat(response).isEqualTo("ok")
    val user = capturing.lastPrompt?.messages?.filterIsInstance<Message.User>()?.single()
    assertThat(user?.parts?.filterIsInstance<ai.koog.prompt.message.ContentPart.Image>()).hasSize(1)
    val image = user?.parts?.filterIsInstance<ai.koog.prompt.message.ContentPart.Image>()?.single()
    assertThat(image?.mimeType).isEqualTo("image/png")
    assertThat(image?.fileName).isEqualTo("dashboard.png")
}
```

- [ ] **Step 2: Run runner test to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.KoogAgentRunnerTest
```

Expected: FAIL because `runWithImage` does not exist.

- [ ] **Step 3: Implement `runWithImage`**

Modify `KoogAgentRunner.kt` imports:

```kotlin
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
```

Add method:

```kotlin
suspend fun runWithImage(
    agentId: String,
    systemPrompt: String,
    userMessage: String,
    imageBytes: ByteArray,
    contentType: String,
    fileName: String,
): String {
    val tier = modelService.getTier(agentId)
    val model = modelRegistry.modelFor(tier)
    val format = contentType.substringAfter("/", "png").substringBefore(";").ifBlank { "png" }
    logger.debug("Running Koog image agent={} tier={} model={} promptLen={} imageBytes={}", agentId, tier, modelRegistry.modelIdFor(tier), systemPrompt.length, imageBytes.size)

    val prompt = prompt("${agentId}-image-analysis") {
        system(systemPrompt)
        user {
            text(userMessage)
            image(
                ContentPart.Image(
                    content = AttachmentContent.Binary.Bytes(imageBytes),
                    format = format,
                    mimeType = contentType,
                    fileName = fileName,
                ),
            )
        }
    }
    return promptExecutor.execute(prompt = prompt, model = model, tools = emptyList()).last().content
}
```

- [ ] **Step 4: Run runner test to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.KoogAgentRunnerTest
```

Expected: PASS.

- [ ] **Step 5: Commit multimodal runner**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunner.kt backend/src/test/kotlin/com/agentwork/productspecagent/agent/KoogAgentRunnerTest.kt
git commit -m "feat(agent): support image prompts"
```

---

### Task 3: Image Analysis Agent, Prompt, And Registries

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignImageAnalysisAgent.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/DesignImageAnalysisAgentTest.kt`
- Create: `backend/src/main/resources/prompts/design-image-analysis-system.md`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Modify: `backend/src/test/resources/application.yml`
- Modify registry-related tests listed in File Structure.

- [ ] **Step 1: Add failing agent tests**

Create `DesignImageAnalysisAgentTest.kt`:

```kotlin
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignImageInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesignImageAnalysisAgentTest {

    private val image = DesignImageInput(
        originalName = "dashboard.png",
        contentRef = "projects/p1/design/input/reference-image",
        contentType = "image/png",
        sizeBytes = 3,
        uploadedAt = "2026-05-10T00:00:00Z",
    )

    @Test
    fun `analyze parses structured agent response`() {
        var capturedPrompt = ""
        val agent = object : DesignImageAnalysisAgent(null, null) {
            override suspend fun runAgent(input: DesignImageAnalysisInput, prompt: String): String {
                capturedPrompt = prompt
                return """
                    {
                      "summary": "Operational dashboard screenshot",
                      "palette": [{"hex":"#111827","role":"background","weight":"dominant","notes":"Dark shell"}],
                      "typography": [{"category":"ui-sans","role":"body","weight":"regular","notes":"Clean labels"}],
                      "layoutHierarchy": [{"name":"Sidebar","order":1,"priority":1,"description":"Left navigation"}],
                      "components": [{"name":"Metric card","role":"summary","description":"KPI cards"}],
                      "moodTags": ["enterprise", "calm"],
                      "brandSignals": ["rounded cards"],
                      "designBrief": "Create a calm enterprise dashboard."
                    }
                """.trimIndent()
            }
        }

        val result = agent.analyze(DesignImageAnalysisInput("p1", image, byteArrayOf(1, 2, 3)))

        assertTrue(capturedPrompt.contains("dashboard.png"))
        assertEquals("Operational dashboard screenshot", result.analysis.summary)
        assertEquals("#111827", result.analysis.palette.single().hex)
        assertEquals("Create a calm enterprise dashboard.", result.analysis.designBrief)
    }

    @Test
    fun `analyze throws typed exception for invalid JSON`() {
        val agent = object : DesignImageAnalysisAgent(null, null) {
            override suspend fun runAgent(input: DesignImageAnalysisInput, prompt: String): String = "not-json"
        }

        assertFailsWith<InvalidDesignImageAnalysisException> {
            agent.analyze(DesignImageAnalysisInput("p1", image, byteArrayOf(1, 2, 3)))
        }
    }

    @Test
    fun `fallback analysis is deterministic and generic`() {
        val result = DesignImageAnalysisAgent(null, null)
            .analyze(DesignImageAnalysisInput("p1", image, byteArrayOf(1, 2, 3)))

        assertEquals("Image metadata captured for design generation.", result.analysis.summary)
        assertTrue(result.analysis.designBrief.contains("dashboard.png"))
        assertTrue(result.analysis.moodTags.contains("reference"))
    }
}
```

- [ ] **Step 2: Run agent tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.DesignImageAnalysisAgentTest
```

Expected: FAIL because the agent file does not exist.

- [ ] **Step 3: Implement `DesignImageAnalysisAgent`**

Create `DesignImageAnalysisAgent.kt`:

```kotlin
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignColor
import com.agentwork.productspecagent.domain.DesignComponentSignal
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import com.agentwork.productspecagent.domain.DesignImageInput
import com.agentwork.productspecagent.domain.DesignLayoutRegion
import com.agentwork.productspecagent.domain.DesignTypographySignal
import com.agentwork.productspecagent.service.PromptService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component

class InvalidDesignImageAnalysisException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Serializable
data class DesignImageAnalysisInput(
    val projectId: String,
    val image: DesignImageInput,
    val bytes: ByteArray,
)

@Serializable
data class DesignImageAnalysisResult(
    val analysis: DesignImageAnalysis,
)

@Component
open class DesignImageAnalysisAgent(
    private val koogRunner: KoogAgentRunner? = null,
    private val promptService: PromptService? = null,
) {
    companion object {
        const val AGENT_ID = "design-image-analysis"
    }

    private val json = Json { ignoreUnknownKeys = true }

    open fun analyze(input: DesignImageAnalysisInput): DesignImageAnalysisResult {
        val prompt = buildPrompt(input)
        val raw = runBlocking { runAgent(input, prompt) }
        if (raw != null) {
            return parseResponse(raw)
        }
        return fallback(input)
    }

    protected open suspend fun runAgent(input: DesignImageAnalysisInput, prompt: String): String? =
        koogRunner?.runWithImage(
            agentId = AGENT_ID,
            systemPrompt = promptService?.get("design-image-analysis-system") ?: defaultSystemPrompt(),
            userMessage = prompt,
            imageBytes = input.bytes,
            contentType = input.image.contentType,
            fileName = input.image.originalName,
        )

    private fun parseResponse(raw: String): DesignImageAnalysisResult {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        return runCatching {
            DesignImageAnalysisResult(normalize(json.decodeFromString<DesignImageAnalysis>(jsonStr)))
        }.getOrElse { error ->
            throw InvalidDesignImageAnalysisException("Image analysis returned invalid JSON.", error)
        }
    }

    private fun normalize(analysis: DesignImageAnalysis): DesignImageAnalysis =
        analysis.copy(
            summary = analysis.summary.trim().ifBlank { "Image analysis completed." },
            palette = analysis.palette.take(8).map {
                it.copy(hex = normalizeHex(it.hex), role = it.role.trim(), weight = it.weight.trim(), notes = it.notes.trim())
            },
            typography = analysis.typography.take(8).map {
                it.copy(category = it.category.trim(), role = it.role.trim(), weight = it.weight.trim(), notes = it.notes.trim())
            },
            layoutHierarchy = analysis.layoutHierarchy.sortedWith(compareBy<DesignLayoutRegion> { it.order }.thenBy { it.priority }).take(12),
            components = analysis.components.take(20).map {
                it.copy(name = it.name.trim(), role = it.role.trim(), description = it.description.trim())
            },
            moodTags = analysis.moodTags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().take(12),
            brandSignals = analysis.brandSignals.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(12),
            designBrief = analysis.designBrief.trim().ifBlank { analysis.summary.trim() },
        )

    private fun normalizeHex(value: String): String {
        val trimmed = value.trim()
        val hex = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return if (Regex("^#[0-9a-fA-F]{6}$").matches(hex)) hex.uppercase() else "#000000"
    }

    private fun fallback(input: DesignImageAnalysisInput): DesignImageAnalysisResult =
        DesignImageAnalysisResult(
            DesignImageAnalysis(
                summary = "Image metadata captured for design generation.",
                palette = listOf(DesignColor("#F7F7F4", "background", "generic", "Fallback neutral background")),
                typography = listOf(DesignTypographySignal("ui-sans", "body", "regular", "Fallback interface typography")),
                layoutHierarchy = listOf(DesignLayoutRegion("Reference image", 1, 1, "Use the uploaded image as broad visual guidance.")),
                components = listOf(DesignComponentSignal("Reference image", "visual-reference", input.image.originalName)),
                moodTags = listOf("reference"),
                brandSignals = listOf("Uploaded image: ${input.image.originalName}"),
                designBrief = "Use ${input.image.originalName} as a visual reference. The fallback did not inspect pixels.",
            ),
        )

    private fun buildPrompt(input: DesignImageAnalysisInput): String = buildString {
        appendLine("Project ID: ${input.projectId}")
        appendLine("Image name: ${input.image.originalName}")
        appendLine("Content type: ${input.image.contentType}")
        appendLine("Size bytes: ${input.image.sizeBytes}")
        appendLine()
        appendLine("Analyze the attached image as visual design reference. Return only valid JSON in the required shape.")
    }

    private fun defaultSystemPrompt(): String =
        "Analyze uploaded UI/design reference images into structured visual design JSON. Focus on layout, colors, typography, components, mood, and brand signals. Do not identify people or infer sensitive traits."
}
```

- [ ] **Step 4: Add image-analysis prompt**

Create `backend/src/main/resources/prompts/design-image-analysis-system.md`:

```markdown
You are the Design Image Analysis agent for Product Spec Agent.

Analyze the attached image as a visual design reference. Extract design-relevant signals only.

Do not identify people. Do not infer sensitive personal traits. Do not describe private attributes. If people are visible, describe only layout-relevant visual composition such as "portrait area" or "human figure used as hero image".

Return exactly one JSON object and no markdown:

{
  "summary": "short human-readable visual design summary",
  "palette": [
    { "hex": "#RRGGBB", "role": "background|text|primary-action|accent|surface|border", "weight": "dominant|secondary|accent", "notes": "short note" }
  ],
  "typography": [
    { "category": "ui-sans|serif-display|mono-labels|handwritten|unknown", "role": "heading|body|label|navigation", "weight": "light|regular|medium|bold|mixed", "notes": "short note" }
  ],
  "layoutHierarchy": [
    { "name": "region name", "order": 1, "priority": 1, "description": "short region description" }
  ],
  "components": [
    { "name": "component name", "role": "component purpose", "description": "short visual description" }
  ],
  "moodTags": ["short-lowercase-tag"],
  "brandSignals": ["short visual brand cue"],
  "designBrief": "compact prompt-ready instruction for generating a similar HTML layout"
}

Rules:

- Use 3 to 8 palette colors.
- Normalize colors as #RRGGBB when visually possible.
- Prefer typography categories over exact font names.
- Keep layout hierarchy ordered from top/outer/primary to lower/inner/secondary.
- Keep all strings concise.
```

- [ ] **Step 5: Register prompt and model defaults**

In `PromptRegistry.kt`, add definition after `design-variant-system`:

```kotlin
PromptDefinition(
    id = "design-image-analysis-system",
    title = "Design-Image-Analysis — System-Prompt",
    description = "Rolle des Bildanalyse-Agents (Designsignale aus Referenzbildern als JSON).",
    agent = "DesignImageAnalysis",
    resourcePath = "/prompts/design-image-analysis-system.md",
    validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
),
```

In `AgentModelRegistry.kt`, add:

```kotlin
"design-image-analysis",
```

In all three application YAML files, add:

```yaml
      design-image-analysis: MEDIUM
```

- [ ] **Step 6: Update registry tests**

Update tests that assert registry counts or explicit IDs:

- Add `"design-image-analysis" to AgentModelTier.MEDIUM` to model default maps.
- Add `"design-image-analysis"` to expected agent IDs.
- Add `"design-image-analysis-system"` to expected prompt IDs.
- Update prompt count assertions by `+1`.
- Add controller expectation:

```kotlin
.andExpect(jsonPath("$[?(@.id == 'design-image-analysis-system')]").exists())
```

- [ ] **Step 7: Run agent and registry tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.DesignImageAnalysisAgentTest --tests com.agentwork.productspecagent.agent.AgentModelRegistryTest --tests com.agentwork.productspecagent.service.PromptRegistryTest --tests com.agentwork.productspecagent.api.PromptControllerTest
```

Expected: PASS.

- [ ] **Step 8: Commit agent and registries**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignImageAnalysisAgent.kt backend/src/main/kotlin/com/agentwork/productspecagent/agent/AgentModelRegistry.kt backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt backend/src/main/resources/prompts/design-image-analysis-system.md backend/src/main/resources/application.yml backend/src/main/resources/application-dev.yml backend/src/test/resources/application.yml backend/src/test/kotlin/com/agentwork/productspecagent/agent backend/src/test/kotlin/com/agentwork/productspecagent/service backend/src/test/kotlin/com/agentwork/productspecagent/api
git commit -m "feat(design): add image analysis agent"
```

---

### Task 4: Service And Controller Orchestration

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt`

- [ ] **Step 1: Add failing service tests**

In `DesignWorkbenchServiceTest.kt`, add imports:

```kotlin
import com.agentwork.productspecagent.agent.DesignImageAnalysisAgent
import com.agentwork.productspecagent.agent.DesignImageAnalysisInput
import com.agentwork.productspecagent.agent.DesignImageAnalysisResult
import com.agentwork.productspecagent.agent.InvalidDesignImageAnalysisException
import com.agentwork.productspecagent.domain.DesignColor
import com.agentwork.productspecagent.domain.DesignComponentSignal
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import com.agentwork.productspecagent.domain.DesignLayoutRegion
import com.agentwork.productspecagent.domain.DesignTypographySignal
```

Add helper:

```kotlin
private fun imageAnalysis() = DesignImageAnalysis(
    summary = "Dashboard image",
    palette = listOf(DesignColor("#111827", "background", "dominant", "Dark shell")),
    typography = listOf(DesignTypographySignal("ui-sans", "body", "regular", "Clean labels")),
    layoutHierarchy = listOf(DesignLayoutRegion("Sidebar", 1, 1, "Left navigation")),
    components = listOf(DesignComponentSignal("Metric card", "summary", "KPI cards")),
    moodTags = listOf("enterprise"),
    brandSignals = listOf("blue actions"),
    designBrief = "Use dark navigation and compact KPI cards.",
)
```

Add tests:

```kotlin
@Test
fun `analyze image rejects missing image`() {
    service.saveInput("p1", "Text only", null, null, null)

    assertFailsWith<InvalidDesignWorkbenchException> {
        service.analyzeImage("p1")
    }
}

@Test
fun `analyze image stores analysis`() {
    val service = service(
        imageAnalysisAgent = object : DesignImageAnalysisAgent(null, null) {
            override fun analyze(input: DesignImageAnalysisInput): DesignImageAnalysisResult =
                DesignImageAnalysisResult(imageAnalysis())
        },
    )
    service.saveInput("p1", null, "dash.png", byteArrayOf(1, 2, 3), "image/png")

    val workbench = service.analyzeImage("p1")

    assertEquals("Dashboard image", workbench.imageAnalysis?.summary)
    assertNull(workbench.imageAnalysisError)
}

@Test
fun `analyze image stores error on failure`() {
    val service = service(
        imageAnalysisAgent = object : DesignImageAnalysisAgent(null, null) {
            override fun analyze(input: DesignImageAnalysisInput): DesignImageAnalysisResult =
                throw InvalidDesignImageAnalysisException("Image analysis returned invalid JSON.")
        },
    )
    service.saveInput("p1", null, "dash.png", byteArrayOf(1, 2, 3), "image/png")

    assertFailsWith<InvalidDesignWorkbenchException> {
        service.analyzeImage("p1")
    }

    assertEquals("Image analysis returned invalid JSON.", service.get("p1").imageAnalysisError)
}

@Test
fun `generate analyzes image when analysis is missing and passes it to design agent`() {
    var capturedInput: DesignGenerationInput? = null
    val service = service(
        imageAnalysisAgent = object : DesignImageAnalysisAgent(null, null) {
            override fun analyze(input: DesignImageAnalysisInput): DesignImageAnalysisResult =
                DesignImageAnalysisResult(imageAnalysis())
        },
        designVariantAgent = object : DesignVariantAgent(null) {
            override fun generate(input: DesignGenerationInput): DesignGenerationResult {
                capturedInput = input
                return DesignGenerationResult(
                    analysis = DesignAnalysis("Generated", "Direction", "Because"),
                    title = "Generated",
                    html = validHtml("Generated"),
                    rationale = "Used analysis",
                )
            }
        },
    )
    service.saveInput("p1", "Build dashboard", "dash.png", byteArrayOf(1, 2, 3), "image/png")

    val workbench = service.generate("p1")

    assertEquals("Dashboard image", capturedInput?.imageAnalysis?.summary)
    assertEquals("Generated", workbench.currentDesign?.title)
}
```

- [ ] **Step 2: Adjust service test factory**

Change the test helper signature:

```kotlin
private fun service(
    imageAnalysisAgent: DesignImageAnalysisAgent = object : DesignImageAnalysisAgent(null, null) {
        override fun analyze(input: DesignImageAnalysisInput): DesignImageAnalysisResult =
            DesignImageAnalysisResult(imageAnalysis())
    },
    designVariantAgent: DesignVariantAgent = object : DesignVariantAgent(null) {
        override fun generate(input: DesignGenerationInput): DesignGenerationResult =
            DesignGenerationResult(
                analysis = DesignAnalysis("Initial", "Clean", "Fallback"),
                title = "Initial",
                html = validHtml("Initial"),
                rationale = "Initial design",
            )
    },
) = DesignWorkbenchService(
    storage = storage,
    previewValidator = DesignPreviewValidator(),
    imageAnalysisAgent = imageAnalysisAgent,
    designVariantAgent = designVariantAgent,
)
```

- [ ] **Step 3: Run service tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.DesignWorkbenchServiceTest
```

Expected: FAIL because service constructor and `analyzeImage` are not implemented.

- [ ] **Step 4: Implement service orchestration**

Modify `DesignWorkbenchService` constructor:

```kotlin
private val imageAnalysisAgent: DesignImageAnalysisAgent,
private val designVariantAgent: DesignVariantAgent,
```

Add imports:

```kotlin
import com.agentwork.productspecagent.agent.DesignImageAnalysisAgent
import com.agentwork.productspecagent.agent.DesignImageAnalysisInput
import com.agentwork.productspecagent.agent.InvalidDesignImageAnalysisException
```

Add method:

```kotlin
fun analyzeImage(projectId: String): DesignWorkbench {
    val workbench = storage.load(projectId)
    val image = workbench.imageInput ?: throw InvalidDesignWorkbenchException("Design image input is required for analysis.")
    val bytes = try {
        storage.readImageInput(projectId)
    } catch (e: NoSuchElementException) {
        val message = e.message ?: "Design image input is missing."
        storage.saveImageAnalysisError(projectId, message)
        throw InvalidDesignWorkbenchException(message)
    }

    return try {
        val result = imageAnalysisAgent.analyze(DesignImageAnalysisInput(projectId, image, bytes))
        storage.saveImageAnalysis(projectId, result.analysis)
    } catch (e: InvalidDesignImageAnalysisException) {
        val message = e.message ?: "Image analysis failed."
        storage.saveImageAnalysisError(projectId, message)
        throw InvalidDesignWorkbenchException(message)
    } catch (e: RuntimeException) {
        val message = e.message ?: "Image analysis failed."
        storage.saveImageAnalysisError(projectId, message)
        throw InvalidDesignWorkbenchException(message)
    }
}
```

Modify `generate` before calling `designVariantAgent.generate`:

```kotlin
val readyWorkbench = if (workbench.imageInput != null && workbench.imageAnalysis == null) {
    analyzeImage(projectId)
} else {
    workbench
}
```

Then pass `readyWorkbench`:

```kotlin
val result = designVariantAgent.generate(
    DesignGenerationInput(
        projectId = projectId,
        description = readyWorkbench.description,
        image = readyWorkbench.imageInput,
        imageAnalysis = readyWorkbench.imageAnalysis,
    ),
)
```

- [ ] **Step 5: Update controller tests**

In `DesignWorkbenchControllerTest.kt`, add a primary test image analysis bean:

```kotlin
@Bean
@Primary
fun testDesignImageAnalysisAgent(): com.agentwork.productspecagent.agent.DesignImageAnalysisAgent =
    object : com.agentwork.productspecagent.agent.DesignImageAnalysisAgent(null, null) {
        override fun analyze(input: com.agentwork.productspecagent.agent.DesignImageAnalysisInput): com.agentwork.productspecagent.agent.DesignImageAnalysisResult =
            com.agentwork.productspecagent.agent.DesignImageAnalysisResult(
                com.agentwork.productspecagent.domain.DesignImageAnalysis(
                    summary = "Controller image analysis",
                    palette = listOf(com.agentwork.productspecagent.domain.DesignColor("#111827", "background", "dominant", "Dark shell")),
                    typography = listOf(com.agentwork.productspecagent.domain.DesignTypographySignal("ui-sans", "body", "regular", "Clean labels")),
                    layoutHierarchy = listOf(com.agentwork.productspecagent.domain.DesignLayoutRegion("Sidebar", 1, 1, "Left navigation")),
                    components = listOf(com.agentwork.productspecagent.domain.DesignComponentSignal("Metric card", "summary", "KPI cards")),
                    moodTags = listOf("enterprise"),
                    brandSignals = listOf("blue actions"),
                    designBrief = "Use dark navigation and compact KPI cards.",
                ),
            )
    }
```

Add tests:

```kotlin
@Test
fun `POST image analyze stores image analysis`() {
    advanceToDesign(projectId)
    val image = MockMultipartFile("file", "dashboard.png", "image/png", byteArrayOf(1, 2, 3))
    mockMvc.perform(putInput(file = image))
        .andExpect(status().isOk())

    mockMvc.perform(post("/api/v1/projects/$projectId/design/image/analyze"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageAnalysis.summary").value("Controller image analysis"))
        .andExpect(jsonPath("$.imageAnalysis.palette[0].hex").value("#111827"))
        .andExpect(jsonPath("$.imageAnalysisError").doesNotExist())
}

@Test
fun `POST image analyze rejects missing image`() {
    advanceToDesign(projectId)
    mockMvc.perform(putInput(description = "Text only"))
        .andExpect(status().isOk())

    mockMvc.perform(post("/api/v1/projects/$projectId/design/image/analyze"))
        .andExpect(status().isBadRequest())
}
```

- [ ] **Step 6: Add controller endpoint**

In `DesignWorkbenchController.kt`, add:

```kotlin
@PostMapping("/image/analyze")
fun analyzeImage(@PathVariable projectId: String): DesignWorkbench {
    validateDesignAccess(projectId)
    return mapInvalidWorkbench { service.analyzeImage(projectId) }
}
```

- [ ] **Step 7: Run service and controller tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.DesignWorkbenchServiceTest --tests com.agentwork.productspecagent.api.DesignWorkbenchControllerTest
```

Expected: PASS.

- [ ] **Step 8: Commit service and API**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt
git commit -m "feat(design): analyze uploaded images"
```

---

### Task 5: Feed Image Analysis Into Design Generation

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgentTest.kt`
- Modify all compile sites constructing `DesignGenerationInput`.

- [ ] **Step 1: Add failing design variant prompt test**

In `DesignVariantAgentTest.kt`, add imports for image-analysis domain classes and helper equivalent to Task 4 `imageAnalysis()`.

Modify `generate parses structured agent response` input:

```kotlin
DesignGenerationInput(
    projectId = "p1",
    description = "Build an operations dashboard",
    image = null,
    imageAnalysis = imageAnalysis(),
)
```

Add assertion:

```kotlin
assertTrue(capturedPrompt.contains("Use dark navigation and compact KPI cards."))
assertTrue(capturedPrompt.contains("\"palette\""))
```

Update every other `DesignGenerationInput` construction to pass `imageAnalysis = null`.

- [ ] **Step 2: Run DesignVariantAgent test to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.DesignVariantAgentTest
```

Expected: FAIL because `DesignGenerationInput` does not include `imageAnalysis`.

- [ ] **Step 3: Extend generation input and prompt**

Modify `DesignVariantAgent.kt`:

```kotlin
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import kotlinx.serialization.encodeToString
```

Extend input:

```kotlin
@Serializable
data class DesignGenerationInput(
    val projectId: String,
    val description: String?,
    val image: DesignImageInput?,
    val imageAnalysis: DesignImageAnalysis?,
)
```

In `buildPrompt`, after reference image block add:

```kotlin
appendLine()
appendLine("Image analysis:")
if (input.imageAnalysis != null) {
    appendLine(json.encodeToString(input.imageAnalysis))
    appendLine()
    appendLine("Design brief from image analysis:")
    appendLine(input.imageAnalysis.designBrief)
} else {
    appendLine("No image analysis provided.")
}
```

In fallback source selection, no logic change is required.

- [ ] **Step 4: Update compile sites**

Add `imageAnalysis = ...` in:

- `DesignWorkbenchService.generate`: `imageAnalysis = readyWorkbench.imageAnalysis`
- all tests constructing `DesignGenerationInput`: use `imageAnalysis = null` unless testing analysis.
- `DesignWorkbenchControllerTest` primary `DesignVariantAgent`: assert or ignore `input.imageAnalysis`.

- [ ] **Step 5: Run backend compile and focused tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.DesignVariantAgentTest --tests com.agentwork.productspecagent.service.DesignWorkbenchServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit generation context**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt backend/src/test/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgentTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt
git commit -m "feat(design): use image analysis for generation"
```

---

### Task 6: Frontend API, Store, And UI

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/stores/design-workbench-store.ts`
- Modify: `frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx`
- Modify: `frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx` only if state plumbing requires it.

- [ ] **Step 1: Extend API types**

In `frontend/src/lib/api.ts`, add after `DesignImageInput`:

```ts
export interface DesignColor {
  hex: string;
  role: string;
  weight: string;
  notes: string;
}

export interface DesignTypographySignal {
  category: string;
  role: string;
  weight: string;
  notes: string;
}

export interface DesignLayoutRegion {
  name: string;
  order: number;
  priority: number;
  description: string;
}

export interface DesignComponentSignal {
  name: string;
  role: string;
  description: string;
}

export interface DesignImageAnalysis {
  summary: string;
  palette: DesignColor[];
  typography: DesignTypographySignal[];
  layoutHierarchy: DesignLayoutRegion[];
  components: DesignComponentSignal[];
  moodTags: string[];
  brandSignals: string[];
  designBrief: string;
}
```

Extend `DesignWorkbench`:

```ts
imageAnalysis?: DesignImageAnalysis | null;
imageAnalysisError?: string | null;
```

Add API helper:

```ts
export async function analyzeDesignImage(projectId: string): Promise<DesignWorkbench> {
  return apiFetch<DesignWorkbench>(`/api/v1/projects/${encodeURIComponent(projectId)}/design/image/analyze`, { method: "POST" });
}
```

- [ ] **Step 2: Extend Zustand store**

In `design-workbench-store.ts`, import `analyzeDesignImage`, add state field and action:

```ts
analyzingImage: boolean;
analyzeImage: (projectId: string) => Promise<boolean>;
```

Initial state:

```ts
analyzingImage: false,
```

Action:

```ts
analyzeImage: async (projectId) => {
  set({ analyzingImage: true, error: null });
  try {
    const workbench = await analyzeDesignImage(projectId);
    set({ workbench, analyzingImage: false });
    return true;
  } catch (err) {
    const message = err instanceof Error ? err.message : "Failed to analyze design image";
    set((state) => ({
      error: message,
      analyzingImage: false,
      workbench: state.workbench
        ? { ...state.workbench, imageAnalysisError: message }
        : state.workbench,
    }));
    return false;
  }
},
```

Update `reset`:

```ts
reset: () => set({ workbench: null, loading: false, working: false, analyzingImage: false, error: null }),
```

- [ ] **Step 3: Update input panel generation flow**

In `DesignInputPanel.tsx`:

- Read `analyzeImage` and `analyzingImage` from store.
- Include `analyzingImage` in disabled states.
- After `saveInput`, if `imageFile` existed, call `analyzeImage(projectId)` and stop if it returns `false`.
- Before `generate`, if current workbench has `imageInput` and no `imageAnalysis`, call `analyzeImage(projectId)` and stop on failure.

Use this logic inside `handleGenerate` after save:

```ts
let shouldAnalyze = Boolean(imageFile);
if (shouldSaveInput) {
  await saveInput(projectId, description, imageFile);
}
const latest = useDesignWorkbenchStore.getState().workbench;
if (latest?.imageInput && !latest.imageAnalysis) {
  shouldAnalyze = true;
}
if (shouldAnalyze) {
  const analyzed = await analyzeImage(projectId);
  if (!analyzed) return;
}
setImageFile(null);
setImageInputKey((current) => current + 1);
await generate(projectId);
```

- [ ] **Step 4: Add compact analysis UI**

In `DesignInputPanel.tsx`, below image file display and before existing generated `workbench.analysis`, render:

```tsx
{analyzingImage && (
  <div className="rounded-md border border-border bg-background p-3 text-xs text-muted-foreground">
    Bild wird analysiert...
  </div>
)}

{workbench?.imageAnalysis && !analyzingImage && (
  <div className="grid gap-2 rounded-md border border-border bg-background p-3 text-xs">
    <div>
      <div className="mb-1 font-medium text-foreground">Bildanalyse</div>
      <p className="line-clamp-3 text-muted-foreground">{workbench.imageAnalysis.summary}</p>
    </div>
    {workbench.imageAnalysis.palette.length > 0 && (
      <div className="flex gap-1.5">
        {workbench.imageAnalysis.palette.slice(0, 6).map((color) => (
          <span
            key={`${color.hex}-${color.role}`}
            title={`${color.role}: ${color.hex}`}
            className="h-5 w-5 rounded border border-border"
            style={{ backgroundColor: color.hex }}
          />
        ))}
      </div>
    )}
    {workbench.imageAnalysis.layoutHierarchy.length > 0 && (
      <div className="text-[11px] leading-4 text-muted-foreground">
        <span className="font-medium text-foreground/80">Layout: </span>
        {workbench.imageAnalysis.layoutHierarchy.slice(0, 3).map((region) => region.name).join(", ")}
      </div>
    )}
  </div>
)}

{workbench?.imageAnalysisError && !analyzingImage && (
  <div className="grid gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive">
    <p>{workbench.imageAnalysisError}</p>
    <Button type="button" variant="outline" size="sm" onClick={() => analyzeImage(projectId)} disabled={actionDisabled}>
      Analyse erneut versuchen
    </Button>
  </div>
)}
```

- [ ] **Step 5: Run frontend lint for changed files**

Run:

```bash
cd frontend && npm run lint -- src/lib/api.ts src/lib/stores/design-workbench-store.ts src/components/wizard/steps/design-workbench/DesignInputPanel.tsx src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx
```

Expected: PASS for changed files.

- [ ] **Step 6: Commit frontend image analysis UI**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/stores/design-workbench-store.ts frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx
git commit -m "feat(design): show image analysis summary"
```

---

### Task 7: Full Verification And Docs Closeout

**Files:**
- Modify: `docs/features/47-agentic-design-workbench.md`

- [ ] **Step 1: Run full backend tests**

Run:

```bash
cd backend && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run frontend build**

Run:

```bash
cd frontend && npm run build
```

Expected: production build succeeds.

- [ ] **Step 3: Run frontend lint**

Run:

```bash
cd frontend && npm run lint
```

Expected: PASS, or only pre-existing baseline issues unrelated to changed files. If lint fails in changed files, fix before continuing.

- [ ] **Step 4: Update Feature 47 implementation note**

In `docs/features/47-agentic-design-workbench.md`, update the next iteration section after implementation:

```markdown
## Bildanalyse-Agent

Die V1-Workbench analysiert hochgeladene Bilder jetzt mit `DesignImageAnalysisAgent`.

- `POST /design/image/analyze` erzeugt strukturierte Bildanalyse.
- `POST /design/generate` holt fehlende Analyse automatisch nach.
- Die UI zeigt kompakte Analyse-Karten und Retry bei Fehlern.
- `DesignVariantAgent` nutzt die Analyse als Designkontext fuer HTML.
```

- [ ] **Step 5: Commit verification docs**

```bash
git add docs/features/47-agentic-design-workbench.md
git commit -m "docs(design): note image analysis implementation"
```

- [ ] **Step 6: Final status check**

Run:

```bash
git status --short --branch
```

Expected: clean worktree on the feature branch.

---

## Self-Review Checklist

- Spec coverage:
  - Dedicated image agent: Task 3.
  - Detailed JSON model: Task 1 and Task 3.
  - Explicit analyze endpoint: Task 4.
  - Generate fallback analysis: Task 4.
  - Generator uses analysis: Task 5.
  - Compact UI and retry-on-error: Task 6.
  - Registry/prompt/model additions: Task 3.
  - Verification: Task 7.
- Type consistency:
  - `DesignImageAnalysis`, `DesignColor`, `DesignTypographySignal`, `DesignLayoutRegion`, `DesignComponentSignal` are introduced in Task 1 and reused consistently.
  - `DesignImageAnalysisInput`, `DesignImageAnalysisResult`, and `InvalidDesignImageAnalysisException` are introduced in Task 3 and reused by service tests in Task 4.
  - `DesignGenerationInput.imageAnalysis` is introduced in Task 5 and passed from service generation.
- No legacy workbench models are reintroduced.
