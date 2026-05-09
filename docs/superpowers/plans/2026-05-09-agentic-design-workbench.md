# Agentic Design Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the ZIP-based DESIGN step with a required agentic HTML/CSS design workbench for frontend-capable project categories.

**Architecture:** Add a new `DesignWorkbench` domain beside the existing `DesignBundle` code, then move the active DESIGN path to `DesignWorkbenchController`, `DesignWorkbenchService`, and a new frontend workbench UI. The first implementation uses three agent boundaries: reference analysis, screen proposal, and variant generation; completion writes `spec/design.md` and active screen HTML files.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, kotlinx.serialization, Project `ObjectStore`, Koog agent runner, Next.js App Router, React 19, Zustand, Tailwind/shadcn UI.

---

## File Structure

Backend files to create:

- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt`: serializable domain models for inputs, analyses, screens, variants, and suggestions.
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt`: object-store persistence for workbench state, inputs, variants, and active outputs.
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignPreviewValidator.kt`: security validation for generated preview HTML.
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt`: orchestration, input operations, screen operations, variant operations, and completion output writing.
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/ReferenceAnalysisAgent.kt`: classifies design inputs.
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/ScreenProposalAgent.kt`: proposes screens from wizard context and analysis.
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt`: generates and revises self-contained HTML/CSS variants.
- `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt`: REST endpoints for the workbench.

Backend files to modify:

- `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignBundleController.kt`: prevent the old `/design/complete` path from being the active wizard completion route.
- `backend/src/main/kotlin/com/agentwork/productspecagent/export/ProjectPackageAssembler.kt`: keep active `design/screens/{screen}/index.html` and `design/assets/{asset}` files in project and handoff exports.
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardMarkdown.kt`: ensure DESIGN summary renders from workbench completion fields.

Frontend files to create:

- `frontend/src/lib/stores/design-workbench-store.ts`: Zustand store for workbench state and actions.
- `frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx`: top-level DESIGN step replacement.
- `frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx`: text, image, and snippet inputs plus analysis cards.
- `frontend/src/components/wizard/steps/design-workbench/DesignCanvasPreview.tsx`: sandboxed iframe preview.
- `frontend/src/components/wizard/steps/design-workbench/DesignControlPanel.tsx`: generation controls, suggestions, variants, and completion CTA.

Frontend files to modify:

- `frontend/src/lib/api.ts`: add workbench API types and client functions.
- `frontend/src/components/wizard/WizardForm.tsx`: map `DESIGN` to `DesignWorkbenchForm` and keep DESIGN owning its own CTA.
- `frontend/src/app/projects/[id]/page.tsx`: load/reset the new design workbench store instead of the old bundle store.

Tests to create or update:

- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignPreviewValidatorTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportControllerTest.kt`

---

### Task 1: Backend Domain and Preview Validator

**Files:**

- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignPreviewValidator.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignPreviewValidatorTest.kt`

- [ ] **Step 1: Write failing validator tests**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignPreviewValidatorTest.kt`:

```kotlin
package com.agentwork.productspecagent.service

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DesignPreviewValidatorTest {
    private val validator = DesignPreviewValidator()

    @Test
    fun `accepts static html css and local inline interaction`() {
        validator.validate(
            """
            <!doctype html>
            <html>
              <head>
                <style>.tab{display:none}.tab.active{display:block}</style>
              </head>
              <body>
                <button onclick="document.querySelector('#a')?.classList.toggle('active')">Toggle</button>
                <section id="a" class="tab active">Hello</section>
              </body>
            </html>
            """.trimIndent(),
        )
    }

    @Test
    fun `rejects external urls`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<img src="https://example.com/a.png">""")
        }
    }

    @Test
    fun `rejects network apis`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>fetch('/api/v1/projects')</script>""")
        }
    }

    @Test
    fun `rejects browser storage and parent access`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>window.parent.postMessage(localStorage.token, '*')</script>""")
        }
    }

    @Test
    fun `rejects external scripts and form actions`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<form action="/api/v1/projects"><script src="/x.js"></script></form>""")
        }
    }
}
```

- [ ] **Step 2: Run validator test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests "*.DesignPreviewValidatorTest"
```

Expected: compilation fails because `DesignPreviewValidator` does not exist.

- [ ] **Step 3: Add domain models**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt`:

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class DesignWorkbench(
    val projectId: String,
    val inputs: List<DesignInput> = emptyList(),
    val screens: List<DesignScreen> = emptyList(),
    val suggestions: List<DesignSuggestion> = emptyList(),
    val updatedAt: String,
)

@Serializable
data class DesignInput(
    val id: String,
    val kind: DesignInputKind,
    val originalName: String? = null,
    val userLabel: String? = null,
    val classification: DesignInputClassification? = null,
    val contentRef: String,
    val createdAt: String,
)

@Serializable
enum class DesignInputKind {
    TEXT,
    IMAGE,
    HTML_CSS_SNIPPET,
}

@Serializable
data class DesignInputClassification(
    val category: DesignInputCategory,
    val summary: String,
    val suggestedUse: String,
    val confidence: Double,
)

@Serializable
enum class DesignInputCategory {
    REFERENCE_IMAGE,
    ASSET_IMAGE,
    HTML_CSS_REFERENCE,
    UNCLEAR,
}

@Serializable
data class DesignScreen(
    val id: String,
    val name: String,
    val purpose: String,
    val variants: List<DesignVariant> = emptyList(),
    val activeVariantId: String? = null,
)

@Serializable
data class DesignVariant(
    val id: String,
    val screenId: String,
    val version: Int,
    val title: String,
    val htmlPath: String,
    val status: DesignVariantStatus,
    val rationale: String,
    val createdAt: String,
)

@Serializable
enum class DesignVariantStatus {
    DRAFT,
    VALID,
    INVALID,
}

@Serializable
data class DesignSuggestion(
    val id: String,
    val screenId: String,
    val title: String,
    val description: String,
    val createdAt: String,
)
```

- [ ] **Step 4: Add validator implementation**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignPreviewValidator.kt`:

```kotlin
package com.agentwork.productspecagent.service

import org.springframework.stereotype.Component

class InvalidDesignPreviewException(reason: String) : RuntimeException("Invalid design preview: $reason")

@Component
class DesignPreviewValidator {
    private val forbiddenPatterns = listOf(
        Regex("""(?i)\bhttps?://""") to "external URLs are not allowed",
        Regex("""(?i)\b(src|href)\s*=\s*["']\s*//""") to "protocol-relative URLs are not allowed",
        Regex("""(?i)<script[^>]+\bsrc\s*=""") to "external scripts are not allowed",
        Regex("""(?i)\bfetch\s*\(""") to "fetch is not allowed",
        Regex("""(?i)\bXMLHttpRequest\b""") to "XMLHttpRequest is not allowed",
        Regex("""(?i)\bWebSocket\b""") to "WebSocket is not allowed",
        Regex("""(?i)\bEventSource\b""") to "EventSource is not allowed",
        Regex("""(?i)\blocalStorage\b""") to "localStorage is not allowed",
        Regex("""(?i)\bsessionStorage\b""") to "sessionStorage is not allowed",
        Regex("""(?i)\bdocument\.cookie\b""") to "cookie access is not allowed",
        Regex("""(?i)\bwindow\.parent\b""") to "parent window access is not allowed",
        Regex("""(?i)\bpostMessage\s*\(""") to "postMessage is not allowed",
        Regex("""(?i)<form[^>]+\baction\s*=""") to "form actions are not allowed",
    )

    fun validate(html: String) {
        if (html.isBlank()) {
            throw InvalidDesignPreviewException("html is blank")
        }
        if (html.length > 500_000) {
            throw InvalidDesignPreviewException("html exceeds 500 KB")
        }
        val hit = forbiddenPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(html) }
        if (hit != null) {
            throw InvalidDesignPreviewException(hit.second)
        }
    }
}
```

- [ ] **Step 5: Run validator tests**

Run:

```bash
cd backend
./gradlew test --tests "*.DesignPreviewValidatorTest"
```

Expected: all `DesignPreviewValidatorTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/DesignWorkbench.kt backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignPreviewValidator.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignPreviewValidatorTest.kt
git commit -m "feat(design): add workbench domain and preview validation"
```

---

### Task 2: Workbench Storage

**Files:**

- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt`

- [ ] **Step 1: Write failing storage tests**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.DesignInputKind
import com.agentwork.productspecagent.domain.DesignScreen
import com.agentwork.productspecagent.domain.DesignVariant
import com.agentwork.productspecagent.domain.DesignVariantStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesignWorkbenchStorageTest {
    private val objectStore = InMemoryObjectStore()
    private val storage = DesignWorkbenchStorage(objectStore)

    @Test
    fun `load initializes empty workbench`() {
        val workbench = storage.load("p1")

        assertEquals("p1", workbench.projectId)
        assertTrue(workbench.inputs.isEmpty())
        assertTrue(workbench.screens.isEmpty())
    }

    @Test
    fun `stores text input and persists workbench`() {
        val input = storage.addTextInput("p1", "Make a compact SaaS dashboard")

        val loaded = storage.load("p1")
        assertEquals(DesignInputKind.TEXT, input.kind)
        assertEquals(input.id, loaded.inputs.single().id)
        assertEquals("Make a compact SaaS dashboard", objectStore.get(input.contentRef)!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `stores variant html and sets active variant`() {
        val screen = DesignScreen(id = "landing", name = "Landing", purpose = "Explain value")
        storage.saveScreens("p1", listOf(screen))
        val variant = DesignVariant(
            id = "v1",
            screenId = "landing",
            version = 1,
            title = "Initial",
            htmlPath = "projects/p1/design/variants/landing/v1.html",
            status = DesignVariantStatus.VALID,
            rationale = "Initial variant",
            createdAt = "2026-05-09T00:00:00Z",
        )

        storage.saveVariant("p1", "landing", variant, "<html><body>Landing</body></html>".toByteArray())
        storage.setActiveVariant("p1", "landing", "v1")

        val loaded = storage.load("p1")
        assertEquals("v1", loaded.screens.single().activeVariantId)
        assertNotNull(objectStore.get("projects/p1/design/variants/landing/v1.html"))
    }
}
```

- [ ] **Step 2: Run storage test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests "*.DesignWorkbenchStorageTest"
```

Expected: compilation fails because `DesignWorkbenchStorage` does not exist.

- [ ] **Step 3: Implement storage**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt`:

```kotlin
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
        save(load(projectId).copy(inputs = load(projectId).inputs + input))
        return input
    }

    fun addBinaryInput(projectId: String, kind: DesignInputKind, originalName: String, bytes: ByteArray, contentType: String): DesignInput {
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
        save(load(projectId).copy(inputs = load(projectId).inputs + input))
        return input
    }

    fun updateInputClassification(projectId: String, inputId: String, classification: DesignInputClassification, userLabel: String?): DesignWorkbench {
        val workbench = load(projectId)
        return save(workbench.copy(inputs = workbench.inputs.map {
            if (it.id == inputId) it.copy(classification = classification, userLabel = userLabel ?: it.userLabel) else it
        }))
    }

    fun saveScreens(projectId: String, screens: List<DesignScreen>): DesignWorkbench =
        save(load(projectId).copy(screens = screens))

    fun saveVariant(projectId: String, screenId: String, variant: DesignVariant, html: ByteArray): DesignWorkbench {
        objectStore.put(variant.htmlPath, html, "text/html")
        val workbench = load(projectId)
        return save(workbench.copy(screens = workbench.screens.map { screen ->
            if (screen.id == screenId) screen.copy(variants = screen.variants.filterNot { it.id == variant.id } + variant) else screen
        }))
    }

    fun setActiveVariant(projectId: String, screenId: String, variantId: String): DesignWorkbench {
        val workbench = load(projectId)
        return save(workbench.copy(screens = workbench.screens.map { screen ->
            if (screen.id == screenId) screen.copy(activeVariantId = variantId) else screen
        }))
    }

    fun readByKey(key: String): ByteArray =
        objectStore.get(key) ?: throw NoSuchElementException("design object not found: $key")

    fun writeActiveScreen(projectId: String, screenSlug: String, html: ByteArray) {
        objectStore.put(activeScreenKey(projectId, screenSlug), html, "text/html")
    }
}
```

- [ ] **Step 4: Run storage tests**

Run:

```bash
cd backend
./gradlew test --tests "*.DesignWorkbenchStorageTest"
```

Expected: all `DesignWorkbenchStorageTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt
git commit -m "feat(design): persist design workbench state"
```

---

### Task 3: Agents and Service Orchestration

**Files:**

- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/ReferenceAnalysisAgent.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/ScreenProposalAgent.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt`

- [ ] **Step 1: Write failing service tests with fake agents**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.agent.GeneratedDesignVariant
import com.agentwork.productspecagent.agent.ReferenceAnalysisAgent
import com.agentwork.productspecagent.agent.ScreenProposalAgent
import com.agentwork.productspecagent.agent.ScreenProposal
import com.agentwork.productspecagent.domain.DesignInputCategory
import com.agentwork.productspecagent.domain.DesignInputClassification
import com.agentwork.productspecagent.domain.DesignInputKind
import com.agentwork.productspecagent.domain.DesignVariantStatus
import com.agentwork.productspecagent.storage.DesignWorkbenchStorage
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesignWorkbenchServiceTest {
    private val objectStore = InMemoryObjectStore()
    private val storage = DesignWorkbenchStorage(objectStore)
    private val service = DesignWorkbenchService(
        storage = storage,
        previewValidator = DesignPreviewValidator(),
        referenceAnalysisAgent = object : ReferenceAnalysisAgent(null) {
            override fun analyze(projectId: String) = listOf(
                DesignInputClassification(
                    category = DesignInputCategory.REFERENCE_IMAGE,
                    summary = "Dashboard reference",
                    suggestedUse = "Use density and navigation pattern",
                    confidence = 0.8,
                )
            )
        },
        screenProposalAgent = object : ScreenProposalAgent(null) {
            override fun propose(projectId: String) = listOf(
                ScreenProposal(id = "landing", name = "Landing", purpose = "Explain value"),
            )
        },
        designVariantAgent = object : DesignVariantAgent(null) {
            override fun generate(projectId: String, screenId: String, prompt: String?) =
                GeneratedDesignVariant(
                    title = "Initial",
                    html = "<!doctype html><html><body><main>Landing</main></body></html>",
                    rationale = "Initial landing",
                )
        },
    )

    @Test
    fun `analyze assigns visible input classification`() {
        storage.addBinaryInput("p1", DesignInputKind.IMAGE, "dash.png", "x".toByteArray(), "image/png")

        val workbench = service.analyzeInputs("p1")

        assertEquals(DesignInputCategory.REFERENCE_IMAGE, workbench.inputs.single().classification?.category)
    }

    @Test
    fun `propose screens writes curated starting screens`() {
        val workbench = service.proposeScreens("p1")

        assertEquals("Landing", workbench.screens.single().name)
    }

    @Test
    fun `generate variant validates and stores variant`() {
        service.proposeScreens("p1")

        val workbench = service.generateVariant("p1", "landing", "compact SaaS")

        val variant = workbench.screens.single().variants.single()
        assertEquals(DesignVariantStatus.VALID, variant.status)
    }

    @Test
    fun `complete rejects missing active screen`() {
        assertFailsWith<InvalidDesignWorkbenchException> {
            service.complete("p1")
        }
    }
}
```

- [ ] **Step 2: Run service test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests "*.DesignWorkbenchServiceTest"
```

Expected: compilation fails because agents and service do not exist.

- [ ] **Step 3: Add agent classes with overridable methods**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/agent/ReferenceAnalysisAgent.kt`:

```kotlin
package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignInputCategory
import com.agentwork.productspecagent.domain.DesignInputClassification
import org.springframework.stereotype.Component

@Component
open class ReferenceAnalysisAgent(private val koogRunner: KoogAgentRunner? = null) {
    companion object {
        const val AGENT_ID = "design-reference-analysis"
    }

    open fun analyze(projectId: String): List<DesignInputClassification> =
        listOf(
            DesignInputClassification(
                category = DesignInputCategory.REFERENCE_IMAGE,
                summary = "Reference material is available for design generation.",
                suggestedUse = "Use as visual reference.",
                confidence = 0.4,
            )
        )
}
```

Create `backend/src/main/kotlin/com/agentwork/productspecagent/agent/ScreenProposalAgent.kt`:

```kotlin
package com.agentwork.productspecagent.agent

import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
data class ScreenProposal(
    val id: String,
    val name: String,
    val purpose: String,
)

@Component
open class ScreenProposalAgent(private val koogRunner: KoogAgentRunner? = null) {
    companion object {
        const val AGENT_ID = "design-screen-proposal"
    }

    open fun propose(projectId: String): List<ScreenProposal> =
        listOf(
            ScreenProposal(id = "landing", name = "Landing", purpose = "Explain product value and first action."),
            ScreenProposal(id = "dashboard", name = "Dashboard", purpose = "Show the primary logged-in workspace."),
        )
}
```

Create `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt`:

```kotlin
package com.agentwork.productspecagent.agent

import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
data class GeneratedDesignVariant(
    val title: String,
    val html: String,
    val rationale: String,
)

@Component
open class DesignVariantAgent(private val koogRunner: KoogAgentRunner? = null) {
    companion object {
        const val AGENT_ID = "design-variant"
    }

    open fun generate(projectId: String, screenId: String, prompt: String?): GeneratedDesignVariant =
        GeneratedDesignVariant(
            title = "Initial",
            html = """
                <!doctype html>
                <html>
                  <head><meta charset="utf-8"><style>body{font-family:system-ui;margin:32px}</style></head>
                  <body><main><h1>${screenId.replaceFirstChar { it.uppercase() }}</h1><p>${prompt ?: "Generated design variant"}</p></main></body>
                </html>
            """.trimIndent(),
            rationale = "Fallback generated from screen name and prompt.",
        )
}
```

- [ ] **Step 4: Add service implementation**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.agent.DesignVariantAgent
import com.agentwork.productspecagent.agent.ReferenceAnalysisAgent
import com.agentwork.productspecagent.agent.ScreenProposalAgent
import com.agentwork.productspecagent.domain.DesignScreen
import com.agentwork.productspecagent.domain.DesignVariant
import com.agentwork.productspecagent.domain.DesignVariantStatus
import com.agentwork.productspecagent.domain.DesignWorkbench
import com.agentwork.productspecagent.storage.DesignWorkbenchStorage
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

class InvalidDesignWorkbenchException(message: String) : RuntimeException(message)

@Service
class DesignWorkbenchService(
    private val storage: DesignWorkbenchStorage,
    private val previewValidator: DesignPreviewValidator,
    private val referenceAnalysisAgent: ReferenceAnalysisAgent,
    private val screenProposalAgent: ScreenProposalAgent,
    private val designVariantAgent: DesignVariantAgent,
) {
    fun get(projectId: String): DesignWorkbench = storage.load(projectId)

    fun addTextInput(projectId: String, text: String): DesignWorkbench {
        if (text.isBlank()) {
            throw InvalidDesignWorkbenchException("Design input text must not be blank.")
        }
        storage.addTextInput(projectId, text)
        return storage.load(projectId)
    }

    fun analyzeInputs(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        val analyses = referenceAnalysisAgent.analyze(projectId)
        var updated = workbench
        workbench.inputs.forEachIndexed { index, input ->
            val analysis = analyses.getOrNull(index) ?: analyses.lastOrNull() ?: return@forEachIndexed
            updated = storage.updateInputClassification(projectId, input.id, analysis, input.userLabel)
        }
        return updated
    }

    fun proposeScreens(projectId: String): DesignWorkbench {
        val proposals = screenProposalAgent.propose(projectId)
        val screens = proposals.map {
            DesignScreen(id = it.id, name = it.name, purpose = it.purpose)
        }
        return storage.saveScreens(projectId, screens)
    }

    fun generateVariant(projectId: String, screenId: String, prompt: String?): DesignWorkbench {
        val generated = designVariantAgent.generate(projectId, screenId, prompt)
        previewValidator.validate(generated.html)
        val workbench = storage.load(projectId)
        val screen = workbench.screens.firstOrNull { it.id == screenId }
            ?: throw InvalidDesignWorkbenchException("Screen not found: $screenId")
        val variantId = UUID.randomUUID().toString()
        val version = screen.variants.size + 1
        val path = storage.variantKey(projectId, screenId, variantId)
        val variant = DesignVariant(
            id = variantId,
            screenId = screenId,
            version = version,
            title = generated.title,
            htmlPath = path,
            status = DesignVariantStatus.VALID,
            rationale = generated.rationale,
            createdAt = Instant.now().toString(),
        )
        return storage.saveVariant(projectId, screenId, variant, generated.html.toByteArray())
    }

    fun setActiveVariant(projectId: String, screenId: String, variantId: String): DesignWorkbench =
        storage.setActiveVariant(projectId, screenId, variantId)

    fun readVariant(projectId: String, htmlPath: String): ByteArray = storage.readByKey(htmlPath)

    fun complete(projectId: String): DesignWorkbench {
        val workbench = storage.load(projectId)
        val activeScreens = workbench.screens.filter { screen ->
            screen.activeVariantId != null && screen.variants.any { it.id == screen.activeVariantId && it.status == DesignVariantStatus.VALID }
        }
        if (activeScreens.isEmpty()) {
            throw InvalidDesignWorkbenchException("At least one active valid design screen is required.")
        }
        activeScreens.forEach { screen ->
            val variant = screen.variants.first { it.id == screen.activeVariantId }
            storage.writeActiveScreen(projectId, screen.name.slug(), storage.readByKey(variant.htmlPath))
        }
        return workbench
    }
}
```

- [ ] **Step 5: Run service tests**

Run:

```bash
cd backend
./gradlew test --tests "*.DesignWorkbenchServiceTest"
```

Expected: all `DesignWorkbenchServiceTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/ReferenceAnalysisAgent.kt backend/src/main/kotlin/com/agentwork/productspecagent/agent/ScreenProposalAgent.kt backend/src/main/kotlin/com/agentwork/productspecagent/agent/DesignVariantAgent.kt backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchServiceTest.kt
git commit -m "feat(design): orchestrate workbench agents"
```

---

### Task 4: Workbench REST API and DESIGN Completion

**Files:**

- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignBundleController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt`

- [ ] **Step 1: Write failing controller tests**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt` using the same Spring test style as `DesignBundleControllerTest`. Include these tests:

```kotlin
@Test
fun `GET workbench returns empty workbench`() {
    mockMvc.perform(get("/api/v1/projects/$projectId/design/workbench"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectId))
        .andExpect(jsonPath("$.inputs").isArray)
}

@Test
fun `POST complete rejects workbench without active variant`() {
    mockMvc.perform(post("/api/v1/projects/$projectId/design/complete").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
}

@Test
fun `GET old design endpoint no longer exposes zip upload as primary state`() {
    mockMvc.perform(get("/api/v1/projects/$projectId/design"))
        .andExpect(status().isNotFound())
}
```

Use the project creation helper already present in controller tests. If that helper is private, duplicate only the minimal helper in this test file.

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```bash
cd backend
./gradlew test --tests "*.DesignWorkbenchControllerTest"
```

Expected: compilation fails because `DesignWorkbenchController` does not exist.

- [ ] **Step 3: Add controller**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt`:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.DesignWorkbench
import com.agentwork.productspecagent.service.DesignWorkbenchService
import com.agentwork.productspecagent.service.InvalidDesignPreviewException
import com.agentwork.productspecagent.service.InvalidDesignWorkbenchException
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.WizardService
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardStepData
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/api/v1/projects/{projectId}/design")
class DesignWorkbenchController(
    private val service: DesignWorkbenchService,
    private val projectService: ProjectService,
    private val wizardService: WizardService,
) {
    data class TextInputRequest(val text: String)
    data class GenerateVariantRequest(val prompt: String? = null)
    data class ActiveVariantRequest(val variantId: String)
    data class CompleteResponse(val message: String, val nextStep: String?)

    @GetMapping("/workbench")
    fun get(@PathVariable projectId: String): DesignWorkbench = service.get(projectId)

    @PostMapping("/inputs/text")
    fun addText(@PathVariable projectId: String, @RequestBody body: TextInputRequest): DesignWorkbench {
        if (body.text.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank")
        return try {
            service.addTextInput(projectId, body.text)
        } catch (e: InvalidDesignWorkbenchException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }

    @PostMapping("/analyze")
    fun analyze(@PathVariable projectId: String): DesignWorkbench = service.analyzeInputs(projectId)

    @PostMapping("/screens/propose")
    fun proposeScreens(@PathVariable projectId: String): DesignWorkbench = service.proposeScreens(projectId)

    @PostMapping("/screens/{screenId}/variants")
    fun generateVariant(
        @PathVariable projectId: String,
        @PathVariable screenId: String,
        @RequestBody body: GenerateVariantRequest,
    ): DesignWorkbench = try {
        service.generateVariant(projectId, screenId, body.prompt)
    } catch (e: InvalidDesignPreviewException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: InvalidDesignWorkbenchException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @PatchMapping("/screens/{screenId}/active-variant")
    fun setActiveVariant(
        @PathVariable projectId: String,
        @PathVariable screenId: String,
        @RequestBody body: ActiveVariantRequest,
    ): DesignWorkbench = service.setActiveVariant(projectId, screenId, body.variantId)

    @GetMapping("/preview/{variantId}")
    fun preview(@PathVariable projectId: String, @PathVariable variantId: String): ResponseEntity<ByteArray> {
        val variant = service.get(projectId).screens.flatMap { it.variants }.firstOrNull { it.id == variantId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "variant not found")
        val bytes = service.readVariant(projectId, variant.htmlPath)
        val headers = HttpHeaders()
        headers.set(HttpHeaders.CONTENT_TYPE, "text/html; charset=utf-8")
        headers.set("X-Content-Type-Options", "nosniff")
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store")
        headers.set("Content-Security-Policy", "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; frame-ancestors 'self'")
        return ResponseEntity(bytes, headers, HttpStatus.OK)
    }

    @PostMapping("/complete")
    fun complete(@PathVariable projectId: String): CompleteResponse {
        val workbench = try {
            service.complete(projectId)
        } catch (e: InvalidDesignWorkbenchException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
        val activeScreens = workbench.screens.filter { it.activeVariantId != null }
        val summary = buildString {
            appendLine("# Design Workbench")
            appendLine()
            activeScreens.forEach { screen ->
                appendLine("- **${screen.name}**: ${screen.purpose}")
            }
        }
        wizardService.saveStepData(
            projectId,
            FlowStepType.DESIGN.name,
            WizardStepData(fields = mapOf("summary" to JsonPrimitive(summary)), completedAt = Instant.now().toString()),
        )
        projectService.saveSpecFile(projectId, "design.md", summary)
        projectService.regenerateDocsScaffold(projectId)
        val next = projectService.advanceStep(projectId, FlowStepType.DESIGN)
        return CompleteResponse("Design workbench completed.", next?.name)
    }
}
```

- [ ] **Step 4: Disable old primary ZIP endpoint**

Modify `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignBundleController.kt` so `GET /api/v1/projects/{projectId}/design` returns 404 when no legacy bundle exists and no frontend path depends on it. Do not remove file-serving methods yet because old project data may still reference design files.

- [ ] **Step 5: Run controller tests**

Run:

```bash
cd backend
./gradlew test --tests "*.DesignWorkbenchControllerTest"
```

Expected: all `DesignWorkbenchControllerTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignBundleController.kt backend/src/main/kotlin/com/agentwork/productspecagent/service/DesignWorkbenchService.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt
git commit -m "feat(design): expose workbench api"
```

---

### Task 5: Export and Handoff Active Design Artifacts

**Files:**

- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ProjectPackageAssembler.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt`

- [ ] **Step 1: Write failing export tests**

Add tests that create a project, write an active design screen through `DesignWorkbenchStorage`, export the project and handoff ZIP, and assert:

```kotlin
assertNotNull(readZipEntry(zipBytes) { it == "design/screens/landing/index.html" })
assertNotNull(readZipEntry(zipBytes) { it == "docs/spec.md" })
```

Use existing `readZipEntry` helpers in `ExportControllerTest` and `HandoffControllerTest`.

- [ ] **Step 2: Run export tests and verify failure**

Run:

```bash
cd backend
./gradlew test --tests "*.ExportControllerTest" --tests "*.HandoffControllerTest"
```

Expected: the new assertions fail if active design files are filtered or not written.

- [ ] **Step 3: Keep design output in package assembly**

Modify `ProjectPackageAssembler` to include active workbench design files. Inject `DesignWorkbenchStorage` into the assembler and write active outputs after docs files. Keep the existing docs-file loop unchanged:

```kotlin
val docsFiles = projectService.listDocsFiles(projectId)
    .filterNot { it.first in deprecatedScaffoldDocs }
    .filterNot { isGeneratedExportDoc(it.first) }
for ((relativePath, content) in docsFiles) {
    writer.addBytes(relativePath, content)
}
```

`design/screens/{screen}/index.html` is not under `docs/`, so add this storage listing and call it from the assembler:

```kotlin
designWorkbenchStorage.listActiveOutputFiles(projectId).forEach { (relativePath, content) ->
    writer.addBytes(relativePath, content)
}
```

Add `listActiveOutputFiles(projectId)` to `DesignWorkbenchStorage`:

```kotlin
fun listActiveOutputFiles(projectId: String): List<Pair<String, ByteArray>> {
    val outputPrefix = "projects/$projectId/design/screens/"
    return objectStore.listKeys(outputPrefix).map { key ->
        key.removePrefix("projects/$projectId/") to (objectStore.get(key) ?: ByteArray(0))
    }
}
```

- [ ] **Step 4: Run export tests**

Run:

```bash
cd backend
./gradlew test --tests "*.ExportControllerTest" --tests "*.HandoffControllerTest"
```

Expected: export and handoff tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/ProjectPackageAssembler.kt backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt
git commit -m "feat(design): export active design artifacts"
```

---

### Task 6: Frontend API and Store

**Files:**

- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/lib/stores/design-workbench-store.ts`

- [ ] **Step 1: Add API types and functions**

Modify `frontend/src/lib/api.ts` by adding:

```ts
export type DesignInputKind = "TEXT" | "IMAGE" | "HTML_CSS_SNIPPET";
export type DesignInputCategory = "REFERENCE_IMAGE" | "ASSET_IMAGE" | "HTML_CSS_REFERENCE" | "UNCLEAR";
export type DesignVariantStatus = "DRAFT" | "VALID" | "INVALID";

export interface DesignInputClassification {
  category: DesignInputCategory;
  summary: string;
  suggestedUse: string;
  confidence: number;
}

export interface DesignInput {
  id: string;
  kind: DesignInputKind;
  originalName?: string | null;
  userLabel?: string | null;
  classification?: DesignInputClassification | null;
  contentRef: string;
  createdAt: string;
}

export interface DesignVariant {
  id: string;
  screenId: string;
  version: number;
  title: string;
  htmlPath: string;
  status: DesignVariantStatus;
  rationale: string;
  createdAt: string;
}

export interface DesignScreen {
  id: string;
  name: string;
  purpose: string;
  variants: DesignVariant[];
  activeVariantId?: string | null;
}

export interface DesignWorkbench {
  projectId: string;
  inputs: DesignInput[];
  screens: DesignScreen[];
  updatedAt: string;
}

export async function getDesignWorkbench(projectId: string): Promise<DesignWorkbench> {
  return apiFetch<DesignWorkbench>(`/api/v1/projects/${encodeURIComponent(projectId)}/design/workbench`);
}

export async function analyzeDesignInputs(projectId: string): Promise<DesignWorkbench> {
  return apiFetch<DesignWorkbench>(`/api/v1/projects/${encodeURIComponent(projectId)}/design/analyze`, { method: "POST" });
}

export async function proposeDesignScreens(projectId: string): Promise<DesignWorkbench> {
  return apiFetch<DesignWorkbench>(`/api/v1/projects/${encodeURIComponent(projectId)}/design/screens/propose`, { method: "POST" });
}

export async function generateDesignVariant(projectId: string, screenId: string, prompt: string): Promise<DesignWorkbench> {
  return apiFetch<DesignWorkbench>(
    `/api/v1/projects/${encodeURIComponent(projectId)}/design/screens/${encodeURIComponent(screenId)}/variants`,
    { method: "POST", body: JSON.stringify({ prompt }) },
  );
}

export async function setActiveDesignVariant(projectId: string, screenId: string, variantId: string): Promise<DesignWorkbench> {
  return apiFetch<DesignWorkbench>(
    `/api/v1/projects/${encodeURIComponent(projectId)}/design/screens/${encodeURIComponent(screenId)}/active-variant`,
    { method: "PATCH", body: JSON.stringify({ variantId }) },
  );
}

export async function completeDesignWorkbench(projectId: string): Promise<DesignCompleteResponse> {
  return apiFetch<DesignCompleteResponse>(`/api/v1/projects/${encodeURIComponent(projectId)}/design/complete`, { method: "POST" });
}
```

- [ ] **Step 2: Add Zustand store**

Create `frontend/src/lib/stores/design-workbench-store.ts`:

```ts
import { create } from "zustand";
import {
  analyzeDesignInputs,
  completeDesignWorkbench,
  generateDesignVariant,
  getDesignWorkbench,
  proposeDesignScreens,
  setActiveDesignVariant,
  type DesignWorkbench,
} from "@/lib/api";

interface DesignWorkbenchState {
  workbench: DesignWorkbench | null;
  selectedScreenId: string | null;
  loading: boolean;
  working: boolean;
  error: string | null;
  load: (projectId: string) => Promise<void>;
  analyze: (projectId: string) => Promise<void>;
  proposeScreens: (projectId: string) => Promise<void>;
  generateVariant: (projectId: string, screenId: string, prompt: string) => Promise<void>;
  setActiveVariant: (projectId: string, screenId: string, variantId: string) => Promise<void>;
  complete: (projectId: string) => Promise<void>;
  selectScreen: (screenId: string | null) => void;
  reset: () => void;
}

export const useDesignWorkbenchStore = create<DesignWorkbenchState>((set) => ({
  workbench: null,
  selectedScreenId: null,
  loading: false,
  working: false,
  error: null,

  load: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const workbench = await getDesignWorkbench(projectId);
      set({ workbench, selectedScreenId: workbench.screens[0]?.id ?? null, loading: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to load design workbench", loading: false });
    }
  },

  analyze: async (projectId) => {
    set({ working: true, error: null });
    try {
      const workbench = await analyzeDesignInputs(projectId);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to analyze references", working: false });
    }
  },

  proposeScreens: async (projectId) => {
    set({ working: true, error: null });
    try {
      const workbench = await proposeDesignScreens(projectId);
      set({ workbench, selectedScreenId: workbench.screens[0]?.id ?? null, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to propose screens", working: false });
    }
  },

  generateVariant: async (projectId, screenId, prompt) => {
    set({ working: true, error: null });
    try {
      const workbench = await generateDesignVariant(projectId, screenId, prompt);
      set({ workbench, selectedScreenId: screenId, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to generate variant", working: false });
    }
  },

  setActiveVariant: async (projectId, screenId, variantId) => {
    set({ working: true, error: null });
    try {
      const workbench = await setActiveDesignVariant(projectId, screenId, variantId);
      set({ workbench, selectedScreenId: screenId, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to activate variant", working: false });
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

  selectScreen: (screenId) => set({ selectedScreenId: screenId }),
  reset: () => set({ workbench: null, selectedScreenId: null, loading: false, working: false, error: null }),
}));
```

- [ ] **Step 3: Run frontend type/lint check**

Run:

```bash
cd frontend
npm run lint
```

Expected: no new lint errors from `api.ts` or `design-workbench-store.ts`. Existing baseline errors may still be present; record them if unchanged.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/stores/design-workbench-store.ts
git commit -m "feat(frontend): add design workbench api store"
```

---

### Task 7: Frontend Workbench UI Replacement

**Files:**

- Create: `frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx`
- Create: `frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx`
- Create: `frontend/src/components/wizard/steps/design-workbench/DesignCanvasPreview.tsx`
- Create: `frontend/src/components/wizard/steps/design-workbench/DesignControlPanel.tsx`
- Modify: `frontend/src/components/wizard/WizardForm.tsx`
- Modify: `frontend/src/app/projects/[id]/page.tsx`

- [ ] **Step 1: Create canvas preview component**

Create `frontend/src/components/wizard/steps/design-workbench/DesignCanvasPreview.tsx`:

```tsx
"use client";

import { AlertCircle, Monitor } from "lucide-react";
import type { DesignScreen } from "@/lib/api";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface Props {
  projectId: string;
  screen: DesignScreen | null;
}

export function DesignCanvasPreview({ projectId, screen }: Props) {
  const active = screen?.variants.find((variant) => variant.id === screen.activeVariantId) ?? screen?.variants.at(-1) ?? null;

  if (!screen) {
    return (
      <div className="flex h-full items-center justify-center bg-muted text-sm text-muted-foreground">
        Waehle oder erzeuge einen Screen.
      </div>
    );
  }

  if (!active) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 bg-muted text-sm text-muted-foreground">
        <AlertCircle size={18} />
        Noch keine Variante fuer {screen.name}.
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-9 items-center gap-2 border-b bg-card px-3 text-xs text-muted-foreground">
        <Monitor size={13} />
        {screen.name} - v{active.version} - {active.title}
      </div>
      <iframe
        title={`${screen.name} preview`}
        src={`${API_BASE}/api/v1/projects/${encodeURIComponent(projectId)}/design/preview/${encodeURIComponent(active.id)}`}
        sandbox="allow-scripts"
        className="h-full w-full border-0 bg-white"
      />
    </div>
  );
}
```

- [ ] **Step 2: Create input panel**

Create `frontend/src/components/wizard/steps/design-workbench/DesignInputPanel.tsx`:

```tsx
"use client";

import { ImageIcon, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { DesignWorkbench } from "@/lib/api";

interface Props {
  workbench: DesignWorkbench;
  selectedScreenId: string | null;
  working: boolean;
  onAnalyze: () => void;
  onProposeScreens: () => void;
  onSelectScreen: (screenId: string) => void;
}

export function DesignInputPanel({ workbench, selectedScreenId, working, onAnalyze, onProposeScreens, onSelectScreen }: Props) {
  return (
    <aside className="flex h-full w-72 shrink-0 flex-col overflow-y-auto border-r bg-card">
      <div className="space-y-3 border-b p-3">
        <div className="text-xs font-semibold uppercase text-muted-foreground">Inputs</div>
        <textarea
          className="min-h-24 w-full resize-none rounded-md border bg-background p-2 text-sm"
          placeholder="Beschreibe die gewuenschte visuelle Richtung."
        />
        <div className="rounded-md border border-dashed p-3 text-xs text-muted-foreground">
          <ImageIcon size={15} className="mb-1" />
          Bilder und HTML/CSS-Snippets werden in dieser Iteration ueber die API vorbereitet.
        </div>
        <Button size="sm" className="w-full gap-1.5" disabled={working} onClick={onAnalyze}>
          <Sparkles size={14} /> Referenzen analysieren
        </Button>
      </div>

      <div className="space-y-2 border-b p-3">
        <div className="text-xs font-semibold uppercase text-muted-foreground">Analyse</div>
        {workbench.inputs.length === 0 ? (
          <p className="text-xs text-muted-foreground">Noch keine analysierten Inputs.</p>
        ) : workbench.inputs.map((input) => (
          <div key={input.id} className="rounded-md border bg-background p-2 text-xs">
            <div className="font-medium">{input.userLabel ?? input.originalName ?? input.kind}</div>
            <div className="text-muted-foreground">{input.classification?.summary ?? "Nicht analysiert"}</div>
          </div>
        ))}
      </div>

      <div className="space-y-2 p-3">
        <div className="flex items-center justify-between">
          <div className="text-xs font-semibold uppercase text-muted-foreground">Screens</div>
          <Button size="sm" variant="outline" disabled={working} onClick={onProposeScreens}>Vorschlagen</Button>
        </div>
        {workbench.screens.map((screen) => (
          <button
            key={screen.id}
            type="button"
            onClick={() => onSelectScreen(screen.id)}
            className={`w-full rounded-md border px-2 py-2 text-left text-sm ${selectedScreenId === screen.id ? "border-primary bg-primary/5" : "bg-background"}`}
          >
            <div className="font-medium">{screen.name}</div>
            <div className="text-xs text-muted-foreground">{screen.purpose}</div>
          </button>
        ))}
      </div>
    </aside>
  );
}
```

- [ ] **Step 3: Create control panel**

Create `frontend/src/components/wizard/steps/design-workbench/DesignControlPanel.tsx`:

```tsx
"use client";

import { ArrowRight, Loader2, Wand2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { DesignScreen } from "@/lib/api";

interface Props {
  screen: DesignScreen | null;
  working: boolean;
  canComplete: boolean;
  onGenerateVariant: () => void;
  onSetActiveVariant: (variantId: string) => void;
  onComplete: () => void;
}

export function DesignControlPanel({ screen, working, canComplete, onGenerateVariant, onSetActiveVariant, onComplete }: Props) {
  return (
    <aside className="flex h-full w-80 shrink-0 flex-col border-l bg-card">
      <div className="space-y-3 border-b p-3">
        <div className="text-xs font-semibold uppercase text-muted-foreground">Controls</div>
        <select className="h-9 w-full rounded-md border bg-background px-2 text-sm" defaultValue="operational-saas">
          <option value="operational-saas">Operational SaaS</option>
          <option value="editorial">Editorial</option>
          <option value="mobile-first">Mobile first</option>
        </select>
        <select className="h-9 w-full rounded-md border bg-background px-2 text-sm" defaultValue="compact">
          <option value="compact">Compact</option>
          <option value="balanced">Balanced</option>
          <option value="spacious">Spacious</option>
        </select>
        <Button size="sm" className="w-full gap-1.5" disabled={!screen || working} onClick={onGenerateVariant}>
          {working ? <Loader2 size={14} className="animate-spin" /> : <Wand2 size={14} />}
          Variante erzeugen
        </Button>
      </div>

      <div className="flex-1 space-y-2 overflow-y-auto p-3">
        <div className="text-xs font-semibold uppercase text-muted-foreground">Varianten</div>
        {!screen || screen.variants.length === 0 ? (
          <p className="text-xs text-muted-foreground">Noch keine Varianten.</p>
        ) : screen.variants.map((variant) => (
          <button
            key={variant.id}
            type="button"
            onClick={() => onSetActiveVariant(variant.id)}
            className={`w-full rounded-md border p-2 text-left text-sm ${screen.activeVariantId === variant.id ? "border-primary bg-primary/5" : "bg-background"}`}
          >
            <div className="font-medium">v{variant.version} - {variant.title}</div>
            <div className="text-xs text-muted-foreground">{variant.rationale}</div>
          </button>
        ))}
      </div>

      <div className="border-t p-3">
        <Button size="sm" className="w-full gap-1.5" disabled={!canComplete || working} onClick={onComplete}>
          Weiter <ArrowRight size={14} />
        </Button>
      </div>
    </aside>
  );
}
```

- [ ] **Step 4: Create top-level form**

Create `frontend/src/components/wizard/steps/design-workbench/DesignWorkbenchForm.tsx`:

```tsx
"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { useDesignWorkbenchStore } from "@/lib/stores/design-workbench-store";
import { DesignCanvasPreview } from "./DesignCanvasPreview";
import { DesignControlPanel } from "./DesignControlPanel";
import { DesignInputPanel } from "./DesignInputPanel";

interface Props {
  projectId: string;
}

export function DesignWorkbenchForm({ projectId }: Props) {
  const {
    workbench,
    selectedScreenId,
    loading,
    working,
    error,
    load,
    analyze,
    proposeScreens,
    generateVariant,
    setActiveVariant,
    complete,
    selectScreen,
  } = useDesignWorkbenchStore();

  useEffect(() => {
    load(projectId);
  }, [projectId, load]);

  if (loading || !workbench) {
    return <div className="flex h-full items-center justify-center"><Loader2 size={18} className="animate-spin" /></div>;
  }

  const selectedScreen = workbench.screens.find((screen) => screen.id === selectedScreenId) ?? workbench.screens[0] ?? null;
  const canComplete = workbench.screens.some((screen) => screen.activeVariantId && screen.variants.some((variant) => variant.id === screen.activeVariantId && variant.status === "VALID"));

  return (
    <div className="flex h-full overflow-hidden rounded-md border bg-background">
      <DesignInputPanel
        workbench={workbench}
        selectedScreenId={selectedScreen?.id ?? null}
        working={working}
        onAnalyze={() => analyze(projectId)}
        onProposeScreens={() => proposeScreens(projectId)}
        onSelectScreen={selectScreen}
      />
      <div className="min-w-0 flex-1">
        {error && <div className="border-b border-destructive bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</div>}
        <DesignCanvasPreview projectId={projectId} screen={selectedScreen} />
      </div>
      <DesignControlPanel
        screen={selectedScreen}
        working={working}
        canComplete={canComplete}
        onGenerateVariant={() => selectedScreen && generateVariant(projectId, selectedScreen.id, "Generate a polished self-contained HTML CSS screen.")}
        onSetActiveVariant={(variantId) => selectedScreen && setActiveVariant(projectId, selectedScreen.id, variantId)}
        onComplete={() => complete(projectId)}
      />
    </div>
  );
}
```

- [ ] **Step 5: Replace DESIGN form mapping**

Modify `frontend/src/components/wizard/WizardForm.tsx`:

```tsx
import { DesignWorkbenchForm } from "./steps/design-workbench/DesignWorkbenchForm";
```

Change `FORM_MAP`:

```tsx
DESIGN: DesignWorkbenchForm,
```

Remove the import of `DesignForm` from `./steps/design/DesignForm`.

- [ ] **Step 6: Replace old bundle store lifecycle**

Modify `frontend/src/app/projects/[id]/page.tsx`:

```tsx
import { useDesignWorkbenchStore } from "@/lib/stores/design-workbench-store";
```

Replace `useDesignBundleStore.getState().reset()` with:

```tsx
useDesignWorkbenchStore.getState().reset();
```

Replace `useDesignBundleStore.getState().loadBundle(id)` with:

```tsx
useDesignWorkbenchStore.getState().load(id);
```

- [ ] **Step 7: Run frontend validation**

Run:

```bash
cd frontend
npm run lint
npm run build
```

Expected: no new lint/build failures from the new workbench files. If repository baseline lint failures remain, record exact unchanged files.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/wizard/WizardForm.tsx frontend/src/app/projects/[id]/page.tsx frontend/src/components/wizard/steps/design-workbench frontend/src/lib/stores/design-workbench-store.ts frontend/src/lib/api.ts
git commit -m "feat(frontend): replace design zip upload with workbench"
```

---

### Task 8: Full Verification and Feature Done Doc

**Files:**

- Create: `docs/features/47-agentic-design-workbench-done.md`

- [ ] **Step 1: Run backend tests**

Run:

```bash
cd backend
./gradlew test
```

Expected: build successful.

- [ ] **Step 2: Run frontend validation**

Run:

```bash
cd frontend
npm run lint
npm run build
```

Expected: `npm run build` succeeds. `npm run lint` should either pass or show only the known pre-existing baseline; if it fails, list exact files and confirm no new errors are from this feature.

- [ ] **Step 3: Manual smoke test**

Run:

```bash
./start.sh
```

Manual checks:

1. Create a new `SaaS` project.
2. Complete IDEA, PROBLEM, FEATURES, and MVP.
3. Verify DESIGN shows the workbench, not a ZIP dropzone.
4. Use `Screens vorschlagen`.
5. Select `Landing`.
6. Generate a variant.
7. Set the variant active.
8. Complete DESIGN.
9. Verify `spec/design.md` exists through project export.
10. Verify exported ZIP contains `design/screens/landing/index.html`.

- [ ] **Step 4: Write done doc**

Create `docs/features/47-agentic-design-workbench-done.md`:

```markdown
# Feature 47 - Agentic Design Workbench - Done

**Datum:** 2026-05-09
**Feature-Doc:** `docs/features/47-agentic-design-workbench.md`
**Spec:** `docs/superpowers/specs/2026-05-09-agentic-design-workbench-design.md`
**Plan:** `docs/superpowers/plans/2026-05-09-agentic-design-workbench.md`

## Zusammenfassung

- Der DESIGN-Step zeigt die neue Workbench statt der ZIP-Dropzone.
- Workbench-Domain, Storage, Agents, API und Frontend-UI wurden umgesetzt.
- Mindestens ein aktiver gueltiger Screen ist fuer Completion erforderlich.
- Completion schreibt `spec/design.md` und aktive HTML/CSS-Screen-Dateien.

## Abweichungen

- Keine Abweichungen dokumentiert.

## Offene Punkte

- Alte `DesignBundle*`-Klassen koennen in einem Folge-Refactor entfernt werden, sobald keine Migration mehr benoetigt wird.
```

- [ ] **Step 5: Commit done doc and final fixes**

```bash
git add docs/features/47-agentic-design-workbench-done.md
git commit -m "docs(feature): add agentic design workbench done doc"
```

---

## Self-Review

Spec coverage:

- ZIP removal: Task 7 replaces the UI; Task 4 de-prioritizes the old endpoint.
- Workbench domain: Task 1.
- Storage and active outputs: Task 2 and Task 5.
- Agent pipeline: Task 3.
- REST API: Task 4.
- Required completion with one active valid screen: Task 3 and Task 4.
- Frontend three-pane UX: Task 7.
- Export/Handoff: Task 5.
- Verification and done doc: Task 8.

Known implementation risks:

- The plan keeps old `DesignBundle*` code in place to avoid migration churn. A later cleanup can remove it after this feature proves stable.
- The image and snippet upload UI is deliberately narrow in Task 7; backend support exists in the storage/service shape, and the final polish can expand the input panel without changing the domain.
- Current repository lint has known pre-existing failures. Workers must distinguish baseline failures from new workbench failures during Task 7 and Task 8.
