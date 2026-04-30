# Feature 33 — Done: Asset-Bundle-Storage-Foundation (Sub-Feature A)

**Datum abgeschlossen:** 2026-04-29
**Auf `main` ab Commit:** `19a853f`
**Spec:** [docs/superpowers/specs/2026-04-29-asset-bundle-storage-design.md](../superpowers/specs/2026-04-29-asset-bundle-storage-design.md)
**Plan:** [docs/superpowers/plans/2026-04-29-asset-bundle-storage.md](../superpowers/plans/2026-04-29-asset-bundle-storage.md)
**Handoff-Doku:** [docs/superpowers/asset-bundles-handoff.md](../superpowers/asset-bundles-handoff.md)

## Zusammenfassung

Backend-Foundation für Asset-Bundles in S3 implementiert: Domain-Modell, `AssetBundleStorage` mit `listAll`/`find`, REST-Endpoints für Listing und Detail, Doku-Update in `persistence.md`, ~17 Tests. Discovery erfolgt live via `objectStore.listCommonPrefixes("asset-bundles/", "/")` ohne Cache. Korrupte Manifeste werden mit Warnung geloggt und übersprungen, niemals propagiert.

**Architektur (wie geplant umgesetzt):** `AssetBundleController` → `AssetBundleStorage` → `ObjectStore` (aus Feature 31, S3 in Prod, In-Memory in Tests).

**Test-Status:** Alle Tests grün, inkl. Integration-Test gegen Testcontainers-MinIO (`AssetBundleStorageIntegrationTest`).

**Commit-Range:** `ee97279` (erste Domain-Klassen) bis `19a853f` (Folder-vs-Manifest-ID-Warnung). 14 Commits in Sub-Feature A.

## Geänderte / neue Dateien

**Backend (Production)**
- `domain/AssetBundle.kt` (neu) — `AssetBundleManifest`, `AssetBundleFile`, `AssetBundle`, plus `assetBundleId(step, field, value)`-Helper und `assetBundleSlug()`
- `storage/AssetBundleStorage.kt` (neu) — `listAll()`, `find(step, field, value)`, Content-Type-Ableitung aus File-Endung
- `api/AssetBundleController.kt` (neu) — `GET /api/v1/asset-bundles` (Liste mit `fileCount`), `GET /api/v1/asset-bundles/{step}/{field}/{value}` (Detail, 404 bei Miss, 400 bei invalidem Enum)
- `service/AssetBundleNotFoundException.kt` (neu) + global Exception-Handler-Mapping → `404`

**Backend-Tests**
- `storage/AssetBundleStorageTest.kt` — Unit-Tests gegen `InMemoryObjectStore` (~10 Cases)
- `storage/AssetBundleStorageIntegrationTest.kt` — Integration-Test gegen Testcontainers-MinIO
- `api/AssetBundleControllerTest.kt` — Controller-Tests via `@SpringBootTest` + `@AutoConfigureMockMvc`

**Doku**
- `docs/architecture/persistence.md` — Abschnitt "Asset-Bundles in S3" mit Layout-Beschreibung und `aws s3 sync`-Beispielkommando

## Akzeptanzkriterien (final geprüft)

| # | Kriterium | Status |
|---|---|---|
| 1 | `AssetBundle*`-Domain-Klassen vorhanden, `kotlinx.serialization`-annotiert | ✅ |
| 2 | `AssetBundleStorage.listAll()` liefert alle Manifeste aus `asset-bundles/`-Prefix | ✅ Unit + Integration |
| 3 | `AssetBundleStorage.find(triple)` liefert Bundle inkl. Files (relative Pfade) | ✅ |
| 4 | Korrupte Manifeste werden geloggt und übersprungen, nicht propagiert | ✅ |
| 5 | `GET /api/v1/asset-bundles` listet alle Bundles | ✅ |
| 6 | `GET /api/v1/asset-bundles/{step}/{field}/{value}` liefert Detail oder 404 | ✅ |
| 7 | Invalides `step`-Enum → 400 | ✅ |
| 8 | Integration-Test gegen Testcontainers-MinIO grün | ✅ |
| 9 | `persistence.md` um Asset-Bundle-Layout ergänzt | ✅ Commit `d4489db` |
| 10 | Alle bestehenden Tests bleiben grün | ✅ |

## Abweichungen vom ursprünglichen Plan / Spec

1. **Folder-vs-Manifest-ID-Warnung als separater Final-Commit (`19a853f`):** Spec verlangte explizit eine Warnung, wenn `manifest.id` nicht zum Folder-Namen in S3 passt (Bundle wird trotzdem akzeptiert, weil Folder-Name = Speicherort = Source of Truth für Lokation). Initial nicht implementiert, im Final-Review erkannt und nachgereicht. Zwei Regressions-Tests (`listAll` und `find`) decken den Pfad ab.

2. **Refactoring-Splits:** `6e6b2e0` ("split missing-vs-invalid manifest paths") trennte zwei Fehlerpfade nachträglich, weil ein Test den Unterschied nicht eindeutig zuordnen konnte. `e9964e5` änderte das Logging von Folder-Segment auf vollständigen S3-Key, weil der Folder-Segment im Mehrbundel-Setup nicht eindeutig genug war.

3. **`@Serializable` auf `AssetBundle`/`AssetBundleFile` entfernt (`436bb61`):** Initial hatten alle drei Domain-Klassen die Annotation. `AssetBundle` und `AssetBundleFile` werden aber niemals serialisiert (nur über DTOs in der API), nur das Manifest selbst. Annotation wurde aufgeräumt.

## Offene Punkte / Tech-Debt

1. **`fileCount` für Listings via N+1 `listKeys`-Calls:** Pro gelistetem Bundle ein zusätzlicher `listKeys`-Call. Bei <100 Bundles akzeptabel; bei großen Listings als Optimierungs-Kandidat (z. B. eigenes Index-File `_index.json`) markiert.

2. **Auth/ACL:** Endpoints sind ungeschützt. Aktuell kein Auth-System in der App. Falls Bundles vertraulich werden, muss `asset-bundles/`-Prefix vor unautorisierten Reads geschützt werden.

3. **Manifest-Schema-Versionierung:** Aktuell implizit v1.0. Bei zukünftiger Schema-Änderung explizites `schemaVersion`-Feld einführen.

4. **Multi-Version-Bundles:** Single-Version-Overwrite reicht für 95 % der Fälle. Reproduzierbarkeit pro Projekt entsteht über das Export-ZIP (Sub-Feature C). Falls Curatoren mehrere Versionen parallel pflegen wollen: erweiterbare Bundle-ID-Konvention.

## Commits (Auszug, chronologisch)

```
19a853f feat(asset-bundles): warn when manifest id disagrees with folder name
d4489db docs(persistence): document asset-bundle layout in S3
372df42 fix(asset-bundles): use assetBundleId() for 404 message instead of raw value
562b567 feat(asset-bundles): REST controller for list and detail endpoints
aeb6d82 feat(asset-bundles): AssetBundleNotFoundException + 404 handler
a409eaf test(asset-bundles): integration test against Testcontainers MinIO
6e6b2e0 refactor(asset-bundles): split missing-vs-invalid manifest paths and add find corruption test
6ae51a3 feat(asset-bundles): AssetBundleStorage.find with file listing and content-type detection
e9964e5 refactor(asset-bundles): log full S3 key instead of folder segment
daaf51f feat(asset-bundles): listAll skips invalid manifests with warning
d8ef954 refactor(asset-bundles): drop unused imports from AssetBundleStorage
b901134 feat(asset-bundles): AssetBundleStorage.listAll happy path
436bb61 refactor(asset-bundles): drop unneeded @Serializable on AssetBundle and AssetBundleFile
ee97279 feat(asset-bundles): add AssetBundle domain types and id helper
ec51551 docs(asset-bundles): add design spec and implementation plan
```

## Verifikation

- `./gradlew test` grün (inkl. Testcontainers-MinIO-Integration-Test).
- API manuell mit `curl localhost:8080/api/v1/asset-bundles` und Triple-Detail-Pfad geprüft.
- `persistence.md` enthält Layout + Sync-Beispiel.
