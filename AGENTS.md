# Guidelines

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

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

## 4. Goal-Driven Execution

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
---

# Repository Guidelines

## Project Structure & Module Organization

Product-Spec-Agent is split into backend, frontend, documentation, and reusable asset areas. Backend Kotlin/Spring Boot code lives in `backend/src/main/kotlin/com/agentwork/productspecagent`, organized by layer: `api/`, `service/`, `storage/`, `agent/`, `domain/`, `export/`, and `config/`. Tests mirror this under `backend/src/test/kotlin`.

The Next.js frontend lives in `frontend/src`, with routes in `app/`, shared UI in `components/`, stores and helpers in `lib/`, and Playwright tests in `frontend/e2e`. Product docs are in `docs/`; reusable agent assets are in `asset-bundles/`.

## Build, Test, and Development Commands

- `./start.sh` starts MinIO, backend `8080`, and frontend `3000`.
- `cd backend && ./gradlew bootRun --quiet` runs the backend locally.
- `cd backend && ./gradlew test` runs JUnit 5 backend tests.
- `cd backend && ./gradlew bootJar` builds the backend JAR.
- `cd frontend && npm run dev` starts the frontend dev server.
- `cd frontend && npm run build` creates a production Next.js build.
- `cd frontend && npm run lint` runs ESLint.
- `cd frontend && npm run test:e2e` runs Playwright end-to-end tests.
- `docker-compose up` runs backend plus MinIO; provide required secrets via `.env`.

## Framework Source Checks

When evaluating framework or package behavior, inspect upstream sources with the `opensrc` CLI.

## Coding Style & Naming Conventions

Use Kotlin 2.3, Java 21, Spring Boot 4, TypeScript, React 19, and Tailwind CSS 4. Keep backend classes in their current layers and prefer Kotlin data classes for domain models. Name Kotlin tests `SubjectTest.kt`; name React components in `PascalCase.tsx`; keep stores under `frontend/src/lib/stores/*-store.ts`. Match existing formatting: 4-space Kotlin indentation and 2-space TypeScript/TSX indentation.

## Testing Guidelines

Backend tests use JUnit 5, Spring test support, Mockito Kotlin, MockWebServer, and Testcontainers/MinIO where needed. Prefer temporary directories over shared `data/` state. Frontend behavior tests use Playwright specs in `frontend/e2e/*.spec.ts`. Add focused tests for changed behavior and run the narrowest relevant command first.

## Commit & Pull Request Guidelines

Recent history mostly follows Conventional Commits such as `fix(config): ...` and `chore: ...`; use that style with concise imperative summaries. Pull requests should describe the user-visible change, list validation commands, link related docs or issues, and include screenshots for UI changes.

## Security & Configuration Tips

Do not commit real secrets. Local development uses MinIO defaults in `start.sh`; deployed runs require `AUTH_JWT_SECRET` and S3 settings. Keep generated runtime data under `data/` out of review unless the change explicitly concerns fixtures or examples.
