# Living-Sync via MCP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Feature 45 V1: external Coding Agents can report implementation progress, tests, token usage, code changes, and sync notes back into Product-Spec-Agent.

**Architecture:** Add append-only Living-Sync events to backend storage, summarize them through a service, expose them through REST and MCP-tool-compatible service methods, show the summary in the project workspace, and add MCP reporting instructions to handoff files. V1 keeps automatic Git import out of scope and stores all sync data under each project.

**Tech Stack:** Kotlin 2.3, Spring Boot 4 WebMVC, kotlinx serialization, existing ObjectStore abstraction, Next.js/React/TypeScript, existing shadcn-style components.

---

## File Structure

- Create `backend/src/main/kotlin/com/agentwork/productspecagent/domain/LivingSyncModels.kt` for event/request/summary DTOs.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/storage/LivingSyncStorage.kt` for append-only event persistence.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/LivingSyncService.kt` for validation, event creation, and summary aggregation.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/api/LivingSyncController.kt` for UI-readable REST endpoints and MCP-compatible report endpoints.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt` to include MCP reporting instructions.
- Modify `frontend/src/lib/api.ts` to add Living-Sync types and API functions.
- Create `frontend/src/components/living-sync/LivingSyncPanel.tsx`.
- Modify `frontend/src/app/projects/[id]/page.tsx` to add a `Living Sync` tab.
- Create tests in `backend/src/test/kotlin/com/agentwork/productspecagent/storage/LivingSyncStorageTest.kt`, `backend/src/test/kotlin/com/agentwork/productspecagent/service/LivingSyncServiceTest.kt`, and `backend/src/test/kotlin/com/agentwork/productspecagent/api/LivingSyncControllerTest.kt`.
- Create `docs/features/45-living-sync-mcp-done.md` after implementation.

## Tasks

### Task 1: Domain And Storage

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/LivingSyncModels.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/LivingSyncStorage.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/LivingSyncStorageTest.kt`

- [ ] Write failing storage tests for append/list events sorted by `createdAt`.
- [ ] Run `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.storage.LivingSyncStorageTest"` and confirm missing classes fail.
- [ ] Add serializable Living-Sync enums, request DTOs, event DTO, and summary DTOs.
- [ ] Add `LivingSyncStorage` using `ObjectStore` keys `projects/{projectId}/sync/events/{eventId}.json`.
- [ ] Re-run the storage test and confirm it passes.

### Task 2: Service Aggregation

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/LivingSyncService.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/LivingSyncServiceTest.kt`

- [ ] Write failing tests proving feature progress, test run, token usage, code changes, and sync note reports create events.
- [ ] Write failing tests proving summary aggregates latest feature status, test totals, token totals, changed files, and notes.
- [ ] Run `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.service.LivingSyncServiceTest"` and confirm missing service fails.
- [ ] Implement `LivingSyncService` with one report method per request type and `getSummary(projectId)`.
- [ ] Re-run the service test and confirm it passes.

### Task 3: REST/MCP-Compatible API

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/LivingSyncController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/LivingSyncControllerTest.kt`

- [ ] Write failing MockMvc tests for `GET /api/v1/projects/{projectId}/living-sync`.
- [ ] Write failing MockMvc tests for `POST /api/v1/projects/{projectId}/living-sync/mcp/report-feature-progress`, `/report-test-run`, `/report-token-usage`, `/report-code-changes`, and `/report-sync-note`.
- [ ] Run `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.api.LivingSyncControllerTest"` and confirm missing controller fails.
- [ ] Implement the controller with JSON request/response bodies using the service methods.
- [ ] Re-run the controller test and confirm it passes.

### Task 4: Handoff Instructions

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/export/HandoffServiceTest.kt`

- [ ] Add or extend tests proving `CLAUDE.md`/`AGENTS.md` mention Living-Sync reporting, project ID, and report tool names.
- [ ] Run the focused HandoffService test and confirm it fails.
- [ ] Add a Living-Sync section to generated handoff text.
- [ ] Re-run the focused HandoffService test and confirm it passes.

### Task 5: Frontend Panel

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/components/living-sync/LivingSyncPanel.tsx`
- Modify: `frontend/src/app/projects/[id]/page.tsx`

- [ ] Add TypeScript types and `getLivingSyncSummary(projectId)`.
- [ ] Add `LivingSyncPanel` that fetches the summary with an ignore cleanup flag and renders feature statuses, tests, token totals, files, and notes.
- [ ] Wire a `Living Sync` tab into the existing project workspace sidebar.
- [ ] Run `cd frontend && npm run lint`.

### Task 6: Done Doc And Verification

**Files:**
- Create: `docs/features/45-living-sync-mcp-done.md`
- Modify: `docs/architecture/rest-api.md`

- [ ] Document the new Living-Sync REST endpoints.
- [ ] Write the done doc with implemented scope, deviations, and open follow-ups.
- [ ] Run focused backend tests for Living-Sync plus `cd frontend && npm run lint`.
- [ ] Run `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.service.LivingSyncServiceTest" --tests "com.agentwork.productspecagent.storage.LivingSyncStorageTest" --tests "com.agentwork.productspecagent.api.LivingSyncControllerTest"`.

## Self-Review

- Spec coverage: V1 MCP-reporting concept is implemented through MCP-compatible report endpoints and service methods; automatic Git import remains documented as V2 option only.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: request, event, and summary names are centralized in `LivingSyncModels.kt`.
