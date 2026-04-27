# Storage-Konsolidierung unter `docs/`

**Datum:** 2026-04-27
**Status:** Approved (pending user review)

## Motivation

Logische Gruppierung im Projekt-Filesystem: Alles, was den Spec inhaltlich begleitet — Decisions, Clarifications, Tasks und User-Uploads — soll unter dem gemeinsamen Dach `docs/` liegen, neben den bereits dort lebenden auto-generierten Markdown-Files (`features/`, `architecture/`, `backend/`, `frontend/`). Der Projekt-Root behält nur die strukturellen Top-Level-Artefakte (`project.json`, `flow-state.json`, `wizard.json`) sowie `spec/` als Quelle für den Generator.

Begründung gegen `spec/` unter `docs/`: `docs/features|architecture|backend|frontend/` ist Output, generiert aus `spec/` als Input. Beides unter `docs/` zu mischen verwischt diese Trennung.

## Zielstruktur

```
data/projects/{id}/
  ├── project.json
  ├── flow-state.json
  ├── wizard.json
  ├── spec/                       (unverändert — Quelle)
  └── docs/
      ├── features/               (auto-generiert, unverändert)
      ├── architecture/           (auto-generiert, unverändert)
      ├── backend/                (auto-generiert, unverändert)
      ├── frontend/               (auto-generiert, unverändert)
      ├── decisions/              ← verschoben aus projects/{id}/decisions/
      ├── clarifications/         ← verschoben aus projects/{id}/clarifications/
      ├── tasks/                  ← verschoben aus projects/{id}/tasks/
      └── uploads/                ← verschoben aus projects/{id}/uploads/
```

## Migration

Keine. `data/projects/` enthält nur `.gitkeep` — es gibt keine bestehenden Projekte im Repo. Falls lokal jemand Projekte unter der alten Struktur hat: einmaliges manuelles `mv` der vier Ordner. Kein Code-Pfad für Auto-Migration nötig (YAGNI).

## Backend-Änderungen

### 1. Storage-Klassen — Pfad-Anpassung (vier Klassen, je eine Zeile)

| Datei | Zeile | alt | neu |
|---|---|---|---|
| `storage/TaskStorage.kt` | 20 | `Path.of(dataPath, "projects", projectId, "tasks")` | `Path.of(dataPath, "projects", projectId, "docs", "tasks")` |
| `storage/DecisionStorage.kt` | 20 | `Path.of(dataPath, "projects", projectId, "decisions")` | `Path.of(dataPath, "projects", projectId, "docs", "decisions")` |
| `storage/ClarificationStorage.kt` | 20 | `Path.of(dataPath, "projects", projectId, "clarifications")` | `Path.of(dataPath, "projects", projectId, "docs", "clarifications")` |
| `storage/UploadStorage.kt` | 35 | `Paths.get(dataPath, "projects", projectId, "uploads")` | `Paths.get(dataPath, "projects", projectId, "docs", "uploads")` |

### 2. `ProjectStorage.listDocsFiles()` — binärfähig machen

`docs/uploads/` enthält nach dem Umzug Binärdateien (PDF, PNG, ...). Aktuelle Implementierung nutzt `Files.readString(file)` und würde an Binaries mit `MalformedInputException` scheitern.

**Signatur-Änderung:**

```kotlin
// alt
fun listDocsFiles(projectId: String): List<Pair<String, String>>

// neu
fun listDocsFiles(projectId: String): List<Pair<String, ByteArray>>
```

Implementierung tauscht `Files.readString(file)` gegen `Files.readAllBytes(file)`. Pass-through in `ProjectService.listDocsFiles` (Zeile 124–126) erbt die neue Signatur unverändert.

### 3. `ExportService` anpassen

**3a. ZIP-Eintrag wird binär** (Zeile 37–39):

```kotlin
// alt
for ((relativePath, content) in projectService.listDocsFiles(projectId)) {
    zip.addEntry("$prefix/$relativePath", content)         // String
}

// neu
for ((relativePath, content) in projectService.listDocsFiles(projectId)) {
    zip.addBinaryEntry("$prefix/$relativePath", content)   // ByteArray
}
```

`addBinaryEntry` existiert bereits (Zeile 230–234).

**3b. `request.includeDocuments`-Block entfernen** (Zeile 76–81):

Uploads kommen jetzt automatisch via `listDocsFiles()` unter `docs/uploads/` ins ZIP. Der separate Block würde sie ein zweites Mal unter `uploads/` ablegen → redundant.

**3c. `ExportRequest.includeDocuments` Flag entfernen** (`domain/ExportModels.kt:7`):

Flag wird nirgendwo vom Frontend gesetzt (alle Defaults greifen). Da sie funktional bedeutungslos wird, raus damit. Konsistent mit CLAUDE.md („avoid backwards-compat hacks for removed code").

**3d. Dependencies bereinigen:** `UploadStorage`-Injection in `ExportService` (Konstruktor-Parameter) entfernen, da nach Streichung des Blocks ungenutzt.

### 4. `ExportRequest`-Flags `includeDecisions/Clarifications/Tasks` — Verhalten

Bewusst **unverändert lassen**. Sie steuern weiterhin die kuratierte Markdown-Sicht (`{prefix}/decisions/001-slug.md`, ...). Die Roh-JSON unter `docs/decisions/` etc. landet zusätzlich automatisch im ZIP über `listDocsFiles()`. Das ist konsistent mit der „alles mitnehmen"-Intention: ein Opt-Out auf JSON-Ebene wäre zusätzliche Komplexität ohne aktuellen UI-Konsumenten.

## Resultierende ZIP-Struktur

```
{prefix}/
  ├── README.md
  ├── SPEC.md
  ├── PLAN.md
  ├── .gitignore
  ├── docs/                       (via listDocsFiles)
  │   ├── features/*.md           (auto-generiert)
  │   ├── architecture/*.md
  │   ├── backend/*.md
  │   ├── frontend/*.md
  │   ├── decisions/*.json        (NEU im ZIP)
  │   ├── clarifications/*.json   (NEU)
  │   ├── tasks/*.json            (NEU)
  │   └── uploads/*               (NEU, binary)
  ├── decisions/001-slug.md       (kuratierte MD-Sicht, unverändert)
  ├── clarifications/001-slug.md  (unverändert)
  └── tasks/001-typ-slug.md       (unverändert)
```

## Tests anzupassen

Pfad-Erwartungen prüfen / aktualisieren in:

- `storage/ProjectStorageTest.kt`
- `storage/UploadStorageTest.kt`
- `api/FileControllerTest.kt`
- `api/ClarificationControllerTest.kt`
- `api/TaskControllerTest.kt`
- `api/DecisionControllerTest.kt`
- `api/ExportControllerTest.kt` — ZIP-Pfade: neu `docs/decisions/`, `docs/uploads/` etc.; Flag `includeDocuments` weg.
- `export/DocsScaffoldGeneratorTest.kt` — voraussichtlich keine Änderung (Generator schreibt nur explizite Pfade `docs/features/...` etc.); im Implementierungs-Plan verifizieren.

## Frontend

Keine Änderung. REST-API-Pfade (`/api/v1/projects/{id}/decisions`, `/uploads`, `/tasks`, `/clarifications`) bleiben identisch — sie sind orthogonal zur Filesystem-Layout-Frage. Falls `lib/api.ts` Felder vom Typ `ExportRequest` hat, prüfen und ggf. `includeDocuments` aus dem TypeScript-Type entfernen.

## Akzeptanzkriterien

- [ ] Backend startet, alle bestehenden Backend-Tests grün.
- [ ] Neues Projekt anlegen → `decisions/`, `clarifications/`, `tasks/`, `uploads/` werden unter `data/projects/{id}/docs/` (nicht mehr im Projekt-Root) erzeugt.
- [ ] User-Upload (Datei via REST) landet in `data/projects/{id}/docs/uploads/<filename>`.
- [ ] Handoff-ZIP enthält
  - `docs/decisions/{uuid}.json` (Roh-JSON)
  - `docs/uploads/<filename>` (Binärinhalt korrekt)
  - `decisions/001-slug.md` (kuratierte MD weiterhin)
  - **kein** zweites `uploads/<filename>` außerhalb von `docs/`
- [ ] `ExportRequest`-Type (Backend + ggf. Frontend) hat kein `includeDocuments`-Feld mehr.
- [ ] `ExportService` injiziert `UploadStorage` nicht mehr.
