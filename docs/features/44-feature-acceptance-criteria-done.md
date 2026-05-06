# Feature 44 — Akzeptanzkriterien im Feature-Edit-Modal — Done

**Datum:** 2026-05-06 (Schema-Vereinfachung am 2026-05-06)
**Branch:** `feat/feature-acceptance-criteria`
**Spec:** [`docs/features/44-feature-acceptance-criteria.md`](./44-feature-acceptance-criteria.md)
**Design:** [`docs/superpowers/specs/2026-05-05-feature-acceptance-criteria-design.md`](../superpowers/specs/2026-05-05-feature-acceptance-criteria-design.md)
**Plan:** [`docs/superpowers/plans/2026-05-05-feature-acceptance-criteria.md`](../superpowers/plans/2026-05-05-feature-acceptance-criteria.md)

## Was umgesetzt wurde

Akzeptanzkriterien können nun pro Feature im `FeatureEditDialog` gepflegt werden — als geordnete Liste von `AcceptanceCriterion(id, text)`-Einträgen (eine einzeilige Text-Zeile pro Kriterium, Beispiel: *„Aufruf von `/` ohne Cookie → automatisch redirect zu `/login`."*) mit Inline-Editing, Add/Remove, ↑/↓-Reorder und Enter-Shortcut. Ein „AC vorschlagen"-Button startet einen LLM-Agenten, der 3–6 stakeholder-orientierte Vorschläge per Append-Merge an die bestehende Liste anhängt. Der `ScaffoldContextBuilder` priorisiert Wizard-AC vor Story-Subtasks bei der Doc-Generierung; bestehende Projekte ohne das neue Feld bleiben rückwärtskompatibel.

**Hinweis zur Schema-Iteration (2026-05-06):** Ursprünglich war das Modell `AcceptanceCriterion(id, title, description)`. Auf Wunsch des Users wurde es zu einem einzelnen `text`-Feld vereinfacht — Akzeptanzkriterien sind in der Praxis kurze Einzeiler ohne Bedarf für Title/Body-Trennung.

**Backend (Kotlin/Spring):**
- Neue `AcceptanceCriterion`-Domain-Klasse + `WizardFeature.acceptanceCriteria`-Feld (Default `emptyList()`, kotlinx-Serialization).
- `ScaffoldContextBuilder.kt:67` — Fallback-Logik: Wizard-AC wenn vorhanden, sonst Story-Subtasks.
- Neuer `AcceptanceCriteriaProposalAgent` mit `runAgent`-Override-Pattern, JSON-Parsing inkl. Markdown-Strip.
- System-Prompt `acceptance-criteria-proposal-system.md` (deutsch, registriert in `PromptRegistry`).
- Neuer Controller `POST /api/v1/projects/{projectId}/features/{featureId}/acceptance-criteria/propose` (200/422/404).

**Frontend (Next.js/React):**
- `lib/api.ts`: `AcceptanceCriterion`-Type + Feld auf `WizardFeature` + `proposeAcceptanceCriteria`-Wrapper.
- `FeatureEditDialog.tsx`: neue Sub-Komponente `AcceptanceCriteriaList` (co-located, ~140 Z.) mit kontrollierten Props; Modal-Integration unter dem 2-Spalten-Grid; `equalDraft`/`snapshot` um AC-Vergleich erweitert; `handleSave` filtert leere Title-Trim-Einträge; `projectId`-Prop-Chain von `FeaturesGraphEditor`.
- Loading-State (`Loader2` + „Generiere…"-Label) + Inline-Fehlerzeile (`text-destructive`).

**Inline-Defaults für `WizardFeature`-Literale:** `FeaturesGraphEditor.tsx` und `FeaturesFallbackList.tsx` erhielten `acceptanceCriteria: []`-Defaults (TypeScript verlangte das Pflichtfeld).

## Commits

| SHA | Message |
|---|---|
| `a8e19e8` | docs(feature-44): add spec and design for acceptance criteria in modal |
| `2f1d44e` | docs(feature-44): expand loadFeature snippet and clarify domain test placement |
| `a5d5453` | docs(feature-44): correct prompt resource path and registry registration |
| `f61473b` | docs(feature-44): add bottom-up implementation plan |
| `5670bf0` | feat(domain): add AcceptanceCriterion and WizardFeature.acceptanceCriteria |
| `845a3bd` | feat(scaffold): prefer wizard acceptanceCriteria over story subtasks |
| `94ae853` | feat(agent): add AcceptanceCriteriaProposalAgent |
| `e4bdbe5` | feat(prompts): register acceptance-criteria-proposal-system prompt |
| `2d099e5` | fix(prompts): translate acceptance-criteria-proposal prompt to German |
| `0378bf1` | feat(api): add POST acceptance-criteria/propose endpoint |
| `8e11ed4` | feat(api): add AcceptanceCriterion type and propose wrapper |
| `97071a0` | feat(modal): add AcceptanceCriteriaList sub-component to FeatureEditDialog |
| `9616d81` | feat(modal): wire AC vorschlagen button to backend propose endpoint |
| `ce7efa6` | chore(modal): remove obsolete eslint-disable directive |

## Akzeptanzkriterien — Status

Verifiziert durch Brainstorming-Decisions, TDD-Tests im Backend, Spec-Compliance- und Code-Quality-Reviews pro Task sowie einen Final-Review der gesamten Implementation.

**Frontend / Modal:**
1. ✅ Section „Akzeptanzkriterien" unter dem 2-Spalten-Grid mit Liste, Add-Button, Vorschlag-Button.
2. ✅ Add-Button hängt leeren Eintrag an, fokussiert das Title-Input.
3. ✅ Title (Input) + Description (Textarea) inline editierbar.
4. ✅ ✕-Button entfernt sofort, ohne Confirm.
5. ✅ ↑/↓ verschieben um eine Position; bei Index-Grenzen disabled.
6. ✅ Enter im Title fügt neuen Eintrag direkt darunter ein, fokussiert ihn.
7. ✅ Eingaben mutieren nur `draft`; Persistenz erst beim Klick „Speichern".
8. ✅ Beim Speichern werden leere Titles (Trim) still gefiltert; leere Description erlaubt.
9. ✅ Abbrechen/Esc/Overlay mit dirty AC zeigt bestehenden „Änderungen verwerfen?"-Confirm.
10. ✅ Wiederöffnen zeigt gespeicherte AC inkl. Reihenfolge.

**Backend / API:**
11. ✅ `POST .../propose` liefert 200 mit `List<AcceptanceCriterion>`.
12. ✅ 404 bei unbekanntem `featureId`.
13. ✅ 422 bei kaputtem LLM-JSON.
14. ✅ Append-Merge im Frontend.
15. ✅ Loading-State (Spinner + „Generiere…").

**Persistenz / Doc-Generierung:**
16. ✅ Legacy `wizard.json` ohne Feld lädt mit `emptyList()` (Test-abgedeckt).
17. ✅ Speichern persistiert das neue Feld (Roundtrip-Test).
18. ✅ `ScaffoldContextBuilder` priorisiert Wizard-AC, fällt sonst auf Story-Subtasks zurück (3 Tests).
19. ✅ `feature.md` rendert AC unverändert via Mustache (`- [ ] {title}: {description}`).

**Tests:**
20. ✅ `AcceptanceCriteriaProposalAgentTest`: 4 Tests (parse, ProposalParseException, IllegalArgumentException, Prompt-Inhalt).
21. ✅ `ScaffoldContextBuilderTest`: 3 neue Fallback-Pfad-Tests.
22. ⚠️ Manuelle Browser-Verifikation — durch User auszuführen (siehe „Offene Punkte").

## Bewusste Abweichungen vom Plan / Spec

- **System-Prompt auf Deutsch statt Englisch.** Code-Quality-Review entdeckte, dass alle bestehenden System-Prompts (`feature-proposal-system`, `decision-system`, `plan-system`) auf Deutsch sind („Du bist ein …"). Der ursprünglich auf Englisch geschriebene neue Prompt wurde mit Commit `2d099e5` ins Deutsche übersetzt, um Codebase-Konsistenz zu wahren.
- **Drei `void`-Statements als temporäre Bridge.** Task 5 musste `void projectId; void setIsProposing; void setProposeError;` einfügen, um TypeScript-noUnusedParameters-Warnings zu unterdrücken, weil die State-Setter erst in Task 6 produktiv genutzt werden. Task 6 hat alle drei wie geplant entfernt.
- **`WizardFeatureGraphTest.kt` existierte bereits.** Plan sagte „Create" — die Datei war schon vorhanden mit 3 anderen Tests; die zwei neuen Tests wurden ergänzt statt überschrieben.
- **Drei zusätzliche Tests bumpten ihre Registry-Size-Assertions** (`PromptControllerTest`, `PromptRegistryTest`, `PromptServiceTest` — von 6 auf 7). Das war im Plan als bedingt vorhergesehen („falls ein Test counts").
- **`FeatureEditDialog.tsx` wuchs auf 427 Zeilen** (Plan rechnete mit ~320, mit Extraktions-Schwelle bei 350). Die `AcceptanceCriteriaList`-Sub-Komponente bleibt co-located, weil sie eng an den Parent-State gekoppelt ist und nirgends sonst verwendet wird. Extraktion in eigene Datei ist als Follow-Up vorgemerkt.

## Bekannte Restpunkte

- **Doc-Regeneration läuft nicht beim Wizard-Step-Save.** `WizardService.saveStepData()` triggert `DocsScaffoldGenerator` nicht — Re-Generation erfolgt nur via `ProjectService.saveSpecFile()` oder bei Export. Konsistent mit dem heutigen Verhalten der Story-Subtasks-Pipeline (bewusst akzeptiert in Plan/Spec). Wer nur AC editiert, sieht den aktualisierten Feature-Doc beim nächsten Spec-Save.
- **Append-Merge erzeugt Duplikate bei mehrfachem „AC vorschlagen"-Klick.** In Spec/Plan akzeptiert — User kann Dubletten via ✕ entfernen. Title-Match-Dedupe würde gleichnamige Vorschläge verbieten.
- **`FeatureEditDialog.tsx` ist 427 Zeilen.** Sollte bei nächster Erweiterung (z. B. Status pro AC, Drag-Reorder) in eine eigene `AcceptanceCriteriaList.tsx`-Datei extrahiert werden.
- **`appendNew()` hat keinen Guard für nicht gefundenen `afterId`.** In aktueller Co-Location-Architektur unmöglich, aber ein 1-Zeilen-Fix wäre defensive: `if (idx === -1) { onChange([...value, newItem]); return; }`. Code-Quality-Reviewer notierte das als Minor.
- **`HandoffControllerTest > POST export embeds project name sync URL …`** schlägt auf main wie auch auf diesem Branch fehl — pre-existing, nicht Feature-44-bezogen. Vor dem Merge separat triagen.

## YAGNI bestätigt — bewusst nicht umgesetzt

- Drag-and-Drop-Reorder (↑/↓-Buttons reichen).
- Status pro Kriterium (`draft|approved|done`).
- Given/When/Then-Sub-Felder.
- Sub-Modal für AC-Edit.
- Lösch-Confirm pro AC.
- Toast-System (Inline-Fehlerzeile reicht).
- Bulk-AC-Generierung über mehrere Features.
- Verlinkung AC ↔ Tasks.
- Mustache-Template-Refactoring (Fallback-Logik macht das obsolet).

## Offene Punkte für den User

- **Manuelle Browser-Verifikation** (AC #22): Backend mit `OPENAI_API_KEY` starten, Frontend dazu, Projekt anlegen, Feature im Modal mit AC-Add/Edit/Reorder/Save sowie „AC vorschlagen" durchspielen.
- **Eintrag in `00-feature-set-overview.md`** auf die Done-Datei umlinken (statt auf die Spec).
