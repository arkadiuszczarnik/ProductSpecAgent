# Cockpit Launch Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route finished projects from the project overview directly into the cockpit, while unfinished projects still open the wizard workspace and direct cockpit access shows a hint page.

**Architecture:** Reuse the existing `flowState.steps` progress data already loaded by the frontend and derive one shared `isCockpitReady` rule from it. Apply that rule in three places: project overview card links, cockpit button visibility in the workspace header, and the cockpit route guard/hint page.

**Tech Stack:** Next.js App Router, React 19, TypeScript, existing frontend API client and Zustand stores

---

### Task 1: Add a shared cockpit-readiness helper

**Files:**
- Create: `frontend/src/lib/project-cockpit.ts`

- [ ] **Step 1: Write the failing helper test or fallback script**

If a frontend unit-test setup already exists, create:

```ts
import { describe, expect, it } from "vitest";
import { isCockpitReady } from "@/lib/project-cockpit";
import type { FlowState } from "@/lib/api";

function flow(stepStatuses: Array<"OPEN" | "IN_PROGRESS" | "COMPLETED">): FlowState {
  return {
    currentStep: "REVIEW",
    steps: stepStatuses.map((status, index) => ({
      step: `STEP_${index + 1}`,
      status,
    })),
  };
}

describe("isCockpitReady", () => {
  it("returns true only when all steps are completed", () => {
    expect(isCockpitReady(flow(["COMPLETED", "COMPLETED"]))).toBe(true);
    expect(isCockpitReady(flow(["COMPLETED", "OPEN"]))).toBe(false);
  });
});
```

If no frontend unit-test runner exists, create a temporary TypeScript check file next to the helper and delete it after implementation. The goal is still to define the expected API before the helper exists.

- [ ] **Step 2: Run test to verify it fails**

If unit tests exist:

Run: `cd frontend && npm test -- project-cockpit`
Expected: FAIL because `isCockpitReady` does not exist.

If there is no frontend unit-test runner:

Run: `cd frontend && npx tsc --noEmit src/lib/project-cockpit.ts`
Expected: FAIL because the file does not exist yet.

- [ ] **Step 3: Write the minimal helper**

Create `frontend/src/lib/project-cockpit.ts` with:

```ts
import type { FlowState } from "@/lib/api";

export function isCockpitReady(flowState?: FlowState | null): boolean {
  const steps = flowState?.steps ?? [];
  if (steps.length === 0) return false;
  return steps.every((step) => step.status === "COMPLETED");
}

export function cockpitHref(projectId: string, flowState?: FlowState | null): string {
  return isCockpitReady(flowState) ? `/projects/${projectId}/cockpit` : `/projects/${projectId}`;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run either:

`cd frontend && npm test -- project-cockpit`

or:

`cd frontend && npx tsc --noEmit src/lib/project-cockpit.ts`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/project-cockpit.ts
git commit -m "feat(cockpit): add readiness helper"
```

### Task 2: Gate project-overview navigation by progress

**Files:**
- Modify: `frontend/src/app/projects/page.tsx`
- Modify: `frontend/src/lib/project-cockpit.ts`

- [ ] **Step 1: Write the failing navigation expectation**

Add a focused test if the repo already has a frontend test runner, or otherwise document the exact expected markup/API before editing:

```ts
expect(cockpitHref("demo", completedFlowState)).toBe("/projects/demo/cockpit");
expect(cockpitHref("demo", incompleteFlowState)).toBe("/projects/demo");
```

- [ ] **Step 2: Run test to verify it fails**

Run the same narrow frontend command as Task 1.
Expected: FAIL because `ProjectsPage` still hardcodes `/projects/${p.id}`.

- [ ] **Step 3: Replace the hardcoded project link**

In `frontend/src/app/projects/page.tsx`:

```ts
import { cockpitHref, isCockpitReady } from "@/lib/project-cockpit";
```

Use the helper when mapping projects:

```ts
const ready = isCockpitReady(p.flowState);
const href = cockpitHref(p.id, p.flowState);
```

Replace:

```tsx
<Link href={`/projects/${p.id}`} className="block">
```

with:

```tsx
<Link href={href} className="block">
```

Optionally use `ready` only if the card copy needs a tiny readiness hint. Do not redesign the card.

- [ ] **Step 4: Run test/build check to verify it passes**

Run:

`cd frontend && npm run lint -- src/app/projects/page.tsx src/lib/project-cockpit.ts`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/projects/page.tsx frontend/src/lib/project-cockpit.ts
git commit -m "feat(cockpit): gate overview links by wizard completion"
```

### Task 3: Keep the cockpit button for preview, but hide it until ready

**Files:**
- Modify: `frontend/src/app/projects/[id]/page.tsx`
- Modify: `frontend/src/lib/project-cockpit.ts`

- [ ] **Step 1: Write the failing expectation**

Define the expected header rule before editing:

```ts
// Given a project with incomplete flowState, the workspace header does not render the Cockpit button.
// Given a project with completed flowState, the button is visible and still links to /projects/{id}/cockpit.
```

If a component test harness exists, encode this as an actual render assertion.

- [ ] **Step 2: Run test to verify it fails**

Run the narrow frontend test or lint/typecheck command for the workspace page.
Expected: FAIL or remain impossible until the file is updated to consume the helper.

- [ ] **Step 3: Gate the button with the shared helper**

In `frontend/src/app/projects/[id]/page.tsx`:

```ts
import { isCockpitReady } from "@/lib/project-cockpit";
```

After `flowState` is loaded:

```ts
const cockpitReady = isCockpitReady(flowState);
```

Replace the unconditional cockpit link block with:

```tsx
{cockpitReady && (
  <Link href={`/projects/${id}/cockpit`}>
    <Button variant="outline" size="sm" className="gap-1.5">
      <Gauge size={14} /> Cockpit
    </Button>
  </Link>
)}
```

- [ ] **Step 4: Run verification**

Run:

`cd frontend && npm run lint -- src/app/projects/[id]/page.tsx src/lib/project-cockpit.ts`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/projects/[id]/page.tsx frontend/src/lib/project-cockpit.ts
git commit -m "feat(cockpit): hide workspace cockpit button until ready"
```

### Task 4: Guard the cockpit route with a hint page

**Files:**
- Modify: `frontend/src/app/projects/[id]/cockpit/page.tsx`
- Create: `frontend/src/components/cockpit/CockpitLockedNotice.tsx`
- Modify: `frontend/src/lib/api.ts` (only if the route needs a server-safe helper or missing type export)
- Modify: `frontend/src/lib/project-cockpit.ts`

- [ ] **Step 1: Write the failing route expectation**

Define the exact behavior before implementation:

```ts
// If getProject(id).flowState is incomplete, /projects/{id}/cockpit renders:
// - headline "Cockpit erst nach abgeschlossenem Wizard verfuegbar"
// - short explanation
// - link back to /projects/{id}
// If complete, the route renders <ProjectCockpitPrototype projectId={id} />.
```

If an App Router route test exists, encode it there. Otherwise verify via targeted lint/build and manual browser check after implementation.

- [ ] **Step 2: Run test to verify it fails**

Run:

`cd frontend && npm run lint -- src/app/projects/[id]/cockpit/page.tsx`

Expected: current file passes lint but does not satisfy the route behavior yet; note this as the red step if no route test exists.

- [ ] **Step 3: Implement the locked notice component**

Create `frontend/src/components/cockpit/CockpitLockedNotice.tsx`:

```tsx
import Link from "next/link";
import { Lock, ArrowLeft } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface CockpitLockedNoticeProps {
  projectId: string;
}

export function CockpitLockedNotice({ projectId }: CockpitLockedNoticeProps) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-6">
      <div className="w-full max-w-lg rounded-2xl border bg-card p-8 text-center shadow-sm">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-muted">
          <Lock size={20} />
        </div>
        <h1 className="text-xl font-semibold">Cockpit erst nach abgeschlossenem Wizard verfuegbar</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          Schliesse zuerst alle Wizard-Schritte ab. Danach gelangst du aus der Projektuebersicht direkt ins Cockpit.
        </p>
        <Link
          href={`/projects/${projectId}`}
          className={cn(buttonVariants({ variant: "outline" }), "mt-6 gap-2")}
        >
          <ArrowLeft size={16} />
          Zurueck zum Workspace
        </Link>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Load project data in the cockpit route and branch by readiness**

Update `frontend/src/app/projects/[id]/cockpit/page.tsx` to:

```tsx
import { ProjectCockpitPrototype } from "@/components/cockpit/ProjectCockpitPrototype";
import { CockpitLockedNotice } from "@/components/cockpit/CockpitLockedNotice";
import { getProject } from "@/lib/api";
import { isCockpitReady } from "@/lib/project-cockpit";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function ProjectCockpitPage({ params }: PageProps) {
  const { id } = await params;
  const response = await getProject(id);

  if (!isCockpitReady(response.flowState)) {
    return <CockpitLockedNotice projectId={id} />;
  }

  return <ProjectCockpitPrototype projectId={id} />;
}
```

If `getProject` is client-only today, extract a server-safe fetch helper in `src/lib/api.ts` rather than duplicating fetch logic in the route.

- [ ] **Step 5: Run verification**

Run:

`cd frontend && npm run lint -- src/app/projects/[id]/cockpit/page.tsx src/components/cockpit/CockpitLockedNotice.tsx src/lib/project-cockpit.ts`

Expected: PASS

- [ ] **Step 6: Run a production build**

Run:

`cd frontend && npm run build`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/projects/[id]/cockpit/page.tsx frontend/src/components/cockpit/CockpitLockedNotice.tsx frontend/src/lib/project-cockpit.ts frontend/src/lib/api.ts
git commit -m "feat(cockpit): guard cockpit route until wizard completion"
```

### Task 5: Final validation and feature bookkeeping

**Files:**
- Modify: `docs/features/52-cockpit-launch-gate-after-wizard.md` (only if implementation meaningfully differs)
- Create: `docs/features/52-cockpit-launch-gate-after-wizard-done.md`

- [ ] **Step 1: Perform manual behavior validation**

Check three flows in the browser:

1. Open `/projects` and click an unfinished project.
Expected: `/projects/{id}` opens.

2. Open `/projects` and click a fully completed project.
Expected: `/projects/{id}/cockpit` opens.

3. Open `/projects/{id}/cockpit` for an unfinished project.
Expected: locked notice with link back to `/projects/{id}`.

- [ ] **Step 2: Write the done file**

Create `docs/features/52-cockpit-launch-gate-after-wizard-done.md` with:

```md
# Feature 52: Cockpit Launch Gate nach Wizard-Abschluss - Done

## Summary
- Projektkarten oeffnen bei 100% Fortschritt direkt das Cockpit.
- Unfertige Projekte bleiben im Workspace-Fluss.
- Die Cockpit-Route zeigt fuer unvollstaendige Projekte eine Hinweisseite.

## Abweichungen
- [nur echte Abweichungen eintragen]

## Offene Punkte
- [nur echte Restpunkte eintragen]
```

- [ ] **Step 3: Run final verification**

Run:

`cd frontend && npm run build`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add docs/features/52-cockpit-launch-gate-after-wizard.md docs/features/52-cockpit-launch-gate-after-wizard-done.md
git commit -m "docs(feature): record cockpit launch gate completion"
```
