# Product-Spec-Agent mit Narrative-Driven Development erweitern

Stand: 2026-05-07

## Ziel dieses Dokuments

Dieses Dokument beschreibt, wie du den Product-Spec-Agent gezielt um neue Features erweitern kannst. Es verbindet die vorhandene Projektstruktur mit den Ideen aus Narrative-Driven Development (NDD), beschrieben unter <https://narrativedriven.org/how-it-works>.

Die wichtigste Annahme: Dieses Projekt soll nicht nur Features sammeln, sondern Product Owner dabei fuehren, aus einer groben Idee belastbare, umsetzbare Spezifikationen, Tasks und Agent-Handoffs zu erzeugen. Deshalb passt NDD gut: Es zwingt neue Features dazu, von Nutzerzielen, Outcomes, Slices, Regeln, Beispielen und Datenfluessen her gedacht zu werden.

## Kurzfassung von NDD

NDD macht aus einer groben App-Idee eine baubare Narrative. Die Methode folgt grob dieser Kette:

1. Prompt: eine Idee in normaler Sprache.
2. Goals: was Menschen erreichen wollen.
3. Outcomes: beobachtbare Ergebnisse pro Goal.
4. Slices: kleine baubare Einheiten, zum Beispiel Query, Command, Experience oder React.
5. Rules und Examples: fachliche Regeln und konkrete Given/When/Then-Beispiele.
6. Component Specs: erwartetes Verhalten einzelner UI-, API- oder Service-Komponenten.
7. Data und Connections: welche Daten gebraucht, erzeugt oder veraendert werden.
8. Screens/Wireframes: sichtbare Nutzerfuehrung, ohne die Fachlogik zu ersetzen.
9. Coding Agent Handoff: ein klarer Build-Kontext fuer Codex, Claude Code oder andere Agenten.
10. Living Narrative: Spezifikation, Code und Tests entwickeln sich gemeinsam weiter.

Fuer dieses Repo bedeutet das: Neue Features sollten nicht direkt als UI-Button oder API-Endpoint starten. Sie sollten als narrative Erweiterung des Produktflusses starten.

## Was im Projekt bereits gut vorbereitet ist

Das Projekt hat schon viele Bausteine, die gut zu NDD passen:

| Bereich | Vorhanden im Projekt | Bedeutung fuer Erweiterungen |
|---|---|---|
| Feature-Roadmap | `docs/features/00-feature-set-overview.md` | Neue Features haben bereits einen Ort, Abhaengigkeiten und Aufwand. |
| Feature-Spezifikationen | `docs/features/*.md` | Jedes Feature kann als eigenstaendige Einheit beschrieben werden. |
| Design- und Plan-Dokumente | `docs/superpowers/specs/`, `docs/superpowers/plans/` | Gute Basis fuer Spec-first- und Plan-first-Arbeit. |
| Backend-Schichten | `api/`, `service/`, `storage/`, `agent/`, `domain/`, `export/`, `config/` | Neue Features koennen sauber entlang vorhandener Layer umgesetzt werden. |
| Frontend-Struktur | `app/`, `components/`, `lib/stores`, `lib/api.ts` | Neue UI-Funktionen haben klare Orte. |
| Agenten | `IdeaToSpecAgent`, `FeatureProposalAgent`, `PlanGeneratorAgent`, `AcceptanceCriteriaProposalAgent` | NDD-Logik kann als Agentenfaehigkeit wachsen. |
| Handoff und Export | `HandoffController`, `ExportController` | Narrative-Slices koennen spaeter an Coding Agents uebergeben werden. |

Der naechste Reifegrad waere, die vorhandene Feature-Roadmap konsequenter um NDD-Artefakte zu erweitern.

## Empfohlenes Vorgehen fuer jedes neue Feature

Nutze fuer jedes neue Feature denselben Ablauf. Dadurch lernst du am Projekt, ohne die Architektur jedes Mal neu zu erfinden.

### 1. Feature als Nutzerziel formulieren

Schreibe zuerst nicht "Button X bauen", sondern:

```text
Als Product Owner moechte ich erkennen, welche Feature-Slices noch keine Regeln, Beispiele oder Tests haben,
damit ich eine Spezifikation erst dann an einen Coding Agent uebergebe, wenn sie baubar ist.
```

Das Ziel ist gut, wenn klar ist:

- Wer will etwas erreichen?
- Welches Ergebnis soll sichtbar sein?
- Warum ist das fuer den Produktfluss wichtig?

### 2. Outcomes ableiten

Aus einem Ziel werden beobachtbare Outcomes:

```text
Goal: Spec ist bereit fuer Agent-Handoff

Outcomes:
- Alle kritischen Features haben Akzeptanzkriterien.
- Jeder Feature-Slice hat mindestens ein Beispiel.
- Blockierende Widersprueche werden vor dem Export angezeigt.
- Der Handoff enthaelt nur freigegebene Slices.
```

Outcomes sind besser als grosse Feature-Beschreibungen, weil sie spaeter Tests und UI-Zustaende erzeugen.

### 3. Outcomes in Slices zerlegen

Ein Slice sollte klein genug sein, um separat gebaut und getestet zu werden:

| Slice-Typ | Beispiel im Product-Spec-Agent |
|---|---|
| Query | "Lade Coverage-Status aller Features eines Projekts." |
| Command | "Markiere einen Feature-Slice als reviewt." |
| Experience | "Zeige im Wizard, warum ein Step blockiert ist." |
| React | "Aktualisiere den Handoff-Status, wenn Akzeptanzkriterien geaendert wurden." |

Diese Slice-Typen kannst du als Standard in neue Feature-Dokumente uebernehmen.

### 4. Regeln und Beispiele definieren

Regeln beschreiben, was wahr sein muss. Beispiele beweisen, dass die Regel verstanden ist.

```gherkin
Rule: Ein Feature darf erst als handoff-ready gelten, wenn alle Pflicht-Slices Beispiele haben.

Given ein Feature hat drei Pflicht-Slices
And zwei Slices haben mindestens ein Beispiel
When der Nutzer den Handoff-Status prueft
Then wird das Feature als "nicht bereit" angezeigt
And der fehlende Slice wird benannt
```

Diese Beispiele koennen spaeter direkt zu Backend-Tests, Frontend-Tests oder Agent-Prompt-Checks werden.

### 5. Datenmodell bewusst machen

Vor dem Coden klaerst du, welche Daten das Feature braucht:

```text
NarrativeSlice
- id
- featureId
- type: QUERY | COMMAND | EXPERIENCE | REACT
- title
- rules[]
- examples[]
- componentSpecs[]
- dataDependencies[]
- connectedSliceIds[]
- readinessStatus
```

Wichtig ist nicht, sofort dieses Modell einzubauen. Wichtig ist, die Daten vorab explizit zu machen. Danach kannst du entscheiden, ob bestehende Modelle reichen oder ein neues Domain-Modell noetig ist.

### 6. UI, API und Agent-Handoff gemeinsam betrachten

Ein Feature ist in diesem Projekt selten nur Frontend oder nur Backend. Pruefe immer drei Oberflaechen:

| Schnittstelle | Frage |
|---|---|
| Frontend | Wo sieht oder bearbeitet der Nutzer das Ergebnis? |
| REST API | Welche Endpoints laden, speichern oder pruefen den Zustand? |
| Agent/Handoff | Welche Informationen braucht ein Coding Agent konkret? |

Wenn eine Schnittstelle nicht betroffen ist, schreibe das explizit in die Feature-Spec. Das verhindert unnoetigen Scope.

## Konkrete Verbesserungsrichtungen fuer dieses Projekt

Die folgenden Erweiterungen passen gut zur vorhandenen Roadmap und wuerden das Projekt in Richtung NDD weiterentwickeln.

### 1. Narrative-Slice-Modell einfuehren

**Problem:** Features enthalten bereits Akzeptanzkriterien und Tasks, aber der Zwischenschritt "Goal -> Outcome -> Slice -> Rule -> Example" ist noch nicht als eigene Produktstruktur sichtbar.

**Verbesserung:** Fuehre ein explizites Slice-Konzept fuer Features ein.

**Moeglicher Einstieg:**

- Backend: Domain-Modell fuer `NarrativeSlice` oder Erweiterung bestehender Feature-/Task-Modelle.
- Frontend: Slice-Liste im Feature-Edit-Modal oder FEATURES-Step.
- Agent: FeatureProposalAgent erzeugt nicht nur Features, sondern optional Slices.
- Tests: pruefen, dass Pflichtfelder und Slice-Typen korrekt validiert werden.

**Warum zuerst:** Das ist der Kernhebel fuer NDD. Ohne Slices bleiben Regeln, Beispiele und Handoff-Kontext schwer sauber zu strukturieren.

### 2. Buildability-Check pro Feature

**Problem:** Es ist schwer zu erkennen, ob ein Feature bereits gut genug beschrieben ist, um von einem Coding Agent umgesetzt zu werden.

**Verbesserung:** Ergaenze einen "buildable narrative"-Status.

**Kriterien koennten sein:**

- Ziel ist formuliert.
- Mindestens ein Outcome existiert.
- Jeder kritische Outcome hat mindestens einen Slice.
- Kritische Slices haben Regeln und Beispiele.
- Datenabhaengigkeiten sind benannt.
- Blockierende offene Clarifications sind beantwortet.

**Passender Ort im Produkt:**

- Wizard-Blocker im FEATURES-Step.
- Feature-Edit-Modal.
- Task-Coverage-Panel.
- Handoff-Preview.

### 3. Given/When/Then-Beispiele als First-Class-Artefakt

**Problem:** Akzeptanzkriterien sind vorhanden, aber konkrete Beispiele sind fuer Tests und Agenten oft nuetzlicher.

**Verbesserung:** Ergaenze Beispiele je Feature oder Slice.

**Moeglicher Workflow:**

1. Nutzer schreibt oder generiert ein Akzeptanzkriterium.
2. Agent schlaegt ein oder mehrere Given/When/Then-Beispiele vor.
3. Nutzer akzeptiert, editiert oder verwirft Beispiele.
4. Export und Handoff enthalten diese Beispiele.

**Technischer Vorteil:** Backend-Tests und Playwright-Szenarien koennen spaeter aus derselben Struktur abgeleitet werden.

### 4. Handoff nach Slices statt nur nach Projekt

**Problem:** Ein vollstaendiges Projekt-Handoff kann fuer Coding Agents zu gross werden.

**Verbesserung:** Ermoegliche Handoffs fuer genau einen Goal/Outcome/Slice-Kontext.

**Inhalt eines Slice-Handoffs:**

- Domain-Kontext.
- Aktuelles Ziel.
- Outcome unter Arbeit.
- Betroffene Slices.
- Regeln und Beispiele.
- Komponenten- und API-Specs.
- Datenabhaengigkeiten.
- Relevante Querverweise.

Das entspricht direkt dem NDD-Gedanken, einem Coding Agent nicht einen vagen Prompt, sondern einen kohaerenten Build-Kontext zu geben.

### 5. Living-Sync

**Problem:** Spezifikationen, generierte Dateien, Tasks und Code koennen auseinanderlaufen.

**Verbesserung:** Baue einen Sync- oder Drift-Check zwischen Narrative-Artefakten und Projektdateien.

**Moegliche Checks:**

- Feature in `docs/features` existiert, aber keine UI/API-Umsetzung.
- Endpoint existiert, aber REST-Doku ist veraltet.
- Task ist erledigt, aber Feature-Spec hat keinen Done-Status.
- Akzeptanzkriterium existiert, aber kein Test referenziert es.

Das passt zu bestehenden Bereichen wie Consistency Checks, Spec-to-Docs Sync und Task Coverage.

### 6. NDD-Template fuer neue Feature-Dateien

**Problem:** Neue Feature-Specs koennen uneinheitlich werden.

**Verbesserung:** Ergaenze ein Template, das NDD-Struktur erzwingt.

Empfohlene Datei: `docs/features/_template-ndd-feature.md`

````markdown
# <Feature-Name>

## Ziel

Als <Rolle> moechte ich <Faehigkeit>, damit <Nutzen>.

## Goals

- <Nutzerziel>

## Outcomes

- <beobachtbares Ergebnis>

## Slices

| Typ | Titel | Beschreibung | Muss fuer MVP? |
|---|---|---|---|
| Query | | | |
| Command | | | |
| Experience | | | |
| React | | | |

## Regeln

- <Regel>

## Beispiele

```gherkin
Given ...
When ...
Then ...
```

## Daten

- <Domain-Objekt oder Feld>

## UI/API/Agent-Handoff

- Frontend:
- API:
- Agent/Handoff:

## Tests

- Backend:
- Frontend:
- Agent/Prompt:
````

## Praktischer Lernpfad fuer dich

Wenn du das Projekt zum Lernen erweitern willst, empfehle ich diese Reihenfolge:

1. **Ein kleines bestehendes Feature nach NDD umschreiben:** Nimm zum Beispiel ein S- oder M-Feature aus `docs/features/`, schreibe Goals, Outcomes, Slices, Regeln und Beispiele dazu. Noch kein Code.
2. **Template einfuehren:** Lege ein NDD-Feature-Template an und nutze es fuer das naechste neue Feature.
3. **Buildability-Check dokumentieren:** Definiere im Feature-Dokument, wann ein Feature "buildable" ist.
4. **Ein UI-only Learning Feature bauen:** Zeige den Buildability-Status zunaechst nur aus vorhandenen Daten an.
5. **Backend-Validierung ergaenzen:** Speichere oder berechne den Status serverseitig.
6. **Agent-Handoff erweitern:** Fuege Goals, Outcomes, Slices, Regeln und Beispiele in den Handoff-Export ein.
7. **Tests ableiten:** Uebersetze Beispiele in konkrete Backend- und Frontend-Tests.

So lernst du Schritt fuer Schritt: erst Spezifikation, dann UI, dann API, dann Persistenz, dann Agent-Handoff.

## Beispiel: neues Feature nach NDD formuliert

````markdown
# Buildability-Check fuer Feature-Slices

## Ziel

Als Product Owner moechte ich sehen, ob ein Feature ausreichend beschrieben ist,
damit ich es erst dann an einen Coding Agent uebergebe, wenn Ziel, Regeln, Beispiele und Daten klar sind.

## Goals

- Product Owner prueft die Umsetzungsreife eines Features.
- Product Owner erkennt fehlende Narrative-Elemente.
- Coding Agent erhaelt einen kleinen, koharenten Build-Kontext.

## Outcomes

- Feature zeigt Status "buildable" oder "needs work".
- Fehlende Elemente werden konkret benannt.
- Handoff kann auf buildable Slices begrenzt werden.

## Slices

| Typ | Titel | Beschreibung | Muss fuer MVP? |
|---|---|---|---|
| Query | Buildability laden | Liefert Reifegrad und fehlende Elemente pro Feature. | ja |
| Experience | Status anzeigen | Zeigt Status im Feature-Edit-Modal. | ja |
| Command | Slice reviewen | Markiert einen Slice als fachlich geprueft. | nein |
| React | Handoff aktualisieren | Handoff-Preview reagiert auf geaenderten Buildability-Status. | nein |

## Regel

Ein Feature gilt nur dann als buildable, wenn mindestens ein Goal, ein Outcome, ein Slice und ein Beispiel vorhanden sind.

## Beispiel

```gherkin
Given ein Feature hat ein Goal und einen Outcome
And kein Beispiel ist definiert
When der Buildability-Status berechnet wird
Then ist der Status "needs work"
And "missing examples" wird als Grund angezeigt
```
````

## Wo du im Code typischerweise anfassen wuerdest

| Aenderung | Typischer Ort |
|---|---|
| Neues Domain-Modell | `backend/src/main/kotlin/com/agentwork/productspecagent/domain/` |
| Neue API | `backend/src/main/kotlin/com/agentwork/productspecagent/api/` |
| Fachlogik | `backend/src/main/kotlin/com/agentwork/productspecagent/service/` |
| Persistenz | `backend/src/main/kotlin/com/agentwork/productspecagent/storage/` |
| Agent-Prompt/Agentenlogik | `backend/src/main/kotlin/com/agentwork/productspecagent/agent/` |
| API-Client | `frontend/src/lib/api.ts` |
| Zustand im Frontend | `frontend/src/lib/stores/*-store.ts` |
| Feature-UI | `frontend/src/components/` oder `frontend/src/app/` |
| Feature-Spec | `docs/features/` |
| Design/Plan | `docs/superpowers/specs/`, `docs/superpowers/plans/` |

## Definition of Done fuer neue Features

Ein neues Feature sollte erst als erledigt gelten, wenn diese Punkte erfuellt sind:

- Feature-Spec beschreibt Ziel, Outcomes, Slices, Regeln und Beispiele.
- API- oder UI-Aenderungen sind in der passenden Architektur- oder Produktdoku erwaehnt.
- Backend-Tests decken fachliche Regeln ab.
- Frontend-Tests oder Komponentenchecks decken kritische Nutzerpfade ab.
- Handoff/Export enthaelt alle Informationen, die ein Coding Agent fuer die Umsetzung braucht.
- `docs/features/00-feature-set-overview.md` ist aktualisiert.
- Falls das Feature abgeschlossen ist, gibt es eine passende `*-done.md`-Datei oder einen dokumentierten Status.

## Wichtigste Empfehlung

Beginne nicht mit einem grossen Umbau. Der beste naechste Schritt ist ein kleines NDD-Template und ein Buildability-Check fuer vorhandene Feature-Daten. Damit verbesserst du sofort die Lernbarkeit des Projekts, ohne Backend, Frontend, Agenten und Export gleichzeitig umzubauen.
