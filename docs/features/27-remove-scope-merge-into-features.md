# Feature 27: SCOPE-Step entfernen, FEATURES vor MVP

## Zusammenfassung

Der SCOPE-Step wird aus dem Wizard-Flow entfernt. Die FEATURES-Step bildet den Scope implizit ab: Was als Feature definiert ist, ist in-scope; was nicht definiert ist, ist out-of-scope. Gleichzeitig wird FEATURES im Flow vor MVP verschoben, damit MVP seine Auswahl aus den bereits definierten Features treffen kann (Chip-Select über Feature-IDs, Anzeige per Title-Lookup).

Neuer Flow: `IDEA → PROBLEM → FEATURES → MVP → ARCHITECTURE → BACKEND → FRONTEND` (7 Steps).

## User Stories

1. Als PO möchte ich Scope-Informationen nicht doppelt pflegen müssen, weil meine Feature-Liste den Scope bereits definiert.
2. Als PO möchte ich mein MVP aus meinen definierten Features zusammenstellen, nicht aus losen Scope-Tags.
3. Als PO möchte ich, dass bestehende Projekte nach dem Update ohne Fehler laden.

## Acceptance Criteria

- [ ] `FlowStepType`-Enum enthält kein `SCOPE` mehr; Reihenfolge: `IDEA, PROBLEM, FEATURES, MVP, ARCHITECTURE, BACKEND, FRONTEND`.
- [ ] Initialer `FlowState` hat 7 Steps.
- [ ] `ScopeForm.tsx` gelöscht; `FORM_MAP` ohne `SCOPE`.
- [ ] `MvpForm` liest Features aus `data.steps["FEATURES"].fields["features"]` und zeigt sie als Chips.
- [ ] `mvpFeatures` speichert Feature-IDs (`string[]`), nicht Titel.
- [ ] Agent-Prompt für SCOPE ist entfernt; kein Step-Branch „SCOPE" in `IdeaToSpecAgent`.
- [ ] `application.yml` System-Prompt-Step-Liste auf 7 Einträge; FEATURES vor MVP.
- [ ] `docs/architecture/overview.md.mustache` ohne `## Scope`-Abschnitt.
- [ ] 10 Backend-Tests angepasst; alle grün.
- [ ] Frontend-Lint-Baseline nicht regressed.
- [ ] Bestehende 3 Projekte in `backend/data/projects/` migriert — keine `SerializationException` beim Laden.
- [ ] Manuell im Browser verifiziert: alte und neue Projekte laden, Wizard-Navigation funktioniert, MVP zeigt Feature-Chips.

## Technische Details

### Backend

**`FlowStepType`-Enum**
```kotlin
enum class FlowStepType {
    IDEA, PROBLEM, FEATURES, MVP, ARCHITECTURE, BACKEND, FRONTEND
}
```

**`IdeaToSpecAgent.buildWizardStepFeedbackPrompt`**
- SCOPE-Step-Branch (ca. Zeilen 315–329) entfernen.
- In IDEA-Prompt (Z. 285) und System-Prompt-Appendix (Z. 457): `(PROBLEM, SCOPE, etc.)` → `(PROBLEM, FEATURES, etc.)`.

**`application.yml` (main + test)**
Step-Liste aktualisieren:
```
1. IDEA - The user's initial idea
2. PROBLEM - Clarify the core problem and its primary audience
3. FEATURES - Define the feature set
4. MVP - Define the minimum viable product (selected from features)
5. ARCHITECTURE - ...
6. BACKEND - ...
7. FRONTEND - ...
```

**`ScaffoldContextBuilder.kt` / `SpecContextBuilder.kt`**
- `scopeContent` und `scope.md`-Reads entfernen.

**Mustache-Template `overview.md.mustache`**
- `## Scope`-Abschnitt mit `{{#scopeContent}}` entfernen.

### Frontend

**`api.ts` — `StepType`**
```ts
export type StepType = "IDEA" | "PROBLEM" | "FEATURES" | "MVP" | "ARCHITECTURE" | "BACKEND" | "FRONTEND";
```

**`wizard-store.ts` — `WIZARD_STEPS`**
- SCOPE-Eintrag entfernen, FEATURES vor MVP.

**`category-step-config.ts`**
- `ALL_STEP_KEYS` und `BASE_STEPS` ohne SCOPE, Reihenfolge FEATURES→MVP.

**`step-field-labels.ts`**
- SCOPE-Block aus `STEP_FIELD_LABELS` löschen.
- Step-Name-Map im `formatStepFields()` ohne SCOPE.
- `SCOPE_FIELD_LABELS` / `SCOPE_FIELDS_BY_SCOPE` **bleiben** (gehören zu FeatureScope, nicht zum SCOPE-Step).

**`WizardForm.tsx`**
- `ScopeForm`-Import und `SCOPE: ScopeForm`-Mapping entfernen.

**`MvpForm.tsx`** (zentrale Umstrukturierung)
- SCOPE-Lookup durch FEATURES-Lookup ersetzen.
- Chip-Select mit `{label: feature.title, value: feature.id}`.
- Speicherung: Feature-IDs in `mvpFeatures`.
- Anzeige: Title-Lookup beim Rendern.
- Falls `ChipSelect` nur `string[]` unterstützt: API minimal erweitern (`{label, value}[]`).

**`ScopeForm.tsx`**
- Datei löschen.

**`spec-flow/editor.ts`**
- SCOPE-Eintrag aus `STEPS` entfernen, Reihenfolge FEATURES→MVP.

**`e2e/fixtures/seed-project.ts`** (falls existent)
- SCOPE-Seed entfernen; MVP-Seed auf Feature-IDs umstellen.

### Datenmigration (bestehende Projekte)

Als Teil desselben Branches, eigener Commit:

- **Alle 3 `flow-state.json`**: SCOPE-Step-Eintrag entfernen.
- **2 `wizard.json`** (`ba4017dc-...`, `d052d24e-...`): SCOPE-Block entfernen.
- **1 Clarification** (`ba4017dc/clarifications/9a632294-...`): `stepType: "SCOPE"` → `"FEATURES"`.
- **2 Tasks** (`ba4017dc/tasks/05cae415-...`, `c9aff265-...`): `specSection: "SCOPE"` → `"FEATURES"`.
- **2 `spec/scope.md`**: löschen.
- **2 `docs/architecture/overview.md`**: `## Scope`-Abschnitt entfernen.

### Abwärtskompatibilität

- Keine. Bestehende Daten werden migriert, nicht getoleriert. Die Bereinigung ist Teil des Feature-Branches — jeder Checkout ist in sich konsistent.

## Abhängigkeiten

- Feature 11 (Guided Wizard Forms)
- Feature 22 (Features-Graph Wizard) — liefert die Feature-Liste, aus der MVP wählt
- Feature 26 (Merge PROBLEM + TARGET_AUDIENCE) — gleiche Refactor-Mechanik

## Aufwand

M (Medium) — mechanisch ähnlich zu Feature 26, plus MvpForm-Umbau und Daten-Cleanup.
