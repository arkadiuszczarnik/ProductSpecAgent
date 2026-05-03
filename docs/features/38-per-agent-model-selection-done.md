# Feature 38 — Per-Agent Model Selection — Done

**Datum:** 2026-05-03
**Branch:** `feat/per-agent-model-selection` (offen, bereit für Merge nach Browser-Verifikation)
**Spec:** `docs/superpowers/specs/2026-05-03-per-agent-model-selection-design.md`
**Plan:** `docs/superpowers/plans/2026-05-03-per-agent-model-selection.md`
**Feature-Doc:** `docs/features/38-per-agent-model-selection.md`

## Was umgesetzt wurde

**Backend**

- `AgentModelTier`-Enum mit 3 Werten (`SMALL`, `MEDIUM`, `LARGE`) und `OpenAiModelResolver`-Top-Level-Funktion. Resolver mappt 10 OpenAI-Modell-IDs (`gpt-4o`, `gpt-4o-mini`, `gpt-4.1`/`mini`/`nano`, `gpt-5-nano`/`mini`/`5`/`5-2`/`5-2-pro`) auf `OpenAIModels.Chat.*` Konstanten; unbekannter Wert → `IllegalStateException`.
- `AgentModelsProperties` (`@ConfigurationProperties(prefix="agent.models")`) bindet `tiers` und `defaults` aus `application.yml`. Auto-discovered via existing `@ConfigurationPropertiesScan`.
- `AgentModelRegistry` validiert beim Boot: Tier-Vollständigkeit, Modell-IDs auflösbar, alle 4 known agent IDs in `defaults`, keine fremden agent IDs. Vier separate Failure-Modes mit aussagekräftigen Messages.
- `AgentModelService` mit S3-First / Resource-Fallback (analog zu `PromptService` aus Feature 37): ConcurrentHashMap-Cache, `init`-Block lädt `agent-models/selections.json`, Self-Healing bei korruptem JSON oder S3-Down. Write-before-cache-Ordering: bei S3-Fehler bleibt Cache unverändert.
- `AgentModelController` (`/api/v1/agent-models`) mit `GET` (Liste), `PUT /{agentId}` (204), `DELETE /{agentId}` (204). Lokaler `@ExceptionHandler(IllegalArgumentException::class) → 404`. Tier-Validierung über Spring/Jackson-Enum-Binding (`"XL"` → 400).
- `KoogAgentRunner` refaktoriert: neue Signatur `run(agentId, systemPrompt, userMessage)`, Modell-Auflösung dynamisch via `AgentModelService` + `AgentModelRegistry`. Alte `@Value("\${agent.model}")`-Injection und `resolveModel`-when-Block entfernt (Letzterer wandert nach `OpenAiModelResolver.kt`).
- 4 Agents (`IdeaToSpecAgent`, `DecisionAgent`, `FeatureProposalAgent`, `PlanGeneratorAgent`) tragen `companion object { const val AGENT_ID = "..." }` und übergeben sie an den Runner.
- `application.yml` (main + test): `agent.model` raus, `agent.models.tiers` + `agent.models.defaults` rein. Test-yml mappt alle drei Tiers auf `gpt-4o-mini` (Tests rufen kein echtes OpenAI auf).
- Tests: 5 neue Test-Suites (`OpenAiModelResolverTest`, `AgentModelRegistryTest`, `AgentModelServiceTest`, `AgentModelControllerTest`, `KoogAgentRunnerTest`) mit 23 neuen Tests. Gesamt-Suite: 387 Tests grün, keine Regression.

**Frontend**

- `lib/api.ts` ergänzt um `AgentModelTier`-Type, `AgentModelInfo`-Interface und 3 Wrapper-Funktionen (`listAgentModels`, `updateAgentModel`, `resetAgentModel`) mit `encodeURIComponent`-defensive-Pattern.
- shadcn `radio-group` via `npx shadcn@latest add radio-group` installiert — generierte Datei nutzt `@base-ui/react/radio` direkt (kein Radix-Rewriting nötig).
- `AgentModelList.tsx` mit Override-Badge (`●`-Circle aus lucide-react) und einfacher flacher Liste (4 Items lohnen keine Gruppierung).
- `AgentModelDetail.tsx` mit shadcn `RadioGroup` (3 Tier-Optionen, Label `"<TIER> — <modelId>"`), Save/Reset-Buttons, Dirty-Tracking, Error-Banner. `window.confirm` für Reset und Listen-Wechsel.
- Neue Route `/agent-models` (`page.tsx`) mit Split-Layout (320px Liste links, Detail rechts).
- `AppShell` ergänzt: `Cpu`-Icon (lucide-react) als neuer Rail-Eintrag nach `/prompts`, Full-Bleed-Handling für `/agent-models`.

## Bewusste Abweichungen / Restpunkte

- **Plan-Task 7 hat die 4 Agent-Call-Sites mit-gepatcht** (mit literalen `agentId`-Strings), weil sonst der Produktiv-Code nicht kompilierte und der Test-Compile blockiert war. Plan-Task 8 hat dann die Literale durch `AGENT_ID`-Konstanten ersetzt — net effect identisch zum Plan, nur die Commit-Aufteilung verschoben.
- **Plan-Task 10 (Anpassung bestehender Agent-Tests) war ein No-op.** Die 4 bestehenden Agent-Tests stubben durchgängig die `runAgent(...)`-Override-Methode in den Subklassen (Pattern A laut Plan) und nutzen Spring-DI für `KoogAgentRunner`. Die neue Konstruktor-Signatur wurde von Spring automatisch korrekt verdrahtet, kein einziger Test brauchte Anpassung.
- **`KoogAgentRunnerTest` brauchte einen Quick-Fix (Commit `6495ce9`):** `fun X() = runBlocking { ... assertThat(...) }` lieferte einen `AbstractObjectAssert`-Returntyp, woraufhin JUnit 5 die @Test-Methode still ignoriert hat. Fix: `: Unit = runBlocking { ... }` als expliziter Returntyp.
- **`CapturingExecutor`-Stub im `KoogAgentRunnerTest` weicht von der Plan-Vorlage ab.** Koog 0.7.3's `PromptExecutor` ist `expect abstract class` mit anderen Methoden-Signaturen als der Plan vermutete (`executeStreaming` ist NICHT `suspend`, returnt `Flow<StreamFrame>`; `moderate` returnt `ModerationResult`; `close()` aus `AutoCloseable`). Stub wurde an die echten Signaturen angepasst, statt `Clock.System` aus `kotlinx.datetime` (nicht im Classpath) wird `ResponseMetaInfo.Empty` verwendet.
- **`agent.system-prompt`-Block** in `backend/src/test/resources/application.yml` (Dead Code seit Feature 37) wurde im Rahmen der YAML-Migration mit-entfernt. Verifikation per grep: kein Backend-Code liest diese Property.
- **Manuelle E2E-Browser-Verifikation steht aus** (Plan-Task 14, Akzeptanzkriterium #10). Alle Code-Pfade verifiziert (387 Tests grün, Frontend-Build clean), aber das End-to-End-Zusammenspiel (`Save` → S3 → nächster Agent-Aufruf nutzt neues Modell, sichtbar im Backend-Log) muss am laufenden System bestätigt werden.

## Nicht umgesetzt (bewusst out-of-scope, im Spec § 10 dokumentiert)

- Pro-Projekt-Override (alle Projekte teilen dieselben Selections).
- Provider außer OpenAI.
- Edit-History / Audit-Log.
- Pro-Tier konfigurierbare `temperature` / `reasoningEffort`.
- Multi-Replica-Cache-Invalidation (Limitation aus Feature 37 wird übernommen).
- Runtime-Änderbarkeit des Tier-Mappings.

## Follow-up-Kandidaten (aus Final-Review)

- `displayNameFor` von `AgentModelService.companion` nach `AgentModelRegistry` umziehen — bündelt „Wahrheit über Agents" an einer Stelle.
- `tierMappingView()` per `Collections.unmodifiableMap` schützen, damit Konsumenten die Registry-State nicht durch Cast korrumpieren können (defense-in-depth).
- Optional: `GET /api/v1/agent-models/{agentId}` für fokussiertes Lookup, statt das ganze Listen-Endpoint im Detail-Panel zu pollen.
- Optional: Default-Selection des ersten Listen-Items beim Page-Load (Pattern-konsistent mit `/prompts` aktuell).
- Pin der `AgentModelTier`-JSON-Serialisierungs-Form per expliziten Test (heute funktioniert es per Spring-Default `Enum.name()`, ist aber nicht abgesichert).

## Akzeptanzkriterien-Status

| # | Kriterium | Status | Verifikation |
|---|---|---|---|
| 1 | `agent.models.tiers` + `defaults` in `application.yml` | ✓ | `backend/src/main/resources/application.yml:22-32` |
| 2 | `agent.model` raus, Runner nutzt Service+Registry | ✓ | `KoogAgentRunner.kt`, kein `@Value` mehr |
| 3 | Resolver kennt GPT-5-Familie, fail-fast bei unbekanntem | ✓ | `OpenAiModelResolverTest` (3 Tests) |
| 4 | Boot-Abbruch bei fehlender / unbekannter YAML-Config | ✓ | `AgentModelRegistryTest` (4 Failure-Modes getestet) |
| 5 | `GET /api/v1/agent-models` mit allen Feldern | ✓ | `AgentModelControllerTest.GET lists all four agents` |
| 6 | `PUT` 204, persistiert + cached, nächster Aufruf nutzt neues Modell | ✓ | `AgentModelServiceTest` + `KoogAgentRunnerTest` (Halbketten verifiziert) |
| 7 | `PUT` invalid Tier → 400, unknown agentId → 404 | ✓ | `AgentModelControllerTest` |
| 8 | `DELETE` 204, GET danach `isOverridden=false` | ✓ | `AgentModelControllerTest.DELETE removes override` |
| 9 | 4 Agents übergeben `AGENT_ID`, alle Tests grün | ✓ | 387 / 387 Tests grün, grep verifiziert AGENT_ID-Aufrufe |
| 10 | Frontend `/agent-models` + AppShell-Eintrag, Save/Reset E2E | ✓ Code, ⚠ Browser-Verifikation steht aus | Frontend-Build clean, manuelle Verifikation noch nötig |

## Commits (chronologisch)

| Commit | Inhalt |
|---|---|
| `23f77ff` | `docs(feature-38): add per-agent model selection feature + design spec` |
| `69c35ff` | `docs(feature-38): add implementation plan for per-agent model selection` |
| `89b61c5` | `feat(agent-models): add AgentModelTier enum + OpenAiModelResolver` |
| `889ad1f` | `feat(agent-models): add AgentModelsProperties for application.yml binding` |
| `94dea39` | `feat(agent-models): add AgentModelRegistry with boot-validation` |
| `58e37ee` | `feat(agent-models): add AgentModelInfo + UpdateAgentModelRequest domain types` |
| `1a5e256` | `feat(agent-models): add AgentModelService with S3-first cache` |
| `f67ccd2` | `feat(agent-models): add REST controller + MockMvc tests (yml migration follows)` |
| `613dbcb` | `refactor(KoogAgentRunner): take agentId, resolve model via AgentModelService` |
| `6495ce9` | `fix(KoogAgentRunner-test): force Unit return so JUnit 5 actually runs both tests` |
| `8663103` | `feat(agent-models): pass AGENT_ID from each agent into KoogAgentRunner` |
| `dcd4872` | `feat(agent-models): migrate application.yml from agent.model to agent.models.{tiers,defaults}` |
| `1fe09a3` | `feat(agent-models): add frontend API client types + wrappers` |
| `fa27866` | `feat(agent-models): add list + detail components with shadcn radio-group` |
| `0cf2c01` | `feat(agent-models): add /agent-models page and AppShell rail entry` |
