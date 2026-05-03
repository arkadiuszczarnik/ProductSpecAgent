# Design: Per-Agent Model Selection

**Feature:** [38-per-agent-model-selection.md](../../features/38-per-agent-model-selection.md)
**Datum:** 2026-05-03
**Branch:** `feat/per-agent-model-selection`
**Pattern-Vorlage:** Feature 37 — Editable Agent Prompts

## 1 — Problem & Ziel

Heute laufen alle 4 Backend-Agents (`IdeaToSpecAgent`, `DecisionAgent`, `FeatureProposalAgent`, `PlanGeneratorAgent`) durch denselben `KoogAgentRunner` mit dem gleichen, global in `application.yml` unter `agent.model` konfigurierten Modell. Zwei Probleme:

1. **Pauschal statt differenziert.** Eine JSON-Decision braucht kein Top-Tier-Modell, eine Spec-Generierung profitiert messbar davon. Heute keine Differenzierung möglich.
2. **Aktuelle Konfig ist still kaputt.** `agent.model: gpt-5.5` ist im `KoogAgentRunner.resolveModel()`-`when` nicht gemappt — der Runner fällt lautlos auf `OpenAIModels.Chat.GPT4o` zurück.

**Ziel:** Pro Agent eine von drei Modell-Stufen (`SMALL` / `MEDIUM` / `LARGE`) wählbar. Stufen werden zentral in `application.yml` auf konkrete Koog-`OpenAIModels.Chat`-Konstanten gemappt und sind in einer Admin-UI (`/agent-models`) je Agent änderbar — analog zum bestehenden `/prompts`-Pattern.

## 2 — Architektur

```
application.yml
├── agent.models.tiers.{SMALL,MEDIUM,LARGE}: <openai-id>   (Deployment-Konfig, fail-fast)
└── agent.models.defaults.<agent-id>: <TIER>               (Deployment-Konfig, fail-fast)

Backend
├── AgentModelTier         enum SMALL / MEDIUM / LARGE
├── AgentModelsProperties  @ConfigurationProperties("agent.models")
├── AgentModelRegistry     boot-validierte Maps Tier→LLModel + AgentId→DefaultTier
├── AgentModelService      S3-First / Resource-Fallback, Cache, Read/Save/Reset
├── KoogAgentRunner.run(agentId, systemPrompt, userMessage)
│       → service.getTier(agentId) → registry.modelFor(tier)
└── AgentModelController   REST /api/v1/agent-models

Frontend (shadcn/ui)
├── lib/api/agent-models.ts
├── components/agent-models/AgentModelList.tsx
├── components/agent-models/AgentModelDetail.tsx
├── app/agent-models/page.tsx
└── app-shell rail-Eintrag (Cpu-Icon)
```

**Trennung der Belange.** Tier-Mapping (Tier→Modell) ist statische Deployment-Konfig — beim Start einmal eingelesen, validiert, danach unveränderlich. Per-Agent-Selection (Agent→Tier) ist dynamische Produkt-Entscheidung — S3-persistiert, im UI änderbar, gecached. Beide Schichten sind unabhängig refactorbar.

## 3 — Komponenten und Schnittstellen

### 3.1 `AgentModelTier`

```kotlin
enum class AgentModelTier { SMALL, MEDIUM, LARGE }
```

### 3.2 `AgentModelsProperties` (`@ConfigurationProperties`)

```kotlin
@ConfigurationProperties(prefix = "agent.models")
data class AgentModelsProperties(
    val tiers: Map<AgentModelTier, String> = emptyMap(),
    val defaults: Map<String, AgentModelTier> = emptyMap(),
)
```

### 3.3 `AgentModelRegistry` (`@Component`)

Konstruiert über eine `@Configuration`-Klasse, die `AgentModelsProperties` einliest und beim Boot validiert:

- Jedes `AgentModelTier` muss einen Eintrag in `tiers` haben — sonst `IllegalStateException("Tier mapping incomplete: missing <TIER>")`.
- Jeder Modell-String wird über die in `KoogAgentRunner` ohnehin existierende `resolveModel(name: String): LLModel`-Funktion aufgelöst (extrahiert in eine private static-Helper-Funktion oder in den `AgentModelRegistry`-`init`-Block) — ergänzt um die GPT-5-Familie. Unbekannter String → `IllegalStateException("Unknown OpenAI model id: <name>")`.
- `defaults` muss genau die 4 erwarteten Agent-IDs enthalten (Set wird hartkodiert in einer `companion object`-Konstante `KNOWN_AGENT_IDS = setOf("idea-to-spec", "decision", "feature-proposal", "plan-generator")`). Fehlend → `IllegalStateException("Missing default tier for agent: <id>")`. Unbekannte ID → `IllegalStateException("Unknown agent id in defaults: <id>")`.

Public API:

```kotlin
class AgentModelRegistry(...) {
    fun agentIds(): Set<String>                       // gibt KNOWN_AGENT_IDS zurück
    fun defaultTier(agentId: String): AgentModelTier  // wirft, wenn agentId unknown
    fun modelFor(tier: AgentModelTier): LLModel
    fun modelIdFor(tier: AgentModelTier): String      // für API-Response (Anzeige)
}
```

### 3.4 `AgentModelService` (Singleton)

S3-First-Pattern komplett aus `PromptService` übernommen:

```kotlin
@Service
class AgentModelService(
    private val registry: AgentModelRegistry,
    private val objectStore: ObjectStore,
) {
    fun getTier(agentId: String): AgentModelTier
    fun setTier(agentId: String, tier: AgentModelTier)
    fun reset(agentId: String)
    fun listAll(): List<AgentModelInfo>
}

data class AgentModelInfo(
    val agentId: String,
    val displayName: String,
    val defaultTier: AgentModelTier,
    val currentTier: AgentModelTier,
    val isOverridden: Boolean,
    val tierMapping: Map<AgentModelTier, String>,
)
```

**Persistenz:** Eine einzige Datei `agent-models/selections.json`:

```json
{ "selections": { "idea-to-spec": "MEDIUM", "decision": "SMALL" } }
```

**Cache:** `ConcurrentHashMap<String, AgentModelTier>`. Bei Boot einmal `selections.json` lesen → Cache füllen. Bei `setTier` / `reset`: erst `selections.json` zurückschreiben, dann Cache mutieren (transaktionale Reihenfolge — Cache nur dann ändern, wenn S3 erfolgreich war). Bei `getTier`: nur Cache, mit Fallback auf `registry.defaultTier(agentId)`.

**Display-Namen** (für UI):
| Agent ID | Display |
|---|---|
| `idea-to-spec` | „Idea-to-Spec" |
| `decision` | „Decision" |
| `feature-proposal` | „Feature Proposal" |
| `plan-generator` | „Plan Generator" |

### 3.5 `AgentModelController`

```
GET    /api/v1/agent-models                  → 200 List<AgentModelInfo>
PUT    /api/v1/agent-models/{agentId}        Body { "tier": "MEDIUM" }   → 204
DELETE /api/v1/agent-models/{agentId}        → 204
```

Fehler-Mapping:
- Unknown `agentId` → `IllegalArgumentException` → Spring-`@ExceptionHandler` → 404.
- Invalides Tier-Enum (`"XL"`) → Spring-Jackson lehnt Deserialisierung ab → 400.
- Fehlender `tier`-Body → 400.

### 3.6 `KoogAgentRunner` (geändert)

```kotlin
@Component
class KoogAgentRunner(
    @Qualifier("openAIExecutor") private val promptExecutor: PromptExecutor,
    private val modelService: AgentModelService,
    private val modelRegistry: AgentModelRegistry,
) {
    suspend fun run(agentId: String, systemPrompt: String, userMessage: String): String {
        val tier = modelService.getTier(agentId)
        val model = modelRegistry.modelFor(tier)
        val agent = AIAgent(promptExecutor = promptExecutor, systemPrompt = systemPrompt, llmModel = model)
        return agent.run(userMessage)
    }
}
```

Die alte `@Value("\${agent.model}")`-Injektion entfällt komplett. Die `resolveModel(name)`-Funktion wandert in eine eigene Datei `agent/OpenAiModelResolver.kt` (Top-Level-Funktion) — wiederverwendet vom `AgentModelRegistry`-Konstruktor und direkt testbar in `OpenAiModelResolverTest`.

**Erweiterte Modell-Mappings** (`OpenAiModelResolver`):

| YAML-Wert | Koog-Konstante |
|---|---|
| `gpt-4o` | `OpenAIModels.Chat.GPT4o` |
| `gpt-4o-mini` | `OpenAIModels.Chat.GPT4oMini` |
| `gpt-4.1` | `OpenAIModels.Chat.GPT4_1` |
| `gpt-4.1-mini` | `OpenAIModels.Chat.GPT4_1Mini` |
| `gpt-4.1-nano` | `OpenAIModels.Chat.GPT4_1Nano` |
| `gpt-5-nano` | `OpenAIModels.Chat.GPT5Nano` |
| `gpt-5-mini` | `OpenAIModels.Chat.GPT5Mini` |
| `gpt-5` | `OpenAIModels.Chat.GPT5` |
| `gpt-5-2` | `OpenAIModels.Chat.GPT5_2` |
| `gpt-5-2-pro` | `OpenAIModels.Chat.GPT5_2Pro` |

Unbekannter Wert → `IllegalStateException` (kein stiller `GPT4o`-Fallback mehr).

### 3.7 Agent-Konstanten

Jeder der 4 Agents bekommt eine `companion object`-Konstante:

```kotlin
class IdeaToSpecAgent(...) {
    companion object { const val AGENT_ID = "idea-to-spec" }
    // …
    runAgent(...) ruft koogRunner.run(AGENT_ID, ...)
}
```

Analog `DecisionAgent.AGENT_ID = "decision"`, `FeatureProposalAgent.AGENT_ID = "feature-proposal"`, `PlanGeneratorAgent.AGENT_ID = "plan-generator"`.

Die Konstanten werden in `AgentModelRegistry.KNOWN_AGENT_IDS` gespiegelt — die Wahrheit liegt im Code, nicht in der YAML.

## 4 — Datenfluss

### 4.1 Boot

1. Spring lädt `application.yml`, bindet `AgentModelsProperties`.
2. `AgentModelsConfiguration` instanziiert `AgentModelRegistry` — Validierung läuft im Konstruktor:
   - Tier-Vollständigkeit
   - Modell-IDs auflösbar
   - Defaults vollständig & nur bekannte Agent-IDs
3. Erster Fehler → `IllegalStateException` → Spring-Boot bricht den Application-Context-Start ab.
4. `AgentModelService` wird konstruiert; `init`-Block lädt `agent-models/selections.json` aus `ObjectStore.get(...)` einmalig in den Cache. Datei nicht vorhanden → leerer Cache (kein Fehler).

### 4.2 Read (Agent-Aufruf)

```
Agent.method()
  ↓
koogRunner.run(AGENT_ID, systemPrompt, userMessage)
  ↓
service.getTier(agentId)        // Cache-Hit oder registry.defaultTier(agentId)
  ↓
registry.modelFor(tier)         // LLModel
  ↓
AIAgent(... llmModel = model).run(userMessage)
```

### 4.3 Write (UI-Save)

```
PUT /api/v1/agent-models/{agentId}    Body { "tier": "MEDIUM" }
  ↓
controller validiert Pfad + Body (Spring-Jackson)
  ↓
service.setTier(agentId, MEDIUM)
   1. wenn agentId ∉ registry.agentIds() → 404 via Exception-Handler
   2. neue Map = currentMap + (agentId → MEDIUM)
   3. objectStore.put("agent-models/selections.json", JSON.encode(map).toByteArray())
   4. cache.replaceAll(map)   // erst nach erfolgreichem put
  ↓
204
```

### 4.4 Reset

```
DELETE /api/v1/agent-models/{agentId}
  ↓
service.reset(agentId)
   1. neue Map = currentMap - agentId
   2. objectStore.put(...) (oder delete, wenn leer)
   3. cache update
  ↓
204
```

### 4.5 List

`listAll()` baut für jede `KNOWN_AGENT_IDS`-Entry ein `AgentModelInfo`. `currentTier` aus Cache oder Default. `isOverridden = cache.containsKey(agentId)`.

## 5 — Fehlerbehandlung

| Schicht | Fehler | Verhalten |
|---|---|---|
| Boot | Tier fehlt / Default fehlt | `IllegalStateException` → Boot-Abbruch |
| Boot | Modell-ID unbekannt | `IllegalStateException` → Boot-Abbruch |
| Boot | Default für unbekannten Agent | `IllegalStateException` → Boot-Abbruch |
| Boot | `selections.json` korrupt (JSON-Parse-Error) | Log-Warning mit Fehlermeldung, Cache bleibt leer, Defaults greifen — keine Boot-Abbruch (Self-Healing, da User-Daten im Zweifel ignorierbar) |
| Boot | S3 down beim ersten `get` | Log-Warning, leerer Cache, Defaults greifen — Boot läuft trotzdem durch |
| Runtime | `getTier(unknownId)` | `IllegalArgumentException` (Programmierfehler) |
| Runtime | `setTier` mit S3-Fehler | Cache nicht mutiert, Exception propagiert → 500 |
| REST | Unknown `agentId` (PUT/DELETE) | `IllegalArgumentException` → 404 |
| REST | Invalid Tier-Enum | Jackson → 400 |
| REST | Fehlender Body / `tier`-Feld | 400 |

**Multi-Replica-Limitation:** Wie in Feature 37 dokumentiert — Cache-Invalidation zwischen Replicas ist out of scope (gleicher Workaround: Restart bei Skalierung > 1).

## 6 — Default-Konfiguration

```yaml
agent:
  models:
    tiers:
      SMALL:  "gpt-5-nano"
      MEDIUM: "gpt-5-mini"
      LARGE:  "gpt-5-2"
    defaults:
      idea-to-spec:     LARGE
      decision:         MEDIUM
      feature-proposal: MEDIUM
      plan-generator:   LARGE
```

**Migration:**
- `agent.model: gpt-5.5` aus `application.yml` ersatzlos streichen (Wert war ohnehin nicht funktional).
- Test-`application.yml` (`backend/src/test/resources/application.yml`) bekommt valide Mappings: alle drei Tiers werden auf `gpt-4o-mini` gemappt (Tests rufen kein echtes OpenAI auf — `runAgent` ist in den Test-Subklassen überschrieben; das Mapping muss nur die Boot-Validierung passieren).

## 7 — Tests

### 7.1 Backend

| Test | Scope |
|---|---|
| `OpenAiModelResolverTest` | bekannte Strings → korrekte `LLModel`-Konstanten; unbekannter String → `IllegalStateException` |
| `AgentModelRegistryTest` | gültiger Properties-Bind → ok; fehlender Tier / fehlender Default / unbekannter Modell-String / unbekannter Agent-ID jeweils → erwartete `IllegalStateException` mit Message-Match |
| `AgentModelServiceTest` | leerer Bootstrap → alle 4 Agents geben Default zurück; `setTier` schreibt in `InMemoryObjectStore` und Cache; `reset` entfernt aus beidem; mehrfaches `getTier` liest S3 nur 1× (Counter); S3-Put-Exception in `setTier` lässt Cache unverändert |
| `AgentModelControllerTest` (`MockMvc`) | `GET` Liste mit allen 4 Einträgen + `tierMapping`-Block; `PUT 204` happy path; `PUT 400` für `"XL"`; `PUT 404` für unknown agentId; `DELETE 204`; `DELETE 404` für unknown |
| `KoogAgentRunnerTest` (neu) | mit gestubbter `AgentModelService` + `AgentModelRegistry`: `run("idea-to-spec", …)` löst korrekten Tier auf und übergibt korrektes `LLModel` an `AIAgent`-Konstruktor (verifizierbar via Stub-Executor, der die `LLModel`-Instanz im Aufruf abgreift); unknown agentId → Exception |
| Bestehende Agent-Tests | Anpassung der Stub-Aufrufe an neue `run(agentId, ...)`-Signatur. Override-Hooks (`runAgent(prompt)` bzw. `runAgent(systemPrompt, userMessage)` in den Subklassen) bleiben unverändert — nur die produktiven Calls in den 4 Agents ändern sich |

### 7.2 Frontend

Browser-Tests sind manuell (kein E2E-Framework im Projekt — Pattern aus Feature 37):

1. `/agent-models` lädt alle 4 Agents mit korrekten Default-Tiers.
2. Tier ändern + Save → Override-Badge erscheint.
3. Reload → neuer Tier persistiert.
4. Reset (mit Confirm) → Default-Tier wieder, Badge weg.
5. Listen-Wechsel mit ungesicherten Änderungen → Confirm-Dialog.
6. Backend-Restart → UI zeigt persistierte Tiers (S3-Überleben).

## 8 — Frontend (shadcn/ui)

Pattern komplett aus `/prompts` adaptiert.

**Layout:** Split-View, Liste links, Detail rechts (`AppShell` mit full-bleed-Route registriert).

**`AgentModelList.tsx`:**
- Flache Liste der 4 Agents (gruppieren lohnt bei nur 4 Items nicht).
- Override-Badge `●` wenn `isOverridden`.

**`AgentModelDetail.tsx`:**
- shadcn `RadioGroup` mit drei `RadioGroupItem`s für `SMALL` / `MEDIUM` / `LARGE`.
- Label-Text pro Item: `"<TIER> — <modelId>"`, z. B. „MEDIUM — gpt-5-mini" (aus `tierMapping` der API-Response).
- `Save`-Button (`shadcn/Button`) → `PUT`. Disabled, solange kein Dirty-State.
- `Reset`-Button → `DELETE`, vorher `window.confirm("Auf Default zurücksetzen?")`.
- Dirty-Tracking lokal; Listen-Wechsel mit ungesichertem Dirty-State → `window.confirm("Änderungen verwerfen?")`.

**`page.tsx`:**
- Lädt `GET /api/v1/agent-models` einmalig.
- State-Management lokal (kein Zustand-Store nötig — kleine isolierte Page).

**`AppShell`-Rail-Eintrag:**
- Icon `Cpu` aus `lucide-react`, nach `/prompts`-Eintrag.
- Pfad als full-bleed-Route registriert (`overflow-hidden` auf `<main>`), wie `/prompts`.

## 9 — Akzeptanzkriterien

1. `application.yml` enthält `agent.models.tiers` (SMALL/MEDIUM/LARGE → OpenAI-Modell-ID) und `agent.models.defaults` (4 Agents → Tier).
2. `agent.model`-Property ist entfernt; `KoogAgentRunner` nutzt `AgentModelService` + `AgentModelRegistry` zur Auflösung.
3. Modell-Resolver kennt mindestens `gpt-5-nano`, `gpt-5-mini`, `gpt-5`, `gpt-5-2`, `gpt-5-2-pro` und die bestehenden `gpt-4*`. Unbekannter Name → `IllegalStateException`, Boot-Abbruch.
4. Fehlender Tier-Eintrag, fehlender Default oder unbekannter Agent-ID in YAML → Boot-Abbruch mit aussagekräftiger Message.
5. `GET /api/v1/agent-models` listet 4 Agents mit `defaultTier`, `currentTier`, `isOverridden` und `tierMapping`.
6. `PUT /api/v1/agent-models/{agentId}` mit gültigem Tier → 204; persistiert nach `agent-models/selections.json`; aktualisiert Cache; nächster Agent-Aufruf nutzt das neue Modell.
7. `PUT` mit invalidem Tier-String → 400. `PUT` für unbekannten `agentId` → 404.
8. `DELETE /api/v1/agent-models/{agentId}` → 204; `GET` liefert wieder `currentTier == defaultTier` und `isOverridden = false`.
9. Alle 4 Agents übergeben ihre `AGENT_ID`-Konstante an `KoogAgentRunner.run(...)`. Bestehende 59+ Test-Suiten bleiben grün.
10. Frontend-Route `/agent-models` zeigt Liste + Detail; Save/Reset funktionieren End-to-End gegen das Backend; AppShell-Rail enthält neuen Eintrag mit `Cpu`-Icon.

## 10 — Out of Scope (V1)

- Pro-Projekt-Override (alle Projekte teilen dieselben Selections).
- Provider außer OpenAI.
- Edit-History / Audit-Log.
- Pro-Tier konfigurierbare `temperature` / `reasoningEffort`.
- Multi-Replica-Cache-Invalidation (Limitation aus Feature 37 wird übernommen).
- Runtime-Änderbarkeit des Tier-Mappings (statisch in `application.yml`).
