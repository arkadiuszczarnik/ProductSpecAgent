# Feature 21 — Wizard FEATURES → EPIC + Stories Tasks (Done)

## Status
Implementiert am 2026-04-09 als Folge-Feature zu Feature 20 (Spec-to-Docs-Sync).

## Problem
Der Wizard FEATURES-Step hat User-Input nur in `spec/features.md` als rohen Felder-Dump geschrieben. `docs/features/` wurde aber von `ScaffoldContextBuilder` aus EPIC-Tasks generiert, die ausschliesslich vom manuellen `POST /api/v1/projects/{id}/tasks/generate`-Aufruf erzeugt werden konnten. Folge: Die im Wizard eingegebenen Features sind nie in der generierten `docs/features/00-feature-set-overview.md` aufgetaucht; stattdessen sah der User stale Feature-Listen aus frueheren Plan-Generator-Laeufen.

## Loesung
Wenn der Wizard FEATURES-Step ohne Blocker abgeschlossen wird, werden die Wizard-Features automatisch via `PlanGeneratorAgent` in einen EPIC + Stories + Tasks-Baum zerlegt. Pro Feature ein EPIC mit 1–3 Stories und 1–3 Tasks pro Story. Die generierten Tasks werden im Task-Storage persistiert und vom existierenden `ScaffoldContextBuilder` automatisch in `docs/features/` gerendert (`saveSpecFile` triggert `regenerateDocsScaffold`).

## Idempotenz
Neuer `TaskSource`-Marker (`WIZARD | PLAN_GENERATOR`) auf `SpecTask`. Bei Wiederholung des FEATURES-Steps loescht `TaskService.replaceWizardFeatureTasks` nur Tasks mit `source == WIZARD` und deren Descendants — manuell erstellte Tasks und PlanGenerator-Tasks bleiben unberuehrt. Bestehende Daten ohne Source (alte Projekte) sind backward-compatible: Default `null`.

## Technische Aenderungen

### Domain
- `SpecTask` bekommt neues optionales Feld `source: TaskSource? = null`
- Neuer Enum `TaskSource { WIZARD, PLAN_GENERATOR }`

### `PlanGeneratorAgent`
- Bestehende `generatePlan(projectId)` markiert ihre Tasks jetzt mit `source = PLAN_GENERATOR`
- Neue Methode `generatePlanForFeature(projectId, title, description, estimate, startPriority)`:
  - Baut einen LLM-Prompt mit dem einzelnen Feature + Project-Context
  - Erzeugt automatisch den EPIC aus den Wizard-Daten (1 LLM-Call pro Feature)
  - LLM liefert nur die `stories[]` mit `tasks[]` darunter
  - Bei Parsing-Fehlern: EPIC bleibt erhalten ohne Stories (nicht-faten Fallback)
  - Alle erzeugten Tasks tragen `source = WIZARD` und `specSection = FEATURES`

### `TaskService`
- Neue `WizardFeatureInput`-Datenklasse
- Neue `replaceWizardFeatureTasks(projectId, features)`:
  1. Liest alle existierenden Tasks
  2. Sammelt alle `source == WIZARD`-Task-IDs plus alle Descendants (transitiv via parentId)
  3. Loescht diese
  4. Berechnet `nextPriority` aus dem hoechsten verbleibenden Priority-Wert
  5. Ruft fuer jedes Wizard-Feature `agent.generatePlanForFeature` auf, persistiert Tasks
  6. Gibt die Liste der neu erzeugten Tasks zurueck

### `IdeaToSpecAgent.processWizardStep`
- Neuer Constructor-Parameter `taskService: TaskService` (kein Cycle: `TaskService` haengt nicht an `IdeaToSpecAgent`)
- Nach `flowState`-Update, vor `saveSpecFile`: wenn `currentStepType == FEATURES && !isBlocked`, parse `fields["features"]` via `parseWizardFeatures` und ruf `taskService.replaceWizardFeatureTasks` auf
- Bei Exception aus dem Task-Service: `logger.warn`, sonst weitermachen — User-Input bleibt im spec/ erhalten
- `saveSpecFile` danach triggert wie gewohnt `regenerateDocsScaffold`

### `parseWizardFeatures`-Helper
Robust gegen verschiedene Jackson-Shapes:
- `List<Map>` → eine `WizardFeatureInput` pro Map
- `Map` → eine einzelne Feature-Eingabe
- `List<String>` → Title-only Eingaben
- Akzeptiert `title|name`, `description|desc`, `estimate|size` als Feldnamen
- Default-Estimate `M` wenn nicht angegeben
- Eintraege ohne Title werden ignoriert

## Geaenderte Dateien
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/SpecTask.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/PlanGeneratorAgent.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/TaskService.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgentTest.kt` (neuer Constructor-Param)
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardChatControllerTest.kt` (neuer Constructor-Param)
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/ChatControllerTest.kt` (neuer Constructor-Param)

## Verifikation
- `./gradlew compileKotlin` — gruen
- `./gradlew compileTestKotlin` — gruen
- `./gradlew test --tests "*.IdeaToSpecAgentTest" --tests "*.WizardChatControllerTest" --tests "*.ChatControllerTest" --tests "*.PlanGeneratorAgentTest" --tests "*.SpecContextBuilderTest" --tests "*.SpecContextBuilderWizardTest"` — gruen

## Manueller Smoke-Test
1. Neues Projekt anlegen, Wizard bis FEATURES durchlaufen
2. 1–3 Features im FEATURES-Step eintragen (Title, Description, Estimate)
3. "Weiter" klicken
4. Im Explorer (jetzt mit Auto-Refresh, siehe Feature 18 Done-Notes) sollte `docs/features/00-feature-set-overview.md` die neuen Wizard-Features zeigen, plus eine `01-<slug>.md`, `02-<slug>.md`, ... pro Feature
5. Bei einer Wiederholung des FEATURES-Steps: alte Wizard-EPICs verschwinden, neue erscheinen, manuell angelegte Tasks bleiben

## Offene Punkte / Technische Schulden
- Pro Feature ein eigener LLM-Call — bei vielen Features langsam und teuer. Optional: Batch-Variante, die alle Features in einem Call zerlegt.
- `ScaffoldContextBuilder` rendert die `dependencies` als naive `"Feature N-1"` (siehe Zeile 41) — fuer Wizard-Features nicht aussagekraeftig. Sollte spaeter aus echten Task-`dependencies` kommen.
- Keine UI-Anzeige der `source` im Task-Tree. Wenn der User wissen will, woher ein Task kommt, muesste das im Frontend sichtbar werden.
- Vorbestehender Test-Fail `FileControllerTest.GET files returns file tree` (Feature 15) bleibt unabhaengig von dieser Aenderung.
- Compiler-Warning `Condition is always 'true'` in `IdeaToSpecAgent.kt:230` — vorbestehend, redundanter `currentStepType != null`-Check.
