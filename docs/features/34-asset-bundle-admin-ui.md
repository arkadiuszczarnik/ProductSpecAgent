# Feature 34: Asset-Bundle-Admin-UI (Sub-Feature B)

**Phase:** Asset-Bundles (Sub-Feature B von 3)
**Abhängig von:** Feature 33 (Asset-Bundle-Storage-Foundation, Sub-Feature A)
**Aufwand:** L
**Status:** Spec approved, Implementierung ausstehend

> **Hinweis zur Nachträglichkeit:** Diese Feature-Doc wurde erst nach der Implementierung erstellt. Die Konvention aus `CLAUDE.md` ("Always write the feature doc BEFORE implementing code") wurde bei Sub-Feature B initial übergangen — die Spec/Plan-Dokumente lagen unter `docs/superpowers/specs/` und `docs/superpowers/plans/`, jedoch ohne korrespondierende `docs/features/`-Datei. Diese Doc holt das nach.

## Problem & Ziel

Sub-Feature A liefert eine read-only Foundation für Asset-Bundles in S3. Curatoren müssen Bundles aktuell per `aws s3 sync` aus einem lokalen Verzeichnis hochladen — das setzt AWS-CLI-Setup, Credentials und Bucket-Zugriff voraus.

**Ziel:** Browser-basierte Admin-Oberfläche, mit der Curatoren Bundles als ZIP hochladen, ihren Inhalt vorschauen und löschen können — ohne lokales AWS-Setup. Backend bekommt schreibende Endpoints + ZIP-Validierungs-Pipeline. Frontend bekommt eine neue Top-Level-Route `/asset-bundles`.

Sub-Feature C (Export-Integration) nutzt denselben File-Bytes-Endpoint, den B einführt — kein Wegwerfcode.

## Architektur (Kurzfassung)

```
Browser (/asset-bundles)
        │
        ▼
AssetBundleController (POST upload, DELETE, GET /files/**)
        │
        ▼
AssetBundleAdminService  (extract → wipe → write)
        │           ▲
        ▼           │
AssetBundleZipExtractor  (pure validation pipeline)
        │
        ▼
AssetBundleStorage  (writeBundle, delete, loadFileBytes — Erweiterung von Sub-Feature A)
        │
        ▼
ObjectStore (S3 / MinIO)
```

**Boundary-Argument:** `AssetBundleZipExtractor` ist absichtlich vom `AssetBundleAdminService` getrennt. ZIP-Validierung ist reine I/O-freie Datenverarbeitung — gut testbar in Isolation, ohne Storage-Mocks. Service orchestriert nur „Extract → Wipe → Write".

## Schlüsselentscheidungen

| # | Entscheidung | Begründung |
|---|---|---|
| 1 | Upload via ZIP | Skill-Pakete leben natürlicherweise in einem Git-Repo. ZIP-Workflow erlaubt PR-Review und Versionsverlauf lokal. Backend bleibt minimal: Validate + Unzip + Write. |
| 2 | UI als Top-Level-Route `/asset-bundles` | Bundles sind global (nicht per-Projekt). Eigener Datenraum verdient eigenen Platz im Icon-Rail. |
| 3 | ZIP-Inhalt direkt im Root (manifest.json + skills/commands/agents/) | Klare Konvention. Wrapping-Folder erhöhen Komplexität ohne Mehrwert. |
| 4 | Strenge Allowlist + Auto-Filter benigner Artefakte (`.DS_Store`, `__MACOSX/*`, `Thumbs.db`) | Robust gegen häufige User-Stolperfallen. Unbekannte Top-Level-Einträge bleiben hart 400. |
| 5 | Re-Upload = Clean-Wipe + Write | Garantiert: nach Upload spiegelt S3 exakt den ZIP-Inhalt. Keine Geister-Files aus alten Versionen, die in Sub-Feature C unsichtbar in `.claude/` mergen würden. |
| 6 | Vorschau: File-Tree + Inline-Inhalt (Markdown gerendert, Code via `shiki`) | Echter Mehrwert ggü. reiner Inventarliste. Erforderlicher File-Bytes-Endpoint ist sowieso für C nötig — wird jetzt sauber gebaut. |
| 7 | Manifest **zuletzt** schreiben (nach allen Files) | Bei abgebrochenem Upload bleibt das Bundle für `find()` unsichtbar (404), nie halb-fertig. |

## Neue REST-Endpoints

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/v1/asset-bundles` | `multipart/form-data` `file=<ZIP>`. 201 + UploadResult, 400 bei Validation, 413 bei Größe |
| `DELETE` | `/api/v1/asset-bundles/{step}/{field}/{value}` | 204 oder 404. Idempotent gegen S3 |
| `GET` | `/api/v1/asset-bundles/{step}/{field}/{value}/files/**` | File-Bytes inline, korrekter Content-Type. 400 bei Path-Traversal, 404 bei Bundle/File-Miss |

Bestehende Endpoints aus Sub-Feature A (`GET /api/v1/asset-bundles` und `GET /…/{triple}`) unverändert.

## ZIP-Validierungs-Pipeline

`AssetBundleZipExtractor.extract(bytes)` durchläuft (in Reihenfolge):

1. ZIP öffnen
2. Entries iterieren mit Auto-Filter (`.DS_Store`, `__MACOSX/`, `Thumbs.db` werden silent geskipped)
3. Pfad-Sicherheits-Check (`../`, absolute Pfade, Path-Normalisierungs-Mismatch → 400)
4. Größenlimits parallel zur Iteration (max. 100 Files, 2 MB pro File, 10 MB total) — Defense gegen Zip-Bomb durch Mitzählen beim Read, nicht über `entry.size`-Header
5. Manifest extrahieren (`MissingManifestException` falls fehlt; `InvalidManifestException` bei Parse-Fehler)
6. Manifest-Werte validieren (Step ∈ {BACKEND, FRONTEND, ARCHITECTURE}; ID matcht Triple; Required-Fields nicht-leer)
7. Top-Level-Allowlist prüfen (alle Files müssen unter `skills/`, `commands/` oder `agents/` liegen)
8. Return `ExtractedBundle(manifest, files)`

## Frontend

- Neue Route `app/asset-bundles/page.tsx` (Server Component → Client-Component)
- `AssetBundlesPage.tsx` mit Liste, Detail-Panel, Upload-Drop-Zone, Delete-Confirmation-Dialog
- `AppShell.tsx` bekommt neuen Nav-Item mit lucide `Package`-Icon
- Zustand-Store `lib/stores/asset-bundle-store.ts`
- API-Wrapper in `lib/api.ts`
- File-Vorschau: Markdown via `react-markdown`, Code via `shiki`, Bilder als `<img>`

**Kein Frontend-Test-Runner.** Verifikation erfolgt manuell als Browser-Smoke beim Plan-Abschluss.

## Akzeptanzkriterien

1. `AssetBundleZipExtractor` validiert alle in der Pipeline beschriebenen Fehlerklassen und filtert Mac/Win-Artefakte
2. `AssetBundleStorage.writeBundle` schreibt Manifest zuletzt
3. `AssetBundleStorage.delete` entfernt alle Keys idempotent
4. `AssetBundleStorage.loadFileBytes` liefert Bytes oder null
5. `POST /api/v1/asset-bundles` mit gültigem ZIP → 201 + `AssetBundleUploadResult`
6. `POST` mit ungültigem ZIP → 400 + `INVALID_BUNDLE` (alle Validation-Klassen abgedeckt)
7. `POST` überschreibt existierendes Bundle (clean-wipe)
8. `DELETE` → 204 oder 404
9. `GET …/files/**` → 200 mit korrektem Content-Type, 404 oder 400
10. Integration-Test gegen MinIO grün für `writeBundle` + `delete`
11. Frontend: Liste lädt, zeigt `fileCount`
12. Frontend: Upload eines Bundles erscheint sofort in der Liste
13. Frontend: File-Inhalt wird gerendert (Markdown + Code via shiki)
14. Frontend: Delete-Confirmation-Dialog funktioniert
15. `AppShell.tsx` zeigt neues Nav-Icon
16. Alle bestehenden Tests bleiben grün

## Out of Scope (YAGNI)

- Tagging im Manifest (Triple-Identität reicht)
- Versionierung (Single-Version-Overwrite)
- Auth/Permissions (kein Auth-System in der App)
- File-Editing im Browser (Curator pflegt ZIPs lokal)
- Bulk-Operationen (mehrere Bundles parallel)
- Diff-View zwischen Versionen
- Export-Integration (Sub-Feature C / Feature 26)
- Frontend-Unit-Tests (Browser-Smoke reicht)

## Detaildesign

Vollständige Spezifikation inkl. Validation-Exception-Hierarchie, Service-Orchestrierung, Frontend-Layout-Skizze, Test-Tabellen:
[docs/superpowers/specs/2026-04-29-asset-bundle-admin-ui-design.md](../superpowers/specs/2026-04-29-asset-bundle-admin-ui-design.md)

Implementierungsplan:
[docs/superpowers/plans/2026-04-29-asset-bundle-admin-ui.md](../superpowers/plans/2026-04-29-asset-bundle-admin-ui.md)
