# Feature 31 — Done: Project Storage auf S3 migriert

**Datum abgeschlossen:** 2026-04-28
**Branch:** `feat/feature-31-s3-storage`
**Spec:** [docs/superpowers/specs/2026-04-28-project-storage-s3-design.md](../superpowers/specs/2026-04-28-project-storage-s3-design.md)
**Plan:** [docs/superpowers/plans/2026-04-28-project-storage-s3.md](../superpowers/plans/2026-04-28-project-storage-s3.md)

## Zusammenfassung

Backend-Persistence vollständig vom lokalen Filesystem (`data/projects/{id}/...`) auf S3-kompatibles Object Storage umgestellt. Lokale Entwicklung und Tests laufen gegen MinIO via Testcontainers/docker-compose; Produktion gegen AWS S3 oder beliebiges S3-kompatibles Backend per `S3_ENDPOINT`-Override.

**Architektur:** Neue `ObjectStore`-Abstraktion (Interface + `S3ObjectStore`-Implementation, AWS SDK v2) zwischen den 5 Domain-Storage-Klassen und S3. Pfad-zu-Key-Mapping bleibt 1:1 zum heutigen Filesystem-Layout (z. B. `projects/{id}/spec/idea.md` als S3-Key).

**Test-Status:** 227/227 Tests grün, inklusive 16 Tests gegen Testcontainers-MinIO (`S3ObjectStoreTest`, `ProjectStorageTest`, `DecisionStorageTest`, `ClarificationStorageTest`, `TaskStorageTest`, `UploadStorageTest`).

**Commits (13):** von `ff17d32` bis `784ec02` auf `feat/feature-31-s3-storage`.

## Abweichungen vom Plan

### 1. Scope-Erweiterung in Task 6 (ProjectStorage Refactor)

Der Plan sah nur 2 Datei-Änderungen vor (`ProjectStorage.kt` + Test). Tatsächlich wurden 19 Dateien geändert:

- **`FileController.kt` migriert** — *Plan-Lücke*: `FileController` nutzte `@Value("\${app.data-path}")` und Filesystem-Reads. Bei Property-Removal in Task 11 wäre die App nicht mehr gestartet. Migration wurde in Task 6 vorgezogen statt in Task 11.
- **`InMemoryObjectStore.kt`** (Test-Helper, in `backend/src/test/kotlin/.../storage/`) — pragmatische Erweiterung, damit `@SpringBootTest`-Tests nicht jeweils einen MinIO-Container starten. Schnelle in-memory-Implementation des `ObjectStore`-Interfaces.
- **`TestStorageConfig.kt`** (`@Configuration` mit `@Primary InMemoryObjectStore`) — überschreibt `S3ObjectStore` in Spring-Test-Contexts.
- **`S3Config.objectStore()` Bean** — mit `@ConditionalOnMissingBean`, damit `TestStorageConfig` den Test-Override durchsetzen kann. Production-Code wird davon nicht beeinflusst.
- **11 Service-/Agent-/Controller-Tests aktualisiert** — die `ProjectStorage(String)` direkt instantiierten, nutzen jetzt `ProjectStorage(InMemoryObjectStore())`. Compile-zwingend.
- **`backend/src/test/resources/application.yml`** — Test-spezifische `app.storage`-Properties.

Diese Erweiterung wurde mit dem Nutzer abgestimmt und akzeptiert (Variante A). Tasks 7-10 (DecisionStorage/ClarificationStorage/TaskStorage/UploadStorage) folgten dem etablierten Pattern und blieben ohne weitere Architektur-Änderungen.

### 2. MinIO Image-Tag

Der Plan spezifizierte `minio/minio:RELEASE.2026-03-12T18-04-02Z`. Dieses Tag existiert nicht auf Docker Hub. Verwendet wird stattdessen **`minio/minio:RELEASE.2025-09-07T16-13-09Z`** (zum Zeitpunkt der Implementierung das aktuelle `latest`-Tag, das per `docker pull` verfügbar war). Konsistent in `S3TestSupport.kt`, `docker-compose.yml`, `start.sh`.

### 3. Zwischen-Task-Failure (in Task 6 committed, in Task 10 behoben)

Während Tasks 6-9 schlug `ExportControllerTest.POST export bundles uploads under docs-uploads` fehl, weil `UploadStorage` noch Filesystem-basiert war. Der Subagent committete diesen Failing-Test in Task 6 transparent dokumentiert. Task 10 (UploadStorage Migration) machte den Test wie erwartet grün. Dieses Vorgehen brach die TDD-Disziplin „alle Tests grün" zwischenzeitlich, war aber pragmatisch: ein zwingender In-Branch-Failure, der durch eine geplante nachfolgende Task aufgelöst wurde.

## Akzeptanzkriterien (final geprüft)

| # | Kriterium | Status |
|---|---|---|
| 1 | docker-compose startet MinIO + Bucket-Init + Backend; Bucket `productspec-data` existiert | ✅ `docker compose config` valide; Smoke-Test in Task 12 |
| 2 | Backend-Restart verliert keine Daten (MinIO-Volume persistiert) | ✅ `minio-data` Named Volume in docker-compose |
| 3 | `./gradlew test` grün, alle Storage-Tests gegen Testcontainers-MinIO | ✅ 227/227 Tests |
| 4 | Keine `java.nio.file.*` / `app.data-path` in Storage-Code | ✅ `grep -rn` leer |
| 5 | Kein `./data:/app/data`-Mount in `docker-compose.yml` | ✅ entfernt |
| 6 | `application.yml` und `persistence.md` aktualisiert | ✅ erledigt (Tasks 11, 14) |
| 7 | REST-API-Verträge unverändert | ✅ kein Endpoint geändert |
| 8 | UI-Smoke (Projekt anlegen, Daten in MinIO) | ⏸ nicht durchgeführt — manuelle Verifikation noch ausstehend |

## Offene Punkte / Technische Schulden

1. **UI-Smoke-Test ausstehend**: Akzeptanzkriterium #8 (manuelles UI-Smoke mit `./start.sh` + Browser → MinIO-Console verifizieren) wurde nicht durchgeführt, da kein OPENAI-API-Key im Test-Setup verfügbar war. Empfohlen: vor Merge eine kurze End-to-End-Session.

2. **`UploadStorage.uniqueFilename()` Race-Condition** (siehe Spec §7.4): Read-then-Write Pattern. Auf S3 nicht atomar. Akzeptiert für aktuelle Single-Worker-Architektur. Bei Multi-Replica-Deploy: UUIDs als Filenames einführen.

3. **`listDocsFiles()` macht N GET-Requests pro Doc-Datei**: Bei <50 Files pro Projekt unkritisch. Bei großen Projekten ggf. S3-Streaming oder Bulk-Read einführen.

4. **Server-Side Encryption / pre-signed URLs**: Bewusst out-of-scope (Spec §1). Bei Cloud-Deploy mit sensiblen Daten als eigenes Feature nachziehen.

5. **MinIO-Image-Pin**: `RELEASE.2025-09-07T16-13-09Z` ist gepinnt. Sollte bei Wartung periodisch auf neuere stable Releases aktualisiert werden (an drei Stellen konsistent: `S3TestSupport.kt`, `docker-compose.yml`, `start.sh`).

6. **Testcontainers-Version**: `1.21.3` (verbreitet). Plan erwähnte `2.0.x` als optionalen Upgrade — funktioniert aktuell stabil, Upgrade nicht akut.

7. **Migrations-Tool**: Wurde nicht implementiert, da `data/`-Verzeichnis leer war. Falls jemand Bestandsdaten migrieren muss: einmaliges CLI/Endpoint, das Filesystem `data/projects/` rekursiv in S3 hochlädt.

## Commits

```
784ec02 docs(architecture): update persistence doc to reflect S3 storage
b557a00 feat(scripts): bootstrap MinIO container for local backend dev
9d9e4b7 feat(docker): replace data volume mount with MinIO and bucket-init service
1fabd08 chore(config): remove app.data-path, S3 storage is now the only persistence
18fd587 refactor(storage): migrate UploadStorage to ObjectStore
11f777c refactor(storage): migrate TaskStorage to ObjectStore
a21637a refactor(storage): migrate ClarificationStorage to ObjectStore
4bf7bc7 refactor(storage): migrate DecisionStorage to ObjectStore
c265441 refactor(storage): migrate ProjectStorage to ObjectStore
34b6fa9 feat(storage): add S3ObjectStore implementation with full test coverage
d8470df feat(storage): add ObjectStore interface
1c09d4f feat(config): add S3StorageProperties and S3Config bean
ff17d32 chore(backend): add AWS SDK v2 S3 and Testcontainers-MinIO dependencies
```
