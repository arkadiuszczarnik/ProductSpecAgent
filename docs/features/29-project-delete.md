# Feature 29: Projekt aus der Übersicht löschen

## Zusammenfassung

Auf der Projekt-Übersichtsseite (`/projects`) erhält jede Karte einen Mülleimer-Button (Hover-Reveal, Tastatur-fokussierbar), der ein Bestätigungsmodal öffnet. Nach Bestätigung wird das Projekt über den bereits vorhandenen Backend-Endpoint `DELETE /api/v1/projects/{id}` hart gelöscht (Verzeichnis `data/projects/{id}/` inkl. aller Specs, Decisions, Clarifications, Tasks, Documents und Docs-Scaffold). Bei Fehler bleibt der Modal-Kontext offen und zeigt eine Inline-Fehlermeldung.

## User Stories

1. Als PO möchte ich ein Projekt aus meiner Übersicht entfernen, das ich versehentlich angelegt habe oder nicht mehr brauche.
2. Als PO möchte ich vor dem Löschen explizit bestätigen, weil die Aktion irreversibel ist und alle zugehörigen Dateien entfernt.
3. Als PO möchte ich bei einem Server-Fehler im Modal sehen, was schiefgegangen ist, und es erneut versuchen oder abbrechen können.

## Acceptance Criteria

- [ ] Mülleimer-Button erscheint oben rechts auf der Projekt-Karte bei Hover oder Tastatur-Fokus.
- [ ] Klick auf den Button öffnet ein Bestätigungsmodal und löst **keine** Card-Navigation aus.
- [ ] Modal zeigt den Projektnamen und einen Warntext zur Irreversibilität.
- [ ] „Löschen" ruft `DELETE /api/v1/projects/{id}` auf; bei Erfolg verschwindet das Projekt aus der Liste ohne Re-Fetch.
- [ ] Während des Löschens sind beide Footer-Buttons disabled, ESC und Backdrop-Klick werden ignoriert.
- [ ] Bei Server-Fehler bleibt das Modal offen, zeigt eine Inline-Fehlermeldung, „Löschen" ist wieder klickbar.
- [ ] „Abbrechen", ESC und Backdrop-Klick schliessen das Modal ohne Aktion.
- [ ] `aria-label` auf dem Trash-Button enthält den Projektnamen.
- [ ] `data/projects/{id}/` ist nach erfolgreichem Löschen im Dateisystem entfernt.

## Technische Details

### Geänderte Dateien

- `frontend/src/lib/api.ts` — `deleteProject()` von raw `fetch` auf `apiFetch` umstellen, damit Fehler propagieren.
- `frontend/src/app/projects/page.tsx` — Card-Wrapper auf `relative group` umstellen, Trash-Button als Geschwister neben dem `<Link>`, lokalen State `projectToDelete`, `DeleteProjectDialog` einbinden, bei Erfolg Projekt aus `projects`-State entfernen.

### Neue Dateien

- `frontend/src/components/projects/DeleteProjectDialog.tsx` — Bestätigungsmodal nach dem Pattern von `components/export/ExportDialog.tsx` (Card-Overlay über Backdrop). Props: `project: { id; name } | null`, `onClose`, `onDeleted(id)`. Interner State für `deleting` und `deleteError`.

### Backend

Keine Änderungen — `ProjectController.deleteProject` (`api/ProjectController.kt:32-35`) existiert bereits und löscht über `ProjectService.deleteProject(id)` das Projekt-Verzeichnis hart.

## Tests

Frontend hat keinen Test-Runner. Manuelle Browser-Verifikation:

1. Happy Path: Hover → Trash → Modal → Löschen → Karte verschwindet.
2. Abbrechen / ESC / Backdrop-Klick schliesst Modal.
3. Trash-Button löst keine Card-Navigation aus.
4. Backend offline → Löschen → Modal bleibt offen mit Fehler-Banner.
5. Tastatur-Reachability: Trash-Button per Tab erreichbar und sichtbar.
6. Filesystem-Check: `data/projects/{id}/` ist nach Erfolg entfernt.

## Out of Scope

- Soft-Delete / Trash-Konzept.
- Bulk-Delete.
- Undo-Funktion.
- Toast/Snackbar-Infrastruktur.
