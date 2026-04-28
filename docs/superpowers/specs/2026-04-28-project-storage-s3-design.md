# Design: Project Storage auf S3 migrieren

**Datum:** 2026-04-28
**Status:** Approved
**Feature-Nr.:** 31
**Verwandte Doku:** [docs/features/31-project-storage-s3.md](../../features/31-project-storage-s3.md), [docs/architecture/persistence.md](../../architecture/persistence.md)

## 1. Problem & Ziel

Projekt-Daten (project.json, flow-state.json, spec/*.md, docs/**, decisions/, clarifications/, tasks/, uploads/) liegen heute als Dateien unter `data/projects/{id}/` im lokalen Dateisystem des Backend-Containers. Das verhindert horizontales Skalieren, erschwert Backups und macht den Cloud-Deploy unflexibel.

**Ziel:** Komplettumstellung der Persistence-Schicht auf S3-kompatibles Object Storage. Lokale Entwicklung und Tests laufen gegen MinIO. Produktion läuft gegen echtes AWS S3 oder ein anderes S3-kompatibles Backend (über `S3_ENDPOINT`-Env-Var konfigurierbar).

**Nicht-Ziele (YAGNI):**
- Server-Side Encryption / KMS
- Pre-signed URLs für Frontend-Direktzugriff
- Multi-Tenant Bucket-Isolation
- Migrations-Tool für Bestandsdaten (`data/`-Verzeichnis ist leer)
- Fallback auf Filesystem
- Backups / Lifecycle-Rules

## 2. Architektur

```
ProjectService / WizardService / DecisionService / ...   (unverändert)
        │
        ▼
ProjectStorage  DecisionStorage  TaskStorage  ClarificationStorage  UploadStorage
        │                       (refactored — Filesystem-Code raus)
        ▼
ObjectStore (Interface)            ← neue Abstraktion
S3ObjectStore (Implementation)
        │
        ▼
S3Client (AWS SDK v2)              ← Bean in S3Config
        │
        ▼
MinIO (lokal & Tests)  oder  AWS S3 (Produktion)
```

**Neue Klassen:**
- `S3Config` — konfiguriert `S3Client` Bean aus `app.storage.*` Properties
- `S3StorageProperties` — `@ConfigurationProperties("app.storage")`
- `ObjectStore` (Interface) + `S3ObjectStore` (@Service) — kapselt alle S3-Operationen

**Geänderte Klassen:**
- 5 Storage-Klassen: ersetzen `Files.*`-Aufrufe durch `objectStore.*`-Aufrufe
- `application.yml`: `app.data-path` raus, `app.storage.*` rein
- `docker-compose.yml`: MinIO + minio-init + Backend-Env-Vars
- 5 Storage-Tests: `@TempDir` raus, Testcontainers-MinIO rein, gemeinsame `S3TestSupport`-Basisklasse

**Gelöscht:**
- Filesystem-Code (`java.nio.file.Files`, `java.nio.file.Path` Aufrufe für Projekt-Daten) in den 5 Storage-Klassen
- `./data:/app/data` Bind-Mount in docker-compose

## 3. Datenmodell & S3-Key-Layout

**Ein Bucket pro Umgebung, Key-Prefix pro Projekt.** Pfade entsprechen 1:1 den heutigen Filesystem-Pfaden (ohne `data/`-Prefix).

| Domain-Storage | Bisheriger Pfad | S3-Key |
|---|---|---|
| ProjectStorage | `data/projects/{id}/project.json` | `projects/{id}/project.json` |
| ProjectStorage | `data/projects/{id}/flow-state.json` | `projects/{id}/flow-state.json` |
| ProjectStorage | `data/projects/{id}/wizard.json` | `projects/{id}/wizard.json` |
| ProjectStorage | `data/projects/{id}/spec/{file}` | `projects/{id}/spec/{file}` |
| ProjectStorage | `data/projects/{id}/docs/{rel}` | `projects/{id}/docs/{rel}` |
| DecisionStorage | `data/projects/{id}/docs/decisions/{id}.json` | `projects/{id}/docs/decisions/{id}.json` |
| ClarificationStorage | `data/projects/{id}/docs/clarifications/{id}.json` | `projects/{id}/docs/clarifications/{id}.json` |
| TaskStorage | `data/projects/{id}/docs/tasks/{id}.json` | `projects/{id}/docs/tasks/{id}.json` |
| UploadStorage | `data/projects/{id}/docs/uploads/{filename}` | `projects/{id}/docs/uploads/{filename}` |
| UploadStorage | `data/projects/{id}/docs/uploads/.index.json` | `projects/{id}/docs/uploads/.index.json` |

**Default-Bucket-Name:** `productspec-data`. Konfigurierbar per `S3_BUCKET` Env-Var.

## 4. Komponenten-Spezifikation

### 4.1 `S3StorageProperties`

```kotlin
@ConfigurationProperties("app.storage")
data class S3StorageProperties(
    val bucket: String,
    val endpoint: String = "",        // leer = AWS-Default-Endpoint
    val region: String = "us-east-1",
    val accessKey: String = "",       // leer = AWS Default-Credential-Chain
    val secretKey: String = "",
    val pathStyleAccess: Boolean = false,
)
```

### 4.2 `S3Config`

```kotlin
@Configuration
@EnableConfigurationProperties(S3StorageProperties::class)
class S3Config {
    @Bean(destroyMethod = "close")
    fun s3Client(props: S3StorageProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(props.region))
            .forcePathStyle(props.pathStyleAccess)
        if (props.endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(props.endpoint))
        }
        if (props.accessKey.isNotBlank() && props.secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey, props.secretKey)
                )
            )
        }
        return builder.build()
    }
}
```

### 4.3 `ObjectStore` Interface

```kotlin
interface ObjectStore {
    fun put(key: String, bytes: ByteArray, contentType: String? = null)
    fun get(key: String): ByteArray?
    fun exists(key: String): Boolean
    fun delete(key: String)
    fun deletePrefix(prefix: String)
    fun listKeys(prefix: String): List<String>
    fun listEntries(prefix: String): List<ObjectEntry>
    fun listCommonPrefixes(prefix: String, delimiter: String): List<String>

    data class ObjectEntry(val key: String, val lastModified: Instant)
}
```

### 4.4 `S3ObjectStore` Implementation (Eckpfeiler)

- `put` → `s3.putObject(PutObjectRequest, RequestBody.fromBytes(bytes))`
- `get` → `s3.getObject(GetObjectRequest, ResponseTransformer.toBytes()).asByteArray()`, fängt `NoSuchKeyException` und gibt `null`
- `exists` → `s3.headObject(HeadObjectRequest)` mit `NoSuchKeyException`-Handling → `Boolean`
- `delete` → `s3.deleteObject(DeleteObjectRequest)` (idempotent — S3 wirft nicht bei fehlendem Key)
- `deletePrefix` → `listKeys(prefix)`, dann in 1000er-Batches `s3.deleteObjects(...)` aufrufen
- `listKeys` → `s3.listObjectsV2Paginator { it.bucket(...).prefix(prefix) }` — flatMap über alle Seiten
- `listEntries` → wie `listKeys`, aber Tupel aus `S3Object.key()` und `S3Object.lastModified()`
- `listCommonPrefixes` → `s3.listObjectsV2Paginator { it.bucket(...).prefix(prefix).delimiter(delimiter) }` — flatMap über `commonPrefixes()`, strippt Trailing-Delimiter

Alle Operationen sind blocking/synchron (matched bestehenden Code-Stil, kein Coroutine-Umbau nötig).

### 4.5 Storage-Refactor — Pattern

**Konstruktor:** `@Value("\${app.data-path}") String dataPath` → `ObjectStore objectStore`

**Beispiel `ProjectStorage`:**

```kotlin
@Service
class ProjectStorage(private val objectStore: ObjectStore) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun projectKey(id: String) = "projects/$id/project.json"
    private fun flowStateKey(id: String) = "projects/$id/flow-state.json"
    private fun specKey(id: String, file: String) = "projects/$id/spec/$file"
    private fun docsKey(id: String, rel: String) = "projects/$id/$rel"
    private fun projectPrefix(id: String) = "projects/$id/"
    private fun docsPrefix(id: String) = "projects/$id/docs/"

    fun saveProject(project: Project) {
        objectStore.put(projectKey(project.id), json.encodeToString(project).toByteArray(), "application/json")
    }

    fun loadProject(projectId: String): Project? =
        objectStore.get(projectKey(projectId))
            ?.toString(Charsets.UTF_8)
            ?.let { json.decodeFromString<Project>(it) }

    fun deleteProject(projectId: String) {
        objectStore.deletePrefix(projectPrefix(projectId))
    }

    fun listProjects(): List<Project> =
        objectStore.listCommonPrefixes("projects/", "/")
            .mapNotNull { id -> loadProject(id) }

    fun listDocsFiles(projectId: String): List<Pair<String, ByteArray>> {
        val docsPrefix = docsPrefix(projectId)
        val projectPrefix = projectPrefix(projectId)
        return objectStore.listKeys(docsPrefix)
            .filter { !it.endsWith(".index.json") }
            .map { key ->
                val rel = key.removePrefix(projectPrefix)
                val bytes = objectStore.get(key) ?: ByteArray(0)
                rel to bytes
            }
    }
    // saveSpecStep, loadSpecStep, saveFlowState, loadFlowState,
    // saveWizardData, loadWizardData, saveDocsFile  → analog
}
```

**Andere Storage-Klassen:** dasselbe Pattern. `DecisionStorage`, `TaskStorage`, `ClarificationStorage` nutzen `listKeys(prefix)` + Filter `*.json` für ihre `list*()`-Methoden.

**`UploadStorage`-Spezialfälle:**
- `readMtime` nutzt `objectStore.listEntries(uploadPrefix)`, sucht `key == filename`, gibt `lastModified.toString()`
- `uniqueFilename` bleibt Read-then-Write (siehe Risiken Abschnitt 7)
- `.index.json` Migration-Logik unverändert (rein-formale Json-Migration)

## 5. Konfiguration

### 5.1 `application.yml` (Patch)

```yaml
# entfernt:
# app:
#   data-path: ./data

# neu:
app:
  storage:
    bucket: ${S3_BUCKET:productspec-data}
    endpoint: ${S3_ENDPOINT:}
    region: ${S3_REGION:us-east-1}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    path-style-access: ${S3_PATH_STYLE:false}
```

### 5.2 `docker-compose.yml` (komplett ersetzt)

```yaml
services:
  minio:
    image: minio/minio:RELEASE.2026-03-12T18-04-02Z
    command: server /data --console-address ":9001"
    ports: ["9000:9000", "9001:9001"]
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes: ["minio-data:/data"]
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      timeout: 3s
      retries: 5

  minio-init:
    image: minio/mc:latest
    depends_on:
      minio:
        condition: service_healthy
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 minioadmin minioadmin &&
      mc mb --ignore-existing local/productspec-data
      "

  backend:
    build: ./backend
    ports: ["8080:8080"]
    depends_on:
      minio-init:
        condition: service_completed_successfully
    environment:
      SPRING_PROFILES_ACTIVE: docker
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
      S3_ENDPOINT: http://minio:9000
      S3_BUCKET: productspec-data
      S3_ACCESS_KEY: minioadmin
      S3_SECRET_KEY: minioadmin
      S3_PATH_STYLE: "true"
      S3_REGION: us-east-1
    restart: unless-stopped

  frontend:
    build: ./frontend
    ports: ["3000:3000"]
    environment:
      NEXT_PUBLIC_API_URL: http://backend:8080
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  minio-data:
```

**Änderung gegenüber heute:**
- `./data:/app/data`-Bind-Mount entfällt
- `ANTHROPIC_API_KEY` → `OPENAI_API_KEY` korrigiert (matched zu `application.yml`-Konfig `ai.koog.openai.api-key`)

### 5.3 Build-Dependencies

`backend/build.gradle.kts`:

```kotlin
dependencies {
    // ...
    implementation(platform("software.amazon.awssdk:bom:2.30.4"))
    implementation("software.amazon.awssdk:s3")

    // Test
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:minio:1.21.3")
}
```

## 6. Tests

### 6.1 `S3TestSupport` Basisklasse

Statische `MinIOContainer`-Instanz, die einmal pro JVM startet. Bucket wird in `@BeforeAll` angelegt, vor jedem Test (`@BeforeEach`) geleert.

```kotlin
@Testcontainers
abstract class S3TestSupport {
    companion object {
        @Container
        @JvmStatic
        val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2026-03-12T18-04-02Z")
            .withUserName("testuser")
            .withPassword("testpassword")

        @JvmStatic
        protected lateinit var s3: S3Client
        protected const val BUCKET = "test-bucket"

        @BeforeAll
        @JvmStatic
        fun initS3() {
            s3 = S3Client.builder()
                .endpointOverride(URI.create(minio.s3URL))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(minio.userName, minio.password)))
                .build()
            s3.createBucket { it.bucket(BUCKET) }
        }
    }

    @BeforeEach
    fun clearBucket() {
        val keys = s3.listObjectsV2 { it.bucket(BUCKET) }.contents().map { it.key() }
        if (keys.isNotEmpty()) {
            s3.deleteObjects { req ->
                req.bucket(BUCKET).delete { d ->
                    d.objects(keys.map { ObjectIdentifier.builder().key(it).build() })
                }
            }
        }
    }

    protected fun objectStore(): S3ObjectStore =
        S3ObjectStore(s3, S3StorageProperties(bucket = BUCKET))
}
```

### 6.2 Test-Migration

Jeder der 5 Storage-Tests:
- erbt von `S3TestSupport`
- entfernt `@TempDir`
- ersetzt `Storage(tempDir.toString())` durch `Storage(objectStore())`
- Test-Logik (Round-Trip, list-empty, delete-non-existent etc.) bleibt unverändert

**Zusätzliche Tests für `S3ObjectStore`:**
- `put` + `get` round-trip
- `get` für nicht-existenten Key → `null`
- `delete` für nicht-existenten Key → kein Throw
- `deletePrefix` löscht alle Keys, aber nichts außerhalb
- `deletePrefix` mit > 1000 Keys (Batching)
- `listKeys` paginiert korrekt (synthetisch > 1000 Keys einfügen — Skip wenn Test zu langsam)
- `listCommonPrefixes` mit `/`-Delimiter

## 7. Risiken & offene Punkte

1. **Spring Boot 4 + AWS SDK v2** — beide aktuell, keine bekannten Konflikte. Mitigation: nach Implementierung einen vollständigen Smoke-Test durchführen.

2. **Testcontainers 1.21 vs. 2.0** — die Doku zeigt 2.0.x als neue Major-Linie. Wir pinnen zunächst auf 1.21.3 (verbreitet), prüfen 2.0.x bei Implementierung als optionalen Upgrade.

3. **MinIO-Image-Pin** — RELEASE-Versionen ändern sich monatlich. Wir pinnen einmal und upgraden bewusst.

4. **`UploadStorage.uniqueFilename` Race-Condition** — Read-then-Write ist auf S3 nicht atomar. Akzeptiert: keine parallele Multi-Worker-Architektur, Upload-Frequenz pro Projekt niedrig. Falls Multi-Replica-Deploy ansteht, später UUIDs als Filenames einführen.

5. **`listDocsFiles` macht N GET-Requests** — bei <50 Files pro Projekt unkritisch. Falls Performance-Hot-Spot, später S3-Stream-Variante.

6. **`S3Client.close()` beim Shutdown** — explizit via `@Bean(destroyMethod = "close")` registriert.

7. **`start.sh` braucht laufendes MinIO** — wer Backend lokal ohne docker-compose startet, muss MinIO separat hochfahren. Mitigation: `start.sh` startet einen MinIO-Container im Hintergrund, falls keiner unter `localhost:9000` läuft. Default-Env (`S3_ENDPOINT=http://localhost:9000`, `S3_PATH_STYLE=true`, `S3_ACCESS_KEY=minioadmin`, `S3_SECRET_KEY=minioadmin`, `S3_BUCKET=productspec-data`) wird vor dem Backend-Start exportiert. Falls MinIO bereits läuft, übernimmt `start.sh` ihn nicht.

## 8. Akzeptanzkriterien

1. `docker-compose up` startet MinIO + minio-init + Backend; Bucket `productspec-data` existiert nach Start
2. Über die UI ein Projekt anlegen, Wizard durchlaufen, Decision generieren — alle Daten in MinIO sichtbar via `mc ls local/productspec-data/projects/`
3. Backend-Restart verliert keine Daten (MinIO-Volume persistiert)
4. `./gradlew test` läuft grün, alle Storage-Tests nutzen Testcontainers-MinIO
5. Suche `grep -rn "java.nio.file.Files\|java.nio.file.Path\|app.data-path"` in `backend/src/main/kotlin/com/agentwork/productspecagent/storage/` ist leer
6. `./data:/app/data`-Mount nicht mehr in `docker-compose.yml`
7. `application.yml` und `docs/architecture/persistence.md` aktualisiert
8. REST-API-Verträge unverändert — Frontend ohne Anpassung lauffähig

## 9. Betroffene Dateien

### Neu
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ObjectStore.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/S3ObjectStore.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/config/S3Config.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/config/S3StorageProperties.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/S3TestSupport.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/S3ObjectStoreTest.kt`
- `docs/features/31-project-storage-s3.md`

### Geändert
- `backend/build.gradle.kts` (Dependencies)
- `backend/src/main/resources/application.yml` (Property-Schema)
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ProjectStorage.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/DecisionStorage.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/TaskStorage.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/ClarificationStorage.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ProjectStorageTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/DecisionStorageTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/TaskStorageTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/ClarificationStorageTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt`
- `docker-compose.yml`
- `start.sh` (MinIO-Bootstrap + S3-Env-Defaults)
- `docs/architecture/persistence.md`
- `docs/features/00-feature-set-overview.md` (Eintrag für Feature 31)

### Optional gelöscht
- `data/.gitkeep` (kann bleiben für lokale Nicht-Docker-Tools)

## 10. Abhängigkeiten

- Feature 0 (Project Setup) — vorhanden
- Alle nachfolgenden Features bleiben funktional intakt; betroffene REST-Verträge ändern sich nicht
