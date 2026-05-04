# Asset-Bundle-Coverage-View — Design Spec

**Datum:** 2026-05-04
**Bezug:** Feature 41. Erweitert die Asset-Bundle-Admin-UI (`/asset-bundles`, Feature 34) um eine „Fehlend"-Ansicht aller Wizard-Wahl-Triples für `ARCHITECTURE/BACKEND/FRONTEND`, die noch kein Bundle in S3 haben, plus einen vorgenerierten `manifest.json`-Stub pro fehlendem Triple zum Copy&Paste in einen lokalen Bundle-Ordner.

## Motivation

Heute zeigt `/asset-bundles` ausschließlich hochgeladene Bundles. Curatoren haben zwei Pain-Points:

1. **Coverage-Lücke unsichtbar.** Die einzige Quelle der „möglichen" Triples ist `frontend/src/lib/category-step-config.ts` — ein Curator muss die Datei manuell durchlesen und gegen die Bundle-Liste abgleichen, um zu sehen, welche Frameworks/Optionen noch ein Bundle brauchen.
2. **Manifest-Erstellung fehleranfällig.** Bundle-ID-Format (`<step>.<field>.<slug>`), Casing-Regeln, Slugify-Regel (`[^a-z0-9]+ → -`) und das vollständige Schema müssen händisch zusammengesetzt werden. Ein Tippfehler im `id`-Feld führt zu Backend-Validation-Errors beim Upload.

## Ziel

In der bestehenden `/asset-bundles`-Seite ein zweiter Tab „Fehlend", der die Differenz aus *allen möglichen Triples* (aus `CATEGORY_STEP_CONFIG`) und *hochgeladenen Bundles* anzeigt. Pro fehlendem Triple rendert die rechte Detail-Spalte einen vollständigen, schemakonformen `manifest.json`-Stub mit Defaults, den der Curator per Klick in die Zwischenablage kopiert.

## Scope

### In Scope

- Neuer Tab „Fehlend" in `AssetBundlesPage`, neben dem bestehenden „Hochgeladen"-Tab.
- Pure Util-Modul für Triple-Aggregation, Diff, Slugify, Manifest-Stub-Generierung.
- Gruppierte Liste fehlender Triples nach `step → field`. Step-Header (`h2`) ist sticky beim Scrollen; Field-Header (`h3`) scrollt mit.
- Detail-Spalte mit Manifest-JSON-Codeblock und Copy-Button.
- Erweiterung des `useAssetBundleStore` um `activeTab` und `selectedMissingTripleId`.

### Out of Scope

- ZIP-Erstellung im Browser. Curator zippt lokal (Konvention aus Sub-Feature B).
- Editieren oder Erweitern von `CATEGORY_STEP_CONFIG` über die UI.
- Backend-Endpoint für mögliche Triples. Die Wizard-Optionsliste ist Frontend-Wahrheit; kein Sync-Bedarf.
- Persistenz des Tab-States über Page-Reloads.
- Filter/Suche innerhalb der Missing-Liste. Bei rund 50 Triples nicht nötig.
- Frontend-Unit-Tests. Frontend hat keinen Test-Runner.
- Backend-/Frontend-Slug-Regel-Parität als separater Test. Implizit verifiziert durch das Diff-Verhalten.

## Architektur

### Datenfluss

```
CATEGORY_STEP_CONFIG (statisch importiert)         listAssetBundles() (existierender Store-Loader)
        │                                                │
        ▼                                                ▼
getAllPossibleTriples()  ─────►  diff (by bundleId)  ◄──  bundles[]
                                       │
                                       ▼
                              missingTriples
                                       │
                              groupByStepAndField()
                                       │
                                       ▼
                          MissingBundleList (linke Spalte)
                                       │ select
                                       ▼
                          ManifestStubView  ◄─── buildManifestStub(triple, now)
                          (rechte Spalte)
```

Keine zusätzlichen API-Calls. Die Diff-Berechnung läuft synchron im Store-Selector und re-evaluiert sich automatisch, sobald der Store nach Upload/Delete `bundles` neu lädt.

### Module

#### `frontend/src/lib/asset-bundles/possible-triples.ts` (neu)

Pure Funktionen, dependency-frei (keine React/Store-Imports):

```ts
import type { StepType, AssetBundleManifest } from "@/lib/api";
import { CATEGORY_STEP_CONFIG } from "@/lib/category-step-config";

export type BundleTriple = {
  step: StepType;            // nur "ARCHITECTURE" | "BACKEND" | "FRONTEND"
  field: string;
  value: string;
};

export type GroupedTriples = Record<
  "ARCHITECTURE" | "BACKEND" | "FRONTEND",
  Record<string, BundleTriple[]>  // field → triples
>;

export const RELEVANT_STEPS: ReadonlyArray<"ARCHITECTURE" | "BACKEND" | "FRONTEND"> =
  ["ARCHITECTURE", "BACKEND", "FRONTEND"];

export function slugifyBundleValue(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

export function bundleId(step: BundleTriple["step"], field: string, value: string): string {
  return `${step.toLowerCase()}.${field}.${slugifyBundleValue(value)}`;
}

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
          // Slug-Kollision (zwei verschiedene Values mit gleichem Slug) ist in der
          // aktuellen Config nicht der Fall. Falls jemals: erste Eintragung gewinnt.
        }
      }
    }
  }
  return Array.from(seen.values());
}

export function diffMissingTriples(
  possible: BundleTriple[],
  uploaded: { id: string }[],
): BundleTriple[] {
  const uploadedIds = new Set(uploaded.map((b) => b.id));
  return possible.filter((t) => !uploadedIds.has(bundleId(t.step, t.field, t.value)));
}

export function groupByStepAndField(triples: BundleTriple[]): GroupedTriples {
  const out: GroupedTriples = { ARCHITECTURE: {}, BACKEND: {}, FRONTEND: {} };
  for (const t of triples) {
    const byField = out[t.step];
    (byField[t.field] ??= []).push(t);
  }
  // Felder alphabetisch, Values alphabetisch innerhalb jedes Felds
  for (const step of RELEVANT_STEPS) {
    for (const field of Object.keys(out[step])) {
      out[step][field].sort((a, b) => a.value.localeCompare(b.value));
    }
  }
  return out;
}

export function buildManifestStub(triple: BundleTriple, now: Date): AssetBundleManifest {
  const iso = now.toISOString();
  return {
    id: bundleId(triple.step, triple.field, triple.value),
    step: triple.step,
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

**Begründungen:**

- `now` als Parameter (nicht `new Date()` intern) → Funktion deterministisch, in Browser-Konsole sanity-checkbar.
- `value` bleibt im Original-Casing — der Backend-Validator vergleicht den Manifest-`value`-Wert gegen die Bundle-ID via Slugify, akzeptiert also `"Kotlin+Spring"`.
- `RELEVANT_STEPS` als Konstante → Single Source of Truth für die drei zugelassenen Steps.
- Slug-Kollision wird nicht explizit geloggt; aktuelle Config kollidiert nicht. Falls sich das ändert: Sichtbarkeit über Liste (zwei verschiedene Values mit gleicher ID erscheinen als ein Eintrag) — nicht silent.

#### `frontend/src/lib/stores/asset-bundle-store.ts` (geändert)

Erweiterung des bestehenden Stores:

```ts
type ActiveTab = "uploaded" | "missing";

interface AssetBundleStore {
  // ─── existierend ─────────────────────────────────────
  bundles: AssetBundleListItem[];
  selectedBundleId: string | null;
  filterStep: StepType | "ALL";
  loading: boolean;
  load: () => Promise<void>;
  setFilter: (s: StepType | "ALL") => void;
  select: (id: string | null) => void;

  // ─── neu ─────────────────────────────────────────────
  activeTab: ActiveTab;
  selectedMissingTripleId: string | null;  // bundleId-Form
  setActiveTab: (tab: ActiveTab) => void;
  selectMissingTriple: (id: string | null) => void;
  getMissingTriples: () => BundleTriple[];  // computed; ruft diffMissingTriples()
}
```

`getMissingTriples` ist eine Methode (nicht state) — sie liest `bundles` aus dem Store und ruft `diffMissingTriples(getAllPossibleTriples(), bundles)` synchron. Selector-Caching (`useMemo` o.ä.) ist in den Komponenten unnötig: die Diff-Berechnung über ~50 Triples × ~12 Bundles ist sub-millisekunden-schnell.

#### `frontend/src/components/asset-bundles/AssetBundlesPage.tsx` (geändert)

Bekommt einen Pill-Tab-Header zwischen `<header>` und `<div className="flex flex-1 …">`:

```tsx
<div className="border-b px-6 py-2 flex items-center gap-2">
  <TabPill active={activeTab === "uploaded"} onClick={() => setActiveTab("uploaded")}>
    Hochgeladen ({bundles.length})
  </TabPill>
  <TabPill active={activeTab === "missing"} onClick={() => setActiveTab("missing")}>
    Fehlend ({missingTriples.length})
  </TabPill>
</div>
```

Body switcht je nach `activeTab`:
- `uploaded` → bisheriges `<BundleList />` + `<BundleDetail />`.
- `missing` → neue `<MissingBundleList />` + `<ManifestStubView />`.

`TabPill` ist ein lokaler Helper in derselben Datei — Stil analog zum `Format`-Selector im `HandoffDialog` (`bg-primary text-primary-foreground` aktiv, `bg-muted text-muted-foreground` inaktiv, `rounded-full px-3 py-1 text-xs font-medium`).

#### `frontend/src/components/asset-bundles/MissingBundleList.tsx` (neu)

```tsx
"use client";

import { PackageOpen } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { groupByStepAndField, bundleId, RELEVANT_STEPS } from "@/lib/asset-bundles/possible-triples";
import { cn } from "@/lib/utils";

export function MissingBundleList() {
  const { selectedMissingTripleId, selectMissingTriple, getMissingTriples } = useAssetBundleStore();
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
            <h2 className="sticky top-0 bg-background z-10 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground border-b">
              {step}
            </h2>
            {fieldNames.map((field) => (
              <div key={field}>
                <h3 className="px-3 py-1.5 text-xs font-medium text-muted-foreground bg-muted/30">
                  {field} ({fields[field].length})
                </h3>
                {fields[field].map((t) => {
                  const id = bundleId(t.step, t.field, t.value);
                  return (
                    <button
                      key={id}
                      onClick={() => selectMissingTriple(id)}
                      className={cn(
                        "w-full text-left px-3 py-2.5 border-b transition-colors hover:bg-muted/50",
                        selectedMissingTripleId === id ? "bg-muted" : "",
                      )}
                    >
                      <div className="flex items-center gap-2 text-xs text-muted-foreground font-mono">
                        <PackageOpen size={12} />
                        <span>{id}</span>
                      </div>
                      <div className="font-medium text-sm mt-1">{t.value}</div>
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

#### `frontend/src/components/asset-bundles/ManifestStubView.tsx` (neu)

```tsx
"use client";

import { useState, useMemo } from "react";
import { Copy, Check } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { bundleId, buildManifestStub, getAllPossibleTriples } from "@/lib/asset-bundles/possible-triples";
import { cn } from "@/lib/utils";

export function ManifestStubView() {
  const { selectedMissingTripleId } = useAssetBundleStore();
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">("idle");

  const triple = useMemo(() => {
    if (!selectedMissingTripleId) return null;
    return getAllPossibleTriples().find(
      (t) => bundleId(t.step, t.field, t.value) === selectedMissingTripleId,
    ) ?? null;
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
        <h2 className="text-base font-semibold mt-1">{stub.title}</h2>
      </header>
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        <div className="relative rounded-md border bg-muted/30">
          <button
            onClick={handleCopy}
            className={cn(
              "absolute right-2 top-2 inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs",
              "bg-background border hover:bg-muted",
              copyState === "copied" && "text-green-600",
              copyState === "failed" && "text-red-600",
            )}
          >
            {copyState === "copied" ? <><Check size={12} /> Kopiert</>
              : copyState === "failed" ? "Kopieren fehlgeschlagen"
              : <><Copy size={12} /> Copy</>}
          </button>
          <pre className="overflow-x-auto p-4 text-xs font-mono">{json}</pre>
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

## Datentypen-Übersicht

Re-use aus `lib/api.ts`: `StepType`, `AssetBundleManifest`, `AssetBundleListItem`. Keine Anpassung.

Neu in `lib/asset-bundles/possible-triples.ts`:

```ts
type RelevantStep = "ARCHITECTURE" | "BACKEND" | "FRONTEND";
type BundleTriple = { step: RelevantStep; field: string; value: string };
type GroupedTriples = Record<RelevantStep, Record<string /* field */, BundleTriple[]>>;
```

Neu im Store:

```ts
type ActiveTab = "uploaded" | "missing";
```

## Edge-Cases

| Fall | Verhalten |
|---|---|
| Bundle existiert in S3 mit Triple, das **nicht** in `CATEGORY_STEP_CONFIG` steht | Erscheint im „Hochgeladen"-Tab (unverändert). Erscheint nicht im „Fehlend"-Tab. Diff-Richtung: `possible \ uploaded`. |
| Triple existiert in mehreren Kategorien (z. B. Kotlin+Spring in SaaS, API, Mobile, Desktop) | 1× in der Liste — `Map<bundleId, triple>` in `getAllPossibleTriples()` dedupliziert. |
| Slug-Kollision (zwei Values mit gleichem Slug) | In aktueller Config nicht der Fall. Falls jemals: erste Eintragung gewinnt; sichtbar als ein Eintrag in der Liste. |
| `bundles`-Fetch failt / `bundles` leer | „Fehlend" zeigt alle möglichen Triples. „Hochgeladen" zeigt seinen bestehenden Empty-State. Akzeptabel — die UI dramatisiert nicht. |
| `navigator.clipboard` nicht verfügbar (kein https / Permission denied) | `handleCopy` fängt den Throw, Button zeigt 1.5 s „Kopieren fehlgeschlagen". JSON bleibt sichtbar (manuelle Selektion möglich). Kein Crash. |
| `missingTriples.length === 0` | Empty-State-Text in der linken Spalte. Tab-Count zeigt `(0)`. |
| Tab-Wechsel mit selektiertem Item | Selektion wird im jeweiligen Tab-Slot persistent gehalten (`selectedBundleId` ↔ `selectedMissingTripleId` getrennt). Keine erzwungene Deselektion. |
| Erfolgreicher Upload eines bisher fehlenden Triples | Bestehender Store-Loader ruft nach `uploadAssetBundle()` `load()` auf → `bundles` aktualisiert → `getMissingTriples()` filtert das Triple weg → UI re-rendert. Kein zusätzlicher Code. |

## Tests

Frontend hat keinen Test-Runner. Verifikation:

- **Browser-Smoke** entsprechend der Akzeptanzkriterien aus dem Feature-Doc:
  1. `/asset-bundles` öffnen — Pill-Tabs sichtbar, Counts plausibel.
  2. Tab „Fehlend" — gruppierte Liste sichtbar, Section-Headers sticky beim Scrollen.
  3. Triple klicken — JSON-Stub rechts mit konformen Defaults und ISO-Timestamp.
  4. Copy-Button → Clipboard-Inhalt prüfen (per `Cmd+V` in Editor), „✓ Kopiert"-Feedback erscheint.
  5. Triple zurück in Tab „Hochgeladen" über Upload-UI hochladen → Triple verschwindet aus „Fehlend"-Liste, Tab-Count sinkt um 1.
- **Sanity-Check der Util** (einmalig): in DevTools-Konsole `getAllPossibleTriples().length` ausführen, Plausibilitäts-Check der Anzahl gegen die Config.
- `npm run build` muss grün bleiben.
- Backend-Tests (`./gradlew test`) müssen unverändert grün bleiben (kein Backend-Code geändert).

## Risiken & Mitigationen

| Risiko | Mitigation |
|---|---|
| Frontend-Slugify divergiert von Backend `assetBundleSlug` | Beide Implementierungen sind drei Zeilen, identisch dokumentiert. Diff-Verhalten in Browser-Smoke verifiziert die Parität implizit (hochgeladenes Bundle muss aus „Fehlend" verschwinden). |
| `CATEGORY_STEP_CONFIG` wird erweitert, ohne dass jemand an dieses Feature denkt | Erweiterungen erscheinen automatisch im „Fehlend"-Tab. Kein Pflegeaufwand. |
| User vergisst, `title`/`description` im kopierten Manifest anzupassen | Defaults sind bereits sinnvoll lesbar (`"Kotlin+Spring Bundle"`). Falls jemand mit unverändertem Default uploadet: weniger schlimm als ein syntax-fehlerhaftes Manifest. |
| Performance bei sehr großer Bundle-Anzahl | Diff ist O(possible × 1) mit Set-Lookup → linear in Triple-Anzahl. ~50 Triples × ~100 Bundles = trivial. Falls jemals 10k Bundles: Fokus ist dann sowieso ein anderer. |

## Reihenfolge der Implementierung

1. `lib/asset-bundles/possible-triples.ts` — pure Util, isoliert.
2. Store-Erweiterung in `lib/stores/asset-bundle-store.ts`.
3. `MissingBundleList.tsx` und `ManifestStubView.tsx`.
4. Tabs-Header und Switch in `AssetBundlesPage.tsx`.
5. `npm run build` als Smoke-Gate.
6. Browser-Smoke gegen Akzeptanzkriterien.
