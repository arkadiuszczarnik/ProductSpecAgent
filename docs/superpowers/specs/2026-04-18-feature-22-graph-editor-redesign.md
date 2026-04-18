---
name: Feature 22 — Graph-Editor Redesign (nach Rollback)
description: Frontend-Redesign-Entscheidungen für den zweiten Anlauf des Features-Graph-Wizards, nach Code-Rollback von d37596d
date: 2026-04-18
status: Approved — ready for writing-plans
---

# Feature 22 — Graph-Editor Redesign

**Datum:** 2026-04-18
**Status:** Approved — ready for `writing-plans`

## Referenzen

- Feature-Spec: [`docs/features/22-features-graph-wizard.md`](../../features/22-features-graph-wizard.md)
- Implementation-Notes (Addendum): [`2026-04-17-feature-22-implementation-notes.md`](2026-04-17-feature-22-implementation-notes.md)
- Bestehender Plan (wird teilweise wiederverwendet): [`docs/superpowers/plans/2026-04-17-feature-22-features-graph-wizard.md`](../plans/2026-04-17-feature-22-features-graph-wizard.md)
- Rollback-Commit: `d37596d` (code-only, Range `645463d^..f920a4b`, Safety-Branch `backup/pre-rollback-feature22`)

## Kontext

Der erste Implementierungsversuch von Feature 22 (Commits `8d64862` bis `e1a7534`) wurde am 2026-04-18 per `git revert` zurückgerollt. Grund: Die Frontend-Integration von Rete.js 2 in den Wizard-Step verursachte einen nicht fangbaren React-19-Strict-Mode-Crash, der sich als browser-seitiger Hang zeigte, sobald der FEATURES-Step geöffnet wurde. Vier kaskadierende Bugs wurden identifiziert (siehe Root-Cause unten).

Die Feature-Spec, der Plan und die Implementation-Notes bleiben gültig. **Dieses Dokument beschreibt ausschließlich die geänderten Frontend-Integration-Entscheidungen für den zweiten Anlauf.** Alle anderen Inhalte (Datenmodell, DAG-Semantik, Agent-Verhalten, Scope-Gating, Backend-Tasks) sind unverändert.

## Root-Cause-Analyse des gescheiterten ersten Versuchs

| # | Symptom | Ursache |
|---|---------|---------|
| 1 | Infinite Re-Render-Loop | `area.addPipe` hörte auf `nodetranslated`, das auch bei programmatischem `node.translate()` feuert → Update → Re-Render → Translate → Event → Loop |
| 2 | „cannot find connection" | Zwei parallele `applyGraph`-Läufe traten in `editor.removeConnection` rein — der zweite sah eine Connection, die der erste gerade entfernte |
| 3 | React-Crash „Cannot update component while rendering" + „Attempted to synchronously unmount a root while React was already rendering" | Full-Rebuild in `applyGraph` rief `editor.removeNode()` — Rete's ReactPlugin ruft darin synchron `root.unmount()`, das während React-19-Strict-Mode-Render fatal ist |
| 4 | Legacy-Features bekamen bei jedem Render neue UUIDs | `normalizeFeature` lief bei jedem Read im Frontend-Store und generierte `crypto.randomUUID()` für Einträge ohne `id` — Rete sah Remove+Add für jede Node |

Die Summe dieser Bugs führte zu einem Browser-Hang, der auch nach Einzelfixes nicht stabil weggefangen werden konnte. Die Redesign-Entscheidungen unten adressieren die Ursachen, nicht die Symptome.

## Design-Entscheidungen

### 1. Store-Topologie: wizard-store mit atomic selectors

Der Graph-State (`features`, `edges`) lebt weiterhin im bestehenden `frontend/src/lib/stores/wizard-store.ts` unter `data.steps.FEATURES.fields.features` / `.edges`. Kein separater `graph-store`.

**Regel:** Jede Komponente, die Graph-State liest, nutzt einen atomaren Selector mit `useShallow`, keine globalen Store-Subscribes.

```ts
// richtig:
const features = useWizardStore(useShallow((s) =>
  (s.data?.steps.FEATURES?.fields.features ?? []) as WizardFeature[]
));

// falsch (Re-Render-Storm):
const { data } = useWizardStore();
const features = data?.steps.FEATURES?.fields.features;
```

**Warum:** Die vier Graph-Konsumenten (Canvas, SidePanel, FallbackList, BlockerBanner) re-rendern nur, wenn ihre konkrete Teil-Sicht sich ändert. Kein `data`-Objekt-Identity-Kaskaden-Rerender.

### 2. Auto-Save-Strategie: 500-ms-Debounce für alle Graph-Mutationen

Graph-Aktionen (`addFeature`, `removeFeature`, `moveFeature`, `addEdge`, `removeEdge`, `updateFeature`) gehen durch den bestehenden `updateField("FEATURES", "features" | "edges", …)`-Pfad → 500-ms-Debounce → PUT zum Backend.

**Kein eigenes Debounce-System für Position.** `nodedragged` feuert nur am Drag-Ende (siehe Entscheidung 3), ist also bereits ein Discrete-Trigger. Zwei kurz hintereinander ausgelöste Drags werden durch den Debounce zusammengefasst — last-wins, das ist gewollt.

### 3. Diff-Berechnung: im Editor-Closure, nur `nodedragged`

Der Rete-Editor-State wird **niemals** full-rebuilt. Stattdessen hält die Factory-Funktion in `editor.ts` eine `Map<featureId, RenderedState>` im Closure und berechnet bei jedem `applyGraph(features, edges)`-Aufruf den Diff:

- Feature in Input, nicht in Map → `editor.addNode`
- Feature in Map, nicht in Input → `editor.removeNode`
- Feature in beiden, `title`/`scopes` unterschiedlich → `node.label = …; area.update("node", id)`
- Feature in beiden, Position unterschiedlich → `area.translate(id, {x, y})` (kein Event, weil programmatisch)
- Edge in Input, nicht in Map → `editor.addConnection`
- Edge in Map, nicht in Input → `editor.removeConnection`

**Ereignis-Trennung:** Der `area.addPipe`-Listener hört **ausschließlich** auf `nodedragged` (User-Drag-Ende), nicht auf `nodetranslated`. `node.translate()` aus `applyGraph` löst kein `nodedragged` aus → kein Feedback-Loop → kein `isApplying`-Flag nötig.

**Coalescing:** `applyGraph` bleibt idempotent; mehrfache parallele Aufrufe werden per `pendingArgs`-Queue serialisiert (ein laufender + ein wartender — Rest überschreibt den wartenden).

### 4. Backward-Compat: Backend-only via `parseWizardFeatures`

Legacy-Features ohne `id`/`scopes` werden **ausschließlich im Backend** in `parseWizardFeatures` konvertiert (UUIDs generiert, `scopes = []`). Beim ersten Read vergibt der Backend die IDs, schreibt sie via `saveWizardStep` zurück — danach sind die Daten stabil.

**Frontend macht keine Normalisierung.** Keine `normalizeFeature`-Funktion im Store, kein `crypto.randomUUID()` in Selectors, kein persist-normalize-Helper.

**Akzeptanzkriterium:** Ein Read → Write → Read auf einem Legacy-Projekt liefert IDs, die sich nicht ändern (Test: `ParseWizardFeaturesTest`).

### 5. Task-Breakdown: bestehenden Plan wiederverwenden

Der bestehende 19-Task-Plan (`docs/superpowers/plans/2026-04-17-feature-22-features-graph-wizard.md`) bleibt die Basis. Alle Backend-Tasks werden inhaltlich unverändert neu ausgeführt (wurden im Revert mit zurückgerollt). **Nur die Frontend-Editor-Tasks werden überarbeitet**, um die Entscheidungen 1–4 umzusetzen. Betroffene Tasks (geschätzt):

- Task zu `FeaturesGraphEditor.tsx` — neu mit atomic selectors + incremental applyGraph
- Task zu `editor.ts` — Diff im Closure, nur `nodedragged`, coalescing queue
- Task zu `FeatureSidePanel.tsx` — atomic selector für das ausgewählte Feature
- Task zu `FeaturesFallbackList.tsx` — atomic selector statt `data`-Subscribe
- Task zu wizard-store-Integration — **kein** persistNormalizeFeatures, Store-Aktionen (`addFeature`, `moveFeature`, …) über bestehenden `updateField`-Debounce

Neuer Task: Playwright-Smoke-Test (siehe Entscheidung 6).

### 6. Visual Smoke-Test als eigener Task

Das Implementation-Notes-Dokument hält fest, dass Rete-Komponenten keine Unit-Tests bekommen. Das bleibt. **Zusätzlich** wird ein Playwright-E2E-Smoke-Test als neuer Task aufgenommen:

```
1. Projekt mit Kategorie „SaaS" anlegen, IDEA–MVP-Steps überspringen (Fixture)
2. FEATURES-Step öffnen → Canvas sichtbar, keine Console-Errors
3. „+ Feature"-Button klicken → eine Node erscheint, Browser bleibt responsiv
4. Node im SidePanel umbenennen → Label im Canvas aktualisiert sich
5. Zweites Feature hinzufügen, Kante zwischen beide ziehen → Connection wird gerendert
6. Page reload → beide Features + Kante korrekt wiederhergestellt, IDs identisch
7. Kategorie „Library" setzen (falls UI das erlaubt) → Step wird ausgeblendet
```

**Akzeptanzkriterium:** Smoke-Test läuft in CI grün, keine React-Error-Overlay-Frames, Explorer-Tree bleibt klickbar während aller Schritte.

## Unverändert gegenüber Feature-Spec + Implementation-Notes

Damit nichts verloren geht, hier die Entscheidungen aus den bestehenden Docs, die weiterhin gelten:

- `rete-auto-arrange-plugin` statt `@dagrejs/dagre` (Notes, Entscheidung 1)
- `useResizable` statt shadcn `ResizablePanelGroup` (Notes, Entscheidung 2)
- `PlanGeneratorAgent` vergibt `epicEstimate` aus `{XS, S, M, L, XL}`, `WizardFeature` hat kein `estimate` (Notes, Entscheidung 3)
- Cycle-Prevention via `editor.addPipe` auf `connectioncreate` mit DFS in `lib/graph/cycleCheck.ts` (Notes)
- Scope-Gating: SaaS/Mobile/Desktop → beide Scopes, API/CLI → nur Backend, Library → kein Scope-Picker (Feature-Spec)
- Mobile-Fallback unter 768 px → Listen-Ansicht (Feature-Spec)

## Nächste Schritte

1. **`writing-plans`-Skill invoken** → bestehenden Plan überarbeiten, Frontend-Editor-Tasks mit Entscheidungen 1–4 neu schreiben, Smoke-Test-Task ergänzen, Tasknummern konsistent halten
2. **User-Review des überarbeiteten Plans**
3. **`subagent-driven-development`-Skill** → Implementation
4. **Smoke-Test lokal grün ziehen**
5. **`finishing-a-development-branch`-Skill** → `docs/features/22-features-graph-wizard-done.md` + PR
