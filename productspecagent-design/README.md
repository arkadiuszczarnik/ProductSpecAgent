# Spec Agent — Design System

> **Product Spec Agent** — _„Von der Idee zur umsetzbaren Produktspezifikation."_
>
> A SaaS workspace where a product owner walks through an 8-step wizard
> (Idee → Problem → Features → MVP → Design → Architektur → Backend → Frontend)
> with an LLM "Spec Agent" co-pilot. Output is a fully-cited spec, a task
> tree, and a handoff bundle for engineering.

---

## What's in this folder

| Path                         | What it is                                                            |
| ---------------------------- | --------------------------------------------------------------------- |
| `README.md`                  | This file — context, content + visual fundamentals, iconography, index |
| `SKILL.md`                   | Cross-compatible Agent Skill manifest                                 |
| `colors_and_type.css`        | Tokens (color, type, radii, motion) + base element styles             |
| `assets/`                    | Logos, app icons, generic illustrations                               |
| `preview/`                   | One small HTML card per design-system concept (registered for review) |
| `ui_kits/spec-agent/`        | Hi-fi recreation of the production web app                            |

---

## Sources

This system was reverse-engineered from:

- **Codebase** (mounted, read-only): `frontend/` — Next.js 16 + React 19 + Tailwind 4 + base-ui/react + shadcn/ui (style `base-nova`)
  - `frontend/src/app/globals.css` — token source of truth (oklch palette, three-block layering)
  - `frontend/src/app/layout.tsx` — `<html lang="de" class="dark">`, Inter + JetBrains Mono via `next/font`
  - `frontend/src/components/ui/{button,badge,card,progress,input,dialog,radio-group,textarea,label}.tsx`
  - `frontend/src/components/{layout/AppShell,wizard,chat,decisions,clarifications,tasks,spec-flow}/*.tsx`
  - `frontend/CLAUDE.md` — language rule (DE), wizard flow, theme blocks
  - `frontend/src/lib/category-step-config.ts` — 6 product categories × visible-step matrix
- **Screenshots** (uploaded): Dashboard, Idea step, Problem step, Features graph, Frontend step, Decisions panel — all from the `Bromo Control Hub` example project running locally.

---

## Products

The repo is a **single product** with one surface (a desktop SaaS web app), so this design system contains **one UI kit**: `ui_kits/spec-agent/`.

There are no marketing pages, no mobile app, no slide template — only the workspace itself.

---

## Content fundamentals

### Voice & tone

A bilingual product. **German is the primary UI language** (per `CLAUDE.md`'s Sprachregel), with English used for technical primitives that have no clean translation. The mix is consistent and codified — it isn't sloppy.

- **German for chrome and labels**: page titles, form fields, buttons, empty states, blocker banners.
  Examples: _Zurueck_, _Weiter_, _Exportieren_, _Abschliessen_, _Klaerung offen_, _Berücksichtigt Markdown- und Text-Dateien aus dem Documents-Tab._
- **English for domain nouns**: _Spec Agent, Decisions, Clarifications, Tasks, Checks, Documents, Sync, Handoff, Dashboard, Project, Spec Progress, Pending, Resolved, In Progress, Done, Story, Epic, Recommended, AI Recommendation_.
- **English for chat copy from the agent's mouth**: _"Say hi! I'll guide you through each step of the spec."_, _"Agent is thinking…"_

### Casing

- **Title Case** for proper nouns and tab labels: `Spec Agent`, `New Project`, `Asset Bundles`, `Agent-Modelle` (note the German-style hyphenation when a German word joins).
- **Sentence case** for body and helpers: _"Beantworte die offenen Punkte im Panel rechts"_, _"Provide your answer…"_
- **UPPERCASE** sparingly — only for step keys rendered as data (`FRONTEND`, `BACKEND` chips on project cards) and badge stamps (`OPEN`, `IN PROGRESS`, `DONE` on flow nodes use Title Case, not allcaps).

### Person & address

- **You-form (English `you`, German `du`/imperative)** — this is a working tool, not a marketing site. Imperative German is preferred over `Sie`: _„Beantworte"_, not _„Beantworten Sie"_.
- The agent talks in **first person** (`"Ich bin bereit"`, `"I'll guide you"`) and addresses the user directly.

### Tone

Calm, technical, Berlin-startup neutral. No marketing exclamation marks. No emoji. The single emoji-shaped element in the whole product is the lucide `Sparkles` icon used for "Generate", which is restrained and on-brand.

Concrete examples lifted from the source:

> _„Hallo! Ich bin bereit. Wir sind aktuell im Schritt **FRONTEND**…"_
> _"2 projects — turn ideas into specs."_
> _„Berücksichtigt Markdown- und Text-Dateien aus dem Documents-Tab."_
> _"No tasks yet. Generate a plan from your spec to create tasks."_

### Numbers, dates, units

- Dates render German: `07. Mai 2026` (`toLocaleDateString("de-DE", { day: "2-digit", month: "short", year: "numeric" })`).
- Counters use bare numerals next to short German nouns: _„1 Klaerung offen"_, _„8/8 steps"_.
- Step counts are 1-indexed in the UI even though stored 0-indexed.

### What we never do

- No emoji.
- No exclamation marks except in the agent's first-person greeting.
- No empty marketing fluff — every label maps to a real action or piece of state.
- No `Sie`. No `please/bitte` softeners.

---

## Visual foundations

### Mode

**Dark by default.** `<html class="dark">` is hard-coded in `layout.tsx`. A light fallback exists in `:root` but is dead code in production. All preview cards in this system are dark-on-dark.

### Color (see `colors_and_type.css`)

- **One primary, one accent.** A single tech-blue (`oklch(0.62 0.19 260)`) carries every primary action, the active wizard step, the sidebar logo, focus rings, and the primary chip. A single mint-green (`oklch(0.65 0.15 160)`) is reserved for "completed / resolved" — never decorative.
- **Three dark surfaces.** Background `0.13`, sidebar `0.15`, card/popover `0.17`, muted/secondary/accent all collapse to `0.22`. Depth comes from stacking these surfaces; not from shadows.
- **Translucent borders.** `oklch(1 0 0 / 10%)` everywhere. Borders read against whichever surface they sit on without recoloring.
- **Status colors are inline oklch literals in the codebase** — not aliased. We've named them here (`--color-success`, `--color-destructive`, `--color-warning`, `--color-purple`) so new code can reference them, but be aware production code writes `bg-[oklch(0.65_0.15_160)]` directly.

### Typography

- **Inter** sans for everything; **JetBrains Mono** for code/file viewers (shiki).
- Tight scale, dense layout: page titles 24px / 700, card titles 16px / 600, body 14px, meta 11–12px, badges 10–11px. Headings carry `letter-spacing: -0.01em`.
- Body line-height ~1.55 — German compounds need room.

### Spacing & density

The product is **dense by intent**. Buttons are 32px tall (`size-default`), inputs 32px, side rail items 40px, badge chips 24px. Page padding 24/32px, card padding 16px, panel section padding 12–16px. Grid gaps 8–16px.

### Radii

Base `0.625rem` (10px). Buttons and inputs use `rounded-md` (~8px). Cards use `rounded-lg` (10px). Dialogs use `rounded-xl` (~14px). Badges and avatars are full pills (`rounded-full`).

### Backgrounds, imagery, decoration

- **No imagery, no illustrations, no patterns, no gradients.** Empty states use a centered lucide icon at ~30% opacity over the bare card surface.
- The icon rail's logo tile is a flat blue square with a white `Sparkles` icon — that's as decorative as the product gets.
- No noise, no grain, no full-bleed photos. The brand identity is "tooling, not marketing."

### Animation

Short and minimal. The keyframes shipped in `globals.css` are: `fade-in-up` (12px translate, 300ms), `fade-in` (300ms), `slide-in-right/left` (16px, 300ms), `scale-in` (95→100, 200ms). Easing is `ease-out` only — no bounces, no spring, no parallax. List items use a 50ms stagger via inline `animationDelay`. A `prefers-reduced-motion` override clamps everything to 0.01ms.

### Hover, active, focus

- **Buttons (primary):** `hover:opacity-90`, no scale, no color shift.
- **Buttons (ghost / outline):** background fades to `bg-secondary` at the same alpha — no color change.
- **Sidebar nav:** color shifts from muted to `zinc-200`; the active item shows a 3px primary bar pinned to the left edge of the rail.
- **Tab strips:** active tab gains a 2px primary border-bottom and primary text — no fill.
- **Form inputs:** focus rings are 3px at `ring/50`, with the border itself swapping to `ring`. Aria-invalid uses `destructive/20`.
- **Cards (project tiles):** `hover:-translate-y-0.5 hover:shadow-md` — a single-pixel lift, no scale.
- **Press states:** there is no codified press state — the `transition-colors duration-150` covers the whole interaction. No scale-down, no inner shadow.

### Borders, shadows, elevation

- Borders are 1px translucent white (`oklch(1 0 0 / 10%)`) by default; `--border` swaps to `0.9 0.01 250` in the light fallback.
- Shadows: `shadow-sm` (a 1px offset, 20% black) is the workhorse; `shadow-md` only appears on card hover and dialog overlays.
- Dialogs use `ring-1 ring-foreground/10` (a faint inner ring) plus `bg-popover` — no drop shadow.
- Backdrop on dialog overlay is `bg-black/10` with `supports-backdrop-filter:backdrop-blur-xs` — a barely-there blur, never opaque.

### Transparency & blur

Used very sparingly:

- Dialog backdrop blur (above).
- Sidebar logo tile and badge fills lean on `bg-primary/10` (10% primary tint) for soft blue containers.
- The cancel/edit FAB on project cards uses `bg-background/80 backdrop-blur-sm` so it floats over the card art without being a hard overlay.

### Cards

The base card recipe (`components/ui/card.tsx`):
```
bg-card text-card-foreground border border-border rounded-lg shadow-sm transition-all duration-200
```

Header `p-4`, content `px-4 pb-4`, footer with a `border-t` divider. **No accent stripe, no colored left-border, no hand-drawn nothing.** Variants tint the border only: `border-primary/20` for decisions, `border-amber-500/20` for clarifications.

### Layout rules

- Fixed-width left icon rail (56px), full-bleed workspace beside it.
- Workspace splits into three zones at the project level: optional Explorer (240px), wizard area (flex-1), right panel (resizable 280–900px, default 600px).
- The right panel uses a flat tab strip (`Chat / Decisions / Clarifications / Tasks / Checks / Documents / Sync`) — no sub-routing, no breadcrumbs.
- Page titles top-left, primary CTA top-right.

### Color vibe of imagery

There is no imagery. If we add any, it should match the dark workspace mood: cool, low-saturation, bluish, no warm tones, no people. Diagrams are preferred to photos.

---

## Iconography

**Single source: [lucide-react](https://lucide.dev) v1.14.0.** Hard codified — `frontend/CLAUDE.md` literally says _„Icons ausschliesslich aus `lucide-react`"_.

- **Stroke style:** lucide default — 1.5px stroke, rounded caps, no fills. Don't mix with solid icon sets.
- **Sizes:** Most icons are sized via the `size` prop (a number). Common values:
  - `11–13` for inline metadata and badges
  - `14–15` for buttons and tab strips
  - `16` for form-field decorations and dialog markers
  - `18–20` for navigation, card icons
  - `24–32` for empty-state illustrations (rendered at `opacity-30`)
- **Color:** icons inherit `text-current`. They never carry their own fill. Color comes from the surrounding text-color utility (`text-primary`, `text-muted-foreground`, `text-amber-400`, etc.).
- **Common icons used in the app:**
  `Sparkles, FolderKanban, Plus, Package, MessageSquareText, Cpu, Settings, Bot, User, Send, Loader2, ArrowLeft, ArrowRight, Check, X, Star, AlertTriangle, Lock, Info, ChevronDown, ChevronRight, Calendar, Layers, BookOpen, CheckSquare, HelpCircle, CheckCircle2, Scale, ShieldCheck, FileText, FolderTree, Activity, Download, Trash2, MessageSquare`
- **Custom SVGs:** none in production. The only files in `frontend/public/` are five CRA-default placeholders (`file.svg`, `globe.svg`, `next.svg`, `vercel.svg`, `window.svg`) that are not referenced anywhere in source — they're dead artifacts.
- **Emoji:** never.
- **Unicode glyphs as icons:** never.
- **Icon font / sprite:** none. Tree-shaken React components only.

This system links lucide via CDN (`https://unpkg.com/lucide-static@0.292.0/icons/*.svg`) in the UI kit, and inlines a few of its SVGs into `assets/` for direct use in slides/decks. **No substitution was needed.**

---

## Index

| File / folder                          | Purpose                                                                |
| -------------------------------------- | ---------------------------------------------------------------------- |
| `colors_and_type.css`                  | All design tokens + base element styles. Import this first.            |
| `assets/logo-mark.svg`                 | The 32px Sparkles logo tile (white on primary blue).                   |
| `assets/logo-wordmark.svg`             | "Spec Agent" wordmark for export/handoff documents.                    |
| `assets/icons/*.svg`                   | Locally-cached lucide SVGs used by the UI kit and slides.              |
| `preview/*.html`                       | Small specimen cards — typography, palette, components, states.        |
| `ui_kits/spec-agent/README.md`         | UI-kit usage notes, component map.                                     |
| `ui_kits/spec-agent/index.html`        | Interactive recreation of the workspace (5 screens, click-thru).       |
| `ui_kits/spec-agent/*.jsx`             | One JSX file per component cluster (loaded via Babel inline).          |
| `SKILL.md`                             | Agent-Skills manifest so this folder works in Claude Code as a skill.  |

---

## Caveats

- **No codebase Figma file** was provided. Visual decisions are driven by source code + screenshots only.
- **Light-mode is dead code in production** — included for completeness but untested.
- The production app uses `next/font/google` to bundle Inter and JetBrains Mono offline. This design system links Google Fonts at runtime instead. If you ship the system without internet, vendor the woff2s into `fonts/` and update `colors_and_type.css`.
- The existing `frontend/public/` SVGs are CRA defaults, not brand assets. The Sparkles logo tile is a new asset created here from the lucide glyph — flag if a real brand logo exists elsewhere.
