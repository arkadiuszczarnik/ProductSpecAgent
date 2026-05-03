# Feature 37 — Editable Agent Prompts

**Phase:** Tooling / Admin
**Abhängig von:** Feature 31 (Project Storage S3, done), Feature 33 (Asset-Bundle-Storage als Referenz-Pattern, done)
**Aufwand:** M
**Design-Spec:** [`docs/superpowers/specs/2026-05-02-editable-agent-prompts-design.md`](../superpowers/specs/2026-05-02-editable-agent-prompts-design.md)

## Problem

Die System-Prompts der KI-Agents sind heute hartkodiert: teils in `application.yml` (`agent.system-prompt`), teils als Kotlin-`val`/`const`-Konstanten in den Agent-Klassen (`IdeaToSpecAgent`, `DecisionAgent`, `PlanGeneratorAgent`, `FeatureProposalAgent`). Änderungen an diesen Prompts erfordern einen Code-Deploy. Domain-Experten und Product Owner können das LLM-Verhalten nicht eigenständig nachjustieren. Außerdem sind drei der sechs System-Prompts noch in Englisch, obwohl die App durchgängig deutsch geführt wird.

## Ziel

Die sechs reinen System-Prompts werden in den S3/MinIO-Bucket ausgelagert und über eine eigene Admin-Sektion `/prompts` im Frontend editierbar gemacht — nach demselben Bauschema wie Feature 33/34 (Asset-Bundles). Die englischen Prompts werden ins Deutsche übersetzt und als Default-Resource versionsverwaltet im Repo abgelegt. Der Read-Pfad fällt bei fehlendem S3-Eintrag auf den Default zurück, ein Reset-Knopf entfernt die Override-Datei und stellt den Default wieder her.

User-Prompt-Templates (die Argumente, die `DecisionAgent` und `PlanGeneratorAgent` per `buildString` zur Laufzeit bauen) sind in dieser Iteration **nicht** im Scope — sie mischen statischen Text mit dynamischer Kontext-Injektion, was eine Template-Engine erfordert.

## Architektur

Siehe Design-Spec für das vollständige Bild. Kurzfassung:

- **Neu (Backend):** `PromptRegistry` (Spring `@Component`, hält das fixe Set von 6 `PromptDefinition`s mit Validatoren), `PromptService` (Read mit S3-First/Resource-Fallback und unbegrenztem In-Memory-Cache, Write mit S3-PUT + Cache-Update, Reset mit S3-DELETE + Cache-Eviction), `PromptController` (REST `/api/v1/prompts/...`), `PromptValidator` (sealed class: NotBlank, MaxLength, RequiresAll).
- **Neu (Resources):** `backend/src/main/resources/prompts/{idea-base,idea-marker-reminder,idea-step-IDEA,decision-system,plan-system,feature-proposal-system}.md` — die übersetzten / kopierten Default-Texte.
- **Refactoring:** Die vier Agents bekommen `PromptService` injiziert und ersetzen ihre Inline-Konstanten / `@Value`-Felder durch `promptService.get(id)`. Die `agent.system-prompt`-Config aus `application.yml` wird gelöscht.
- **Neu (Frontend):** Route `/prompts` mit `PromptList` (gruppiert nach Agent, mit Override-Badge), `PromptDetail` (Title + Beschreibung + CodeMirror-Editor mit Markdown-Highlighting + Save/Reset). API-Calls in `lib/api.ts`.
- **Cache-Strategie:** unbegrenzter `ConcurrentHashMap`-Cache, explizite Invalidation beim Edit/Reset-Endpoint. Edits werden sofort bei der nächsten Agent-Anfrage wirksam, ohne wiederkehrendes S3-GET pro Aufruf.

## Datenmodell

Keine Änderungen am Domain-Modell der Workspace-Funktionen. Neu eingeführt:

```kotlin
data class PromptDefinition(
    val id: String,                       // "idea-base", "decision-system", ...
    val title: String,                    // "IdeaToSpec — Basis-System-Prompt"
    val description: String,              // wann/wo der Prompt verwendet wird
    val agent: String,                    // "IdeaToSpec", "Decision", "Plan", "FeatureProposal"
    val resourcePath: String,             // "/prompts/idea-base.md"
    val validators: List<PromptValidator>,
)

sealed class PromptValidator {
    abstract fun validate(content: String): List<String>
    data object NotBlank : PromptValidator()
    data class MaxLength(val max: Int) : PromptValidator()
    data class RequiresAll(val tokens: List<String>, val reason: String) : PromptValidator()
}
```

S3-Layout: `prompts/<id>.md` (z.B. `prompts/idea-base.md`). Override-Existenz = Edit durch Admin. Default = nicht in S3.

## Service-Schnittstellen

```kotlin
interface PromptService {
    fun get(id: String): String                      // Cache → S3 → Resource
    fun list(): List<PromptListItem>                  // alle Definitions + isOverridden
    fun put(id: String, content: String): Unit       // validiert; wirft PromptValidationException
    fun reset(id: String): Unit                       // S3-DELETE + cache.remove
}

data class PromptListItem(
    val id: String,
    val title: String,
    val description: String,
    val agent: String,
    val isOverridden: Boolean,
)
```

REST:

| Method | Path | Body / Return |
|---|---|---|
| `GET` | `/api/v1/prompts` | `[PromptListItem, ...]` |
| `GET` | `/api/v1/prompts/{id}` | `{id, title, description, agent, content, isOverridden, validators}` |
| `PUT` | `/api/v1/prompts/{id}` | `{content}` → 200 oder 400 mit `{errors: [...]}` |
| `DELETE` | `/api/v1/prompts/{id}` | 200 — Reset auf Default |

## Akzeptanzkriterien

1. Backend stellt unter `/api/v1/prompts` die Liste der 6 Prompts bereit; jeder Eintrag hat `agent`, `title`, `description`, `isOverridden`.
2. `GET /api/v1/prompts/{id}` ohne S3-Override liefert den Default aus `resources/prompts/<id>.md`; `isOverridden=false`.
3. `PUT /api/v1/prompts/{id}` mit gültigem Content schreibt nach S3, aktualisiert den Cache, und der nächste Agent-Aufruf nutzt den neuen Content.
4. `PUT` mit ungültigem Content (z.B. leer, oder `idea-marker-reminder` ohne `[STEP_COMPLETE]`) wird mit 400 abgelehnt; Body listet alle Fehlermeldungen; S3 bleibt unverändert; Cache bleibt unverändert.
5. `DELETE /api/v1/prompts/{id}` entfernt den S3-Eintrag, evictet den Cache; nächster `GET` liefert wieder den Default; `isOverridden=false`.
6. Die vier Agents (`IdeaToSpecAgent`, `DecisionAgent`, `PlanGeneratorAgent`, `FeatureProposalAgent`) lesen ihre Prompts ausschliesslich über `PromptService.get(id)` — keine Inline-Konstanten mehr für die sechs V1-Prompts; `application.yml` enthält kein `agent.system-prompt` mehr.
7. Defaults für `idea-base`, `decision-system`, `plan-system`, `feature-proposal-system` sind ins Deutsche übersetzt; `idea-marker-reminder` und `idea-step-IDEA` sind 1:1 aus dem bestehenden deutschen Code übernommen. Marker-Token (`[STEP_COMPLETE]`, `[DECISION_NEEDED]`, `[CLARIFICATION_NEEDED]`) bleiben unverändert auf Englisch in den Prompts (Parser-Kompatibilität).
8. Frontend-Route `/prompts` zeigt die Liste gruppiert nach Agent. Klick auf Eintrag öffnet `PromptDetail` rechts mit Title, Description und einem CodeMirror-Editor mit Markdown-Highlighting (Dark-Theme).
9. Editor-Save → Server-Validierung → bei Fehler werden die Server-Meldungen oberhalb des Editors als rotes Banner gezeigt; bei Erfolg verschwindet das Banner und die Liste zeigt das `●`-Override-Badge.
10. Reset-Button öffnet einen Confirm-Dialog (`window.confirm`, konsistent mit Feature 36); bei Bestätigung verschwindet das Override-Badge und der Default-Inhalt wird neu geladen.
11. Wechsel des selektierten Listen-Eintrags bei Dirty-State zeigt `window.confirm("Änderungen verwerfen?")`.
12. Bestehende Agent-Tests laufen weiter — `PromptService` ist mockbar, die Agent-Tests injizieren einen Stub, der die alten Default-Texte zurückgibt.

## Betroffene Dateien

**Backend (neu):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/api/PromptController.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptService.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptValidator.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/Prompt.kt` (Datenklassen `PromptListItem`, `PromptDetail`, Request-/Response-DTOs)
- `backend/src/main/resources/prompts/{idea-base,idea-marker-reminder,idea-step-IDEA,decision-system,plan-system,feature-proposal-system}.md`
- `backend/src/test/kotlin/.../service/PromptValidatorTest.kt`
- `backend/src/test/kotlin/.../service/PromptServiceTest.kt`
- `backend/src/test/kotlin/.../api/PromptControllerTest.kt`

**Backend (geändert):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt` — `@Value` weg, `PromptService` injiziert, `IDEA_STEP_PROMPT` und `MARKER_REMINDER` als `@Deprecated` markiert oder entfernt; alle Lesepfade auf `promptService.get(...)`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/DecisionAgent.kt` — Inline-System-Prompt durch `promptService.get("decision-system")` ersetzt
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgent.kt` — Inline-System-Prompt durch `promptService.get("plan-system")` ersetzt
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgent.kt` — `SYSTEM_PROMPT` durch `promptService.get("feature-proposal-system")` ersetzt
- `backend/src/main/resources/application.yml` — `agent.system-prompt` entfernt
- Bestehende Agent-Tests: `PromptService`-Stub injizieren

**Frontend (neu):**
- `frontend/src/app/prompts/page.tsx`
- `frontend/src/components/prompts/PromptList.tsx`
- `frontend/src/components/prompts/PromptDetail.tsx`
- `frontend/src/components/prompts/ResetPromptDialog.tsx`
- `frontend/package.json` (neue Dependencies: `@uiw/react-codemirror`, `@codemirror/lang-markdown`, `@codemirror/language-data`, `@uiw/codemirror-theme-basic`)

**Frontend (geändert):**
- `frontend/src/lib/api.ts` — neue Types und Endpoint-Wrapper `listPrompts`, `getPrompt`, `savePrompt`, `resetPrompt`
- `frontend/src/components/layout/AppShell.tsx` — neuer Icon-Eintrag in der Rail (z.B. `MessageSquareText`-Icon mit Tooltip "Prompts")

## YAGNI

- Keine Versionierung / kein Edit-History (nur Reset auf Default als "Rollback")
- Keine Diff-Anzeige Default vs. Override
- Keine Mehrsprachigkeit im Editor (DE-Default + S3-Override genügt)
- Kein Add/Delete neuer Prompt-IDs durch User (nur Code-getrieben)
- Keine Auth/Permissions (konsistent mit `/asset-bundles` aktuell)
- Keine Multi-Replica-Cache-Invalidation (Backend läuft single-replica; bei Skalierung später Pub/Sub-Invalidate ergänzen)
- Keine User-Prompt-Templates (`DecisionAgent.buildString`, `PlanGeneratorAgent.buildString`) — diese mischen Anweisung mit dynamischer Kontext-Injektion und benötigen eine Template-Engine, V1-out-of-Scope
