# Sub-Feature B — Asset-Bundle-Admin-UI

**Datum:** 2026-04-29
**Status:** Design — Review ausstehend
**Übergeordnetes Feature:** Asset-Bundles beim Project-Export aus S3 hinzufügen
**Voraussetzung:** Sub-Feature A (`docs/superpowers/specs/2026-04-29-asset-bundle-storage-design.md`) ist auf `main` (gemerged ab Commit `19a853f`).

## Kontext

Sub-Feature A hat eine read-only Foundation für Asset-Bundles in S3 geliefert — Domain-Modell, `AssetBundleStorage` mit `listAll`/`find`, REST-Endpoints für Listing und Detail, Doku, ~17 Tests.

Sub-Feature B baut die Admin-Oberfläche obendrauf: Web-UI zum Hochladen, Vorschauen und Löschen kuratierter Bundles. Damit wird der `aws s3 sync`-Workflow durch einen Browser-Workflow ersetzt — Curatoren brauchen kein lokales AWS-Setup mehr, sondern packen lokal ein ZIP und laden es hoch.

Sub-Feature C (Export-Integration, separat) nutzt denselben File-Bytes-Endpoint, den B einführt, um beim Project-Export passende Bundle-Dateien in den ZIP zu mergen. Daher entsteht in B kein „Wegwerfcode".

## Scope

**In-Scope:**

Backend:
- Drei neue REST-Endpoints:
  - `POST /api/v1/asset-bundles` — ZIP-Upload, validate + extract + write
  - `DELETE /api/v1/asset-bundles/{step}/{field}/{value}` — clean-wipe Delete
  - `GET /api/v1/asset-bundles/{step}/{field}/{value}/files/**` — File-Bytes mit Content-Type
- Neue Service-Klassen:
  - `AssetBundleAdminService` — Orchestriert Upload (extract → delete → write).
  - `AssetBundleZipExtractor` — pure Funktion, ZIP-Bytes → `ExtractedBundle` oder Validation-Exception.
- Erweiterung von `AssetBundleStorage` um `writeBundle()`, `delete(step, field, value)`, `loadFileBytes(step, field, value, relativePath)`.
- Validation-Exceptions + GlobalExceptionHandler-Mappings.
- Tests: ~35–40 neue Backend-Tests (Unit gegen `InMemoryObjectStore`, ein MinIO-Integrationstest, Controller via `@SpringBootTest`).

Frontend:
- Top-Level-Route `/asset-bundles` mit eigener Page.
- Icon-Rail-Eintrag in `AppShell.tsx` (lucide `Package`).
- UI-Komponenten: `AssetBundlesPage`, `BundleList`, `BundleDetail`, `BundleUpload`, `FileTree`, `FileViewer`.
- Zustand-Store `asset-bundle-store.ts`.
- API-Wrapper in `lib/api.ts`.
- Manuelle Browser-Smoketest beim Plan-Abschluss.

**Explizit Out-of-Scope:**
- Tagging im Manifest (Sub-Feature A's Triple-Identität reicht).
- Versionierung (Sub-Feature A: Single-Version-Overwrite).
- Auth/Permissions (kein Auth-System in der App).
- File-Editing im Browser (Curator pflegt ZIPs lokal, idealerweise in einem separaten Git-Repo).
- Bulk-Operationen (mehrere Bundles parallel löschen/hochladen).
- Diff-View zwischen Versionen.
- Export-Integration (Sub-Feature C, separates Spec).
- Frontend-Unit-Tests (kein Test-Runner konfiguriert; Browser-Smoke reicht).

## Entwurfs-Entscheidungen

| # | Entscheidung | Begründung |
|---|---|---|
| 1 | **Upload-Mechanismus:** ZIP-basiert | Skill-Pakete leben natürlicherweise in einem Git-Repo (Markdown, Code-Beispiele); ZIP-Workflow erlaubt PR-Review und Versionsverlauf lokal. Backend-Code minimal: Validate + Unzip + Write. |
| 2 | **UI-Platzierung:** Neuer Top-Level-Route `/asset-bundles` mit eigenem Icon-Rail-Eintrag | Bundles sind global (nicht per-Projekt), eigener Datenraum verdient eigenen Platz. Settings-Page existiert in der App noch nicht funktional. |
| 3 | **ZIP-Struktur:** Bundle-Inhalt direkt im ZIP-Root (manifest.json + skills/commands/agents/) | Eine klare Konvention. Wrapping-Folder oder Hybrid-Erkennung erhöhen Komplexität ohne Mehrwert. |
| 4 | **Validation-Strenge:** Strenge Allowlist + Auto-Filter benigner Artefakte (`.DS_Store`, `__MACOSX/*`, `Thumbs.db`) | Unbekannte Top-Level-Einträge bleiben hart 400. Mac/Win-Verpackungs-Müll wird stillschweigend gefiltert — robust gegenüber häufigen User-Stolperfallen. |
| 5 | **Re-Upload:** Clean-Wipe + Write (`deletePrefix` vor `put`) | Garantiert: nach Upload spiegelt S3 exakt den ZIP-Inhalt. Keine Geister-Files aus alten Versionen, die in Sub-Feature C unsichtbar in `.claude/` mergen würden. Inkonsistenz-Fenster (Millisekunden) ist in der Praxis irrelevant. |
| 6 | **Vorschau-Tiefe:** File-Tree + Inline-Inhaltsanzeige (Markdown gerendert, Code via `shiki`) | Echter Mehrwert ggü. reiner Inventarliste. Erforderlicher File-Bytes-Endpoint ist sowieso für Sub-Feature C nötig — wird jetzt sauber gebaut statt später nachgezogen. |

## Architektur

### Backend-Komponenten

```
api/AssetBundleController.kt          (erweitert: POST, DELETE, GET /files/**)
        │
        ▼
service/AssetBundleAdminService.kt    (neu: orchestriert upload/delete)
        │           ▲
        ▼           │
service/AssetBundleZipExtractor.kt    (neu: pure validation pipeline)
                    │
                    ▼
storage/AssetBundleStorage.kt         (erweitert: writeBundle, delete, loadFileBytes)
                    │
                    ▼
storage/ObjectStore                   (existiert)
```

**Boundary-Argument:** `AssetBundleZipExtractor` ist absichtlich von `AssetBundleAdminService` getrennt. Die ZIP-Validierungs-Logik ist reine I/O-freie Datenverarbeitung — gut testbar in Isolation, ohne Mocks für Storage. Service orchestriert nur „Extract → Wipe → Write".

### Datentypen

`backend/src/main/kotlin/com/agentwork/productspecagent/domain/AssetBundleUpload.kt` (neu):
```kotlin
data class AssetBundleUploadResult(
    val manifest: AssetBundleManifest,
    val fileCount: Int,
)
```

Service-internes Result-Objekt im Extractor:
```kotlin
data class ExtractedBundle(
    val manifest: AssetBundleManifest,
    val files: Map<String, ByteArray>,  // relativePath → bytes (manifest.json NICHT enthalten)
)
```

### Validation-Exceptions

`backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleValidationExceptions.kt` (neu, alle Sub-Klassen einer Sealed-Hierarchie oder einfach getrennte Klassen):

```kotlin
class MissingManifestException(message: String) : RuntimeException(message)
class InvalidManifestException(message: String) : RuntimeException(message)
class ManifestIdMismatchException(expected: String, actual: String) :
    RuntimeException("Manifest id mismatch: expected '$expected', got '$actual'")
class UnsupportedStepException(step: FlowStepType) :
    RuntimeException("Bundles only supported for BACKEND, FRONTEND, ARCHITECTURE — got $step")
class IllegalBundleEntryException(path: String, reason: String) :
    RuntimeException("Illegal bundle entry '$path': $reason")
class BundleTooLargeException(message: String) : RuntimeException(message)
class BundleFileNotFoundException(bundleId: String, relativePath: String) :
    RuntimeException("File not found in bundle '$bundleId': $relativePath")
```

## ZIP-Pipeline

`AssetBundleZipExtractor.extract(bytes: ByteArray): ExtractedBundle`

### Pipeline-Schritte (in Reihenfolge)

1. **ZIP öffnen.** `ZipInputStream(ByteArrayInputStream(bytes))`. Bei `ZipException` → `IllegalBundleEntryException("(zip)", "Invalid ZIP file")`.

2. **Entries iterieren mit Auto-Filter.** Pro `ZipEntry`:
   - Filter-Regeln (skip ohne Fehler):
     - Pfad endet auf `.DS_Store`
     - Pfad startet mit `__MACOSX/`
     - Pfad endet auf `Thumbs.db`
   - `entry.isDirectory == true` → skip (Ordner sind implizit über Pfade definiert).
   - Sonst → Pfad-Sicherheits-Check (Schritt 3) und Bytes lesen, in `Map<String, ByteArray>` sammeln.

3. **Pfad-Sicherheits-Check** pro Entry:
   - `name.contains("../")` ODER `name.startsWith("/")` ODER `name.startsWith("\\")` → `IllegalBundleEntryException(name, "path traversal blocked")`.
   - `Path.of(name).normalize().toString()` ≠ `name` → analog (fängt z. B. `skills/./foo` oder `skills//foo` ab).

4. **Größenlimits parallel zur Iteration prüfen:**
   - Anzahl gesammelter Files überschreitet 100 → `BundleTooLargeException("Too many files: > 100")`.
   - Pro Entry beim Lesen: laufender Byte-Counter > 2 MB für ein File → `BundleTooLargeException("File too large: $name")`.
   - Total-Counter über alle Entries > 10 MB → `BundleTooLargeException("Total bundle size too large: > 10 MB")`.
   Defense gegen Zip-Bomb: nicht `entry.size`-Header vertrauen, sondern beim eigentlichen Read mitzählen.

5. **Manifest extrahieren:**
   - `files.remove("manifest.json")` → falls `null`: `MissingManifestException("manifest.json must be at the ZIP root")`.
   - `Json { ignoreUnknownKeys = true }.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))` → bei `SerializationException` → `InvalidManifestException("manifest.json: ${e.message}")`.

6. **Manifest-Werte validieren:**
   - `manifest.step` ∈ {`BACKEND`, `FRONTEND`, `ARCHITECTURE`} → sonst `UnsupportedStepException(manifest.step)`. (Beachte: `FlowStepType` enthält insgesamt mehr Werte, der Upload-Validator ist hier explizit strenger als das Domain-Enum, weil Asset-Bundles per Spec nur für diese drei Steps existieren.)
   - `manifest.id == assetBundleId(manifest.step, manifest.field, manifest.value)` → sonst `ManifestIdMismatchException(expected, actual)`.
   - `manifest.title.isNotBlank() && manifest.description.isNotBlank() && manifest.version.isNotBlank()` → sonst `InvalidManifestException("Required field empty: <name>")`.

7. **Top-Level-Allowlist prüfen.** Für jeden verbleibenden File-Pfad in `files`:
   - First segment vor `/` muss `skills` | `commands` | `agents` sein.
   - Sonst → `IllegalBundleEntryException(path, "Top-level must be skills/, commands/, or agents/")`.
   - Files direkt am Root (kein Slash im Pfad) → blockiert (manifest.json wurde bereits in Schritt 5 entfernt).

8. **Return** `ExtractedBundle(manifest, files)`.

### Service-Orchestrierung

`AssetBundleAdminService.upload(zipBytes: ByteArray): AssetBundleUploadResult`:

```kotlin
val extracted = extractor.extract(zipBytes)
storage.delete(extracted.manifest.step, extracted.manifest.field, extracted.manifest.value)  // clean-wipe
storage.writeBundle(extracted.manifest, extracted.files)                                     // write all
return AssetBundleUploadResult(extracted.manifest, extracted.files.size)
```

`AssetBundleStorage.writeBundle(manifest, files)`:
- Schreibe alle `files` zuerst: `objectStore.put("asset-bundles/${manifest.id}/$path", bytes, contentType)`.
- Schreibe `manifest.json` **zuletzt** als `objectStore.put("asset-bundles/${manifest.id}/manifest.json", json.encodeToString(manifest).toByteArray(), "application/json")`.
- Begründung: `find(...)` prüft zuerst auf Manifest-Existenz und liefert `null` wenn nicht da. Schreibt der Service die Files zuerst und das Manifest zuletzt, ist ein abgebrochener Upload für `find()` unsichtbar — Sub-Feature C sieht entweder das alte (gelöschte) Bundle als 404 oder das vollständige neue.

## API-Endpoints (Detail)

### POST /api/v1/asset-bundles

**Request:** `multipart/form-data`, Feld `file` mit ZIP-Inhalt.

**Response 201 Created:**
```json
{
  "manifest": { "id": "backend.framework.kotlin-spring", "step": "BACKEND", ... },
  "fileCount": 12
}
```

**Fehler:**

| Status | `error` | Auslöser |
|---|---|---|
| 400 | `INVALID_BUNDLE` | Alle Validation-Exceptions außer Größe |
| 413 | `BUNDLE_TOO_LARGE` | `BundleTooLargeException` (interne Limits) |
| 413 | `FILE_TOO_LARGE` | `MaxUploadSizeExceededException` (Multipart-Roh-Limit) |

### DELETE /api/v1/asset-bundles/{step}/{field}/{value}

**Response 204 No Content** bei Erfolg.

**Pipeline:**
1. `storage.find(step, field, value)` → falls `null`: `AssetBundleNotFoundException` → 404.
2. `objectStore.deletePrefix("asset-bundles/${id}/")` (idempotent).

### GET /api/v1/asset-bundles/{step}/{field}/{value}/files/**

Catch-all-Pattern für relative Pfade mit Slashes (z. B. `/files/skills/spring-testing/SKILL.md`).

**Implementation:** `@GetMapping("/{step}/{field}/{value}/files/**")` plus `HttpServletRequest` extrahieren des Path-Suffixes via `request.requestURI.substringAfter("/files/")`. URL-Dekodierung via `URLDecoder.decode(suffix, UTF_8)`.

**Response 200 OK:**
- `Content-Type` aus `contentTypeFor(relativePath)` (existierende Logik aus Sub-Feature A).
- `Content-Disposition: inline; filename="${relativePath.substringAfterLast('/')}"`.
- Body: rohe Bytes.

**Fehler:**
- 400 (`IllegalBundleEntryException`) bei Path-Traversal in der Path-Variablen (`..`).
- 404 (`AssetBundleNotFoundException`) wenn Bundle nicht existiert (Manifest fehlt).
- 404 (`BundleFileNotFoundException`) wenn Bundle existiert, aber Datei nicht.

### Bestehende Endpoints (aus Sub-Feature A)

Unverändert:
- `GET /api/v1/asset-bundles`
- `GET /api/v1/asset-bundles/{step}/{field}/{value}`

## Frontend

### Routing und Layout

- Neue Route `app/asset-bundles/page.tsx` (Server Component, lädt nur Client-Component).
- `AssetBundlesPage.tsx` ist eine Client-Component.
- `AppShell.tsx` erkennt den neuen Pfad nicht als „Workspace" (also Standard-Layout, kein Full-Bleed), aber Page rendert sich selbst Full-Height.

```tsx
// AppShell — neuer NavItem zwischen "New Project" und Settings
<NavItem
  href="/asset-bundles"
  icon={<Package size={20} />}
  label="Asset Bundles"
  active={pathname?.startsWith("/asset-bundles") ?? false}
/>
```

### Layout-Skizze

```
┌─────┬─────────────────┬──────────────────────────────────┐
│Icon │  Bundle-Liste   │  Bundle-Detail                   │
│Rail │  + Upload-Zone  │                                  │
│     │  + Filter       │  Manifest-Card (mit Delete-Btn)  │
│     │                 │                                  │
│     │  Bundle 1       │  ┌─File-Tree─┬─Viewer──────┐     │
│     │  Bundle 2       │  │ skills/    │  (md/code) │     │
│     │  ▣ Bundle 3 ←   │  │   ▣ x.md   │             │     │
│     │  Bundle 4       │  │ commands/  │             │     │
│     │                 │  │ agents/    │             │     │
│     │                 │  └────────────┴─────────────┘     │
└─────┴─────────────────┴──────────────────────────────────┘
```

### Komponenten

| Komponente | Verantwortung |
|---|---|
| `AssetBundlesPage.tsx` | Top-Level. Lädt Liste beim Mount. Resizable Split-Panes über `useResizable`. |
| `BundleList.tsx` | Liste + eingebetteter `BundleUpload` + Filter-Dropdown nach `step`. Klick auf Item selektiert. Empty-State, wenn leer. |
| `BundleUpload.tsx` | Drag-Drop + Klick-Zone (Pattern wie `DocumentsPanel`). Akzeptiert `.zip`. Bei Erfolg: Liste neu laden, frisches Bundle selektieren. Bei 4xx: Banner mit Server-Message. |
| `BundleDetail.tsx` | Lädt Detail beim Selection-Change. Manifest-Card oben, File-Tree + Viewer darunter, Delete-Button mit Confirmation. |
| `FileTree.tsx` (oder inline in Detail) | Aus `bundle.files` ein Tree gruppiert nach `relativePath.split('/')[0]`. Selection setzt `selectedFilePath`. |
| `FileViewer.tsx` | Lädt File-Bytes via API. Markdown → existierende Markdown-Komponente. Text-artige Files mit `shiki`. Bilder via `<img>` aus `blob`-URL. Sonst: Größenanzeige + „Preview nicht verfügbar". |

### Zustand-Store

`frontend/src/lib/stores/asset-bundle-store.ts` (Zustand):

```ts
type AssetBundleState = {
  bundles: AssetBundleListItem[];
  selectedBundleId: string | null;
  selectedBundle: AssetBundleDetail | null;
  selectedFilePath: string | null;
  loadedFile: { path: string; contentType: string; body: string | Blob } | null;
  loading: boolean;
  uploading: boolean;
  error: string | null;
  load: () => Promise<void>;
  select: (id: string | null) => Promise<void>;
  selectFile: (relativePath: string | null) => Promise<void>;
  upload: (file: File) => Promise<void>;
  delete: (step: FlowStepType, field: string, value: string) => Promise<void>;
};
```

### API-Wrapper

`frontend/src/lib/api.ts` ergänzen:
```ts
export type AssetBundleListItem = { id, step, field, value, version, title, description, fileCount };
export type AssetBundleDetail = { manifest: AssetBundleManifest, files: AssetBundleFile[] };
export type AssetBundleManifest = { id, step, field, value, version, title, description, createdAt, updatedAt };
export type AssetBundleFile = { relativePath: string, size: number, contentType: string };

export const listAssetBundles = (): Promise<AssetBundleListItem[]> =>
  apiFetch<AssetBundleListItem[]>("/api/v1/asset-bundles");

export const getAssetBundle = (step, field, value): Promise<AssetBundleDetail> =>
  apiFetch<AssetBundleDetail>(`/api/v1/asset-bundles/${step}/${field}/${encodeURIComponent(value)}`);

export const uploadAssetBundle = (file: File): Promise<{ manifest: AssetBundleManifest, fileCount: number }> => {
  const formData = new FormData();
  formData.append("file", file);
  return fetch(`${apiBaseUrl}/api/v1/asset-bundles`, { method: "POST", body: formData })
    .then(handleResponse);  // existing helper, throws on non-2xx
};

export const deleteAssetBundle = (step, field, value): Promise<void> =>
  apiFetch(`/api/v1/asset-bundles/${step}/${field}/${encodeURIComponent(value)}`, { method: "DELETE" });

export const getAssetBundleFile = (step, field, value, relativePath): Promise<Response> =>
  fetch(`${apiBaseUrl}/api/v1/asset-bundles/${step}/${field}/${encodeURIComponent(value)}/files/${relativePath.split('/').map(encodeURIComponent).join('/')}`);
```

## Error Handling

### Backend → HTTP-Mapping

| Exception | HTTP | `error`-Code | Bemerkung |
|---|---|---|---|
| `AssetBundleNotFoundException` | 404 | `NOT_FOUND` | bestehend aus Sub-Feature A |
| `BundleFileNotFoundException` | 404 | `NOT_FOUND` | neu — Bundle existiert, Datei nicht |
| `MissingManifestException` | 400 | `INVALID_BUNDLE` | „manifest.json must be at the ZIP root" |
| `InvalidManifestException` | 400 | `INVALID_BUNDLE` | Parse-Detail in `message` |
| `ManifestIdMismatchException` | 400 | `INVALID_BUNDLE` | message: `expected '<id>', got '<id>'` |
| `UnsupportedStepException` | 400 | `INVALID_BUNDLE` | „Bundles only supported for BACKEND, FRONTEND, ARCHITECTURE" |
| `IllegalBundleEntryException` | 400 | `INVALID_BUNDLE` | Pfad in `message` |
| `BundleTooLargeException` | 413 | `BUNDLE_TOO_LARGE` | konkrete Grenze in `message` |
| `MaxUploadSizeExceededException` | 413 | `FILE_TOO_LARGE` | bestehend |
| `MethodArgumentTypeMismatchException` | 400 | (Spring default) | invalides `step`-Enum als Path-Variable |

Alle Validation-Exceptions teilen sich `INVALID_BUNDLE` als `error`-Code. Das Frontend zeigt einfach `message` an — keine Code-spezifische Logik.

### Konsistenz-Edge-Cases

- **Upload-Race auf gleiches Bundle:** Letzter Schreiber gewinnt. Curation ist single-user; kein Locking.
- **Upload bricht mitten im Schreiben ab:** Manifest-zuletzt-schreiben sorgt dafür, dass `find()` das Bundle als nicht-existent sieht. Re-Upload macht's ganz. Kein automatisches Rollback.
- **Delete eines nicht existierenden Bundles:** `find()`-Check liefert 404. `objectStore.deletePrefix` wäre selbst idempotent, aber wir wollen klares User-Feedback.
- **File-Endpoint mit nicht existentem Pfad:** 404 mit `BundleFileNotFoundException`.

### Frontend

- Upload-Fehler → roter Banner über Drop-Zone mit `error.message`. Auto-Dismiss nach 8s.
- File-Load-Fehler → Inline „Datei konnte nicht geladen werden: {message}" im Viewer.
- Detail-Load-Fehler → vorheriges Selektion bleibt sichtbar; Console-Warning oder Toast.
- Delete-Fehler → Dialog bleibt offen mit Fehlermeldung.
- Kein Retry-Loop. Bei wiederholten Fehlern: User triggert Re-Load.

## Testing

### Backend

**Unit-Tests gegen `AssetBundleZipExtractor`** (`AssetBundleZipExtractorTest.kt`, ~20 Tests):

Happy:
- `extract returns manifest and files for valid ZIP`
- `extract preserves relative paths under skills, commands, agents`

Validation:
- `extract throws MissingManifestException when manifest.json missing`
- `extract throws MissingManifestException when manifest.json nested in folder`
- `extract throws InvalidManifestException for malformed JSON`
- `extract throws InvalidManifestException for missing required fields`
- `extract throws UnsupportedStepException for IDEA / PROBLEM / FEATURES / MVP step`
- `extract throws ManifestIdMismatchException when id does not match triple`
- `extract throws IllegalBundleEntryException for top-level file outside allowlist`
- `extract throws IllegalBundleEntryException for top-level folder outside allowlist`
- `extract throws IllegalBundleEntryException for path traversal entry`
- `extract throws IllegalBundleEntryException for absolute path entry`
- `extract throws BundleTooLargeException for file count over 100`
- `extract throws BundleTooLargeException for file size over 2 MB`
- `extract throws BundleTooLargeException for total size over 10 MB`
- `extract throws IllegalBundleEntryException for invalid ZIP bytes`

Auto-Filter:
- `extract silently skips DS_Store, __MACOSX, Thumbs.db entries`
- `extract counts non-filtered entries against the limit` (sicherstellen, dass Filter den Counter nicht beeinflusst)

Fixtures: `buildZip(manifest, files, extras)` in einer Test-Helper-Datei.

**Storage-Erweiterungs-Tests** (`AssetBundleStorageTest.kt` aus Sub-Feature A erweitern, ~7 neue):
- `writeBundle persists manifest and files at correct keys`
- `writeBundle writes manifest last so partial writes leave bundle invisible to find`
- `delete removes all keys under bundle prefix`
- `delete is idempotent when bundle does not exist`
- `loadFileBytes returns bytes for existing file`
- `loadFileBytes returns null for missing file`
- `loadFileBytes returns null when bundle does not exist`

**Service-Tests** (`AssetBundleAdminServiceTest.kt`, ~3-4):
- Mocks für Extractor und Storage; verifiziert Orchestrierungs-Reihenfolge `extract → delete → writeBundle`.
- `upload propagates extraction exceptions` (kein delete bei invalidem ZIP).

**Controller-Tests** (`AssetBundleControllerTest.kt` aus Sub-Feature A erweitern, ~11):
- `POST asset-bundles 201 with valid ZIP`
- `POST asset-bundles 400 with malformed manifest`
- `POST asset-bundles 400 with manifest id mismatch`
- `POST asset-bundles 400 with unsupported step`
- `POST asset-bundles 413 with too-large bundle`
- `POST asset-bundles overwrites existing bundle (clean-wipe)`
- `DELETE asset-bundles 204 for existing bundle`
- `DELETE asset-bundles 404 for unknown bundle`
- `GET asset-bundle file 200 with bytes and correct Content-Type`
- `GET asset-bundle file 404 for unknown file path`
- `GET asset-bundle file 400 for path traversal`
- `GET asset-bundle file with nested path (catch-all routing)`

Pattern: `@SpringBootTest + @AutoConfigureMockMvc + @Autowired ObjectStore` — analog zu Sub-Feature A.

**Integration-Test gegen MinIO** (`AssetBundleStorageIntegrationTest.kt` erweitern, 1 neuer):
- `writeBundle and delete work against real MinIO`.

**Erwartete Test-Anzahl in B:** ~35–40 neue Tests.

### Frontend

Kein Test-Runner. Manuelle Browser-Smoketest beim Plan-Abschluss:
- Upload eines validen ZIPs → Bundle erscheint in Liste, ist selektiert.
- Upload eines invaliden ZIPs (z. B. fehlendes Manifest) → roter Banner mit klarer Server-Message.
- Bundle selektieren → Detail-Panel mit File-Tree.
- File anklicken → Markdown-Datei wird gerendert; Code-Datei mit Syntax-Highlighting; Bild als `<img>`.
- Delete-Button → Confirmation-Dialog → Bundle aus Liste entfernt.
- Filter „Step: BACKEND" → Liste reduziert sich auf BACKEND-Bundles.

## Akzeptanzkriterien

| # | Kriterium | Verifikation |
|---|---|---|
| 1 | `AssetBundleZipExtractor` validiert alle in der Pipeline beschriebenen Fehlerklassen und filtert Mac/Win-Artefakte | Unit-Tests |
| 2 | `AssetBundleStorage.writeBundle` schreibt Manifest zuletzt | Unit-Test mit zwischenzeitlichem `find()`-Check |
| 3 | `AssetBundleStorage.delete` entfernt alle Keys idempotent | Unit-Tests |
| 4 | `AssetBundleStorage.loadFileBytes` liefert Bytes oder null | Unit-Tests |
| 5 | `POST /api/v1/asset-bundles` mit gültigem ZIP → 201 + UploadResult | Controller-Test |
| 6 | `POST` mit ungültigem ZIP → 400 + `INVALID_BUNDLE` (alle Validation-Klassen abgedeckt) | Controller-Tests |
| 7 | `POST` überschreibt existierendes Bundle (clean-wipe) | Controller-Test |
| 8 | `DELETE` → 204 oder 404 | Controller-Tests |
| 9 | `GET …/files/**` → 200 mit korrektem Content-Type, 404 oder 400 | Controller-Tests |
| 10 | Integration-Test gegen MinIO grün für `writeBundle` + `delete` | Integration-Test |
| 11 | Frontend: Liste lädt, zeigt fileCount | Browser-Smoke |
| 12 | Frontend: Upload eines Bundles erscheint sofort in der Liste | Browser-Smoke |
| 13 | Frontend: File-Inhalt wird gerendert (Markdown + Code via shiki) | Browser-Smoke |
| 14 | Frontend: Delete-Confirmation-Dialog funktioniert | Browser-Smoke |
| 15 | `AppShell.tsx` zeigt neues Nav-Icon | Browser-Smoke |
| 16 | Alle bestehenden Tests bleiben grün | `./gradlew test` |

## Offene Punkte für Sub-Feature C

1. `getAssetBundleFile`-API kann von C direkt wiederverwendet werden, um Datei-Bytes für den Export-ZIP-Merge zu laden.
2. `AssetBundleAdminService` und `AssetBundleZipExtractor` sind nicht relevant für C (nur Read-Pfad).
3. `AssetBundleStorage.find(...)` (existiert seit A) ist die zentrale Read-API für C's Match-Logik.
4. C wird einen Match-Algorithmus brauchen, der aus `wizard.json`-Werten die Triples ableitet, dann `AssetBundleStorage.find` für jedes Triple aufruft, dann die Files in `.claude/{skills,commands,agents}/` des Export-ZIPs merged.
