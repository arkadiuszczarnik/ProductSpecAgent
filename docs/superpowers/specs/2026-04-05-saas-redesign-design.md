# SaaS Frontend Redesign — Design Spec

**Datum:** 2026-04-05
**Ziel:** Das Frontend von einem dark-mode-only Glassmorphism-Design zu einem professionellen Modern Startup SaaS-Look transformieren (Linear/Vercel-Stil).

## Entscheidungen

| Aspekt | Entscheidung |
|--------|-------------|
| Stil | Modern Startup SaaS (Linear/Vercel) |
| Farben | Violet (#7C3AED) & Cyan (#06B6D4) auf Zinc-Basis |
| Mode | Light + Dark Mode (Light als Default) |
| Navigation | Slim Icon Rail (56px) |
| Workspace | Bestehendes 4-Panel-Layout beibehalten, visuell aufräumen |
| Abgrenzung | Subtle Shadows (Elevation-basiert) |
| Font | Plus Jakarta Sans (ersetzt Outfit) |

---

## 1. Design Tokens & Farbsystem

Alle Farben als CSS Custom Properties in `globals.css`. OKLCH wird durch Hex/RGB ersetzt für bessere Tooling-Kompatibilität.

### Light Mode (Default, `:root`)

| Token | Wert | Verwendung |
|-------|------|-----------|
| `--background` | `#FAFAFA` | Page-Hintergrund |
| `--foreground` | `#09090B` (Zinc-950) | Primärtext |
| `--card` | `#FFFFFF` | Karten, Panels |
| `--card-foreground` | `#09090B` | Text auf Karten |
| `--primary` | `#7C3AED` (Violet-600) | Buttons, aktive Nav, Links |
| `--primary-foreground` | `#FFFFFF` | Text auf Primary |
| `--secondary` | `#F4F4F5` (Zinc-100) | Sekundäre Flächen |
| `--secondary-foreground` | `#18181B` (Zinc-900) | Text auf Secondary |
| `--muted` | `#F4F4F5` | Disabled, Placeholder-BG |
| `--muted-foreground` | `#71717A` (Zinc-500) | Sekundärtext |
| `--accent` | `#06B6D4` (Cyan-500) | Fortschritt, Erfolg, Highlights |
| `--accent-foreground` | `#FFFFFF` | Text auf Accent |
| `--destructive` | `#EF4444` (Red-500) | Fehler, Löschen |
| `--destructive-foreground` | `#FFFFFF` | Text auf Destructive |
| `--border` | `#E4E4E7` (Zinc-200) | Trennlinien |
| `--input` | `#E4E4E7` | Input-Borders |
| `--ring` | `#7C3AED` | Focus-Ring |
| `--sidebar` | `#18181B` (Zinc-900) | Icon Rail Hintergrund |
| `--sidebar-foreground` | `#A1A1AA` (Zinc-400) | Icons inaktiv |
| `--sidebar-active` | `#7C3AED` | Icon aktiv |

### Dark Mode (`.dark`)

| Token | Wert | Verwendung |
|-------|------|-----------|
| `--background` | `#09090B` (Zinc-950) | Page-Hintergrund |
| `--foreground` | `#FAFAFA` (Zinc-50) | Primärtext |
| `--card` | `#18181B` (Zinc-900) | Karten |
| `--card-foreground` | `#FAFAFA` | Text auf Karten |
| `--primary` | `#8B5CF6` (Violet-500) | Buttons (heller für Kontrast) |
| `--primary-foreground` | `#FFFFFF` | Text auf Primary |
| `--secondary` | `#27272A` (Zinc-800) | Sekundäre Flächen |
| `--secondary-foreground` | `#FAFAFA` | Text auf Secondary |
| `--muted` | `#27272A` | Disabled |
| `--muted-foreground` | `#A1A1AA` (Zinc-400) | Sekundärtext |
| `--accent` | `#22D3EE` (Cyan-400) | Highlights (heller) |
| `--accent-foreground` | `#09090B` | Text auf Accent |
| `--destructive` | `#EF4444` | Fehler |
| `--destructive-foreground` | `#FFFFFF` | Text auf Destructive |
| `--border` | `#27272A` (Zinc-800) | Trennlinien |
| `--input` | `#27272A` | Input-Borders |
| `--ring` | `#8B5CF6` | Focus-Ring |
| `--sidebar` | `#09090B` | Icon Rail |
| `--sidebar-foreground` | `#71717A` | Icons inaktiv |
| `--sidebar-active` | `#8B5CF6` | Icon aktiv |

### Shadows (Elevation)

| Token | Wert |
|-------|------|
| `--shadow-xs` | `0 1px 2px rgba(0,0,0,0.05)` |
| `--shadow-sm` | `0 1px 3px rgba(0,0,0,0.1), 0 1px 2px rgba(0,0,0,0.06)` |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.07), 0 2px 4px rgba(0,0,0,0.06)` |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1), 0 4px 6px rgba(0,0,0,0.05)` |

Dark-Mode-Shadows nutzen geringere Opacity da der Hintergrund bereits dunkel ist.

### Entfernungen

- Noise-Overlay (`body::after` mit SVG fractal noise)
- Gradient-Mesh-Background (`body::before` mit radialen Gradienten)
- Glassmorphism (`backdrop-blur-sm`, `bg-*/80` Opacity)
- `glow-pulse` Animation
- Button-Scale-Transforms (`hover:scale-[1.02]`, `active:scale-[0.97]`)

### Typography

- Sans: `Plus Jakarta Sans` (Google Fonts, Weights 300–700) ersetzt `Outfit`
- Mono: `JetBrains Mono` bleibt
- Border-Radius: `--radius: 0.5rem` (8px, reduziert von 10px)

---

## 2. Icon Rail & Navigation

### Struktur

```
┌──────┬─────────────────────────────────────┐
│ 56px │         Main Content Area           │
│      │                                     │
│ Logo │                                     │
│ ──── │                                     │
│ 📋   │                                     │
│ ➕   │                                     │
│      │                                     │
│      │                                     │
│ 🌙   │                                     │
│ ⚙    │                                     │
└──────┴─────────────────────────────────────┘
```

### Icon Rail Details

- **Breite:** 56px, links fixiert, `position: fixed`
- **Hintergrund:** `--sidebar` — bleibt dunkel in beiden Modi
- **Logo:** 32x32px, `rounded-lg`, `bg-primary`, weißes Icon/Buchstabe
- **Separator:** 1px Linie in `Zinc-700`
- **Nav-Icons:** Lucide-Icons 24x24, Farbe `--sidebar-foreground`
- **Aktiver State:** Icon in `--sidebar-active` (Violet), 3px vertikaler Indikator-Bar links
- **Hover:** Icon wird `Zinc-200`, Transition 150ms
- **Tooltips:** Rechts erscheinend bei Hover mit Label-Text
- **Bottom-Bereich:** Dark Mode Toggle (`Sun`/`Moon`) + Settings (`Settings`)

### Icons

| Position | Icon (Lucide) | Label |
|----------|---------------|-------|
| Nav | `FolderKanban` | Projects |
| Nav | `Plus` | New Project |
| Bottom | `Sun` / `Moon` | Theme Toggle |
| Bottom | `Settings` | Settings |
| Workspace | `PanelLeft` | Toggle Explorer |

### Activity Bar Entfällt

Die Activity Bar (40px) wird entfernt. Deren Funktionen (Explorer-Toggle) wandern als Icon in die Rail.

### Responsive (< 768px)

Icon Rail wird zu einer Bottom-Bar (56px Höhe, horizontal), Icons horizontal angeordnet.

---

## 3. Projects Dashboard

### Layout

- Container: `max-w-6xl mx-auto px-8 py-8`
- Header: Titel + Subtitle + "New Project" Button
- Grid: `grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4`

### Header

- Titel: `text-2xl font-semibold text-foreground` — "Projects"
- Subtitle: `text-sm text-muted-foreground` — "{n} active projects"
- Button: Primary, `Plus`-Icon links, "New Project"

### Project Cards

- Container: `bg-card border border-border rounded-lg shadow-sm`
- Hover: `shadow-md translate-y-[-2px]` Transition 200ms
- Inhalt:
  - Icon (`FolderKanban`) + Titel (`font-semibold text-base`) in `flex items-center gap-3`
  - Datum: `text-xs text-muted-foreground`
  - Progress Bar: `h-1.5 rounded-full`, Track `bg-secondary`, Fill `bg-accent` (Cyan)
  - Prozent: `text-xs font-medium` rechts
  - Footer: Nächster Schritt `text-sm text-muted-foreground`, `ArrowRight`-Icon

### Empty State

- Zentriert, `FolderPlus`-Icon (48px, `text-muted-foreground`)
- Titel: "No projects yet" — `text-lg font-medium`
- Text: "Create your first product specification" — `text-sm text-muted-foreground`
- Primary Button: "Create Project"

### Loading

- 3 Skeleton-Cards: `animate-pulse bg-secondary rounded-lg h-40`

---

## 4. Workspace (Projekt-Editor)

### Gesamtstruktur

```
┌──────┬────────┬──────────────────────────┬──────────────┐
│ Rail │Explorer│     Wizard Content       │  Right Panel │
│ 56px │ 240px  │       flex-1             │   340px      │
│      │optional│                          │  resizable   │
└──────┴────────┴──────────────────────────┴──────────────┘
```

### Header (52px)

- `bg-card border-b border-border`
- Links: Breadcrumb — "Projects" (Link, `text-muted-foreground`) `/` Projektname (`font-medium`)
- Rechts: Ghost-Buttons — `Download` (Export), `Share` (Handoff)

### Explorer Panel (optional, 240px)

- `bg-card border-r border-border`
- Header: "Explorer" in `text-xs font-semibold uppercase tracking-wider text-muted-foreground`
- File-Tree: Lucide-Icons (`FileText`, `Folder`), `text-sm`
- Hover: `bg-secondary rounded-md`
- Aktive Datei: `bg-primary/10 text-primary`

### Wizard Area (flex-1)

- `bg-background`
- Content: `max-w-2xl mx-auto px-8 py-6`

### Step Indicator

- Horizontale Leiste mit Steps
- **Completed:** `bg-accent` (Cyan) Circle, `Check`-Icon (weiß)
- **Active:** `bg-primary` (Violet) Circle, Step-Nummer, `ring-2 ring-primary/30`
- **Blocked:** `bg-amber-500` Circle, `AlertTriangle`-Icon, `ring-2 ring-amber-500/30`
- **Locked:** `bg-muted` Circle, `Lock`-Icon, `text-muted-foreground`
- **Verbindungslinien:** `h-0.5`, completed `bg-accent`, pending `bg-border`
- **Labels:** `text-xs`, aktiv `font-medium text-foreground`, Rest `text-muted-foreground`
- Keine `glow-pulse` oder `pulse-amber` Animationen

### Step Content

- Formular-Container: `bg-card border border-border rounded-lg shadow-xs p-4`
- Inputs: `bg-background border border-input rounded-md h-10 px-3 text-sm`
- Focus: `ring-2 ring-ring border-transparent`
- Labels: `text-sm font-medium`

### Right Panel (340px, resizable 280–600px)

- `bg-card border-l border-border`
- Tab-Bar: `border-b border-border`, aktiver Tab `border-b-2 border-primary text-primary`
- Resize Handle: `w-1 hover:bg-primary/50` Transition 150ms

### Chat

- **User:** `bg-primary text-primary-foreground rounded-2xl rounded-tr-sm`
- **Bot:** `bg-secondary text-secondary-foreground rounded-2xl rounded-tl-sm`
- **System:** `bg-muted border border-border rounded-lg` mit `Info`-Icon
- **Input:** `bg-background border border-border rounded-lg`, Send-Button `bg-primary`

---

## 5. Komponenten

### Button

| Variant | Styling |
|---------|---------|
| `default` | `bg-primary text-primary-foreground shadow-sm hover:opacity-90` |
| `secondary` | `bg-secondary text-secondary-foreground hover:bg-secondary/80` |
| `outline` | `border border-border bg-transparent hover:bg-secondary` |
| `ghost` | `bg-transparent hover:bg-secondary` |
| `destructive` | `bg-destructive text-destructive-foreground hover:opacity-90` |
| `link` | `text-primary underline-offset-4 hover:underline` |

- Hover: `opacity-90`, Transition 150ms — kein Scale
- Active: `opacity-80`
- Focus: `ring-2 ring-ring ring-offset-2`
- Disabled: `opacity-50 pointer-events-none`

### Card

- `bg-card border border-border rounded-lg shadow-sm`
- Interaktiv: `hover:shadow-md hover:translate-y-[-1px]` Transition 200ms

### Badge

| Variant | Light | Dark |
|---------|-------|------|
| `default` | `bg-primary/10 text-primary` | gleich mit Dark-Tokens |
| `secondary` | `bg-secondary text-secondary-foreground` | gleich |
| `success` | `bg-emerald-50 text-emerald-700` | `bg-emerald-500/10 text-emerald-400` |
| `warning` | `bg-amber-50 text-amber-700` | `bg-amber-500/10 text-amber-400` |
| `destructive` | `bg-red-50 text-red-700` | `bg-red-500/10 text-red-400` |

Alle: `text-xs font-medium px-2 py-0.5 rounded-full`

### Progress Bar

- Track: `h-1.5 bg-secondary rounded-full`
- Fill: `bg-accent`, Transition `width 500ms ease`

### Inputs

- `h-10 rounded-md border border-input bg-background px-3 text-sm`
- Focus: `ring-2 ring-ring border-transparent`
- Placeholder: `text-muted-foreground`

---

## 6. Animationen

### Beibehaltene Animationen

| Animation | Dauer | Verwendung |
|-----------|-------|-----------|
| `fade-in-up` | 0.3s ease-out | Seiten-Eingang |
| `slide-in-right` | 0.3s ease-out | Panel open |
| `slide-in-left` | 0.3s ease-out | Panel close |
| `scale-in` | 0.2s ease-out | Modals, Dropdowns |
| Staggered fade | 50ms pro Item | Dashboard Cards |

### Entfernte Animationen

| Animation | Ersatz |
|-----------|--------|
| `glow-pulse` | Entfernt, kein Ersatz |
| `pulse-amber` | Subtiles `ring-2 ring-amber-500/30` (statisch) |
| Noise overlay | Entfernt |
| Gradient mesh | Entfernt |
| Button `scale` | `opacity` Transition |

### Neue Regel

- `prefers-reduced-motion: reduce` — alle Animationen auf `0.01ms` setzen
- Maximale Dauer: 400ms für komplexe Transitions

---

## 7. Dark Mode Toggle

- Position: Icon Rail, unten neben Settings
- Icons: `Sun` (Light aktiv) / `Moon` (Dark aktiv) aus Lucide
- Mechanismus: `class="dark"` auf `<html>`, gespeichert in `localStorage`
- System-Preference als Default: `prefers-color-scheme: dark`
- Neuer Hook/Utility: `useTheme()` mit `light | dark | system`

---

## 8. Dateien die geändert werden

| Datei | Änderungstyp |
|-------|-------------|
| `globals.css` | Komplett überarbeitet — neue Tokens, Animationen, Entfernungen |
| `layout.tsx` | Font-Swap Outfit → Plus Jakarta Sans |
| `AppShell.tsx` | Sidebar → Icon Rail, Activity Bar entfernt |
| `button.tsx` | Variants auf neue Tokens, Scale entfernt |
| `card.tsx` | Glassmorphism → Clean Shadow |
| `badge.tsx` | Neue Variant-Farben |
| `StepIndicator.tsx` | Glow/Pulse entfernt, Ring-Style |
| `ChatMessage.tsx` | Neue Bubble-Farben |
| `ChatPanel.tsx` | Glow entfernt, cleanes Styling |
| `projects/page.tsx` | Dashboard Cards neu gestylt |
| `projects/[id]/page.tsx` | Header, Layout-Anpassungen |
| Neu: `lib/hooks/useTheme.ts` | Dark Mode Toggle Hook |
