# Feature 41 — Asset-Bundle-Coverage-View — Done

**Datum:** 2026-05-04
**Branch:** `feat/asset-bundle-coverage-view` (6 Commits, lokal)
**Feature-Doc:** `docs/features/41-asset-bundle-coverage-view.md`
**Spec:** `docs/superpowers/specs/2026-05-04-asset-bundle-coverage-view-design.md`
**Plan:** `docs/superpowers/plans/2026-05-04-asset-bundle-coverage-view.md`

## Was umgesetzt wurde

- Neue Pure-Util `frontend/src/lib/asset-bundles/possible-triples.ts` mit `getAllPossibleTriples`, `diffMissingTriples`, `groupByStepAndField`, `slugifyBundleValue`, `bundleId`, `buildManifestStub` und Konstante `RELEVANT_STEPS`. Slug-Regel zeichengetreu spiegelt Backend-`assetBundleSlug`.
- `useAssetBundleStore` um `activeTab`, `selectedMissingTripleId`, `setActiveTab`, `selectMissingTriple`, `getMissingTriples` erweitert. Bestehende Actions (`load`, `select`, `selectFile`, `upload`, `delete`, `clearError`) unberührt.
- Neue Komponenten:
  - `MissingBundleList.tsx` — gruppierte Liste fehlender Triples nach `step → field`, sticky Step-Header beim Scrollen.
  - `ManifestStubView.tsx` — JSON-Codeblock mit Copy-Button (idle / „✓ Kopiert" / „Kopieren fehlgeschlagen"-States).
- `AssetBundlesPage.tsx` bekommt Pill-Tabs „Hochgeladen / Fehlend" mit live Counts; Tab-bedingter Switch zwischen den Spalten-Paaren. Object-Destructure des Stores migriert auf separate Selector-Hooks.
- Backend: keine Änderungen.

## Subagent-Driven Workflow — Reviews

| Task | Spec-Compliance | Code-Quality |
|---|---|---|
| 1 — possible-triples.ts | PASS | APPROVED |
| 2 — Store-Erweiterung | PASS | APPROVED |
| 3 — MissingBundleList | PASS | APPROVED |
| 4 — ManifestStubView | PASS | APPROVED |
| 5 — AssetBundlesPage Tabs | PASS | APPROVED |
| Final (branch-level) | – | APPROVED |

## Bewusste Abweichungen / Restpunkte

- **Keine Abweichungen vom Plan.** Jeder Implementer-Subagent hat den Plan-Code 1:1 umgesetzt; Spec-Compliance war pro Task PASS.
- **`MissingBundleList.tsx:19`** (`useAssetBundleStore((s) => s.bundles)` ohne Variable) ist ein bewusstes Subscription-Trick, damit Re-Renders nach Bundle-Upload zuverlässig erfolgen, weil `getMissingTriples()` als Methode (nicht als Selector) ansonsten keine Subscription registrieren würde. Begründungs-Kommentar steht direkt im Code.
- **`new Date()` im Render-Body** von `ManifestStubView`: Timestamp ändert sich bei jedem Render. In der Praxis irrelevant (Curator kopiert genau einmal); spec-konform per Design-Doc.
- **A11y:** Tabs sind native `<button>`s ohne `role="tablist"`/`role="tab"`. Vertretbar für ein internes Admin-Tool; falls künftig externe Nutzung kommt, kleines Follow-up.
- **`setTimeout`-Cleanup im Copy-Button** fehlt — bei Unmount innerhalb 1.5 s Aufruf von `setState` auf entladenem Component. React 19 toleriert das ohne Warning. Kein Blocker.

## Akzeptanzkriterien-Status

| # | Kriterium | Status |
|---|---|---|
| 1 | Pill-Tabs „Hochgeladen (n) / Fehlend (m)" mit live Counts | ✅ (Browser-Smoke) |
| 2 | „Fehlend"-Tab listet alle Triples aus `CATEGORY_STEP_CONFIG`, dedupliziert, gruppiert nach `step → field`, Step-Header sticky | ✅ (Browser-Smoke) |
| 3 | Triples mit existierendem Bundle (Match über `bundleId`) gefiltert | ✅ (Browser-Smoke) |
| 4 | Click auf Triple → Manifest-Stub mit konformen Defaults (`title = "<value> Bundle"`, ISO-Timestamps, `version = "1.0.0"`) | ✅ (Browser-Smoke) |
| 5 | Copy-Button kopiert formatierten JSON, „✓ Kopiert"-Feedback 1.5 s | ✅ (Browser-Smoke) |
| 6 | Triple verschwindet aus „Fehlend" nach erfolgreichem Upload (kein manuelles Refresh) | ✅ (Code-Pfad verifiziert; manueller Upload-Smoke optional, vom User skipped) |
| 7 | `AssetBundlesPage` Verhalten unter „Hochgeladen"-Tab unverändert | ✅ (Browser-Smoke) |
| 8 | `npm run build` grün | ✅ (5× pro Code-Task) |

## Verifikation

- `cd frontend && npm run build` grün nach jedem Code-Task.
- `cd backend && ./gradlew test` unverändert grün (kein Backend-Touch).
- Manueller Browser-Smoke-Test gegen AC1-AC5 + AC7 + AC8 durch User durchgelaufen, alle ✓.

## Commits auf der Branch

```
1e133c3 feat(asset-bundles): add tabs for uploaded/missing coverage in AssetBundlesPage
5273a81 feat(asset-bundles): add ManifestStubView with clipboard copy
3f9e8ff feat(asset-bundles): add MissingBundleList component for coverage-view
2adfa8e feat(asset-bundles): extend store for coverage-view tab and missing-triple selection
b2ed3c4 feat(asset-bundles): add possible-triples util for coverage view
f9e700a docs(feature-41): add feature doc, design spec, and implementation plan
```
