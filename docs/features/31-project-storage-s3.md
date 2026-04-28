# Feature 31: Project Storage auf S3 migrieren

**Phase:** Infrastruktur
**Abhängig von:** Feature 0 (Project Setup)
**Aufwand:** L
**Status:** Spec approved, Implementierung ausstehend

## Problem & Ziel

Projekt-Daten liegen heute als Dateien unter `data/projects/{id}/` im Backend-Container. Das blockiert horizontales Skalieren, erschwert Backups und macht Cloud-Deploys unflexibel.

**Ziel:** Komplettumstellung der Persistence-Schicht auf S3-kompatibles Object Storage. Lokale Entwicklung und Tests gegen MinIO. Produktion gegen echtes AWS S3 (oder beliebiges S3-kompatibles Backend via Endpoint-Override).

## Architektur (Kurzfassung)

```
ProjectStorage  DecisionStorage  TaskStorage  ClarificationStorage  UploadStorage
        │
        ▼
    ObjectStore (Interface) → S3ObjectStore (Implementation)
        │
        ▼
    S3Client (AWS SDK v2)
        │
        ▼
    MinIO (lokal & Tests)  oder  AWS S3 (prod)
```

**Schlüsselentscheidungen:**
- AWS SDK for Java v2 (kein Spring Cloud AWS)
- Ein Bucket pro Umgebung, Key-Prefix pro Projekt: `projects/{id}/...`
- Keine Filesystem-Fallback-Implementierung — Komplettumstellung
- Testcontainers + MinIO in Tests
- Keine Migration nötig (`data/`-Verzeichnis ist leer)

## S3-Key-Layout

Pfade entsprechen 1:1 den heutigen Filesystem-Pfaden ohne `data/`-Prefix:

| Storage | S3-Key |
|---|---|
| ProjectStorage | `projects/{id}/project.json`, `flow-state.json`, `wizard.json`, `spec/{file}`, `docs/{rel}` |
| DecisionStorage | `projects/{id}/docs/decisions/{id}.json` |
| ClarificationStorage | `projects/{id}/docs/clarifications/{id}.json` |
| TaskStorage | `projects/{id}/docs/tasks/{id}.json` |
| UploadStorage | `projects/{id}/docs/uploads/{filename}`, `.index.json` |

## Konfiguration

```yaml
app:
  storage:
    bucket: ${S3_BUCKET:productspec-data}
    endpoint: ${S3_ENDPOINT:}              # leer = AWS-Default
    region: ${S3_REGION:us-east-1}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    path-style-access: ${S3_PATH_STYLE:false}
```

## docker-compose

Drei Services: `minio`, `minio-init` (legt Bucket an), `backend` (depends_on minio-init: service_completed_successfully). MinIO-Volume persistiert Daten zwischen Restarts. `./data:/app/data` Bind-Mount entfällt komplett.

## Akzeptanzkriterien

1. `docker-compose up` startet MinIO + Bucket-Init + Backend; Bucket `productspec-data` existiert nach Start
2. Projekt-Lifecycle (Anlegen, Wizard, Decisions, Uploads) speichert/liest gegen MinIO; `mc ls local/productspec-data/projects/` zeigt erwartete Struktur
3. Backend-Restart verliert keine Daten (MinIO-Volume persistiert)
4. `./gradlew test` grün, alle Storage-Tests gegen Testcontainers-MinIO
5. `grep -rn "java.nio.file" backend/src/main/kotlin/.../storage/` liefert leer
6. REST-API-Verträge unverändert — Frontend ohne Anpassung lauffähig

## Out of Scope (YAGNI)

- Server-Side Encryption / KMS
- Pre-signed URLs für Frontend-Direktzugriff
- Multi-Tenant Bucket-Isolation
- Migration für Bestandsdaten (`data/` ist leer)
- Filesystem-Fallback
- Backups / Lifecycle-Rules

## Detaildesign

Vollständige Spezifikation inkl. ObjectStore-Interface, S3ObjectStore-Skizze, Test-Setup, Risiken und betroffenen Dateien:
[docs/superpowers/specs/2026-04-28-project-storage-s3-design.md](../superpowers/specs/2026-04-28-project-storage-s3-design.md)
