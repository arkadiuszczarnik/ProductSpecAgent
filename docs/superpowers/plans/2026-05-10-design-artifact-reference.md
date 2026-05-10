# Design Artifact Reference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the generated DESIGN summary out of `spec/design.md` into `design/design.md` and make the final `spec/spec.md` reference it.

**Architecture:** `DesignWorkbenchStorage` owns design output files under `projects/{id}/design/`. The DESIGN controller writes the summary through this storage. `ProjectPackageAssembler` already exports active design output files, so it will include the summary from the same list. `WizardStepCompletionService` adds a deterministic instruction to the final spec prompt when the design artifact exists.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, MockMvc, in-memory object store tests.

---

### Task 1: Persist Design Summary Under `design/design.md`

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorage.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DesignWorkbenchStorageTest.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignWorkbenchControllerTest.kt`

- [ ] Add storage APIs `designSummaryKey(projectId)`, `writeDesignSummary(projectId, markdown)`, and `readDesignSummary(projectId)`.
- [ ] Extend `listActiveOutputFiles()` so completed design packages include `design/design.md` as well as `design/screens/design/index.html`.
- [ ] Change `DesignWorkbenchController.complete()` to call `designWorkbenchStorage.writeDesignSummary(projectId, summary)` instead of `projectService.saveSpecFile(projectId, "design.md", summary)`.
- [ ] Add tests proving `design/design.md` exists and `spec/design.md` does not.

### Task 2: Reference Design Artifact In Final Spec Generation

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`

- [ ] Inject `DesignWorkbenchStorage` into `WizardStepCompletionService`.
- [ ] When the final step generates `spec.md`, check `readDesignSummary(projectId)`.
- [ ] If present, append a mandatory instruction to the summary prompt to include a concise Design section linking `design/design.md` and active HTML preview.
- [ ] Add a test that captures the final summary prompt and verifies the design artifact instruction is present.

### Task 3: Export And Handoff Coverage

**Files:**
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/ExportControllerTest.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt`

- [ ] Update export tests to expect `design/design.md` when an active design exists.
- [ ] Update handoff tests to expect `design/design.md` when an active design exists.
- [ ] Run focused backend tests for storage, controller, wizard completion, export, and handoff.

### Task 4: Final Verification

**Files:**
- No production files.

- [ ] Run `cd backend && ./gradlew test`.
- [ ] Run `git diff --check`.
- [ ] Commit the implementation.

## Self-Review

- Spec coverage: all desired behaviors map to Tasks 1-3.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: all named methods live on `DesignWorkbenchStorage`; controller and completion service consume that storage directly.
