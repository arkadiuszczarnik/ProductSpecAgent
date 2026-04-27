# Handoff URL Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the dynamic `CLAUDE.md` generation in the project handoff with a static template that prominently includes a `GET /api/v1/projects/{id}/handoff/handoff.zip` URL the agent can `curl` to re-sync, and add that endpoint.

**Architecture:** Mostly backend refactor: add a Mustache template, replace the `generateClaudeMd()` body with a two-variable render, propagate a `syncUrl` field through domain types and service methods, add a new `@GetMapping("/handoff.zip")` controller method. Frontend just gets a TypeScript type update — no UI structural change. Existing `POST /handoff/preview` and `POST /handoff/export` endpoints keep working with the same override semantics.

**Tech Stack:** Kotlin 2.3, Spring Boot 4 with Spring Web MVC, JMustache (`com.github.spullara.mustache.java:compiler:0.9.14`), JUnit 5 + MockMvc + `@SpringBootTest` for backend tests, TypeScript on the frontend.

---

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `backend/src/main/resources/templates/handoff/claude.md.mustache` | Create | Static CLAUDE.md template with `{{{projectName}}}` and `{{{syncUrl}}}` placeholders |
| `backend/src/main/kotlin/com/agentwork/productspecagent/domain/HandoffModels.kt` | Modify | Add `syncUrl: String` to `HandoffPreview`; add optional `syncUrl: String? = null` to `HandoffExportRequest` |
| `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt` | Modify | Replace `generateClaudeMd()` body with Mustache render; add `MustacheFactory`; thread `syncUrl` through `generatePreview()` and `exportHandoff()`; remove now-unused dynamic-spec/decision/task code paths used only by the old `generateClaudeMd()` |
| `backend/src/main/kotlin/com/agentwork/productspecagent/api/HandoffController.kt` | Modify | Compute sync URLs via `ServletUriComponentsBuilder`; add `@GetMapping("/handoff.zip")` handler; pass `syncUrl` to service |
| `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt` | Modify | Add tests for: GET endpoint returns ZIP, `CLAUDE.md` in ZIP starts with `# <name>` and contains the sync URL + behavioral guidelines, `POST /preview` exposes `syncUrl` |
| `frontend/src/lib/api.ts` | Modify | Add `syncUrl: string` to the `HandoffPreview` interface |

The existing `HandoffControllerTest` uses `@SpringBootTest @AutoConfigureMockMvc`. We extend it rather than introducing a parallel `HandoffServiceTest` — testing through MockMvc covers both controller wiring and the template rendering, with no extra mock setup.

---

### Task 1: Extend domain models with `syncUrl`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/HandoffModels.kt`

- [ ] **Step 1: Update `HandoffModels.kt`**

Open `backend/src/main/kotlin/com/agentwork/productspecagent/domain/HandoffModels.kt`. Replace its full content with:

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class HandoffPreview(
    val claudeMd: String,
    val agentsMd: String,
    val implementationOrder: String,
    val format: String = "claude-code",
    val syncUrl: String
)

@Serializable
data class HandoffExportRequest(
    val format: String = "claude-code",
    val claudeMd: String? = null,
    val agentsMd: String? = null,
    val implementationOrder: String? = null,
    val syncUrl: String? = null
)
```

`syncUrl` on `HandoffPreview` is **non-nullable**: every preview-call must produce a URL. On `HandoffExportRequest` it is **nullable**: the UI doesn't need to send it (the service computes it itself).

- [ ] **Step 2: Build to confirm callers still compile**

Run from `backend/`: `./gradlew compileKotlin compileTestKotlin --quiet`
Expected: BUILD SUCCESSFUL. If `HandoffService` or `HandoffController` reference `HandoffPreview(...)` constructors positionally, you'll see compile errors — that's expected, they'll be fixed in Task 3 / Task 4. Just note them and continue.

If you see compile errors only in `HandoffService.kt` and `HandoffController.kt`, that's expected for this task. Other compile errors mean someone else uses these models — investigate before continuing.

Run: `grep -rn "HandoffPreview\|HandoffExportRequest" backend/src/main`
Expected callers: only `HandoffService.kt` and `HandoffController.kt`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/HandoffModels.kt
git commit -m "feat(handoff): add syncUrl field to HandoffPreview and HandoffExportRequest"
```

---

### Task 2: Add the static `claude.md.mustache` template

**Files:**
- Create: `backend/src/main/resources/templates/handoff/claude.md.mustache`

JMustache reads templates from the classpath. We place the file under `src/main/resources/templates/handoff/` to match the existing convention used by `templates/scaffold/...` (see `DocsScaffoldGenerator.kt:43`).

The template uses **triple-stash** `{{{...}}}` for both placeholders so HTML-escaping doesn't mangle URL characters like `&` or markdown-relevant characters in project names.

- [ ] **Step 1: Create the template file**

Write to `backend/src/main/resources/templates/handoff/claude.md.mustache`:

````markdown
# {{{projectName}}}

> **AI Agent — read this entire file before doing anything else.**

---

## How to Sync This Project

Dieses Projekt wird vom Product-Spec-Agent verwaltet. Die hier vorliegende Spec
ist eine **Momentaufnahme**. Wenn du Updates brauchst, hol dir die aktuelle
Version vom Service:

```bash
curl -L -o handoff.zip "{{{syncUrl}}}"
unzip -o handoff.zip
```

- **Sync-URL:** `{{{syncUrl}}}`
- **Method:** `GET` (kein Auth, kein Body)
- **Response:** ZIP mit `CLAUDE.md`, `AGENTS.md`, `implementation-order.md`, `SPEC.md`, `decisions/`, `clarifications/`, `tasks/`, `documents/`.

**Empfohlenes Vorgehen vor jeder grösseren Änderung:**

1. Sync ziehen (`curl ...` wie oben).
2. `git diff` auf den entpackten Files prüfen — gibt es Änderungen am Spec?
3. Falls ja: Plan anpassen, mit dem User abstimmen, dann erst implementieren.

---

## Behavioral Guidelines

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.
````

- [ ] **Step 2: Verify the file exists on the classpath**

Run from `backend/`: `ls src/main/resources/templates/handoff/`
Expected output: `claude.md.mustache`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/templates/handoff/claude.md.mustache
git commit -m "feat(handoff): add static claude.md.mustache template with sync URL placeholder"
```

---

### Task 3: Refactor `HandoffService` to use the template and propagate `syncUrl`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt` (one new test added)

This task replaces the body of `generateClaudeMd()` with a Mustache render, removes the now-unused `generateClaudeMd` parameters and the dependencies that only fed it (`decisionService`, `taskService` were used by `generateClaudeMd` and `generateImplementationOrder`; `taskService` is still needed by `generateImplementationOrder`, but `decisionService` becomes unused — verify and drop). Both `generatePreview()` and `exportHandoff()` accept a `syncUrl` and pass it down.

- [ ] **Step 1: Write the failing test for the new CLAUDE.md content**

Open `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt`. Add this test method just before the closing `}` of the class:

```kotlin
@Test
fun `POST export embeds project name sync URL and behavioral guidelines into CLAUDE md`() {
    val pid = createProject()

    val result = mockMvc.perform(
        post("/api/v1/projects/$pid/handoff/export")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"format":"claude-code"}""")
    )
        .andExpect(status().isOk())
        .andReturn()

    val zipBytes = result.response.contentAsByteArray
    val claudeContent = readZipEntry(zipBytes) { it.endsWith("CLAUDE.md") }
        ?: error("CLAUDE.md not found in handoff ZIP")

    assertTrue(claudeContent.startsWith("# Handoff Test"), "CLAUDE.md should start with project H1, got: ${claudeContent.take(80)}")
    assertTrue(
        claudeContent.contains("## How to Sync This Project"),
        "CLAUDE.md should contain 'How to Sync This Project' section"
    )
    assertTrue(
        claudeContent.contains("/handoff/handoff.zip"),
        "CLAUDE.md should embed sync URL pointing at the GET endpoint"
    )
    assertTrue(
        claudeContent.contains("### 1. Think Before Coding"),
        "CLAUDE.md should contain Behavioral Guidelines section 1"
    )
    assertTrue(
        claudeContent.contains("### 4. Goal-Driven Execution"),
        "CLAUDE.md should contain Behavioral Guidelines section 4"
    )
}

private fun readZipEntry(zipBytes: ByteArray, predicate: (String) -> Boolean): String? {
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (predicate(entry.name)) return zis.readBytes().toString(Charsets.UTF_8)
            entry = zis.nextEntry
        }
    }
    return null
}
```

- [ ] **Step 2: Run the new test to verify it fails**

Run from `backend/`: `./gradlew test --tests "com.agentwork.productspecagent.api.HandoffControllerTest.POST export embeds project name sync URL and behavioral guidelines into CLAUDE md" --quiet`

Expected: FAIL. The current `generateClaudeMd()` produces a `# {projectName}` header so the first assert MIGHT pass, but the `## How to Sync This Project` and `### 1. Think Before Coding` sections are absent → test fails.

- [ ] **Step 3: Replace `HandoffService.kt` body**

Open `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt`. Replace the **entire file** with:

```kotlin
package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.*
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Service
class HandoffService(
    private val projectService: ProjectService,
    private val taskService: TaskService,
    private val exportService: ExportService
) {

    private val mf: MustacheFactory = DefaultMustacheFactory("templates/handoff")

    fun generatePreview(projectId: String, format: String, syncUrl: String): HandoffPreview {
        val projectResponse = projectService.getProject(projectId)
        val project = projectResponse.project
        val tasks = taskService.listTasks(projectId)

        return HandoffPreview(
            claudeMd = generateClaudeMd(project.name, syncUrl),
            agentsMd = generateAgentsMd(project, format),
            implementationOrder = generateImplementationOrder(tasks),
            format = format,
            syncUrl = syncUrl
        )
    }

    fun exportHandoff(projectId: String, request: HandoffExportRequest, syncUrl: String): ByteArray {
        val projectResponse = projectService.getProject(projectId)
        val project = projectResponse.project
        val slug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        val effectiveSyncUrl = request.syncUrl ?: syncUrl

        val preview = if (request.claudeMd != null || request.agentsMd != null || request.implementationOrder != null) {
            val defaults = generatePreview(projectId, request.format, effectiveSyncUrl)
            HandoffPreview(
                claudeMd = request.claudeMd ?: defaults.claudeMd,
                agentsMd = request.agentsMd ?: defaults.agentsMd,
                implementationOrder = request.implementationOrder ?: defaults.implementationOrder,
                format = request.format,
                syncUrl = effectiveSyncUrl
            )
        } else {
            generatePreview(projectId, request.format, effectiveSyncUrl)
        }

        val baseZip = exportService.exportProject(projectId)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            ZipInputStream(ByteArrayInputStream(baseZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    zip.putNextEntry(ZipEntry(entry.name))
                    zis.copyTo(zip)
                    zip.closeEntry()
                    entry = zis.nextEntry
                }
            }

            zip.addEntry("$slug/CLAUDE.md", preview.claudeMd)
            zip.addEntry("$slug/AGENTS.md", preview.agentsMd)
            zip.addEntry("$slug/implementation-order.md", preview.implementationOrder)
        }

        return baos.toByteArray()
    }

    private fun generateClaudeMd(projectName: String, syncUrl: String): String {
        val mustache = mf.compile("claude.md.mustache")
        val writer = StringWriter()
        mustache.execute(writer, mapOf("projectName" to projectName, "syncUrl" to syncUrl)).flush()
        return writer.toString()
    }

    private fun generateAgentsMd(project: Project, format: String): String = buildString {
        appendLine("# AI Coding Agent Instructions")
        appendLine()
        appendLine("Project: ${project.name}")
        appendLine()
        appendLine("## General Guidelines")
        appendLine()
        appendLine("- Read `CLAUDE.md` for project context before starting")
        appendLine("- Follow `implementation-order.md` for task sequencing")
        appendLine("- Implement one task at a time, commit after each")
        appendLine("- Write tests for all new functionality")
        appendLine()

        when (format) {
            "claude-code" -> {
                appendLine("## Claude Code")
                appendLine()
                appendLine("- Use `CLAUDE.md` as the project brief")
                appendLine("- Reference `implementation-order.md` for task priority")
                appendLine("- Commit after completing each task")
            }
            "codex" -> {
                appendLine("## Codex")
                appendLine()
                appendLine("- Use the specification files as context")
                appendLine("- Follow the implementation order strictly")
                appendLine("- Validate each task against the spec before moving on")
            }
            else -> {
                appendLine("## Custom Agent ($format)")
                appendLine()
                appendLine("- Adapt the project files to your agent's workflow")
                appendLine("- Use `SPEC.md` and `PLAN.md` as primary references")
            }
        }
    }

    private fun generateImplementationOrder(tasks: List<SpecTask>): String = buildString {
        appendLine("# Implementation Order")
        appendLine()

        val epics = tasks.filter { it.type == TaskType.EPIC }.sortedBy { it.priority }
        if (epics.isEmpty()) {
            appendLine("No tasks defined yet.")
            return@buildString
        }

        var taskNumber = 1
        for (epic in epics) {
            appendLine("## ${epic.title}")
            appendLine()
            val stories = tasks.filter { it.parentId == epic.id }.sortedBy { it.priority }
            for (story in stories) {
                appendLine("### ${story.title}")
                appendLine()
                val subtasks = tasks.filter { it.parentId == story.id }.sortedBy { it.priority }
                for (task in subtasks) {
                    appendLine("$taskNumber. **${task.title}** (${task.estimate}) — ${task.description}")
                    taskNumber++
                }
                appendLine()
            }
        }
    }

    private fun ZipOutputStream.addEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }
}
```

Key changes:
- Removed `decisionService` from the constructor (was only used by the dynamic `generateClaudeMd`).
- Added `MustacheFactory("templates/handoff")` field.
- `generateClaudeMd(projectName, syncUrl)` now renders the template.
- `generatePreview(projectId, format, syncUrl)` requires `syncUrl` from the caller.
- `exportHandoff(projectId, request, syncUrl)` requires `syncUrl`; if `request.syncUrl` is set it overrides.
- `generateAgentsMd` and `generateImplementationOrder` are unchanged.

- [ ] **Step 4: Confirm `decisionService` removal**

The original `HandoffService` had `private val decisionService: DecisionService` injected. Removing it should not break Spring's dependency injection because `DecisionService` is an `@Service` and other beans presumably use it.

Run: `grep -rn "decisionService" backend/src/main/kotlin/com/agentwork/productspecagent/export/`
Expected: empty (no remaining references in `export/`).

If `grep` finds `decisionService` anywhere else in `export/`, do not remove it from the constructor — instead leave it injected but unused, and report this as a `DONE_WITH_CONCERNS`.

- [ ] **Step 5: Build to confirm `HandoffController.kt` is the only remaining compile error**

Run from `backend/`: `./gradlew compileKotlin --quiet`
Expected: compile errors only in `HandoffController.kt` (it still calls `generatePreview(projectId)` and `exportHandoff(projectId, request)` with the old signatures). That's normal — Task 4 fixes them.

- [ ] **Step 6: Skip running the test for now**

Tests cannot run until Task 4 fixes the controller. Continue to Task 4.

- [ ] **Step 7: Commit (allow follow-up tasks to fix the controller)**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt
git commit -m "refactor(handoff): render CLAUDE.md from static Mustache template with syncUrl"
```

The repo is intentionally in a non-compiling state for one commit. The next task fixes it. If your team blocks non-compiling commits via hooks, combine Task 3 and Task 4 into one commit at the end of Task 4.

---

### Task 4: Add GET endpoint and update controller

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/HandoffController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt`

The controller needs to:
- Compute the sync URL for `POST /preview` and `POST /export` (because the `HandoffService` now requires it).
- Add a new `GET /handoff.zip` handler that uses the request URI as its sync URL and delegates to `HandoffService.exportHandoff()`.

- [ ] **Step 1: Replace the controller body**

Open `backend/src/main/kotlin/com/agentwork/productspecagent/api/HandoffController.kt`. Replace the entire file with:

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.HandoffExportRequest
import com.agentwork.productspecagent.domain.HandoffPreview
import com.agentwork.productspecagent.export.HandoffService
import com.agentwork.productspecagent.service.ProjectService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/v1/projects/{projectId}/handoff")
class HandoffController(
    private val handoffService: HandoffService,
    private val projectService: ProjectService
) {

    @PostMapping("/preview")
    fun preview(
        @PathVariable projectId: String,
        @RequestParam(defaultValue = "claude-code") format: String
    ): ResponseEntity<HandoffPreview> {
        val syncUrl = buildSyncUrl(projectId)
        val preview = handoffService.generatePreview(projectId, format, syncUrl)
        return ResponseEntity.ok(preview)
    }

    @PostMapping("/export")
    fun export(
        @PathVariable projectId: String,
        @RequestBody(required = false) request: HandoffExportRequest?
    ): ResponseEntity<ByteArray> {
        val project = projectService.getProject(projectId).project
        val slug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val syncUrl = buildSyncUrl(projectId)
        val zipBytes = handoffService.exportHandoff(projectId, request ?: HandoffExportRequest(), syncUrl)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$slug-handoff.zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zipBytes)
    }

    @GetMapping("/handoff.zip")
    fun downloadHandoffZip(
        @PathVariable projectId: String,
        request: HttpServletRequest
    ): ResponseEntity<ByteArray> {
        val project = projectService.getProject(projectId).project
        val slug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val syncUrl = ServletUriComponentsBuilder.fromRequest(request).build().toUriString()
        val zipBytes = handoffService.exportHandoff(projectId, HandoffExportRequest(), syncUrl)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$slug-handoff.zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zipBytes)
    }

    private fun buildSyncUrl(projectId: String): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/projects/{projectId}/handoff/handoff.zip")
            .buildAndExpand(projectId)
            .toUriString()
}
```

Notes:
- `buildSyncUrl` always points at the GET endpoint, regardless of which HTTP method invoked it. That's deliberate: the URL embedded in `CLAUDE.md` should let the agent fetch with `curl`.
- `GET /handoff.zip` uses `fromRequest(request).build().toUriString()` for `syncUrl` — that's the exact URL the agent called, so re-running `curl` with that URL works.
- All three endpoints share the same slug-derivation logic; we don't extract it into a helper because that would be premature abstraction (3 callers, 1-line each).

- [ ] **Step 2: Add a test for the GET endpoint**

Open `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt`. Add the import at the top:

```kotlin
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
```

Then add this test just before the closing `}` of the class:

```kotlin
@Test
fun `GET handoff zip returns ZIP and embeds the request URL into CLAUDE md`() {
    val pid = createProject()

    val result = mockMvc.perform(get("/api/v1/projects/$pid/handoff/handoff.zip"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("-handoff.zip")))
        .andExpect(header().string("Content-Type", "application/zip"))
        .andReturn()

    val zipBytes = result.response.contentAsByteArray
    assertTrue(zipBytes.isNotEmpty(), "GET should return non-empty ZIP")

    val claudeContent = readZipEntry(zipBytes) { it.endsWith("CLAUDE.md") }
        ?: error("CLAUDE.md not found in handoff ZIP")

    assertTrue(
        claudeContent.contains("/api/v1/projects/$pid/handoff/handoff.zip"),
        "CLAUDE.md from GET response should embed the original request URL"
    )
}
```

This test relies on `readZipEntry` from Task 3 Step 1 — both helpers live in the same test class.

- [ ] **Step 3: Run all `HandoffControllerTest` tests**

Run from `backend/`: `./gradlew test --tests "com.agentwork.productspecagent.api.HandoffControllerTest" --quiet`

Expected: all tests pass, including:
- the four pre-existing tests
- the new `POST export embeds...` test from Task 3
- the new `GET handoff zip returns...` test

If anything fails, read the error and fix before continuing. Common pitfalls:
- Test asserts `# Handoff Test` but the project name comes back as `Handoff Test` without the H1 → check the Mustache template syntax (use triple-stash `{{{projectName}}}`).
- Sync URL doesn't include `/handoff/handoff.zip` → check `buildSyncUrl` path construction.

- [ ] **Step 4: Run the full backend test suite**

Run from `backend/`: `./gradlew test --quiet`

Expected: BUILD SUCCESSFUL. If anything outside the handoff area fails, you've broken something incidentally — investigate.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/HandoffController.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt
git commit -m "feat(handoff): add GET /handoff.zip endpoint and propagate sync URL"
```

---

### Task 5: Update frontend `HandoffPreview` type

**Files:**
- Modify: `frontend/src/lib/api.ts`

The frontend uses `HandoffPreview` in `lib/api.ts`. After Task 1 the backend serializes a new `syncUrl` field; the TypeScript type needs to know about it. The UI itself doesn't need to display `syncUrl` (it's already inside the rendered `claudeMd` shown in the dialog), so no React component changes.

- [ ] **Step 1: Locate the existing type**

Run: `grep -n "HandoffPreview" frontend/src/lib/api.ts`
Expected: one type definition and at least one function signature using it.

- [ ] **Step 2: Add `syncUrl: string` to the type**

Find the `HandoffPreview` interface in `frontend/src/lib/api.ts`. It currently looks like:

```ts
export interface HandoffPreview {
  claudeMd: string;
  agentsMd: string;
  implementationOrder: string;
  format: string;
}
```

Replace with:

```ts
export interface HandoffPreview {
  claudeMd: string;
  agentsMd: string;
  implementationOrder: string;
  format: string;
  syncUrl: string;
}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run from `frontend/`: `npx tsc --noEmit`
Expected: no errors. The dialog destructures `claudeMd`/`agentsMd`/`implementationOrder` only, so adding a new field doesn't break consumers.

- [ ] **Step 4: Verify ESLint passes for the changed file**

Run from `frontend/`: `npx eslint src/lib/api.ts`
Expected: no new errors in this file.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): add syncUrl to HandoffPreview type"
```

---

### Task 6: Manual browser + curl verification

No tests — verification only. The dev servers should already be running (`backend` on port 8080, `frontend` on port 3000); if not, start them per the project README.

- [ ] **Step 1: Open the projects overview**

Navigate to `http://localhost:3000/projects`. Click an existing project (or create a fresh one).

- [ ] **Step 2: Open the Handoff dialog**

In the workspace, find the "Handoff" trigger (existing UI). Click it.

Expected: Dialog opens, "CLAUDE.md" tab shows the new static template with:
- `# <project name>` at the top
- the `> **AI Agent — read this entire file...** ` blockquote
- `## How to Sync This Project` section with a URL like `http://localhost:8080/api/v1/projects/<id>/handoff/handoff.zip`
- `### 1. Think Before Coding` through `### 4. Goal-Driven Execution` sections

- [ ] **Step 3: Verify the AGENTS.md and Implementation Order tabs are unchanged**

Switch to the "AGENTS.md" tab — same content as before this feature.
Switch to the "Impl Order" tab — same content as before.

- [ ] **Step 4: Export the ZIP via the dialog**

Click "Export Handoff ZIP". A download starts. Save the file.

Run: `unzip -l <downloaded-file>.zip`
Expected: `CLAUDE.md`, `AGENTS.md`, `implementation-order.md` plus the spec/decisions/tasks/documents directories.

Run: `unzip -p <downloaded-file>.zip <slug>/CLAUDE.md | head -30`
Expected: starts with `# <project name>`, includes the sync URL, includes "How to Sync This Project".

- [ ] **Step 5: Verify the GET endpoint works with `curl`**

Copy the sync URL from the unzipped `CLAUDE.md`. Run:

```bash
curl -L -o /tmp/handoff.zip "<paste-url-here>"
unzip -l /tmp/handoff.zip
```

Expected: ZIP downloads (no error), `unzip -l` shows the same files as Step 4.

- [ ] **Step 6: Verify URL stays consistent**

Run `curl` twice, save both ZIPs as `/tmp/handoff1.zip` and `/tmp/handoff2.zip`. The exported `CLAUDE.md` content should be identical (same project name, same URL). Run:

```bash
diff <(unzip -p /tmp/handoff1.zip <slug>/CLAUDE.md) <(unzip -p /tmp/handoff2.zip <slug>/CLAUDE.md)
```

Expected: empty diff. If the file differs, the template rendering is non-deterministic — investigate.

- [ ] **Step 7: No commit needed**

Verification only. If any check fails, fix the issue in the relevant earlier task and re-verify.

---

## Self-Review Checklist (already performed)

- **Spec coverage:**
  - GET endpoint (`/handoff.zip`) → Task 4
  - Static `CLAUDE.md` content (project H1, AI agent quote, How to Sync section, Behavioral Guidelines) → Task 2 (template) + Task 3 (rendering) + Task 4 (test asserting these sections)
  - `HandoffPreview.syncUrl` and `HandoffExportRequest.syncUrl?` → Task 1
  - Sync URL via `ServletUriComponentsBuilder` → Task 4 (`buildSyncUrl` for POST, `fromRequest(request)` for GET)
  - User edits override → preserved unchanged in Task 3 (`request.claudeMd ?: defaults.claudeMd`)
  - `AGENTS.md` and `implementation-order.md` unchanged → preserved in Task 3
  - Backend tests (template rendering + GET endpoint) → Task 3 Step 1 + Task 4 Step 2
  - Manual browser verification → Task 6
- **Placeholder scan:** No "TBD"/"TODO". All steps contain literal code or commands. The only deliberate "non-trivial" step is Task 6 Step 2 ("find the Handoff trigger") which is unavoidable (UI navigation).
- **Type consistency:** `syncUrl: String` in `HandoffPreview` (non-null) and `syncUrl: String? = null` in `HandoffExportRequest` are referenced consistently across Tasks 1, 3, 4, 5. Method signatures match: `generatePreview(projectId, format, syncUrl)` and `exportHandoff(projectId, request, syncUrl)` are used identically by `HandoffController` and the tests.
- **Discovered constraint:** Task 3 leaves the repo in a non-compiling state for one commit (`HandoffService` signatures change before `HandoffController` is updated). Documented at the end of Task 3 with a fallback to combine commits if hooks reject the intermediate state.
- **Removed dependency:** Task 3 drops `decisionService` from `HandoffService`'s constructor (was only used by the old dynamic `generateClaudeMd`). Step 4 of Task 3 verifies via grep that nothing else in `export/` references it.
