# Design-Bundle-Docs-Relocation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Design-Bundle storage from `data/projects/{id}/design/files/` to `data/projects/{id}/docs/design/` (flat layout), drop the persisted `bundle.zip`, and remove the explicit Design-Bundle block from `ExportService` so the existing `docs/`-iteration handles export.

**Architecture:** Two surgical Kotlin files change in `main/`: `DesignBundleStorage` (rename internal path constants, drop ZIP write) and `ExportService` (remove explicit design block + constructor injection). URL pattern, controller, frontend, agent, and domain models are untouched. Three test files update: `DesignBundleStorageTest` (path asserts + new ZIP-not-persisted test), `ExportServiceDesignBundleTest` (path asserts + new not-duplicated test), `ProjectServiceTest` (new test verifying scaffold-regen does not wipe `docs/design/`).

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, AssertJ. Tests use `InMemoryObjectStore` (already provided in test sources).

**Spec:** [`docs/superpowers/specs/2026-05-04-design-bundle-docs-relocation-design.md`](../specs/2026-05-04-design-bundle-docs-relocation-design.md)

**Reference Bundle:** `examples/Scheduler.zip` (436 KB, 14 files, 5 design pages).

---

## File Structure

### Backend — Modify

| File | Change |
|---|---|
| `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignBundleStorage.kt` | Rename `filesPrefix`/`manifestKey` to point under `docs/design/`. Drop `zipKey` and the `objectStore.put(zipKey(...), ...)` call in `save()`. Simplify `delete()` to a single `deleteFiles(...)` call (manifest is under the prefix now). |
| `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt` | Remove `designBundleStorage` constructor parameter, related imports, and the explicit Design-Bundle block (today lines 86-97). |

### Backend — Test (modify)

| File | Change |
|---|---|
| `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignBundleStorageTest.kt` | Add `save does not persist bundle zip` test. Add `save writes manifest and files under docs/design/ prefix` test. The existing tests pass unchanged because they only exercise public API (`save/get/readFile/delete`) which keeps the same contract. |
| `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportServiceDesignBundleTest.kt` | Update existing test's assertions: `design/manifest.json` → `docs/design/manifest.json`, `design/files/Scheduler.html` → `docs/design/Scheduler.html`. Add `export does not duplicate design files under prefix/design/` test. |
| `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt` | Add `regenerateDocsScaffold preserves docs/design/ contents` test. |

### Backend — No Change

`api/DesignBundleController.kt`, `service/DesignBundleExtractor.kt`, `agent/DesignSummaryAgent.kt`, `domain/DesignBundle.kt`, `config/DesignBundleProperties.kt`, `service/ProjectService.kt`, all frontend files.

---

## Task 1: DesignBundleStorage path move + drop ZIP

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignBundleStorageTest.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignBundleStorage.kt`

- [ ] **Step 1.1: Add the new failing tests to `DesignBundleStorageTest.kt`**

Open `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignBundleStorageTest.kt`. The existing fixture creates the storage via `InMemoryObjectStore`. We need to extend the fixture so individual tests can inspect the underlying `InMemoryObjectStore` keys directly.

Replace the existing `newStorage(...)` helper with:

```kotlin
    private fun newStorageWithStore(): Pair<DesignBundleStorage, InMemoryObjectStore> {
        val store = InMemoryObjectStore()
        val extractor = DesignBundleExtractor(DesignBundleProperties())
        return DesignBundleStorage(store, extractor) to store
    }

    private fun newStorage(@Suppress("UNUSED_PARAMETER") tmp: Path? = null): DesignBundleStorage =
        newStorageWithStore().first
```

Then append two new test methods to the class (anywhere inside the class body, before the closing `}`):

```kotlin
    @Test
    fun `save does not persist the original bundle zip`() {
        val (storage, store) = newStorageWithStore()
        storage.save("proj-zip", "Scheduler.zip", schedulerZip)

        val keys = store.listKeys("projects/proj-zip/")
        assertThat(keys).isNotEmpty
        assertThat(keys).noneMatch { it.endsWith("bundle.zip") }
    }

    @Test
    fun `save writes manifest and files under docs design prefix`() {
        val (storage, store) = newStorageWithStore()
        storage.save("proj-layout", "Scheduler.zip", schedulerZip)

        val keys = store.listKeys("projects/proj-layout/")
        assertThat(keys).contains("projects/proj-layout/docs/design/manifest.json")
        assertThat(keys).contains("projects/proj-layout/docs/design/Scheduler.html")
        assertThat(keys).noneMatch { it.startsWith("projects/proj-layout/design/") }
    }
```

- [ ] **Step 1.2: Run the new tests to verify they FAIL**

Run from the repo root:

```bash
(cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.DesignBundleStorageTest")
```

Expected: BUILD FAILED.
- `save does not persist the original bundle zip` fails because the current `save()` writes `projects/{id}/design/bundle.zip`.
- `save writes manifest and files under docs design prefix` fails because the current `save()` writes under `projects/{id}/design/files/...` and `projects/{id}/design/manifest.json`.

If the build is green here, the tests are wrong — re-read Step 1.1.

- [ ] **Step 1.3: Modify `DesignBundleStorage.kt`**

Open `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignBundleStorage.kt`.

Replace the three private path-helper functions:

```kotlin
    private fun designPrefix(projectId: String) = "projects/$projectId/design/"
    private fun zipKey(projectId: String) = "${designPrefix(projectId)}bundle.zip"
    private fun manifestKey(projectId: String) = "${designPrefix(projectId)}manifest.json"
    private fun filesPrefix(projectId: String) = "${designPrefix(projectId)}files/"
```

with:

```kotlin
    private fun filesPrefix(projectId: String) = "projects/$projectId/docs/design/"
    private fun manifestKey(projectId: String) = "${filesPrefix(projectId)}manifest.json"
```

Then in `save(...)`, **delete** the line that persists the original ZIP. Find:

```kotlin
        // Write original ZIP
        objectStore.put(zipKey(projectId), zipBytes, "application/zip")
```

and remove both lines (the comment and the `put` call).

Then in `delete(...)`, replace the body:

```kotlin
    open fun delete(projectId: String) {
        deleteFiles(projectId)
        objectStore.delete(zipKey(projectId))
        objectStore.delete(manifestKey(projectId))
        // design.md cleanup is delegated to caller (ProjectService) — keep storage focused.
    }
```

with:

```kotlin
    open fun delete(projectId: String) {
        // deleteFiles wipes the whole docs/design/ prefix, including manifest.json.
        // spec/design.md cleanup is delegated to caller (ProjectService) — keep storage focused.
        deleteFiles(projectId)
    }
```

Verify the resulting class compiles by reading through it: `zipKey` and `designPrefix` should no longer be referenced anywhere in the file. The `fileKey(projectId, relPath)` helper still resolves correctly because it uses `filesPrefix(projectId)` which now points under `docs/design/`.

- [ ] **Step 1.4: Run all `DesignBundleStorageTest` tests to verify they PASS**

```bash
(cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.DesignBundleStorageTest")
```

Expected: BUILD SUCCESSFUL, 7 tests pass (5 existing + 2 new).

If `save replaces existing bundle atomically` fails, check that `deleteFiles(projectId)` still wipes the entire `docs/design/` prefix on every save (including the previous manifest). If `readFile rejects path traversal` fails, the path-normalization assertion in `readFile` is unchanged and should still work — re-read your changes.

- [ ] **Step 1.5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignBundleStorage.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignBundleStorageTest.kt
git commit -m "$(cat <<'EOF'
refactor(design-bundle): store extracted files under docs/design/

DesignBundleStorage now writes manifest.json and extracted files under
projects/{id}/docs/design/ instead of projects/{id}/design/files/, and
no longer persists the original upload ZIP. The single-prefix layout
lets delete() collapse to one deleteFiles() call.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: ExportService — remove explicit design block

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportServiceDesignBundleTest.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`

- [ ] **Step 2.1: Update the existing test assertions and add the not-duplicated test**

Open `backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportServiceDesignBundleTest.kt`.

Replace the existing test method `export includes design manifest and files when bundle is uploaded` body with:

```kotlin
    @Test
    fun `export includes design manifest and files under docs design`() {
        val projectId = createProject()
        val schedulerZip = java.io.File("../examples/Scheduler.zip").readBytes()

        designBundleStorage.save(projectId, "Scheduler.zip", schedulerZip)

        val zipBytes = exportService.exportProject(projectId)
        val entries = zipEntries(zipBytes)

        val entryNames = entries.keys.toList()
        assertTrue(
            entryNames.any { it.endsWith("docs/design/manifest.json") },
            "ZIP should contain docs/design/manifest.json, got: $entryNames"
        )
        assertTrue(
            entryNames.any { it.endsWith("docs/design/Scheduler.html") },
            "ZIP should contain docs/design/Scheduler.html, got: $entryNames"
        )
    }

    @Test
    fun `export does not duplicate design files under legacy design prefix`() {
        val projectId = createProject()
        val schedulerZip = java.io.File("../examples/Scheduler.zip").readBytes()

        designBundleStorage.save(projectId, "Scheduler.zip", schedulerZip)

        val zipBytes = exportService.exportProject(projectId)
        val entries = zipEntries(zipBytes)

        // Compute the prefix that ExportService applies (slugified project name).
        // The project name "Export Design Test" slugifies to "export-design-test".
        val legacyPrefixedEntries = entries.keys.filter {
            it.matches(Regex("[^/]+/design/.*"))
        }
        assertTrue(
            legacyPrefixedEntries.isEmpty(),
            "No entry should live under <prefix>/design/ (legacy layout). Got: $legacyPrefixedEntries"
        )
    }
```

- [ ] **Step 2.2: Run the design-export tests to verify they FAIL**

```bash
(cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.ExportServiceDesignBundleTest")
```

Expected: BUILD FAILED.
- `export includes design manifest and files under docs design` fails because — even after Task 1 — `ExportService` still uses its explicit block to write under `prefix/design/manifest.json` and `prefix/design/files/...`. The `docs/`-iteration in `ExportService` would also pick up the new `docs/design/...` files (good), so `endsWith("docs/design/manifest.json")` should actually pass after Task 1. **But** `export does not duplicate ...` fails because the explicit block is still there too.

If the second test passes here, the explicit block was already removed — which would mean Task 1 went too far. Re-read your Task 1 changes.

- [ ] **Step 2.3: Modify `ExportService.kt` — remove explicit design block**

Open `backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt`.

**Change A — constructor:** remove `designBundleStorage` from the parameter list.

Find:

```kotlin
class ExportService(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService,
    private val wizardService: WizardService,
    private val assetBundleExporter: AssetBundleExporter,
    private val designBundleStorage: DesignBundleStorage,
) {
```

Replace with:

```kotlin
class ExportService(
    private val projectService: ProjectService,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val taskService: TaskService,
    private val wizardService: WizardService,
    private val assetBundleExporter: AssetBundleExporter,
) {
```

**Change B — explicit design block:** delete the entire block (today lines 85-97):

```kotlin
            // Design bundle: manifest.json + files/
            val bundle = designBundleStorage.get(projectId)
            if (bundle != null) {
                val manifestJson = json.encodeToString(DesignBundle.serializer(), bundle)
                zip.addEntry("$prefix/design/manifest.json", manifestJson)

                for (file in bundle.files) {
                    val bytes = runCatching { designBundleStorage.readFile(projectId, file.path) }.getOrNull()
                    if (bytes != null) {
                        zip.addBinaryEntry("$prefix/design/files/${file.path}", bytes)
                    }
                }
            }

```

Just remove all those lines (including the leading comment and the trailing blank line — keep formatting clean).

**Change C — imports:** at the top of the file, remove the now-unused imports:

```kotlin
import com.agentwork.productspecagent.storage.DesignBundleStorage
```

The `com.agentwork.productspecagent.domain.*` import stays — `DesignBundle` was the only thing it provided that used to be referenced here, but other domain types (`Project`, `FlowState`, `Decision`, `Clarification`, `SpecTask`, `TaskType`, `FlowStepStatus`, `DecisionStatus`) are still in use throughout the file. Do **not** narrow the wildcard import.

The `private val json = kotlinx.serialization.json.Json { prettyPrint = true }` line at the top of `exportProject` is no longer used (it was only for the manifest serialization). Remove it too:

```kotlin
    private val json = kotlinx.serialization.json.Json { prettyPrint = true }
```

(This is a top-of-class field, not inside `exportProject`. Verify it's not referenced anywhere else in the file before deleting.)

- [ ] **Step 2.4: Run the design-export tests to verify PASS**

```bash
(cd backend && ./gradlew test --tests "com.agentwork.productspecagent.export.ExportServiceDesignBundleTest")
```

Expected: BUILD SUCCESSFUL, 2 tests pass.

If the test fails because Spring can't construct `ExportService` due to mismatched DI, double-check that no `@Autowired lateinit var designBundleStorage: DesignBundleStorage` was relied on elsewhere. The test class itself still autowires `designBundleStorage` directly (line 16), which is fine — that field is used to seed the upload before the export.

- [ ] **Step 2.5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/export/ExportService.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/export/ExportServiceDesignBundleTest.kt
git commit -m "$(cat <<'EOF'
refactor(export): drop explicit design-bundle block in ExportService

The generic docs/-iteration already covers projects/{id}/docs/design/
files now that DesignBundleStorage writes them there. Removing the
explicit block prevents duplicate ZIP entries under <prefix>/design/.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Scaffold-regen does not wipe docs/design/

**Files:**
- Modify: `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`

- [ ] **Step 3.1: Add the regression test**

Open `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`.

Append a new `@Test` method to the class (immediately after the `regenerateDocsScaffold leaves non-docs project files untouched` test, before the closing `}`):

```kotlin
    @Test
    fun `regenerateDocsScaffold preserves docs design contents`() {
        // Convention: DocsScaffoldGenerator never emits under docs/design/, so the
        // diff-sync's managedDirs computation never includes that path, so user-uploaded
        // design-bundle files survive every spec-save / regen.
        val syncingService = ProjectService(storage, DocsScaffoldGenerator())
        val response = syncingService.createProject("Design Preserve Test")
        val projectId = response.project.id

        // Simulate a design bundle written by DesignBundleStorage.
        storage.saveDocsFile(projectId, "docs/design/manifest.json", """{"projectId":"x"}""")
        storage.saveDocsFile(projectId, "docs/design/Scheduler.html", "<html/>")
        storage.saveDocsFile(projectId, "docs/design/design-canvas.jsx", "// canvas")

        syncingService.regenerateDocsScaffold(projectId)

        val paths = listDocsRelativePaths(projectId)
        assertTrue("docs/design/manifest.json" in paths, "manifest was deleted")
        assertTrue("docs/design/Scheduler.html" in paths, "html was deleted")
        assertTrue("docs/design/design-canvas.jsx" in paths, "jsx was deleted")
    }
```

- [ ] **Step 3.2: Run the new test to verify PASS**

```bash
(cd backend && ./gradlew test --tests "com.agentwork.productspecagent.service.ProjectServiceTest.regenerateDocsScaffold preserves docs design contents")
```

Expected: BUILD SUCCESSFUL, 1 test passes.

This test should pass on the first run — it documents existing behavior (the diff-sync already protects `docs/design/` by construction, because the scaffold-generator never emits there). The test exists as a regression guard against a future scaffold-generator change that would accidentally start "managing" `docs/design/`.

If the test fails, something is wrong with the convention assumption — re-read `ProjectService.regenerateDocsScaffold` (`backend/.../service/ProjectService.kt:67-104`) and `DocsScaffoldGenerator.generate` (`backend/.../export/DocsScaffoldGenerator.kt:45-76`).

- [ ] **Step 3.3: Commit**

```bash
git add backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt
git commit -m "$(cat <<'EOF'
test(docs-scaffold): regression guard for docs/design/ preservation

Pins the convention that DocsScaffoldGenerator never emits under
docs/design/, so the diff-sync's managedDirs never includes the path
and user-uploaded design-bundle files survive spec-save regen cycles.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Full backend test suite green

**Files:** none modified — verification only.

- [ ] **Step 4.1: Run the full backend test suite**

```bash
(cd backend && ./gradlew test)
```

Expected: BUILD SUCCESSFUL. All tests pass.

If unexpected failures appear in unrelated test classes:
- Check that you did not accidentally break the `ExportService` constructor signature for any other test (search: `grep -rn "ExportService(" backend/src/test`).
- Check that no other test asserts on `projects/{id}/design/files/...` paths (search: `grep -rn "design/files" backend/src/test`).

If any call site of `ExportService` constructor in non-test code (other than Spring DI) breaks, fix the call site to drop the now-removed `designBundleStorage` argument.

- [ ] **Step 4.2: Sanity-check the orphan reference search**

```bash
grep -rn "design/files\|bundle\.zip" backend/src
```

Expected output should only contain:
- Test files in `backend/src/test/kotlin/.../api/AssetBundleControllerTest.kt` (uses `bundle.zip` as a *multipart filename* literal — unrelated to the design bundle).
- Possibly the `frontendOrigin` / `entryUrl` strings in `DesignBundleController.kt` and `DesignBundleControllerTest.kt` — these reference the **URL** path `/design/files/`, which we kept on purpose. That is fine.

If the grep returns any reference to a *storage path* `design/files/` or to a written `bundle.zip` outside what's listed above, hunt it down and remove it.

---

## Task 5: Manual browser smoke test

**Files:** none modified — runtime verification only.

- [ ] **Step 5.1: Reset dev test data**

```bash
rm -rf data/projects
```

Why: any leftover bundles from before this refactor live under the old `data/projects/{id}/design/` layout, which the new code never reads. Cleanest start.

- [ ] **Step 5.2: Start backend + frontend**

If `./start.sh` is not already running:

```bash
./start.sh
```

Wait until both servers report ready (backend on :8080, frontend on :3000 — note `frontend/CLAUDE.md` says port 3001 but the actual default in `package.json` is 3000; check the terminal output for the actual URL).

- [ ] **Step 5.3: Walk the smoke-test path in the browser**

Open the frontend URL printed by `start.sh`. For each of the seven steps below, watch backend logs in the other terminal for any errors.

1. Create a new SaaS project. Click through wizard until MVP is complete.
2. On the DESIGN step, drag-drop `examples/Scheduler.zip` (or use the file picker). Verify the iframe renders with all 5 artboards.
3. Open the Spec-File-Explorer (right-side tab). Navigate to `docs/design/`. Verify you see `Scheduler.html`, `design-canvas.jsx`, and other extracted assets in the tree. Verify `manifest.json` is **not** visible by default (it should appear only when the Debug toggle is on).
4. Click "Weiter" to complete the DESIGN step. Verify the chat shows the agent's confirmation, the flow advances to ARCHITECTURE, and `data/projects/{id}/spec/design.md` exists on disk (use `ls`).
5. Trigger the Project-Export (Export-Tab in the UI). Save the ZIP locally and inspect:
   ```bash
   unzip -l ~/Downloads/<exported-zip-name>.zip | grep -E 'design'
   ```
   Expected: entries under `<prefix>/docs/design/...` (manifest.json, Scheduler.html, etc.). **No** entries under `<prefix>/design/`.
6. Back in the UI, delete the design bundle. Verify all `docs/design/` files disappear from the explorer, but `docs/features/`, `docs/architecture/`, etc. remain intact.
7. Replace-flow: upload the bundle again, then upload it a second time and confirm the replace dialog. Verify the iframe re-mounts and the explorer's `docs/design/` contents are fully replaced (no leftover orphans from the first upload).

- [ ] **Step 5.4: Verify on-disk layout matches the spec**

```bash
find data/projects -type d -name design
find data/projects -type f -name 'bundle.zip'
ls data/projects/*/docs/design/ 2>/dev/null
```

Expected:
- `find ... -name design` returns paths like `data/projects/{id}/docs/design` only — no `data/projects/{id}/design`.
- `find ... -name 'bundle.zip'` returns nothing.
- `ls data/projects/*/docs/design/` shows `manifest.json`, `Scheduler.html`, and the rest of the extracted files flat.

- [ ] **Step 5.5: Commit if any unstaged changes from the smoke test surfaced**

If the smoke test surfaced bug fixes, commit them. Otherwise nothing to commit.

```bash
git status
```

If clean, move on to Task 6.

---

## Task 6: Done-doc

**Files:**
- Create: `docs/features/40-design-bundle-step-relocation-done.md`

- [ ] **Step 6.1: Write the relocation done-doc**

Per the project's `implement-feature` workflow Step 7, every implemented refactor/feature gets a sibling `*-done.md` in `docs/features/`. The original Feature 40 done-doc stays as-is; this is a separate addendum because it's a follow-up refactor.

Create `docs/features/40-design-bundle-step-relocation-done.md` with:

```markdown
# Feature 40 Refactor — Design-Bundle nach docs/design/ — Done

**Datum:** 2026-05-04
**Branch:** main (direkt, kein Feature-Branch — kleiner Refactor)
**Spec:** `docs/superpowers/specs/2026-05-04-design-bundle-docs-relocation-design.md`
**Plan:** `docs/superpowers/plans/2026-05-04-design-bundle-docs-relocation.md`

## Was umgesetzt wurde

- `DesignBundleStorage` schreibt entpackte Files und Manifest unter `projects/{id}/docs/design/` statt `projects/{id}/design/files/` bzw. `projects/{id}/design/manifest.json`. Originaler ZIP-Upload wird nicht mehr persistiert.
- `delete()` collapsed auf einen einzelnen `deleteFiles(...)`-Call, weil Manifest jetzt unter dem gleichen Prefix lebt.
- `ExportService` Constructor verliert `designBundleStorage`; der explizite Design-Bundle-Block (Manifest + Files unter `prefix/design/...`) ist raus. Die generische `docs/`-Iteration übernimmt automatisch unter `prefix/docs/design/...`.
- Drei neue Tests:
  - `DesignBundleStorageTest.save does not persist the original bundle zip`
  - `DesignBundleStorageTest.save writes manifest and files under docs design prefix`
  - `ExportServiceDesignBundleTest.export does not duplicate design files under legacy design prefix`
  - `ProjectServiceTest.regenerateDocsScaffold preserves docs design contents` (Regression-Guard)

## Bewusste Abweichungen / Restpunkte

- Keine Migration für bestehende `data/projects/{id}/design/`-Verzeichnisse — Dev-User räumt selbst auf (per Spec).
- URL-Pattern `/api/v1/projects/{id}/design/files/**` blieb stabil — Frontend null Änderung. Das URL-Segment `files/` ist jetzt ein historisches Relikt ohne Disk-Entsprechung.
- Frontend manuell verifiziert (Browser-Smoke-Test, alle 7 Schritte aus Spec).

## Akzeptanzkriterien-Status

| # | Kriterium | Status |
|---|---|---|
| 1 | Manifest + Files unter `projects/{id}/docs/design/`, kein `files/`-Subdir | ✅ |
| 2 | Kein Key endet auf `bundle.zip` | ✅ (Test) |
| 3 | `projects/{id}/design/` nicht mehr beschrieben | ✅ |
| 4 | Iframe-URL stabil mit `nosniff` + CSP | ✅ |
| 5 | `regenerateDocsScaffold` löscht keine `docs/design/`-Datei | ✅ (Test) |
| 6 | Export-ZIP-Files unter `<prefix>/docs/design/`, nichts unter `<prefix>/design/` | ✅ (Test) |
| 7 | Explorer listet `docs/design/`-Files; `manifest.json` nur mit Debug-Toggle | ✅ (Browser-Smoke) |
| 8 | `delete()` räumt nur `docs/design/`, restliches `docs/` bleibt | ✅ (Browser-Smoke) |
| 9 | `./gradlew test` grün, neue Tests bestehen | ✅ |
| 10 | Manueller Browser-Smoke-Test erfolgreich | ✅ |
```

- [ ] **Step 6.2: Commit the done-doc**

```bash
git add docs/features/40-design-bundle-step-relocation-done.md
git commit -m "$(cat <<'EOF'
docs(feature-40): done-doc for design-bundle docs/ relocation

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review Notes

- **Spec coverage:** All 10 acceptance criteria from the spec are covered — criteria 1-3, 8 by Task 1 (storage); criterion 6 by Task 2 (export); criterion 5 by Task 3 (scaffold guard); criterion 4, 7-8 by Task 5 (browser smoke); criterion 9 by Task 4 (full suite); criterion 10 by Task 5.
- **Placeholder scan:** No "TBD" / "TODO" / vague-handwave steps. Every code change shows the exact replacement.
- **Type consistency:** `filesPrefix` / `manifestKey` / `fileKey` names stay. `DesignBundleStorage`'s public API (`save`, `get`, `readFile`, `delete`) is byte-identical. `ExportService` constructor change is propagated as the only call-site change (Spring DI — verified via Step 4.1).
- **Note on test ordering convention:** `DesignBundleStorageTest` uses `InMemoryObjectStore` directly (no Spring), so tests run fast and are independent. `ExportServiceDesignBundleTest` is `@SpringBootTest` (full context) — slower but already established as the convention.
- **`delete()` simplification rationale:** Old `delete()` had three calls (`deleteFiles`, `delete(zipKey)`, `delete(manifestKey)`). New `delete()` has one (`deleteFiles`), because all three legacy keys now live under the single `docs/design/` prefix. The trailing comment about `spec/design.md` is preserved — that file is the agent-generated spec, lives outside `docs/design/`, and remains the caller's responsibility.
