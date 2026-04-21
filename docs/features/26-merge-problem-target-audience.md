# Feature 26: Wizard-Steps PROBLEM und TARGET_AUDIENCE zusammenlegen

## Zusammenfassung
Die beiden Wizard-Steps `PROBLEM` und `TARGET_AUDIENCE` werden zu einem Step zusammengeführt. Der enum-Wert `PROBLEM` bleibt als Kennung erhalten, `TARGET_AUDIENCE` wird entfernt. Der neue Step heißt im UI „Problem & Zielgruppe" und enthält nur noch drei Felder: `coreProblem`, `primaryAudience`, `painPoints`. Der Wizard-Flow verkürzt sich von 9 auf 8 Steps.

## User Stories
1. Als PO möchte ich Problem und Zielgruppe in einem einzigen Wizard-Schritt ausfüllen, damit ich nicht zwischen zwei eng verknüpften Themen hin- und herwechseln muss.
2. Als PO möchte ich weniger Pflichtfelder durchklicken, damit der Einstieg in die Spec schneller geht.
3. Als PO möchte ich, dass der Agent in der generierten `problem.md` Problem und Zielgruppe gemeinsam dokumentiert, damit die Spec einen kohärenten Kontext-Abschnitt bekommt.

## Acceptance Criteria
- [ ] `FlowStepType`-Enum enthält **kein** `TARGET_AUDIENCE` mehr
- [ ] Initialer `FlowState` hat 8 Steps (vorher 9)
- [ ] Der UI-Step `PROBLEM` trägt das Label „Problem & Zielgruppe"
- [ ] `ProblemForm` zeigt genau drei Felder: Kernproblem, Primäre Zielgruppe, Pain Points
- [ ] `TargetAudienceForm.tsx` ist gelöscht; `WizardForm.FORM_MAP` enthält keinen `TARGET_AUDIENCE`-Eintrag
- [ ] `step-field-labels.ts` PROBLEM-Block: `coreProblem`, `primaryAudience`, `painPoints`; kein TARGET_AUDIENCE-Block mehr
- [ ] Der Agent-Prompt für den PROBLEM-Step fordert eine Markdown-Ausgabe an, die Problem **und** Zielgruppe dokumentiert (statt nur Problem). Kein `TARGET_AUDIENCE`-Prompt-Branch mehr
- [ ] `saveSpecFile("problem.md", …)` wird beim Step-Complete weiterhin aufgerufen; `target_audience.md` wird von neuen Projekten nicht mehr angelegt
- [ ] Backend-Tests sind an den 8-Step-Flow angepasst (`FlowStateTest`, `IdeaToSpecAgentTest`, `SpecContextBuilderWizardTest`, `WizardChatControllerTest`, `ChatControllerTest`, `WizardControllerTest`, evtl. weitere)
- [ ] Frontend-Lint-Baseline unverändert
- [ ] Manuell verifiziert: neues Projekt → nach IDEA folgt ein Step „Problem & Zielgruppe" mit drei Feldern → „Weiter" advanct auf SCOPE

## Technische Details

### Backend

**`domain/FlowState.kt`** — `FlowStepType` reduziert:
```kotlin
enum class FlowStepType {
    IDEA, PROBLEM, SCOPE, MVP, FEATURES, ARCHITECTURE, BACKEND, FRONTEND
}
```
`createInitialFlowState` erzeugt entsprechend 8 Steps.

**`agent/IdeaToSpecAgent.kt`** — `TARGET_AUDIENCE`-Branch in `buildWizardStepFeedbackPrompt` entfernen. Der `PROBLEM`-Branch wird erweitert:
- Prompt beschreibt jetzt Kernproblem, Primäre Zielgruppe und Pain Points als Input
- Anweisung an den Agent: die Markdown-Ausgabe (`problem.md`) soll beide Aspekte abbilden — typischerweise in den Abschnitten „Problem", „Zielgruppe", „Pain Points"
- Der IDEA-Step-Prompt (Zeile 285 aktuell) entfernt die Erwähnung von `TARGET_AUDIENCE` aus der „spätere Steps"-Liste; gleiches für Zeile 469 (deutsche Prompt-Komponente)

**Spec-File:** `saveSpecFile` wird weiterhin mit `"problem.md"` aufgerufen. `target_audience.md` entsteht nicht mehr bei neuen Projekten. Der Scaffold-Generator (`ScaffoldContextBuilder`) liest `target_audience.md` derzeit in einen eigenen Kontext-Abschnitt — bei Abwesenheit bleibt der Abschnitt leer (siehe „Nicht im Scope").

**Betroffene Tests (mindestens):**
- `domain/FlowStateTest.kt` — erwartet 9 Steps, wird 8
- `agent/IdeaToSpecAgentTest.kt` — mehrere Tests mit `TARGET_AUDIENCE` als `nextStep` oder `currentStep`
- `agent/SpecContextBuilderWizardTest.kt` — Flow-Assertion anpassen
- `api/ChatControllerTest.kt`, `api/WizardChatControllerTest.kt`, `api/WizardControllerTest.kt` — Step-Name-Assertions
- `service/ProjectServiceTest.kt` — `assertEquals(9, flowState.steps.size)` → 8

### Frontend

**`src/lib/api.ts`** — `StepType` union ohne `TARGET_AUDIENCE`.

**`src/lib/stores/wizard-store.ts`** — `WIZARD_STEPS`:
- Eintrag `{ key: "TARGET_AUDIENCE", label: "Zielgruppe" }` entfernen
- Label des PROBLEM-Eintrags: `"Problem & Zielgruppe"`

**`src/lib/category-step-config.ts`** — `BASE_STEPS` ohne TARGET_AUDIENCE.

**`src/lib/step-field-labels.ts`** — PROBLEM-Block neu: `coreProblem: "Kernproblem"`, `primaryAudience: "Primäre Zielgruppe"`, `painPoints: "Pain Points"`. TARGET_AUDIENCE-Block gelöscht. Der Stepname-Mapping-Block (Zeile 71) bekommt nur noch `PROBLEM: "Problem & Zielgruppe"` (TARGET_AUDIENCE-Eintrag entfällt).

**`src/components/wizard/WizardForm.tsx`** — `FORM_MAP` ohne `TARGET_AUDIENCE`-Eintrag.

**`src/components/wizard/steps/ProblemForm.tsx`** — nach dem Kernproblem-Feld werden die zwei neuen FormFields eingefügt:
```tsx
<FormField label="Primäre Zielgruppe" required>
  <input value={get("primaryAudience")} onChange={(e) => set("primaryAudience", e.target.value)}
    placeholder="z.B. Product Owner, Startup-Gründer"
    className="w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
</FormField>
<FormField label="Pain Points">
  <TagInput tags={getTags("painPoints")}
    onAdd={(t) => set("painPoints", [...getTags("painPoints"), t])}
    onRemove={(t) => set("painPoints", getTags("painPoints").filter((x: string) => x !== t))}
    placeholder="Pain Point eingeben + Enter" />
</FormField>
```
`getTags`-Helper + `TagInput`-Import werden aus `TargetAudienceForm.tsx` übernommen. Die Felder `affected`, `workarounds`, `secondary` bleiben **nicht** erhalten (Feature-Scope).

**`src/components/wizard/steps/TargetAudienceForm.tsx`** — **gelöscht**.

**`src/components/spec-flow/editor.ts`** — `{ type: "TARGET_AUDIENCE", label: "Zielgruppe" }` entfernen.

### Manueller Test nach Merge
1. Neues Projekt anlegen → nach IDEA folgt direkt „Problem & Zielgruppe"
2. Drei Felder sichtbar, „Weiter" disabled bis `coreProblem` + `primaryAudience` nicht leer sind
3. Step abschließen → Agent generiert `problem.md` mit beiden Aspekten; `target_audience.md` wird nicht angelegt
4. Flow advanct auf `SCOPE`; StepIndicator zeigt 8 Steps

## Abhängigkeiten
- Features 24 und 25 sind bereits gemerged; PROBLEM und TARGET_AUDIENCE haben bereits ihre schlankere Feldliste

## Nicht im Scope
- Migration bestehender `flow-state.json`/`wizard.json`-Dateien mit `TARGET_AUDIENCE` — existierende Projekte laden nach diesem Change evtl. nicht mehr sauber. Der User nutzt die App für neue Projekte.
- `ScaffoldContextBuilder.targetAudienceContent` aus dem Scaffold-Kontext entfernen oder umbauen — wenn der Build-Pfad ohne `target_audience.md` weiterhin funktioniert (leerer Abschnitt), ist kein Cleanup nötig. Falls Tests scheitern, im Implementation-Plan aufnehmen.
- Entfernung eines bereits auf der Platte liegenden `target_audience.md` bei migrierten Projekten
- Wording-Feinschliff am neuen PROBLEM-Prompt über das Minimum hinaus

## Aufwand
M
