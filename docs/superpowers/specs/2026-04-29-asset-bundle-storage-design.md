# Sub-Feature A — Asset-Bundle-Storage & Datenmodell

**Datum:** 2026-04-29
**Status:** Design — Review ausstehend
**Übergeordnetes Feature:** Asset-Bundles (Claude-Code-Skills/Commands/Agents) beim Project-Export aus S3 hinzufügen

## Kontext

Der Product-Spec-Agent erzeugt Projekt-Exports (ZIP) basierend auf Wizard-Wahlen. Aktuell enthält der Export Spec-Markdown, Decisions, Clarifications und Tasks (siehe `ExportService.kt`). Nutzer wünschen, dass beim Export auch passende, vorkurierte Claude-Code-Assets (Skills, Commands, Agents) zur jeweiligen Tech-Wahl ins ZIP aufgenommen werden — ein generiertes Projekt soll direkt mit Claude-Code lauffähig sein.

Das Gesamt-Feature ist in drei Sub-Features zerlegt:
- **A (dieses Doc):** Backend-Foundation — Storage, Datenmodell, read-only API.
- **B:** Admin-UI im Frontend — Upload, Liste, Tagging, Versionierung, Vorschau.
- **C:** Export-Integration — Match-Logik (Wizard-Wahl → Bundle), Merge ins ZIP unter `.claude/`.

A ist Voraussetzung für B und C. B und C können parallel implementiert werden.

## Scope

**In-Scope:**
- Domain-Klassen `AssetBundle`, `AssetBundleManifest`, `AssetBundleFile`
- Storage-Klasse `AssetBundleStorage`, baut auf bestehendem `ObjectStore`-Interface auf (Feature 31)
- REST-API `GET /api/v1/asset-bundles` (Liste) und `GET /api/v1/asset-bundles/{step}/{field}/{value}` (Detail)
- Manifest-Schema (`manifest.json` pro Bundle)
- Bucket-Bootstrap-Doku: Bundles per `aws s3 sync` befüllen
- Tests (Unit gegen `InMemoryObjectStore` + Integration gegen Testcontainers-MinIO)

**Explizit Out-of-Scope:**
- Schreibende API (Upload/Delete/Update) → Sub-Feature B
- Frontend-UI → Sub-Feature B
- File-Download-Endpoint (`…/files/{path}`) → in B/C wenn benötigt
- Match-Algorithmus Wizard-Wahl → Bundle → Sub-Feature C
- ZIP-Merging beim Export → Sub-Feature C
- Versionierung jenseits des informativen `version`-String-Felds
- Authentifizierung/Autorisierung (Endpoints sind read-only und liefern nur Metadaten)
- Performance-Optimierung (Index-Files, Caching)

## Entwurfs-Entscheidungen

Folgende Entscheidungen wurden im Brainstorming validiert:

| # | Entscheidung | Begründung |
|---|---|---|
| 1 | **Granularität:** Ein Bundle pro Wizard-Wert (Triple `step.field.value`) | 1:1-Mapping zur Wizard-UI, einfaches Mental-Model, vermeidet Stack-Kombinations-Explosion |
| 2 | **Bundle-Inhalt:** Polymorph (Skills + Commands + Agents in einem Bundle) | Domänenkohärenz, Bundle-Struktur spiegelt Claude-Code-Verzeichnisse 1:1 |
| 3 | **Identität:** Triple `(step, field, value)` als zusammengesetzter Schlüssel | Eindeutig, triviale Match-Logik in C, Bundle-ID = `${step.lower()}.${field}.${slug(value)}` |
| 4 | **Versionierung:** Single-Version, In-Place-Overwrite. `version`-Feld informativ | Deckt 95 % ab; Reproduzierbarkeit über Export-ZIP gegeben; kein Lock-in für späteren Multi-Version-Ausbau |
| 5 | **S3-Bucket:** Gleicher Bucket `productspec-data`, neuer Prefix `asset-bundles/` | Eine Konfiguration, ein `S3Client`. Trennung später per Property-Wechsel ohne Code-Refactor |
| 6 | **Discovery:** Live via `listCommonPrefixes("asset-bundles/", "/")` + `manifest.json`-Read pro Bundle | Keine Cache-Invalidierung, S3 ist Source of Truth. N+1 unkritisch bei <50 Bundles |

## Architektur

### Verzeichnis-Layout in S3 (innerhalb `productspec-data`)

```
asset-bundles/
  backend.framework.kotlin-spring/
    manifest.json
    skills/
      spring-testing/
        SKILL.md
        examples.md
    commands/
      gradle-build.md
    agents/
      spring-debug.md
  frontend.framework.stitch/
    manifest.json
    skills/
      stitch-components/
        SKILL.md
    commands/
      stitch-init.md
  architecture.architecture.microservices/
    manifest.json
    ...
```

**Bundle-Folder-Name = Bundle-ID = `${step.lowercase()}.${field}.${slugify(value)}`**

`slugify`: lowercase, ersetze `[^a-z0-9]+` durch `-`, trimme `-` an den Rändern.
Beispiele:
- `BACKEND` + `framework` + `Kotlin+Spring` → `backend.framework.kotlin-spring`
- `FRONTEND` + `framework` + `Stitch` → `frontend.framework.stitch`
- `ARCHITECTURE` + `architecture` + `Microservices` → `architecture.architecture.microservices`

### Manifest-Schema (`manifest.json`)

```json
{
  "id": "backend.framework.kotlin-spring",
  "step": "BACKEND",
  "field": "framework",
  "value": "Kotlin+Spring",
  "version": "1.0.0",
  "title": "Kotlin + Spring Boot Essentials",
  "description": "Skills, Commands und Agents für Spring-Boot-Backend-Entwicklung",
  "createdAt": "2026-04-29T12:00:00Z",
  "updatedAt": "2026-04-29T12:00:00Z"
}
```

Felder:
- `id` — String, `${step.lower()}.${field}.${slugify(value)}`. Muss zum Bundle-Folder-Namen passen (validiert in Sub-Feature B beim Upload; A loggt nur Inkonsistenzen).
- `step` — Enum `StepType`: `BACKEND` | `FRONTEND` | `ARCHITECTURE`. Andere Werte verwerfen Bundle.
- `field` — String, frei aber muss zu einem im Wizard existierenden Feld passen (semantisch nicht in A geprüft).
- `value` — String, der ursprüngliche Wizard-Wert (Anzeige-Form, z. B. `Kotlin+Spring` mit `+` und Capitalization).
- `version` — String. Informativ. Beeinflusst kein Verhalten in A.
- `title`, `description` — Strings. UI-Anzeige.
- `createdAt`, `updatedAt` — ISO-8601-Strings. UI-Anzeige.

### Komponenten

**`backend/src/main/kotlin/com/agentwork/productspecagent/domain/AssetBundle.kt`**
```kotlin
@Serializable
data class AssetBundleManifest(
    val id: String,
    val step: StepType,
    val field: String,
    val value: String,
    val version: String,
    val title: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
)

data class AssetBundleFile(
    val relativePath: String,   // z. B. "skills/spring-testing/SKILL.md"
    val size: Long,
    val contentType: String,    // best-effort aus Endung
)

data class AssetBundle(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)
```

**`backend/src/main/kotlin/com/agentwork/productspecagent/storage/AssetBundleStorage.kt`**
```kotlin
@Service
class AssetBundleStorage(private val objectStore: ObjectStore) {
    fun listAll(): List<AssetBundleManifest>
    fun find(step: StepType, field: String, value: String): AssetBundle?
}
```

`listAll()` interne Logik:
1. `objectStore.listCommonPrefixes("asset-bundles/", "/")` → Liste von Bundle-Folder-Slugs.
2. Für jeden Folder: `objectStore.get("asset-bundles/$folder/manifest.json")`.
3. JSON parsen mit kotlinx.serialization. Bei Fehler: log Warnung, skippen.
4. Rückgabe: alle erfolgreich geparsten Manifeste.

`find(step, field, value)` interne Logik:
1. Bundle-ID berechnen: `"${step.name.lowercase()}.$field.${slugify(value)}"`.
2. Manifest laden via `objectStore.get("asset-bundles/$id/manifest.json")`. Falls null → return null.
3. Files listen via `objectStore.listEntries("asset-bundles/$id/")`.
4. `manifest.json` aus File-Liste herausfiltern.
5. Pfade auf relativ kürzen (Prefix `asset-bundles/$id/` entfernen).
6. Aus Endung den Content-Type ableiten (`.md` → `text/markdown`, etc.).
7. `AssetBundle(manifest, files)` zurückgeben.

**`backend/src/main/kotlin/com/agentwork/productspecagent/api/AssetBundleController.kt`**
```kotlin
@RestController
@RequestMapping("/api/v1/asset-bundles")
class AssetBundleController(private val storage: AssetBundleStorage) {

    @GetMapping
    fun list(): List<AssetBundleListItem>

    @GetMapping("/{step}/{field}/{value}")
    fun detail(
        @PathVariable step: StepType,    // automatische Enum-Validation → 400 bei invalid
        @PathVariable field: String,
        @PathVariable value: String,
    ): ResponseEntity<AssetBundleDetail>  // 404 bei null
}
```

DTOs (im Controller-File definiert):
```kotlin
data class AssetBundleListItem(
    val id: String,
    val step: StepType,
    val field: String,
    val value: String,
    val version: String,
    val title: String,
    val description: String,
    val fileCount: Int,
)

data class AssetBundleDetail(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)
```

`fileCount` für Listings billig: zusätzliches `listKeys`-Call pro Bundle. Bei großen Listings (>100 Bundles) als Optimierungs-Kandidat in zukünftiger Iteration markieren — aktuell akzeptabel.

## Data Flow

### Bootstrap (manuell, einmalig + bei Updates)

1. Pflegender legt Bundle-Ordner lokal an, z. B. in einem separaten Repo `productspec-asset-bundles/`.
2. `aws s3 sync ./asset-bundles/ s3://productspec-data/asset-bundles/ --delete --exclude ".git/*"`
3. Backend liest Bundles ab sofort live aus S3 — kein Restart, kein Cache-Flush.

Doku-Eintrag in `docs/architecture/persistence.md` ergänzen: kurzer Abschnitt "Asset-Bundles in S3" mit Layout-Beschreibung und Beispiel-Sync-Kommando.

### Read-Path (API)

```
Frontend / Sub-Feature B / C
    ↓ HTTP GET
AssetBundleController
    ↓
AssetBundleStorage
    ↓
ObjectStore (= S3ObjectStore in prod, InMemoryObjectStore in tests)
    ↓
S3 / MinIO
```

## Error Handling

| Fall | Verhalten |
|---|---|
| Bundle-Folder ohne `manifest.json` | `listAll()` skippt; Warnung mit Folder-Name geloggt |
| `manifest.json` mit invalidem JSON | `listAll()` skippt; Warnung mit Folder-Name + Parse-Error geloggt |
| Manifest mit unbekanntem `step`-Enum-Wert | Bundle wird verworfen; Warnung geloggt |
| Manifest-`id` widerspricht Folder-Name | Bundle wird **angenommen** (Manifest = Source of Truth); Warnung geloggt; Folder-Audit-Hinweis. In Sub-Feature B beim Upload validiert |
| Leerer Bucket / Prefix | `listAll()` → `[]`; API liefert `200 OK` mit `[]` |
| S3 nicht erreichbar | `S3Exception` propagiert; globaler `ExceptionHandler` mappt zu `503` (existierendes Verhalten aus Feature 31) |
| Detail-Endpoint, Triple unbekannt | `404 Not Found` mit `{ "error": "Asset bundle not found", "id": "<computed-id>" }` |
| Detail-Endpoint, invalides Step-Enum | `400 Bad Request` (Spring-Standard für Path-Variable-Konversion) |

**Bewusst nicht behandelt:**
- Concurrency: Storage ist read-only. Schreibvorgänge in B haben dort eigene Race-Logik.
- Pagination: Nicht erforderlich bei aktuellem Scale.
- Auth: Endpoints sind read-only auf Metadaten. Falls später nötig, durch globalen Filter ergänzen.

## Testing

### Unit-Tests gegen `InMemoryObjectStore`

`backend/src/test/kotlin/.../storage/AssetBundleStorageTest.kt`:

- `listAll() liefert leere Liste bei leerem Prefix`
- `listAll() liest Manifest pro Bundle-Folder`
- `listAll() überspringt Ordner ohne manifest.json mit Warnung`
- `listAll() überspringt Ordner mit invalidem JSON-Manifest`
- `listAll() überspringt Manifest mit unbekanntem Step-Enum`
- `find() liefert Bundle inkl. Files; manifest.json wird aus Files-Liste gefiltert`
- `find() liefert null bei nicht-existentem Bundle`
- `find() liefert relative Pfade, nicht volle S3-Keys`
- `find() leitet Content-Type aus Endung ab (.md → text/markdown)`
- `slugify(value) ist konsistent zur Bundle-ID-Konvention`

### Controller-Tests (`@WebMvcTest`)

`backend/src/test/kotlin/.../api/AssetBundleControllerTest.kt` — `AssetBundleStorage` ge-`@MockBean`-t:

- `GET /api/v1/asset-bundles → 200 + JSON-Array (mit fileCount)`
- `GET /api/v1/asset-bundles/{step}/{field}/{value} → 200 + Detail`
- `GET …/{step}/… → 404 bei unbekanntem Triple`
- `GET …/{step}/… → 400 bei invalidem Step-Enum (z. B. UNKNOWN)`

### Integration-Test gegen Testcontainers-MinIO

`backend/src/test/kotlin/.../storage/AssetBundleStorageIntegrationTest.kt`:

Ein Test, der den gesamten Read-Path gegen ein echtes MinIO verifiziert: Bundle-Files in MinIO ablegen, `listAll()` und `find()` aufrufen, Asserts. Nutzt existierende `S3TestSupport`-Helper.

### Test-Fixtures

`backend/src/test/kotlin/.../storage/AssetBundleTestFixtures.kt`:

```kotlin
fun buildManifest(
    id: String = "backend.framework.kotlin-spring",
    step: StepType = StepType.BACKEND,
    field: String = "framework",
    value: String = "Kotlin+Spring",
    version: String = "1.0.0",
    ...
): AssetBundleManifest

fun ObjectStore.putBundle(manifest: AssetBundleManifest, files: Map<String, ByteArray>)
```

Ziel: ~12–15 neue Tests, 100 % Pfad-Coverage in `AssetBundleStorage`.

## Akzeptanzkriterien

| # | Kriterium | Verifikation |
|---|---|---|
| 1 | `AssetBundle*`-Domain-Klassen vorhanden, kotlinx.serialization-annotiert wo nötig | Compile + Tests |
| 2 | `AssetBundleStorage.listAll()` liefert alle Manifeste aus `asset-bundles/`-Prefix | Unit-Test |
| 3 | `AssetBundleStorage.find(triple)` liefert Bundle inkl. Files (relative Pfade) | Unit-Test |
| 4 | Korrupte Manifeste werden geloggt und übersprungen, nicht propagiert | Unit-Tests |
| 5 | `GET /api/v1/asset-bundles` listet alle Bundles | Controller-Test |
| 6 | `GET /api/v1/asset-bundles/{step}/{field}/{value}` liefert Detail oder 404 | Controller-Test |
| 7 | Invalid `step`-Enum → 400 | Controller-Test |
| 8 | Integration-Test gegen Testcontainers-MinIO grün | Integration-Test |
| 9 | `docs/architecture/persistence.md` um Asset-Bundle-Layout ergänzt | Manuelle Prüfung |
| 10 | Alle bestehenden Tests bleiben grün (`./gradlew test`) | CI |

## Offene Punkte (für Sub-Feature B/C dokumentiert, nicht in A)

1. **Auth/ACL** — falls die Bundles vertraulich werden, muss das `asset-bundles/`-Prefix vor unautorisierten Reads geschützt werden (nicht aktuell).
2. **File-Download-Endpoint** — wird in B (Preview) und C (Export) eingeführt.
3. **Index-File-Caching** — Performance-Optimierung, falls `listAll()` zu teuer wird.
4. **Manifest-Schema-Versionierung** — aktuell implizit v1.0; bei Schema-Änderung explizites `schemaVersion`-Feld einführen.
5. **Multi-Version-Bundles** — falls Reproduzierbarkeit pro Projekt gefordert wird.
