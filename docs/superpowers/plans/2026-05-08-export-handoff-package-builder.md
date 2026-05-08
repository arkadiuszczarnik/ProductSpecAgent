# Export Handoff Package Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace scattered export and handoff ZIP assembly with a deep `ExportPackageBuilder` boundary while preserving current HTTP behavior and ZIP contents.

**Architecture:** Add a caller-optimized package facade that returns `ZipPackage` values containing bytes, filename, and media type. Migrate controllers first through a delegating implementation, then move ZIP mechanics into `ZipArchiveWriter`, shared project package content into `ProjectPackageAssembler`, and handoff-specific overlays into `HandoffContentFactory` plus `HandoffOverlayWriter`.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, MockMvc, Mustache, Java ZIP APIs, existing in-memory `ObjectStore` test doubles.

---

## File Structure

- Create `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportPackageBuilder.kt`: public facade API, option types, `ZipPackage`, and mapping extensions from existing request DTOs.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/export/DefaultExportPackageBuilder.kt`: initial delegating implementation, later orchestration point for assembler/content/overlay/writer collaborators.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/export/ZipArchiveWriter.kt`: internal ZIP writer that owns text entries, binary entries, symlink entries, duplicate protection, root prefixing, and final symlink patching.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/export/ProjectPackageAssembler.kt`: internal writer for shared project package entries.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffContentFactory.kt`: internal renderer for preview and handoff markdown/config content.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffOverlayWriter.kt`: internal writer for handoff-only entries and static sync asset bundles.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/api/ExportController.kt`: depend on `ExportPackageBuilder`.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/api/HandoffController.kt`: depend on `ExportPackageBuilder`.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`: delegate normal export entry writing to `ProjectPackageAssembler` during migration.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt`: delegate preview content to `HandoffContentFactory` during migration.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt`: keep matching logic and route ZIP writing through `ZipArchiveWriter`.
- Create `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportPackageBuilderTest.kt`: boundary ZIP tests for normal export, handoff preview/export, overrides, sync config, asset bundles, and symlinks.
- Modify `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`: keep HTTP response tests, move ZIP internals to builder tests.
- Modify `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt`: keep HTTP response and sync-url source tests, move ZIP internals to builder tests.

### Task 1: Add Facade API and Delegate to Existing Services

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportPackageBuilder.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/export/DefaultExportPackageBuilder.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportPackageBuilderTest.kt`

- [ ] **Step 1: Write a failing facade test for normal export filename and bytes**

Add this test skeleton:

```kotlin
@Test
fun `exportProject returns zip package with slug filename and zip bytes`() {
    val projectId = projectService.createProject("My App").project.id

    val zip = packageBuilder.exportProject(projectId)

    assertThat(zip.filename).isEqualTo("my-app.zip")
    assertThat(zip.mediaType).isEqualTo("application/zip")
    assertThat(zip.bytes).isNotEmpty()
    assertThat(zipEntries(zip.bytes)).contains("my-app/README.md")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ExportPackageBuilderTest
```

Expected: compilation fails because `ExportPackageBuilder` does not exist.

- [ ] **Step 3: Add facade API**

Create `ExportPackageBuilder.kt`:

```kotlin
package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.ExportRequest
import com.agentwork.productspecagent.domain.HandoffExportRequest
import com.agentwork.productspecagent.domain.HandoffPreview

interface ExportPackageBuilder {
    fun exportProject(
        projectId: String,
        options: ProjectExportOptions = ProjectExportOptions(),
    ): ZipPackage

    fun previewHandoff(
        projectId: String,
        syncUrl: String,
        format: HandoffFormat = HandoffFormat.ClaudeCode,
    ): HandoffPreview

    fun exportHandoff(
        projectId: String,
        syncUrl: String,
        options: HandoffPackageOptions = HandoffPackageOptions(),
    ): ZipPackage
}

data class ZipPackage(
    val filename: String,
    val bytes: ByteArray,
    val mediaType: String = "application/zip",
)

data class ProjectExportOptions(
    val includeDecisions: Boolean = true,
    val includeClarifications: Boolean = true,
    val includeTasks: Boolean = true,
)

data class HandoffPackageOptions(
    val format: HandoffFormat = HandoffFormat.ClaudeCode,
    val overrides: HandoffOverrides = HandoffOverrides(),
)

data class HandoffOverrides(
    val claudeMd: String? = null,
    val agentsMd: String? = null,
    val implementationOrder: String? = null,
)

@JvmInline
value class HandoffFormat private constructor(val value: String) {
    companion object {
        val ClaudeCode = HandoffFormat("claude-code")
        val Codex = HandoffFormat("codex")

        fun custom(value: String): HandoffFormat = HandoffFormat(value)

        fun fromWire(value: String?): HandoffFormat =
            when (value) {
                null, "", "claude-code" -> ClaudeCode
                "codex" -> Codex
                else -> custom(value)
            }
    }
}

fun ExportRequest.toProjectExportOptions(): ProjectExportOptions =
    ProjectExportOptions(
        includeDecisions = includeDecisions,
        includeClarifications = includeClarifications,
        includeTasks = includeTasks,
    )

fun HandoffExportRequest.toHandoffPackageOptions(): HandoffPackageOptions =
    HandoffPackageOptions(
        format = HandoffFormat.fromWire(format),
        overrides = HandoffOverrides(
            claudeMd = claudeMd,
            agentsMd = agentsMd,
            implementationOrder = implementationOrder,
        ),
    )
```

- [ ] **Step 4: Add delegating implementation**

Create `DefaultExportPackageBuilder.kt`:

```kotlin
package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.ExportRequest
import com.agentwork.productspecagent.domain.HandoffExportRequest
import com.agentwork.productspecagent.service.ProjectService
import org.springframework.stereotype.Service

@Service
class DefaultExportPackageBuilder(
    private val projectService: ProjectService,
    private val exportService: ExportService,
    private val handoffService: HandoffService,
) : ExportPackageBuilder {
    override fun exportProject(projectId: String, options: ProjectExportOptions): ZipPackage {
        val slug = slugFor(projectId)
        val bytes = exportService.exportProject(
            projectId = projectId,
            request = ExportRequest(
                includeDecisions = options.includeDecisions,
                includeClarifications = options.includeClarifications,
                includeTasks = options.includeTasks,
            ),
            includeAgentTemplateFiles = true,
        )
        return ZipPackage(filename = "$slug.zip", bytes = bytes)
    }

    override fun previewHandoff(projectId: String, syncUrl: String, format: HandoffFormat) =
        handoffService.generatePreview(projectId, format.value, syncUrl)

    override fun exportHandoff(projectId: String, syncUrl: String, options: HandoffPackageOptions): ZipPackage {
        val slug = slugFor(projectId)
        val request = HandoffExportRequest(
            format = options.format.value,
            claudeMd = options.overrides.claudeMd,
            agentsMd = options.overrides.agentsMd,
            implementationOrder = options.overrides.implementationOrder,
            syncUrl = syncUrl,
        )
        val bytes = handoffService.exportHandoff(projectId, request, syncUrl, flat = true)
        return ZipPackage(filename = "$slug-handoff.zip", bytes = bytes)
    }

    private fun slugFor(projectId: String): String =
        projectService.getProject(projectId).project.name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ExportPackageBuilderTest
```

Expected: test passes.

### Task 2: Migrate Controllers to the Facade

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/ExportController.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/HandoffController.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt`

- [ ] **Step 1: Write or update controller tests for facade-owned filenames**

In `ExportControllerTest`, keep an HTTP-level assertion:

```kotlin
mockMvc.perform(
    post("/api/v1/projects/$projectId/export")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"includeDecisions":true,"includeClarifications":true,"includeTasks":true}""")
)
    .andExpect(status().isOk())
    .andExpect(header().string("Content-Disposition", containsString("filename=\"test-project.zip\"")))
    .andExpect(content().contentType("application/zip"))
```

In `HandoffControllerTest`, keep:

```kotlin
mockMvc.perform(get("/api/v1/projects/$projectId/handoff/handoff.zip"))
    .andExpect(status().isOk())
    .andExpect(header().string("Content-Disposition", containsString("filename=\"handoff-test-handoff.zip\"")))
    .andExpect(content().contentType("application/zip"))
```

- [ ] **Step 2: Run controller tests and verify current behavior**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.ExportControllerTest --tests com.agentwork.productspecagent.api.HandoffControllerTest
```

Expected: tests pass before controller migration.

- [ ] **Step 3: Change controllers to depend on `ExportPackageBuilder`**

`ExportController` should call:

```kotlin
val zip = packageBuilder.exportProject(
    projectId = projectId,
    options = (request ?: ExportRequest()).toProjectExportOptions(),
)

return zip.toResponseEntity()
```

`HandoffController.preview` should call:

```kotlin
val preview = packageBuilder.previewHandoff(
    projectId = projectId,
    syncUrl = buildSyncUrl(projectId),
    format = HandoffFormat.fromWire(format),
)
```

`HandoffController.export` and `downloadHandoffZip` should call `packageBuilder.exportHandoff(...)`.

Add a private helper in each controller or a package-local API helper:

```kotlin
private fun ZipPackage.toResponseEntity(): ResponseEntity<ByteArray> =
    ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
        .contentType(MediaType.parseMediaType(mediaType))
        .body(bytes)
```

- [ ] **Step 4: Run controller tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.ExportControllerTest --tests com.agentwork.productspecagent.api.HandoffControllerTest
```

Expected: tests pass, and controllers no longer inject `ProjectService` for filename slugging.

### Task 3: Add Package Boundary Tests for Current ZIP Contract

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportPackageBuilderTest.kt`

- [ ] **Step 1: Add normal export ZIP contract test**

Test expected entries:

```kotlin
val zip = packageBuilder.exportProject(projectId)
val entries = zipEntries(zip.bytes)

assertThat(entries.keys).contains(
    "test-project/README.md",
    "test-project/docs/SPEC.md",
    "test-project/CLAUDE.md",
    "test-project/AGENTS.md",
)
assertThat(entries.keys.none { it.endsWith("/implementation-order.md") }).isTrue()
assertThat(entries.keys).anyMatch { it.endsWith(".asset-bundles/skills/global.product-spec-sync/product-spec-sync/SKILL.md") }
```

- [ ] **Step 2: Add handoff ZIP contract test**

Test expected root entries:

```kotlin
val zip = packageBuilder.exportHandoff(projectId, syncUrl)
val entries = zipEntries(zip.bytes)

assertThat(entries.keys).contains(
    "CLAUDE.md",
    "AGENTS.md",
    "implementation-order.md",
    ".claude/settings.json",
    ".claude/living-sync.json",
)
assertThat(entries.keys.none { it.startsWith("test-project/") }).isTrue()
assertThat(entries.keys).contains(".asset-bundles/skills/global.product-spec-sync/product-spec-sync/SKILL.md")
assertThat(entries.keys).contains(".asset-bundles/skills/global.living-sync-reporter/living-sync-reporter/SKILL.md")
```

- [ ] **Step 3: Add preview/export parity test**

```kotlin
val preview = packageBuilder.previewHandoff(projectId, syncUrl)
val zip = packageBuilder.exportHandoff(projectId, syncUrl)

assertThat(readZipEntry(zip.bytes, "CLAUDE.md")).isEqualTo(preview.claudeMd)
assertThat(readZipEntry(zip.bytes, "AGENTS.md")).isEqualTo(preview.agentsMd)
assertThat(readZipEntry(zip.bytes, "implementation-order.md")).isEqualTo(preview.implementationOrder)
```

- [ ] **Step 4: Run package boundary tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ExportPackageBuilderTest
```

Expected: tests pass against the delegating implementation.

### Task 4: Introduce `ZipArchiveWriter`

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ZipArchiveWriter.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/export/ZipArchiveWriterTest.kt`

- [ ] **Step 1: Write failing ZIP writer test**

```kotlin
@Test
fun `writer applies slug root writes text binary and symlink entries`() {
    val writer = ZipArchiveWriter(ArchiveRoot.Slugged("my-app"))

    writer.text("README.md", "hello")
    writer.binary("docs/a.bin", byteArrayOf(1, 2, 3))
    writer.symlink(".agents/skills", "../.asset-bundles/skills")

    val bytes = writer.finish()

    assertThat(readZipEntry(bytes, "my-app/README.md")).isEqualTo("hello")
    assertThat(binaryZipEntry(bytes, "my-app/docs/a.bin")).containsExactly(1, 2, 3)
    assertZipSymlink(bytes, "my-app/.agents/skills", "../.asset-bundles/skills")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ZipArchiveWriterTest
```

Expected: compilation fails because `ZipArchiveWriter` does not exist.

- [ ] **Step 3: Implement writer**

Create:

```kotlin
package com.agentwork.productspecagent.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.agentwork.productspecagent.export.ZipSymlinkSupport.addSymlinkEntry

sealed interface ArchiveRoot {
    data class Slugged(val slug: String) : ArchiveRoot
    data object Flat : ArchiveRoot
}

class ZipArchiveWriter(private val root: ArchiveRoot) {
    private val baos = ByteArrayOutputStream()
    private val zip = ZipOutputStream(baos)
    private val names = mutableSetOf<String>()
    private val symlinks = mutableSetOf<String>()

    fun text(path: String, content: String) = binary(path, content.toByteArray())

    fun binary(path: String, content: ByteArray) {
        val name = resolve(path)
        if (!names.add(name)) return
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }

    fun symlink(path: String, target: String) {
        val name = resolve(path)
        if (!names.add(name)) return
        zip.addSymlinkEntry(name, target)
        symlinks.add(name)
    }

    fun finish(): ByteArray {
        zip.close()
        return ZipSymlinkSupport.patchSymlinks(baos.toByteArray(), symlinks)
    }

    private fun resolve(path: String): String {
        val clean = path.trimStart('/')
        return when (root) {
            ArchiveRoot.Flat -> clean
            is ArchiveRoot.Slugged -> "${root.slug}/$clean"
        }
    }
}
```

- [ ] **Step 4: Run writer tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ZipArchiveWriterTest
```

Expected: writer test passes.

### Task 5: Extract Shared `ProjectPackageAssembler`

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ProjectPackageAssembler.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/DefaultExportPackageBuilder.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportPackageBuilderTest.kt`

- [ ] **Step 1: Add boundary assertion that project export still includes all existing sections**

Extend the package builder test to assert README, SPEC, raw spec, docs files, decisions, clarifications, tasks, asset bundles, and tool symlinks when relevant fixtures exist.

- [ ] **Step 2: Run package test before extraction**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ExportPackageBuilderTest
```

Expected: passes before extraction.

- [ ] **Step 3: Create assembler API**

```kotlin
class ProjectPackageAssembler(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService,
    private val wizardService: WizardService,
    private val assetBundleExporter: AssetBundleExporter,
) {
    fun writeBasePackage(
        writer: ZipArchiveWriter,
        projectId: String,
        options: ProjectExportOptions,
        agentTemplateFiles: AgentTemplateFiles,
        toolLinks: ToolLinks,
    )
}

enum class AgentTemplateFiles { Include, Skip }
enum class ToolLinks { Include, Skip }
```

- [ ] **Step 4: Move normal export entry-writing into assembler**

Copy the current `ExportService.exportProject` entry-writing branches into `ProjectPackageAssembler.writeBasePackage`, preserving the same ordering and template-rendering helpers while replacing direct `ZipOutputStream` calls with `ZipArchiveWriter.text`, `ZipArchiveWriter.binary`, and `ZipArchiveWriter.symlink`.

- [ ] **Step 5: Make `DefaultExportPackageBuilder.exportProject` use assembler directly**

```kotlin
val writer = ZipArchiveWriter(ArchiveRoot.Slugged(slug))
projectAssembler.writeBasePackage(
    writer = writer,
    projectId = projectId,
    options = options,
    agentTemplateFiles = AgentTemplateFiles.Include,
    toolLinks = ToolLinks.Include,
)
return ZipPackage(filename = "$slug.zip", bytes = writer.finish())
```

- [ ] **Step 6: Run package and existing export tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ExportPackageBuilderTest --tests com.agentwork.productspecagent.api.ExportControllerTest --tests com.agentwork.productspecagent.export.ExportServiceDesignBundleTest
```

Expected: tests pass.

### Task 6: Extract Handoff Content and Overlay

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffContentFactory.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffOverlayWriter.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/DefaultExportPackageBuilder.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportPackageBuilderTest.kt`

- [ ] **Step 1: Add handoff override and sync config tests at builder boundary**

Add assertions:

```kotlin
val zip = packageBuilder.exportHandoff(
    projectId = projectId,
    syncUrl = syncUrl,
    options = HandoffPackageOptions(
        overrides = HandoffOverrides(
            claudeMd = "# Custom CLAUDE",
            agentsMd = "# Custom AGENTS",
            implementationOrder = "# Custom Order",
        )
    )
)

assertThat(readZipEntry(zip.bytes, "CLAUDE.md")).isEqualTo("# Custom CLAUDE")
assertThat(readZipEntry(zip.bytes, "AGENTS.md")).isEqualTo("# Custom AGENTS")
assertThat(readZipEntry(zip.bytes, "implementation-order.md")).isEqualTo("# Custom Order")
assertThat(readZipEntry(zip.bytes, ".claude/living-sync.json")).contains(syncUrl.substringBefore("/handoff/handoff.zip"))
```

- [ ] **Step 2: Run builder tests before extraction**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ExportPackageBuilderTest
```

Expected: passes before extraction.

- [ ] **Step 3: Create `HandoffContentFactory`**

Move these responsibilities from `HandoffService`:

```kotlin
fun preview(projectId: String, format: HandoffFormat, syncUrl: String): HandoffPreview
fun livingSyncSettings(): String
fun livingSyncConfig(syncUrl: String): String
fun applyOverrides(preview: HandoffPreview, overrides: HandoffOverrides): HandoffPreview
```

- [ ] **Step 4: Create `HandoffOverlayWriter`**

Move these responsibilities from `HandoffService`:

```kotlin
fun write(
    writer: ZipArchiveWriter,
    preview: HandoffPreview,
    syncUrl: String,
)
```

It must write:

- `CLAUDE.md`
- `AGENTS.md`
- `implementation-order.md`
- `.claude/settings.json`
- `.claude/living-sync.json`
- Living Sync reporter bundle files
- Product Spec Sync bundle file
- `.claude/*` and `.agents/*` symlinks

- [ ] **Step 5: Make `DefaultExportPackageBuilder.exportHandoff` use direct assembly**

Use:

```kotlin
val writer = ZipArchiveWriter(ArchiveRoot.Flat)
projectAssembler.writeBasePackage(
    writer = writer,
    projectId = projectId,
    options = ProjectExportOptions(),
    agentTemplateFiles = AgentTemplateFiles.Skip,
    toolLinks = ToolLinks.Skip,
)
val preview = handoffContentFactory.applyOverrides(
    handoffContentFactory.preview(projectId, options.format, syncUrl),
    options.overrides,
)
handoffOverlayWriter.write(writer, preview, syncUrl)
return ZipPackage(filename = "$slug-handoff.zip", bytes = writer.finish())
```

- [ ] **Step 6: Run handoff tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ExportPackageBuilderTest --tests com.agentwork.productspecagent.api.HandoffControllerTest
```

Expected: tests pass and handoff no longer unzips/reprocesses normal export bytes.

### Task 7: Reduce Legacy Service Surface

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt`
- Modify: tests that still inject old services directly

- [ ] **Step 1: Search old public call sites**

Run:

```bash
rg -n "exportProject\\(|exportHandoff\\(|generatePreview\\(" backend/src/main/kotlin backend/src/test/kotlin
```

Expected: production controllers use `ExportPackageBuilder`; remaining old service use is limited to compatibility tests or internals.

- [ ] **Step 2: Convert old services to internal collaborators or adapters**

Acceptable end state:

- `ExportService` is removed if no longer needed, or delegates to `ExportPackageBuilder.exportProject`.
- `HandoffService` is removed if no longer needed, or delegates preview/export to `ExportPackageBuilder`.
- No production code should unzip package bytes to build another package.

- [ ] **Step 3: Move ZIP-internal assertions from controller tests**

Controller tests should assert HTTP status, filename, content type, and minimal non-empty ZIP. Detailed ZIP entry assertions belong in `ExportPackageBuilderTest`.

- [ ] **Step 4: Run affected tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.export.ExportPackageBuilderTest --tests com.agentwork.productspecagent.api.ExportControllerTest --tests com.agentwork.productspecagent.api.HandoffControllerTest --tests com.agentwork.productspecagent.export.AssetBundleExporterZipTest
```

Expected: tests pass.

### Task 8: Final Verification

**Files:**
- Review all modified backend export/controller/test files.

- [ ] **Step 1: Run all backend tests**

Run:

```bash
cd backend && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run diff hygiene check**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 3: Search for obsolete packaging smells**

Run:

```bash
rg -n "ZipInputStream|flat = true|includeAgentTemplateFiles|patchSymlinks" backend/src/main/kotlin/com/agentwork/productspecagent
```

Expected:

- `ZipInputStream` is absent from handoff package construction.
- `flat = true` is absent from controllers.
- `includeAgentTemplateFiles` is absent from controllers.
- `patchSymlinks` is only called by `ZipArchiveWriter` or low-level ZIP tests.
