# Design — Editable Agent Prompts (Feature 37)

**Status:** Draft
**Datum:** 2026-05-02
**Feature-Doc:** [`docs/features/37-editable-agent-prompts.md`](../../features/37-editable-agent-prompts.md)

## Kontext

Heute leben die System-Prompts der vier KI-Agents an drei verschiedenen Stellen:

1. `application.yml` `agent.system-prompt` (Englisch, Basis-Prompt für `IdeaToSpecAgent`, ~50 Zeilen)
2. Kotlin-Konstanten in den Agent-Klassen (`IDEA_STEP_PROMPT`, `MARKER_REMINDER`, `SYSTEM_PROMPT` in `FeatureProposalAgent`)
3. Inline-Strings in `koogRunner?.run("...", prompt)`-Aufrufen (`DecisionAgent`, `PlanGeneratorAgent`)

Änderungen an diesen Prompts erfordern einen Code-Deploy. Außerdem sind 3 von 6 Prompts (`idea-base`, `decision-system`, `plan-system`, `feature-proposal-system`) noch in Englisch, obwohl die App durchgängig auf Deutsch geführt wird (UI-Texte, Wizard, `IDEA_STEP_PROMPT`, `MARKER_REMINDER`).

Der Asset-Bundles-Stack (Feature 33/34) liefert die passende Vorlage: REST-Controller + AdminService + Storage-Klasse, plus Frontend-Route mit Liste + Editor + Reset/Delete. Wir nutzen denselben Bauschein.

## Entscheidungen aus Brainstorming

| Frage | Wahl | Konsequenz |
|---|---|---|
| Granularität | **Pro Konzept** (6 Einträge) | Single-Purpose pro Prompt, kein Hybrid-Layout im UI |
| Default-Strategie | **Bundle als Resource + S3-Override** | Defaults im Git, kein Migrations-Code, "Reset to default" trivial via S3-DELETE |
| Hot-Reload | **Cache + explizite Invalidation** | `ConcurrentHashMap` ohne TTL, Edit-Endpoint flusht den Eintrag |
| Editor | **CodeMirror 6 mit Markdown-Highlighting** | `@uiw/react-codemirror` + `@codemirror/lang-markdown` |
| Validierung | **Pro-Prompt Validatoren in Registry** | NotBlank, MaxLength, RequiresAll — frontendseitig nur Server-Echo, keine Dual-Implementation |
| Sprache | **Alle Defaults DE** | EN-Prompts werden übersetzt, Marker-Tokens bleiben EN |
| User-Prompt-Templates | **YAGNI** | `buildString`-Templates bleiben Code; nur reine System-Prompts editierbar |

## Architektur-Übersicht

```
┌─────────────────────── Frontend (Next.js) ────────────────────────┐
│  /prompts          ← page.tsx                                      │
│   ├── PromptList   ← GET /api/v1/prompts                           │
│   └── PromptDetail ← GET/PUT/DELETE /api/v1/prompts/{id}           │
│       └── CodeMirror (@uiw/react-codemirror + lang-markdown)       │
└────────────────────────────────────────────────────────────────────┘
                              │ HTTP
┌─────────────────────── Backend (Spring Boot) ─────────────────────┐
│  PromptController          ← REST                                  │
│      │                                                             │
│      ▼                                                             │
│  PromptService             ← Cache (ConcurrentHashMap),            │
│      │                       Validation, Read+Write                │
│      ▼                                                             │
│  ObjectStore (S3 / MinIO)  ← prompts/<id>.md (Override)            │
│      │ fallback                                                    │
│      ▼                                                             │
│  ClassPathResource         ← /prompts/<id>.md (Default)            │
│                                                                    │
│  PromptRegistry (singleton, definiert das fixe Set + Validatoren) │
└────────────────────────────────────────────────────────────────────┘

  Agents (IdeaToSpec/Decision/Plan/FeatureProposal)
   ↳ promptService.get(id) statt Inline-Konstanten
```

## Datenmodell

### `PromptDefinition` und Validatoren

```kotlin
data class PromptDefinition(
    val id: String,
    val title: String,
    val description: String,
    val agent: String,
    val resourcePath: String,
    val validators: List<PromptValidator> = emptyList(),
)

sealed class PromptValidator {
    abstract fun validate(content: String): List<String>

    data object NotBlank : PromptValidator() {
        override fun validate(content: String): List<String> =
            if (content.isBlank()) listOf("Inhalt darf nicht leer sein.") else emptyList()
    }

    data class MaxLength(val max: Int) : PromptValidator() {
        override fun validate(content: String): List<String> =
            if (content.length > max) listOf("Maximal $max Zeichen erlaubt (aktuell ${content.length}).") else emptyList()
    }

    data class RequiresAll(val tokens: List<String>, val reason: String) : PromptValidator() {
        override fun validate(content: String): List<String> {
            val missing = tokens.filter { !content.contains(it) }
            return if (missing.isEmpty()) emptyList()
            else listOf("Fehlende Marker: ${missing.joinToString(", ")}. $reason")
        }
    }
}
```

### `PromptRegistry`

Spring `@Component`, hält die Liste fest:

```kotlin
@Component
class PromptRegistry {
    val definitions: List<PromptDefinition> = listOf(
        PromptDefinition(
            id = "idea-base",
            title = "IdeaToSpec — Basis-System-Prompt",
            description = "Rolle und Schritt-Reihenfolge des IdeaToSpec-Agents. Wird bei jedem Wizard-Schritt vor den Step-Prompt gehängt.",
            agent = "IdeaToSpec",
            resourcePath = "/prompts/idea-base.md",
            validators = listOf(
                PromptValidator.NotBlank,
                PromptValidator.MaxLength(100_000),
                PromptValidator.RequiresAll(
                    tokens = listOf("[STEP_COMPLETE]"),
                    reason = "Dieser Marker treibt die Wizard-Progression — ohne Erwähnung kann der Agent keinen Step abschließen.",
                ),
            ),
        ),
        PromptDefinition(
            id = "idea-marker-reminder",
            title = "IdeaToSpec — Marker-Erinnerung",
            description = "Wird an Decision/Clarification-Feedback-Prompts angehängt, um den Agent an die Marker-Tokens zu erinnern.",
            agent = "IdeaToSpec",
            resourcePath = "/prompts/idea-marker-reminder.md",
            validators = listOf(
                PromptValidator.NotBlank,
                PromptValidator.MaxLength(50_000),
                PromptValidator.RequiresAll(
                    tokens = listOf("[STEP_COMPLETE]", "[DECISION_NEEDED]", "[CLARIFICATION_NEEDED]"),
                    reason = "Die Erinnerung muss alle drei Marker erklären — sonst funktioniert der Marker-Parser nicht.",
                ),
            ),
        ),
        PromptDefinition(
            id = "idea-step-IDEA",
            title = "IdeaToSpec — Step IDEA",
            description = "Step-spezifische Anweisung für den IDEA-Schritt. Wird zwischen Basis-Prompt und Locale-Anweisung eingefügt.",
            agent = "IdeaToSpec",
            resourcePath = "/prompts/idea-step-IDEA.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
        PromptDefinition(
            id = "decision-system",
            title = "Decision — System-Prompt",
            description = "Rolle des Decision-Agents (strukturierte Entscheidungs-Optionen als JSON).",
            agent = "Decision",
            resourcePath = "/prompts/decision-system.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
        PromptDefinition(
            id = "plan-system",
            title = "Plan — System-Prompt",
            description = "Rolle des Plan-Generators (Epics/Stories/Tasks als JSON).",
            agent = "Plan",
            resourcePath = "/prompts/plan-system.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
        PromptDefinition(
            id = "feature-proposal-system",
            title = "Feature-Proposal — System-Prompt",
            description = "Rolle des Feature-Proposal-Agents (Feature-Graph-Vorschlag basierend auf Spec + Uploads).",
            agent = "FeatureProposal",
            resourcePath = "/prompts/feature-proposal-system.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
    )

    fun byId(id: String): PromptDefinition =
        definitions.find { it.id == id } ?: throw PromptNotFoundException(id)
}
```

### S3-Layout

Key-Format: `prompts/<id>.md` (z.B. `prompts/idea-base.md`).
Inhalt: roher UTF-8-Text. Kein Manifest, keine Metadaten — die Metadaten liegen in der Registry.

### Frontend-DTOs

```ts
interface PromptListItem {
  id: string;
  title: string;
  description: string;
  agent: string;
  isOverridden: boolean;
}

interface PromptDetail {
  id: string;
  title: string;
  description: string;
  agent: string;
  content: string;
  isOverridden: boolean;
}

interface PromptValidationError {
  errors: string[];
}
```

## Service-Schnittstellen

### `PromptService`

```kotlin
interface PromptService {
    fun get(id: String): String
    fun list(): List<PromptListItem>
    fun put(id: String, content: String)         // wirft PromptValidationException bei Fehlern
    fun reset(id: String)
}

@Service
class PromptServiceImpl(
    private val registry: PromptRegistry,
    private val objectStore: ObjectStore,
) : PromptService {
    private val cache = ConcurrentHashMap<String, String>()

    override fun get(id: String): String {
        registry.byId(id)  // wirft PromptNotFoundException für unbekannte IDs
        return cache.computeIfAbsent(id) { loadFromStoreOrResource(id) }
    }

    override fun list(): List<PromptListItem> = registry.definitions.map { def ->
        PromptListItem(
            id = def.id,
            title = def.title,
            description = def.description,
            agent = def.agent,
            isOverridden = objectStore.exists(s3Key(def.id)),
        )
    }

    override fun put(id: String, content: String) {
        val def = registry.byId(id)
        val errors = def.validators.flatMap { it.validate(content) }
        if (errors.isNotEmpty()) throw PromptValidationException(errors)

        objectStore.put(s3Key(id), content.toByteArray(Charsets.UTF_8), contentType = "text/markdown")
        cache[id] = content
    }

    override fun reset(id: String) {
        registry.byId(id)
        objectStore.delete(s3Key(id))
        cache.remove(id)
    }

    private fun loadFromStoreOrResource(id: String): String {
        val def = registry.byId(id)
        objectStore.get(s3Key(id))?.toString(Charsets.UTF_8)?.let { return it }
        return ClassPathResource(def.resourcePath).inputStream
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun s3Key(id: String) = "prompts/$id.md"
}

class PromptValidationException(val errors: List<String>) : RuntimeException(errors.joinToString("; "))
class PromptNotFoundException(id: String) : RuntimeException("Prompt not found: $id")
```

### `PromptController`

```kotlin
@RestController
@RequestMapping("/api/v1/prompts")
class PromptController(private val service: PromptService) {

    @GetMapping
    fun list(): List<PromptListItem> = service.list()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String): PromptDetail {
        val item = service.list().find { it.id == id }
            ?: throw PromptNotFoundException(id)
        return PromptDetail(
            id = item.id,
            title = item.title,
            description = item.description,
            agent = item.agent,
            content = service.get(id),
            isOverridden = item.isOverridden,
        )
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody body: UpdatePromptRequest) {
        service.put(id, body.content)
    }

    @DeleteMapping("/{id}")
    fun reset(@PathVariable id: String) {
        service.reset(id)
    }

    @ExceptionHandler(PromptValidationException::class)
    fun handleValidation(ex: PromptValidationException): ResponseEntity<PromptValidationError> =
        ResponseEntity.badRequest().body(PromptValidationError(ex.errors))

    @ExceptionHandler(PromptNotFoundException::class)
    fun handleNotFound(ex: PromptNotFoundException): ResponseEntity<Void> =
        ResponseEntity.notFound().build()
}

data class UpdatePromptRequest(val content: String)
```

## Agent-Refactoring (Read-Pfad)

### `IdeaToSpecAgent`

- `@Value("\${agent.system-prompt}") private val baseSystemPrompt: String` → `private val promptService: PromptService` (constructor inject).
- Im Body: `baseSystemPrompt` → `promptService.get("idea-base")`.
- `IDEA_STEP_PROMPT` (Z. 435) und `MARKER_REMINDER` (Z. 420) als `companion object`-Konstanten **gelöscht**; `buildStepPrompt(step)` wird zu:
  ```kotlin
  private fun buildStepPrompt(step: FlowStepType): String = when (step) {
      FlowStepType.IDEA -> promptService.get("idea-step-IDEA")
      else -> ""
  }
  ```
- `appendLine(MARKER_REMINDER)` → `appendLine(promptService.get("idea-marker-reminder"))`.

### `DecisionAgent`

- Konstruktor: `+ private val promptService: PromptService`.
- `koogRunner?.run("You are a product decision advisor...", prompt)` → `koogRunner?.run(promptService.get("decision-system"), prompt)`.

### `PlanGeneratorAgent`

- Konstruktor: `+ private val promptService: PromptService`.
- `koogRunner?.run("You are a product implementation planner...", prompt)` → `koogRunner?.run(promptService.get("plan-system"), prompt)`.

### `FeatureProposalAgent`

- Konstruktor: `+ private val promptService: PromptService`.
- `SYSTEM_PROMPT`-Konstante (Z. 54) **gelöscht**.
- Aufrufe von `SYSTEM_PROMPT` → `promptService.get("feature-proposal-system")`.

### `application.yml`

`agent.system-prompt` Block (Z. 22–55) wird gelöscht. `agent.model: gpt-5.5` bleibt.

## Default-Resources (Übersetzung)

| Resource | Aktion |
|---|---|
| `prompts/idea-base.md` | Übersetzung des aktuellen englischen Inhalts aus `application.yml`. Marker-Tokens (`[STEP_COMPLETE]`, `[DECISION_NEEDED]`, `[CLARIFICATION_NEEDED]`) bleiben unverändert. |
| `prompts/idea-marker-reminder.md` | 1:1 aus `MARKER_REMINDER` kopiert (Deutsch). |
| `prompts/idea-step-IDEA.md` | 1:1 aus `IDEA_STEP_PROMPT` kopiert (Deutsch). |
| `prompts/decision-system.md` | Übersetzung "You are a product decision advisor..." → "Du bist ein Produkt-Entscheidungs-Berater..." |
| `prompts/plan-system.md` | Übersetzung "You are a product implementation planner..." → "Du bist ein Produkt-Implementierungs-Planer..." |
| `prompts/feature-proposal-system.md` | Übersetzung des aktuellen englischen `SYSTEM_PROMPT`. |

## Frontend

### Route + Layout

`frontend/src/app/prompts/page.tsx` ist eine Server-Component, die `<PromptsClient />` wraps (mit `"use client"`-Komponente innen). Linkes Side-Layout: `PromptList`, rechtes Side-Layout: `PromptDetail` oder Empty-State.

### `PromptList`

```tsx
"use client";
// Lädt einmal beim Mount. Gruppiert nach `agent`. Klick → setSelectedId.
// Override-Badge (●) bei isOverridden=true.
```

Atomic Zustand: `selectedId: string | null`, gehoben an die Page-Wurzel oder via `useState`.

### `PromptDetail`

```tsx
"use client";
// Lädt GET /api/v1/prompts/{id} bei Änderung von selectedId.
// Lokaler Form-State `draft`, Snapshot beim Laden, Dirty via String-Compare.
// Server-Errors aus PUT-Response werden in einem roten Banner über dem Editor gezeigt.
// Save-Button auch bei Dirty=false aktiv (Idempotent), aber Reset-Button nur bei isOverridden.
```

CodeMirror-Konfiguration:

```tsx
import CodeMirror from "@uiw/react-codemirror";
import { markdown, markdownLanguage } from "@codemirror/lang-markdown";
import { languages } from "@codemirror/language-data";
import { basicDark } from "@uiw/codemirror-theme-basic";

<CodeMirror
  value={draft}
  height="60vh"
  theme={basicDark}
  extensions={[markdown({ base: markdownLanguage, codeLanguages: languages })]}
  onChange={(val) => setDraft(val)}
/>
```

### `ResetPromptDialog`

- Native `window.confirm("Prompt auf Default zurücksetzen?")` — konsistent mit Feature 36 Stil. Kein eigenes Modal nötig.

### API-Client (`lib/api.ts`)

```ts
export async function listPrompts(): Promise<PromptListItem[]> {
  return apiFetch("/api/v1/prompts");
}
export async function getPrompt(id: string): Promise<PromptDetail> {
  return apiFetch(`/api/v1/prompts/${encodeURIComponent(id)}`);
}
export async function savePrompt(id: string, content: string): Promise<void> {
  return apiFetch(`/api/v1/prompts/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify({ content }),
  });
}
export async function resetPrompt(id: string): Promise<void> {
  return apiFetch(`/api/v1/prompts/${encodeURIComponent(id)}`, { method: "DELETE" });
}
```

`apiFetch` muss 400-Errors mit JSON-Body durchreichen, damit die Validation-Errors auf der Frontend-Seite ankommen.

### AppShell-Navigation

Neuer Eintrag in der linken Icon-Rail (`components/layout/AppShell.tsx`):
- Icon: `MessageSquareText` aus `lucide-react`
- Tooltip: "Prompts"
- Route: `/prompts`
- Position: nach `/asset-bundles`

## Tests

### Backend

| Datei | Testfälle |
|---|---|
| `PromptValidatorTest` | NotBlank/MaxLength/RequiresAll mit positiven & negativen Fällen, kombinierte Validatoren liefern alle Fehler |
| `PromptServiceTest` | get fällt auf Resource zurück; get nutzt S3 wenn vorhanden; put validiert+schreibt+cached; put mit Fehler wirft Exception, S3 unverändert; reset löscht S3 + Cache; konkurrente get-Aufrufe rufen Resource nicht doppelt auf (computeIfAbsent-Verhalten) |
| `PromptControllerTest` (MockMvc) | Roundtrip List → Get → Put → Get neue Version → Delete → Get Default → 404 für unbekannte IDs → 400 mit Errors-Body bei Validation-Fehler |
| Bestehende `IdeaToSpecAgentTest` u.a. | `PromptService`-Stub statt `@Value`, sonst keine Änderung |

### Frontend

Browser-Verifikation (kein Test-Runner):
1. Navigation in der Rail → `/prompts` rendert.
2. Liste zeigt 6 Einträge, gruppiert nach Agent.
3. Klick auf Eintrag → Detail-Ansicht lädt, Editor zeigt Markdown-Highlight.
4. Edit + Speichern → Banner verschwindet, ●-Badge erscheint im Listen-Eintrag.
5. Edit `idea-marker-reminder` ohne `[STEP_COMPLETE]` → 400 → roter Banner mit Server-Fehlermeldung.
6. Reset → Confirm → Default-Inhalt zurück, ●-Badge weg.
7. Dirty + Klick auf anderen Listen-Eintrag → `window.confirm("Änderungen verwerfen?")`.
8. Editiere `idea-base`, sende Wizard-Step IDEA → Agent zeigt das geänderte Verhalten (z.B. anderes Antwort-Muster).

## Risiken & Mitigationen

| Risiko | Wahrscheinlichkeit | Mitigation |
|---|---|---|
| `application.yml`-Removal bricht produktive Deployments mit altem Code | niedrig | Deploy-Reihenfolge: erst Backend mit `PromptService`, alte `@Value`-Reads abgekoppelt, dann config wegnehmen |
| Übersetzte Defaults verschlechtern LLM-Output | mittel | Browser-Verifikation #8: Wizard-Schritt durchspielen mit gpt-5.5; bei merkbarer Regression neu kalibrieren |
| Multi-Replica-Deployment: Cache-Drift zwischen Instanzen | niedrig (heute single-replica) | YAGNI-Eintrag im Done-Doc; bei Skalierung Pub/Sub-Invalidate ergänzen |
| CodeMirror-Bundle-Größe (~250 KB) | niedrig | Nur auf `/prompts`-Route geladen; Next.js code-split greift automatisch |
| Validator `RequiresAll(["[STEP_COMPLETE]"])` blockiert legitime Texte mit Inline-Beispielen | niedrig | Marker-Token sind so spezifisch, dass false-positives praktisch ausgeschlossen sind |

## Verifikation (manuell)

Siehe Abschnitt "Browser-Verifikation" oben. Backend-Tests müssen alle grün laufen (`./gradlew test`).

## Nicht im Scope (YAGNI)

Bereits in der Feature-Doc gelistet:
- Versionierung / Edit-History
- Diff-Anzeige Default ↔ Override
- Mehrsprachigkeit im Editor
- Add/Delete neuer Prompts durch User
- Auth/Permissions
- Multi-Replica-Cache-Invalidation
- User-Prompt-Templates der `buildString`-basierten Aufrufe
