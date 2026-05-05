# Design — Akzeptanzkriterien im Feature-Edit-Modal

**Datum:** 2026-05-05
**Feature-Doc:** [`docs/features/44-feature-acceptance-criteria.md`](../../features/44-feature-acceptance-criteria.md)
**Phase:** Wizard-Erweiterung
**Abhängig von:** Feature 22 (Features-Graph-Wizard), Feature 36 (Features-Edit-Modal)

## Kontext

Im Wizard-Step **FEATURES** öffnet ein Single-Click auf einen Node das `FeatureEditDialog` (Feature 36). Das Modal hat heute ein 2-Spalten-Layout: Stammdaten links (Title, Scope-Chips, Description), Scope-Felder rechts (Frontend/Backend). Akzeptanzkriterien werden nicht erfasst.

Der `ScaffoldContextBuilder` (Doc-Generierung) rendert heute einen `## Acceptance Criteria`-Block im Feature-Doc, befüllt ihn aber mit Story-Subtasks (`ScaffoldContextBuilder.kt:67: subtasks.map { TaskContext(it.title, it.description) }`) — also Implementierungs-Tasks, nicht stakeholder-orientierte Done-Bedingungen. Das Mustache-Template (`feature.md.mustache:15-18`) erwartet bereits die Form `{ title, description }` und rendert als `- [ ] {title}: {description}`-Checkliste.

## Ziel

Im `FeatureEditDialog` lassen sich pro Feature Akzeptanzkriterien als geordnete Liste pflegen. Ein KI-Vorschlags-Button generiert 3–6 Kriterien per LLM-Agent. Die Doc-Generierung nutzt die neuen Wizard-AC als primäre Quelle, fällt für Bestandsprojekte auf Story-Subtasks zurück.

## Architektur

### Schichten

| Layer | Datei | Änderung |
|---|---|---|
| Backend Domain | `WizardFeatureGraph.kt` | Neuer Datentyp `AcceptanceCriterion(id, title, description)`; neues Feld `acceptanceCriteria: List<AcceptanceCriterion> = emptyList()` in `WizardFeature` |
| Backend Agent | `agent/AcceptanceCriteriaProposalAgent.kt` (neu) | Eigener Koog-Agent mit gleichem Pattern wie `FeatureProposalAgent`: contextBuilder + promptService + koogRunner, JSON-Output |
| Backend Prompt | `data/agent-prompts/acceptance-criteria-proposal-system.md` (neu) | System-Prompt für AC-Generierung (geladen über `PromptService`) |
| Backend API | `api/AcceptanceCriteriaProposalController.kt` (neu) | `POST /api/v1/projects/{projectId}/features/{featureId}/acceptance-criteria/propose` → liefert `List<AcceptanceCriterion>` |
| Backend Export | `export/ScaffoldContextBuilder.kt` | 3-Zeilen-Diff: Fallback-Logik (Wizard-AC > Story-Subtasks) |
| Frontend Types | `lib/api.ts` | TypeScript-Type `AcceptanceCriterion`; Feld in `WizardFeature`; Endpoint-Wrapper `proposeAcceptanceCriteria(projectId, featureId)` |
| Frontend Modal | `FeatureEditDialog.tsx` | Neue Section unter dem 2-Spalten-Grid: Liste mit Add/Remove/Reorder, „AC vorschlagen"-Button mit Loading-State |
| Frontend Modal Helper | `FeatureEditDialog.tsx` (Sub-Komponente) | `AcceptanceCriteriaList` (Datei-intern) ~80–100 Zeilen |

### Reihenfolge der Implementierung

Bottom-up, jede Stufe testbar:

1. Backend-Domain-Modell erweitern + Test, dass alte JSON-Files weiter laden
2. ScaffoldContextBuilder-Fallback + Test für beide Pfade
3. AC-Proposal-Agent + Prompt + Controller + Tests (Pattern aus `FeatureProposalAgent` kopieren)
4. Frontend-Types + API-Wrapper
5. Modal-UI: `AcceptanceCriteriaList`-Sub-Komponente + Wire-up im Dialog
6. „AC vorschlagen"-Button mit Loading/Error-States

### Boundaries (Isolation & Klarheit)

- `AcceptanceCriteriaProposalAgent` ist eigenständig — kennt nur `WizardFeature` und Project-Kontext, gibt `List<AcceptanceCriterion>` zurück. Keine Kopplung an `FeatureProposalAgent`.
- `AcceptanceCriteriaList`-Sub-Komponente bekommt `value`, `onChange`, `onPropose`, `isProposing` als Props. Kennt die `WizardFeature`-Struktur nicht.
- ScaffoldContextBuilder-Fallback ist 3 Zeilen, dokumentiert mit einem 1-Satz-Kommentar.

### Was bleibt unverändert

- Modal-Save-Pattern (Draft-Snapshot, explizites Save)
- Wizard-Persistenz (`wizard.json`, kotlinx-Serialization)
- Mustache-Template (`feature.md.mustache`) — die bestehende `acceptanceCriteria`-Schleife passt 1:1 auf den neuen Typ

## Datenmodell

### Backend (`domain/WizardFeatureGraph.kt`)

```kotlin
@Serializable
data class AcceptanceCriterion(
    val id: String,
    val title: String,
    val description: String = "",
)

@Serializable
data class WizardFeature(
    val id: String,
    val title: String,
    val scopes: Set<FeatureScope> = emptySet(),
    val description: String = "",
    val scopeFields: Map<String, String> = emptyMap(),
    val acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),  // NEU
    val position: GraphPosition = GraphPosition(),
)
```

**Begründung:**
- `id: String` (UUID): stabile React-Keys, künftige Verlinkung mit Tasks möglich
- `title: String`: Pflichtfeld, beim Speichern Trim und leere Einträge filtern
- `description: String = ""`: optional, leerer Default = „keine zusätzliche Erklärung"
- `acceptanceCriteria: List<...> = emptyList()`: Default macht alle bestehenden `wizard.json`-Files rückwärtskompatibel
- Reihenfolge via List-Order, kein zusätzliches `order`-Feld

### Frontend (`lib/api.ts`)

```ts
export interface AcceptanceCriterion {
  id: string;
  title: string;
  description: string;
}

export interface WizardFeature {
  id: string;
  title: string;
  scopes: FeatureScope[];
  description: string;
  scopeFields: Record<string, string>;
  acceptanceCriteria: AcceptanceCriterion[];   // NEU
  position: GraphPosition;
}

export async function proposeAcceptanceCriteria(
  projectId: string,
  featureId: string,
): Promise<AcceptanceCriterion[]> {
  return apiFetch<AcceptanceCriterion[]>(
    `/api/v1/projects/${projectId}/features/${featureId}/acceptance-criteria/propose`,
    { method: "POST" },
  );
}
```

### Wizard-Store (`lib/stores/wizard-store.ts`)

`updateFeature(id, patch)` ist heute generisch (`Partial<WizardFeature>`) — kein Code-Change. Das neue Feld fließt automatisch durch.

## Modal-UI

### Layout

Neue Section unter dem 2-Spalten-Grid in `FeatureEditDialog.tsx`, **vor** dem `DialogFooter`:

```
┌─ Feature bearbeiten ────────────────────────────────────────┐
│  [2-Spalten-Grid wie bisher]                                │
│  Stammdaten        │  Scope-Felder                          │
├─────────────────────────────────────────────────────────────┤
│  Akzeptanzkriterien                    [✨ AC vorschlagen]  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Title:  [User kann mit gültiger E-Mail einloggen]    │   │
│  │ Beschr.:[Dashboard wird angezeigt nach Login.]       │   │
│  │                                       [↑] [↓]  [✕]   │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Title:  [...]                                        │   │
│  └──────────────────────────────────────────────────────┘   │
│  [+ Akzeptanzkriterium hinzufügen]                          │
├─────────────────────────────────────────────────────────────┤
│  [Löschen]                          [Abbrechen][Speichern]  │
└─────────────────────────────────────────────────────────────┘
```

### Sub-Komponente `AcceptanceCriteriaList`

```tsx
interface AcceptanceCriteriaListProps {
  value: AcceptanceCriterion[];
  onChange: (next: AcceptanceCriterion[]) => void;
  onPropose: () => void;
  isProposing: boolean;
  proposeError: string | null;
}
```

State komplett kontrolliert via Props (kein eigener `useState`). Der Draft im Parent (`FeatureEditDialog`) bleibt single-source-of-truth — konsistent mit dem bestehenden Draft-Pattern.

### Verhalten im Detail

**Add (`+ Akzeptanzkriterium hinzufügen`):**
```ts
onChange([...value, { id: crypto.randomUUID(), title: "", description: "" }]);
// Nach Add: Title-Input des neuen Eintrags wird per Ref fokussiert
```

**Edit (Title/Description-Inputs):**
```ts
function patchItem(id: string, key: "title" | "description", val: string) {
  onChange(value.map(c => c.id === id ? { ...c, [key]: val } : c));
}
```

**Remove (✕):**
```ts
onChange(value.filter(c => c.id !== id));   // kein Confirm
```

**Reorder (↑ / ↓):**
```ts
function move(id: string, direction: -1 | 1) {
  const idx = value.findIndex(c => c.id === id);
  const target = idx + direction;
  if (target < 0 || target >= value.length) return;
  const next = [...value];
  [next[idx], next[target]] = [next[target], next[idx]];
  onChange(next);
}
```

**Enter im Title-Input:** fügt neue leere Zeile **darunter** ein (Insert at index+1) und fokussiert sie. Description-Textarea: Enter macht Newline (Standardverhalten).

**Pfeil-Buttons disabled-state:**
- ↑ disabled bei `idx === 0`
- ↓ disabled bei `idx === value.length - 1`
- Beide via `disabled`-Attribut + `opacity-50` + `cursor-not-allowed`

### Save-Pfad (`FeatureEditDialog.handleSave`)

Vor dem Speichern: leere Einträge filtern (Title-Trim leer):

```ts
const cleaned = draft.acceptanceCriteria
  .map(c => ({ ...c, title: c.title.trim(), description: c.description.trim() }))
  .filter(c => c.title.length > 0);

onSave({ ..., acceptanceCriteria: cleaned });
```

### Dirty-Tracking (`equalDraft`)

Bestehende `equalDraft`-Funktion erweitern um AC-Vergleich (length + index-weiser Item-Vergleich auf id/title/description). ~10 Zeilen mehr, bleibt rein.

### shadcn-Komponenten & Icons

- Verwendet vorhandene shadcn-Komponenten: `Input`, `Textarea` (`rows={2}`), `Button` (Variants `outline`/`ghost`)
- Icons aus `lucide-react`: `Plus`, `Trash2`, `ChevronUp`, `ChevronDown`, `Sparkles`, `Loader2`
- Kein neuer `npx shadcn add`-Aufruf nötig

### Größenabschätzung

- `AcceptanceCriteriaList`-Sub-Komponente: ~90 Zeilen
- `FeatureEditDialog.tsx` wächst von 227 → ca. 320 Zeilen (inkl. Sub-Komponente in derselben Datei). Falls über 350 Zeilen → in eigene Datei `AcceptanceCriteriaList.tsx` extrahieren

## „AC vorschlagen"-Button (AI-Generierung)

### Backend — Agent

`backend/src/main/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgent.kt`:

```kotlin
@Service
open class AcceptanceCriteriaProposalAgent(
    private val contextBuilder: SpecContextBuilder,
    private val wizardService: WizardService,
    private val promptService: PromptService,
    private val koogRunner: KoogAgentRunner? = null,
) {
    companion object { const val AGENT_ID = "acceptance-criteria-proposal" }

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun propose(projectId: String, featureId: String): List<AcceptanceCriterion> {
        val feature = loadFeature(projectId, featureId)
            ?: throw IllegalArgumentException("Feature $featureId not found")
        val context = contextBuilder.buildProposalContext(projectId)
        val prompt = buildString {
            appendLine("Generate concrete, testable acceptance criteria for the following feature.")
            appendLine("Each criterion must describe a stakeholder-observable Done condition (not implementation steps).")
            appendLine()
            appendLine("=== PROJECT CONTEXT ===")
            appendLine(context)
            appendLine()
            appendLine("=== FEATURE ===")
            appendLine("Title: ${feature.title}")
            if (feature.description.isNotBlank()) appendLine("Description: ${feature.description}")
            if (feature.scopes.isNotEmpty()) appendLine("Scopes: ${feature.scopes.joinToString()}")
            appendLine()
            appendLine("Respond with EXACTLY this JSON format (no markdown, no explanation):")
            appendLine("""{"criteria":[{"title":"...","description":"..."}]}""")
            appendLine("Aim for 3–6 criteria. 'description' is optional (empty string allowed).")
        }
        val raw = runAgent(prompt)
        return parseResponse(raw)
    }

    protected open suspend fun runAgent(prompt: String): String =
        koogRunner?.run(AGENT_ID, promptService.get("acceptance-criteria-proposal-system"), prompt)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")

    private fun loadFeature(projectId: String, featureId: String): WizardFeature? { /* via wizardService */ }
    private fun parseResponse(raw: String): List<AcceptanceCriterion> {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val parsed = runCatching { json.decodeFromString<ProposalResponse>(jsonStr) }
            .getOrElse { throw ProposalParseException("Invalid JSON from LLM: ${it.message}", it) }
        return parsed.criteria.map { c ->
            AcceptanceCriterion(
                id = UUID.randomUUID().toString(),
                title = c.title,
                description = c.description ?: "",
            )
        }
    }

    @Serializable private data class ProposalResponse(val criteria: List<CriterionDef> = emptyList())
    @Serializable private data class CriterionDef(val title: String, val description: String? = null)
}
```

Konsistent mit `FeatureProposalAgent`: gleiche Struktur, gleicher Test-Override-Pattern (`runAgent` ist `protected open`), gleiche JSON-Markdown-Strip-Logik.

### Backend — System-Prompt

`backend/data/agent-prompts/acceptance-criteria-proposal-system.md`:

```markdown
You generate acceptance criteria for product features.

Acceptance criteria are stakeholder-observable Done conditions, not implementation steps.
Each criterion must be:
- Specific (concrete, not vague)
- Measurable (testable from outside the system)
- Independent (no dependency on other criteria)
- User-language (no technical jargon unless the user is a developer)

Output JSON ONLY. No markdown. No explanation.
```

### Backend — Controller

```kotlin
@RestController
@RequestMapping("/api/v1/projects/{projectId}/features/{featureId}/acceptance-criteria")
class AcceptanceCriteriaProposalController(
    private val agent: AcceptanceCriteriaProposalAgent,
) {
    @PostMapping("/propose")
    fun propose(
        @PathVariable projectId: String,
        @PathVariable featureId: String,
    ): ResponseEntity<Any> = runBlocking {
        try {
            ResponseEntity.ok<Any>(agent.propose(projectId, featureId))
        } catch (e: ProposalParseException) {
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("error" to (e.message ?: "Parsing failed")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to (e.message ?: "Feature not found")))
        }
    }
}
```

### Frontend — Button-Verhalten

Im `FeatureEditDialog.tsx`:

```tsx
const [isProposing, setIsProposing] = useState(false);
const [proposeError, setProposeError] = useState<string | null>(null);

async function handlePropose() {
  if (!feature || !draft) return;
  setIsProposing(true);
  setProposeError(null);
  try {
    const proposed = await proposeAcceptanceCriteria(projectId, feature.id);
    // Append-Strategie: vorhandene AC bleiben, Vorschläge hängen unten an
    patch("acceptanceCriteria", [...draft.acceptanceCriteria, ...proposed]);
  } catch (e) {
    setProposeError(e instanceof Error ? e.message : "Vorschlag fehlgeschlagen");
  } finally {
    setIsProposing(false);
  }
}
```

**Merge-Strategie: Append**, nicht Überschreiben:
- ✅ Schützt manuell gepflegte Kriterien
- ✅ User kann unerwünschte Vorschläge per ✕ entfernen
- Vorgeschlagene Items bekommen vom Backend frische UUIDs → keine Kollision

### Frontend — Button-Darstellung

```tsx
<Button
  variant="outline"
  size="sm"
  onClick={handlePropose}
  disabled={isProposing}
  type="button"
>
  {isProposing ? <Loader2 className="animate-spin" size={14} /> : <Sparkles size={14} />}
  {isProposing ? "Generiere..." : "AC vorschlagen"}
</Button>
{proposeError && (
  <p className="text-xs text-destructive mt-1">{proposeError}</p>
)}
```

Position: rechts vom „Akzeptanzkriterien"-Header.

### `projectId`-Prop

`FeatureEditDialog` bekommt `projectId: string` als zusätzliches Prop von `FeaturesGraphEditor` (kleine Signatur-Erweiterung in `FeatureEditDialogProps`).

## Doc-Generierung (Fallback im ScaffoldContextBuilder)

### Bestehender Code (`ScaffoldContextBuilder.kt:67`)

```kotlin
acceptanceCriteria = subtasks.map { TaskContext(it.title, it.description) },
```

### Neuer Code

```kotlin
// Prefer wizard acceptance criteria (Feature 44) when available; fall back to
// story subtasks for backward compatibility with projects created before this feature.
acceptanceCriteria = wizardFeature?.acceptanceCriteria
    ?.takeIf { it.isNotEmpty() }
    ?.map { TaskContext(it.title, it.description) }
    ?: subtasks.map { TaskContext(it.title, it.description) },
```

### Verhalten

| Projekt-Zustand | `wizardFeature.acceptanceCriteria` | Resultierender Doc-Content |
|---|---|---|
| Alt, nie editiert mit neuem Modal | `[]` | Story-Subtasks (heutiges Verhalten) |
| Alt, neues Modal benutzt aber AC leer gelassen | `[]` | Story-Subtasks (heutiges Verhalten) |
| Neu, AC manuell gepflegt | `[AC1, AC2, ...]` | Wizard-AC (neues Verhalten) |
| Neu, AC per Button vorgeschlagen | `[AC1, AC2, ...]` | Wizard-AC (neues Verhalten) |
| Wizard-Feature nicht im Graph (nur EPIC ohne Wizard-Match) | `wizardFeature == null` | Story-Subtasks (Fallback) |

### Mustache-Template

`feature.md.mustache` bleibt unverändert. Der bestehende Render-Block passt 1:1:

```mustache
## Acceptance Criteria
{{#acceptanceCriteria}}
- [ ] {{title}}{{#description}}: {{description}}{{/description}}
{{/acceptanceCriteria}}
```

`AcceptanceCriterion(id, title, description)` wird in `TaskContext(title, description)` übersetzt — die `id` braucht das Template nicht.

### Auto-Regeneration

`DocsScaffoldGenerator` regeneriert auf jedes `saveSpecFile()`. Im Plan-Schritt prüfen, ob Wizard-Step-Saves diese Pipeline triggern; falls Lücke: 1-Zeilen-Hook nach `wizardService.saveStep()` oder bewusste Akzeptanz, dass die Doc erst beim nächsten Spec-Save aktuell ist.

## Tests

- **Backend (Kotlin):**
  - `AcceptanceCriteriaProposalAgentTest` — anonymous subclass, override `runAgent` mit fester JSON-Antwort, parse-Pfad inkl. `ProposalParseException` testen
  - `ScaffoldContextBuilderTest` — drei Pfade: Wizard-AC vorhanden, Wizard-AC leer, kein Wizard-Match
  - `WizardFeatureGraphTest` (oder bestehender Domain-Test) — Deserialisierung alter JSON ohne `acceptanceCriteria`-Feld
- **Frontend:** kein Test-Runner konfiguriert → manuelle Browser-Verifikation gemäß `frontend/CLAUDE.md`. Test-Plan im Implementierungs-Plan dokumentieren

## Akzeptanzkriterien (für dieses Feature selbst)

**Frontend / Modal:**
1. Im Feature-Bearbeiten-Modal erscheint unter dem 2-Spalten-Grid eine Section „Akzeptanzkriterien" mit AC-Liste, Add-Button und „AC vorschlagen"-Button.
2. „+ Akzeptanzkriterium hinzufügen" hängt einen leeren Eintrag an und fokussiert das Title-Input.
3. Title (Input) und Description (Textarea) sind inline editierbar.
4. ✕-Button entfernt einen Eintrag sofort, ohne Confirm.
5. ↑/↓-Buttons verschieben um eine Position; bei Index-Grenzen disabled.
6. Enter im Title-Input fügt einen neuen leeren Eintrag direkt darunter ein und fokussiert ihn.
7. Eingaben mutieren `draft`; Speichern erst beim Klick auf „Speichern".
8. Beim Speichern werden Einträge mit leerem Title (Trim) still gefiltert; leere Description ist erlaubt.
9. „Abbrechen"/Esc/Overlay-Klick mit gedirtyten AC zeigt den bestehenden „Änderungen verwerfen?"-Confirm.
10. Beim Wiederöffnen eines gespeicherten Features sind AC korrekt vorbefüllt (inkl. Reihenfolge).

**Backend / API:**
11. `POST /api/v1/projects/{projectId}/features/{featureId}/acceptance-criteria/propose` liefert `200 OK` mit `List<AcceptanceCriterion>` (3–6 Einträge mit UUID und mind. Title).
12. Endpoint liefert `404` bei unbekanntem `featureId`.
13. Endpoint liefert `422` bei kaputtem LLM-JSON.
14. Vorgeschlagene AC werden im Frontend an die bestehende Liste angehängt (Append-Merge).
15. Während `isProposing` ist der Button disabled mit Spinner+„Generiere…"-Label.

**Persistenz / Doc-Generierung:**
16. Bestehende `wizard.json`-Files ohne `acceptanceCriteria` laden ohne Fehler (kotlinx-Default `emptyList()`).
17. Speichern eines Features mit AC schreibt das neue Feld in `wizard.json`.
18. `ScaffoldContextBuilder` rendert Wizard-AC, wenn vorhanden — sonst Story-Subtasks.
19. `feature.md` zeigt AC im Format `- [ ] {title}: {description}` (bzw. nur `- [ ] {title}` bei leerer Description).

**Tests:**
20. Backend-Test: `AcceptanceCriteriaProposalAgent` parst valides JSON; `ProposalParseException` bei kaputtem JSON.
21. Backend-Test: `ScaffoldContextBuilderTest` deckt drei Pfade ab.
22. Manuelle Browser-Verifikation für Add/Edit/Remove/Reorder/Save/Propose-Pfade.

## Risiken & Mitigation

| Risiko | Mitigation |
|---|---|
| LLM-JSON unzuverlässig | Pattern aus `FeatureProposalAgent`: Markdown-Strip, `runCatching`, `ProposalParseException` → 422 mit Inline-Error im Frontend |
| `wizard.json` älterer Projekte ohne neues Feld | kotlinx-Default `emptyList()` + Test |
| Wizard-Step-Save triggert Doc-Regeneration nicht | Im Plan-Schritt verifizieren; falls Lücke → 1-Zeilen-Hook oder bewusste Akzeptanz |
| Modal wird mit AC-Section zu lang | `max-h-[80vh] overflow-y-auto` ist gesetzt |
| `crypto.randomUUID()` in älteren Browsern | Next.js 16 + moderne Targets — verfügbar |
| Append-Merge erzeugt Duplikate bei Mehrfachklick | Akzeptiert — User kann Dubletten via ✕ entfernen; Title-Match-Dedupe verlöre die Möglichkeit, gleichnamige Vorschläge zu bekommen |

## YAGNI

- **Drag-and-Drop-Reorder** — ↑/↓-Buttons reichen für die erwartete Listenlänge (3–8 Items)
- **Status pro Kriterium** (`draft|approved|done`) — kein User-Bedarf erwähnt; späteres Tasks-Mapping kann das via separate Tabelle abbilden
- **Given/When/Then-Sub-Felder** — frei textuelle Description ist flexibler
- **Einzelner AC-Edit-Modal** — Inline reicht
- **Bestätigungs-Dialog beim Lösch-Klick** — Save ist explizit, Abbrechen rollt zurück
- **Toast-System für Fehler** — eine Inline-Fehlerzeile reicht
- **Bulk-Generierung** (AC für alle Features) — Per-Feature-Button passt zur Modal-UX
- **Verlinkung AC ↔ Tasks** — wäre eigenes Feature
- **Mustache-Template-Refactoring**, um Story-Subtasks woanders zu rendern — Fallback-Logik macht das obsolet

## Migration

Keine Schreib-Migration. kotlinx-Serialization deserialisiert bestehende `wizard.json`-Files ohne `acceptanceCriteria`-Feld korrekt zum Default `emptyList()`. Beim ersten Save eines Features mit dem neuen Modal wird das Feld serialisiert. Test deckt das ab.
