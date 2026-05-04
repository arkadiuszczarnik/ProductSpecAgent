# Feature 41 — Asset-Bundle-Coverage-View

**Phase:** Asset-Bundles (Folge-Feature zu 26/33/34)
**Abhängig von:** Feature 33 (Asset-Bundle-Storage-Foundation, done), Feature 34 (Asset-Bundle-Admin-UI, done)
**Aufwand:** S
**Design-Spec:** [`docs/superpowers/specs/2026-05-04-asset-bundle-coverage-view-design.md`](../superpowers/specs/2026-05-04-asset-bundle-coverage-view-design.md)

## Problem

Die Asset-Bundle-Admin-UI (`/asset-bundles`) zeigt heute nur Bundles, die bereits in S3 liegen. Curatoren haben keinen Überblick darüber, welche Wizard-Wahl-Triples `(step, field, value)` für `ARCHITECTURE/BACKEND/FRONTEND` theoretisch ein Bundle bekommen könnten und welche davon noch fehlen. Die einzige Quelle der „möglichen" Triples ist `frontend/src/lib/category-step-config.ts` — ein Curator muss die Datei manuell durchlesen und gegen die hochgeladene Bundle-Liste abgleichen, um zu sehen, was noch zu tun ist.

Zusätzlich: beim Anlegen eines neuen Bundles muss der Curator die korrekte Bundle-ID (`<step>.<field>.<slug>`), das passende `step`/`field`/`value`-Triple sowie ein konformes `manifest.json`-Schema händisch zusammensetzen. Das ist fehleranfällig (Casing, Slugify-Regel, Whitespace) und erzeugt Reibung im Curator-Workflow.

## Ziel

In der bestehenden `/asset-bundles`-Seite ein zusätzlicher Tab „Fehlend", der alle aus `CATEGORY_STEP_CONFIG` ableitbaren Triples für die drei relevanten Steps listet, abzüglich der bereits hochgeladenen. Pro fehlendem Triple wird per Klick rechts ein konformer `manifest.json`-Stub gerendert, den der Curator per Copy-Button in seinen lokalen Bundle-Ordner kopiert. Reine Frontend-Erweiterung — kein Backend-Change.

## Architektur

Siehe Design-Spec für das vollständige Bild. Kurzfassung:

- **Neu:** `lib/asset-bundles/possible-triples.ts` — pure Util mit `getAllPossibleTriples()`, `slugifyBundleValue()`, `bundleId()`, `groupByStepAndField()`, `buildManifestStub()`.
- **Neu:** `components/asset-bundles/MissingBundleList.tsx` — gruppierte Liste mit sticky Section-Headers.
- **Neu:** `components/asset-bundles/ManifestStubView.tsx` — JSON-Codeblock + Copy-Button.
- **Geändert:** `components/asset-bundles/AssetBundlesPage.tsx` — Pill-Tabs „Hochgeladen / Fehlend", Switch zwischen `BundleList`+`BundleDetail` und `MissingBundleList`+`ManifestStubView`.
- **Geändert:** `lib/stores/asset-bundle-store.ts` — `activeTab`, `selectedMissingTripleId`, abgeleiteter `missingTriples`-Selector.

## Datenmodell

Keine Änderungen am Backend-Domain-Modell. Frontend-Typen (analog zu `AssetBundleManifest`):

```ts
type BundleTriple = { step: "ARCHITECTURE" | "BACKEND" | "FRONTEND"; field: string; value: string };
type ManifestStub = AssetBundleManifest; // identisch zum Backend-Schema
```

Die Diff-Berechnung läuft über deterministische `bundleId(step, field, value)`-Werte — damit greift Slugify-Toleranz (Backend-Bundle `kotlin-spring` matcht Frontend-Triple `Kotlin+Spring`).

## API

Keine neuen Endpoints. Genutzt wird der bestehende `GET /api/v1/asset-bundles`.

## Akzeptanzkriterien

1. `/asset-bundles` zeigt Pill-Tabs „Hochgeladen (n) / Fehlend (m)" mit live Counts.
2. „Fehlend"-Tab listet alle `(step, field, value)`-Triples aus `CATEGORY_STEP_CONFIG` für `ARCHITECTURE/BACKEND/FRONTEND`, dedupliziert über Kategorien, gruppiert nach `step → field` (Step-Header sticky beim Scrollen).
3. Triples mit existierendem Bundle (Match über deterministische `bundleId`) werden gefiltert.
4. Click auf einen Missing-Triple zeigt rechts den `manifest.json`-Stub mit Defaults (`title = "<value> Bundle"`, `description = "Skills, Commands und Agents für <value>"`, `version = "1.0.0"`, echte ISO-Timestamps).
5. Copy-Button kopiert formatierten JSON (2-Space-Indent) in die Zwischenablage, Feedback „✓ Kopiert" für 1.5 s.
6. Nach erfolgreichem Upload eines bisher fehlenden Triples (über bestehende Upload-UI) verschwindet das Triple automatisch aus der Liste, sobald der Store `bundles` reloaded.
7. `AssetBundlesPage` bleibt ansonsten unverändert (Layout, Sidebar, bestehende Bundle-Detail-Funktionen).
8. `npm run build` läuft ohne neue Fehler.

## Out of Scope

- ZIP-Bauen im Browser (Curator zippt lokal, wie bestehende Konvention).
- Editieren der Optionsliste `CATEGORY_STEP_CONFIG`.
- Backend-Endpoint für mögliche Triples (YAGNI; Frontend-State ist Quelle).
- Persistenz des Tab-States über Reloads.
- Filter/Suche innerhalb der Missing-Liste (bei ~50 Triples nicht nötig).
- Frontend-Unit-Tests (kein Test-Runner konfiguriert; Browser-Smoke reicht).
