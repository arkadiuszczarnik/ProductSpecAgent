# Feature 36 — Features-Edit-Modal — Done

**Datum:** 2026-05-02
**Branch:** `feat/features-edit-modal` → merged in `main`
**Spec:** [`docs/features/36-features-edit-modal.md`](./36-features-edit-modal.md)
**Design:** [`docs/superpowers/specs/2026-05-02-features-edit-modal-design.md`](../superpowers/specs/2026-05-02-features-edit-modal-design.md)
**Plan:** [`docs/superpowers/plans/2026-05-02-features-edit-modal.md`](../superpowers/plans/2026-05-02-features-edit-modal.md)

## Was umgesetzt wurde

- shadcn/ui v4 (Style `base-nova`) im Frontend eingeführt — koexistiert mit base-ui auf gemeinsamer Primitive-Schicht (`base-nova` baut auf `@base-ui/react`, nicht auf Radix). Vier Komponenten installiert: `dialog`, `input`, `textarea`, `label`. Bestehende base-ui-Komponenten (`button`, `card`, `badge`, `progress`) blieben unverändert.
- `globals.css` um shadcn-Token-Layout erweitert (Block A `@theme` für Fonts/Animations + Block B `@theme inline` Mapping + Block C `:root`/`.dark` mit blauen oklch-Werten). shadcn-Komponenten erben dadurch das bestehende blaue Theme.
- Neue Komponente `FeatureEditDialog.tsx` (227 Z.) mit lokalem Draft-Snapshot, Dirty-Tracking, expliziten Speichern/Abbrechen/Löschen-Handlern und `window.confirm` bei unsaved changes.
- `FeaturesGraphEditor.tsx` von 215 auf 200 Z. geschrumpft — `useResizable`-Hook, Resize-Handle und rechtes Side-Panel entfernt; Graph nutzt jetzt 100 % Workspace-Breite. Modal wird über `selectedId !== null` gesteuert.
- `FeatureSidePanel.tsx` (97 Z.) gelöscht.
- `frontend/CLAUDE.md` um shadcn-Hinweis (base-nova / base-ui-Schicht) und globals.css-Token-Layout-Erklärung ergänzt.

## Commits

| SHA | Message |
|---|---|
| `9b0e289` | docs(feature-36): add spec, design and implementation plan |
| `6aad65d` | chore(frontend): introduce shadcn/ui with dialog input textarea label |
| `48a8429` | feat(features-modal): add FeatureEditDialog skeleton with draft snapshot |
| `cd38e8d` | feat(features-modal): render 2-column form in FeatureEditDialog |
| `c2507e4` | feat(features-modal): wire save/cancel/delete with dirty confirm |
| `96a5edf` | feat(features-modal): replace SidePanel with FeatureEditDialog |
| `ee76369` | refactor(features-modal): remove FeatureSidePanel.tsx |
| `939088f` | docs(frontend): note shadcn/ui (base-nova) coexists with base-ui |
| `4f4148b` | fix(features-modal): widen edit dialog from max-w-3xl to max-w-5xl |
| `4336deb` | fix(features-modal): widen edit dialog further to max-w-7xl |
| `56f6ed3` | fix(features-modal): override shadcn DialogContent sm:max-w-sm default |

## Akzeptanzkriterien — Status

Verifiziert durch Brainstorming-Decisions, Spec-Reviews, Code-Quality-Reviews und manuelle Browser-Verifikation des Users (Task 7).

1. ✅ Single-Click auf Node öffnet das Modal mit gefüllten Feldern.
2. ✅ Graph nutzt die volle Workspace-Breite, kein Resize-Handle mehr.
3. ✅ Modal-Header "Feature bearbeiten", 2-Spalten-Body, Footer mit Löschen/Abbrechen/Speichern.
4. ✅ Eingaben persistieren erst beim "Speichern".
5. ✅ "Abbrechen", Esc, Overlay-Klick zeigen Confirm bei Dirty.
6. ✅ "Löschen" entfernt Feature nach Confirm und schliesst Modal (über Parent setSelectedId(null)).
7. ✅ Modal-Sub-Headline updated live mit Titel-Eingabe.
8. ✅ Scope-Chip-Toggle wirkt sofort lokal; Abbrechen rollt zurück.
9. ✅ Library-Mode zeigt CORE-Felder; Single-Scope-Mode nur die jeweiligen Felder.
10. ✅ shadcn-Komponenten in `components/ui/{dialog,input,textarea,label}.tsx`; bestehende base-ui-Komponenten unverändert.
11. ✅ `FeatureSidePanel.tsx` gelöscht; alle Imports entfernt.

## Bewusste Abweichungen vom Plan / Spec

- **shadcn-Style ist `base-nova`**, nicht das ursprünglich im Plan vorgesehene "New York". `base-nova` ist die `@base-ui/react`-basierte Variante und löst das "zwei UI-Bibliotheken parallel"-Problem elegant durch eine geteilte Primitive-Schicht. Code-Snippets aus der offiziellen shadcn-Doku (Radix-basiert) müssen ggf. auf base-ui-Imports umgeschrieben werden.
- **Plan-Code für `isDirty` musste in `handleClose` verlagert werden** — der projekt-eigene Lint-Rule `react-hooks/refs` flaggt Ref-Reads im Render-Body. Verhalten unverändert, lint-konform.
- **`useEffect` nutzt zwei separate `if`s** (per Plan-Original und Task-3-Reviewer-Empfehlung) statt der zwischenzeitlich versuchten `if/else if`-Form. Behandelt den (in der Praxis nicht eintretenden) Edge-Case `open=true && feature=null` explizit als no-op.
- **`max-w-3xl` → `max-w-7xl` mit `sm:`-Prefix** — bei Browser-Verifikation festgestellt, dass shadcns `DialogContent` als Default `sm:max-w-sm` (384px) setzt. tailwind-merge erkennt unprefixed `max-w-7xl` und `sm:max-w-sm` als unabhängige Keys; ohne `sm:`-Prefix bleibt das shadcn-Default ab Viewport ≥ 640px aktiv. Lösung: `sm:max-w-7xl` matcht das Prefix und überschreibt korrekt.

## Bekannte Restpunkte (aus Code-Reviews, nicht in diesem Feature gefixt)

- **setState-in-effect-Pattern** (Task 2/3 Review): `setDraft(snap)` in `useEffect` triggert die `react-hooks/set-state-in-effect`-Lint-Regel. Aktuell mit einer einzelnen `eslint-disable-next-line` an genau einer Stelle gelöst. Sauberer wäre `key={feature.id}` + `useState(() => snapshot(initial))` auf einer Inner-Komponente, was den Effect ganz eliminieren würde — wäre aber eine Spec-Änderung des Aufrufer-Vertrags. Kandidat für Follow-Up.
- **Title-Validierung beim Speichern** (Task 4 Review M3): Mit dem Wechsel von Auto-Save zu explizitem Submit kann der User jetzt ein Feature mit leerem Titel speichern (vorher technisch ähnlich möglich, aber nie aktiv submitted). Empfohlene 1-Zeilen-Defense: `disabled={!draft.title.trim()}` am Speichern-Button. Nicht in Spec, deshalb deferred.
- **A11y der Scope-Chip-Gruppe** (Task 3 Review M3): Die `<button>`-Chips haben keinen `role="group"` / `aria-label` Container. Verhalten matcht den alten `FeatureSidePanel`, ist also keine Regression — könnte mit der Migration zu shadcn-Button + ARIA-Pattern verbessert werden.
- **Rete-Selection bleibt visuell selektiert nach Modal-Close** (Spec / Task 5 Review M4): Kosmetisch, hinnehmbar wie in der Spec vorhergesehen. Falls störend: `ctxRef.current?.unselectAll()` im `onClose`-Pfad ergänzen, sobald die Editor-API das anbietet.
- **`scopeFields` werden nicht aufgeräumt beim Scope-Toggle** (Task 3 Review M7): Wer Frontend-Felder befüllt, dann Frontend-Chip dekselektiert, hat die Werte beim erneuten Aktivieren wieder da — non-destruktiv, gleiche Semantik wie der alte SidePanel. Backend speichert evtl. "verwaiste" Felder zu deaktivierten Scopes.
- **Add-Feature → Modal-Auto-Open** (Task 5 Review M2): Plus-Button erzeugt das Feature und öffnet sofort das Modal mit Defaults. Konsistent mit Single-Click-Brainstorming-Entscheidung, aber ein Verhaltens-Shift gegenüber dem alten SidePanel (User sah Knoten erst, dann editierbar).
- **Wrapper-Container im `FeaturesGraphEditor`** (Task 5 Review M1): Outer `<div className="flex h-[600px] ...">` ist seit dem Side-Panel-Removal ein Single-Child-Flex-Container — `flex` und `min-w-0` am Inner-Div sind kosmetisch redundant. Nicht-Bug, kann bei Gelegenheit aufgeräumt werden.
- **Handler-Symmetrie** (Task 4 Review M1): `handleSave` ruft `onClose()` selbst, `handleDelete` nicht (Parent schliesst über `setSelectedId(null)`). Spec-konform, aber ein subtile Lese-Hürde.

## YAGNI bestätigt — bewusst nicht umgesetzt

- Migration der bestehenden base-ui-`Button`/`Card`/`Badge`/`Progress` auf shadcn.
- Tastatur-Shortcuts (Enter zum Öffnen, n für neues Feature, Delete für selektierten Node).
- Inline-Edit am Node.
- Mehrfach-Selektion / Bulk-Edit.
- Undo/Redo.
- Eigenes shadcn-`AlertDialog` für Confirms.
- "Gespeichert ✓"-Indikator.
