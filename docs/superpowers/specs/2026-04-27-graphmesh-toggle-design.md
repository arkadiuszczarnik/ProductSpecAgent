# Design: GraphMesh-Toggle (Per-Backend + Per-Projekt)

**Datum:** 2026-04-27
**Feature:** 28 (Erweiterung)
**Status:** Spec — genehmigt, Plan folgt

## Kontext & Motivation

Feature 28 hat die GraphMesh-Integration produktiv gemacht: Dokumente werden hochgeladen, in GraphMesh-Collections persistiert, lokal gespiegelt, im Explorer sichtbar und im ZIP-Export bündelbar. In der Praxis ist GraphMesh aber nicht immer verfügbar oder gewünscht — z. B. bei Demos ohne Container, in Offline-Setups oder weil ein Projekt schlicht keine RAG-Funktionalität benötigt. Aktuell zwingt jeder Upload einen GraphMesh-Roundtrip; geht GraphMesh down, scheitert das ganze Feature.

Diese Erweiterung führt einen **zwei-stufigen Schalter** ein, mit dem GraphMesh deployment-weit (Backend) und pro Projekt (UI) deaktiviert werden kann. Im deaktivierten Zustand läuft das Documents-Feature im **Lokal-only-Modus**: Dateien landen ausschließlich in `data/projects/{id}/uploads/`, sind im Explorer sichtbar und im ZIP exportierbar — nur keine RAG-Verarbeitung, keine States, kein Polling.

## Ziele

- Eine deployment-weite Hard-Off-Schaltung von GraphMesh per `application.yml` / ENV.
- Eine per-Projekt UI-Schaltung im Workspace, die GraphMesh für genau dieses Projekt aktiviert/deaktiviert.
- Backend hat Vorrang: deaktivierter Backend-Schalter überschreibt jede Projekt-Aktivierung.
- **Default für beide: aus.** Frische Installationen laufen ohne GraphMesh-Abhängigkeit.
- Lokal-only-Modus liefert weiterhin Upload, Listen, Delete, Explorer-Sichtbarkeit, Export.

## Nicht-Ziele

- Keine Migration bestehender GraphMesh-Daten in den Lokal-Modus oder umgekehrt.
- Keine retroaktive Aktivierung (existierende lokale Files werden nicht in GraphMesh nachgeladen, wenn der Toggle umgelegt wird).
- Kein User-Account-spezifisches Setting — der Schalter lebt am Projekt, nicht am User.
- Kein „Mixed-Mode" pro Projekt (z. B. neue Uploads in GraphMesh, alte lokal). Genau eine Quelle pro Operation entscheidet.
- Kein Per-Sitzungs-Schalter (kein temporäres Override pro Browser-Tab).

## Architektur

```
┌─────────────────────────────────────────────────────────────────┐
│ Backend                                                         │
│                                                                 │
│  application.yml                                                │
│   └─ graphmesh.enabled: false (default)                         │
│                                                                 │
│  GraphMeshConfig.enabled: Boolean = false                       │
│        │                                                        │
│        ▼                                                        │
│  DocumentService.isGraphMeshActive(project): Boolean            │
│   = config.enabled  &&  project.graphmeshEnabled                │
│        │                                                        │
│        ├──► true  → GraphMesh-Pfad (wie bisher)                 │
│        │           + UploadStorage als Mirror                   │
│        │                                                        │
│        └──► false → Lokal-only-Pfad                             │
│                    UploadStorage als Source-of-Truth            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ Frontend                                                        │
│                                                                 │
│  GET /api/v1/config/features  →  { graphmeshEnabled: bool }     │
│   (einmal beim App-Start abgefragt)                             │
│                                                                 │
│  WorkspaceHeader                                                │
│   └─ Settings-Zahnrad → Popover                                 │
│       └─ Toggle "GraphMesh aktivieren"                          │
│           - aktiv,  wenn Backend-Feature true                   │
│           - disabled, wenn Backend-Feature false                │
│                                                                 │
│  DocumentsPanel + Explorer + ExportDialog: unverändert          │
│   (rendern Document-Liste je nach Quelle)                       │
└─────────────────────────────────────────────────────────────────┘
```

## Design-Entscheidungen

### Zwei-Stufen-Schalter mit Backend-Vorrang

Backend-Schalter ist Hard-Off: Wenn Operations gerade GraphMesh nicht laufen lassen wollen (Wartung, Demo), ist die Sache sofort entschieden — das UI kann nichts daran ändern. Der Per-Projekt-Schalter ist eine User-Convenience: ein Projekt explizit ohne GraphMesh fahren, auch wenn es technisch verfügbar wäre.

**Konsequenz:** Wenn Backend-Schalter `false`, wirkt jede Projekt-Aktivierung wie eine Deaktivierung. Frontend zeigt den Toggle dann disabled mit Tooltip-Erklärung.

**Default `false` für beide:** Frische Installationen kommen ohne GraphMesh-Abhängigkeit. Nutzer aktivieren bewusst — Documents als Feature ist nicht „blind aktiv".

### Lokal-only-Modus über erweiterte UploadStorage

Statt einer parallelen Speicherklasse erweitern wir `UploadStorage` so, dass sie pro Document mehr als nur den Filename kennt. Neuer Index:

```json
{
  "documents": [
    {
      "id": "uuid-1",
      "filename": "spec.pdf",
      "title": "spec.pdf",
      "mimeType": "application/pdf",
      "createdAt": "2026-04-27T10:00:00Z"
    }
  ]
}
```

`UploadStorage.save(...)` schreibt diese Metadaten. Neue Methode `listAsDocuments(projectId): List<Document>` liefert eine `Document`-Liste, die strukturell identisch ist mit der GraphMesh-Variante, aber mit `state = LOCAL`.

Im GraphMesh-Modus wird der Index trotzdem genutzt (für die Filename-Auflösung beim Delete und Export), nur die Service-Pfade tauchen unterschiedlich in die Datenquelle ab.

### Neuer DocumentState `LOCAL`

`DocumentState` bekommt einen vierten Wert `LOCAL`, der einen rein lokalen Lebenszyklus markiert. Er ist immer terminal (kein Polling, keine Verarbeitungs-Erwartung) und bekommt im Frontend ein neutrales graues Badge mit Label „Lokal".

**Warum nicht einfach `EXTRACTED`?** Wäre semantisch falsch — `EXTRACTED` impliziert RAG-Verarbeitung. `LOCAL` macht den Modus-Unterschied beim Debugging sichtbar (Logs, JSON-Antworten, Export-Inhalte).

### UI-Schalter im Workspace-Header

Toggle lebt im Workspace-Header (Zahnrad-Icon → Popover), nicht im Documents-Panel. Begründung: der Schalter wirkt sich auch auf das Documents-Panel selbst aus — bei Lokal-only fehlt der State-Indikator, ändert sich der Polling-Status etc. Wenn der Toggle im Panel sitzt, kann er bei Reset/Reload kurzzeitig „verschwinden". Header ist stabil sichtbar.

Toggle-Verhalten:
- Backend `enabled=false` → Toggle disabled, Tooltip „Backend GraphMesh ist deaktiviert (application.yml)".
- Backend `enabled=true` & Projekt `graphmeshEnabled=false` → Toggle aktiv, leer.
- Backend `enabled=true` & Projekt `graphmeshEnabled=true` → Toggle aktiv, gesetzt.

Klick führt zu `PATCH /api/v1/projects/{id}` mit `graphmeshEnabled`-Update; nach Erfolg wird `Project` im Store aktualisiert.

### Service-Pfad-Verzweigung

`DocumentService` bekommt eine private Methode:

```kotlin
private fun isGraphMeshActive(project: Project): Boolean =
    graphMeshConfig.enabled && project.graphmeshEnabled
```

Jede der vier Operationen (`upload`, `list`, `get`, `delete`) verzweigt am Anfang:

| Operation | GraphMesh-aktiv | Lokal-only |
|---|---|---|
| `upload` | createCollection (lazy) → uploadDocument → uploadStorage.save | UUID erzeugen → uploadStorage.save mit `LOCAL` |
| `list` | graphMeshClient.listDocuments | uploadStorage.listAsDocuments |
| `get` | graphMeshClient.getDocument | uploadStorage.getDocument (neu, liefert aus Index) |
| `delete` | graphMeshClient.deleteDocument + uploadStorage.delete | uploadStorage.delete |

`UploadStorage` lebt also auf beiden Pfaden — im GraphMesh-Modus als Mirror (wie bisher), im Lokal-only als Source of Truth.

### Frontend Feature-Endpoint

`GET /api/v1/config/features` liefert ein einfaches JSON mit booleschen Flags:

```json
{ "graphmeshEnabled": true }
```

Frontend ruft das einmal beim App-Start ab und cached es im `app-store` (oder einem neuen `feature-store`). Spätere Backend-Konfigurationsänderungen erfordern App-Reload — kein Live-Polling nötig, da der Schalter sich selten ändert.

### `graphmeshEnabled` Validation

Wenn der User versucht, den Per-Projekt-Schalter auf `true` zu setzen, während Backend `enabled=false` ist, lehnt das Backend die Operation mit `409 Conflict` ab und Frontend zeigt eine Toast-Nachricht. Die UI sollte das ohnehin durch den disabled-Toggle verhindern, aber Backend ist defensiv.

## Backend-Änderungen

| Datei | Änderung |
|---|---|
| `infrastructure/graphmesh/GraphMeshConfig.kt` | Neues Feld `enabled: Boolean = false`. |
| `domain/Project.kt` | Neues Feld `graphmeshEnabled: Boolean = false`. |
| `domain/Document.kt` | `DocumentState`-Enum: neuer Wert `LOCAL`. |
| `storage/UploadStorage.kt` | Index-Format erweitert auf `{ documents: [{id, filename, title, mimeType, createdAt}] }`. Neue Methoden: `listAsDocuments(projectId)`, `getDocument(projectId, docId)`. Bestehende `save/delete/list/read` bleiben in der Signatur, aber `save` nimmt jetzt zusätzlich `mimeType` und `createdAt`. |
| `service/DocumentService.kt` | Konstruktor erhält `GraphMeshConfig`. Neue private `isGraphMeshActive(project)`. Pro Operation Pfad-Verzweigung. |
| `service/ProjectService.kt` | Neue Methode `setGraphMeshEnabled(projectId, enabled)` mit Validation gegen Backend-Config. Wirft `GraphMeshDisabledException` bei `enabled=true` & Backend off. |
| `api/ProjectController.kt` | Neuer Endpoint `PATCH /api/v1/projects/{id}/graphmesh-enabled` mit Body `{ enabled: Boolean }`. |
| `api/ConfigController.kt` (neu) | `GET /api/v1/config/features` → `{ graphmeshEnabled: Boolean }`. |
| `api/GlobalExceptionHandler.kt` | Mapping `GraphMeshDisabledException` → 409 Conflict. |
| `src/main/resources/application.yml` | `graphmesh.enabled: ${GRAPHMESH_ENABLED:false}`. |
| `src/test/resources/application.yml` | Test-Default `graphmesh.enabled: true` (damit bestehende Tests laufen, soweit sie GraphMesh-Calls erwarten). Einzelne Tests können per `@TestPropertySource` überschreiben. |

## Frontend-Änderungen

| Datei | Änderung |
|---|---|
| `lib/api.ts` | `Project.graphmeshEnabled: boolean`. Neuer Type `FeatureFlags { graphmeshEnabled: boolean }`. Funktionen `getFeatures()`, `setProjectGraphMeshEnabled(projectId, enabled)`. |
| `lib/stores/feature-store.ts` (neu) | Lädt einmalig `getFeatures()`, hält `graphmeshEnabled`-Flag global. |
| `lib/stores/project-store.ts` | `graphmeshEnabled` im aktiven Projekt mitführen, Update-Action ergänzen. |
| `lib/stores/document-store.ts` | Polling pausiert, wenn alle Docs `state === "LOCAL"`. |
| `components/layout/WorkspaceHeader.tsx` (oder neue Komponente, falls bisher nicht existent) | Settings-Zahnrad-Button rechts neben Project-Title. Klick öffnet Popover via `base-ui/react`. Im Popover: Switch „GraphMesh aktivieren" mit Hinweis-Text bei Backend-Off. |
| `components/documents/DocumentsPanel.tsx` | Status-Badge-Style für `LOCAL` (graues Label „Lokal"). |
| `app/projects/[id]/page.tsx` | Beim Mount Feature-Flags + Project laden. Toggle-Visibility entsprechend. |

## REST-API

### Bestehend (unverändert in der Signatur, Verhalten je nach Modus)

| Methode | Pfad | Verhalten |
|---|---|---|
| `POST /api/v1/projects/{id}/documents` | wie bisher | wenn `isGraphMeshActive` → GraphMesh + lokal; sonst nur lokal |
| `GET /api/v1/projects/{id}/documents` | wie bisher | wenn aktiv → GraphMesh-Liste; sonst lokale Liste |
| `GET /api/v1/projects/{id}/documents/{docId}` | wie bisher | analog |
| `DELETE /api/v1/projects/{id}/documents/{docId}` | wie bisher | analog |

### Neu

| Methode | Pfad | Body | Response |
|---|---|---|---|
| `GET` | `/api/v1/config/features` | — | `200 { graphmeshEnabled: boolean }` |
| `PATCH` | `/api/v1/projects/{id}/graphmesh-enabled` | `{ enabled: Boolean }` | `200` (returns updated `Project`) oder `409 { error: "GRAPHMESH_DISABLED_BACKEND" }` |

## Fehlerbehandlung

| Szenario | HTTP | Body |
|---|---|---|
| Projekt-Toggle auf `true`, Backend `enabled=false` | 409 | `{ error: "GRAPHMESH_DISABLED_BACKEND" }` |
| Upload bei `isGraphMeshActive=false` | 201 | wie GraphMesh-Modus, aber `state: "LOCAL"` |

Bestehende GraphMesh-Fehler (503/502/413/415) gelten weiterhin, aber nur wenn `isGraphMeshActive=true`.

## Tests

| Klasse | Inhalt |
|---|---|
| `UploadStorageTest` (erweitert) | Index-Roundtrip mit Metadaten; `listAsDocuments` liefert vollständige `Document`-Objekte; Backwards-Compat mit altem Index-Format (Migration On-Read). |
| `DocumentServiceTest` (erweitert) | Backend off + Project on → Lokal-only; Backend on + Project off → Lokal-only; beide on → GraphMesh; beide off → Lokal-only. Pro Mode jeweils upload/list/delete-Pfad. |
| `ProjectServiceTest` (erweitert) | `setGraphMeshEnabled(true)` mit Backend off → wirft `GraphMeshDisabledException`. |
| `ProjectControllerTest` (erweitert) | PATCH-Endpoint Happy-Path + 409. |
| `ConfigControllerTest` (neu) | Liefert korrekten Feature-Status. |
| `DocumentControllerTest` (erweitert) | Lokal-only-Pfad: Upload landet ohne GraphMesh-Call mit `state=LOCAL`. |

Frontend manuell: Toggle umschalten, Upload-Verhalten beobachten, Disabled-Tooltip prüfen.

## Backwards-Kompatibilität

- Bestehende `project.json`-Dateien ohne `graphmeshEnabled` laden mit Default `false` (Lokal-only). User muss Toggle aktiv setzen, um GraphMesh zu nutzen.
- Bestehender `.index.json` im alten Format `{docId: filename}` wird beim ersten Lesen migriert: Metadaten werden aus dem Filesystem rekonstruiert (mtime → createdAt, MIME aus Extension), Schema upgegradet. Neue Saves nutzen das neue Format.
- Bestehende GraphMesh-Collections bleiben unangetastet. Wenn der User ein Projekt mit aktiver Collection auf Lokal-only umschaltet, bleibt die Collection in GraphMesh — sie ist nur nicht mehr UI-sichtbar. Beim Zurückschalten auf GraphMesh wird die alte `collectionId` weiterverwendet.

## Risiken

1. **Test-Default-Inversion:** Aktuell laufen Tests ohne `graphmesh.enabled`-Property — Default `false` würde fast alle bestehenden GraphMesh-Tests brechen. Lösung: `src/test/resources/application.yml` setzt `graphmesh.enabled: true`, einzelne Tests überschreiben gezielt.
2. **Migration der Index-Datei:** Robust gegen halbgeschriebene Indizes prüfen (z. B. JSON-Parsing-Fehler → leere Migration als Recovery, statt Crash).
3. **State-Polling-Logik:** Frontend muss erkennen, dass `LOCAL` immer terminal ist — sonst pollt es ewig auf einem stabilen State. Ein Test im manuellen Smoke-Test deckt das ab.

## Aufwand

**M (Medium)** — domänenseitig kleine Erweiterungen (Project, DocumentState), aber service-seitig durchgehende Pfad-Verzweigung in `DocumentService`. Neuer Endpoint, neue Storage-Methoden, neuer UI-Toggle. Keine Migration alter Daten in einen anderen Modus.
