# Design-Bundle-Docs-Relocation — Design Spec

**Datum:** 2026-05-04
**Bezug:** Refactor von Feature 40 (Design-Bundle-Step). Verschiebt das Design-Bundle-Storage-Layout, damit Bundle-Files im Spec-File-Explorer sichtbar sind und im Project-Export an der von Downstream-Tools erwarteten Stelle (`docs/design/`) liegen.

## Motivation

Heute liegen Design-Bundle-Files unter `data/projects/{id}/design/files/`, das Manifest unter `data/projects/{id}/design/manifest.json`, und der Original-ZIP-Upload bleibt als `bundle.zip` daneben liegen. Das hat drei Probleme:

1. Die Files sind im Spec-File-Explorer **nicht sichtbar**, weil der Explorer nur unter `docs/` listet.
2. Im Project-Export landen sie unter `prefix/design/files/...` — Downstream-Tools (insb. agentische Implementierungs-Workflows, die das Export-ZIP konsumieren) erwarten alle visuellen Spec-Artefakte unter `docs/design/`.
3. `bundle.zip` belegt unnötig Speicher, ohne dass es eine Re-Download-Funktion gibt — wird nach dem Extract nie wieder gelesen.

## Ziel

Storage-Layout so umstellen, dass:

- alle entpackten Bundle-Files unter `projects/{id}/docs/design/` liegen (flach, kein `files/`-Subdir),
- das Manifest unter `projects/{id}/docs/design/manifest.json` liegt,
- der Original-ZIP-Upload nach erfolgreichem Extract verworfen wird (kein `bundle.zip` mehr persistiert),
- die Bundle-Files automatisch im Spec-File-Explorer sichtbar werden (durch die existierende Listing-Logik unter `docs/`),
- der Project-Export ohne expliziten Design-Block die Files unter `prefix/docs/design/...` ausliefert.

## Scope

### In Scope

- `DesignBundleStorage`: Pfad-Konstanten umstellen, ZIP-Persistenz entfernen, `delete()` an neuen Single-Prefix-Layout anpassen.
- `ExportService`: expliziten Design-Block entfernen — die existierende `docs/`-Iteration übernimmt.
- Tests: Asserts auf neue Pfade, Regression-Schutz gegen Doppel-Export und gegen Diff-Sync-Lösch des `docs/design/`-Pfads.
- Spec dokumentiert die `docs/design/` ↔ `docs/frontend/design.md` Naming-Distinktion.

### Out of Scope

- Migration bestehender `data/projects/{id}/design/`-Verzeichnisse — Dev-Reset (`rm -rf`) ist die Lösung. Backend ignoriert die alten Pfade nach dem Code-Wechsel.
- Re-Download-Funktion für das Original-ZIP (existierte vorher nicht, gibt's auch danach nicht).
- Änderung des Iframe-URL-Patterns (`/api/v1/projects/{id}/design/files/**` bleibt — URL und Storage entkoppelt).
- Änderungen am Frontend (`useDesignBundleStore`, `DesignForm`, `DesignIframePreview`).
- Änderungen am `DesignSummaryAgent` (liest weiterhin transparent über `storage.readFile`).
- Defensive `NEVER_MANAGED_DIRS`-Konstante in `ProjectService` — Schutz durch Konvention reicht (gleicher Mechanismus wie für `docs/uploads/`).

## Architektur

### Storage-Layout

**Vorher**

```
projects/{id}/
  design/
    bundle.zip
    manifest.json
    files/
      Scheduler.html
      design-canvas.jsx
      assets/...
  docs/
    features/
    architecture/
    backend/
    frontend/
```

**Nachher**

```
projects/{id}/
  docs/
    design/
      manifest.json
      Scheduler.html
      design-canvas.jsx
      assets/...
    features/
    architecture/
    backend/
    frontend/
```

`projects/{id}/design/` (Top-Level-Dir) verschwindet komplett.

### URL-Pattern (unverändert)

Iframe lädt weiterhin über `/api/v1/projects/{id}/design/files/<relPath>`. Der Controller mapped intern `relPath` auf den ObjectStore-Key `projects/{id}/docs/design/<relPath>`. URL und Storage-Layout sind bewusst entkoppelt — das URL-Segment `files/` bleibt als historisches Relikt erhalten, weil eine Änderung Frontend-Brüche (`entryUrl`/`bundleUrl` in `DesignBundleResponse`) und Smoke-Test-Updates erzwingen würde, ohne semantischen Mehrwert.

### Schutz vor Diff-Sync-Lösch

`ProjectService.regenerateDocsScaffold` löscht orphans nur in Verzeichnissen, in denen der `DocsScaffoldGenerator` mindestens eine Datei rendert (`managedDirs` aus `desired`-Pfaden). Da der Generator nichts unter `docs/design/` rendert, ist der Pfad nie in `managedDirs`, und User-Uploads bleiben unangetastet. Gleicher Schutz wie für `docs/uploads/`, `docs/decisions/`, `docs/clarifications/`, `docs/tasks/`. Ein neuer Test (`ProjectServiceDocsScaffoldTest.designDirNotWipedOnRegen`) sichert die Konvention explizit ab.

### Naming-Distinktion: `docs/design/` vs. `docs/frontend/design.md`

Beide Pfade existieren parallel und beziehen sich auf unterschiedliche Konzepte:

- **`docs/frontend/design.md`** — vom `DocsScaffoldGenerator` aus dem `frontendContent`-Spec gerenderte Frontend-Design-Spezifikation (Markdown). Lebt im `docs/frontend/`-Subdir und ist „managed" (wird bei jedem Spec-Save überschrieben).
- **`docs/design/`** — vom User hochgeladenes Design-Bundle (Visual Mockups, Canvas, JSX, Assets). Lebt im eigenen Subdir, ist „non-managed" (wird nicht vom Scaffold angefasst).

Konflikt-frei, weil unterschiedliche Subdirs. Die Spec dokumentiert die Distinktion zur Vermeidung von Verwirrung beim Lesen des Codes.

## Komponenten-Änderungen

### `backend/.../storage/DesignBundleStorage.kt` (geändert)

| Heute | Neu |
|---|---|
| `filesPrefix(projectId) = "projects/$projectId/design/files/"` | `filesPrefix(projectId) = "projects/$projectId/docs/design/"` |
| `manifestKey(projectId) = "projects/$projectId/design/manifest.json"` | `manifestKey(projectId) = "projects/$projectId/docs/design/manifest.json"` |
| `zipKey(projectId) = "projects/$projectId/design/bundle.zip"` | **entfernt** |
| `save()` schreibt `objectStore.put(zipKey(projectId), zipBytes, "application/zip")` | **entfernt** |
| `delete()` ruft `deleteFiles(...)`, `objectStore.delete(zipKey(...))`, `objectStore.delete(manifestKey(...))` | `delete()` ruft nur noch `deleteFiles(...)` — der Prefix umfasst Manifest mit |
| Methoden-Signaturen | unverändert |

`readFile(projectId, relPath)` liest weiterhin via `fileKey(projectId, relPath)`, das jetzt auf den neuen Prefix expandiert — null Logik-Änderung.

### `backend/.../export/ExportService.kt` (geändert)

- Constructor-Injection `private val designBundleStorage: DesignBundleStorage` **entfernen**.
- Block Zeilen 86-97 (`val bundle = designBundleStorage.get(projectId); if (bundle != null) { … addEntry("$prefix/design/manifest.json", …); … addBinaryEntry("$prefix/design/files/${file.path}", …) }`) komplett **löschen**.
- Existierende Iteration Zeilen 42-45 (`for ((relativePath, content) in projectService.listDocsFiles(projectId)) { zip.addBinaryEntry("$prefix/$relativePath", content) }`) erledigt den Export der Bundle-Files automatisch unter `prefix/docs/design/...`.
- Imports `com.agentwork.productspecagent.domain.DesignBundle` und `com.agentwork.productspecagent.storage.DesignBundleStorage` aufräumen.

### `backend/.../api/DesignBundleController.kt` (unverändert)

URL-Pattern, `DesignBundleResponse.entryUrl`, `bundleUrl` und alle Header (`nosniff`, CSP) bleiben byte-identisch.

### Frontend (unverändert)

`useDesignBundleStore`, `DesignForm`, `DesignIframePreview`, `DesignDropzone`, `DesignBundleHeader`, `DesignPagesList`, `DesignReplaceConfirmDialog` bleiben unangetastet.

## Tests

### Geändert

| Datei | Änderung |
|---|---|
| `storage/DesignBundleStorageTest.kt` | Save-Roundtrip-Asserts auf neue ObjectStore-Keys (`projects/{id}/docs/design/...`). Test-Fall „bundle.zip wird persistiert" entfernen. Replace-Test bleibt logisch unverändert (atomares Wegräumen + neu schreiben), läuft im neuen Prefix. Path-traversal-Reject in `readFile` unverändert. |
| `export/ExportServiceDesignBundleTest.kt` | ZIP-Entry-Asserts: `prefix/design/files/...` → `prefix/docs/design/...`, `prefix/design/manifest.json` → `prefix/docs/design/manifest.json`. |
| `api/DesignBundleControllerTest.kt` | Keine Änderung — URL-Pattern stabil. |

### Neu

- **`DesignBundleStorageTest.zipIsNotPersisted`** — nach `save()` enthält `objectStore.listKeys("projects/{id}/")` keinen Key, der mit `bundle.zip` endet.
- **`ExportServiceDesignBundleTest.designFilesNotDuplicated`** — nach `exportProject(...)` gibt es keinen ZIP-Entry, dessen Pfad mit `<prefix>/design/` (ohne `docs/`) beginnt. Schützt gegen Regression bei versehentlichem Wieder-Einbau des Export-Blocks.
- **`ProjectServiceDocsScaffoldTest.designDirNotWipedOnRegen`** — Setup: schreibe `projects/{id}/docs/design/Scheduler.html` direkt über `objectStore`. Action: rufe `regenerateDocsScaffold(projectId)`. Assert: Datei existiert noch. Schützt explizit die Konvention „Scaffold-Generator emittiert nichts in `docs/design/`".

### Frontend

Kein Test-Runner. Manuelle Smoke-Tests:

1. SaaS-Projekt anlegen, bis MVP, dann DESIGN.
2. `examples/Scheduler.zip` hochladen → Iframe rendert wie heute (5 Pages).
3. Spec-File-Explorer öffnen → `docs/design/Scheduler.html`, `docs/design/design-canvas.jsx` etc. sichtbar im Tree; `manifest.json` nur mit Debug-Toggle.
4. Step abschließen → `spec/design.md` wird geschrieben (Agent-Pfad transparent), Flow → ARCHITECTURE.
5. Project-Export → ZIP enthält `<prefix>/docs/design/Scheduler.html`, `<prefix>/docs/design/manifest.json` etc., **kein** `<prefix>/design/...`.
6. Bundle löschen → alle Files unter `docs/design/` weg, `docs/features/`, `docs/architecture/` etc. unangetastet.
7. Replace-Flow: erneut anderes ZIP hochladen → vorheriger `docs/design/`-Inhalt vollständig ersetzt.

## Edge Cases

1. **Doppel-Export verhindert** durch den neuen `designFilesNotDuplicated`-Test.
2. **`docs/frontend/design.md` und `docs/design/` koexistieren** ohne Code-Konflikt — unterschiedliche Subdirs.
3. **`manifest.json` durch `.json`-Filter im Explorer versteckt** (`ExplorerPanel.tsx:20`), nur via Debug-Toggle sichtbar.
4. **Replace-Flow bleibt atomar** — `save()` ruft `deleteFiles(projectId)` (wegräumen), dann `objectStore.put(...)` für jede neue Datei inkl. Manifest. Single-threaded request, kein Halbzustand sichtbar.
5. **Path-Traversal-Schutz unverändert** — Controller-seitiger Reject (`../`, leading `/`, URL-decoded `%2e%2e`) und Storage-seitige Pfad-Normalisierung gegen abstrakten Root bleiben.
6. **Diff-Sync schützt `docs/design/` automatisch** — durch Konvention plus expliziten neuen Test.

## Migration

Keine. Wer in `data/projects/{id}/design/` (alte Layout) Reste hat, löscht selbst per `rm -rf data/projects/*/design/` oder ganz `rm -rf data/`. Backend ignoriert die alten Pfade nach dem Code-Wechsel komplett — keine Read-Pfade mehr aktiv. Begründung: keine Production-Deployment, Test-Daten sind günstig zu rekreieren, Migrations-Code würde Hot-Path-Komplexität für ein vorübergehendes Problem einführen.

## Akzeptanzkriterien

| # | Kriterium |
|---|---|
| 1 | Nach Upload existiert `projects/{id}/docs/design/manifest.json` und alle entpackten Bundle-Files liegen flach unter `projects/{id}/docs/design/`, kein `files/`-Subdir |
| 2 | Nach Upload existiert **kein** Key in `projects/{id}/`, der mit `bundle.zip` endet |
| 3 | `projects/{id}/design/` (alte Top-Level-Dir) wird vom Backend nicht mehr beschrieben |
| 4 | Iframe lädt unverändert über `/api/v1/projects/{id}/design/files/<relPath>` und liefert die Bytes mit `nosniff` + CSP |
| 5 | `regenerateDocsScaffold(projectId)` löscht keine Datei unter `docs/design/` (auch nicht `manifest.json`) |
| 6 | Project-Export-ZIP enthält Bundle-Files unter `<prefix>/docs/design/...`, **null** Entries unter `<prefix>/design/...` |
| 7 | Spec-File-Explorer listet Bundle-Files unter `docs/design/` im Tree (default), `manifest.json` nur mit Debug-Toggle sichtbar |
| 8 | `delete()` räumt alle Keys unter `projects/{id}/docs/design/` weg, restliche `docs/`-Inhalte (`docs/features/`, `docs/architecture/`, etc.) bleiben |
| 9 | `./gradlew test` grün, neue Tests (`zipIsNotPersisted`, `designFilesNotDuplicated`, `designDirNotWipedOnRegen`) bestehen |
| 10 | Manueller Browser-Smoke-Test (alle 7 Schritte oben) erfolgreich |
