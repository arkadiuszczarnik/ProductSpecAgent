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

### 4. Kuratierte Markdown-Pfade ins ZIP nach `docs/` umziehen

Die per `request.includeDecisions/Clarifications/Tasks` erzeugten kuratierten Markdown-Files ziehen ebenfalls unter `docs/`, damit das ZIP **eine konsistente `docs/`-Wurzel** für alle Spec-begleitenden Inhalte hat:

| Datei | alt (ExportService.kt) | neu |
|---|---|---|
| Zeile 47 | `"$prefix/decisions/..."` | `"$prefix/docs/decisions/..."` |
| Zeile 58 | `"$prefix/clarifications/..."` | `"$prefix/docs/clarifications/..."` |
| Zeile 71 | `"$prefix/tasks/..."` | `"$prefix/docs/tasks/..."` |

Konsequenz: in den Subordnern `docs/decisions/`, `docs/clarifications/`, `docs/tasks/` liegen dann **beide Sichten parallel** (Roh-JSON `{uuid}.json` aus `listDocsFiles` + kuratierte MD `001-slug.md`). Filenamen kollidieren nicht.

**`PLAN.md` und `SPEC.md` ziehen ebenfalls nach `docs/`:**

| Datei | alt (ExportService.kt) | neu |
|---|---|---|
| Zeile 31 | `"$prefix/SPEC.md"` | `"$prefix/docs/SPEC.md"` |
| Zeile 67 | `"$prefix/PLAN.md"` | `"$prefix/docs/PLAN.md"` |

**README.md-Generator (`generateReadme`)** muss die Struktur-Sektion (Zeile 94–98) auf die neuen Pfade aktualisieren:

```kotlin
appendLine("- `docs/SPEC.md` — Full product specification")
appendLine("- `docs/PLAN.md` — Implementation plan with tasks")
appendLine("- `docs/decisions/` — Key product decisions")
appendLine("- `docs/clarifications/` — Clarified requirements")
appendLine("- `docs/tasks/` — Individual task files")
```

### 5. `ExportRequest`-Flags `includeDecisions/Clarifications/Tasks` — Verhalten

Bewusst **unverändert lassen**. Sie gaten weiterhin nur die kuratierte Markdown-Sicht. Die Roh-JSON unter `docs/decisions/` etc. landet zusätzlich automatisch im ZIP über `listDocsFiles()`. Das ist konsistent mit der „alles mitnehmen"-Intention: ein Opt-Out auf JSON-Ebene wäre zusätzliche Komplexität ohne aktuellen UI-Konsumenten.

## Resultierende ZIP-Struktur

```
{prefix}/
  ├── README.md
  ├── .gitignore
  └── docs/
      ├── SPEC.md                    (Full product specification)
      ├── PLAN.md                    (Implementation plan)
      ├── features/*.md              (auto-generiert)
      ├── architecture/*.md
      ├── backend/*.md
      ├── frontend/*.md
      ├── decisions/
      │   ├── {uuid}.json            (Roh, via listDocsFiles)
      │   └── 001-slug.md            (kuratierte MD)
      ├── clarifications/
      │   ├── {uuid}.json
      │   └── 001-slug.md
      ├── tasks/
      │   ├── {uuid}.json
      │   └── 001-typ-slug.md
      └── uploads/*                  (binary)
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
  - `docs/decisions/{uuid}.json` (Roh-JSON via listDocsFiles)
  - `docs/decisions/001-slug.md` (kuratierte MD jetzt unter docs/)
  - analog für `docs/clarifications/` und `docs/tasks/`
  - `docs/uploads/<filename>` (Binärinhalt korrekt)
  - **keine** Top-Level-Ordner `decisions/`, `clarifications/`, `tasks/`, `uploads/` mehr im ZIP-Root
  - `SPEC.md` unter `docs/SPEC.md` (nicht mehr am ZIP-Root)
  - `PLAN.md` unter `docs/PLAN.md` (nicht mehr am ZIP-Root)
  - `README.md` weiterhin am ZIP-Root (alleiniger Top-Level-Einstiegspunkt)
- [ ] `ExportRequest`-Type (Backend + ggf. Frontend) hat kein `includeDocuments`-Feld mehr.
- [ ] `ExportService` injiziert `UploadStorage` nicht mehr.
