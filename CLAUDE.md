# CLAUDE.md

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

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

Respond in German. Keep code identifiers, commit messages, and technical terms in English.

## Project

Product-Spec-Agent: A wizard-based app that guides product owners from a rough idea to a complete, structured product specification. Uses AI agents (JetBrains Koog framework with OpenAI models) to analyze input, generate decisions, surface clarifications, and produce exportable specs.

## Tech Stack

- **Backend**: Kotlin 2.3, Spring Boot 4, Java 21, Gradle, JetBrains Koog 0.7.3
- **Frontend**: Next.js 16, React 19, TypeScript, Tailwind CSS 4, shadcn/ui, Rete.js, Zustand
- **Persistence**: Filesystem (JSON + Markdown in `data/projects/{id}/`), no database
- **Deployment**: Docker Compose (backend:8080, frontend:3000)

## Commands

### Backend (run from `backend/`)
```bash
./gradlew bootRun --quiet          # Start backend (port 8080)
./gradlew test                     # Run all tests
./gradlew test --tests "com.agentwork.productspecagent.export.DocsScaffoldGeneratorTest"  # Single test class
./gradlew test --tests "*.DocsScaffoldGeneratorTest.generates feature overview"           # Single test method
./gradlew bootJar                  # Build JAR
```

### Frontend (run from `frontend/`)
```bash
npm run dev       # Dev server (port 3000)
npm run build     # Production build
npm run lint      # ESLint
```

### Both services
```bash
./start.sh                # Run backend + frontend in parallel
docker-compose up         # Docker (needs OPENAI_API_KEY env var)
```

## Architecture

### 9-Step Wizard Flow
Projects progress through: `IDEA → PROBLEM → TARGET_AUDIENCE → SCOPE → MVP → FEATURES → ARCHITECTURE → BACKEND → FRONTEND`. Each step has a status (`OPEN`, `IN_PROGRESS`, `COMPLETED`) tracked in `flow-state.json`. Steps can be dynamically shown/hidden based on project category.

### Agent Marker Protocol
`IdeaToSpecAgent` parses special markers from AI responses to trigger side effects:
- `[STEP_COMPLETE]` — advances flow state, saves spec file
- `[DECISION_NEEDED]: title` — creates a Decision entity via `DecisionAgent`
- `[CLARIFICATION_NEEDED]: question | why` — creates a Clarification entity

Markers must appear on their own line, no markdown formatting. This is the core mechanism that drives wizard progression.

### Persistence (No DB)
All data lives under `data/projects/{project-id}/`:
- `project.json`, `flow-state.json`, `wizard.json` — project metadata
- `spec/` — Markdown files per wizard step (idea.md, problem.md, etc.)
- `decisions/`, `clarifications/`, `tasks/` — JSON entities
- `docs/` — Auto-generated documentation (Mustache templates, regenerated on every spec save)

Storage classes (`ProjectStorage`, `DecisionStorage`, etc.) use `java.nio.file` directly. Tests use `@TempDir` for isolation.

### Docs Scaffold System
`ScaffoldContextBuilder` assembles a `ScaffoldContext` from spec files + tasks (EPICs) + decisions. `DocsScaffoldGenerator` renders Mustache templates into `docs/features/`, `docs/architecture/`, `docs/backend/`, `docs/frontend/`. Docs regenerate automatically on every `saveSpecFile()` call.

### Backend Layers
- `api/` — REST controllers (`/api/v1/projects/...`)
- `agent/` — Koog agents (`IdeaToSpecAgent`, `DecisionAgent`, `ClarificationAgent`, `PlanGeneratorAgent`)
- `domain/` — Data classes (kotlinx.serialization)
- `storage/` — Filesystem persistence
- `export/` — Scaffold generation, ZIP export, handoff
- `config/` — CORS, Security, Jackson

### Frontend Patterns
- API client: `lib/api.ts` — fetch-based `apiFetch<T>()` wrapper, all types defined here
- State: Zustand stores in `lib/stores/` (project, wizard, decision, clarification, task)
- Components: `components/ui/` (shadcn), `components/wizard/`, `components/chat/`

## Key Conventions

- Use allway superpowers for new features and code example research with context7.
- Feature specs go in `docs/features/NN-feature-name.md` (German, numbered, kebab-case)
- Design specs go in `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`
- Implementation plans go in `docs/superpowers/plans/YYYY-MM-DD-<feature-name>.md`
- Always write the feature doc BEFORE implementing code
- `@Lazy` is used on `ScaffoldContextBuilder` injection in `ProjectService` to break a circular dependency


