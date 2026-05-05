# Feature 44 — Akzeptanzkriterien im Feature-Edit-Modal

**Phase:** Wizard-Erweiterung
**Abhängig von:** Feature 22 (Features-Graph-Wizard, done), Feature 36 (Features-Edit-Modal, done)
**Aufwand:** M
**Design-Spec:** [`docs/superpowers/specs/2026-05-05-feature-acceptance-criteria-design.md`](../superpowers/specs/2026-05-05-feature-acceptance-criteria-design.md)

## Problem

Der Wizard-Schritt **FEATURES** sammelt heute pro Feature: Title, Beschreibung, Scopes und Scope-Felder. Akzeptanzkriterien — die Done-Bedingungen, anhand derer ein Product Owner ein Feature abnimmt — werden nicht erfasst. Im Feature-Doc (`docs/features/NN.md`) erscheint zwar ein `## Acceptance Criteria`-Block, der ist aber heute mit Story-Subtasks befüllt (`ScaffoldContextBuilder.kt:67`) — Implementierungs-Tasks, keine stakeholder-orientierten Done-Bedingungen.

## Ziel

Im `FeatureEditDialog` lassen sich pro Feature Akzeptanzkriterien als geordnete Liste pflegen (Title + optionale Description). Ein „AC vorschlagen"-Button startet einen LLM-Agenten, der 3–6 stakeholder-orientierte Kriterien generiert; Vorschläge werden an die bestehende Liste angehängt. Der `ScaffoldContextBuilder` nimmt die neuen Wizard-AC als primäre Quelle, mit Fallback auf Story-Subtasks für bestehende Projekte.

## Architektur

Siehe Design-Spec für das vollständige Bild. Kurzfassung:

- **Backend Domain (`WizardFeatureGraph.kt`):** neuer `AcceptanceCriterion(id, title, description)`-Datentyp; neues Feld `acceptanceCriteria: List<AcceptanceCriterion> = emptyList()` in `WizardFeature`.
- **Backend Agent (neu):** `agent/AcceptanceCriteriaProposalAgent.kt` — gleiche Struktur wie `FeatureProposalAgent` (contextBuilder + promptService + koogRunner, JSON-Output).
- **Backend Prompt (neu):** `data/agent-prompts/acceptance-criteria-proposal-system.md`.
- **Backend API (neu):** `api/AcceptanceCriteriaProposalController.kt` mit `POST /api/v1/projects/{projectId}/features/{featureId}/acceptance-criteria/propose`.
- **Backend Export (geändert):** `ScaffoldContextBuilder.kt:67` — Fallback-Logik (Wizard-AC > Story-Subtasks).
- **Frontend Types (`lib/api.ts`):** `AcceptanceCriterion`-Interface; Feld in `WizardFeature`; `proposeAcceptanceCriteria()`-Wrapper.
- **Frontend Modal (`FeatureEditDialog.tsx`):** neue Section unter dem 2-Spalten-Grid mit `AcceptanceCriteriaList`-Sub-Komponente, „AC vorschlagen"-Button mit Loading/Error-State, `projectId` als zusätzliches Prop.

## Datenmodell

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

Reihenfolge wird über die List-Order abgebildet. `id` ist UUID, im Frontend per `crypto.randomUUID()` beim Add gesetzt; vom Agent für Vorschläge gesetzt. Kein zusätzliches `order`-Feld.

## UI-Verhalten

- Section „Akzeptanzkriterien" rendert unter dem 2-Spalten-Grid, vor dem Footer.
- „+ Akzeptanzkriterium hinzufügen" hängt einen leeren Eintrag an, fokussiert das Title-Input.
- Title (Input) und Description (Textarea, `rows={2}`) sind inline editierbar.
- ✕-Button entfernt einen Eintrag sofort, ohne Confirm.
- ↑/↓-Buttons verschieben um eine Position; bei Index-Grenzen disabled.
- Enter im Title-Input fügt einen leeren Eintrag direkt **darunter** ein und fokussiert ihn.
- „AC vorschlagen"-Button (Sparkles-Icon) ruft den Backend-Endpoint, hängt die Vorschläge an die bestehende Liste (Append-Merge). Während des Calls disabled, mit Spinner und „Generiere…"-Label.
- Beim Speichern werden Einträge mit leerem Title (nach Trim) still gefiltert.
- Eingaben mutieren ausschließlich `draft`; Persistenz erst beim Klick auf „Speichern". Dirty-Tracking über `equalDraft` (erweitert um AC-Vergleich).
- „Abbrechen"/Esc/Overlay-Klick mit gedirtyten AC zeigen den bestehenden „Änderungen verwerfen?"-Confirm.

## Doc-Generierung

`ScaffoldContextBuilder.kt:67` wird zu:

```kotlin
acceptanceCriteria = wizardFeature?.acceptanceCriteria
    ?.takeIf { it.isNotEmpty() }
    ?.map { TaskContext(it.title, it.description) }
    ?: subtasks.map { TaskContext(it.title, it.description) },
```

Mustache-Template (`feature.md.mustache`) bleibt unverändert.

## Akzeptanzkriterien

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
12. `404` bei unbekanntem `featureId`, `422` bei kaputtem LLM-JSON.
13. Vorgeschlagene AC werden im Frontend an die bestehende Liste angehängt (Append-Merge).
14. Während `isProposing` ist der Button disabled mit Spinner+„Generiere…"-Label.

**Persistenz / Doc-Generierung:**
15. Bestehende `wizard.json`-Files ohne `acceptanceCriteria` laden ohne Fehler (kotlinx-Default `emptyList()`).
16. Speichern eines Features mit AC schreibt das neue Feld in `wizard.json`.
17. `ScaffoldContextBuilder` rendert Wizard-AC, wenn vorhanden — sonst Story-Subtasks.
18. `feature.md` zeigt AC im Format `- [ ] {title}: {description}` (bzw. nur `- [ ] {title}` bei leerer Description).

**Tests:**
19. Backend-Test: `AcceptanceCriteriaProposalAgent` parst valides JSON; `ProposalParseException` bei kaputtem JSON.
20. Backend-Test: `ScaffoldContextBuilderTest` deckt drei Pfade ab (Wizard-AC vorhanden, leer, kein Wizard-Match).
21. Manuelle Browser-Verifikation für Add/Edit/Remove/Reorder/Save/Propose-Pfade.

## Betroffene Dateien

**Backend (neu):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgent.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/api/AcceptanceCriteriaProposalController.kt`
- `backend/src/main/resources/prompts/acceptance-criteria-proposal-system.md` (zusätzlich in `PromptRegistry.kt` als `PromptDefinition` registrieren)
- `backend/src/test/kotlin/com/agentwork/productspecagent/agent/AcceptanceCriteriaProposalAgentTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilderTest.kt` (falls noch nicht existent)

**Backend (geändert):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardFeatureGraph.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilder.kt`

**Frontend (geändert):**
- `frontend/src/lib/api.ts`
- `frontend/src/components/wizard/steps/features/FeatureEditDialog.tsx`
- `frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx` (gibt `projectId` an Modal weiter)

## YAGNI

- Drag-and-Drop-Reorder (↑/↓-Buttons reichen)
- Status pro Kriterium (`draft|approved|done`)
- Given/When/Then-Sub-Felder
- Sub-Modal für AC-Edit
- Lösch-Confirm pro Eintrag
- Toast-System für Fehler (Inline-Zeile reicht)
- Bulk-AC-Generierung über mehrere Features
- AC-↔-Tasks-Verlinkung
- Mustache-Template-Refactoring für Story-Subtasks-Block
