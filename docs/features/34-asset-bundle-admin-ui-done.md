# Feature 34 — Done: Asset-Bundle-Admin-UI (Sub-Feature B)

**Datum abgeschlossen:** 2026-04-29
**Auf `main` ab Commit:** `797d70e`
**Spec:** [docs/superpowers/specs/2026-04-29-asset-bundle-admin-ui-design.md](../superpowers/specs/2026-04-29-asset-bundle-admin-ui-design.md)
**Plan:** [docs/superpowers/plans/2026-04-29-asset-bundle-admin-ui.md](../superpowers/plans/2026-04-29-asset-bundle-admin-ui.md)
**Handoff-Doku:** [docs/superpowers/asset-bundles-handoff.md](../superpowers/asset-bundles-handoff.md)

## Zusammenfassung

Admin-UI für Asset-Bundles implementiert: Backend bekommt drei schreibende Endpoints (`POST` Upload, `DELETE`, `GET /files/**`), eine ZIP-Validierungs-Pipeline (`AssetBundleZipExtractor` mit Manifest-Validation, Allowlist, Größenlimits, Zip-Bomb-Schutz, Auto-Filter für Mac/Win-Artefakte) und einen Service (`AssetBundleAdminService`), der den Upload als Clean-Wipe + Write orchestriert. Frontend bekommt eine neue Top-Level-Route `/asset-bundles` mit Liste, Detail-Panel mit File-Tree, File-Vorschau (Markdown via `react-markdown`, Code via `shiki`, Bilder via `<img>`), Upload-Drop-Zone und Delete-Confirmation-Dialog.

**Test-Status:** ~44 neue Backend-Tests grün (Unit gegen `InMemoryObjectStore`, MinIO-Integration, Controller via `@SpringBootTest`). Frontend-Browser-Smoke ausstehend (Post-Merge-Aufgabe — siehe Handoff-Doku).

**Commit-Range:** `ea004ac` (Validation-Exception-Klassen) bis `797d70e` (final-review-Lückenschluss). 19 Commits in Sub-Feature B.

## Geänderte / neue Dateien

**Backend (Production)**
- `domain/AssetBundleUpload.kt` (neu) — `AssetBundleUploadResult`
- `service/AssetBundleValidationExceptions.kt` (neu) — `MissingManifestException`, `InvalidManifestException`, `ManifestIdMismatchException`, `UnsupportedStepException`, `IllegalBundleEntryException`, `BundleTooLargeException`, `BundleFileNotFoundException`
- `service/AssetBundleZipExtractor.kt` (neu) — pure Validation-Pipeline (8 Schritte: ZIP öffnen → Auto-Filter → Path-Safety → Größenlimits → Manifest-Extract → Manifest-Validate → Top-Level-Allowlist → Return)
- `service/AssetBundleAdminService.kt` (neu) — orchestriert `extract → delete → writeBundle`
- `storage/AssetBundleStorage.kt` (erweitert) — `writeBundle(manifest, files)` (Manifest zuletzt), `delete(step, field, value)` (idempotent), `loadFileBytes(step, field, value, relativePath)`
- `api/AssetBundleController.kt` (erweitert) — `POST` Upload (`multipart/form-data`), `DELETE`, `GET /files/**` (catch-all-Routing für nested paths)

**Backend-Tests**
- `service/AssetBundleZipExtractorTest.kt` + `AssetBundleZipExtractorTestFixtures.kt` — ~20 Cases (happy + alle Validation-Pfade + Auto-Filter)
- `service/AssetBundleAdminServiceTest.kt` — Orchestrierungs-Tests
- `storage/AssetBundleStorageTest.kt` (erweitert) — ~7 neue Cases für `writeBundle`/`delete`/`loadFileBytes`
- `storage/AssetBundleStorageIntegrationTest.kt` (erweitert) — `writeBundle` + `delete` gegen MinIO
- `api/AssetBundleControllerTest.kt` (erweitert) — ~11 neue Cases (POST/DELETE/GET-Files)

**Frontend**
- `app/asset-bundles/page.tsx` (neu) — Server-Component-Wrapper
- `components/asset-bundles/AssetBundlesPage.tsx` (neu) — Liste, Detail-Panel, Upload, Delete-Dialog, Filter
- `components/AppShell.tsx` (erweitert) — neuer Nav-Item mit `Package`-Icon (lucide)
- `lib/stores/asset-bundle-store.ts` (neu) — Zustand-Store
- `lib/api.ts` (erweitert) — API-Wrapper für die drei neuen Endpoints + Re-Exports

## Akzeptanzkriterien (final geprüft)

| # | Kriterium | Status |
|---|---|---|
| 1 | `AssetBundleZipExtractor` validiert alle Fehlerklassen und filtert Mac/Win-Artefakte | ✅ |
| 2 | `AssetBundleStorage.writeBundle` schreibt Manifest zuletzt | ✅ Test mit zwischenzeitlichem `find()`-Check |
| 3 | `AssetBundleStorage.delete` entfernt alle Keys idempotent | ✅ |
| 4 | `AssetBundleStorage.loadFileBytes` liefert Bytes oder null | ✅ |
| 5 | `POST /api/v1/asset-bundles` mit gültigem ZIP → 201 + UploadResult | ✅ |
| 6 | `POST` mit ungültigem ZIP → 400 + `INVALID_BUNDLE` | ✅ inkl. fehlende-manifest.json + 413-Case (im Final-Review nachgereicht) |
| 7 | `POST` überschreibt existierendes Bundle (clean-wipe) | ✅ |
| 8 | `DELETE` → 204 oder 404 | ✅ |
| 9 | `GET …/files/**` → 200 mit korrektem Content-Type, 404 oder 400 | ✅ inkl. Backslash-Path-Traversal-Schutz |
| 10 | Integration-Test gegen MinIO grün für `writeBundle` + `delete` | ✅ |
| 11–15 | Frontend-Smoke-Kriterien (Liste, Upload, Vorschau, Delete-Dialog, Nav-Icon) | ⏸ Browser-Smoke ausstehend |
| 16 | Alle bestehenden Tests bleiben grün | ✅ |

## Abweichungen vom ursprünglichen Plan / Spec

1. **Final-Review-Lückenschluss in `797d70e`:** Vor Merge fehlten zwei Controller-Tests, um die Spec-Akzeptanzkriterien wörtlich abzudecken — `POST 400` für fehlende `manifest.json` (nur als „malformed JSON" gecovered) und `POST 413` für Größen-Überschreitung. Dazu Backslash-Path-Traversal-Defense im `/files/**`-Endpoint (Storage-Layer hätte `null` geliefert, aber Defense-in-Depth-Konsistenz mit dem ZIP-Extractor wurde angestrebt). Beide Lücken im Final-Review erkannt und in einem Commit geschlossen.

2. **`shiki`-`setHtml`-Bug (`5ec6c95`):** Erste Implementierung des `FileViewer` rief `shikiHighlighter.codeToHtml(...)` synchron in einem `useEffect` auf, was zu Hydration-Mismatch führte. Fix: async/await mit Loading-State.

3. **Bundle-Spaltenfilter erweitert (`11fac53`):** Initial wurde nur `step` als Filter exposed, im Refinement kam `field` als zweite Filter-Spalte dazu (Spec hatte „Filter: Step" minimal beschrieben — UI-Praxis zeigte, dass `field` zusätzlich nützlich ist).

4. **Sample-Manifest-ID dynamisch (`11fac53`):** Test-Fixture hatte initial eine hartcodierte ID, die bei Triple-Variation nicht mehr passte. Dynamische Berechnung via `assetBundleId(...)` aus den anderen Feldern.

5. **Test-Helper-Konsolidierung (`2635975`):** Drei Test-Klassen hatten initial dupliziertes `buildZip(...)`-Setup. Refactor in eine zentrale Top-Level-Fixture in `AssetBundleZipExtractorTestFixtures.kt`.

6. **URI-Anchor + Charsets im File-Endpoint (`90d5f93`):** Erste Version des `/files/**`-Endpoints zerlegte den Pfad mit `request.requestURI.substring(...)`-Slicing, das brach bei URL-encoded Slashes. Fix: über Spring's `pathMatcher.extractPathWithinPattern(...)` und expliziter `URLDecoder.decode(..., UTF_8)`. Außerdem fehlte für Text-Content-Types der `;charset=utf-8`-Suffix → wurde nachgereicht.

## Offene Punkte / Tech-Debt

1. **Browser-Smoke ausstehend (Akzeptanzkriterien #11-15):** Manueller Test gegen die fünf Frontend-Akzeptanzkriterien (Upload → Liste → Detail → File-Vorschau md/code/image → Delete-Confirmation → Filter). Beschrieben in der Handoff-Doku unter „Was offen ist". Test-ZIP: `asset-bundles/stitch-frontend-bundle.zip` im Repo-Root.

2. **`format`-Param am `/files/**`-Endpoint:** Nicht implementiert. Falls Curatoren mal "raw bytes als Download" statt "inline" wollen, wäre `?download=1` ein einfacher Switch.

3. **Bulk-Upload nicht implementiert:** Curatoren müssen Bundles einzeln hochladen. Bei initialem Bootstrap eines Bundles-Sets wäre Multi-File-Drop angenehm, war aber bewusst Out-of-Scope (YAGNI).

4. **Kein Diff-View zwischen Versionen:** Versionierung selbst ist Out-of-Scope (Single-Version-Overwrite reicht). Falls Multi-Version-Bundles eingeführt werden, wäre ein Diff-View ein folgender Wunsch.

5. **`shiki`-Bundle-Größe:** `shiki` zieht eine spürbare Menge JS in den Frontend-Bundle. Bei Performance-Engpass: Lazy-Loading via dynamic import nur für die Detail-Page.

6. **Frontend-Tests fehlen weiterhin:** Bewusst — kein Test-Runner konfiguriert. Browser-Smoke bleibt der Verifikations-Pfad.

## Commits (Auszug, chronologisch)

```
797d70e test+fix(asset-bundles-admin): close acceptance-criteria gaps from final review
5ec6c95 fix(asset-bundles-admin): drop synchronous setHtml in FileViewer effect
d7cff72 feat(asset-bundles-admin): bundle detail with file viewer and delete confirmation
6debc63 feat(asset-bundles-admin): bundle list with filter and ZIP upload
9c337d9 feat(asset-bundles-admin): /asset-bundles route shell + nav entry
8d50cf1 feat(asset-bundles-admin): zustand store for asset-bundle state
78070eb feat(asset-bundles-admin): frontend API client for asset-bundles
0789665 test(asset-bundles-admin): MinIO integration test for writeBundle and delete
90d5f93 fix(asset-bundles-admin): URI-anchor file path, charsets for text types, missing-bundle test
d697b00 feat(asset-bundles-admin): POST upload, DELETE, and GET file endpoints
04774fb feat(asset-bundles-admin): map bundle validation exceptions to HTTP responses
162703c feat(asset-bundles-admin): AssetBundleAdminService orchestrates upload
2c2d467 feat(asset-bundles-admin): write, delete, loadFileBytes on AssetBundleStorage
b423001 feat(asset-bundles-admin): size limits and zip-bomb protection in ZipExtractor
11fac53 fix(asset-bundles-admin): make sample-manifest id dynamic and broaden filter list
2635975 refactor(asset-bundles-admin): drop duplicated test helpers, use top-level fixtures
6d7ab7d feat(asset-bundles-admin): manifest and structure validation in ZipExtractor
5fd4f6c feat(asset-bundles-admin): AssetBundleZipExtractor happy path
ea004ac feat(asset-bundles-admin): add validation exception classes
1b9c421 docs(asset-bundles-admin): add design spec and implementation plan
```

## Verifikation

- `./gradlew test` grün (~44 neue Tests + Sub-Feature-A-Tests + Bestand).
- MinIO-Integration für `writeBundle` und `delete` validiert.
- Frontend-Build (`npm run build`) grün.
- Browser-Smoke (Akzeptanzkriterien #11-15) **noch ausstehend** — siehe Handoff-Doku, Sektion „Was offen ist".
