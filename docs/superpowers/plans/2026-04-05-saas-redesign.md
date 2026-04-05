# SaaS Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the frontend from dark-mode-only glassmorphism to a professional Modern Startup SaaS look with Violet/Cyan colors, Plus Jakarta Sans font, Slim Icon Rail, subtle shadows, and Light+Dark mode support.

**Architecture:** Token-first approach — redefine all CSS custom properties first (changing ~70% of the visual appearance), then swap the font, rebuild the navigation as a slim icon rail, and update individual components. Each task produces a working UI state.

**Tech Stack:** Next.js 16, React 19, Tailwind CSS 4, Lucide React, class-variance-authority

**Design Spec:** `docs/superpowers/specs/2026-04-05-saas-redesign-design.md`

---

### Task 1: Design Tokens & globals.css Overhaul

**Files:**
- Modify: `frontend/src/app/globals.css`

This is the highest-impact change — replaces all color tokens, removes glassmorphism effects, and adds light/dark mode support.

- [ ] **Step 1: Replace the entire globals.css with the new design tokens**

```css
@import "tailwindcss";

@custom-variant dark (&:is(.dark *));

@theme inline {
  /* ── Light Mode (default) ─────────────────────────── */
  --color-background: #FAFAFA;
  --color-foreground: #09090B;
  --color-card: #FFFFFF;
  --color-card-foreground: #09090B;
  --color-primary: #7C3AED;
  --color-primary-foreground: #FFFFFF;
  --color-secondary: #F4F4F5;
  --color-secondary-foreground: #18181B;
  --color-muted: #F4F4F5;
  --color-muted-foreground: #71717A;
  --color-accent: #06B6D4;
  --color-accent-foreground: #FFFFFF;
  --color-destructive: #EF4444;
  --color-destructive-foreground: #FFFFFF;
  --color-border: #E4E4E7;
  --color-input: #E4E4E7;
  --color-ring: #7C3AED;
  --color-popover: #FFFFFF;
  --color-popover-foreground: #09090B;
  --color-sidebar: #18181B;
  --color-sidebar-foreground: #A1A1AA;
  --color-sidebar-primary: #7C3AED;
  --color-sidebar-accent: #27272A;
  --color-sidebar-border: #27272A;
  --color-chart-1: #7C3AED;
  --color-chart-2: #06B6D4;
  --color-chart-3: #8B5CF6;
  --color-chart-4: #22D3EE;
  --color-chart-5: #EF4444;
  --radius: 0.5rem;
  --font-sans: "Plus Jakarta Sans", ui-sans-serif, system-ui, sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, monospace;

  --animate-fade-in-up: fade-in-up 0.3s ease-out both;
  --animate-fade-in: fade-in 0.3s ease-out both;
  --animate-slide-in-right: slide-in-right 0.3s ease-out both;
  --animate-slide-in-left: slide-in-left 0.3s ease-out both;
  --animate-scale-in: scale-in 0.2s ease-out both;
}

/* ── Dark Mode Overrides ─────────────────────────────── */

.dark {
  --color-background: #09090B;
  --color-foreground: #FAFAFA;
  --color-card: #18181B;
  --color-card-foreground: #FAFAFA;
  --color-primary: #8B5CF6;
  --color-primary-foreground: #FFFFFF;
  --color-secondary: #27272A;
  --color-secondary-foreground: #FAFAFA;
  --color-muted: #27272A;
  --color-muted-foreground: #A1A1AA;
  --color-accent: #22D3EE;
  --color-accent-foreground: #09090B;
  --color-destructive: #EF4444;
  --color-destructive-foreground: #FFFFFF;
  --color-border: #27272A;
  --color-input: #27272A;
  --color-ring: #8B5CF6;
  --color-popover: #18181B;
  --color-popover-foreground: #FAFAFA;
  --color-sidebar: #09090B;
  --color-sidebar-foreground: #71717A;
  --color-sidebar-primary: #8B5CF6;
  --color-sidebar-accent: #18181B;
  --color-sidebar-border: #27272A;
}

/* ── Keyframes ─────────────────────────────────────────── */

@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slide-in-right {
  from { opacity: 0; transform: translateX(16px); }
  to { opacity: 1; transform: translateX(0); }
}

@keyframes slide-in-left {
  from { opacity: 0; transform: translateX(-16px); }
  to { opacity: 1; transform: translateX(0); }
}

@keyframes scale-in {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}

/* ── Base ──────────────────────────────────────────────── */

body {
  background: var(--color-background);
  color: var(--color-foreground);
  font-family: var(--font-sans);
}

/* ── Reduced Motion ────────────────────────────────────── */

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

- [ ] **Step 2: Verify the file saved correctly**

Run: `head -5 frontend/src/app/globals.css`
Expected: `@import "tailwindcss";` followed by `@custom-variant dark`

- [ ] **Step 3: Verify the build compiles**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds without CSS errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/globals.css
git commit -m "feat: replace design tokens with SaaS color system (Violet/Cyan, Light+Dark)"
```

---

### Task 2: Font Swap — Outfit → Plus Jakarta Sans

**Files:**
- Modify: `frontend/src/app/layout.tsx`

- [ ] **Step 1: Replace the font import and configuration**

Change `layout.tsx` to use Plus Jakarta Sans instead of Outfit:

```tsx
import type { Metadata } from "next";
import { Plus_Jakarta_Sans, JetBrains_Mono } from "next/font/google";
import "./globals.css";
import { AppShell } from "@/components/layout/AppShell";

const plusJakartaSans = Plus_Jakarta_Sans({
  subsets: ["latin"],
  variable: "--font-sans",
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
});

export const metadata: Metadata = {
  title: "Product Spec Agent",
  description: "Von der Idee zur umsetzbaren Produktspezifikation",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="de" className="dark" suppressHydrationWarning>
      <body
        className={`${plusJakartaSans.variable} ${jetbrainsMono.variable} font-sans antialiased`}
      >
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
```

Key changes:
- `Outfit` → `Plus_Jakarta_Sans` import and instantiation
- `outfit.variable` → `plusJakartaSans.variable`
- Added `suppressHydrationWarning` on `<html>` (needed for theme toggle later — the `className` will be set by client JS)

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds, font loads correctly

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/layout.tsx
git commit -m "feat: swap Outfit font to Plus Jakarta Sans"
```

---

### Task 3: Theme Toggle Hook

**Files:**
- Create: `frontend/src/lib/hooks/use-theme.ts`

- [ ] **Step 1: Create the useTheme hook**

```ts
"use client";

import { useEffect, useState, useCallback } from "react";

type Theme = "light" | "dark" | "system";

function getSystemTheme(): "light" | "dark" {
  if (typeof window === "undefined") return "dark";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme(theme: Theme) {
  const resolved = theme === "system" ? getSystemTheme() : theme;
  document.documentElement.classList.toggle("dark", resolved === "dark");
}

export function useTheme() {
  const [theme, setThemeState] = useState<Theme>(() => {
    if (typeof window === "undefined") return "dark";
    return (localStorage.getItem("theme") as Theme) ?? "dark";
  });

  const setTheme = useCallback((newTheme: Theme) => {
    setThemeState(newTheme);
    localStorage.setItem("theme", newTheme);
    applyTheme(newTheme);
  }, []);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  useEffect(() => {
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = () => {
      if (theme === "system") applyTheme("system");
    };
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, [theme]);

  const resolvedTheme = theme === "system" ? getSystemTheme() : theme;

  return { theme, setTheme, resolvedTheme } as const;
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | tail -10`
Expected: No type errors related to `use-theme.ts`

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/hooks/use-theme.ts
git commit -m "feat: add useTheme hook for light/dark/system toggle"
```

---

### Task 4: Icon Rail Navigation (AppShell)

**Files:**
- Modify: `frontend/src/components/layout/AppShell.tsx`

- [ ] **Step 1: Rewrite AppShell with slim icon rail**

Replace the entire `AppShell.tsx` with:

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { FolderKanban, Plus, Settings, Sun, Moon, Sparkles, PanelLeft } from "lucide-react";
import { cn } from "@/lib/utils";
import { useTheme } from "@/lib/hooks/use-theme";

interface AppShellProps {
  children: React.ReactNode;
}

interface NavItemProps {
  href: string;
  icon: React.ReactNode;
  label: string;
  active: boolean;
}

function NavItem({ href, icon, label, active }: NavItemProps) {
  return (
    <Link
      href={href}
      className={cn(
        "relative flex h-10 w-10 items-center justify-center rounded-lg transition-colors duration-150 group",
        active
          ? "text-sidebar-primary"
          : "text-sidebar-foreground hover:text-zinc-200"
      )}
      title={label}
    >
      {active && (
        <div className="absolute left-0 top-1/2 -translate-y-1/2 h-5 w-[3px] rounded-r-full bg-sidebar-primary" />
      )}
      {icon}
    </Link>
  );
}

function IconRail() {
  const pathname = usePathname();
  const { resolvedTheme, setTheme, theme } = useTheme();

  function cycleTheme() {
    const next = theme === "dark" ? "light" : theme === "light" ? "system" : "dark";
    setTheme(next);
  }

  return (
    <aside className="flex h-screen w-14 shrink-0 flex-col items-center bg-sidebar py-3 gap-1">
      {/* Logo */}
      <Link
        href="/projects"
        className="flex h-8 w-8 items-center justify-center rounded-lg bg-sidebar-primary text-white mb-2"
      >
        <Sparkles size={16} />
      </Link>

      {/* Separator */}
      <div className="h-px w-6 bg-zinc-700 mb-1" />

      {/* Nav */}
      <nav className="flex flex-col items-center gap-1 flex-1">
        <NavItem
          href="/projects"
          icon={<FolderKanban size={20} />}
          label="Projects"
          active={pathname === "/projects"}
        />
        <NavItem
          href="/projects/new"
          icon={<Plus size={20} />}
          label="New Project"
          active={pathname === "/projects/new"}
        />
      </nav>

      {/* Bottom */}
      <div className="flex flex-col items-center gap-1">
        <button
          onClick={cycleTheme}
          className="flex h-10 w-10 items-center justify-center rounded-lg text-sidebar-foreground hover:text-zinc-200 transition-colors duration-150"
          title={`Theme: ${theme}`}
        >
          {resolvedTheme === "dark" ? <Moon size={20} /> : <Sun size={20} />}
        </button>
        <button
          className="flex h-10 w-10 items-center justify-center rounded-lg text-sidebar-foreground hover:text-zinc-200 transition-colors duration-150"
          title="Settings"
        >
          <Settings size={20} />
        </button>
      </div>
    </aside>
  );
}

export function AppShell({ children }: AppShellProps) {
  const pathname = usePathname();
  const isWorkspace = pathname?.startsWith("/projects/") && pathname !== "/projects/new";

  return (
    <div className="flex h-screen bg-background">
      <IconRail />
      {isWorkspace ? (
        <>{children}</>
      ) : (
        <main className="flex-1 overflow-y-auto">{children}</main>
      )}
    </div>
  );
}
```

Key changes:
- 224px sidebar → 56px (w-14) icon rail
- Icon-only nav with tooltips (via `title`)
- Always-dark sidebar (`bg-sidebar`)
- Active indicator: 3px violet bar on left
- Theme toggle button at bottom
- Workspace pages still render children directly (but now with icon rail visible)

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/layout/AppShell.tsx
git commit -m "feat: replace sidebar with slim icon rail navigation"
```

---

### Task 5: Button Component Update

**Files:**
- Modify: `frontend/src/components/ui/button.tsx`

- [ ] **Step 1: Update button variants — remove scale transforms, update colors**

Replace the `buttonVariants` definition:

```tsx
const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-md text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 cursor-pointer",
  {
    variants: {
      variant: {
        default:
          "bg-primary text-primary-foreground shadow-sm hover:opacity-90",
        outline:
          "border border-border bg-transparent hover:bg-secondary hover:text-secondary-foreground",
        secondary:
          "bg-secondary text-secondary-foreground hover:bg-secondary/80",
        ghost: "hover:bg-secondary hover:text-secondary-foreground",
        destructive:
          "bg-destructive text-destructive-foreground shadow-sm hover:opacity-90",
        link: "text-primary underline-offset-4 hover:underline",
      },
      size: {
        xs: "h-6 rounded-md px-2 text-xs",
        sm: "h-7 rounded-md px-3",
        default: "h-8 px-3 py-1.5",
        lg: "h-9 rounded-md px-4",
        icon: "size-8",
        "icon-xs": "size-6",
        "icon-sm": "size-7",
        "icon-lg": "size-9",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);
```

Key changes:
- Removed `hover:scale-[1.02] active:scale-[0.97]`
- Changed `transition-all` → `transition-colors` (performance)
- Added `focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2`
- Added `cursor-pointer`
- Outline variant: `hover:bg-accent` → `hover:bg-secondary` (more neutral hover)
- Ghost variant: same treatment
- Default/destructive: use `hover:opacity-90` instead of color shift

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/button.tsx
git commit -m "feat: update button styles — remove scale, add focus rings"
```

---

### Task 6: Card Component Update

**Files:**
- Modify: `frontend/src/components/ui/card.tsx`

- [ ] **Step 1: Remove glassmorphism from Card**

Change the Card component's className from:
```
"bg-card/80 backdrop-blur-sm text-card-foreground border rounded-xl transition-all duration-300"
```
to:
```
"bg-card text-card-foreground border border-border rounded-lg shadow-sm transition-all duration-200"
```

Key changes:
- `bg-card/80 backdrop-blur-sm` → `bg-card` (solid background, no blur)
- `rounded-xl` → `rounded-lg` (8px instead of 12px)
- Added `border-border` explicitly
- Added `shadow-sm` for subtle elevation
- `duration-300` → `duration-200`

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/card.tsx
git commit -m "feat: card — remove glassmorphism, add subtle shadow"
```

---

### Task 7: Badge Component Update

**Files:**
- Modify: `frontend/src/components/ui/badge.tsx`

- [ ] **Step 1: Update badge variants with new color system**

Replace the `badgeVariants` definition:

```tsx
const badgeVariants = cva(
  "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium leading-none",
  {
    variants: {
      variant: {
        default: "bg-primary/10 text-primary",
        secondary: "bg-secondary text-secondary-foreground",
        destructive: "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-400",
        outline: "border border-border text-foreground",
        success: "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400",
        warning: "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400",
        ghost: "bg-muted text-muted-foreground",
      },
    },
    defaultVariants: { variant: "default" },
  }
);
```

Key changes:
- `default`: `bg-primary text-primary-foreground` → `bg-primary/10 text-primary` (softer, tinted)
- `destructive`: solid red → light red tint with dark mode variant
- `success`: `bg-[oklch(...)]` → emerald tint with dark mode variant
- `warning`: amber tint with dark mode variant
- `text-[10px]` → `text-xs` (slightly larger, more readable)

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/badge.tsx
git commit -m "feat: badge — update variants with tinted light/dark colors"
```

---

### Task 8: Progress Bar Update

**Files:**
- Modify: `frontend/src/components/ui/progress.tsx`

- [ ] **Step 1: Update progress bar to use accent color (Cyan)**

Change the fill className from:
```
"h-full rounded-full bg-primary transition-all duration-300"
```
to:
```
"h-full rounded-full bg-accent transition-all duration-500 ease-out"
```

Also update the track from `bg-muted` to `bg-secondary`:
```
"h-1.5 w-full rounded-full bg-secondary overflow-hidden"
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/ui/progress.tsx
git commit -m "feat: progress bar — use accent (cyan) fill, secondary track"
```

---

### Task 9: StepIndicator Update

**Files:**
- Modify: `frontend/src/components/wizard/StepIndicator.tsx`

- [ ] **Step 1: Update StepIndicator to remove glow/pulse animations and OKLCH colors**

Replace the entire component:

```tsx
"use client";

import { useWizardStore } from "@/lib/stores/wizard-store";
import { Check, AlertTriangle, Lock } from "lucide-react";
import { cn } from "@/lib/utils";
import { useStepBlockers } from "@/lib/hooks/use-step-blockers";

export function StepIndicator() {
  const { data, activeStep, setActiveStep, visibleSteps } = useWizardStore();
  const steps = visibleSteps();
  const activeIdx = steps.findIndex((s) => s.key === activeStep);

  const { isBlocked, openClarifications, pendingDecisions } =
    useStepBlockers(activeStep);

  let blockerBadge = "";
  if (isBlocked) {
    const parts: string[] = [];
    if (openClarifications > 0) parts.push(`${openClarifications} Klaerung${openClarifications > 1 ? "en" : ""}`);
    if (pendingDecisions > 0) parts.push(`${pendingDecisions} Entscheidung${pendingDecisions > 1 ? "en" : ""}`);
    blockerBadge = parts.join(", ") + " offen";
  }

  return (
    <div className="px-6 py-4 border-b border-border bg-card">
      <div className="flex items-center">
        {steps.map((step, i) => {
          const stepData = data?.steps[step.key];
          const isCompleted = !!stepData?.completedAt;
          const isActive = activeStep === step.key;
          const isAfterBlocked = isBlocked && i > activeIdx;
          const isLocked = !isCompleted && !isActive && isAfterBlocked;

          const canClick = isCompleted || isActive || (!isAfterBlocked && !isLocked);

          return (
            <div key={step.key} className="flex items-center" style={{ flex: i < steps.length - 1 ? 1 : "none" }}>
              <button
                onClick={() => canClick && setActiveStep(step.key)}
                className={cn("flex flex-col items-center gap-1 group", !canClick && "cursor-not-allowed")}
                disabled={!canClick}
              >
                <div
                  className={cn(
                    "flex h-7 w-7 items-center justify-center rounded-full text-xs font-medium transition-colors duration-150",
                    isCompleted && "bg-accent text-accent-foreground",
                    isActive && !isCompleted && !isBlocked && "bg-primary text-primary-foreground ring-2 ring-primary/30",
                    isActive && isBlocked && "bg-amber-500 text-white ring-2 ring-amber-500/30",
                    isLocked && "bg-muted text-muted-foreground/50",
                    !isActive && !isCompleted && !isLocked && "bg-secondary text-muted-foreground group-hover:bg-secondary/80"
                  )}
                >
                  {isCompleted ? (
                    <Check size={13} />
                  ) : isActive && isBlocked ? (
                    <AlertTriangle size={13} />
                  ) : isLocked ? (
                    <Lock size={11} />
                  ) : (
                    i + 1
                  )}
                </div>
                <span
                  className={cn(
                    "text-[9px] whitespace-nowrap transition-colors",
                    isActive && isBlocked && "text-amber-600 dark:text-amber-400 font-semibold",
                    isActive && !isBlocked && "text-primary font-semibold",
                    isCompleted && !isActive && "text-accent",
                    isLocked && "text-muted-foreground/50",
                    !isActive && !isCompleted && !isLocked && "text-muted-foreground"
                  )}
                >
                  {step.label}
                </span>
                {isActive && isBlocked && (
                  <span className="text-[8px] text-amber-700 dark:text-amber-400 bg-amber-100 dark:bg-amber-500/15 px-1.5 py-0.5 rounded-full whitespace-nowrap">
                    {blockerBadge}
                  </span>
                )}
              </button>
              {i < steps.length - 1 && (
                <div className={cn(
                  "h-[2px] flex-1 mx-1 transition-colors",
                  isCompleted ? "bg-accent" : "bg-border"
                )} />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
```

Key changes:
- `bg-card/50` → `bg-card` (solid)
- `bg-[oklch(0.65_0.15_160)]` → `bg-accent` (uses token)
- `animate-glow-pulse` removed
- `animate-[pulse-amber_2s_infinite]` removed
- Active step: just `ring-2 ring-primary/30` (static, no pulse)
- Blocked step: static `ring-2 ring-amber-500/30`
- Added `border-border` to the container border
- Blocker badge: light/dark variants with proper tint colors
- `bg-muted/50` → `bg-muted` for locked steps
- Connecting lines: `bg-muted` → `bg-border`
- `transition-all` → `transition-colors duration-150`

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wizard/StepIndicator.tsx
git commit -m "feat: step indicator — remove glow/pulse, use design tokens"
```

---

### Task 10: ChatPanel & ChatMessage Update

**Files:**
- Modify: `frontend/src/components/chat/ChatPanel.tsx`
- Modify: `frontend/src/components/chat/ChatMessage.tsx`

- [ ] **Step 1: Update ChatMessage — remove hover scale, update system message**

Replace `ChatMessage.tsx`:

```tsx
"use client";

import { cn } from "@/lib/utils";
import type { ChatMessage as ChatMessageType } from "@/lib/stores/project-store";
import { Bot, User, Info } from "lucide-react";

export function ChatMessage({ message }: { message: ChatMessageType }) {
  const isUser = message.role === "user";
  const isSystem = message.role === "system";

  if (isSystem) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-border bg-muted px-3 py-2 text-xs text-muted-foreground animate-fade-in">
        <Info size={12} className="shrink-0 text-muted-foreground" />
        <span>{message.content}</span>
      </div>
    );
  }

  return (
    <div className={cn("flex gap-2.5 animate-fade-in-up", isUser ? "flex-row-reverse" : "flex-row")}>
      <div className={cn(
        "flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs",
        isUser ? "bg-primary text-primary-foreground" : "bg-secondary text-secondary-foreground"
      )}>
        {isUser ? <User size={14} /> : <Bot size={14} />}
      </div>
      <div className={cn(
        "max-w-[80%] rounded-2xl px-3 py-2 text-sm leading-relaxed",
        isUser ? "rounded-tr-sm bg-primary text-primary-foreground" : "rounded-tl-sm bg-secondary text-secondary-foreground"
      )}>
        {message.content}
      </div>
    </div>
  );
}
```

Key changes:
- Avatar: removed `transition-transform duration-200 hover:scale-110`
- System message: `bg-muted/40` → `bg-muted`, `AlertCircle` → `Info`, `text-destructive` → `text-muted-foreground`
- Bubble shapes: `rounded-xl` → `rounded-2xl` (already was this, confirmed)

- [ ] **Step 2: Update ChatPanel — remove glow and gradient text**

In `ChatPanel.tsx`, make these changes:

Change the header section (lines 40-44) from:
```tsx
        <div className="flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-primary animate-glow-pulse">
          <Bot size={15} />
        </div>
        <span className="text-sm font-semibold bg-gradient-to-r from-foreground to-primary bg-clip-text text-transparent">Spec Agent</span>
```
to:
```tsx
        <div className="flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-primary">
          <Bot size={15} />
        </div>
        <span className="text-sm font-semibold text-foreground">Spec Agent</span>
```

Change the input container (line 69) from:
```tsx
          <div className="flex items-end gap-2 rounded-lg border bg-input p-2">
```
to:
```tsx
          <div className="flex items-end gap-2 rounded-lg border border-border bg-background p-2">
```

- [ ] **Step 3: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/chat/ChatMessage.tsx frontend/src/components/chat/ChatPanel.tsx
git commit -m "feat: chat — remove glow/gradient, use clean styling"
```

---

### Task 11: Projects Dashboard Update

**Files:**
- Modify: `frontend/src/app/projects/page.tsx`

- [ ] **Step 1: Update the dashboard page with clean SaaS styling**

Make these changes to `projects/page.tsx`:

1. In the "New Project" link (line 70), remove the scale transforms:
```tsx
// From:
className={cn(buttonVariants(), "gap-2 transition-all duration-200 hover:scale-[1.03] active:scale-[0.97]")}
// To:
className={cn(buttonVariants(), "gap-2")}
```

2. In the empty state icon container (line 82), remove glow:
```tsx
// From:
<div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10 text-primary animate-glow-pulse">
// To:
<div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10 text-primary">
```

3. In the Card component (line 101), simplify hover:
```tsx
// From:
className="flex flex-col h-full transition-all duration-300 hover:border-primary/50 hover:-translate-y-1 group-hover:shadow-lg group-hover:shadow-primary/10 animate-fade-in-up"
// To:
className="flex flex-col h-full hover:-translate-y-0.5 hover:shadow-md animate-fade-in-up"
```

4. Update animation delay (line 103):
```tsx
// From:
style={{ animationDelay: `${idx * 80}ms` }}
// To:
style={{ animationDelay: `${idx * 50}ms` }}
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/projects/page.tsx
git commit -m "feat: dashboard — remove glow/scale, clean card hover"
```

---

### Task 12: Workspace Page Update

**Files:**
- Modify: `frontend/src/app/projects/[id]/page.tsx`

- [ ] **Step 1: Update workspace header and remove activity bar**

Make these changes to `projects/[id]/page.tsx`:

1. Add the icon rail's explorer toggle import — add `PanelLeft` to the imports if not already present. Actually, the activity bar becomes unnecessary because the icon rail in AppShell now always shows. But the workspace still needs the explorer toggle. Since the workspace renders inside AppShell (which now provides the icon rail), we need to remove the separate activity bar and instead keep explorer toggle in the header.

Replace the header (lines 87-102):
```tsx
      <header className="flex shrink-0 items-center gap-3 border-b border-border bg-card px-4 py-2.5">
        <Link href="/projects" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft size={14} />
          Projects
        </Link>
        <ChevronRight size={14} className="text-muted-foreground" />
        <span className="text-sm font-medium truncate max-w-xs">{project?.name ?? "..."}</span>
        <div className="ml-auto flex items-center gap-2">
          <Button variant="ghost" size="sm" onClick={() => setShowExplorer(!showExplorer)} className="gap-1.5" title="Toggle Explorer">
            <FolderTree size={14} />
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setShowHandoff(true)} className="gap-1.5">
            <Bot size={14} /> Handoff
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setShowExport(true)} className="gap-1.5">
            <Download size={14} /> Export
          </Button>
        </div>
      </header>
```

2. Remove the Activity Bar div entirely (lines 105-117 — the `w-10 shrink-0 border-r` div). The explorer toggle has moved to the header.

3. Remove scale transforms from Handoff/Export buttons — the new header above already uses `variant="ghost"` without scale classes.

4. Update the explorer panel container (line 121):
```tsx
// From:
<div className="w-60 shrink-0 border-r overflow-hidden animate-slide-in-left">
// To:
<div className="w-60 shrink-0 border-r border-border bg-card overflow-hidden animate-slide-in-left">
```

5. Update the wizard area border (line 127):
```tsx
// From:
<div className="flex flex-1 flex-col overflow-hidden border-r">
// To:
<div className="flex flex-1 flex-col overflow-hidden">
```

6. Update the right panel border (line 135):
```tsx
// From:
<div className="flex-1 overflow-hidden flex flex-col border-l">
// To:
<div className="flex-1 overflow-hidden flex flex-col border-l border-border">
```

7. Update tab bar border (line 137):
```tsx
// From:
<div className="flex border-b">
// To:
<div className="flex border-b border-border bg-card">
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/projects/[id]/page.tsx
git commit -m "feat: workspace — remove activity bar, clean header, add border tokens"
```

---

### Task 13: Final Verification & Visual Check

**Files:** None (verification only)

- [ ] **Step 1: Run full build**

Run: `cd frontend && npm run build 2>&1 | tail -30`
Expected: Build succeeds with no errors

- [ ] **Step 2: Run lint**

Run: `cd frontend && npm run lint 2>&1 | tail -20`
Expected: No lint errors (warnings acceptable)

- [ ] **Step 3: Start dev server and verify visually**

Run: `cd frontend && npm run dev`

Manual checks:
- Light mode: white background, violet buttons, zinc text
- Dark mode (toggle via icon rail): dark zinc background, lighter violet, zinc-50 text
- Icon rail: always dark, violet active indicator
- Dashboard: clean cards with subtle shadow, cyan progress bars
- Workspace: clean header with breadcrumb, no activity bar, no glow effects
- Chat: clean bubbles, no gradient text
- Step indicator: no pulsing glow, static rings

- [ ] **Step 4: Commit any remaining fixes**

```bash
git add -A
git commit -m "fix: final cleanup from visual verification"
```
