# Feature 40 Refactor — Design-Bundle nach docs/design/ — Done

**Datum:** 2026-05-04
**Branch:** main (direkt, kein Feature-Branch — kleiner Refactor)
**Spec:** `docs/superpowers/specs/2026-05-04-design-bundle-docs-relocation-design.md`
**Plan:** `docs/superpowers/plans/2026-05-04-design-bundle-docs-relocation.md`

## Was umgesetzt wurde

- `DesignBundleStorage` schreibt entpackte Files und Manifest unter `projects/{id}/docs/design/` statt `projects/{id}/design/files/` bzw. `projects/{id}/design/manifest.json`. Originaler ZIP-Upload wird nicht mehr persistiert.
- `delete()` collapsed auf einen einzelnen `deleteFiles(...)`-Call, weil Manifest jetzt unter dem gleichen Prefix lebt.
- `ExportService` Constructor verliert `designBundleStorage`; der explizite Design-Bundle-Block (Manifest + Files unter `prefix/design/...`) ist raus. Die generische `docs/`-Iteration übernimmt automatisch unter `prefix/docs/design/...`.
- Vier neue Tests:
  - `DesignBundleStorageTest.save does not persist the original bundle zip`
  - `DesignBundleStorageTest.save writes manifest and files under docs design prefix`
  - `ExportServiceDesignBundleTest.export does not duplicate design files under legacy design prefix`
  - `ProjectServiceTest.regenerateDocsScaffold preserves docs design contents` (Regression-Guard)

## Bewusste Abweichungen / Restpunkte

- Keine Migration für bestehende `data/projects/{id}/design/`-Verzeichnisse — Dev-User räumt selbst auf (per Spec).
- URL-Pattern `/api/v1/projects/{id}/design/files/**` blieb stabil — Frontend null Änderung. Das URL-Segment `files/` ist jetzt ein historisches Relikt ohne Disk-Entsprechung.
- Frontend manuell verifiziert (Browser-Smoke-Test, alle 7 Schritte aus Spec).

## Akzeptanzkriterien-Status

| # | Kriterium | Status |
|---|---|---|
| 1 | Manifest + Files unter `projects/{id}/docs/design/`, kein `files/`-Subdir | ✅ |
| 2 | Kein Key endet auf `bundle.zip` | ✅ (Test) |
| 3 | `projects/{id}/design/` nicht mehr beschrieben | ✅ |
| 4 | Iframe-URL stabil mit `nosniff` + CSP | ✅ |
| 5 | `regenerateDocsScaffold` löscht keine `docs/design/`-Datei | ✅ (Test) |
| 6 | Export-ZIP-Files unter `<prefix>/docs/design/`, nichts unter `<prefix>/design/` | ✅ (Test) |
| 7 | Explorer listet `docs/design/`-Files; `manifest.json` nur mit Debug-Toggle | ✅ (Browser-Smoke) |
| 8 | `delete()` räumt nur `docs/design/`, restliches `docs/` bleibt | ✅ (Browser-Smoke) |
| 9 | `./gradlew test` grün, neue Tests bestehen | ✅ |
| 10 | Manueller Browser-Smoke-Test erfolgreich | ✅ |
