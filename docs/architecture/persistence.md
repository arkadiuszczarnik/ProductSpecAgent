# Architecture: Persistenz (S3 Object Storage)

## Überblick

Alle Projekt-Daten werden als Objekte in einem S3-kompatiblen Bucket gespeichert. Lokale Entwicklung und Tests laufen gegen MinIO; Produktion gegen AWS S3 oder ein anderes S3-kompatibles Backend (z. B. Cloudflare R2, Wasabi) per `S3_ENDPOINT`-Override.

Die Backend-Storage-Schicht nutzt eine `ObjectStore`-Abstraktion (`S3ObjectStore` Implementation, AWS SDK for Java v2). Es gibt keine Datenbank.

## Bucket-Layout

Ein Bucket pro Umgebung (Default-Name `productspec-data`, konfigurierbar via `S3_BUCKET`). Pro Projekt ein Key-Prefix `projects/{project-id}/`.

```
{bucket}/
└── projects/
    └── {project-id}/
        ├── project.json             # Metadaten (Name, Owner, Status, ...)
        ├── flow-state.json          # Aktueller Stand im Wizard-Graph
        ├── wizard.json              # Wizard-Form-Daten
        ├── spec/
        │   └── spec.md              # Finale Produktspezifikation
        └── docs/
            ├── decisions/{id}.json
            ├── clarifications/{id}.json
            ├── tasks/{id}.json
            ├── uploads/
            │   ├── .index.json
            │   └── {filename}
            └── (weitere generierte Doku-Dateien)
```

## Konfiguration

```yaml
app:
  storage:
    bucket: ${S3_BUCKET:productspec-data}
    endpoint: ${S3_ENDPOINT:}            # leer = AWS-Default-Endpoint
    region: ${S3_REGION:us-east-1}
    access-key: ${S3_ACCESS_KEY:}        # leer = AWS Default-Credential-Chain
    secret-key: ${S3_SECRET_KEY:}
    path-style-access: ${S3_PATH_STYLE:false}  # true bei MinIO
```

## Komponenten

- `ObjectStore` (Interface) — kapselt put/get/exists/delete/listKeys/listEntries/listCommonPrefixes/deletePrefix
- `S3ObjectStore` (Implementation) — AWS SDK v2, übersetzt `NoSuchKeyException` zu `null`, paginiert via `listObjectsV2Paginator`, batched `deletePrefix` in 1000er-Chunks
- `S3Config` — registriert `S3Client` als Spring-Bean mit konfigurierbarem Endpoint und Credentials
- 5 Domain-Storage-Klassen (`ProjectStorage`, `DecisionStorage`, `ClarificationStorage`, `TaskStorage`, `UploadStorage`) — nutzen ausschließlich `ObjectStore`-API

## Datei-Formate

### project.json

```json
{
  "id": "uuid",
  "name": "Projektname",
  "ownerId": "user-uuid",
  "status": "in_progress",
  "createdAt": "2026-03-30T12:00:00Z",
  "updatedAt": "2026-03-30T14:00:00Z"
}
```

### Spec-Dateien (Markdown)

```markdown
# Problemdefinition
...
```

## Lokale Entwicklung

`./start.sh` startet eine MinIO-Instanz als Docker-Container, falls noch keine unter `localhost:9000` läuft, legt das Bucket an und exportiert die `S3_*`-Env-Vars vor dem Backend-Start.

`docker-compose up` startet einen vollständigen Stack (MinIO + Backend + Frontend); ein dedizierter `minio-init`-Container erzeugt das Bucket beim Hochfahren.

## Tests

Storage-Tests erben von `S3TestSupport`, das einen MinIO-Container per Testcontainers JVM-weit hochfährt. Vor jedem Test wird das Test-Bucket geleert; das sorgt für Isolation ohne Container-Restart-Overhead. Service- und Controller-Tests, die schnellere Unit-Tests sind, nutzen `InMemoryObjectStore` (in-memory `ObjectStore`-Implementation, im Test-Classpath).

## Asset-Bundles in S3

Vorkurierte Claude-Code-Skills, -Commands und -Agents leben unter dem Prefix `asset-bundles/` im selben Bucket (`productspec-data`). Sie sind global geteilt — nicht per-Projekt.

### Layout

```
asset-bundles/
  backend.framework.kotlin-spring/
    manifest.json
    skills/spring-testing/SKILL.md
    commands/gradle-build.md
    agents/spring-debug.md
  frontend.framework.stitch/
    manifest.json
    skills/...
  architecture.architecture.microservices/
    manifest.json
    ...
```

Bundle-Folder-Name = Bundle-ID = `${step.lower()}.${field}.${slug(value)}`.

### Manifest-Schema (`manifest.json`)

```json
{
  "id": "backend.framework.kotlin-spring",
  "step": "BACKEND",
  "field": "framework",
  "value": "Kotlin+Spring",
  "version": "1.0.0",
  "title": "Kotlin + Spring Boot Essentials",
  "description": "Skills für Spring-Boot-Backend",
  "createdAt": "2026-04-29T12:00:00Z",
  "updatedAt": "2026-04-29T12:00:00Z"
}
```

### Bundles befüllen

Bundles werden manuell verwaltet (Sub-Feature A — kein UI). Beispiel-Sync aus separatem Repo:

```bash
aws s3 sync ./asset-bundles/ s3://productspec-data/asset-bundles/ --delete --exclude ".git/*"
```

Backend liest Bundles live aus S3 — kein Restart nötig nach Sync.

### Read-API

- `GET /api/v1/asset-bundles` — Liste aller Bundles (Manifest + fileCount)
- `GET /api/v1/asset-bundles/{step}/{field}/{value}` — Detail mit File-Liste oder 404
