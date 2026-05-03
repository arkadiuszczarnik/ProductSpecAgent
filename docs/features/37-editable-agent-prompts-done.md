# Feature 37 — Editable Agent Prompts — Done

**Datum:** 2026-05-02
**Branch:** `feat/editable-agent-prompts` → `main` (offen)
**Spec:** `docs/features/37-editable-agent-prompts.md`
**Design:** `docs/superpowers/specs/2026-05-02-editable-agent-prompts-design.md`
**Plan:** `docs/superpowers/plans/2026-05-02-editable-agent-prompts.md`

## Was umgesetzt wurde

**Backend**

- `PromptRegistry` mit 6 Definitionen (`idea-base`, `idea-marker-reminder`, `idea-step-IDEA`, `decision-system`, `plan-system`, `feature-proposal-system`) inkl. sealed `PromptValidator` (`NotBlank`, `MaxLength`, `RequiresAll`).
- `PromptService` mit S3-First / Resource-Fallback und `ConcurrentHashMap`-Cache + expliziter Invalidation bei Save/Reset.
- `PromptController` (REST `/api/v1/prompts`) — `GET` (Liste + Detail), `PUT` (validiert + cached + persistiert), `DELETE` (Reset).
- 6 deutsche Default-Prompts unter `backend/src/main/resources/prompts/`. Marker-Token (`[STEP_COMPLETE]`, `[DECISION_NEEDED]`, `[CLARIFICATION_NEEDED]`) bleiben unverändert auf Englisch (Parser-Kompatibilität).
- 4 Agents (`IdeaToSpecAgent`, `DecisionAgent`, `PlanGeneratorAgent`, `FeatureProposalAgent`) lesen ihre Prompts ausschließlich über `PromptService.get(id)`. `application.yml` `agent.system-prompt`-Block entfernt.
- Tests: 59 Test-Suiten, 362+ Tests grün, inkl. neuer Coverage für `PromptValidator`, `PromptService` (Cache-Verhalten + S3-Override), `PromptController` (MockMvc).

**Frontend**

- Route `/prompts` mit Split-Layout (Liste links, Detail rechts).
- `PromptList` gruppiert Einträge nach Agent, zeigt Override-Badge (`●`) bei `isOverridden`.
- `PromptDetail` mit CodeMirror 6 + `@codemirror/lang-markdown` + `basicDark`-Theme. Save mit Server-Validation-Echo (400 → roter Banner mit `errors[]`-Liste). Reset mit `window.confirm`. Dirty-Tracking + Confirm beim Listen-Wechsel.
- `apiFetch` umgestellt auf `ApiError`-Klasse (status + body) sowie 204-No-Content-Handling — backward-kompatibel zu allen bestehenden Call-Sites.
- Neuer Rail-Eintrag in `AppShell` (`MessageSquareText`-Icon nach `/asset-bundles`). `/prompts` als full-bleed-Route registriert, damit der Editor innerhalb der Page scrollt.

## Bewusste Abweichungen / Restpunkte

- **User-Prompt-Templates** (`DecisionAgent.buildString`, `PlanGeneratorAgent.buildString`) bleiben Code — sie mischen Anweisungen mit dynamischer Kontext-Injektion. Out-of-Scope für V1.
- **Multi-Replica-Cache-Invalidation**: bei Skalierung auf >1 Backend-Instanz müsste ein Pub/Sub-Invalidate ergänzt werden.
- **Keine Versionierung / Edit-History** — nur "Reset auf Default" als Rollback.

## Plan-Abweichungen während Implementation

1. **Tests-Cleanup für Singleton-Cache (Task 4)**: Plan-`@AfterEach` mit nur `objectStore.deletePrefix("prompts/")` reichte nicht — `PromptService` ist Spring-Singleton mit `ConcurrentHashMap`-Cache, der zwischen Tests überlebte. Fix: zusätzlich `promptRegistry.definitions.forEach { promptService.reset(it.id) }` im Cleanup.
2. **Mehr `IdeaToSpecAgent`-Verwendungen als der Plan annahm (Task 5)**: 4 Test-Files (nicht 2): `IdeaToSpecAgentTest`, `ChatControllerTest`, `WizardChatControllerTest`. `MARKER_REMINDER` an 5 Stellen verwendet (nicht 4).
3. **`DecisionAgent`/`PlanGeneratorAgent`/`FeatureProposalAgent`-Refactor traf 11 Test-Files (Task 6)**: 4 Agent-Tests + 4 Controller-Tests mit `@TestConfiguration`-Beans + 3 Service/Builder-Tests. Pattern überall: `PromptService(PromptRegistry(), InMemoryObjectStore())` injizieren.
4. **`InMemoryObjectStore` existiert bereits** im `storage`-Package — wiederverwendet, keine eigene Test-Fake nötig.
5. **Kein MockK im Projekt**: Plan schlug MockK-Pattern für `PromptServiceTest` vor; das Projekt nutzt durchweg echte Services + hand-rolled Test-Doubles. `PromptServiceTest` hat eine private `InMemoryObjectStore`-Klasse mit Call-Counter — passt zum Projekt-Stil.
6. **Akzeptanzkriterium #11 (Dirty-Confirm)** war im Plan-Self-Review als Lücke markiert ("fehlt im Plan, ergänzen in Task 10"); wurde in einem separaten Commit nach Task 10 nachgereicht: `PromptDetail.onDirtyChange` + `page.tsx`-Handler mit `window.confirm("Änderungen verwerfen?")`.
7. **`AppShell` minimal erweitert**: `/prompts` als zusätzliche full-bleed-Route registriert (`overflow-hidden` auf `<main>`), damit der CodeMirror-Editor innerhalb der Page scrollt statt die ganze Seite. Plan hatte das nicht vorgesehen, ergab sich aus dem Page-Layout.

## Akzeptanzkriterien-Status

| # | Kriterium | Status | Verifikation |
|---|---|---|---|
| 1 | `GET /api/v1/prompts` liefert Liste mit `agent`/`title`/`description`/`isOverridden` | ✓ | `PromptController` + `PromptControllerTest` |
| 2 | `GET /api/v1/prompts/{id}` ohne S3-Override liefert Default + `isOverridden=false` | ✓ | `PromptServiceTest` (Resource-Fallback) |
| 3 | `PUT /api/v1/prompts/{id}` schreibt nach S3, aktualisiert Cache, Agent nutzt neuen Content | ✓ | `PromptServiceTest` (Cache-Update); Browser-Test Step 7 |
| 4 | `PUT` mit ungültigem Content → 400 mit `errors[]`, S3 + Cache unverändert | ✓ | `PromptControllerTest` (NotBlank, RequiresAll, MaxLength); Browser-Test Step 4+5 |
| 5 | `DELETE /api/v1/prompts/{id}` evictet Cache + S3, `GET` liefert Default | ✓ | `PromptControllerTest`; Browser-Test Step 6 |
| 6 | 4 Agents lesen Prompts ausschließlich via `PromptService`; `application.yml` ohne `agent.system-prompt` | ✓ | Commits `66e5178`, `bf99775`, `229713a` + grep-Check |
| 7 | Defaults DE-übersetzt, Marker-Token unverändert EN | ✓ | `resources/prompts/*.md`; Marker bleiben `[STEP_COMPLETE]` etc. |
| 8 | `/prompts`-Route gruppiert nach Agent, CodeMirror-Editor mit Markdown-Highlight (Dark) | ✓ | `PromptList.tsx` + `PromptDetail.tsx`; Browser-Test Step 1+2 |
| 9 | Save-Validation-Fehler als rotes Banner; Erfolg → Override-Badge erscheint | ✓ | `PromptDetail.tsx` errors-Banner; Browser-Test Step 3+4 |
| 10 | Reset mit `window.confirm`, lädt Default, Override-Badge weg | ✓ | `PromptDetail.handleReset`; Browser-Test Step 6 |
| 11 | Listen-Wechsel bei Dirty-State → `window.confirm("Änderungen verwerfen?")` | ✓ | `page.tsx` `handleSelect` (Commit `fb19ce0`) |
| 12 | Bestehende Agent-Tests laufen weiter | ✓ | 59 Suiten / 362+ Tests grün |

## Commits (Frontend)

| Commit | Inhalt |
|---|---|
| `6669169` | `feat(prompts): add frontend api client + codemirror deps` |
| `86f5220` | `feat(prompts): add /prompts page with grouped PromptList` |
| `41a0815` | `feat(prompts): replace placeholder PromptDetail with CodeMirror editor` |
| `943ff73` | `feat(prompts): add /prompts entry to AppShell rail` |
| `fb19ce0` | `feat(prompts): confirm before discarding unsaved edits on selection switch` |
