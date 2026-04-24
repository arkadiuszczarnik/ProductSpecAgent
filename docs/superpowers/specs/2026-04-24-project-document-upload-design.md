# Design: Projekt-Dokumente in GraphMesh hochladen

**Datum:** 2026-04-24
**Feature:** 28
**Status:** Spec — genehmigt, Plan folgt

## Kontext & Motivation

Product-Spec-Agent generiert strukturierte Produkt-Specs aus User-Input und KI-Dialog. Für die nächste Stufe — RAG-gestützte Antworten und kontextbewusste Agents — muss pro Projekt eine Wissensbasis aus echten Dokumenten (Policies, bestehende Specs, Anforderungen) verfügbar sein. Diese Wissensbasis lebt extern in **GraphMesh**, einem GraphQL-/MCP-Service mit Document-RAG (siehe `docs/integration-koog-client.md`).

Aktuell hat Product-Spec-Agent keinen Weg, Dokumente zu einem Projekt anzubinden. Dieses Feature schafft den Upload-Pfad und persistiert die GraphMesh-`collectionId` pro Projekt, damit spätere RAG-Aufrufe die richtige Collection ansprechen.

## Ziele

- Pro Projekt können Nutzer Dokumente hochladen, listen und löschen.
- Der Upload landet in einer GraphMesh-Collection, deren ID im Projekt persistiert wird.
- Die Collection wird **lazy** (beim ersten Upload) erzeugt, damit Projekt-Anlage und Wizard-Flow von GraphMesh entkoppelt bleiben.
- Frontend zeigt den Verarbeitungs-Status pro Dokument live (Polling), kein blockiertes Backend.
- Saubere Fehler-Mappings, wenn GraphMesh nicht erreichbar oder ablehnend ist.

## Nicht-Ziele

- Keine Persistenz der Document-Metadaten im Product-Spec-Agent — Source of Truth ist GraphMesh.
- Keine RAG-Calls von Product-Spec-Agent zu GraphMesh in diesem Feature (nur Upload-Pfad).
- Keine GraphMesh-Auth-Mechanik (gibt es laut Integration-Doc nicht; bei Einführung später nachziehbar).
- Kein Sync von Projektnamen → Collection-Name nach Anlage. Collection-Name ist bewusst nur Label.
- Keine Persistente Upload-Queue / Retries / Background-Jobs.
- Keine Quotas oder Rate-Limits pro Projekt.
- Keine Erweiterung des E2E-Test-Setups (GraphMesh läuft nicht in CI).

## Architektur-Überblick

```
┌──────────────────────┐    upload+poll    ┌──────────────────────┐
│  Frontend (Next.js)  │ ─────────────────►│   Backend (Spring)   │
│                      │                   │                      │
│  DocumentsTab        │   /api/v1/        │  DocumentController  │
│  - drop-zone         │   projects/       │  DocumentService     │
│  - list w/ status    │   {id}/documents  │  GraphMeshClient     │
│                      │ ◄─────────────────│  (RestClient)        │
└──────────────────────┘                   └──────────┬───────────┘
                                                      │ GraphQL
                                                      ▼
                                           ┌──────────────────────┐
                                           │  GraphMesh :8083     │
                                           │  /graphql            │
                                           └──────────────────────┘

┌──────────────────────┐
│  ProjectStorage      │  ← Project bekommt neues Feld:
│  data/projects/{id}/ │     collectionId: String? = null
│  project.json        │
└──────────────────────┘
```

**Source of Truth:**
- `collectionId` → `project.json` (lokal).
- Document-Liste, States, Inhalte → GraphMesh (Live-Query, kein lokaler Cache).

**Das Backend ist Single Point of Contact zu GraphMesh.** Frontend kennt GraphMesh nicht, ruft nur eigene REST-Endpoints. Vorteile: einheitlicher API-Pfad, kein CORS-Konflikt (GraphMesh erlaubt aktuell nur `localhost:3002`), `collectionId`-Lebenszyklus liegt zentral im Backend.

## Design-Entscheidungen

### Lazy Collection-Lifecycle

Beim ersten Upload pro Projekt prüft `DocumentService`, ob `project.collectionId == null`. Wenn ja: `createCollection(name = project.name)` aufrufen, ID im Projekt persistieren, dann hochladen. Folge-Uploads übernehmen die ID direkt aus dem Projekt.

**Warum lazy?**
- Projekt-Anlage (heißer Wizard-Pfad) bleibt unabhängig von GraphMesh-Verfügbarkeit.
- Keine leeren Collections in GraphMesh für Projekte, die nie Dokumente bekommen.
- Marginale Mehrkosten beim ersten Upload (zwei GraphQL-Calls statt einem) sind unkritisch.

**Konsequenz für Persistenz:** `collectionId` wird **vor** dem `uploadDocument`-Call persistiert. Wenn `uploadDocument` danach scheitert, bleibt die leere Collection in GraphMesh und wird beim Retry wiederverwendet — keine doppelten Collections.

### CRUD-Scope

User können hochladen, listen und löschen. Kein Edit (GraphMesh hat keine Update-Mutation für Document-Inhalte; bei Bedarf delete + re-upload).

### Asynchrone Verarbeitung — Frontend-Polling

GraphMesh extrahiert Dokumente asynchron (`UPLOADED → PROCESSING → EXTRACTED`/`FAILED`). Wir wählen **fire-and-forget mit Frontend-Polling** statt Backend-Wait:

- Backend antwortet sofort nach `uploadDocument` mit `{ documentId, state: "UPLOADED" }`.
- Frontend pollt `GET /documents` alle 3 s, solange ≥ 1 Dokument nicht terminal.
- Polling pausiert bei `document.hidden`, stoppt beim Tab-Unmount.

**Warum nicht Backend-Wait?** Spring-MVC ist nicht für 60-Sekunden-Requests gemacht. Polling im Frontend ist robuster und gibt dem User Live-Feedback statt eines weißen Spinners.

### Datei-Whitelist & Größenlimit

Erlaubt: `application/pdf`, `text/markdown`, `text/plain`. Maximal 10 MB pro Datei.

**Warum so eng?**
- Vorhersehbares Verhalten — User weiß, was funktioniert.
- Vermeidet `FAILED`-State-Spam durch Datei-Typen, die GraphMesh ohnehin nicht extrahiert.
- 10 MB deckt typische Specs/Policies ab.

Spring-MVC-Multipart-Default ist 1 MB — wird in `application.yml` auf 10 MB hochgesetzt. `max-request-size` etwas höher (12 MB) für Multipart-Overhead.

### Naming & Rename

Collection-Name = `project.name` zum Anlage-Zeitpunkt. Spätere Projekt-Umbenennungen wirken **nicht** auf GraphMesh. Source of Truth ist die `collectionId`, der Name ist nur menschenlesbares Label in der GraphMesh-UI.

**Warum?** Das Integration-Doc erwähnt keine `updateCollection`-Mutation. Eine Sync-Logik wäre Spekulation und zusätzliche Fehlerquelle. Eindeutigkeit ist über die ID gewährleistet, nicht über den Namen.

### HTTP-Client-Wahl

Backend nutzt Spring `RestClient` (Spring Boot 4 nativ, synchron). Keine neue Dependency für Ktor — wir brauchen kein Coroutines-Streaming, jeder Call ist ein einzelner Request-Response-Zyklus.

## Backend-Änderungen

| Datei | Änderung |
|---|---|
| `domain/Project.kt` | Neues Feld `collectionId: String? = null`. |
| `domain/Document.kt` (neu) | `data class Document(id, title, mimeType, state: DocumentState, uploadedAt)` + Enum. |
| `infrastructure/graphmesh/GraphMeshClient.kt` (neu) | `RestClient`-Wrapper, GraphQL-Helper, Methoden `createCollection`, `uploadDocument`, `listDocuments`, `deleteDocument`. |
| `infrastructure/graphmesh/GraphMeshConfig.kt` (neu) | `@ConfigurationProperties("graphmesh")`. |
| `infrastructure/graphmesh/GraphMeshException.kt` (neu) | Subtypen `Unavailable`, `GraphQlError`. |
| `service/DocumentService.kt` (neu) | Lazy-Collection-Logik, Orchestrierung. |
| `api/DocumentController.kt` (neu) | REST-Endpoints, Multipart-Handling, Validierung. |
| `api/GlobalExceptionHandler.kt` | Mappings für `GraphMeshException`-Subtypen, MIME-/Size-Validierung. |
| `src/main/resources/application.yml` | `graphmesh.*`, `spring.servlet.multipart.*`. |
| `src/test/resources/application.yml` | analog. |

## Frontend-Änderungen

| Datei | Änderung |
|---|---|
| `lib/api.ts` | Type-Defs `Document`/`DocumentState`; Funktionen `uploadDocument`, `listDocuments`, `getDocument`, `deleteDocument`. |
| `lib/stores/document-store.ts` (neu) | Zustand-Store + Polling-Hook `useDocumentPolling`. |
| `components/documents/DocumentsPanel.tsx` (neu) | Drop-zone + Liste mit Status-Badges + Trash-Icon. |
| `app/projects/[id]/page.tsx` | `rightTab`-Union um `"documents"` erweitern; Tab-Button + Content-Branch ergänzen. |

## REST-API

| Methode | Pfad | Body | Response |
|---|---|---|---|
| `POST` | `/api/v1/projects/{id}/documents` | `multipart/form-data: file` | `201 { documentId, title, mimeType, state }` |
| `GET`  | `/api/v1/projects/{id}/documents` | — | `200 [{ id, title, mimeType, state, uploadedAt }]` |
| `GET`  | `/api/v1/projects/{id}/documents/{docId}` | — | `200 { id, title, mimeType, state, uploadedAt }` |
| `DELETE` | `/api/v1/projects/{id}/documents/{docId}` | — | `204` |

## Fehlerbehandlung

| Szenario | HTTP | Body |
|---|---|---|
| Datei > 10 MB | 413 | `{ error: "FILE_TOO_LARGE", maxBytes: 10485760 }` |
| MIME-Type abgelehnt | 415 | `{ error: "UNSUPPORTED_TYPE", allowed: [...] }` |
| Kein File im Multipart | 400 | `{ error: "MISSING_FILE" }` |
| Project nicht gefunden | 404 | `{ error: "PROJECT_NOT_FOUND" }` |
| Document nicht gefunden | 404 | `{ error: "DOCUMENT_NOT_FOUND" }` |
| GraphMesh nicht erreichbar | 503 | `{ error: "GRAPHMESH_UNAVAILABLE" }` |
| GraphMesh GraphQL-Errors | 502 | `{ error: "GRAPHMESH_ERROR", detail: "..." }` |

Document-State `FAILED` in der Liste: rotes Badge, Trash-Icon aktiv, kein Auto-Retry.

## Tests

| Klasse | Inhalt |
|---|---|
| `GraphMeshClientTest` | MockWebServer, Request-Body, Response-Parsing, Error-Mapping. |
| `DocumentServiceTest` | Gemockter Client: Lazy-Erzeugung, idempotenter zweiter Upload, Persistenz nach `createCollection`-Erfolg. |
| `DocumentControllerTest` (`@WebMvcTest`) | Multipart-Validierung, alle Status-Codes, JSON-Shape. |
| `ProjectStorageTest` (erweitert) | `collectionId` round-trip + Default `null` für alte JSON-Files. |
| `DocumentsPanel.test.tsx` | Drop-zone, State-Badges, Polling-Stop. |

E2E nicht erweitert. Manueller Smoke-Test im done-doc.

## Risiken & Verifikation vor Implementierung

1. **`deleteDocument`-Mutation in GraphMesh existiert?** Per Schema-Introspection prüfen (`{ __schema { mutationType { fields { name } } } }`). Fallback: Delete aus Plan entfernen, als Folge-Feature in GraphMesh.
2. **`documents(collectionId: ID!)`-Query-Shape** — verifizieren ob `state`/`uploadedAt` direkt am Document hängen.
3. **GraphMesh-Erreichbarkeit beim manuellen Test** — Container muss laufen. Stop-Test (Container herunterfahren) explizit Teil des Smoke-Tests.

## Abwärtskompatibilität

`Project.collectionId: String? = null` mit Default — bestehende `project.json`-Dateien laden ohne Migration.

## Abhängigkeiten

- Feature 0 (Project Setup), Feature 1 (Idea-to-Spec Flow).
- Externes System: GraphMesh unter `${GRAPHMESH_URL}` (Default `http://localhost:8083/graphql`).

## Aufwand

M (Medium).
