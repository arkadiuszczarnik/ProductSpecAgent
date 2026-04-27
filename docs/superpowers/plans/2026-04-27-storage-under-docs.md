# Storage-Konsolidierung unter `docs/` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Konsolidiere `decisions/`, `clarifications/`, `tasks/`, `uploads/` unterhalb von `docs/` — sowohl im Filesystem (`data/projects/{id}/docs/...`) als auch im Handoff-ZIP. Zusätzlich: `SPEC.md`, `PLAN.md` und kuratierte MD-Sichten ins `docs/`-Subverzeichnis des ZIPs verlagern.

**Architecture:** Vier Storage-Klassen erhalten je eine geänderte Pfad-Konstruktion. `ProjectStorage.listDocsFiles()` wechselt von `String` auf `ByteArray`-Inhalt, weil unter `docs/uploads/` Binärdateien liegen. `ExportService` schreibt alle Spec-begleitenden ZIP-Pfade unter `docs/...` und verliert den `includeDocuments`-Block (Uploads kommen jetzt automatisch via `listDocsFiles`). Frontend-Type-Bereinigung folgt.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, kotlinx.serialization, Gradle. Frontend: TypeScript (Next.js 16), nur Type-Cleanup.

**Spec:** [`docs/superpowers/specs/2026-04-27-storage-under-docs-design.md`](../specs/2026-04-27-storage-under-docs-design.md)

---

## Task 1: TaskStorage → `docs/tasks/`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/TaskStorage.kt:20`

Diese Klasse hat keinen dedizierten Unit-Test. Wir verifizieren über den vorhandenen `TaskControllerTest` (REST-Roundtrip) und einen neuen FS-Path-Assertion-Test.

- [ ] **Step 1: Failing FS-path-assertion test schreiben**

Erstelle eine neue Testdatei `backend/src/test/kotlin/com/agentwork/productspecagent/storage/TaskStorageTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.SpecTask
import com.agentwork.productspecagent.domain.TaskType
import com.agentwork.productspecagent.domain.SpecTaskStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TaskStorageTest {

    @TempDir lateinit var tempDir: Path

    @Test
    fun `saveTask writes to docs-tasks subdirectory`() {
        val storage = TaskStorage(tempDir.toString())
        val task = SpecTask(
            id = "t1",
            projectId = "p1",
            type = TaskType.EPIC,
            title = "Epic",
            description = "...",
            estimate = "1w",
            priority = 1,
            status = SpecTaskStatus.OPEN,
            parentId = null,
            specSection = null,
            dependencies = emptyList()
        )

        storage.saveTask(task)

        val expected = tempDir.resolve("projects/p1/docs/tasks/t1.json")
        assertTrue(Files.exists(expected), "Task should land at docs/tasks/, got existing files: " +
            Files.walk(tempDir).filter(Files::isRegularFile).toList())
    }
}
```

> **Hinweis:** Falls `SpecTask` zusätzliche required Felder hat, anhand der Datei `domain/SpecTask.kt` ergänzen — der Test braucht nur ein konstruierbares Objekt.

- [ ] **Step 2: Test laufen lassen, FAIL erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.TaskStorageTest" --quiet
```

Expected: `FAIL` — Datei landet aktuell unter `projects/p1/tasks/t1.json`, nicht unter `docs/tasks/`.

- [ ] **Step 3: Pfad in TaskStorage anpassen**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/storage/TaskStorage.kt`, Zeile 19–20:

```kotlin
// alt
private fun tasksDir(projectId: String): Path =
    Path.of(dataPath, "projects", projectId, "tasks")

// neu
private fun tasksDir(projectId: String): Path =
    Path.of(dataPath, "projects", projectId, "docs", "tasks")
```

- [ ] **Step 4: Test laufen lassen, PASS erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.TaskStorageTest" --quiet
```

Expected: `PASS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/TaskStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/TaskStorageTest.kt
git commit -m "refactor(storage): move tasks under docs/tasks/"
```

---

## Task 2: DecisionStorage → `docs/decisions/`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DecisionStorage.kt:20`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DecisionStorageTest.kt`

- [ ] **Step 1: Failing FS-path-assertion test**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DecisionStorageTest.kt`:

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DecisionStorageTest {

    @TempDir lateinit var tempDir: Path

    @Test
    fun `saveDecision writes to docs-decisions subdirectory`() {
        val storage = DecisionStorage(tempDir.toString())
        val decision = Decision(
            id = "d1",
            projectId = "p1",
            stepType = StepType.IDEA,
            title = "Test decision",
            options = emptyList(),
            recommendation = "...",
            status = DecisionStatus.OPEN,
            chosenOptionId = null,
            rationale = null,
            createdAt = "2026-04-27T10:00:00Z",
            resolvedAt = null
        )

        storage.saveDecision(decision)

        val expected = tempDir.resolve("projects/p1/docs/decisions/d1.json")
        assertTrue(Files.exists(expected), "Decision should land at docs/decisions/")
    }
}
```

> **Hinweis:** `Decision`-Konstruktor-Signatur in `domain/Decision.kt` prüfen; ggf. fehlende Felder ergänzen.

- [ ] **Step 2: Test laufen lassen, FAIL erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.DecisionStorageTest" --quiet
```

Expected: `FAIL`.

- [ ] **Step 3: Pfad in DecisionStorage anpassen**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DecisionStorage.kt`, Zeile 19–20:

```kotlin
// alt
private fun decisionsDir(projectId: String): Path =
    Path.of(dataPath, "projects", projectId, "decisions")

// neu
private fun decisionsDir(projectId: String): Path =
    Path.of(dataPath, "projects", projectId, "docs", "decisions")
```

- [ ] **Step 4: Test laufen lassen, PASS erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.DecisionStorageTest" --quiet
```

Expected: `PASS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/DecisionStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/DecisionStorageTest.kt
git commit -m "refactor(storage): move decisions under docs/decisions/"
```

---

## Task 3: ClarificationStorage → `docs/clarifications/`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ClarificationStorage.kt:20`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ClarificationStorageTest.kt`

- [ ] **Step 1: Failing FS-path-assertion test**

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ClarificationStorageTest {

    @TempDir lateinit var tempDir: Path

    @Test
    fun `saveClarification writes to docs-clarifications subdirectory`() {
        val storage = ClarificationStorage(tempDir.toString())
        val clarification = Clarification(
            id = "c1",
            projectId = "p1",
            stepType = StepType.IDEA,
            question = "Test?",
            reason = "Because...",
            status = ClarificationStatus.OPEN,
            answer = null,
            createdAt = "2026-04-27T10:00:00Z",
            answeredAt = null
        )

        storage.saveClarification(clarification)

        val expected = tempDir.resolve("projects/p1/docs/clarifications/c1.json")
        assertTrue(Files.exists(expected), "Clarification should land at docs/clarifications/")
    }
}
```

> **Hinweis:** Konstruktor-Signatur in `domain/Clarification.kt` prüfen; fehlende Felder ergänzen.

- [ ] **Step 2: Test laufen lassen, FAIL erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.ClarificationStorageTest" --quiet
```

Expected: `FAIL`.

- [ ] **Step 3: Pfad in ClarificationStorage anpassen**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ClarificationStorage.kt`, Zeile 19–20:

```kotlin
// alt
private fun clarificationsDir(projectId: String): Path =
    Path.of(dataPath, "projects", projectId, "clarifications")

// neu
private fun clarificationsDir(projectId: String): Path =
    Path.of(dataPath, "projects", projectId, "docs", "clarifications")
```

- [ ] **Step 4: Test laufen lassen, PASS erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.ClarificationStorageTest" --quiet
```

Expected: `PASS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/ClarificationStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/ClarificationStorageTest.kt
git commit -m "refactor(storage): move clarifications under docs/clarifications/"
```

---

## Task 4: UploadStorage → `docs/uploads/`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt:35`
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt` (Pfad-Assertions an mehreren Stellen)

Anders als bei den drei vorigen Tasks gibt es hier bereits einen umfangreichen Test mit FS-Path-Assertions. Wir aktualisieren ihn als Failing-Test.

- [ ] **Step 1: UploadStorageTest auf neue Pfade umschreiben**

Ersetze in `UploadStorageTest.kt` alle Vorkommen von `"projects/p1/uploads"` durch `"projects/p1/docs/uploads"`:

```bash
# Sicherer Sed-Pfad (BSD/macOS): in-place mit explizitem Backup-Suffix
sed -i '' 's|projects/p1/uploads|projects/p1/docs/uploads|g' \
    backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt
```

Verifikation: `grep -n "projects/p1" backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt` darf nur noch `projects/p1/docs/uploads` zeigen.

- [ ] **Step 2: Test laufen lassen, FAIL erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.UploadStorageTest" --quiet
```

Expected: mehrere `FAIL` — Files landen unter `projects/p1/uploads/...`, Tests erwarten `projects/p1/docs/uploads/...`.

- [ ] **Step 3: Pfad in UploadStorage anpassen**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt`, Zeile 34–35:

```kotlin
// alt
private fun uploadsDir(projectId: String): Path =
    Paths.get(dataPath, "projects", projectId, "uploads")

// neu
private fun uploadsDir(projectId: String): Path =
    Paths.get(dataPath, "projects", projectId, "docs", "uploads")
```

- [ ] **Step 4: Test laufen lassen, PASS erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.UploadStorageTest" --quiet
```

Expected: alle `PASS`.

- [ ] **Step 5: FileControllerTest Pfad-Setup anpassen**

Edit `backend/src/test/kotlin/com/agentwork/productspecagent/api/FileControllerTest.kt`, Zeile 91 + 97:

```kotlin
// alt (Zeile 91)
val dataPath = java.nio.file.Paths.get("build/test-data/projects/$pid/uploads")
// neu
val dataPath = java.nio.file.Paths.get("build/test-data/projects/$pid/docs/uploads")
```

```kotlin
// alt (Zeile 97)
mockMvc.perform(get("/api/v1/projects/$pid/files/uploads/doc.pdf"))
// neu
mockMvc.perform(get("/api/v1/projects/$pid/files/docs/uploads/doc.pdf"))
```

- [ ] **Step 6: FileControllerTest laufen lassen, PASS erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.FileControllerTest" --quiet
```

Expected: alle `PASS`. (Dieser Test geht über den HTTP-Endpoint `/files/{path}` und ruft die Datei direkt vom Filesystem ab — die Pfad-Änderung muss transparent für die API sein.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/FileControllerTest.kt
git commit -m "refactor(storage): move uploads under docs/uploads/"
```

---

## Task 5: `ProjectStorage.listDocsFiles()` → binärfähig (`ByteArray`)

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt:96-106`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt:124-127`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt:37-39`

**Begründung:** Mit Task 4 liegen User-Uploads (PDF, PNG, …) jetzt unter `docs/uploads/`. `Files.readString()` würde an Binärdateien mit `MalformedInputException` scheitern, sobald `listDocsFiles()` ein nicht-UTF-8-File trifft.

Da Compiler-Errors über den Compile-Schritt selbst auffallen (Signatur-Mismatch im Caller), nutzen wir hier keinen Failing-Test — die Compile-Stage ist der Gate.

- [ ] **Step 1: `ProjectStorage.listDocsFiles` Rückgabetyp ändern**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt`, Zeile 95–106:

```kotlin
// alt
/** Returns every file under `data/projects/{id}/docs/` as `(relativePath, content)` pairs. */
fun listDocsFiles(projectId: String): List<Pair<String, String>> {
    val docs = projectDir(projectId).resolve("docs")
    if (!Files.exists(docs)) return emptyList()
    val projectRoot = projectDir(projectId)
    return Files.walk(docs).use { stream ->
        stream.filter { Files.isRegularFile(it) }.toList()
    }.map { file ->
        val rel = projectRoot.relativize(file).toString().replace('\\', '/')
        rel to Files.readString(file)
    }
}

// neu
/** Returns every file under `data/projects/{id}/docs/` as `(relativePath, bytes)` pairs. */
fun listDocsFiles(projectId: String): List<Pair<String, ByteArray>> {
    val docs = projectDir(projectId).resolve("docs")
    if (!Files.exists(docs)) return emptyList()
    val projectRoot = projectDir(projectId)
    return Files.walk(docs).use { stream ->
        stream.filter { Files.isRegularFile(it) }.toList()
    }.map { file ->
        val rel = projectRoot.relativize(file).toString().replace('\\', '/')
        rel to Files.readAllBytes(file)
    }
}
```

- [ ] **Step 2: `ProjectService.listDocsFiles` Pass-through Signatur anpassen**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`, Zeile 124:

```kotlin
// alt
fun listDocsFiles(projectId: String): List<Pair<String, String>> {
    storage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
    return storage.listDocsFiles(projectId)
}

// neu
fun listDocsFiles(projectId: String): List<Pair<String, ByteArray>> {
    storage.loadProject(projectId) ?: throw ProjectNotFoundException(projectId)
    return storage.listDocsFiles(projectId)
}
```

- [ ] **Step 3: `ExportService` Caller auf `addBinaryEntry` umstellen**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`, Zeile 36–39:

```kotlin
// alt
// Docs scaffold — always included (docs/features, docs/architecture, docs/backend, docs/frontend)
for ((relativePath, content) in projectService.listDocsFiles(projectId)) {
    zip.addEntry("$prefix/$relativePath", content)
}

// neu
// Docs scaffold + alle Spec-begleitenden Inhalte (decisions/clarifications/tasks/uploads als JSON+Binär)
for ((relativePath, content) in projectService.listDocsFiles(projectId)) {
    zip.addBinaryEntry("$prefix/$relativePath", content)
}
```

- [ ] **Step 4: Compile prüfen**

```bash
cd backend && ./gradlew compileKotlin compileTestKotlin --quiet
```

Expected: keine Errors. Falls es weitere Caller von `listDocsFiles()` gibt (z.B. ein Handoff-Sync-Service), die `String` erwarten — kompilieren würde fehlschlagen. Dann diese Caller analog auf `addBinaryEntry` / `String(bytes)` umstellen.

> **Hinweis:** `git grep "listDocsFiles" -- backend/src` ausführen, falls Step 4 unerwartete Errors zeigt. Aktuell sind nur die zwei oben genannten Caller bekannt (`ProjectService` und `ExportService`).

- [ ] **Step 5: Bestehende Tests laufen lassen**

```bash
cd backend && ./gradlew test --quiet
```

Expected: alle vorherigen Tests immer noch grün. `ExportControllerTest` bekommt jetzt ggf. einige neue ZIP-Einträge (z.B. `docs/decisions/{uuid}.json` falls schon Decisions gespeichert) — aber nichts, was die bestehenden Assertions kaputt machen sollte.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt
git commit -m "refactor(storage): listDocsFiles returns ByteArray for binary uploads"
```

---

## Task 6: ExportService — kuratierte MD + SPEC + PLAN unter `docs/` im ZIP

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt` (Zeilen 31, 47, 58, 67)
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`

- [ ] **Step 1: Failing-Test schreiben — neue ZIP-Pfade erwarten**

Edit `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`, ersetze den Test `POST export returns ZIP with README and SPEC` (Zeilen 48–76) durch:

```kotlin
@Test
fun `POST export returns ZIP with README at root and SPEC under docs`() {
    val pid = createProject()

    val result = mockMvc.perform(
        post("/api/v1/projects/$pid/export")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"includeDecisions":true,"includeClarifications":true,"includeTasks":true}""")
    )
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".zip")))
        .andReturn()

    val zipBytes = result.response.contentAsByteArray
    assertTrue(zipBytes.isNotEmpty())

    val entries = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            entries.add(entry.name)
            entry = zis.nextEntry
        }
    }

    assertTrue(entries.any { it.endsWith("/README.md") && !it.contains("/docs/") },
        "ZIP should contain README.md at root, got: $entries")
    assertTrue(entries.any { it.endsWith("/docs/SPEC.md") },
        "ZIP should contain docs/SPEC.md, got: $entries")
    assertTrue(entries.any { it.endsWith(".gitignore") },
        "ZIP should contain .gitignore, got: $entries")
    assertTrue(entries.none { it.matches(Regex(".*/[^/]+/SPEC\\.md$")) && !it.contains("/docs/") },
        "ZIP should NOT contain top-level SPEC.md anymore, got: $entries")
}
```

- [ ] **Step 2: Test laufen lassen, FAIL erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.ExportControllerTest.POST export returns ZIP with README at root and SPEC under docs" --quiet
```

Expected: `FAIL` — `docs/SPEC.md` fehlt, oben liegt `SPEC.md` am Root.

- [ ] **Step 3: ExportService — vier Pfade umstellen**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`:

Zeile 31:
```kotlin
// alt
zip.addEntry("$prefix/SPEC.md", generateSpec(projectId, flowState))
// neu
zip.addEntry("$prefix/docs/SPEC.md", generateSpec(projectId, flowState))
```

Zeile 47:
```kotlin
// alt
zip.addEntry("$prefix/decisions/${String.format("%03d", i + 1)}-$slug.md", generateDecisionMd(d))
// neu
zip.addEntry("$prefix/docs/decisions/${String.format("%03d", i + 1)}-$slug.md", generateDecisionMd(d))
```

Zeile 58:
```kotlin
// alt
zip.addEntry("$prefix/clarifications/${String.format("%03d", i + 1)}-$slug.md", generateClarificationMd(c))
// neu
zip.addEntry("$prefix/docs/clarifications/${String.format("%03d", i + 1)}-$slug.md", generateClarificationMd(c))
```

Zeilen 67 + 71 (PLAN.md und Task-Files):
```kotlin
// alt
zip.addEntry("$prefix/PLAN.md", generatePlanMd(tasks))
tasks.forEachIndexed { i, t ->
    val slug = t.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
    val typePrefix = t.type.name.lowercase()
    zip.addEntry("$prefix/tasks/${String.format("%03d", i + 1)}-$typePrefix-$slug.md", generateTaskMd(t))
}

// neu
zip.addEntry("$prefix/docs/PLAN.md", generatePlanMd(tasks))
tasks.forEachIndexed { i, t ->
    val slug = t.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
    val typePrefix = t.type.name.lowercase()
    zip.addEntry("$prefix/docs/tasks/${String.format("%03d", i + 1)}-$typePrefix-$slug.md", generateTaskMd(t))
}
```

- [ ] **Step 4: Test laufen lassen, PASS erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.ExportControllerTest.POST export returns ZIP with README at root and SPEC under docs" --quiet
```

Expected: `PASS`.

- [ ] **Step 5: Vollständigen ExportControllerTest laufen lassen**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.ExportControllerTest" --quiet
```

Expected: die zwei `includeDocuments`-Tests werden in Task 8 noch angepasst — sie können hier noch FAIL produzieren. Andere Tests (z.B. `POST export ZIP includes docs scaffold directory`) sollten PASSen.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt
git commit -m "refactor(export): bundle SPEC.md, PLAN.md and curated MD under docs/ in ZIP"
```

---

## Task 7: README-Generator-Inhalt aktualisieren

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt` (Methode `generateReadme`, Zeilen 93–98)
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`

- [ ] **Step 1: Failing-Test für README-Inhalt**

Hänge in `ExportControllerTest.kt` einen neuen Test an (vor der schließenden `}`):

```kotlin
@Test
fun `README references new docs paths`() {
    val pid = createProject()

    val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
        .andExpect(status().isOk()).andReturn()

    val readme = ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
        var entry = zis.nextEntry
        var content: String? = null
        while (entry != null) {
            if (entry.name.endsWith("/README.md")) {
                content = String(zis.readAllBytes())
                break
            }
            entry = zis.nextEntry
        }
        content
    }

    assertNotNull(readme, "README.md should exist in ZIP")
    assertTrue(readme!!.contains("`docs/SPEC.md`"), "README should reference docs/SPEC.md, got: $readme")
    assertTrue(readme.contains("`docs/PLAN.md`"), "README should reference docs/PLAN.md")
    assertTrue(readme.contains("`docs/decisions/`"), "README should reference docs/decisions/")
    assertTrue(readme.contains("`docs/clarifications/`"), "README should reference docs/clarifications/")
    assertTrue(readme.contains("`docs/tasks/`"), "README should reference docs/tasks/")
}
```

- [ ] **Step 2: Test laufen lassen, FAIL erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.ExportControllerTest.README references new docs paths" --quiet
```

Expected: `FAIL` — README listet noch `SPEC.md`, `PLAN.md`, `decisions/` ohne `docs/`-Präfix.

- [ ] **Step 3: `generateReadme` aktualisieren**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`, Zeile 93–98:

```kotlin
// alt
appendLine("## Structure")
appendLine("- `SPEC.md` — Full product specification")
appendLine("- `PLAN.md` — Implementation plan with tasks")
appendLine("- `decisions/` — Key product decisions")
appendLine("- `clarifications/` — Clarified requirements")
appendLine("- `tasks/` — Individual task files")

// neu
appendLine("## Structure")
appendLine("- `docs/SPEC.md` — Full product specification")
appendLine("- `docs/PLAN.md` — Implementation plan with tasks")
appendLine("- `docs/decisions/` — Key product decisions")
appendLine("- `docs/clarifications/` — Clarified requirements")
appendLine("- `docs/tasks/` — Individual task files")
appendLine("- `docs/uploads/` — Original uploaded documents")
```

- [ ] **Step 4: Test laufen lassen, PASS erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.ExportControllerTest.README references new docs paths" --quiet
```

Expected: `PASS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt
git commit -m "refactor(export): update README structure references to new docs/ layout"
```

---

## Task 8: `includeDocuments`-Flag entfernen

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt:7`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt` (Zeilen 12–17 Constructor + Zeilen 76–81 Block + ggf. Import)
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt` (zwei Tests anpassen/entfernen)
- Modify: `frontend/src/lib/api.ts` (Zeilen 432, 442)

- [ ] **Step 1: Failing-Test umschreiben — neue Erwartung an Upload-Pfad im ZIP**

Edit `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`:

Test `POST export with includeDocuments=true bundles uploads folder` (Zeile 125–142) wird durch:

```kotlin
@Test
fun `POST export bundles uploads under docs-uploads`() {
    val pid = createProject()
    uploadStorage.save(pid, "d1", "a.pdf", "application/pdf", byteArrayOf(1, 2, 3), "2026-04-27T10:00:00Z")

    val result = mockMvc.perform(post("/api/v1/projects/$pid/export"))
        .andExpect(status().isOk()).andReturn()

    val entries = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zis ->
        var e = zis.nextEntry
        while (e != null) { entries.add(e.name); e = zis.nextEntry }
    }
    assertTrue(entries.any { it.endsWith("/docs/uploads/a.pdf") },
        "ZIP should contain docs/uploads/a.pdf, got: $entries")
    assertTrue(entries.none { it.endsWith(".index.json") },
        "ZIP must not contain .index.json, got: $entries")
    assertTrue(entries.none { it.matches(Regex(".*/[^/]*/uploads/a\\.pdf$")) && !it.contains("/docs/") },
        "ZIP must not contain top-level uploads/a.pdf, got: $entries")
}
```

ersetzt. Test `POST export with includeDocuments=false skips uploads` (Zeile 144–160) **komplett löschen** — der Flag existiert nicht mehr.

- [ ] **Step 2: Test laufen lassen, FAIL erwarten**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.ExportControllerTest.POST export bundles uploads under docs-uploads" --quiet
```

Expected: `FAIL` — `addBinaryEntry` schreibt `.index.json` als Eintrag (sie wird mit-walked von `listDocsFiles`). Wir filtern sie im nächsten Step im Service. ABER: tatsächlich wird `.index.json` aktuell mit-walked nicht? Doch — `listDocsFiles` filtert nur `Files.isRegularFile`, nicht den Filename. Wir müssen `.index.json` ausschliessen.

> **Hinweis:** Diese Erkenntnis bedeutet eine kleine zusätzliche Anpassung in Step 4 unten — wir filtern `.index.json` in `listDocsFiles` heraus (oder alternativ in `ExportService`, aber im Storage gehört es semantisch hin).

- [ ] **Step 3: `ExportService` — `includeDocuments`-Block + `UploadStorage`-Dependency entfernen** *(zuerst, damit kein Compile-Fehler entsteht, wenn das Feld in Step 4 verschwindet)*

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`:

Konstruktor (Zeilen 12–18):
```kotlin
// alt
class ExportService(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService,
    private val uploadStorage: UploadStorage
) {

// neu
class ExportService(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService
) {
```

Import-Zeile 5 entfernen:
```kotlin
// alt
import com.agentwork.productspecagent.storage.UploadStorage
// neu
(Zeile gelöscht)
```

Block Zeilen 76–81 entfernen:
```kotlin
// alt
// Documents (uploads)
if (request.includeDocuments) {
    for (filename in uploadStorage.list(projectId)) {
        zip.addBinaryEntry("$prefix/uploads/$filename", uploadStorage.read(projectId, filename))
    }
}

// neu
(Block komplett gelöscht — Uploads kommen nun via listDocsFiles unter docs/uploads/)
```

- [ ] **Step 4: `ExportRequest.includeDocuments` aus Domain-Model entfernen**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt`:

```kotlin
// alt
data class ExportRequest(
    val includeDecisions: Boolean = true,
    val includeClarifications: Boolean = true,
    val includeTasks: Boolean = true,
    val includeDocuments: Boolean = true
)

// neu
data class ExportRequest(
    val includeDecisions: Boolean = true,
    val includeClarifications: Boolean = true,
    val includeTasks: Boolean = true
)
```

- [ ] **Step 5: `.index.json` im Storage-Walk filtern**

Edit `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt`, Methode `listDocsFiles`:

```kotlin
// alt (nach Task 5)
return Files.walk(docs).use { stream ->
    stream.filter { Files.isRegularFile(it) }.toList()
}.map { file ->
    val rel = projectRoot.relativize(file).toString().replace('\\', '/')
    rel to Files.readAllBytes(file)
}

// neu
return Files.walk(docs).use { stream ->
    stream.filter { Files.isRegularFile(it) }
        .filter { it.fileName.toString() != ".index.json" }  // UploadStorage interne Index-Datei
        .toList()
}.map { file ->
    val rel = projectRoot.relativize(file).toString().replace('\\', '/')
    rel to Files.readAllBytes(file)
}
```

- [ ] **Step 6: Frontend `api.ts` — `includeDocuments` entfernen**

Edit `frontend/src/lib/api.ts`, Zeilen 426–447:

```typescript
// alt
export async function exportProject(
  projectId: string,
  options: {
    includeDecisions?: boolean;
    includeClarifications?: boolean;
    includeTasks?: boolean;
    includeDocuments?: boolean;
  } = {}
): Promise<Blob> {
  const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      includeDecisions: options.includeDecisions ?? true,
      includeClarifications: options.includeClarifications ?? true,
      includeTasks: options.includeTasks ?? true,
      includeDocuments: options.includeDocuments ?? true,
    }),
  });

// neu
export async function exportProject(
  projectId: string,
  options: {
    includeDecisions?: boolean;
    includeClarifications?: boolean;
    includeTasks?: boolean;
  } = {}
): Promise<Blob> {
  const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      includeDecisions: options.includeDecisions ?? true,
      includeClarifications: options.includeClarifications ?? true,
      includeTasks: options.includeTasks ?? true,
    }),
  });
```

- [ ] **Step 7: Frontend-Konsumenten von `exportProject` prüfen**

```bash
grep -rn "exportProject\|includeDocuments" frontend/src --include='*.ts' --include='*.tsx'
```

Falls ein Aufrufer `includeDocuments` setzt, dort entfernen. Mit hoher Wahrscheinlichkeit gibt es keine Aufrufer, die diese Option explizit übergeben — der Default war `true` und nirgendwo überschrieben.

- [ ] **Step 8: Frontend Lint + Build**

```bash
cd frontend && npm run lint --silent && npm run build --silent
```

Expected: keine Type-Errors. Falls TypeScript am Aufruf-Site `includeDocuments` als Excess-Property meldet, dort entfernen.

- [ ] **Step 9: Backend full test suite**

```bash
cd backend && ./gradlew test --quiet
```

Expected: alle Tests grün.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/ExportModels.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt \
        frontend/src/lib/api.ts
git commit -m "refactor(export): remove includeDocuments flag, uploads always bundle via docs/"
```

---

## Task 9: End-to-End-Smoketest

**Goal:** Sicherstellen, dass ein neu angelegtes Projekt mit Decisions/Clarifications/Tasks/Uploads die korrekte FS-Struktur erzeugt und das Handoff-ZIP die erwartete Struktur hat.

- [ ] **Step 1: Backend starten**

```bash
cd backend && ./gradlew bootRun --quiet &
echo $! > /tmp/backend.pid
# Warte bis Health antwortet
until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do sleep 1; done
echo "Backend up"
```

> **Hinweis:** Falls das Backend nicht auf 8081 läuft (siehe `application.yml`), Port anpassen.

- [ ] **Step 2: Projekt anlegen, FS-Struktur prüfen**

```bash
PID=$(curl -sf -X POST http://localhost:8081/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"Smoke Test"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "Project: $PID"

ls -la "data/projects/$PID/"
ls -la "data/projects/$PID/docs/"
```

Expected: `data/projects/$PID/` enthält `project.json`, `flow-state.json`, `wizard.json`, `spec/`, `docs/` — **keine** Top-Level `tasks/`, `decisions/`, `clarifications/`, `uploads/` mehr.

- [ ] **Step 3: Upload schreiben + Pfad verifizieren**

```bash
echo "test content" > /tmp/test.txt
curl -sf -X POST "http://localhost:8081/api/v1/projects/$PID/uploads" \
  -F "file=@/tmp/test.txt;type=text/plain"

ls "data/projects/$PID/docs/uploads/"
```

Expected: `test.txt` (oder ähnlicher Sanitize-Name) liegt unter `docs/uploads/`.

- [ ] **Step 4: Handoff-ZIP exportieren + Struktur prüfen**

```bash
curl -sf -X POST "http://localhost:8081/api/v1/projects/$PID/export" \
  -H "Content-Type: application/json" \
  -d '{}' \
  --output /tmp/export.zip

unzip -l /tmp/export.zip
```

Expected:
- `{prefix}/README.md` am Root
- `{prefix}/docs/SPEC.md`
- `{prefix}/docs/uploads/test.txt`
- **keine** Einträge unter `{prefix}/SPEC.md`, `{prefix}/PLAN.md` oder `{prefix}/uploads/` am Root

- [ ] **Step 5: Backend stoppen**

```bash
kill $(cat /tmp/backend.pid)
```

- [ ] **Step 6: Cleanup-Commit (falls Smoke-Test Test-Daten hinterlassen hat)**

Smoke-Test sollte nichts ins Git pushen. Falls das Backend `data/projects/$PID/` erzeugt hat, ist das gitignored (Projekt-Konvention) — kein Commit nötig. Bei Unsicherheit:

```bash
git status
# Alle Änderungen sollten in vorhergehenden Tasks committet sein
```

---

## Self-Review Notes

- **Spec coverage:** Alle Anforderungen aus dem Spec abgedeckt — vier FS-Pfade (Tasks 1–4), `listDocsFiles` ByteArray (Task 5), ExportService ZIP-Pfade (Task 6), README content (Task 7), `includeDocuments` removal (Task 8), Verifikation (Task 9).
- **Type consistency:** `listDocsFiles` Signatur in ProjectStorage und ProjectService synchron auf `List<Pair<String, ByteArray>>`. ExportService Caller nutzt `addBinaryEntry`.
- **Test design:** Failing-Test-First für jeden Pfad-Wechsel. Storage-Tests verifizieren genau das Filesystem-Layout — der wichtigste Verhaltenspunkt der Refaktorierung.
- **Edge cases:**
  - `.index.json` von UploadStorage filtern (in Task 8 Step 5 ergänzt) — sonst landet sie im ZIP.
  - Migration alter Projekte: bewusst ausgelassen, Spec-Begründung (`data/` nur `.gitkeep`).
- **Frontend:** Minimal-invasiv — nur `includeDocuments`-Feld aus Type + JSON-Body. Lint+Build als Gate.
