# Asset-Bundle-Coverage-View Implementation Plan

> **Für agentische Worker:** ERFORDERLICHE SUB-SKILL: `superpowers:subagent-driven-development` (empfohlen) oder `superpowers:executing-plans` zur Task-für-Task-Ausführung. Steps nutzen Checkbox-Syntax (`- [ ]`) für Tracking.

**Goal:** Erweitere die `/asset-bundles`-Seite um einen Tab „Fehlend", der alle nicht-hochgeladenen `(step, field, value)`-Triples für `ARCHITECTURE/BACKEND/FRONTEND` aus `CATEGORY_STEP_CONFIG` listet und pro Triple einen kopierbaren `manifest.json`-Stub rendert.

**Architecture:** Reine Frontend-Erweiterung. Eine neue Pure-Util-Datei (Triple-Aggregation, Slugify, Manifest-Stub-Bau), zwei neue Components (`MissingBundleList`, `ManifestStubView`), Store um `activeTab` + `selectedMissingTripleId` erweitert, `AssetBundlesPage` bekommt Pill-Tabs zur Umschaltung. Backend bleibt unverändert.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript, Tailwind 4, Zustand 5, lucide-react. Pfad-Alias `@/*` → `./src/*`. Kein Unit-Test-Runner — Verifikation pro Task via `npm run build` (TypeScript-Check), abschließender Browser-Smoke gegen Akzeptanzkriterien.

**Quellen:**
- Feature-Doc: `docs/features/41-asset-bundle-coverage-view.md`
- Design-Spec: `docs/superpowers/specs/2026-05-04-asset-bundle-coverage-view-design.md`

**Stilkonventionen:**
- Raw Tailwind + `cn()` wie in `frontend/src/components/asset-bundles/BundleList.tsx` und `BundleDetail.tsx` — KEINE shadcn-Komponenten neu einführen.
- Lucide für Icons.
- `"use client"` an die Spitze jeder Datei mit Hooks/Events.
- Pill-Tab-Stil analog zum Format-Selector in `frontend/src/components/handoff/HandoffDialog.tsx`.

---

## File-Struktur

| Aktion | Datei | Verantwortung |
|---|---|---|
| Create | `frontend/src/lib/asset-bundles/possible-triples.ts` | Pure Util: Triple-Aggregation aus `CATEGORY_STEP_CONFIG`, Slugify, Diff, Gruppierung, Manifest-Stub-Bau |
| Modify | `frontend/src/lib/stores/asset-bundle-store.ts` | Erweiterung: `activeTab`, `selectedMissingTripleId`, `setActiveTab`, `selectMissingTriple`, `getMissingTriples` |
| Create | `frontend/src/components/asset-bundles/MissingBundleList.tsx` | Linke Spalte im „Fehlend"-Tab: gruppierte Liste mit sticky Step-Headern |
| Create | `frontend/src/components/asset-bundles/ManifestStubView.tsx` | Rechte Spalte im „Fehlend"-Tab: JSON-Codeblock + Copy-Button |
| Modify | `frontend/src/components/asset-bundles/AssetBundlesPage.tsx` | Pill-Tab-Header, Switch zwischen den beiden Spalten-Paaren |

Tasks 3 und 4 sind nach Task 2 unabhängig voneinander parallelisierbar. Task 5 (Page-Integration) baut auf 1-4. Task 6 ist abschließende manuelle Verifikation.

---

## Task 1: Pure Util `possible-triples.ts`

**Files:**
- Create: `frontend/src/lib/asset-bundles/possible-triples.ts`

- [ ] **Step 1: Datei anlegen mit komplettem Inhalt**

```ts
// frontend/src/lib/asset-bundles/possible-triples.ts
import type { StepType, AssetBundleManifest } from "@/lib/api";
import { CATEGORY_STEP_CONFIG } from "@/lib/category-step-config";

export type RelevantStep = "ARCHITECTURE" | "BACKEND" | "FRONTEND";

export type BundleTriple = {
  step: RelevantStep;
  field: string;
  value: string;
};

export type GroupedTriples = Record<RelevantStep, Record<string, BundleTriple[]>>;

export const RELEVANT_STEPS: ReadonlyArray<RelevantStep> = ["ARCHITECTURE", "BACKEND", "FRONTEND"];

/** Slugify identisch zur Backend-Regel (`assetBundleSlug` in domain/AssetBundle.kt). */
export function slugifyBundleValue(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

/** Deterministische Bundle-ID — match zum Backend-`assetBundleId`. */
export function bundleId(step: RelevantStep, field: string, value: string): string {
  return `${step.toLowerCase()}.${field}.${slugifyBundleValue(value)}`;
}

/** Aggregiert alle (step, field, value)-Triples aus allen Kategorien, dedupliziert über bundleId. */
export function getAllPossibleTriples(): BundleTriple[] {
  const seen = new Map<string, BundleTriple>();
  for (const config of Object.values(CATEGORY_STEP_CONFIG)) {
    for (const step of RELEVANT_STEPS) {
      const fields = config.fieldOptions[step];
      if (!fields) continue;
      for (const [field, values] of Object.entries(fields)) {
        for (const value of values) {
          const id = bundleId(step, field, value);
          if (!seen.has(id)) {
            seen.set(id, { step, field, value });
          }
        }
      }
    }
  }
  return Array.from(seen.values());
}

/** Filtert die Triples raus, deren bundleId bereits unter den hochgeladenen Bundles ist. */
export function diffMissingTriples(
  possible: BundleTriple[],
  uploaded: ReadonlyArray<{ id: string }>,
): BundleTriple[] {
  const uploadedIds = new Set(uploaded.map((b) => b.id));
  return possible.filter((t) => !uploadedIds.has(bundleId(t.step, t.field, t.value)));
}

/** Gruppiert Triples nach step → field. Values innerhalb eines Felds alphabetisch sortiert. */
export function groupByStepAndField(triples: BundleTriple[]): GroupedTriples {
  const out: GroupedTriples = { ARCHITECTURE: {}, BACKEND: {}, FRONTEND: {} };
  for (const t of triples) {
    const byField = out[t.step];
    (byField[t.field] ??= []).push(t);
  }
  for (const step of RELEVANT_STEPS) {
    for (const field of Object.keys(out[step])) {
      out[step][field].sort((a, b) => a.value.localeCompare(b.value));
    }
  }
  return out;
}

/** Erzeugt einen vollständigen, schemakonformen manifest.json-Stub mit Defaults. */
export function buildManifestStub(triple: BundleTriple, now: Date): AssetBundleManifest {
  const iso = now.toISOString();
  return {
    id: bundleId(triple.step, triple.field, triple.value),
    step: triple.step as StepType,
    field: triple.field,
    value: triple.value,
    version: "1.0.0",
    title: `${triple.value} Bundle`,
    description: `Skills, Commands und Agents für ${triple.value}`,
    createdAt: iso,
    updatedAt: iso,
  };
}
```

**Hinweis zur Type-Compat:** `RelevantStep` ist eine echte Untermenge von `StepType` aus `lib/api.ts`. Der Cast `triple.step as StepType` in `buildManifestStub` ist trivial korrekt — TS akzeptiert auch implizit, je nach `StepType`-Definition. Falls der Cast als Lint-Warnung erscheint: weglassen.

- [ ] **Step 2: Build-Verifikation**

Run: `cd frontend && npm run build`
Expected: Build läuft ohne neue TypeScript-Errors. Output endet mit `✓ Compiled successfully`. Falls Errors zu Imports von `CATEGORY_STEP_CONFIG` oder `AssetBundleManifest`: Datei-Pfade gegen `@/lib/category-step-config` und `@/lib/api` prüfen.

- [ ] **Step 3: Optionaler Sanity-Check (manuell, nicht blockierend)**

Falls du dir bei der Triple-Anzahl unsicher bist: in einer beliebigen Component temporär `console.log(getAllPossibleTriples().length)` einfügen, dev-Server starten, im Browser-Devtools die Zahl checken (~50 erwartet), Log wieder entfernen.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/asset-bundles/possible-triples.ts
git commit -m "feat(asset-bundles): add possible-triples util for coverage view"
```

---

## Task 2: Store-Erweiterung

**Files:**
- Modify: `frontend/src/lib/stores/asset-bundle-store.ts`

- [ ] **Step 1: Imports erweitern**

Edit-Stelle: oberer Import-Block. Vorher:

```ts
import { create } from "zustand";
import {
  listAssetBundles,
  getAssetBundle,
  uploadAssetBundle,
  deleteAssetBundle,
  fetchAssetBundleFile,
  type AssetBundleListItem,
  type AssetBundleDetail,
  type StepType,
} from "@/lib/api";
```

Nachher (eine zusätzliche Import-Zeile):

```ts
import { create } from "zustand";
import {
  listAssetBundles,
  getAssetBundle,
  uploadAssetBundle,
  deleteAssetBundle,
  fetchAssetBundleFile,
  type AssetBundleListItem,
  type AssetBundleDetail,
  type StepType,
} from "@/lib/api";
import {
  diffMissingTriples,
  getAllPossibleTriples,
  type BundleTriple,
} from "@/lib/asset-bundles/possible-triples";
```

- [ ] **Step 2: State-Interface erweitern**

Edit-Stelle: `interface AssetBundleState`. Vorher endet der Block bei `clearError: () => void;`. Vor der schließenden `}` einfügen:

```ts
  // Coverage-View
  activeTab: "uploaded" | "missing";
  selectedMissingTripleId: string | null;

  setActiveTab: (tab: "uploaded" | "missing") => void;
  selectMissingTriple: (id: string | null) => void;
  getMissingTriples: () => BundleTriple[];
```

Vollständiger neuer State-Block (zur Verifikation, was am Ende dasteht):

```ts
interface AssetBundleState {
  bundles: AssetBundleListItem[];
  selectedBundleId: string | null;
  selectedBundle: AssetBundleDetail | null;
  selectedFilePath: string | null;
  loadedFile: LoadedFile | null;
  loading: boolean;
  uploading: boolean;
  error: string | null;
  filterStep: StepType | "ALL";

  load: () => Promise<void>;
  setFilter: (step: StepType | "ALL") => void;
  select: (id: string | null) => Promise<void>;
  selectFile: (relativePath: string | null) => Promise<void>;
  upload: (file: File) => Promise<void>;
  delete: (step: StepType, field: string, value: string) => Promise<void>;
  clearError: () => void;

  // Coverage-View
  activeTab: "uploaded" | "missing";
  selectedMissingTripleId: string | null;

  setActiveTab: (tab: "uploaded" | "missing") => void;
  selectMissingTriple: (id: string | null) => void;
  getMissingTriples: () => BundleTriple[];
}
```

- [ ] **Step 3: Initial-State und Actions im `create`-Block ergänzen**

Edit-Stelle: innerhalb von `create<AssetBundleState>((set, get) => ({ ... }))`. Vor der schließenden `}))` einfügen — nach der `clearError`-Action:

```ts
  clearError() {
    set({ error: null });
  },

  // Coverage-View
  activeTab: "uploaded",
  selectedMissingTripleId: null,

  setActiveTab(tab) {
    set({ activeTab: tab });
  },

  selectMissingTriple(id) {
    set({ selectedMissingTripleId: id });
  },

  getMissingTriples() {
    return diffMissingTriples(getAllPossibleTriples(), get().bundles);
  },
```

(Die existierende `clearError`-Action bleibt unverändert — die Edits werden DIREKT DAHINTER eingefügt.)

- [ ] **Step 4: Build-Verifikation**

Run: `cd frontend && npm run build`
Expected: Build grün. Falls Type-Error „Property X is missing in type" auftaucht: Reihenfolge der State-Properties prüfen — alle drei Initial-Werte (`activeTab`, `selectedMissingTripleId`) und drei Methoden (`setActiveTab`, `selectMissingTriple`, `getMissingTriples`) müssen im `create`-Block stehen.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/stores/asset-bundle-store.ts
git commit -m "feat(asset-bundles): extend store for coverage-view tab and missing-triple selection"
```

---

## Task 3: `MissingBundleList.tsx` (linke Spalte)

**Files:**
- Create: `frontend/src/components/asset-bundles/MissingBundleList.tsx`

- [ ] **Step 1: Datei anlegen mit komplettem Inhalt**

```tsx
// frontend/src/components/asset-bundles/MissingBundleList.tsx
"use client";

import { PackageOpen } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import {
  bundleId,
  groupByStepAndField,
  RELEVANT_STEPS,
} from "@/lib/asset-bundles/possible-triples";
import { cn } from "@/lib/utils";

export function MissingBundleList() {
  const selectedMissingTripleId = useAssetBundleStore((s) => s.selectedMissingTripleId);
  const selectMissingTriple = useAssetBundleStore((s) => s.selectMissingTriple);
  const getMissingTriples = useAssetBundleStore((s) => s.getMissingTriples);
  // bundles ist hier nicht direkt genutzt, aber die Subscription stellt sicher,
  // dass der Component re-rendert, wenn sich die Bundle-Liste ändert.
  useAssetBundleStore((s) => s.bundles);

  const missing = getMissingTriples();

  if (missing.length === 0) {
    return (
      <div className="p-4 text-sm text-muted-foreground">
        Vollständige Abdeckung — alle bekannten Triples haben ein Bundle.
      </div>
    );
  }

  const grouped = groupByStepAndField(missing);

  return (
    <div className="flex h-full flex-col overflow-y-auto">
      {RELEVANT_STEPS.map((step) => {
        const fields = grouped[step];
        const fieldNames = Object.keys(fields).sort();
        if (fieldNames.length === 0) return null;
        return (
          <section key={step}>
            <h2 className="sticky top-0 z-10 bg-background px-3 py-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground border-b">
              {step}
            </h2>
            {fieldNames.map((field) => (
              <div key={field}>
                <h3 className="bg-muted/30 px-3 py-1.5 text-xs font-medium text-muted-foreground">
                  {field} ({fields[field].length})
                </h3>
                {fields[field].map((t) => {
                  const id = bundleId(t.step, t.field, t.value);
                  return (
                    <button
                      key={id}
                      onClick={() => selectMissingTriple(id)}
                      className={cn(
                        "w-full border-b px-3 py-2.5 text-left transition-colors hover:bg-muted/50",
                        selectedMissingTripleId === id ? "bg-muted" : "",
                      )}
                    >
                      <div className="flex items-center gap-2 font-mono text-xs text-muted-foreground">
                        <PackageOpen size={12} />
                        <span>{id}</span>
                      </div>
                      <div className="mt-1 text-sm font-medium">{t.value}</div>
                    </button>
                  );
                })}
              </div>
            ))}
          </section>
        );
      })}
    </div>
  );
}
```

**Hinweis:** `useAssetBundleStore((s) => s.bundles)` als „Subscription-only" — der Wert wird nicht in einer Variable gehalten, aber Zustand schreibt das Component bei `bundles`-Updates für ein Re-Render ein. Alternativ könnten alle Selectors über einen einzigen Store-Zugriff laufen — dieser Stil ist näher an `BundleList.tsx`.

- [ ] **Step 2: Build-Verifikation**

Run: `cd frontend && npm run build`
Expected: Build grün. Falls TS „Cannot find module" für `@/lib/asset-bundles/possible-triples`: Task 1 nicht committet/auf Branch — neu prüfen.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/asset-bundles/MissingBundleList.tsx
git commit -m "feat(asset-bundles): add MissingBundleList component for coverage-view"
```

---

## Task 4: `ManifestStubView.tsx` (rechte Spalte)

**Files:**
- Create: `frontend/src/components/asset-bundles/ManifestStubView.tsx`

- [ ] **Step 1: Datei anlegen mit komplettem Inhalt**

```tsx
// frontend/src/components/asset-bundles/ManifestStubView.tsx
"use client";

import { useState, useMemo } from "react";
import { Copy, Check } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import {
  bundleId,
  buildManifestStub,
  getAllPossibleTriples,
} from "@/lib/asset-bundles/possible-triples";
import { cn } from "@/lib/utils";

export function ManifestStubView() {
  const selectedMissingTripleId = useAssetBundleStore((s) => s.selectedMissingTripleId);
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">("idle");

  const triple = useMemo(() => {
    if (!selectedMissingTripleId) return null;
    return (
      getAllPossibleTriples().find(
        (t) => bundleId(t.step, t.field, t.value) === selectedMissingTripleId,
      ) ?? null
    );
  }, [selectedMissingTripleId]);

  if (!triple) {
    return (
      <div className="flex h-full items-center justify-center p-6 text-sm text-muted-foreground">
        Wähle links einen fehlenden Triple, um einen manifest.json-Stub zu generieren.
      </div>
    );
  }

  const stub = buildManifestStub(triple, new Date());
  const json = JSON.stringify(stub, null, 2);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(json);
      setCopyState("copied");
    } catch {
      setCopyState("failed");
    }
    setTimeout(() => setCopyState("idle"), 1500);
  }

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <header className="border-b px-4 py-3">
        <div className="font-mono text-xs text-muted-foreground">{stub.id}</div>
        <h2 className="mt-1 text-base font-semibold">{stub.title}</h2>
      </header>
      <div className="flex-1 space-y-3 overflow-y-auto p-4">
        <div className="relative rounded-md border bg-muted/30">
          <button
            onClick={handleCopy}
            className={cn(
              "absolute right-2 top-2 inline-flex items-center gap-1 rounded-md border bg-background px-2 py-1 text-xs hover:bg-muted",
              copyState === "copied" && "text-green-600",
              copyState === "failed" && "text-red-600",
            )}
          >
            {copyState === "copied" ? (
              <>
                <Check size={12} /> Kopiert
              </>
            ) : copyState === "failed" ? (
              "Kopieren fehlgeschlagen"
            ) : (
              <>
                <Copy size={12} /> Copy
              </>
            )}
          </button>
          <pre className="overflow-x-auto p-4 font-mono text-xs">{json}</pre>
        </div>
        <p className="text-xs text-muted-foreground">
          Lege diesen Inhalt als <code className="font-mono">manifest.json</code> im Root deines Bundle-Ordners ab.
          Lade den ZIP anschließend über den „Hochgeladen"-Tab hoch.
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Build-Verifikation**

Run: `cd frontend && npm run build`
Expected: Build grün.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/asset-bundles/ManifestStubView.tsx
git commit -m "feat(asset-bundles): add ManifestStubView with clipboard copy"
```

---

## Task 5: `AssetBundlesPage.tsx` — Pill-Tabs + Switch

**Files:**
- Modify: `frontend/src/components/asset-bundles/AssetBundlesPage.tsx`

Aktueller Inhalt (zur Orientierung — wird komplett ersetzt):

```tsx
"use client";

import { useEffect } from "react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { BundleList } from "./BundleList";
import { BundleDetail } from "./BundleDetail";

export function AssetBundlesPage() {
  const { load } = useAssetBundleStore();

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="flex h-screen flex-col">
      <header className="border-b px-6 py-4">
        <h1 className="text-xl font-semibold">Asset Bundles</h1>
        <p className="text-sm text-muted-foreground">
          Kuratierte Claude-Code Skills, Commands und Agents.
        </p>
      </header>
      <div className="flex flex-1 min-h-0">
        <aside className="w-96 border-r overflow-hidden">
          <BundleList />
        </aside>
        <main className="flex-1 overflow-hidden">
          <BundleDetail />
        </main>
      </div>
    </div>
  );
}
```

- [ ] **Step 1: Komplette Datei ersetzen**

```tsx
// frontend/src/components/asset-bundles/AssetBundlesPage.tsx
"use client";

import { useEffect } from "react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { BundleList } from "./BundleList";
import { BundleDetail } from "./BundleDetail";
import { MissingBundleList } from "./MissingBundleList";
import { ManifestStubView } from "./ManifestStubView";
import { cn } from "@/lib/utils";

export function AssetBundlesPage() {
  const load = useAssetBundleStore((s) => s.load);
  const bundles = useAssetBundleStore((s) => s.bundles);
  const activeTab = useAssetBundleStore((s) => s.activeTab);
  const setActiveTab = useAssetBundleStore((s) => s.setActiveTab);
  const getMissingTriples = useAssetBundleStore((s) => s.getMissingTriples);

  useEffect(() => {
    load();
  }, [load]);

  const missingCount = getMissingTriples().length;

  return (
    <div className="flex h-screen flex-col">
      <header className="border-b px-6 py-4">
        <h1 className="text-xl font-semibold">Asset Bundles</h1>
        <p className="text-sm text-muted-foreground">
          Kuratierte Claude-Code Skills, Commands und Agents.
        </p>
      </header>
      <div className="flex items-center gap-2 border-b px-6 py-2">
        <TabPill
          active={activeTab === "uploaded"}
          onClick={() => setActiveTab("uploaded")}
        >
          Hochgeladen ({bundles.length})
        </TabPill>
        <TabPill
          active={activeTab === "missing"}
          onClick={() => setActiveTab("missing")}
        >
          Fehlend ({missingCount})
        </TabPill>
      </div>
      <div className="flex flex-1 min-h-0">
        <aside className="w-96 border-r overflow-hidden">
          {activeTab === "uploaded" ? <BundleList /> : <MissingBundleList />}
        </aside>
        <main className="flex-1 overflow-hidden">
          {activeTab === "uploaded" ? <BundleDetail /> : <ManifestStubView />}
        </main>
      </div>
    </div>
  );
}

function TabPill({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "rounded-full px-3 py-1 text-xs font-medium transition-colors",
        active
          ? "bg-primary text-primary-foreground"
          : "bg-muted text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}
```

- [ ] **Step 2: Build-Verifikation**

Run: `cd frontend && npm run build`
Expected: Build grün, keine neuen Warnings für unused imports.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/asset-bundles/AssetBundlesPage.tsx
git commit -m "feat(asset-bundles): add tabs for uploaded/missing coverage in AssetBundlesPage"
```

---

## Task 6: Browser-Smoke (manuelle Verifikation)

**Files:** keine Änderungen.

Diese Task ist die abschließende manuelle Verifikation gegen die 8 Akzeptanzkriterien aus `docs/features/41-asset-bundle-coverage-view.md`. Kein Commit, kein Code.

**Voraussetzung:** Backend muss laufen, damit `GET /api/v1/asset-bundles` antwortet. Empfehlung: `./start.sh` aus dem Repo-Root.

- [ ] **Step 1: Dev-Server starten**

Run (aus Repo-Root): `./start.sh`
Erwartet: Backend auf `:8081`, Frontend auf `:3001` (laut `frontend/CLAUDE.md`).

Alternativ separat:
- Backend: `cd backend && ./gradlew bootRun --quiet`
- Frontend: `cd frontend && npm run dev`

- [ ] **Step 2: AC1 — Pill-Tabs sichtbar mit Counts**

Browser: `http://localhost:3001/asset-bundles` öffnen.

Erwartet:
- Header „Asset Bundles" + Untertitel sichtbar wie bisher.
- Direkt darunter Pill-Tabs „Hochgeladen (n)" und „Fehlend (m)" — `n` = Anzahl bestehender Bundles, `m` ≈ 50 minus n bei aktueller `CATEGORY_STEP_CONFIG`.
- Aktiver Tab in Primary-Farbe, inaktiver muted.

- [ ] **Step 3: AC2 + AC3 — Fehlend-Liste, Gruppierung, Filter**

Klick auf Tab „Fehlend".

Erwartet:
- Linke Spalte zeigt drei Sektionen in Reihenfolge: `ARCHITECTURE`, `BACKEND`, `FRONTEND`.
- Innerhalb jeder Sektion: Field-Sub-Header (z. B. `architecture (3)`, `database (5)`, `framework (5)`), darunter alphabetisch sortierte Triple-Items.
- Beim Scrollen bleibt der jeweils aktive Step-Header (`h2`) am oberen Rand sticky; Field-Header (`h3`) scrollen mit.
- Rechte Spalte zeigt Empty-State „Wähle links einen fehlenden Triple…".
- Bereits hochgeladene Triples (z. B. `frontend.framework.stitch`, falls hochgeladen) erscheinen NICHT in der Fehlend-Liste.

- [ ] **Step 4: AC4 — JSON-Stub mit Defaults**

Klick auf einen Triple, z. B. „Kotlin+Spring" unter `BACKEND → framework`.

Erwartet rechts:
- Header zeigt `backend.framework.kotlin-spring` und `Kotlin+Spring Bundle`.
- JSON-Codeblock mit:
  - `"id": "backend.framework.kotlin-spring"`
  - `"step": "BACKEND"`
  - `"field": "framework"`
  - `"value": "Kotlin+Spring"` (Original-Casing inkl. `+`)
  - `"version": "1.0.0"`
  - `"title": "Kotlin+Spring Bundle"`
  - `"description": "Skills, Commands und Agents für Kotlin+Spring"`
  - `createdAt`/`updatedAt` als plausibler ISO-Timestamp (heutiges Datum, Zulu).

- [ ] **Step 5: AC5 — Copy-Button**

Klick auf Button „Copy" rechts oben im Codeblock.

Erwartet:
- Button-Label switcht für ~1.5 s auf „✓ Kopiert" (grün), danach zurück zu „Copy".
- In einem Editor mit `Cmd+V` einfügen → der vollständige JSON-Stub erscheint, identisch formatiert (2-Space-Indent).
- Falls Browser/Origin-Setup `navigator.clipboard` blockiert: Button switcht auf „Kopieren fehlgeschlagen" (rot). Akzeptabel — JSON ist im Codeblock manuell selektierbar. (Lokal über `localhost:3001` sollte clipboard funktionieren.)

- [ ] **Step 6: AC6 — Dynamisches Verschwinden nach Upload**

Im Tab „Hochgeladen" über die bestehende Upload-UI ein Bundle hochladen, dessen Triple gerade noch in „Fehlend" stand. Falls keine Test-ZIP zur Hand: aus dem Repo `asset-bundles/stitch-frontend-bundle.zip` als Vorlage nutzen — manifest.json mit dem Stub aus Step 5 ersetzen, nur leere Verzeichnisse `skills/`, `commands/`, `agents/` daneben legen, neu zippen mit `cd my-bundle/ && zip -r ../my-bundle.zip . -x ".*"`.

Nach erfolgreichem Upload zurück auf Tab „Fehlend".

Erwartet:
- Das hochgeladene Triple ist NICHT mehr in der Liste.
- Tab-Count „Fehlend (m)" ist um 1 gesunken.
- Tab-Count „Hochgeladen (n)" ist um 1 gestiegen.
- Kein Page-Reload nötig.

- [ ] **Step 7: AC7 — bestehende Funktionen unverändert**

Tab „Hochgeladen" anklicken, ein bestehendes Bundle auswählen.

Erwartet:
- `BundleDetail` rendert wie zuvor (Header mit Title/Description/Trash-Button, Files-Liste, FileViewer).
- `BundleUpload` und Step-Filter im `BundleList` funktionieren unverändert.
- `DeleteBundleDialog` funktioniert unverändert.

- [ ] **Step 8: AC8 — Build-Sanity**

Run: `cd frontend && npm run build`
Expected: Build grün, keine neuen Warnings.

Run zusätzlich: `cd backend && ./gradlew test`
Expected: alle Backend-Tests grün (sollten unverändert sein, da kein Backend-Code geändert wurde).

- [ ] **Step 9: Wenn alles grün — Done**

Kein Commit. Erfolgsmarker für Plan-Abschluss.

---

## Self-Review (durchgelaufen vom Plan-Author)

**Spec-Coverage:** Alle 8 Akzeptanzkriterien aus dem Feature-Doc sind durch Task 6 (Steps 2-8) abgedeckt. Alle 5 Module aus der Spec werden in Tasks 1-5 erstellt/modifiziert. Edge-Cases sind im Code-Verhalten reflektiert (Slug-Kollision in `getAllPossibleTriples` per `Map.has`-Check, Clipboard-Failure im `handleCopy`-try/catch, Empty-State in beiden neuen Components).

**Placeholder-Scan:** Keine TBDs, TODOs, „implement later" oder „handle edge cases" ohne konkretes Verhalten. Alle Code-Steps zeigen vollen Code, keine Auslassungen.

**Type-Konsistenz:** `BundleTriple`, `RelevantStep`, `GroupedTriples` werden in Task 1 definiert und in Tasks 3+4 importiert. `bundleId`, `getAllPossibleTriples`, `groupByStepAndField`, `buildManifestStub`, `diffMissingTriples`, `RELEVANT_STEPS` — alle als Exports in Task 1, Verwendung konsistent benannt in Tasks 2-5. Store-Methoden `setActiveTab`, `selectMissingTriple`, `getMissingTriples` und State-Properties `activeTab`, `selectedMissingTripleId` werden in Task 2 als Interface deklariert UND in `create`-Body implementiert; in Tasks 3-5 mit identischen Namen referenziert.

**Subagent-Eignung:** Tasks 1, 2 sequenziell (Task 2 importiert Task-1-Exports). Tasks 3 und 4 nach Task 2 parallelisierbar — weder importiert vom anderen, beide nutzen Store + Util. Task 5 wartet auf Tasks 3+4 (importiert beide Components). Task 6 ist manuell und blockt nicht.
