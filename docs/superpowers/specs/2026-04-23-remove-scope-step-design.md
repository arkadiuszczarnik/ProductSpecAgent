# Design: SCOPE-Step entfernen und FEATURES vor MVP verschieben

**Datum:** 2026-04-23
**Feature:** 27
**Status:** Spec — genehmigt, Plan folgt

## Kontext & Motivation

Der SCOPE-Step im Wizard-Flow (`IDEA → PROBLEM → SCOPE → MVP → FEATURES → ARCHITECTURE → BACKEND → FRONTEND`) ist redundant: Er fragt nach `inScope` / `outOfScope`-Listen, aber die FEATURES-Step erfasst bereits alle konkreten Features des Produkts. Was nicht in der Feature-Liste steht, ist implizit out-of-scope. Nutzer pflegen Scope-Tags und Feature-Listen doppelt — Aufwand ohne Gegenwert.

Zusätzliches Symptom: MVP-Form liest `inScope` aus SCOPE, um ein Chip-Select anzubieten („MVP Features (aus Scope)"). Die Chip-Auswahl sollte logisch auf die echte Feature-Liste verweisen, nicht auf lose Scope-Tags.

## Ziele

- `SCOPE` aus `FlowStepType`-Enum entfernen, Flow auf 7 Steps reduzieren.
- FEATURES vor MVP einordnen, damit MVP aus definierten Features wählen kann.
- MVP-Form zeigt Chips aus der Feature-Liste der FEATURES-Step, speichert Feature-IDs.
- Bestehende Projekt-Daten (3 Projekte in `backend/data/projects/`) einmalig migrieren, damit keine `SerializationException` beim Laden entsteht.

## Nicht-Ziele

- Kein Robustheits-Code im Storage-Layer (kein try/catch für unbekannte Enum-Werte).
- Keine inhaltliche Migration bestehender `inScope`/`outOfScope`-Einträge in die Feature-Liste — bestehende Projekte haben entweder Platzhalter (`["-"]`) oder schon vollwertige Feature-Einträge.
- Keine Änderung an Feature-bezogenen „Scope"-Konzepten (`FeatureScope`, `scopeFields`) — das ist die Frontend/Backend-Zuordnung pro Feature und bleibt unverändert.

## Architektur-Änderung

### Neue Step-Reihenfolge

```
IDEA → PROBLEM → FEATURES → MVP → ARCHITECTURE → BACKEND → FRONTEND
```

7 Steps statt 8. Die Reihenfolge wird weiterhin ausschließlich durch die Enum-Deklarationsreihenfolge definiert; `FlowStepType.entries` propagiert automatisch.

### MVP-Datenfluss (neu)

`MvpForm` liest `data.steps["FEATURES"].fields["features"]` (Array von `{id, title, scopes, …}`). Das Chip-Select bindet:
- `label = feature.title`
- `value = feature.id`

`mvpFeatures` in `data.steps["MVP"].fields` wird als `string[]` von Feature-IDs gespeichert. Anzeige im UI erfolgt per Title-Lookup; bei Umbenennung eines Features bleibt die MVP-Auswahl referenziell konsistent.

## Backend-Änderungen

| Datei | Änderung |
|-------|---------|
| `domain/FlowState.kt` | `SCOPE` aus Enum entfernen; Reihenfolge: `IDEA, PROBLEM, FEATURES, MVP, ARCHITECTURE, BACKEND, FRONTEND`. |
| `agent/IdeaToSpecAgent.kt` | SCOPE-Prompt-Block (Zeilen ~315–329) löschen. Zeile 285 und 457: `"(PROBLEM, SCOPE, etc.)"` → `"(PROBLEM, FEATURES, etc.)"`. |
| `src/main/resources/application.yml` | Step-Liste im System-Prompt: SCOPE entfernen; FEATURES vor MVP; Nummerierung aktualisieren. |
| `src/test/resources/application.yml` | Spiegelung der main-yml. |
| `export/ScaffoldContextBuilder.kt` | `scopeContent`-Variable (Z. 85) und Template-Binding (Z. 97) entfernen. |
| `agent/SpecContextBuilder.kt` | `"scope.md"` aus Read-Loop (Z. 93) entfernen. |
| `src/main/resources/templates/scaffold/docs/architecture/overview.md.mustache` | `## Scope` Abschnitt mit `{{#scopeContent}}` entfernen. |

### Tests (Backend)

10 Testdateien — SCOPE-Referenzen ersetzen (durch FEATURES, außer wo Step-Reihenfolge explizit getestet wird):

- `FlowStateTest.kt` — Enum-Reihenfolge und Step-Count-Assertion (8 → 7).
- `PlanGeneratorAgentTest.kt` — `specSection: "SCOPE"` in Test-Payload auf `"FEATURES"`.
- `DecisionAgentTest.kt` — `FlowStepType.SCOPE` → `FlowStepType.FEATURES` in 2 Zeilen.
- `DecisionStorageTest.kt`, `DecisionModelsTest.kt`, `DecisionControllerTest.kt` (2 Stellen), `ClarificationStorageTest.kt`, `ClarificationControllerTest.kt`, `ClarificationModelsTest.kt`, `ConsistencyCheckServiceTest.kt` (2 Stellen) — jeweils `SCOPE` → `FEATURES`.

## Frontend-Änderungen

| Datei | Änderung |
|-------|---------|
| `lib/api.ts:25` | `StepType`-Union ohne `"SCOPE"`, FEATURES vor MVP. |
| `lib/stores/wizard-store.ts` | `WIZARD_STEPS`: SCOPE raus, FEATURES vor MVP. |
| `lib/category-step-config.ts` | `ALL_STEP_KEYS` und `BASE_STEPS` analog. |
| `lib/step-field-labels.ts` | SCOPE-Block in `STEP_FIELD_LABELS` löschen; Step-Name-Map im `formatStepFields()` ohne SCOPE. `SCOPE_FIELD_LABELS` / `SCOPE_FIELDS_BY_SCOPE` bleiben (gehören zu FeatureScope). |
| `components/wizard/WizardForm.tsx` | `ScopeForm`-Import + `FORM_MAP`-Eintrag entfernen. |
| `components/wizard/steps/ScopeForm.tsx` | Datei löschen. |
| `components/wizard/steps/MvpForm.tsx` | Lookup umstellen: `data.steps["FEATURES"].fields["features"]`. Chip-Select auf `{label: title, value: id}`. Speichert IDs. |
| `components/spec-flow/editor.ts:28` | SCOPE-Eintrag aus `STEPS`; FEATURES vor MVP. |
| `components/wizard/ChipSelect.tsx` (falls nötig) | Falls aktuell nur `string[]`-Optionen: kleine API-Erweiterung auf `{label, value}`-Paare. Beim Implementieren verifizieren. |
| `frontend/e2e/fixtures/seed-project.ts` (falls existent) | SCOPE-Seed entfernen; MVP-Fixture auf Feature-IDs umstellen. |

## Datenmigration (bestehende Projekte)

Einmalige Bereinigung in `backend/data/projects/` — Teil desselben Branches, eigener Commit.

| Projekt | Aktion |
|---------|--------|
| `6bbd692e-.../flow-state.json` | SCOPE-Step-Eintrag entfernen. |
| `ba4017dc-.../flow-state.json` | SCOPE-Step-Eintrag entfernen. |
| `d052d24e-.../flow-state.json` | SCOPE-Step-Eintrag entfernen. |
| `ba4017dc-.../wizard.json` | SCOPE-Block entfernen. |
| `d052d24e-.../wizard.json` | SCOPE-Block entfernen. |
| `ba4017dc-.../clarifications/9a632294-....json` | `stepType: "SCOPE"` → `"FEATURES"`. |
| `ba4017dc-.../tasks/05cae415-....json` | `specSection: "SCOPE"` → `"FEATURES"`. |
| `ba4017dc-.../tasks/c9aff265-....json` | `specSection: "SCOPE"` → `"FEATURES"`. |
| `ba4017dc-.../spec/scope.md` | löschen. |
| `d052d24e-.../spec/scope.md` | löschen. |
| `ba4017dc-.../docs/architecture/overview.md` | `## Scope`-Abschnitt entfernen. |
| `d052d24e-.../docs/architecture/overview.md` | `## Scope`-Abschnitt entfernen. |

Die gespeicherte Step-Reihenfolge in `flow-state.json` wird nicht aktiv umsortiert. Der Code navigiert über die Enum-Reihenfolge; die Array-Reihenfolge in der Datei ist irrelevant.

## Commit-Sequenz

1. `feat(backend): remove SCOPE wizard step, reorder FEATURES before MVP` — Enum, Agent-Prompts, yml, Scaffold-Builder, Mustache.
2. `test(backend): update tests for 7-step flow without SCOPE` — 10 Testdateien.
3. `feat(frontend): remove SCOPE wizard step and route MVP to FEATURES` — api.ts, Stores, Configs, Forms (inkl. MvpForm-Umbau und ScopeForm-Delete), Spec-Flow-Editor, ggf. ChipSelect.
4. `chore(data): migrate existing projects to 7-step flow` — alle Datenänderungen.
5. *(optional)* `test(frontend): update e2e seed fixture for 7-step flow` — falls Fixture existiert und SCOPE referenziert.

## Verifikation

- `./gradlew test` nach Commit 2 → alle Tests grün.
- `npm run lint && npm run build` nach Commit 3 → keine Fehler, Lint-Baseline nicht regressed.
- Manueller Smoke-Test mit allen 3 Altprojekten: kein `SerializationException` beim Laden, Wizard navigierbar.
- Neues Projekt anlegen: 7-Step-Flow, FEATURES vor MVP, MVP-Form zeigt leere Feature-Chips bis Features angelegt sind.

## Acceptance Criteria

- [ ] `FlowStepType`-Enum hat 7 Werte in Reihenfolge `IDEA, PROBLEM, FEATURES, MVP, ARCHITECTURE, BACKEND, FRONTEND`.
- [ ] Keine `SCOPE`-Referenzen mehr in Kotlin-Quellcode (ausgenommen kommentar-/freitextartige „Scope"-Erwähnungen und `FeatureScope`-Kontext).
- [ ] Keine `SCOPE`-Referenzen mehr in Frontend-Quellcode (ausgenommen `FeatureScope`-Kontext).
- [ ] Alle Backend-Tests grün; Frontend-Build fehlerfrei.
- [ ] Bestehende Projekte laden ohne Exception.
- [ ] MvpForm zeigt Features aus `FEATURES.fields["features"]`, speichert `mvpFeatures` als Feature-ID-Liste.
- [ ] Keine `spec/scope.md` und kein `## Scope`-Abschnitt in generierten Docs für neue Projekte.
- [ ] Kein `scopeContent`-Binding im Mustache-Template.

## Risiken & Mitigation

- **ChipSelect unterstützt evtl. nur `string[]`.** Beim Implementieren verifizieren. Falls ja: minimale API-Erweiterung mit Rückwärtskompatibilität (Alt-Aufrufer übergeben weiterhin `string[]`, neue Aufrufer `{label, value}[]`).
- **E2E-Seed-Fixture.** Feature 26 hatte den Nachzieher — hier proaktiv prüfen, ob `frontend/e2e/fixtures/seed-project.ts` existiert und SCOPE enthält.
- **Manuelles Debug-Skript `scripts/test-agent-flow.sh`.** Laut Feature-26-Doc „bekannter Legacy-Rückstand". Kein Blocker, aber im Rahmen der Feature-27-Änderung konsistent halten (SCOPE-Abschnitt entfernen, falls vorhanden).

## Abhängigkeiten

- Feature 11 (Guided Wizard Forms) — Wizard-Grundstruktur.
- Feature 22 (Features-Graph Wizard) — Features-Step liefert die Feature-Liste, auf die MVP referenziert.
- Feature 26 (Merge PROBLEM + TARGET_AUDIENCE) — gleiches Muster, gleiche Stolpersteine.
