# Spec Agent — UI Kit

A high-fidelity recreation of the production web app. Dark workspace, German-first chrome, lucide icons, base-ui-sourced components.

## Files

| File             | What                                                                                        |
| ---------------- | ------------------------------------------------------------------------------------------- |
| `index.html`     | Click-thru entry. Boot lands on Dashboard → click a project → wizard + right panel + tabs.   |
| `app.jsx`        | The shell + screen routing. Single source for the demo state machine.                       |
| `components.jsx` | Every component this kit ships. Exported to `window` so other Babel scripts can use them.   |

Load order in `index.html` is fixed: React → ReactDOM → Babel → `components.jsx` → `app.jsx`.

## What's covered

- **AppShell** — 56px left icon rail (logo + nav + footer Settings).
- **Dashboard** — page header + "New Project" CTA + project grid (`ProjectCard`).
- **WizardHeader** — title, breadcrumb, step pill, progress bar, primary CTA.
- **WizardStepper** — horizontal 8-step indicator.
- **WizardBody** — step-specific form (Idee / Problem / Features shown).
- **RightPanel** — resizable container + flat tab strip + per-tab content (Chat, Decisions, Clarifications, Tasks).
- **Chat** — `Bubble`, `MessageInput`, "Agent is thinking" loader.
- **DecisionList** — decision cards with "Recommended" tag and confirm action.
- **ClarificationList** — open / resolved chips.
- **Buttons / Badges / Inputs / Select / Tabs / Card / Progress / Avatar** — primitives lifted from `frontend/src/components/ui/*`.

Out of scope (matches the spec — "don't draw what isn't in the codebase"): Documents file viewer, Sync modal, Checks runner, Settings page. Stubs are present where the tab strip references them.

## Not pixel-perfect

Component implementations are cosmetic only — no real form state machines, no persisted store, no API. The point is the look and the click-flow, not parity with production logic.
