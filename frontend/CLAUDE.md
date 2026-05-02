# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Siehe auch die Root-`CLAUDE.md` eine Ebene höher — sie enthält Sprachregel (Deutsch), Wizard-Flow, Agent-Marker-Protokoll, Persistenz-Layout und die generelle Architektur. Dieses Dokument ergänzt sie um frontend-spezifische Details.

## Commands (run from `frontend/`)

```bash
npm run dev       # Next.js dev server on port 3001
npm run build     # Production build (standalone output)
npm run start     # Serve production build
npm run lint      # ESLint (next/core-web-vitals + TS rules)
```

Kein Test-Runner konfiguriert — das Frontend hat derzeit keine Unit-Tests. UI-Verhalten im Browser prüfen.

## Stack-Details

- **Next.js 16** mit App Router (`src/app/`), Standalone-Output (siehe `next.config.ts`) für Docker-Deployment
- **React 19** mit Server/Client-Komponenten-Split — `"use client"`-Direktive an alles, was State, Effects oder Browser-APIs nutzt
- **Tailwind CSS 4** (PostCSS-Plugin, kein Config-File) — Theme in `src/app/globals.css` via `@theme`-Direktive mit oklch-Farben
  - Drei Token-Blöcke koexistieren in `globals.css`: das ursprüngliche `@theme {…}` (Block A — heute live nur für `--font-*` und `--animate-*`, die Color-Tokens sind dort dead-code), `@theme inline {…}` (Block B — mappt shadcn-Variablen-Namen auf raw CSS-Variablen) sowie `:root {…}` und `.dark {…}` (Block C — liefert die tatsächlichen oklch-Werte). **Color-Werte ausschliesslich in Block C ändern** — Änderungen in Block A wirken nicht, weil Block B sie überschreibt.
- **base-ui/react** (nicht shadcn) als Primitive-Bibliothek, kombiniert mit lokalen Variants via `class-variance-authority`
- **shadcn/ui** (Style `base-nova`, seit Feature 36) als Komponenten-Quelle für neue UI — koexistiert mit base-ui auf gemeinsamer Primitive-Schicht (`base-nova` baut auf `@base-ui/react`, nicht auf Radix). shadcn-Komponenten landen in `src/components/ui/` (`dialog`, `input`, `textarea`, `label` installiert). Bestehende base-ui-Komponenten (`button`, `card`, `badge`, `progress`) bleiben unverändert. Neue Komponenten via `npx shadcn@latest add <name>` — Code-Snippets aus der offiziellen shadcn-Doku (Radix-basiert) müssen ggf. auf base-ui-Imports umgeschrieben werden.
- **Rete.js 2** für Node-Editoren (Features-Graph, Spec-Flow)
- **Zustand 5** für globalen State, keine Kontext-Provider
- **lucide-react** für Icons, **shiki** für Syntax-Highlighting in der Datei-Vorschau

## Pfad-Alias

`@/*` → `./src/*` (siehe `tsconfig.json`). Immer den Alias nutzen, keine relativen `../../`-Pfade.

## Architektur

### App-Router-Struktur
- `src/app/layout.tsx` — wurzelt das App-Shell, setzt `lang="de"` und `className="dark"` (Dark-Mode ist default), lädt Inter + JetBrains Mono über `next/font/google`
- `src/app/page.tsx` — Einstiegspunkt
- `src/app/projects/page.tsx` — Projektliste
- `src/app/projects/new/page.tsx` — Neues Projekt
- `src/app/projects/[id]/page.tsx` — Workspace mit Wizard, Chat, Explorer, Tabs (Decisions/Clarifications/Tasks/Checks)

`AppShell` (in `components/layout/`) rendert die linke Icon-Rail und entscheidet anhand von `usePathname()`, ob die Workspace-Route Full-Bleed bekommt oder ob `<main>` mit Scroll-Container umwickelt wird.

### State-Management (Zustand)
Stores in `src/lib/stores/`:
- `project-store.ts` — aktives Projekt, Flow-State, Chat-Nachrichten
- `wizard-store.ts` — Wizard-Daten pro Step, aktiver Step, sichtbare Steps (abhängig von Kategorie), Feature-Graph-CRUD mit Zyklus-Schutz
- `decision-store.ts`, `clarification-store.ts`, `task-store.ts` — jeweils eigenes Entity-Loading und Mutation

Alle Stores laden initial über `apiFetch<T>()` aus `lib/api.ts`. Nach `reset()` muss der Store vor Wiederverwendung neu geladen werden — siehe `useEffect`-Kette in der Workspace-Page.

### API-Client
`src/lib/api.ts` ist der Single Source of Truth für:
1. Die `apiFetch<T>()`-Funktion (fetch-Wrapper mit JSON-Headern und Error-Unwrapping)
2. Alle TypeScript-Types für Backend-Domain-Modelle (`StepType`, `Project`, `Decision`, `Clarification`, `SpecTask`, `CheckResult`, `WizardFeature`, `WizardFeatureEdge`, ...)
3. Alle Endpoint-Wrapper-Funktionen (`getProject`, `saveWizardStep`, `proposeFeatures`, ...)

Neue Backend-Endpoints werden hier registriert — keine `fetch()`-Aufrufe ausserhalb dieser Datei.

`NEXT_PUBLIC_API_URL` steuert die Base-URL (Default `http://localhost:8081`).

### Wizard-System
- **Step-Reihenfolge**: definiert in `wizard-store.ts` (`WIZARD_STEPS`) und spiegelt die 9 Backend-Steps
- **Sichtbare Steps**: `lib/category-step-config.ts` mappt `Category` (SaaS, Mobile App, CLI Tool, Library, Desktop App, API) auf `visibleSteps`, `fieldOptions` und `allowedScopes`. Die Auswahl in Schritt IDEA steuert, welche späteren Steps erscheinen
- **Form-Rendering**: `WizardForm` enthält die `FORM_MAP` mit `<IdeaForm>`, `<ProblemForm>`, …, `<FeaturesForm>` usw. Jedes Step-Formular bekommt `projectId` und nutzt `useWizardStore` für Save/Next-Mutationen
- **Step-Blocker**: `useStepBlockers` (in `lib/hooks/`) prüft ungelöste Decisions und Clarifications für den aktiven Step und blockiert `Next`. `BlockerBanner` linkt auf den passenden Tab im rechten Panel
- **Deutsche Feld-Labels**: `lib/step-field-labels.ts` hält die UI-Texte pro Step-Feld. Spezifikationen liegen in Englisch im Backend, die Labels sind nur Anzeige

### Graph-Editoren (Rete.js)
Zwei separate Editoren mit jeweils eigener `editor.ts`:
- `components/spec-flow/` — statischer Überblick über den Wizard-Flow
- `components/wizard/steps/features/` — interaktiver Features-Graph (drag, connect, auto-arrange)

Muster: `editor.ts` exportiert `createXEditor(container)`, baut `NodeEditor` + `AreaPlugin` + `ConnectionPlugin` + `ReactPlugin` + `AutoArrangePlugin` auf und gibt einen Context mit `destroy`, `applyGraph`, `autoLayout`, Listenern zurück. Der Editor-Generic ist bewusst `any` — die `ClassicPreset.Node`-Subklassen sind nicht strukturell kompatibel mit dem von React-Preset erwarteten `ClassicScheme`. Entsprechende `eslint-disable-next-line`-Kommentare sind akzeptiert.

Zyklus-Schutz bei neuen Kanten: `lib/graph/cycleCheck.ts` (`wouldCreateCycle`) muss vor `addEdge` konsultiert werden. Der Store zeigt ein Banner bei abgelehnten Verbindungen.

### UI-Komponenten
- `components/ui/` — hauseigene Primitives (Button, Badge, Card, Progress). Button nutzt `cva` + `cn()` aus `lib/utils.ts`; neue Varianten dort ergänzen, nicht inline Tailwind in Call-Sites wiederholen
- Alle feature-spezifischen Komponenten liegen gruppiert (`chat/`, `checks/`, `clarifications/`, `decisions/`, `explorer/`, `export/`, `handoff/`, `layout/`, `spec-flow/`, `tasks/`, `wizard/`)

### Resizable-Panels
`lib/hooks/use-resizable.ts` + `components/layout/ResizeHandle.tsx` liefern drag-basiertes Resizen für das Wizard-Workspace. Breiten werden nicht persistiert — Default-Werte im Hook-Aufruf.

## Konventionen

- `"use client"` nur setzen, wenn wirklich nötig (Hooks, Event-Handler, Browser-APIs). Layout und statische Seiten bleiben Server-Components
- Icons ausschliesslich aus `lucide-react`
- Tailwind-Klassen via `cn()` mergen, nie als String-Interpolation
- Neue Farben / Radii / Fonts im `@theme`-Block in `globals.css` ergänzen, nicht als Hex-Literale streuen
- Features aus `docs/features/NN-name.md` (siehe Root-`CLAUDE.md`) — vor Implementierung die Spec schreiben
