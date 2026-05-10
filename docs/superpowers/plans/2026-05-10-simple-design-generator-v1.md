# Simple Design Generator V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the over-broad DESIGN workbench with a V1 flow where the user provides a description, an image, or both, and an agent generates one active HTML canvas design that can be regenerated and completed.

**Architecture:** Collapse the visible Workbench contract to one input state plus one active generated design. Backend keeps secure HTML preview validation and wizard progression integration. Frontend replaces screen/variant/snippet controls with a two-zone generator UI and a smaller Zustand store.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, kotlinx.serialization, MockMvc/JUnit 5, Next.js App Router, React 19, TypeScript, Zustand, Tailwind CSS 4.

---

## File Structure

Backend:

- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt`
  - Replace broad screen/input/suggestion model with simplified V1 model.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt`
  - Store one workbench JSON, one optional image input, one active generated HTML, and active export output.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt`
  - Reshape to a simple design generation agent.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt`
  - Replace screen/variant orchestration with input save, generate, preview, complete.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt`
  - Expose V1 endpoints: `GET /workbench`, `PUT /input`, `POST /generate`, `GET /preview`, `POST /complete`.
- Modify tests:
  - `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt`
  - `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt`
  - Existing export/handoff tests only if they fail because exported active design paths changed.

Frontend:

- Modify `frontend/src/lib/api.ts`
  - Replace broad workbench types and helpers with V1 types/helpers.
- Modify `frontend/src/lib/stores/design-workbench-store.ts`
  - Smaller Zustand store: load, saveInput, generate, complete, reset.
- Modify `frontend/src/lib/stores/wizard-store.ts`
  - DESIGN completion chat summary uses `currentDesign`, not screens/variants.
- Replace `frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx`
  - Compose simplified panel and canvas.
- Replace `frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx`
  - Description/image input plus generate/regenerate/complete actions.
- Replace `frontend/src/components/wizard/steps/design-workbench/DesignCanvasPreview.tsx`
  - Preview by active `/design/preview`.
- Delete or stop importing `frontend/src/components/wizard/steps/design-workbench/DesignControlPanel.tsx`
  - Remove visible screen/variant/suggestion controls.

Handoff template:

- Modify `backend/src/main/resources/templates/handoff/agent-template.md.mustache`
  - Keep the user's German text cleanup, but normalize Markdown bullets and fix introduced grammar regressions.

Docs:

- Modify `docs/features/47-agentic-design-workbench-done.md`
  - Note that V1 has been simplified to the single-result generator.

---

### Task 1: Simplify Backend Domain And Storage

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt`

- [ ] **Step 1: Write failing storage tests**

Replace screen/variant-specific storage tests with V1 behavior tests:

```kotlin
@Test
fun `save input replaces description and clears current design`() {
    val first = storage.saveInput("p1", "First", null)
    val generated = GeneratedDesign(
        id = "design-1",
        title = "Landing",
        htmlPath = storage.currentDesignKey("p1"),
        rationale = "Initial",
        createdAt = "now",
    )
    storage.saveGeneratedDesign(
        projectId = "p1",
        analysis = DesignAnalysis("Summary", "Clean", "Because"),
        generated = generated,
        html = "<!doctype html><html><body>First</body></html>".toByteArray(),
    )

    val updated = storage.saveInput("p1", "Second", null)

    assertEquals("Second", updated.description)
    assertNull(updated.currentDesign)
    assertNull(updated.analysis)
}

@Test
fun `save image input records metadata`() {
    val image = storage.saveImageInput(
        projectId = "p1",
        originalName = "reference.png",
        bytes = byteArrayOf(1, 2, 3),
        contentType = "image/png",
    )

    assertEquals("reference.png", image.imageInput?.originalName)
    assertEquals("image/png", image.imageInput?.contentType)
    assertEquals(3, image.imageInput?.sizeBytes)
}

@Test
fun `save generated design writes active html`() {
    storage.saveInput("p1", "Build a pricing page", null)

    val workbench = storage.saveGeneratedDesign(
        projectId = "p1",
        analysis = DesignAnalysis("Pricing page", "Focused SaaS", "Matches prompt"),
        generated = GeneratedDesign(
            id = "design-1",
            title = "Pricing",
            htmlPath = storage.currentDesignKey("p1"),
            rationale = "Focused",
            createdAt = "now",
        ),
        html = "<!doctype html><html><body>Pricing</body></html>".toByteArray(),
    )

    assertEquals("Pricing", workbench.currentDesign?.title)
    assertContentEquals(
        "<!doctype html><html><body>Pricing</body></html>".toByteArray(),
        storage.readCurrentDesign("p1"),
    )
}
```

- [ ] **Step 2: Run storage tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.storage.DesignWorkbenchStorageTest
```

Expected: FAIL because `saveInput`, `saveImageInput`, `saveGeneratedDesign`, `readCurrentDesign`, and simplified domain types do not exist yet.

- [ ] **Step 3: Replace the domain model**

Replace `DesignWorkbench.kt` with:

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class DesignWorkbench(
    val projectId: String,
    val description: String? = null,
    val imageInput: DesignImageInput? = null,
    val analysis: DesignAnalysis? = null,
    val currentDesign: GeneratedDesign? = null,
    val updatedAt: String,
)

@Serializable
data class DesignImageInput(
    val originalName: String,
    val contentRef: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: String,
)

@Serializable
data class DesignAnalysis(
    val summary: String,
    val visualDirection: String,
    val rationale: String,
)

@Serializable
data class GeneratedDesign(
    val id: String,
    val title: String,
    val htmlPath: String,
    val rationale: String,
    val createdAt: String,
)
```

- [ ] **Step 4: Replace storage with V1 methods**

Update `DesignWorkbenchStorage` so it keeps existing object-store prefixing but removes screens/variants as the public storage path:

```kotlin
@Service
class DesignWorkbenchStorage(private val objectStore: ObjectStore) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun prefix(projectId: String) = "projects/$projectId/design/"
    private fun workbenchKey(projectId: String) = "${prefix(projectId)}workbench.json"
    private fun imageInputKey(projectId: String) = "${prefix(projectId)}input/reference-image"
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
        val key = imageInputKey(projectId)
        objectStore.put(key, bytes, contentType)
        val image = DesignImageInput(
            originalName = originalName,
            contentRef = key,
            contentType = contentType,
            sizeBytes = bytes.size.toLong(),
            uploadedAt = Instant.now().toString(),
        )
        return saveInput(projectId = projectId, description = load(projectId).description, imageInput = image)
    }

    fun saveGeneratedDesign(
        projectId: String,
        analysis: DesignAnalysis,
        generated: GeneratedDesign,
        html: ByteArray,
    ): DesignWorkbench {
        val normalized = generated.copy(htmlPath = currentDesignKey(projectId))
        objectStore.put(normalized.htmlPath, html, "text/html")
        return save(load(projectId).copy(analysis = analysis, currentDesign = normalized))
    }

    fun readCurrentDesign(projectId: String): ByteArray =
        objectStore.get(currentDesignKey(projectId)) ?: throw NoSuchElementException("current design not found: $projectId")

    fun writeActiveScreen(projectId: String, html: ByteArray) {
        objectStore.put(activeScreenKey(projectId, "design"), html, "text/html")
    }

    fun listActiveOutputFiles(projectId: String): List<Pair<String, ByteArray>> {
        val workbench = load(projectId)
        if (workbench.currentDesign == null) return emptyList()
        val content = objectStore.get(activeScreenKey(projectId, "design")) ?: return emptyList()
        return listOf("design/screens/design/index.html" to content)
    }
}
```

Keep any imports needed by the real file and remove imports for deleted screen/variant types.

- [ ] **Step 5: Run storage tests to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.storage.DesignWorkbenchStorageTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt
git commit -m "refactor(design): simplify workbench storage"
```

---

### Task 2: Replace Agent And Service Flow

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt`

- [ ] **Step 1: Write failing service tests**

Replace old screen/variant tests with V1 service tests:

```kotlin
@Test
fun `save input rejects missing description and image`() {
    assertFailsWith<InvalidDesignWorkbenchException> {
        service.saveInput("p1", " ", null, null)
    }
}

@Test
fun `save input accepts description only`() {
    val workbench = service.saveInput("p1", "A calm SaaS dashboard", null, null)

    assertEquals("A calm SaaS dashboard", workbench.description)
    assertNull(workbench.imageInput)
}

@Test
fun `save input accepts image only`() {
    val workbench = service.saveInput("p1", null, "dash.png", byteArrayOf(1, 2), "image/png")

    assertEquals("dash.png", workbench.imageInput?.originalName)
    assertNull(workbench.description)
}

@Test
fun `generate creates current design from combined input`() {
    val service = service(
        designVariantAgent = object : DesignVariantAgent(null) {
            override fun generate(input: DesignGenerationInput): DesignGenerationResult =
                DesignGenerationResult(
                    analysis = DesignAnalysis("Dashboard", "Dense operations UI", "Uses both inputs"),
                    title = "Dashboard",
                    html = validHtml("Dashboard"),
                    rationale = "Generated from description and image.",
                )
        },
    )
    service.saveInput("p1", "Build dashboard", "dash.png", byteArrayOf(1), "image/png")

    val workbench = service.generate("p1")

    assertEquals("Dashboard", workbench.analysis?.summary)
    assertEquals("Dashboard", workbench.currentDesign?.title)
}

@Test
fun `regenerate replaces current design`() {
    service.saveInput("p1", "Build dashboard", null, null)

    val first = service.generate("p1").currentDesign?.id
    val second = service.generate("p1").currentDesign?.id

    assertNotNull(first)
    assertNotNull(second)
    assertTrue(first != second)
}

@Test
fun `invalid generated html preserves previous current design`() {
    var invalid = false
    val service = service(
        designVariantAgent = object : DesignVariantAgent(null) {
            override fun generate(input: DesignGenerationInput): DesignGenerationResult =
                DesignGenerationResult(
                    analysis = DesignAnalysis("Summary", "Direction", "Rationale"),
                    title = "Generated",
                    html = if (invalid) """<!doctype html><html><body><img src="https://example.com/x.png"></body></html>""" else validHtml("Safe"),
                    rationale = "Result",
                )
        },
    )
    service.saveInput("p1", "Build page", null, null)
    val previous = service.generate("p1").currentDesign
    invalid = true

    assertFailsWith<InvalidDesignPreviewException> {
        service.generate("p1")
    }

    assertEquals(previous, service.get("p1").currentDesign)
}

@Test
fun `complete rejects missing generated design`() {
    service.saveInput("p1", "Build page", null, null)

    assertFailsWith<InvalidDesignWorkbenchException> {
        service.complete("p1")
    }
}

@Test
fun `complete writes generated design to active output`() {
    service.saveInput("p1", "Build page", null, null)
    service.generate("p1")

    service.complete("p1")

    assertContentEquals(service.readPreview("p1"), objectStore.get(storage.activeScreenKey("p1", "design")))
}
```

Use this helper in the test file:

```kotlin
private fun validHtml(title: String) =
    "<!doctype html><html><head><meta charset=\"utf-8\"></head><body><main><h1>$title</h1></main></body></html>"
```

- [ ] **Step 2: Run service tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.DesignWorkbenchServiceTest
```

Expected: FAIL because the V1 methods and agent interface do not exist.

- [ ] **Step 3: Reshape the agent**

Replace `GeneratedDesignVariant` with V1 agent types in `DesignVariantAgent.kt`:

```kotlin
@Serializable
data class DesignGenerationInput(
    val projectId: String,
    val description: String?,
    val image: DesignImageInput?,
)

@Serializable
data class DesignGenerationResult(
    val analysis: DesignAnalysis,
    val title: String,
    val html: String,
    val rationale: String,
)

@Component
open class DesignVariantAgent(private val koogRunner: KoogAgentRunner? = null) {
    companion object {
        const val AGENT_ID = "design-variant"
    }

    open fun generate(input: DesignGenerationInput): DesignGenerationResult {
        val subject = input.description?.take(120)
            ?: input.image?.originalName?.let { "Reference image $it" }
            ?: "Generated design"
        val escapedSubject = escapeHtml(subject)
        val imageLine = input.image?.originalName?.let { "<p>Reference image: ${escapeHtml(it)}</p>" }.orEmpty()
        return DesignGenerationResult(
            analysis = DesignAnalysis(
                summary = "Generated layout from ${if (input.description != null && input.image != null) "description and image" else if (input.image != null) "image" else "description"}.",
                visualDirection = "Clean product interface with clear hierarchy.",
                rationale = "Fallback generation keeps local development deterministic.",
            ),
            title = "Generated Design",
            html = """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                      body{margin:0;font-family:system-ui;background:#f7f7f4;color:#171717}
                      main{min-height:100vh;display:grid;place-items:center;padding:48px}
                      section{max-width:920px;width:100%;background:white;border:1px solid #ddd;border-radius:8px;padding:40px}
                      h1{font-size:40px;line-height:1.05;margin:0 0 16px}
                      p{font-size:16px;line-height:1.6;color:#4b5563}
                    </style>
                  </head>
                  <body>
                    <main><section><h1>$escapedSubject</h1><p>Generated HTML layout preview.</p>$imageLine</section></main>
                  </body>
                </html>
            """.trimIndent(),
            rationale = "Fallback generated from the current V1 input.",
        )
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
```

- [ ] **Step 4: Replace service methods**

Update `DesignWorkbenchService` to the V1 surface:

```kotlin
class InvalidDesignWorkbenchException(message: String) : RuntimeException(message)

@Service
class DesignWorkbenchService(
    private val storage: DesignWorkbenchStorage,
    private val previewValidator: DesignPreviewValidator,
    private val designVariantAgent: DesignVariantAgent,
) {
    private val maxImageInputBytes = 5 * 1024 * 1024

    fun get(projectId: String): DesignWorkbench = storage.load(projectId)

    fun saveInput(
        projectId: String,
        description: String?,
        originalName: String?,
        bytes: ByteArray?,
        contentType: String?,
    ): DesignWorkbench {
        val trimmed = description?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null && (bytes == null || bytes.isEmpty())) {
            throw InvalidDesignWorkbenchException("Design input requires a description or image.")
        }
        val base = storage.saveInput(projectId, trimmed, null)
        if (bytes == null) return base
        if (bytes.isEmpty()) throw InvalidDesignWorkbenchException("Design image input must not be empty.")
        if (bytes.size > maxImageInputBytes) throw InvalidDesignWorkbenchException("Design image input must not exceed 5 MB.")
        val normalizedContentType = contentType?.trim()?.lowercase().orEmpty()
        if (!normalizedContentType.startsWith("image/")) {
            throw InvalidDesignWorkbenchException("Design image input must use an image content type.")
        }
        return storage.saveImageInput(
            projectId = projectId,
            originalName = originalName?.trim()?.takeIf { it.isNotBlank() } ?: "image-upload",
            bytes = bytes,
            contentType = normalizedContentType,
        )
    }

    fun generate(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        if (workbench.description.isNullOrBlank() && workbench.imageInput == null) {
            throw InvalidDesignWorkbenchException("Design generation requires a description or image.")
        }
        val result = designVariantAgent.generate(
            DesignGenerationInput(projectId = projectId, description = workbench.description, image = workbench.imageInput),
        )
        previewValidator.validate(result.html)
        return storage.saveGeneratedDesign(
            projectId = projectId,
            analysis = result.analysis,
            generated = GeneratedDesign(
                id = UUID.randomUUID().toString(),
                title = result.title.trim().ifBlank { "Generated Design" },
                htmlPath = storage.currentDesignKey(projectId),
                rationale = result.rationale,
                createdAt = Instant.now().toString(),
            ),
            html = result.html.toByteArray(Charsets.UTF_8),
        )
    }

    fun readPreview(projectId: String): ByteArray {
        if (storage.load(projectId).currentDesign == null) {
            throw InvalidDesignWorkbenchException("No generated design exists.")
        }
        return storage.readCurrentDesign(projectId)
    }

    fun complete(projectId: String) {
        val workbench = storage.load(projectId)
        if (workbench.currentDesign == null) {
            throw InvalidDesignWorkbenchException("Generate a design before completing the DESIGN step.")
        }
        storage.writeActiveScreen(projectId, storage.readCurrentDesign(projectId))
    }
}
```

- [ ] **Step 5: Run service tests to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.DesignWorkbenchServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt
git commit -m "refactor(design): generate single canvas design"
```

---

### Task 3: Simplify Controller API

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt`

- [ ] **Step 1: Write failing controller tests**

Use MockMvc multipart PUT for `/input`:

```kotlin
@Test
fun `PUT input rejects empty description and missing image`() {
    advanceToDesign(projectId)

    mockMvc.perform(
        multipart("/api/v1/projects/$projectId/design/input")
            .with { request -> request.method = "PUT"; request }
            .param("description", " "),
    )
        .andExpect(status().isBadRequest())
}

@Test
fun `PUT input accepts description only`() {
    advanceToDesign(projectId)

    mockMvc.perform(
        multipart("/api/v1/projects/$projectId/design/input")
            .with { request -> request.method = "PUT"; request }
            .param("description", "A compact CRM dashboard"),
    )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("A compact CRM dashboard"))
        .andExpect(jsonPath("$.currentDesign").doesNotExist())
}

@Test
fun `PUT input accepts image only`() {
    advanceToDesign(projectId)
    val image = MockMultipartFile("file", "reference.png", "image/png", byteArrayOf(1, 2, 3))

    mockMvc.perform(
        multipart("/api/v1/projects/$projectId/design/input")
            .file(image)
            .with { request -> request.method = "PUT"; request },
    )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageInput.originalName").value("reference.png"))
}

@Test
fun `POST generate creates current design`() {
    advanceToDesign(projectId)
    mockMvc.perform(
        multipart("/api/v1/projects/$projectId/design/input")
            .with { request -> request.method = "PUT"; request }
            .param("description", "A compact CRM dashboard"),
    ).andExpect(status().isOk())

    mockMvc.perform(post("/api/v1/projects/$projectId/design/generate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysis.summary").isString)
        .andExpect(jsonPath("$.currentDesign.title").isString)
}

@Test
fun `GET preview returns active generated html with CSP`() {
    advanceToDesign(projectId)
    mockMvc.perform(
        multipart("/api/v1/projects/$projectId/design/input")
            .with { request -> request.method = "PUT"; request }
            .param("description", "A compact CRM dashboard"),
    ).andExpect(status().isOk())
    mockMvc.perform(post("/api/v1/projects/$projectId/design/generate")).andExpect(status().isOk())

    mockMvc.perform(get("/api/v1/projects/$projectId/design/preview"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(header().string("Content-Security-Policy", containsString("connect-src 'none'")))
}

@Test
fun `POST complete rejects before generate`() {
    advanceToDesign(projectId)
    mockMvc.perform(
        multipart("/api/v1/projects/$projectId/design/input")
            .with { request -> request.method = "PUT"; request }
            .param("description", "A compact CRM dashboard"),
    ).andExpect(status().isOk())

    mockMvc.perform(post("/api/v1/projects/$projectId/design/complete"))
        .andExpect(status().isBadRequest())
}
```

- [ ] **Step 2: Run controller tests to verify RED**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.DesignWorkbenchControllerTest
```

Expected: FAIL because old endpoints still exist and V1 endpoints are missing.

- [ ] **Step 3: Replace controller endpoint surface**

Keep `validateDesignAccess`, `mapInvalidWorkbench`, `badRequest`, CSP helpers, and wizard completion code. Replace old input/screen/variant methods with:

```kotlin
@GetMapping("/workbench")
fun get(@PathVariable projectId: String): DesignWorkbench =
    service.get(projectId)

@PutMapping("/input", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
fun saveInput(
    @PathVariable projectId: String,
    @RequestParam("description", required = false) description: String?,
    @RequestParam("file", required = false) file: MultipartFile?,
): DesignWorkbench {
    validateDesignAccess(projectId)
    return mapInvalidWorkbench {
        service.saveInput(
            projectId = projectId,
            description = description,
            originalName = file?.originalFilename,
            bytes = file?.bytes,
            contentType = file?.contentType,
        )
    }
}

@PostMapping("/generate")
fun generate(@PathVariable projectId: String): DesignWorkbench {
    validateDesignAccess(projectId)
    return mapInvalidWorkbench { service.generate(projectId) }
}

@GetMapping("/preview")
fun preview(@PathVariable projectId: String): ResponseEntity<ByteArray> =
    try {
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_SECURITY_POLICY, previewCsp())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.TEXT_HTML)
            .body(service.readPreview(projectId))
    } catch (ex: InvalidDesignWorkbenchException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message, ex)
    }
```

Keep `POST /complete` but make it call `service.complete(projectId)` and use the existing progression response shape.

- [ ] **Step 4: Remove obsolete endpoint tests**

Delete tests for:

- `/inputs/text`
- `/inputs/image`
- `/inputs/snippet`
- `/inputs/{id}`
- `/analyze`
- `/screens/propose`
- `/screens`
- `/screens/{screenId}/variants`
- `/screens/{screenId}/suggestions`
- `/screens/{screenId}/active-variant`
- `/preview/{variantId}`

Do not delete legacy ZIP controller tests.

- [ ] **Step 5: Run controller tests to verify GREEN**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.DesignWorkbenchControllerTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt
git commit -m "refactor(design): expose simple generator api"
```

---

### Task 4: Simplify Frontend API And Store

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/stores/design-workbench-store.ts`

- [ ] **Step 1: Update API types and helpers**

In `frontend/src/lib/api.ts`, replace broad Workbench types with:

```ts
export interface DesignImageInput {
  originalName: string;
  contentRef: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface DesignAnalysis {
  summary: string;
  visualDirection: string;
  rationale: string;
}

export interface GeneratedDesign {
  id: string;
  title: string;
  htmlPath: string;
  rationale: string;
  createdAt: string;
}

export interface DesignWorkbench {
  projectId: string;
  description?: string | null;
  imageInput?: DesignImageInput | null;
  analysis?: DesignAnalysis | null;
  currentDesign?: GeneratedDesign | null;
  updatedAt: string;
}
```

Replace old helper functions with:

```ts
export async function getDesignWorkbench(projectId: string): Promise<DesignWorkbench> {
  return apiFetch<DesignWorkbench>(`/api/v1/projects/${encodeURIComponent(projectId)}/design/workbench`);
}

export async function saveDesignInput(
  projectId: string,
  description: string,
  file: File | null,
): Promise<DesignWorkbench> {
  const fd = new FormData();
  if (description.trim()) fd.append("description", description.trim());
  if (file) fd.append("file", file);
  const res = await fetch(`${API_BASE}/api/v1/projects/${encodeURIComponent(projectId)}/design/input`, {
    method: "PUT",
    credentials: "include",
    body: fd,
  });
  if (res.status === 401) onUnauthorized?.();
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const message =
      body && typeof body === "object" && "message" in body && typeof (body as { message?: unknown }).message === "string"
        ? (body as { message: string }).message
        : `API error: ${res.status}`;
    throw new ApiError(res.status, body, message);
  }
  return (await res.json()) as DesignWorkbench;
}

export async function generateDesign(projectId: string): Promise<DesignWorkbench> {
  return apiFetch<DesignWorkbench>(`/api/v1/projects/${encodeURIComponent(projectId)}/design/generate`, { method: "POST" });
}

export function designPreviewUrl(projectId: string): string {
  return `${API_BASE}/api/v1/projects/${encodeURIComponent(projectId)}/design/preview`;
}

export async function completeDesignWorkbench(projectId: string): Promise<DesignCompleteResponse> {
  return apiFetch<DesignCompleteResponse>(`/api/v1/projects/${encodeURIComponent(projectId)}/design/complete`, { method: "POST" });
}
```

- [ ] **Step 2: Replace Zustand store**

Use the Zustand async action pattern from Context7. Replace `design-workbench-store.ts` with:

```ts
import { create } from "zustand";
import {
  completeDesignWorkbench,
  generateDesign,
  getDesignWorkbench,
  saveDesignInput,
  type DesignWorkbench,
} from "@/lib/api";

interface DesignWorkbenchState {
  workbench: DesignWorkbench | null;
  loading: boolean;
  working: boolean;
  error: string | null;
  load: (projectId: string) => Promise<void>;
  saveInput: (projectId: string, description: string, file: File | null) => Promise<void>;
  generate: (projectId: string) => Promise<void>;
  complete: (projectId: string) => Promise<void>;
  reset: () => void;
}

export const useDesignWorkbenchStore = create<DesignWorkbenchState>((set) => ({
  workbench: null,
  loading: false,
  working: false,
  error: null,

  load: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const workbench = await getDesignWorkbench(projectId);
      set({ workbench, loading: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to load design workbench", loading: false });
    }
  },

  saveInput: async (projectId, description, file) => {
    set({ working: true, error: null });
    try {
      const workbench = await saveDesignInput(projectId, description, file);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to save design input", working: false });
    }
  },

  generate: async (projectId) => {
    set({ working: true, error: null });
    try {
      const workbench = await generateDesign(projectId);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to generate design", working: false });
    }
  },

  complete: async (projectId) => {
    set({ working: true, error: null });
    try {
      await completeDesignWorkbench(projectId);
      set({ working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to complete design step", working: false });
    }
  },

  reset: () => set({ workbench: null, loading: false, working: false, error: null }),
}));
```

- [ ] **Step 3: Run targeted lint for API/store**

Run:

```bash
cd frontend && npm run lint -- src/lib/api.ts src/lib/stores/design-workbench-store.ts
```

Expected: PASS for changed files.

- [ ] **Step 4: Commit Task 4**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/stores/design-workbench-store.ts
git commit -m "refactor(design): simplify frontend workbench store"
```

---

### Task 5: Replace DESIGN UI With Two-Zone Generator

**Files:**
- Modify: `frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx`
- Modify: `frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx`
- Modify: `frontend/src/components/wizard/steps/design-workbench/DesignCanvasPreview.tsx`
- Delete or stop importing: `frontend/src/components/wizard/steps/design-workbench/DesignControlPanel.tsx`

- [ ] **Step 1: Replace top-level form composition**

`DesignWorkbenchForm.tsx` should calculate only `hasDesign` from `workbench.currentDesign`:

```tsx
const hasDesign = Boolean(workbench?.currentDesign);

async function handleComplete() {
  if (isBlocked || !hasDesign) return;
  await completeStep(projectId, "DESIGN");
}
```

Render a responsive two-zone grid:

```tsx
<div className="grid min-h-0 min-w-0 flex-1 grid-cols-[minmax(280px,360px)_minmax(0,1fr)] gap-3 max-lg:grid-cols-1">
  <DesignInputPanel
    projectId={projectId}
    workbench={workbench}
    working={working}
    completing={completing}
    blocked={isBlocked}
    onComplete={handleComplete}
  />
  <DesignCanvasPreview projectId={projectId} workbench={workbench} working={working} />
</div>
```

- [ ] **Step 2: Replace input panel**

`DesignInputPanel.tsx` should expose description, image upload, generate/regenerate, and complete:

```tsx
const saveInput = useDesignWorkbenchStore((s) => s.saveInput);
const generate = useDesignWorkbenchStore((s) => s.generate);
const [description, setDescription] = useState(workbench?.description ?? "");
const [imageFile, setImageFile] = useState<File | null>(null);
const canSubmitInput = Boolean(description.trim() || imageFile || workbench?.imageInput);
const hasDesign = Boolean(workbench?.currentDesign);

async function handleGenerate() {
  if (!canSubmitInput) return;
  await saveInput(projectId, description, imageFile);
  if (useDesignWorkbenchStore.getState().error) return;
  await generate(projectId);
  if (!useDesignWorkbenchStore.getState().error) setImageFile(null);
}
```

The panel must include:

- `Textarea` with label `Designbeschreibung`
- `Input type="file" accept="image/*"`
- `Button` text `Design generieren` before `currentDesign`
- `Button` text `Neu generieren` after `currentDesign`
- `Button` text `Design übernehmen`, disabled when `!currentDesign || blocked || working || completing`
- analysis summary:

```tsx
{workbench?.analysis && (
  <div className="rounded-md border border-border bg-background p-3 text-xs">
    <p className="font-medium text-foreground">{workbench.analysis.summary}</p>
    <p className="mt-1 text-muted-foreground">{workbench.analysis.visualDirection}</p>
  </div>
)}
```

- [ ] **Step 3: Replace canvas preview**

`DesignCanvasPreview.tsx` should use `designPreviewUrl(projectId)` and no longer accept screen/variant:

```tsx
interface DesignCanvasPreviewProps {
  projectId: string;
  workbench: DesignWorkbench | null;
  working: boolean;
}

const previewSrc = workbench?.currentDesign
  ? `${designPreviewUrl(projectId)}?v=${encodeURIComponent(workbench.currentDesign.id)}`
  : null;
```

Header subtitle should show:

```tsx
{workbench?.currentDesign ? workbench.currentDesign.title : "Noch kein Design generiert"}
```

Empty state text:

```tsx
Beschreibung oder Bild eingeben und Design generieren.
```

- [ ] **Step 4: Remove obsolete control panel**

Delete `DesignControlPanel.tsx` if no imports remain:

```bash
rg "DesignControlPanel" frontend/src
```

Expected: no matches after removing the import from `DesignWorkbenchForm.tsx`.

- [ ] **Step 5: Run targeted lint for UI**

Run:

```bash
cd frontend && npm run lint -- src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx src/components/wizard/steps/design-workbench/DesignInputPanel.tsx src/components/wizard/steps/design-workbench/DesignCanvasPreview.tsx
```

Expected: PASS for changed files.

- [ ] **Step 6: Commit Task 5**

```bash
git add frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx \
  frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx \
  frontend/src/components/wizard/steps/design-workbench/DesignCanvasPreview.tsx
git add -u frontend/src/components/wizard/steps/design-workbench/DesignControlPanel.tsx
git commit -m "refactor(design): simplify generator ui"
```

---

### Task 6: Update Wizard Completion, Export, Docs, And Handoff Template

**Files:**
- Modify: `frontend/src/lib/stores/wizard-store.ts`
- Modify if failing: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ProjectPackageAssembler.kt`
- Modify if failing: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`
- Modify if failing: `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt`
- Modify: `backend/src/main/resources/templates/handoff/agent-template.md.mustache`
- Modify: `docs/features/47-agentic-design-workbench-done.md`

- [ ] **Step 1: Update DESIGN chat summary**

In `wizard-store.ts`, replace the DESIGN completion summary block with:

```ts
const workbench = useDesignWorkbenchStore.getState().workbench;
const chatMessage = [
  "**Design**",
  "",
  `Beschreibung: ${workbench?.description ? "vorhanden" : "nicht vorhanden"}`,
  `Bild: ${workbench?.imageInput?.originalName ?? "nicht vorhanden"}`,
  `Aktives Design: ${workbench?.currentDesign?.title ?? "nicht vorhanden"}`,
  "",
  workbench?.analysis?.summary ? `Analyse: ${workbench.analysis.summary}` : "Analyse: nicht vorhanden",
].join("\n");
```

Keep the existing `completeDesignWorkbench`, progression refresh, export trigger, and chat message handling.

- [ ] **Step 2: Check export/handoff tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.ExportControllerTest --tests com.agentwork.productspecagent.api.HandoffControllerTest
```

Expected: PASS or focused failures showing old screen/variant assumptions.

If failures mention `listActiveOutputFiles`, update assertions to expect:

```text
design/screens/design/index.html
```

- [ ] **Step 3: Normalize handoff template changes**

The current worktree already contains user-requested German text cleanup in `backend/src/main/resources/templates/handoff/agent-template.md.mustache`. Keep the umlaut cleanup, but fix the introduced Markdown/grammar regressions:

- Use `-` bullets, not en dash bullets.
- `Wenn ein spezialisiertes Dokument vorhanden ist, nutze es ...`
- `Gebundelte Agent-Assets liegen in .asset-bundles.`
- `Wenn Living Sync eingerichtet ist, melde relevante Ereignisse:`
- `Verwenden für Living-Sync-Berichte ...`
- Ensure the file ends with a newline.

Expected corrected snippets:

```md
- `docs/features/`: Feature-Dokumente, Done-Dateien und Feature-Übersicht.

Wenn ein spezialisiertes Dokument vorhanden ist, nutze es vor allgemeinen Annahmen.

Gebundelte Agent-Assets liegen in `.asset-bundles`.

Wenn Living Sync eingerichtet ist, melde relevante Ereignisse:
```

- [ ] **Step 4: Update done notes**

Append a concise note to `docs/features/47-agentic-design-workbench-done.md`:

```md
## 2026-05-10 Simplified V1 Pivot

The DESIGN step was reduced to the Simple Design Generator V1:

- Description, image, or both as the only visible inputs.
- One active generated HTML canvas design.
- Regenerate replaces the active design.
- Screens, variants, snippets, classifications, and suggestions are no longer visible in the V1 UI.
```

- [ ] **Step 5: Run frontend lint for wizard store**

Run:

```bash
cd frontend && npm run lint -- src/lib/stores/wizard-store.ts
```

Expected: PASS for changed file.

- [ ] **Step 6: Commit Task 6**

```bash
git add frontend/src/lib/stores/wizard-store.ts \
  backend/src/main/resources/templates/handoff/agent-template.md.mustache \
  docs/features/47-agentic-design-workbench-done.md
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/ProjectPackageAssembler.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt 2>/dev/null || true
git commit -m "chore(design): align completion and handoff docs"
```

---

### Task 7: Full Verification

**Files:**
- Verify all changed backend/frontend files.

- [ ] **Step 1: Run backend tests**

Run:

```bash
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run changed-file frontend lint**

Run:

```bash
cd frontend && npm run lint -- src/lib/api.ts src/lib/stores/design-workbench-store.ts src/lib/stores/wizard-store.ts src/components/wizard/WizardForm.tsx 'src/app/projects/[id]/page.tsx' src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx src/components/wizard/steps/design-workbench/DesignInputPanel.tsx src/components/wizard/steps/design-workbench/DesignCanvasPreview.tsx
```

Expected: PASS for changed files.

- [ ] **Step 3: Run frontend build**

Run:

```bash
cd frontend && npm run build
```

Expected: Compiles successfully.

- [ ] **Step 4: Check worktree and summarize**

Run:

```bash
git status --short --branch
git log -5 --oneline
```

Expected: no unintended uncommitted changes. If unrelated local changes remain, name them explicitly.

---

## Self-Review

- Spec coverage:
  - Description-only, image-only, and combined input: Task 2 and Task 3.
  - Single active generated design: Task 1 and Task 2.
  - Regenerate replaces current design: Task 2.
  - Preview security retained: Task 2 and Task 3 reuse `DesignPreviewValidator` and CSP.
  - Simplified frontend: Task 4 and Task 5.
  - Complete only after valid design: Task 2, Task 3, Task 5.
  - Handoff template user change included: Task 6.
- Placeholder scan: no open implementation placeholders are intentionally left; optional export/handoff edits are guarded by a concrete test command and expected failure mode.
- Type consistency:
  - Kotlin uses `DesignWorkbench`, `DesignImageInput`, `DesignAnalysis`, `GeneratedDesign`.
  - Agent uses `DesignGenerationInput` and `DesignGenerationResult`.
  - TypeScript mirrors the simplified workbench contract.
