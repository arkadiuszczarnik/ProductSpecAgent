# Design — Features-Edit-Modal (Feature 36)

**Status:** Draft
**Datum:** 2026-05-02
**Feature-Doc:** [`docs/features/36-features-edit-modal.md`](../../features/36-features-edit-modal.md)

## Kontext

Im Wizard-Step **FEATURES** rendert `FeaturesGraphEditor.tsx` (215 Zeilen) heute zwei Bereiche nebeneinander: links den Rete-Graph, rechts ein resizable Side-Panel (`FeatureSidePanel.tsx`, 97 Zeilen) zum Bearbeiten des selektierten Features. Der Resize-Hook `useResizable` mit Default 360 px (Range 280–560) hält das Panel zwar schmal, aber präsent. Solange kein Feature ausgewählt ist, erscheint dort nur ein Hilfetext.

Die User-Anforderung: das rechte Panel entfernen, die Felder in einen Modal-Dialog auslagern, damit der Graph die volle Workspace-Breite nutzen kann. Begleitend wird **shadcn/ui** als neue UI-Bibliothek eingeführt (koexistierend mit dem bestehenden `base-ui/react`-Setup).

## Entscheidungen aus Brainstorming

| Frage | Wahl | Konsequenz |
|---|---|---|
| Trigger | **A** Single-Click → Modal öffnet sofort | Kein Doppelklick-Handling, Verhalten konsistent mit heute |
| Save | **B** Explizites Speichern | Lokaler Form-State, Dirty-Tracking, Confirm bei Dirty-Close |
| Layout | **B** 2 Spalten (Stammdaten \| Scope-Felder) | Nutzt Modal-Breite, weniger Scrollen bei vielen Feldern |
| shadcn-Scope | Nur Dialog/Input/Textarea/Label | Kein Konflikt mit bestehendem `Button` (base-ui), keine Migration im Bestand |
| Confirm-Stil | `window.confirm` | Konsistent mit `FeaturesGraphEditor.tsx:163` ("Bestehenden Graph ueberschreiben?") |

## Architektur-Übersicht

```
FeaturesGraphEditor.tsx  (~150 Z.)
 ├── Graph (full-width) + Toolbar (Add / Vorschlagen / Auto-Layout)
 └── <FeatureEditDialog
        feature={selected}
        allowedScopes={allowedScopes}
        open={selectedId !== null}
        onClose={() => setSelectedId(null)}
        onSave={(patch) => updateFeature(id, patch)}
        onDelete={() => removeFeature(id)}
     />
```

Die Komponente kapselt:
- shadcn `Dialog` + `DialogContent` + `DialogHeader` + `DialogFooter`
- 2-Spalten-Body (`grid-cols-1 md:grid-cols-[1fr_1.3fr]`)
- Lokalen `draft`-State (kontrolliert)
- `initialRef` für Dirty-Vergleich
- Confirm-Logic für Close mit Dirty
- Confirm-Logic für Delete

## Komponenten-Schnittstelle

```ts
interface FeatureEditDialogProps {
  feature: WizardFeature | null;        // null = Modal zu
  allowedScopes: FeatureScope[];        // [] = Library/CORE-Mode
  open: boolean;
  onClose: () => void;                  // Aufrufer setzt selectedId = null
  onSave: (patch: Partial<WizardFeature>) => void;
  onDelete: () => void;                 // Aufrufer entfernt Feature
}

type DraftFeature = Pick<WizardFeature,
  "title" | "description" | "scopes" | "scopeFields">;
```

Begründung: minimale Schnittstelle. Die Komponente kennt weder Store noch IDs — der Aufrufer (`FeaturesGraphEditor`) wirklicht ID, Persistenz und Selektions-Reset. Dadurch ist `FeatureEditDialog` isoliert und ohne Store-Mocking testbar (auch wenn aktuell keine Tests existieren).

## State & Lifecycle

```
              feature !== null
              & open === true
       ┌────────────────────────┐
       ▼                        │
  ┌─────────┐  setDraft   ┌─────────┐
  │ snapshot│ ─────────►  │  dirty  │
  │  draft  │             │         │
  └────┬────┘             └────┬────┘
       │ open=false            │ "Speichern"
       │                       ▼
       │                   onSave(draft)
       │                   onClose()
       │                       │
       │ "Abbrechen" / Esc / Overlay
       ▼                       │
   dirty?                      │
   ├─yes→ confirm("Änderungen verwerfen?") ─┐
   └─no ───────────────────────────────────►┴─► onClose()
```

**Snapshot-Strategie:** Beim Übergang `open: false → true` wird `draft = snapshot(feature)` gesetzt und `initialRef.current = snapshot(feature)`. Updates aus dem Store am offenen Modal werden ignoriert — sonst überschreibt ein Auto-Save-Echo gerade getippte Eingaben. Beim erneuten Öffnen wird neu gesnapshotted (frischer Stand aus Store).

```ts
function snapshot(f: WizardFeature): DraftFeature {
  return {
    title: f.title,
    description: f.description,
    scopes: [...f.scopes],
    scopeFields: { ...f.scopeFields },
  };
}

function equalDraft(a: DraftFeature, b: DraftFeature): boolean {
  if (a.title !== b.title || a.description !== b.description) return false;
  if (a.scopes.length !== b.scopes.length) return false;
  for (const s of a.scopes) if (!b.scopes.includes(s)) return false;
  const ak = Object.keys(a.scopeFields), bk = Object.keys(b.scopeFields);
  if (ak.length !== bk.length) return false;
  for (const k of ak) if (a.scopeFields[k] !== b.scopeFields[k]) return false;
  return true;
}
```

Beide Helper bleiben lokal in `FeatureEditDialog.tsx` (ca. 15 Zeilen, kein eigenes Modul).

## UI-Layout (shadcn Dialog)

```tsx
<Dialog open={open} onOpenChange={(o) => !o && handleClose()}>
  <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
    <DialogHeader>
      <DialogTitle>Feature bearbeiten</DialogTitle>
      <DialogDescription>{draft.title || "(ohne Titel)"}</DialogDescription>
    </DialogHeader>

    <div className="grid grid-cols-1 md:grid-cols-[1fr_1.3fr] gap-6 py-4">
      {/* Stammdaten */}
      <div className="space-y-4">
        <div>
          <Label>Titel</Label>
          <Input value={draft.title} onChange={...} />
        </div>
        {allowedScopes.length > 1 && (
          <div>
            <Label>Scope</Label>
            <div className="flex gap-2 mt-1">
              {allowedScopes.map((s) => <ScopeChip ... />)}
            </div>
          </div>
        )}
        <div>
          <Label>Beschreibung</Label>
          <Textarea rows={5} value={draft.description} onChange={...} />
        </div>
      </div>

      {/* Scope-Felder */}
      <div className="space-y-4">
        {scopeSections.map(({ scope, fields }) => (
          <section key={scope}>
            {scopeSections.length > 1 && (
              <h4 className="text-xs font-semibold uppercase text-muted-foreground mb-2">
                {scope === "FRONTEND" ? "Frontend" : "Backend"}
              </h4>
            )}
            {fields.map((key) => (
              <div key={key} className="mb-3">
                <Label>{SCOPE_FIELD_LABELS[key] ?? key}</Label>
                <Textarea rows={2} value={draft.scopeFields[key] ?? ""} onChange={...} />
              </div>
            ))}
          </section>
        ))}
      </div>
    </div>

    <DialogFooter className="flex justify-between sm:justify-between">
      <Button variant="ghost" onClick={handleDelete}>
        <Trash2 size={14} /> Löschen
      </Button>
      <div className="flex gap-2">
        <Button variant="outline" onClick={handleClose}>Abbrechen</Button>
        <Button onClick={handleSave}>Speichern</Button>
      </div>
    </DialogFooter>
  </DialogContent>
</Dialog>
```

**Scope-Sections-Aufbau** (gleiche Logik wie heute in `FeatureSidePanel`, nur in Sections gruppiert):

```ts
const scopeSections = allowedScopes.length === 0
  ? [{ scope: "CORE" as const, fields: SCOPE_FIELDS_BY_SCOPE.CORE }]
  : allowedScopes
      .filter((s) => draft.scopes.includes(s))
      .map((scope) => ({ scope, fields: SCOPE_FIELDS_BY_SCOPE[scope] }));
```

`Button` wird weiter aus `@/components/ui/button` (base-ui-Variante) importiert — kein shadcn-Button.

## Änderungen in `FeaturesGraphEditor.tsx`

**Entfernen:**
- Import `useResizable`, `FeatureSidePanel`
- Aufruf `useResizable({ initialWidth: 360, ... })`
- `<div className="w-1 cursor-col-resize ...">` (Resize-Handle)
- Rechtes Panel-`<div style={{ width: panelWidth }}>...{selected ? <FeatureSidePanel> : <p>}...</div>`

**Ersetzen durch:**
```tsx
<FeatureEditDialog
  feature={selected}
  allowedScopes={allowedScopes}
  open={selectedId !== null}
  onClose={() => setSelectedId(null)}
  onSave={(patch) => selected && updateFeature(selected.id, patch)}
  onDelete={() => {
    if (selected) {
      removeFeature(selected.id);
      setSelectedId(null);
    }
  }}
/>
```

**Hinzufügen:** Import `updateFeature` aus dem Wizard-Store (heute nur in `FeatureSidePanel` verwendet).

**Container-Layout:** `<div className="flex h-[600px] ...">` bleibt; das innere `<div className="flex-1 min-w-0 flex flex-col">` rendert weiterhin Graph + Toolbar — entfällt nur das Geschwister-Panel rechts.

**Rete-Selection-Reset:** Beim `onClose()` zusätzlich versuchen, die Rete-Selektion visuell zu lösen, falls die Editor-API eine Methode dafür anbietet. Andernfalls kosmetisch (Node bleibt visuell als "selected" markiert bis zum nächsten Klick) — hinnehmbar.

## shadcn-Setup

```bash
cd frontend
npx shadcn@latest init
# wenn interaktiv:
#   - Style: New York
#   - Base color: Neutral (passt zu unserem oklch-Theme)
#   - CSS-Variablen: yes
#   - components.json wird in frontend/ angelegt
#   - utils-Pfad: existiert bereits unter @/lib/utils
npx shadcn@latest add dialog input textarea label
```

shadcn 3.x hat seit Mitte 2025 native Tailwind-4-Unterstützung und respektiert vorhandene `@theme`-Definitionen in `globals.css`. Der `init` darf `globals.css` ergänzen, aber die bestehenden Custom-Variablen (Violet/Cyan, Plus Jakarta) müssen erhalten bleiben — vor dem Commit per Diff gegenchecken.

**Neue Dependencies (via shadcn):**
- `@radix-ui/react-dialog`
- `@radix-ui/react-label`

Falls `init` zusätzliche Pakete vorschlägt (`tw-animate-css`, `clsx`/`tailwind-merge`), durchwinken — `clsx`/`tailwind-merge` werden vermutlich schon transitiv genutzt.

## Risiken & Mitigationen

| Risiko | Wahrscheinlichkeit | Mitigation |
|---|---|---|
| `shadcn init` überschreibt `globals.css`-Custom-Theme | mittel | Vor Commit `git diff frontend/src/app/globals.css` prüfen, manuell mergen |
| `tailwind.config` fehlt — shadcn erwartet ggf. eine | niedrig | Tailwind-4 nutzt `@theme` in CSS; shadcn 3.x kann das |
| Rete-Selection bleibt visuell selected nach Close | niedrig | Kosmetisch, hinnehmbar; kein Blocker |
| Auto-Save-Echo überschreibt Modal-Eingabe | mittel | Snapshot-Strategie (oben) |
| `Button` aus base-ui passt visuell nicht zu shadcn-Dialog | niedrig | Beide stilen via `cn()` + Tailwind-Tokens — sollten kohärent aussehen, sonst per Spacing-Klassen anpassen |

## Verifikation (manuell, im Browser)

1. **Klick öffnet** — Single-Click auf Node → Modal öffnet, Felder gefüllt.
2. **Speichern persistiert** — Felder ändern → "Speichern" → Modal zu, Node-Titel im Graph aktualisiert.
3. **Dirty-Confirm** — Felder ändern → "Abbrechen" → Confirm; Esc und Overlay-Klick verhalten sich identisch.
4. **Clean-Close** — ohne Änderung → "Abbrechen"/Esc/Overlay schliessen direkt ohne Confirm.
5. **Scope-Toggle** — Scope-Chip toggeln → rechte Spalte aktualisiert lokal; "Abbrechen" rollt zurück.
6. **Löschen** — "Löschen" → Confirm → Node verschwindet aus Graph, Modal zu, `selectedId === null`.
7. **Auto-Save-Echo** — Modal offen lassen, parallel ein anderes Feature ändern (oder Save triggern lassen) → Eingaben im offenen Modal bleiben unberührt.
8. **Library-Mode** — Projekt mit Category=Library → Modal zeigt CORE-Felder, kein Scope-Chip-Block.
9. **Single-Scope** — Projekt mit Category=Mobile App (nur FRONTEND) → rechte Spalte zeigt nur FRONTEND-Felder ohne Scope-Header.
10. **Voller Graph** — kein selektiertes Feature → Graph nutzt 100% Workspace-Breite, keine vertikale Trennlinie mehr.
11. **Mobile** — Browser auf < 768 px verkleinern → `<FeaturesFallbackList>` greift wie bisher.

## Nicht im Scope (YAGNI)

- Migration der bestehenden base-ui-Komponenten auf shadcn.
- Tastatur-Shortcuts (Enter zum Öffnen, n für neues Feature, Delete für selektierten Node).
- Inline-Edit am Node.
- Mehrfach-Selektion / Bulk-Edit.
- Undo/Redo.
- Eigenes shadcn-`AlertDialog` für Confirms (`window.confirm` reicht).
- Tooltip mit "Klicke zum Bearbeiten" — selbsterklärend.
- Animationen über shadcn-Defaults hinaus.
