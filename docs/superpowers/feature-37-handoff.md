# Feature 37 — Editable Agent Prompts — Handoff

**Status:** Backend done, Frontend offen
**Branch:** `feat/editable-agent-prompts` (lokal, nicht gepusht)
**Letzter Commit:** `229713a` (chore: remove agent.system-prompt config block)
**Tests:** 59 Test-Suiten, 362+ Tests, 0 Failures

## Resume in neuer Session

```bash
cd /Users/czarnik/IdeaProjects/ProductSpecAgent
git checkout feat/editable-agent-prompts
git log --oneline main..HEAD     # 9 Commits seit main
```

Dann: "weiter mit Plan-Task 8 — Frontend Dependencies + API-Client" sagen, ich nehme den Faden auf.

## Was bereits fertig ist (Backend, Plan-Tasks 1–7)

| Commit | Inhalt |
|---|---|
| `ac935a0` | Spec + Design-Doc |
| `f1fe0c8` | Implementation Plan |
| `be9c152` | 6 deutsche Default-Prompts in `resources/prompts/` |
| `8605492` | `PromptDefinition` + `PromptValidator` (sealed) + `PromptRegistry` |
| `1ebe641` | `PromptService` mit Cache + S3-First/Resource-Fallback |
| `0f86930` | `PromptController` REST `/api/v1/prompts` |
| `66e5178` | `IdeaToSpecAgent` refactored (`@Value` + 2 companion-Konstanten weg) |
| `bf99775` | `DecisionAgent` + `PlanGeneratorAgent` + `FeatureProposalAgent` refactored |
| `229713a` | `application.yml` `agent.system-prompt`-Block entfernt |

Backend ist Production-ready: Endpoints liefern, Tests grün, alle 4 Agents lesen über `PromptService`.

## Was offen ist (Frontend, Plan-Tasks 8–13)

Plan: `docs/superpowers/plans/2026-05-02-editable-agent-prompts.md`

- **Task 8**: `npm install @uiw/react-codemirror @codemirror/lang-markdown @codemirror/language-data @uiw/codemirror-theme-basic`. `lib/api.ts` erweitern: Types `PromptListItem`/`PromptDetail`/`PromptValidationError` + Wrapper `listPrompts`/`getPrompt`/`savePrompt`/`resetPrompt`. Sicherstellen dass `apiFetch` 400-Bodies durchreicht.
- **Task 9**: `frontend/src/app/prompts/page.tsx` + `frontend/src/components/prompts/PromptList.tsx`. Gruppierung nach `agent`, Override-Badge bei `isOverridden`.
- **Task 10**: `PromptDetail.tsx` mit CodeMirror + Markdown-Highlight + Save/Reset-Buttons + Server-Error-Banner + Dirty-Hook (für Listen-Wechsel-Confirm im `page.tsx`).
- **Task 11**: `AppShell.tsx` — neuer `MessageSquareText`-Icon-Eintrag in der Rail nach `/asset-bundles`.
- **Task 12**: Browser-Verifikation (8-Punkte-Checkliste im Plan).
- **Task 13**: Done-Doc `docs/features/37-editable-agent-prompts-done.md` mit Akzeptanzkriterien-Status und den unten gelisteten Plan-Abweichungen.

## Plan-Abweichungen, die im Done-Doc landen sollten

1. **Tests-Cleanup für Singleton-Cache (Task 4)**: Plan-`@AfterEach` mit nur `objectStore.deletePrefix("prompts/")` reichte nicht — `PromptService` ist Spring-Singleton mit `ConcurrentHashMap`-Cache, der zwischen Tests überlebte. Fix: zusätzlich `promptRegistry.definitions.forEach { promptService.reset(it.id) }` im Cleanup.

2. **Mehr `IdeaToSpecAgent`-Verwendungen als der Plan annahm (Task 5)**: 4 Test-Files (nicht 2): `IdeaToSpecAgentTest`, `ChatControllerTest`, `WizardChatControllerTest`. `MARKER_REMINDER` an 5 Stellen verwendet (nicht 4).

3. **`DecisionAgent`/`PlanGeneratorAgent`/`FeatureProposalAgent`-Refactor traf 11 Test-Files (Task 6)**: 4 Agent-Tests + 4 Controller-Tests mit `@TestConfiguration`-Beans + 3 Service/Builder-Tests. Pattern überall: `PromptService(PromptRegistry(), InMemoryObjectStore())` injizieren.

4. **`InMemoryObjectStore` existiert bereits**: im `storage`-Package, von früheren Tests genutzt. Wiederverwendet — keine eigene Test-Fake nötig.

5. **Kein MockK im Projekt (Task 3)**: Plan schlug MockK-Pattern vor, aber das Projekt nutzt durchweg echte Services + hand-rolled Test-Doubles. `PromptServiceTest` hat eine private `InMemoryObjectStore`-Klasse mit Call-Counter — passt zum Projekt-Stil.

## Bekannte Restpunkte für die Done-Doc (nicht durch Implementation entstanden — bewusst YAGNI)

- User-Prompt-Templates (`DecisionAgent.buildString`, `PlanGeneratorAgent.buildString`) bleiben Code (mischen statischen Text + Kontext-Injektion).
- Multi-Replica-Cache-Invalidation: bei Skalierung auf >1 Backend-Instanz müsste Pub/Sub-Invalidate ergänzt werden.
- Keine Versionierung / Edit-History — nur "Reset auf Default" als Rollback.

## Nicht-feature-37-bezogener Working-Tree-Stand (unangetastet)

- `M backend/src/main/resources/application.yml` (User-Änderung `model: gpt-4o → gpt-5.5`, nicht zu committen)
- `M infra/base/.../LoadBalancerController.kt` (pre-existing)
- `?? scripts/destroy.sh`, `?? scripts/destroy-unreachable-cluster.sh` (untracked, pre-existing)

## Spec/Design/Plan zum Querlesen

- `docs/features/37-editable-agent-prompts.md`
- `docs/superpowers/specs/2026-05-02-editable-agent-prompts-design.md`
- `docs/superpowers/plans/2026-05-02-editable-agent-prompts.md`
