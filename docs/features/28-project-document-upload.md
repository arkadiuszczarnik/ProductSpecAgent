# Feature 28: Projekt-Dokumente in GraphMesh hochladen

## Zusammenfassung

Pro Projekt können Nutzer PDF-, Markdown- und Plain-Text-Dokumente hochladen. Die Dokumente werden in eine GraphMesh-Collection gespeichert, deren ID einmalig im Projekt persistiert wird. Die Collection wird **lazy** beim ersten Upload erzeugt; ihr Name entspricht dem Projektnamen zum Anlage-Zeitpunkt. Ein neuer "Documents"-Tab in der Workspace-Sidebar zeigt die Liste hochgeladener Dokumente mit Verarbeitungs-Status (per Frontend-Polling) und erlaubt das Löschen.

## User Stories

1. Als PO möchte ich Dokumente (z. B. Policies, Specs, Anforderungen) zu meinem Projekt hochladen, damit AI-Agents später per RAG darauf zugreifen können.
2. Als PO möchte ich sehen, welche Dokumente bereits hochgeladen sind und ob deren Verarbeitung in GraphMesh abgeschlossen ist.
3. Als PO möchte ich versehentlich hochgeladene oder veraltete Dokumente wieder entfernen können.
4. Als PO möchte ich, dass die Projekt-Anlage und der Wizard-Flow auch dann funktionieren, wenn GraphMesh gerade nicht erreichbar ist.

## Acceptance Criteria

- [ ] Neuer "Documents"-Tab erscheint in der Workspace-Sidebar (analog Decisions/Clarifications/Tasks).
- [ ] Upload einer PDF-Datei < 10 MB erscheint sofort in der Liste mit State `UPLOADED`.
- [ ] Liste pollt alle 3 Sekunden den Status, solange mindestens ein Dokument nicht in einem terminalen State (`EXTRACTED`/`FAILED`) ist.
- [ ] Polling stoppt automatisch, wenn alle Dokumente terminal sind oder der Tab nicht aktiv ist (`document.hidden`).
- [ ] Datei > 10 MB → HTTP 413 mit Body `{ error: "FILE_TOO_LARGE", maxBytes: 10485760 }`.
- [ ] Datei mit nicht erlaubtem MIME-Type → HTTP 415 mit Body `{ error: "UNSUPPORTED_TYPE", allowed: [...] }`.
- [ ] Erlaubte MIME-Types: `application/pdf`, `text/markdown`, `text/plain`.
- [ ] Erster Upload für ein Projekt → `project.json` enthält danach das Feld `collectionId` (vorher `null`).
- [ ] Zweiter Upload für dasselbe Projekt → kein zusätzlicher `createCollection`-Call (nur `uploadDocument`).
- [ ] GraphMesh nicht erreichbar → HTTP 503 `{ error: "GRAPHMESH_UNAVAILABLE" }`, Toast im Frontend, restliche Wizard-Funktionalität läuft normal weiter.
- [ ] GraphMesh antwortet mit GraphQL-Errors → HTTP 502 `{ error: "GRAPHMESH_ERROR", detail: "..." }`.
- [ ] Document löschen → verschwindet aus Liste (sofern `deleteDocument`-Mutation in GraphMesh existiert; siehe Verifikation unten).
- [ ] Bestehende Projekte ohne `collectionId` laden korrekt (Default `null`, kein Migrationsbedarf).
- [ ] Backend-Tests: `GraphMeshClientTest` (MockWebServer), `DocumentServiceTest` (gemockter Client), `DocumentControllerTest` (`@WebMvcTest`), erweiterter `ProjectStorageTest`.
- [ ] Manuell verifiziert: Upload → State-Übergang `UPLOADED → PROCESSING → EXTRACTED` in der UI sichtbar.

## Technische Details

### Backend

**Neue Dateien**

- `infrastructure/graphmesh/GraphMeshClient.kt` — Spring `RestClient`-Wrapper, sendet GraphQL-Queries an `${graphmesh.url}`, mappt HTTP-/GraphQL-Errors auf `GraphMeshException`.
- `infrastructure/graphmesh/GraphMeshConfig.kt` — `@ConfigurationProperties("graphmesh")` mit Feldern `url: String`, `requestTimeout: Duration` (Default `30s`).
- `infrastructure/graphmesh/GraphMeshException.kt` — Sealed-Class oder einfache Exception mit Subtypen `Unavailable` / `GraphQlError`.
- `domain/Document.kt` — `data class Document(id: String, title: String, mimeType: String, state: DocumentState, createdAt: String)` + Enum `DocumentState { UPLOADED, PROCESSING, EXTRACTED, FAILED }`.
- `service/DocumentService.kt` — Orchestriert: `ensureCollection(project)`, `upload(projectId, file)`, `list(projectId)`, `delete(projectId, docId)`.
- `api/DocumentController.kt` — REST-Endpoints unter `/api/v1/projects/{projectId}/documents`.

**Modifizierte Dateien**

- `domain/Project.kt` — neues optionales Feld `collectionId: String? = null` (Default für Backwards-Compat bestehender JSON-Files).
- `src/main/resources/application.yml` — neue Sektion `graphmesh.url=${GRAPHMESH_URL:http://localhost:8083/graphql}`, `graphmesh.request-timeout=30s`. Multipart-Limits: `spring.servlet.multipart.max-file-size=10MB`, `spring.servlet.multipart.max-request-size=12MB`.
- `src/test/resources/application.yml` — analog (Test-URL kann auf MockWebServer-Port gesetzt werden, falls integration-style).
- `api/GlobalExceptionHandler.kt` — Mappings für `GraphMeshException.Unavailable` → 503, `GraphMeshException.GraphQlError` → 502, Multipart-Size-Exceeded → 413, unsupported MIME → 415.

**REST-API**

| Methode | Pfad | Body | Response |
|---|---|---|---|
| `POST` | `/api/v1/projects/{id}/documents` | `multipart/form-data: file` | `201 { documentId, title, mimeType, state }` |
| `GET`  | `/api/v1/projects/{id}/documents` | — | `200 [{ id, title, mimeType, state, createdAt }]` |
| `GET`  | `/api/v1/projects/{id}/documents/{docId}` | — | `200 { id, title, mimeType, state, createdAt }` |
| `DELETE` | `/api/v1/projects/{id}/documents/{docId}` | — | `204` |

**GraphMesh-Calls (vom Backend)**

| Trigger | GraphQL-Operation |
|---|---|
| Erster Upload für Projekt | `mutation createCollection(input: { name })` (Beschreibung weggelassen — `Project` hat kein Description-Feld) |
| Jeder Upload | `mutation uploadDocument(input: { collectionId, title, mimeType, content })` (`content` Base64) |
| Liste | `query documents(collectionId: ID!) { id title mimeType state createdAt }` |
| Löschen | `mutation deleteDocument(id: ID!)` *— Existenz vor Implementierung verifizieren, siehe Risiken* |

**Lazy-Collection-Logik**

```
DocumentService.upload(projectId, file):
    project = projectStorage.load(projectId)
    if project.collectionId == null:
        collId = graphMeshClient.createCollection(name = project.name)
        project = project.copy(collectionId = collId, updatedAt = now())
        projectStorage.save(project)
    return graphMeshClient.uploadDocument(
        collectionId = project.collectionId!!,
        title = file.originalFilename,
        mimeType = file.contentType,
        content = Base64.encode(file.bytes)
    )
```

### Frontend

**Neue Dateien**

- `components/documents/DocumentsPanel.tsx` — Drop-zone (mit Datei-Picker als Fallback), Liste der Dokumente mit Status-Badges (`UPLOADED`/`PROCESSING` neutral, `EXTRACTED` grün, `FAILED` rot), Trash-Icon pro Zeile.
- `lib/stores/document-store.ts` — Zustand-Store: `documents[]`, `loadDocuments(projectId)`, `uploadDocument(projectId, file)`, `deleteDocument(projectId, docId)`, Polling-Hook (`useDocumentPolling`).

**Modifizierte Dateien**

- `lib/api.ts` — neue Funktionen `uploadDocument`, `listDocuments`, `getDocument`, `deleteDocument`. Type-Definitionen für `Document` und `DocumentState`.
- `app/projects/[id]/page.tsx` — `rightTab`-State-Union um `"documents"` erweitern; Tab-Button + Content-Branch für `<DocumentsPanel projectId={id} />` ergänzen.

**Polling-Verhalten**

- Polling-Intervall: 3 Sekunden.
- Aktiv nur, wenn ≥ 1 Dokument in `UPLOADED`/`PROCESSING`.
- Pausiert bei `document.hidden === true`.
- Stoppt vollständig beim Unmount des Tabs.

### Konfiguration

```yaml
graphmesh:
  url: ${GRAPHMESH_URL:http://localhost:8083/graphql}
  request-timeout: 30s

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
```

GraphMesh-Auth wird **bewusst nicht** vorbereitet (laut Integration-Doc gibt es aktuell keine). Falls später eingeführt: optionales `graphmesh.api-key`-Feld nachziehen, Header `Authorization: Bearer …` setzen.

### Tests

**Backend (TDD vor Implementierung)**

- `GraphMeshClientTest` — MockWebServer (OkHttp), prüft GraphQL-Request-Body, Response-Parsing, Error-Mapping (`Unavailable` bei Connection-Refused, `GraphQlError` bei `errors[]`-Array in Response).
- `DocumentServiceTest` — gemockter `GraphMeshClient`: Lazy-Collection-Erzeugung, idempotenter zweiter Upload (kein zweites `createCollection`), `collectionId` wird nach erfolgreichem `createCollection` persistiert (auch wenn `uploadDocument` danach scheitert).
- `DocumentControllerTest` (`@WebMvcTest`) — Multipart-Validierung (Größe, MIME), Status-Codes (201, 404, 413, 415, 502, 503), JSON-Shape.
- `ProjectStorageTest` (erweitert) — `collectionId` wird korrekt serialisiert/deserialisiert; Default `null` für JSON-Files ohne das Feld.

**Frontend**

- Frontend hat keinen Test-Runner (siehe `frontend/CLAUDE.md`) — verifiziert wird ausschließlich per **manuellem Browser-Smoke-Test**, dokumentiert im done-doc:
  - Upload PDF < 10 MB → erscheint in Liste mit `UPLOADED`-Badge.
  - Polling-Intervall sichtbar in Network-Tab; stoppt nach `EXTRACTED`/`FAILED`.
  - Wechsel zu anderem Tab → kein Polling-Request mehr (Network-Tab).
  - Datei > 10 MB → 413-Toast.
  - GraphMesh stoppen → Upload schlägt mit 503-Toast fehl, Wizard läuft normal.

### Risiken & Verifikation vor Implementierung

1. **`deleteDocument`-Mutation existiert?** Per Schema-Introspection (`{ __schema { mutationType { fields { name } } } }`) gegen lokales GraphMesh prüfen. Falls nicht: Delete-Endpoint im Plan auf "out of scope, GraphMesh muss erweitert werden" zurückstufen — Liste + Upload bleiben.
2. **`documents(collectionId: ID!)`-Query-Shape** — gleiches Vorgehen, verifizieren ob `state` und `createdAt` direkt am Document hängen oder anders heißen.
3. **GraphMesh-CORS** — laut Doc nur `localhost:3002` erlaubt. Da **alle** Calls vom Backend gehen, irrelevant.

### Abwärtskompatibilität

- Bestehende `project.json`-Dateien ohne `collectionId`-Feld laden korrekt, weil das Feld als `String? = null` definiert ist (kotlinx.serialization Default).
- Keine Migrations-Skripte nötig.

## Abhängigkeiten

- Feature 0 (Project Setup), Feature 1 (Idea-to-Spec Flow) — bestehender Projekt-Lifecycle.
- Externes System: GraphMesh muss unter `${GRAPHMESH_URL}` erreichbar sein. Lokal: `docker-compose up -d` im GraphMesh-Repo + `./gradlew bootRun` auf Port 8083.

## Aufwand

**M (Medium)** — neuer Backend-Modul (`infrastructure/graphmesh/`), neuer Service + Controller, kleinerer Frontend-Tab mit Polling-Hook, ein neues Feld in `Project`. Kein UI-Redesign, keine Datenmigration, keine Änderung am Wizard-Flow.

---

## Erweiterung 2026-04-26: Lokale Persistenz + Export

### Motivation

Hochgeladene Dokumente sind aktuell ausschließlich in GraphMesh sichtbar. Der User möchte sie auch im Frontend-Explorer-Tree (`data/projects/{id}/...`) sehen und beim ZIP-Export optional einbeziehen können. GraphMesh bleibt Source of Truth für die strukturierte Document-Liste/States; lokal entsteht eine zusätzliche, lesbare Kopie.

### Zusätzliche User Stories

5. Als PO möchte ich meine hochgeladenen Dokumente im Projekt-Explorer-Tree sehen, damit ich nicht zwischen Tabs wechseln muss.
6. Als PO möchte ich beim ZIP-Export entscheiden können, ob meine Originaldateien mit ins Archiv kommen.

### Zusätzliche Acceptance Criteria

- [ ] Erfolgreicher Upload legt zusätzlich zur GraphMesh-Speicherung eine Kopie unter `data/projects/{id}/uploads/{filename}` ab.
- [ ] Schreib-Fehler beim lokalen Speichern bricht den Upload **nicht** ab (GraphMesh ist authoritativ; Fehler wird geloggt).
- [ ] Zweiter Upload mit identischem Titel → lokale Datei wird automatisch umbenannt: `spec.pdf`, `spec (2).pdf`, `spec (3).pdf` (wie macOS/Finder). Title in GraphMesh bleibt unverändert.
- [ ] Delete eines Dokuments entfernt sowohl GraphMesh-Eintrag als auch lokale Datei + Index-Eintrag.
- [ ] `uploads/` erscheint automatisch im Frontend-Explorer-Tree (kein Hidden-Folder).
- [ ] Klick auf eine PDF im Explorer öffnet den `SpecFileViewer` mit Hinweis „Binärdatei – keine Inline-Vorschau" statt Crash/Müll.
- [ ] `ExportDialog` hat eine vierte Checkbox „Documents" (Default **an**). Beim Export landen die Files unter `<prefix>/uploads/{filename}` im ZIP. `.index.json` wird ausgelassen.
- [ ] Index-Datei `data/projects/{id}/uploads/.index.json` mappt `documentId → filename` und wird konsistent zu Upload/Delete fortgeschrieben.
- [ ] Filename-Sanitize: `/`, `\`, `..` werden entfernt; leerer Title → fallback `"document"`; max 255 Zeichen.

### Technische Details (Erweiterung)

**Neue Dateien**

- `storage/UploadStorage.kt` — `save(projectId, docId, title, bytes) → filename`, `delete(projectId, docId)`, `list(projectId): List<String>`. Verwaltet `uploads/.index.json` intern. Filename-Sanitize + Auto-Rename-Logik.

**Modifizierte Dateien**

- `service/DocumentService.kt` — Nach erfolgreichem GraphMesh-`uploadDocument` zusätzlich `UploadStorage.save(...)`. In `delete(...)` zusätzlich `UploadStorage.delete(...)`.
- `api/FileController.kt` — In `readFile`: Binär-Extensions (`.pdf`, `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, `.zip`) erkennen und `FileContent` mit `binary: true` ohne `content`-Feld zurückgeben (kein `Files.readString` für Binärdaten).
- `domain/FileContent.kt` — neues optionales Feld `binary: Boolean = false`.
- `domain/ExportModels.kt` — `ExportRequest` erhält `val includeDocuments: Boolean = true`.
- `export/ExportService.kt` — Wenn `includeDocuments`: alle Dateien aus `data/projects/{id}/uploads/` (außer `.index.json`) als ZIP-Entries unter `<prefix>/uploads/{filename}` packen.
- `lib/api.ts` (Frontend) — `FileContent` um `binary?: boolean` erweitern; `exportProject(...)` um `includeDocuments` ergänzen.
- `components/explorer/SpecFileViewer.tsx` — Bei `binary === true` Hinweis-Box rendern statt Code-Highlight.
- `components/export/ExportDialog.tsx` — Vierte Checkbox „Documents" (default an), Beschreibung „Hochgeladene Dateien aus uploads/".

**Auto-Rename-Algorithmus**

```
sanitized = sanitizeFilename(title)
candidate = sanitized
n = 2
while uploads/{candidate} existiert:
    base, ext = splitExt(sanitized)
    candidate = "{base} ({n}){ext}"
    n++
write uploads/{candidate}
index[docId] = candidate
```

**Reihenfolge beim Upload**

```
DocumentService.upload(projectId, file):
    project = projectStorage.load(...)
    [...lazy collection logic...]
    document = graphMeshClient.uploadDocument(...)   // GraphMesh first (authoritativ)
    try:
        UploadStorage.save(projectId, document.id, title, content)
    catch IOException:
        log.warn(...) — Upload bleibt erfolgreich
    return document
```

### Tests (Erweiterung)

- `UploadStorageTest` (neu) — Save/Delete-Roundtrip, Auto-Rename `(2)/(3)` bei wiederholtem Title, Index-Konsistenz nach mehreren Uploads/Deletes, Sanitize von Path-Traversal-Zeichen.
- `DocumentServiceTest` (erweitert) — Upload schreibt zusätzlich lokal; Delete entfernt lokale Datei; lokaler Schreib-Fehler bricht GraphMesh-Upload nicht ab.
- `FileControllerTest` (neu, falls nicht vorhanden) — PDF-Read liefert `FileContent` mit `binary=true` ohne Crash.
- `ExportServiceTest` (erweitert) — `includeDocuments=true` packt `uploads/`-Files in ZIP, `.index.json` ausgeschlossen; `includeDocuments=false` → kein `uploads/`-Ordner im ZIP.

### Frontend-Smoke-Test (Erweiterung)

- Upload PDF → Datei erscheint im Explorer unter `uploads/` (Refresh ggf. nötig).
- Klick auf PDF im Explorer → Viewer-Modal mit Hinweis „Binärdatei".
- Klick auf `.md`-Upload → Markdown-Vorschau wie gehabt.
- Doppelter Upload mit gleichem Titel → zweite Datei heißt `<name> (2).<ext>` im Explorer.
- Delete im Documents-Panel → Datei verschwindet auch aus Explorer (nach Refresh).
- ZIP-Export mit aktivierter „Documents"-Checkbox → ZIP enthält `<prefix>/uploads/...`.

### Aufwand (Erweiterung)

**S (Small)** — eine neue Storage-Klasse, leichte Erweiterung von `DocumentService`/`ExportService`/`FileController`, eine neue Checkbox im ExportDialog, kleine Anpassung im SpecFileViewer.
