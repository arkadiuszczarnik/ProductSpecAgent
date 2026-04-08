# Frontend Design System Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the frontend's design tokens, typography, and theme to match `docs/frontend-design-system.md` (OKLCh color space, Inter font, blue primary, dark-only, radius 0.625rem) without touching existing component APIs.

**Architecture:** This is a **token-level migration**. The Tailwind v4 utility classes already used across 34 files (`bg-primary`, `bg-card`, `border-border`, …) stay identical — only the values behind them change in `globals.css`. Additionally, the root font switches from Plus Jakarta Sans to Inter in `layout.tsx`, and the light-mode token block plus the theme toggle are removed to match the dark-only spec. Missing shadcn-style primitives (Dialog, Tabs, Select, Input, Sheet, Alert, ScrollArea, Separator) from the design doc are explicitly **out of scope** (YAGNI — create a follow-up plan when actually needed). The IDE-style 3-column layout in the doc is also **out of scope** because it conflicts with the current wizard flow.

**Tech Stack:** Next.js 16, React 19, TypeScript 5, Tailwind CSS v4 (`@theme` directive), CVA, @base-ui/react, Inter + JetBrains Mono via `next/font/google`.

---

## Scope & Non-Goals

**In scope:**
1. Replace Hex color tokens in `globals.css` with OKLCh values per design doc.
2. Switch primary color from violet (`#8B5CF6`) to blue (`oklch(0.62 0.19 260)`).
3. Switch body font from Plus Jakarta Sans to Inter; keep JetBrains Mono for mono.
4. Change `--radius` from `0.5rem` to `0.625rem`.
5. Remove light-mode tokens and dark override → dark-only root.
6. Remove theme toggle UI from `AppShell.tsx` + delete `use-theme` hook if unused elsewhere.
7. Verify build + visual smoke test on the core wizard routes.

**Out of scope (create separate plans if needed):**
- New UI primitives (Dialog, Tabs, Select, Input, Sheet, Alert, ScrollArea, Separator).
- IDE-shell layout rewrite (Icon-Bar 40 / Sidebar 240 / Detail 320 / Bottom 30vh).
- Replacing `lucide-react ^1.7.0` (API check — not a breaking token change).
- Touching existing `button.tsx` / `card.tsx` / `badge.tsx` / `progress.tsx` code (already CVA-conformant; values come from tokens).

---

## File Structure

**Modify:**
- `frontend/src/app/globals.css` — token values, remove light-mode block, update radius
- `frontend/src/app/layout.tsx` — swap `Plus_Jakarta_Sans` → `Inter`
- `frontend/src/components/layout/AppShell.tsx` — remove theme toggle button + imports
- `frontend/src/lib/hooks/use-theme.ts` — **delete** if no other consumers remain
- `docs/frontend-design-system.md` — add a short header note marking it as the active reference

**No changes:**
- `frontend/src/components/ui/*.tsx` — already use token utility classes; automatic pickup.
- All 30+ consumer files using `bg-primary`, `text-muted-foreground`, etc. — no rename needed.

---

## Task 1: Migrate color tokens to OKLCh in globals.css

**Files:**
- Modify: `frontend/src/app/globals.css`

**Context:** Tailwind v4 reads `@theme { --color-* }` to generate utilities like `bg-primary`. Keep the `--color-*` prefix so no utility class breaks. The design doc uses unprefixed names (`--primary`) following shadcn convention — we adapt to Tailwind v4 by keeping the prefix. Values are OKLCh per the doc's `:root` block. The current file has **both** a light-mode `@theme` block and a `.dark` override — since the app is dark-only now, collapse into a single `@theme` with dark values and delete the `.dark { … }` block.

- [ ] **Step 1: Replace the entire `@theme` block and delete the `.dark` block**

Open `frontend/src/app/globals.css` and replace lines 5–71 (the `@theme { … }` block through the closing `}` of `.dark { … }`) with:

```css
@theme {
  /* Primary (blue) */
  --color-primary: oklch(0.62 0.19 260);
  --color-primary-foreground: oklch(0.98 0 0);

  /* Background & foreground */
  --color-background: oklch(0.13 0.02 260);
  --color-foreground: oklch(0.93 0.01 250);

  /* Card */
  --color-card: oklch(0.17 0.02 260);
  --color-card-foreground: oklch(0.93 0.01 250);

  /* Muted */
  --color-muted: oklch(0.22 0.03 260);
  --color-muted-foreground: oklch(0.55 0.03 250);

  /* Secondary */
  --color-secondary: oklch(0.22 0.03 260);
  --color-secondary-foreground: oklch(0.93 0.01 250);

  /* Accent */
  --color-accent: oklch(0.22 0.03 260);
  --color-accent-foreground: oklch(0.93 0.01 250);

  /* Destructive */
  --color-destructive: oklch(0.65 0.2 25);
  --color-destructive-foreground: oklch(0.98 0 0);

  /* Borders, inputs, ring */
  --color-border: oklch(1 0 0 / 10%);
  --color-input: oklch(1 0 0 / 12%);
  --color-ring: oklch(0.62 0.19 260);

  /* Popover */
  --color-popover: oklch(0.17 0.02 260);
  --color-popover-foreground: oklch(0.93 0.01 250);

  /* Sidebar */
  --color-sidebar: oklch(0.15 0.025 260);
  --color-sidebar-foreground: oklch(0.93 0.01 250);
  --color-sidebar-primary: oklch(0.62 0.19 260);
  --color-sidebar-accent: oklch(0.22 0.03 260);
  --color-sidebar-border: oklch(1 0 0 / 10%);

  /* Chart colors */
  --color-chart-1: oklch(0.62 0.19 260);
  --color-chart-2: oklch(0.65 0.15 160);
  --color-chart-3: oklch(0.6 0.18 300);
  --color-chart-4: oklch(0.7 0.15 60);
  --color-chart-5: oklch(0.6 0.2 30);

  /* Radius */
  --radius: 0.625rem;

  /* Fonts */
  --font-sans: "Inter", ui-sans-serif, system-ui, sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, monospace;

  /* Animations (kept from previous version) */
  --animate-fade-in-up: fade-in-up 0.3s ease-out both;
  --animate-fade-in: fade-in 0.3s ease-out both;
  --animate-slide-in-right: slide-in-right 0.3s ease-out both;
  --animate-slide-in-left: slide-in-left 0.3s ease-out both;
  --animate-scale-in: scale-in 0.2s ease-out both;
}
```

Then **delete** the entire `.dark { … }` block (previous lines 46–71). The `@custom-variant dark (&:is(.dark *));` directive on line 3 stays — we keep it in case a component uses `dark:` utilities, but since `<html class="dark">` is always set, they always match.

- [ ] **Step 2: Run the frontend build to verify no token regressions**

Run from the `frontend/` directory:

```bash
npm run build
```

Expected: Build succeeds. Any error like `Cannot find --color-xyz` means a token was renamed — restore the missing token under its `--color-*` name.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/globals.css
git commit -m "feat(frontend): migrate design tokens to OKLCh, dark-only"
```

---

## Task 2: Switch body font from Plus Jakarta Sans to Inter

**Files:**
- Modify: `frontend/src/app/layout.tsx`

**Context:** `next/font/google` exports `Inter`. We keep the CSS-variable name `--font-sans` so the `@theme` mapping in `globals.css` from Task 1 picks it up automatically. Only the import + the variable assignment change.

- [ ] **Step 1: Replace the Plus_Jakarta_Sans import and usage**

In `frontend/src/app/layout.tsx`:

Replace line 2:

```tsx
import { Plus_Jakarta_Sans, JetBrains_Mono } from "next/font/google";
```

with:

```tsx
import { Inter, JetBrains_Mono } from "next/font/google";
```

Replace lines 6–9:

```tsx
const plusJakartaSans = Plus_Jakarta_Sans({
  subsets: ["latin"],
  variable: "--font-sans",
});
```

with:

```tsx
const inter = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
});
```

Replace line 29:

```tsx
className={`${plusJakartaSans.variable} ${jetbrainsMono.variable} font-sans antialiased`}
```

with:

```tsx
className={`${inter.variable} ${jetbrainsMono.variable} font-sans antialiased`}
```

- [ ] **Step 2: Rebuild to verify font loads**

Run:

```bash
npm run build
```

Expected: Build succeeds. Next.js downloads and inlines Inter during build. Any network error during build = try again.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/layout.tsx
git commit -m "feat(frontend): switch body font to Inter"
```

---

## Task 3: Remove theme toggle from AppShell (dark-only enforcement)

**Files:**
- Modify: `frontend/src/components/layout/AppShell.tsx`
- Delete (conditional): `frontend/src/lib/hooks/use-theme.ts`

**Context:** The design doc is dark-only. The current `AppShell` has a sun/moon toggle that cycles `dark → light → system`. Since we deleted the `.dark { … }` override in Task 1 and left only one token set, toggling would produce a broken state. Remove the button and its dependencies.

- [ ] **Step 1: Verify `use-theme` has no consumers outside AppShell**

Run:

```bash
rg "use-theme|useTheme" frontend/src
```

Expected output: only occurrences inside `frontend/src/components/layout/AppShell.tsx` and the hook file `frontend/src/lib/hooks/use-theme.ts` itself. If anything else consumes it, stop and narrow the change — only remove the toggle UI, keep the hook file.

- [ ] **Step 2: Remove theme toggle UI from AppShell.tsx**

In `frontend/src/components/layout/AppShell.tsx`:

Replace line 5:

```tsx
import { FolderKanban, Plus, Settings, Sun, Moon, Sparkles } from "lucide-react";
```

with:

```tsx
import { FolderKanban, Plus, Settings, Sparkles } from "lucide-react";
```

Delete line 7:

```tsx
import { useTheme } from "@/lib/hooks/use-theme";
```

Delete lines 42–47 inside `IconRail` (the `useTheme` hook call and `cycleTheme` function):

```tsx
  const { resolvedTheme, setTheme, theme, mounted } = useTheme();

  function cycleTheme() {
    const next = theme === "dark" ? "light" : theme === "light" ? "system" : "dark";
    setTheme(next);
  }
```

Delete the theme toggle `<button>` block (currently lines 76–82):

```tsx
        <button
          onClick={cycleTheme}
          className="flex h-10 w-10 items-center justify-center rounded-lg text-sidebar-foreground hover:text-zinc-200 transition-colors duration-150"
          title={mounted ? `Theme: ${theme}` : "Theme: dark"}
        >
          {!mounted || resolvedTheme === "dark" ? <Moon size={20} /> : <Sun size={20} />}
        </button>
```

The remaining `Settings` button stays.

- [ ] **Step 3: Delete the use-theme hook file (if Step 1 confirmed no other consumers)**

```bash
rm frontend/src/lib/hooks/use-theme.ts
```

If the `frontend/src/lib/hooks/` directory is now empty, remove it too:

```bash
rmdir frontend/src/lib/hooks 2>/dev/null || true
```

- [ ] **Step 4: Rebuild and lint**

```bash
npm run build && npm run lint
```

Expected: Build + lint succeed. Any "unused import" error = remove the leftover import.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/layout/AppShell.tsx frontend/src/lib/hooks
git commit -m "feat(frontend): remove theme toggle, enforce dark-only mode"
```

---

## Task 4: Visual smoke test on core routes

**Files:** None (manual verification).

**Context:** Token changes cascade across 34 files — we need eyes on it. No automated visual-regression tooling exists in this repo, so this is a manual check against a running dev server.

- [ ] **Step 1: Start the frontend dev server**

From `frontend/`:

```bash
npm run dev
```

Expected: Server starts on port 3000, no build errors in the terminal.

- [ ] **Step 2: Visit each core route and verify colors/fonts**

Open each URL and confirm:

| URL | Check |
|---|---|
| `http://localhost:3000/projects` | Dark background, blue primary buttons, Inter font on body text, cards have the new dark card background |
| `http://localhost:3000/projects/new` | Form inputs readable, primary submit button is blue (not violet) |
| `http://localhost:3000/projects/<any-existing-id>` | Wizard shell renders, step indicator readable, chat panel text legible, sidebar icon rail intact (no theme toggle button) |

Reference for expected colors: `docs/frontend-design-system.md` "Design-Zusammenfassung" table.

- [ ] **Step 3: Verify no theme-toggle remnant**

In the running app, inspect the icon rail. Expected: Sparkles logo, Projects icon, New Project icon, (gap), Settings icon only. No Sun/Moon button.

- [ ] **Step 4: Stop the dev server**

Press `Ctrl+C` in the terminal running `npm run dev`.

- [ ] **Step 5: No commit needed (verification only)**

If all checks passed, proceed to Task 5. If any route looks broken (e.g., unreadable text, missing border contrast), note the specific component and fix its token usage before continuing — do **not** rewrite tokens in `globals.css`; instead update the component to use the correct semantic token (e.g., `text-muted-foreground` instead of hard-coded `text-zinc-500`).

---

## Task 5: Mark the design system doc as the active reference

**Files:**
- Modify: `docs/frontend-design-system.md`

**Context:** The doc currently reads as a generic spec. After migration it becomes the living reference. Add a short status banner at the top so future readers know it reflects the actual implementation.

- [ ] **Step 1: Add status banner at the top of the doc**

In `docs/frontend-design-system.md`, insert immediately after line 1 (`# Frontend Design System`):

```markdown

> **Status:** Aktiv angewandt seit 2026-04-08 (siehe `docs/superpowers/plans/2026-04-08-frontend-design-system-migration.md`).
> Tokens leben in `frontend/src/app/globals.css`, Font-Wiring in `frontend/src/app/layout.tsx`.
> Nicht umgesetzt: IDE-Shell-Layout (inkompatibel mit Wizard-Flow), zusätzliche UI-Primitives (Dialog/Tabs/Select/Input/Sheet/Alert/ScrollArea/Separator) — bei Bedarf separate Pläne anlegen.
```

- [ ] **Step 2: Commit**

```bash
git add docs/frontend-design-system.md
git commit -m "docs: mark frontend design system as active reference"
```

---

## Rollback

If the migration looks wrong after Task 4 and a rollback is preferred over forward-fixing:

```bash
git revert HEAD~4..HEAD   # reverts Tasks 1–4 (5 commits including this doc change: adjust count)
```

Or cherry-pick revert individual commits (`git log --oneline` → `git revert <sha>`).

---

## Done Criteria

- `npm run build` succeeds in `frontend/`.
- `npm run lint` succeeds in `frontend/`.
- `/projects`, `/projects/new`, and one existing project detail route render with blue primary, OKLCh dark background, and Inter font.
- No theme toggle button in `AppShell`.
- `docs/frontend-design-system.md` has the status banner.
