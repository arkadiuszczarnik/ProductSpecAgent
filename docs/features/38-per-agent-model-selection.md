# Feature 38 — Per-Agent Model Selection

**Phase:** Konfiguration / AI-Backend
**Abhängig von:** Feature 0 (Project Setup), Feature 37 (Editable Agent Prompts) — Pattern-Vorlage
**Aufwand:** M
**Branch:** `feat/per-agent-model-selection`

## Problem

Heute nutzen alle vier Backend-Agents (`IdeaToSpecAgent`, `DecisionAgent`, `FeatureProposalAgent`, `PlanGeneratorAgent`) das gleiche, global in `application.yml` unter `agent.model` konfigurierte OpenAI-Modell. Das ist aus zwei Gründen unzureichend:

1. **Nicht differenziert nach Aufgabe:** Ein einfaches Decision-JSON braucht kein `GPT5_2Pro`, eine vollständige Spec-Generierung profitiert dagegen messbar von einem stärkeren Modell. Kosten und Latenz werden unnötig über alle Agents gemittelt.
2. **Aktuell gar nicht funktional:** Der konfigurierte Wert `gpt-5.5` ist im `KoogAgentRunner.resolveModel()` nicht gemappt — der Runner fällt still auf `OpenAIModels.Chat.GPT4o` zurück. Die Konfiguration suggeriert eine Auswahl, die es nicht gibt.

## Ziel

Pro Agent ist eine von drei Modell-Stufen (`SMALL`, `MEDIUM`, `LARGE`) wählbar. Stufen werden zentral in `application.yml` auf konkrete Koog-Modelle gemappt und sind in einer separaten Admin-UI je Agent änderbar — analog zum bestehenden `/prompts`-Pattern aus Feature 37.

## Architektur

### Begriffe

- **`AgentModelTier`** — Enum mit drei Werten: `SMALL`, `MEDIUM`, `LARGE`
- **`AgentId`** — String-Identifier pro Agent (wiederverwendet aus Prompt-Registry-Logik): `idea-to-spec`, `decision`, `feature-proposal`, `plan-generator`
- **Tier-Mapping** — `application.yml` ordnet jeder Stufe einen Koog-Modellnamen zu (`gpt-5-nano`, `gpt-5-mini`, `gpt-5-2`)
- **Per-Agent-Override** — S3-persistierte Map `agent-id -> AgentModelTier`, mit Default-Tier pro Agent als Fallback

### Datenmodell

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

S3-Override (eine Datei): `agent-models/selections.json`

```json
{
  "selections": {
    "idea-to-spec": "LARGE",
    "decision": "SMALL",
    "feature-proposal": "MEDIUM",
    "plan-generator": "LARGE"
  }
}
```

### Komponenten

| Klasse | Aufgabe |
|---|---|
| `AgentModelRegistry` | Liest Tier-Mapping + Defaults aus Spring-Config. Liefert `agentIds()`, `defaultTier(id)`, `modelFor(tier)`. |
| `AgentModelService` | S3-First / Resource-Fallback wie `PromptService`. `getTier(agentId)`, `setTier(agentId, tier)`, `reset(agentId)`. ConcurrentHashMap-Cache. |
| `KoogAgentRunner` | Erweitert: `run(agentId, systemPrompt, userMessage)`. Resolved Modell-Stufe via `AgentModelService` → `AgentModelRegistry.modelFor(...)` → Koog `LLModel`. |
| `AgentModelController` | REST `/api/v1/agent-models` — `GET` Liste, `PUT /{agentId}` setzt Tier, `DELETE /{agentId}` reset auf Default. |
| Agents (4×) | Jeder ruft `koogRunner.run(agentId = "...", ...)` mit eigenem `agentId`. |

### Mapping `OpenAIModels.Chat`

`KoogAgentRunner.resolveModel(name: String)` wird erweitert um die GPT-5-Familie:

| YAML-Wert | Koog-Konstante |
|---|---|
| `gpt-5-nano` | `OpenAIModels.Chat.GPT5Nano` |
| `gpt-5-mini` | `OpenAIModels.Chat.GPT5Mini` |
| `gpt-5` | `OpenAIModels.Chat.GPT5` |
| `gpt-5-2` | `OpenAIModels.Chat.GPT5_2` |
| `gpt-5-2-pro` | `OpenAIModels.Chat.GPT5_2Pro` |
| (bestehend) | `gpt-4o`, `gpt-4o-mini`, `gpt-4.1`, `gpt-4.1-mini`, `gpt-4.1-nano` |

Unbekannter Wert → `IllegalArgumentException` (kein stiller Fallback mehr — der aktuelle Bug, dass `gpt-5.5` lautlos zu GPT4o wird, soll dabei explizit beseitigt werden).

## REST-API

```
GET    /api/v1/agent-models
  → 200 [{ agentId, displayName, defaultTier, currentTier, isOverridden, tier: { SMALL, MEDIUM, LARGE -> modelId } }]

PUT    /api/v1/agent-models/{agentId}
  Body: { "tier": "SMALL" | "MEDIUM" | "LARGE" }
  → 204 No Content
  → 400 wenn Tier-Enum invalid

DELETE /api/v1/agent-models/{agentId}
  → 204 No Content (löscht Override → Fallback auf Default)
```

## Frontend

Neue Route `/agent-models` (gleiches Layout-Muster wie `/prompts`):

- Liste aller Agents links (gruppiert wie in `/prompts`).
- Detail rechts: drei Radio-Buttons (`SMALL` / `MEDIUM` / `LARGE`) mit zugehöriger Modell-ID als Beschriftung („MEDIUM — gpt-5-mini").
- `Override`-Badge, wenn `currentTier != defaultTier`.
- `Save`-Button → `PUT`. `Reset`-Button → `DELETE` (mit `window.confirm`).
- Eintrag im `AppShell`-Rail nach `/prompts` (Icon: `Cpu` aus lucide-react).

## Betroffene Dateien

**Backend (neu):**

- `agent/AgentModelTier.kt`
- `agent/AgentModelRegistry.kt`
- `agent/AgentModelService.kt`
- `api/AgentModelController.kt`
- `src/test/.../agent/AgentModelServiceTest.kt`
- `src/test/.../api/AgentModelControllerTest.kt`
- `src/test/.../agent/KoogAgentRunnerTest.kt` (neu, aktuell ungetestet)

**Backend (geändert):**

- `agent/KoogAgentRunner.kt` — Signatur `run(agentId, systemPrompt, userMessage)`, Modell-Auflösung via Service.
- `agent/IdeaToSpecAgent.kt`, `DecisionAgent.kt`, `FeatureProposalAgent.kt`, `PlanGeneratorAgent.kt` — übergeben `agentId`.
- `src/main/resources/application.yml` — `agent.model` raus, `agent.models.{tiers,defaults}` rein.
- Bestehende Agent-Tests: passen Stub-Aufrufe an neue Signatur an.

**Frontend (neu):**

- `frontend/app/agent-models/page.tsx`
- `frontend/components/agent-models/AgentModelList.tsx`
- `frontend/components/agent-models/AgentModelDetail.tsx`
- `frontend/lib/api/agent-models.ts` (oder Erweiterung von `lib/api.ts`)

**Frontend (geändert):**

- `frontend/components/app-shell/AppShell.tsx` — neuen Rail-Eintrag.

## Akzeptanzkriterien

1. `application.yml` enthält `agent.models.tiers` (SMALL/MEDIUM/LARGE → Koog-Modell-ID) und `agent.models.defaults` (4 Agents → Tier).
2. `agent.model`-Property ist entfernt; `KoogAgentRunner` nutzt `AgentModelService` zur Auflösung.
3. `KoogAgentRunner.resolveModel(name)` kennt mindestens `gpt-5-nano`, `gpt-5-mini`, `gpt-5`, `gpt-5-2`. Unbekannter Name → `IllegalArgumentException`.
4. `GET /api/v1/agent-models` listet 4 Agents mit `defaultTier`, `currentTier`, `isOverridden` und Tier→Modell-Mapping.
5. `PUT /api/v1/agent-models/{agentId}` mit gültigem Tier → 204; persistiert nach S3, aktualisiert Cache; nächste Agent-Ausführung nutzt das neue Modell.
6. `PUT` mit ungültigem Tier (z. B. `"XL"`) → 400.
7. `DELETE /api/v1/agent-models/{agentId}` → 204; `GET` liefert wieder `currentTier = defaultTier` und `isOverridden = false`.
8. Agent-Side-Effects bleiben funktional: alle 4 Agents werden mit `agentId` aufgerufen, alle bestehenden Tests grün.
9. Frontend-Route `/agent-models` zeigt Liste + Detail; Save/Reset funktionieren End-to-End gegen das Backend.
10. AppShell-Rail enthält neuen Eintrag mit erkennbarem Icon.

## Out of Scope (V1)

- Pro-Projekt-Override (alle Projekte teilen dieselben Selections).
- Andere Provider als OpenAI.
- Edit-History / Audit-Log.
- Pro-Tier konfigurierbare `temperature` / `reasoningEffort` (bleibt Default).
- Multi-Replica-Cache-Invalidation (Limitation aus Feature 37 wird übernommen).

## Offene Fragen — vom Brainstorming zu klären

- Sollen Tier-Mappings runtime-änderbar sein (eigener Endpoint), oder nur via `application.yml` + Restart? V1-Vorschlag: nur YAML.
- Bevorzugte Default-Zuordnung der 4 Agents zu den 3 Stufen — ist die obige Heuristik (Decision/Proposal = MEDIUM, Idea/Plan = LARGE) okay?
- Soll der Tier-Override pro Projekt zugänglich sein (z. B. „dieses Projekt mit kleinem Modell durchspielen")? Vorschlag V1: Nein — globale Settings reichen.
