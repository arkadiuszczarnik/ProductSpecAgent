# Project Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to delete a project from the overview page (`/projects`) via a hover-revealed trash button + confirmation modal, with inline error handling.

**Architecture:** Pure frontend change. Backend (`DELETE /api/v1/projects/{id}`) and basic API client function already exist. We harden the API client (proper error propagation), build a new `DeleteProjectDialog` component, and wire it into the projects page with a card-wrapper restructure that keeps the link and the new button as siblings (no nested interactive elements).

**Tech Stack:** Next.js 16 App Router, React 19 (client component), TypeScript, Tailwind CSS 4, lucide-react icons. No test framework on frontend — verification is manual in the browser.

---

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `frontend/src/lib/api.ts` | Modify (line 273-275) | Harden `deleteProject()` so HTTP errors propagate |
| `frontend/src/components/projects/DeleteProjectDialog.tsx` | Create | Confirmation modal — overlay + card pattern matching `ExportDialog` |
| `frontend/src/app/projects/page.tsx` | Modify | Wrap each card in a `relative group`, add trash button as sibling, wire dialog |

No new directory needs creation: `frontend/src/components/projects/` is new. Next.js/TypeScript don't require explicit directory creation — `Write` will create it.

---

### Task 1: Harden `deleteProject` API client

**Files:**
- Modify: `frontend/src/lib/api.ts:273-275`

The current implementation swallows errors silently (raw `fetch`, no `!res.ok` check, no JSON parse since backend returns 204). We can't blindly use `apiFetch<void>` because it always calls `res.json()` at the end (line 20 in `api.ts`), which would throw `SyntaxError` on an empty 204 body. Cleanest fix: keep `fetch`, add the same error-propagation pattern that `apiFetch` uses for non-OK responses.

- [ ] **Step 1: Replace the function body**

Open `frontend/src/lib/api.ts`, locate lines 273-275:

```ts
export async function deleteProject(id: string): Promise<void> {
  await fetch(`${API_BASE}/api/v1/projects/${id}`, { method: "DELETE" });
}
```

Replace with:

```ts
export async function deleteProject(id: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/v1/projects/${id}`, { method: "DELETE" });
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(error.message || `API error: ${res.status}`);
  }
}
```

- [ ] **Step 2: Verify no other callers exist**

Run: `grep -rn "deleteProject\b" frontend/src/`
Expected: only the definition at `frontend/src/lib/api.ts:273` (no callers yet — the page integration happens in Task 3).

- [ ] **Step 3: Verify TypeScript still compiles**

Run from `frontend/`: `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "fix(frontend): propagate errors from deleteProject API client"
```

---

### Task 2: Create `DeleteProjectDialog` component

**Files:**
- Create: `frontend/src/components/projects/DeleteProjectDialog.tsx`

Modeled on `frontend/src/components/export/ExportDialog.tsx`:
- Card-on-backdrop overlay, NOT a base-ui Dialog primitive (consistency with existing pattern).
- Open state derived from `project !== null`; one source of truth.
- Local state for `deleting` (loading) and `deleteError` (inline error).
- ESC handler closes the modal unless `deleting`.
- Backdrop click closes the modal unless `deleting`.

- [ ] **Step 1: Create the component**

Write to `frontend/src/components/projects/DeleteProjectDialog.tsx`:

```tsx
"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { deleteProject } from "@/lib/api";

interface DeleteProjectDialogProps {
  project: { id: string; name: string } | null;
  onClose: () => void;
  onDeleted: (id: string) => void;
}

export function DeleteProjectDialog({ project, onClose, onDeleted }: DeleteProjectDialogProps) {
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!project) {
      setDeleting(false);
      setDeleteError(null);
      return;
    }
    cancelButtonRef.current?.focus();
  }, [project]);

  useEffect(() => {
    if (!project) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !deleting) onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [project, deleting, onClose]);

  if (!project) return null;

  async function handleDelete() {
    if (!project) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteProject(project.id);
      onDeleted(project.id);
      onClose();
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : "Löschen fehlgeschlagen.");
      setDeleting(false);
    }
  }

  function handleBackdropClick() {
    if (!deleting) onClose();
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={handleBackdropClick} />
      <Card className="relative z-10 w-full max-w-md mx-4">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Projekt löschen?</CardTitle>
            <button
              type="button"
              onClick={onClose}
              disabled={deleting}
              className="text-muted-foreground hover:text-foreground disabled:opacity-50"
              aria-label="Dialog schliessen"
            >
              <X size={16} />
            </button>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm">
            Möchtest du <strong>„{project.name}"</strong> wirklich löschen? Diese Aktion entfernt
            Spec, Decisions, Clarifications, Tasks, Documents und Docs-Scaffold — sie kann nicht
            rückgängig gemacht werden.
          </p>
          {deleteError && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
              {deleteError}
            </div>
          )}
        </CardContent>
        <CardFooter className="justify-end gap-2">
          <Button ref={cancelButtonRef} variant="ghost" onClick={onClose} disabled={deleting}>
            Abbrechen
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
            {deleting ? (
              <>
                <Loader2 size={14} className="animate-spin" /> Lösche…
              </>
            ) : (
              <>
                <Trash2 size={14} /> Löschen
              </>
            )}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
```

- [ ] **Step 2: Check that `Button` accepts a `ref`**

Open `frontend/src/components/ui/button.tsx` and verify the function signature. The existing definition is:

```tsx
function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button data-slot="button" className={cn(...)} {...props} />;
}
```

This does **not** forward refs. We need the cancel button focus-on-open. Fix it: convert `Button` to forward refs.

Modify `frontend/src/components/ui/button.tsx`. Replace:

```tsx
function Button({ className, variant, size, ...props }: ButtonProps) {
  return (
    <button
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  );
}
```

with:

```tsx
const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => {
    return (
      <button
        ref={ref}
        data-slot="button"
        className={cn(buttonVariants({ variant, size, className }))}
        {...props}
      />
    );
  }
);
Button.displayName = "Button";
```

The `import * as React from "react"` is already at the top of the file.

- [ ] **Step 3: Verify TypeScript compiles**

Run from `frontend/`: `npx tsc --noEmit`
Expected: no errors. If `forwardRef` complains about the props type, ensure `ButtonProps` extends `React.ButtonHTMLAttributes<HTMLButtonElement>` (already does in the existing file, line 39-41).

- [ ] **Step 4: Verify ESLint passes**

Run from `frontend/`: `npm run lint`
Expected: no new warnings/errors in the changed files.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/projects/DeleteProjectDialog.tsx frontend/src/components/ui/button.tsx
git commit -m "feat(frontend): add DeleteProjectDialog confirmation modal"
```

---

### Task 3: Wire dialog into projects page

**Files:**
- Modify: `frontend/src/app/projects/page.tsx`

Three changes:
1. Add imports (`Trash2`, `DeleteProjectDialog`).
2. Add `projectToDelete` state + delete handlers.
3. Restructure the card render: wrap each card in `<div class="relative group">`, place the `<Link>` and the trash button as siblings (not nested).
4. Render `<DeleteProjectDialog>` once at the bottom of the page.

- [ ] **Step 1: Add imports**

In `frontend/src/app/projects/page.tsx`, find line 5:

```ts
import { Plus, FolderKanban, Calendar, Loader2, ArrowRight } from "lucide-react";
```

Replace with:

```ts
import { Plus, FolderKanban, Calendar, Loader2, ArrowRight, Trash2 } from "lucide-react";
```

After line 11 (`import { cn } from "@/lib/utils";`), add:

```ts
import { DeleteProjectDialog } from "@/components/projects/DeleteProjectDialog";
```

- [ ] **Step 2: Add `projectToDelete` state**

Inside `ProjectsPage()` (around line 24), find:

```ts
const [projects, setProjects] = useState<ProjectWithStats[]>([]);
const [loading, setLoading] = useState(true);
const [error, setError] = useState<string | null>(null);
```

Add after `error`:

```ts
const [projectToDelete, setProjectToDelete] = useState<ProjectWithStats | null>(null);
```

- [ ] **Step 3: Restructure card rendering — wrap with `relative group`, add trash button**

Locate the `.map(...)` block starting at line 94 (`projects.map((p, idx) => {`). Find the inner return (line 99):

```tsx
return (
  <Link key={p.id} href={`/projects/${p.id}`} className="block group">
    <Card
      className="flex flex-col h-full hover:-translate-y-0.5 hover:shadow-md animate-fade-in-up"
      style={{ animationDelay: `${idx * 50}ms` }}
    >
      ...
    </Card>
  </Link>
);
```

Replace with:

```tsx
return (
  <div key={p.id} className="relative group">
    <Link href={`/projects/${p.id}`} className="block">
      <Card
        className="flex flex-col h-full hover:-translate-y-0.5 hover:shadow-md animate-fade-in-up"
        style={{ animationDelay: `${idx * 50}ms` }}
      >
        <CardHeader className="pb-2">
          <div className="flex items-start justify-between">
            <div className="flex items-start gap-3">
              <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <FolderKanban size={18} />
              </div>
              <div className="min-w-0">
                <CardTitle className="truncate text-base">{p.name}</CardTitle>
                <CardDescription className="mt-0.5 flex items-center gap-1.5 text-xs">
                  <Calendar size={11} /> {formatDate(p.createdAt)}
                </CardDescription>
              </div>
            </div>
            <ArrowRight size={14} className="text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity mt-1" />
          </div>
        </CardHeader>

        <CardContent className="flex-1 space-y-3 pb-3">
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-[10px] text-muted-foreground">Spec Progress</span>
              <span className="text-[10px] font-medium">{progress}%</span>
            </div>
            <Progress value={progress} />
          </div>

          <div className="flex items-center gap-1.5">
            <span className="text-[10px] text-muted-foreground">Next:</span>
            <Badge variant="default" className="capitalize text-[9px]">{nextStep}</Badge>
          </div>
        </CardContent>

        <CardFooter className="border-t pt-2.5 pb-2.5">
          <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
            <span>{p.completedSteps}/{p.totalSteps} steps</span>
          </div>
        </CardFooter>
      </Card>
    </Link>
    <button
      type="button"
      onClick={() => setProjectToDelete(p)}
      aria-label={`Projekt "${p.name}" löschen`}
      className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 focus-visible:opacity-100 transition-opacity rounded-md p-1.5 bg-background/80 backdrop-blur-sm text-muted-foreground hover:text-destructive hover:bg-destructive/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <Trash2 size={14} />
    </button>
  </div>
);
```

Notes:
- The existing `group` class moved from `<Link>` to the new wrapping `<div>`, so the existing `group-hover:opacity-100` on `ArrowRight` keeps working.
- `key={p.id}` moves to the wrapping `<div>`.
- `<Link>` no longer needs `group` — drop it from `className="block"`.

- [ ] **Step 4: Render the dialog at the bottom of the page**

Find the closing of the outer wrapping div (the one with `className="mx-auto max-w-5xl px-6 py-8"`) — it's the final `</div>` before `);` at the end of the component (around line 149-150).

Just before that closing `</div>`, add:

```tsx
<DeleteProjectDialog
  project={projectToDelete ? { id: projectToDelete.id, name: projectToDelete.name } : null}
  onClose={() => setProjectToDelete(null)}
  onDeleted={(id) => setProjects((prev) => prev.filter((p) => p.id !== id))}
/>
```

- [ ] **Step 5: Verify TypeScript compiles**

Run from `frontend/`: `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 6: Verify ESLint passes**

Run from `frontend/`: `npm run lint`
Expected: no new warnings/errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/projects/page.tsx
git commit -m "feat(frontend): wire DeleteProjectDialog into projects overview page"
```

---

### Task 4: Manual browser verification

No tests, so verification is manual against the spec's acceptance criteria.

**Files:** None (verification only).

- [ ] **Step 1: Start backend**

Run from `backend/`: `./gradlew bootRun --quiet`
Expected: server up on port 8080.

- [ ] **Step 2: Start frontend dev server**

Run from `frontend/`: `npm run dev`
Expected: server up on port 3000 (or whatever Next.js auto-selects). Watch the console for the actual URL.

- [ ] **Step 3: Verify happy path**

1. Open `http://localhost:3000/projects`.
2. Hover over a project card → trash icon appears top-right.
3. Click trash icon → modal opens, card-link did NOT navigate.
4. Modal shows correct project name in the body.
5. Click "Löschen" → spinner appears briefly, then modal closes and the card is gone from the list. Other cards unchanged.
6. On filesystem, verify `data/projects/{deleted-id}/` is gone:

   Run: `ls /Users/czarnik/IdeaProjects/ProductSpecAgent/data/projects/`
   Expected: deleted project's directory no longer present.

- [ ] **Step 4: Verify cancel paths**

1. Hover → trash → modal opens.
2. Click "Abbrechen" → modal closes, list unchanged.
3. Reopen modal → press ESC → modal closes, list unchanged.
4. Reopen modal → click on dimmed backdrop → modal closes, list unchanged.

- [ ] **Step 5: Verify keyboard reachability**

1. Reload page.
2. Press Tab repeatedly. The trash button should receive focus (visible because of `focus-visible:opacity-100`) for each card.
3. Press Enter while trash button is focused → modal opens, focus is on "Abbrechen" button.
4. Press Tab inside modal → focus moves to "Löschen" button.

- [ ] **Step 6: Verify error path**

1. Stop the backend (`Ctrl+C` in backend terminal).
2. Hover → trash → modal opens.
3. Click "Löschen" → spinner shows briefly, then modal stays open with red error banner displaying a network error message.
4. "Löschen" button is enabled again. Click "Abbrechen" — modal closes, project still in list.
5. Restart backend and verify deletion now works.

- [ ] **Step 7: Verify navigation isolation**

1. Click anywhere on a card except the trash button → navigates to workspace.
2. Click on the trash button → modal opens, no navigation.

- [ ] **Step 8: No commit needed**

Verification only. If any check fails, fix the issue in the relevant task and re-verify.

---

## Self-Review Checklist (already performed)

- **Spec coverage:** All 9 acceptance criteria from the spec map to verification steps in Task 4. The API hardening covers the spec's "kleine Aufräumarbeit". The card restructure covers the a11y/event-bubbling requirement.
- **Placeholder scan:** No "TBD" / "TODO" / "appropriate handling" — every step contains literal code or commands.
- **Type consistency:** `project: { id; name } | null` prop type is consistent between Task 2 (component definition) and Task 3 (callsite). `setProjectToDelete` and `setProjects` signatures match.
- **Discovered deviation from spec:** Spec said "deleteProject auf apiFetch umstellen" but `apiFetch` always parses JSON — broken for HTTP 204. Plan keeps `fetch` and adds explicit `!res.ok` check (same error semantics as `apiFetch`). Spec intent ("Fehler propagieren") is preserved.
- **Discovered extra work:** `Button` doesn't forward refs, blocking the focus-on-open pattern in the dialog. Added a small forwardRef conversion in Task 2 Step 2 — surgical, no behavior change for existing callers.
