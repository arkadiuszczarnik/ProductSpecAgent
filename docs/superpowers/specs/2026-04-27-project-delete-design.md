# Design: Projekt löschen auf der Übersichtsseite

## Zusammenfassung

Auf der Projekt-Übersichtsseite (`/projects`) soll der Nutzer einzelne Projekte löschen können. Backend-Endpoint und API-Client-Funktion existieren bereits — das Feature ist im Kern reine Frontend-Arbeit: Trigger auf der Karte, Bestätigungsmodal, Liste lokal aktualisieren. Eine kleine Aufräumarbeit am API-Client gehört dazu, damit Fehler überhaupt durchpropagieren.

## User Stories

1. Als PO möchte ich ein Projekt aus meiner Übersicht entfernen können, das ich versehentlich angelegt habe oder nicht mehr brauche.
2. Als PO möchte ich vor dem Löschen explizit bestätigen, weil die Aktion alle Spec-Dateien, Decisions, Clarifications, Tasks, Documents und Docs-Scaffold im Filesystem entfernt und nicht rückgängig gemacht werden kann.
3. Als PO möchte ich bei einem Server-Fehler im Modal direkt sehen, was schiefgegangen ist, und es erneut versuchen oder abbrechen können — ohne den Modal-Kontext zu verlieren.

## Acceptance Criteria

- [ ] Auf jeder Projekt-Karte erscheint bei Hover oder Tastatur-Fokus ein Mülleimer-Icon-Button oben rechts.
- [ ] Klick auf den Mülleimer-Button öffnet ein Bestätigungsmodal — die Card-Navigation wird **nicht** ausgelöst.
- [ ] Modal zeigt den Projektnamen prominent und einen Warntext, dass alle Projekt-Daten im Filesystem entfernt werden.
- [ ] Klick auf „Löschen" ruft `DELETE /api/v1/projects/{id}` auf; bei `2xx` verschwindet das Projekt aus der Liste **ohne** Re-Fetch der gesamten Übersicht.
- [ ] Während des Löschens sind beide Footer-Buttons disabled, ESC und Backdrop-Klick werden ignoriert.
- [ ] Bei Server-Fehler bleibt das Modal offen und zeigt eine Inline-Fehlermeldung; der „Löschen"-Button ist wieder klickbar.
- [ ] Klick auf „Abbrechen", ESC oder Backdrop-Klick schliesst das Modal ohne Aktion (sofern nicht gerade gelöscht wird).
- [ ] `aria-label` auf dem Trash-Button enthält den Projektnamen (Screenreader-Kontext).
- [ ] Manuell verifiziert: Hover-Reveal funktioniert, Modal-Flow für Erfolg und Fehler durchgespielt.

## Technische Details

### Geänderte Dateien

- `frontend/src/lib/api.ts` — `deleteProject()` von raw `fetch` auf `apiFetch` umstellen, damit Fehler als Exception propagieren und einheitlich behandelt werden.
- `frontend/src/app/projects/page.tsx` — Card-Wrapper auf `<div class="relative group">` umstellen, Trash-Button als Geschwister neben dem `<Link>` einfügen, lokalen State (`projectToDelete`) für das Modal verwalten, `DeleteProjectDialog` einbinden, bei Erfolg Projekt aus `projects`-State entfernen.

### Neue Dateien

- `frontend/src/components/projects/DeleteProjectDialog.tsx` — Bestätigungsmodal nach dem Pattern von `components/export/ExportDialog.tsx` (Card als Overlay über Backdrop, kein base-ui Primitive). Props: `project: { id; name } | null`, `onClose`, `onDeleted(id)`. Eigener interner State für `deleting` und `deleteError`.

### UI-Struktur (Card-Wrapper)

```tsx
<div key={p.id} className="relative group">
  <Link href={`/projects/${p.id}`} className="block">
    <Card>…unverändert…</Card>
  </Link>
  <button
    type="button"
    onClick={() => setProjectToDelete(p)}
    aria-label={`Projekt "${p.name}" löschen`}
    className="absolute top-2 right-2 opacity-0 group-hover:opacity-100
               focus-visible:opacity-100 transition-opacity
               rounded-md p-1.5 bg-background/80 backdrop-blur-sm
               text-muted-foreground hover:text-destructive
               hover:bg-destructive/10"
  >
    <Trash2 size={14} />
  </button>
</div>
```

Begründung: Geschwister-Anordnung statt Verschachtelung verhindert nested interactive elements (a11y) und Event-Bubbling auf den Link. `focus-visible:opacity-100` macht den Button für Tastatur-Nutzer erreichbar.

### Modal-Inhalt

- **Titel:** „Projekt löschen?"
- **Body:** „Möchtest du **„{projectName}"** wirklich löschen? Diese Aktion entfernt Spec, Decisions, Clarifications, Tasks, Documents und Docs-Scaffold — sie kann nicht rückgängig gemacht werden."
- **Fehler-Slot (bedingt):** rotes Inline-Banner mit `border-destructive/30 bg-destructive/10` über dem Footer (analog zum bestehenden `error`-Banner in `projects/page.tsx`).
- **Footer:** `Abbrechen` (variant=`ghost`), `Löschen` (variant=`destructive`, mit `Loader2`-Spinner während `deleting`).
- Initialer Fokus auf „Abbrechen" (sicherer Default für destructive Action).

### State-Modell (in `projects/page.tsx`)

```ts
const [projectToDelete, setProjectToDelete] = useState<ProjectWithStats | null>(null);
```

Modal-`open`-Zustand wird aus `projectToDelete !== null` abgeleitet — eine Quelle der Wahrheit.

`deleting` und `deleteError` leben **innerhalb** des Dialogs (lokaler State der Modal-Komponente), nicht in der Page — der Page interessiert nur das Resultat (`onDeleted(id)`).

### Erfolgsfluss

```
Klick Trash      → setProjectToDelete(p)
Klick „Löschen"  → setDeleting(true), setDeleteError(null)
                 → await deleteProject(p.id)
   ✓ Erfolg     → onDeleted(p.id) → parent: setProjects(prev => prev.filter(x => x.id !== id))
                 → onClose()
   ✗ Fehler     → setDeleteError(e.message), Modal bleibt offen
```

### Was nicht geändert wird

- Backend (`DELETE /api/v1/projects/{id}` existiert in `ProjectController.kt:32-35`).
- `Card`/`Button`-Primitives — `destructive`-Variant existiert bereits in `button.tsx:17-18`.
- Andere Komponenten — strict scoped auf Übersichtsseite und neuen Dialog.
- Toast/Snackbar-System — wird nicht eingeführt; Fehler-UX läuft über das bestehende Inline-Banner-Pattern im Modal.

## Tests

Frontend hat keinen Test-Runner (siehe `frontend/CLAUDE.md`). Verifikation manuell im Browser:

1. **Happy Path:** Hover über Karte → Trash-Icon erscheint → Klick → Modal öffnet → „Löschen" → Modal schliesst, Karte verschwindet, andere Karten bleiben.
2. **Abbrechen:** Modal öffnen → „Abbrechen" → Modal schliesst, Liste unverändert.
3. **Backdrop-Klick:** Modal öffnen → Klick auf Hintergrund → Modal schliesst.
4. **ESC:** Modal öffnen → ESC → Modal schliesst.
5. **Keyboard-Reachability:** Tab-Reihenfolge führt durch Card-Link und Trash-Button; beim Fokus wird der Trash-Button sichtbar.
6. **Fehler-Pfad:** Backend stoppen → Löschen versuchen → Modal bleibt offen, rotes Fehler-Banner erscheint, „Löschen"-Button wieder klickbar.
7. **Card-Navigation isoliert:** Klick auf Trash-Button löst keine Navigation in den Workspace aus.
8. **Backend-Effekt:** Nach erfolgreichem Löschen ist `data/projects/{id}/` im Dateisystem entfernt.

## Out of Scope

- Soft-Delete oder Trash-Konzept — Backend macht hard delete; entspricht der bestehenden Datenphilosophie ohne DB.
- Bulk-Delete mehrerer Projekte.
- Undo-Funktion nach erfolgreichem Löschen.
- Toast/Snackbar-Infrastruktur (separates, grösseres Refactoring).
- Tippe-Projektnamen-zur-Bestätigung (Overkill für internes Tool).
