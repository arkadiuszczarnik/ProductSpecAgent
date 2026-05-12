# Feature 51: Feature-Done-Markdown Import via MCP

**Design-Spec:** [`docs/superpowers/specs/2026-05-12-feature-done-markdown-import-design.md`](../superpowers/specs/2026-05-12-feature-done-markdown-import-design.md)

## Zusammenfassung

Product-Spec-Agent erhaelt eine weitere MCP-Schnittstelle, ueber die ein Coding Agent eine `*-done.md` Datei fuer genau ein Projekt-Feature hochladen kann. Product-Spec-Agent analysiert diesen Markdown-Inhalt nicht mit parserbasierter Anwendungslogik, sondern ueber einen dedizierten PSA-Agenten, der daraus eine feste JSON-Struktur erzeugt.

Das Analyse-Ergebnis wird gespeichert und direkt auf das betroffene Projekt-Feature gespiegelt. Dadurch sehen Workspace, Living Sync und Feature-Graph nicht nur einzelne Reporting-Events, sondern auch einen verdichteten Abschlussstand mit `DONE`-Status, Zusammenfassung, offenen Punkten, Abweichungen und Test-Evidenz.

## Problem

Feature 45 deckt laufendes Living-Sync-Reporting ueber einzelne Events ab. Was heute fehlt, ist ein strukturierter Rueckkanal fuer abgeschlossene Feature-Arbeit in Form einer `*-done.md` Datei:

- Coding Agents koennen heute keine konsolidierte Feature-Abschlussdokumentation an PSA uebergeben.
- Offene Punkte, Abweichungen und Test-Evidenz aus einer Done-Datei bleiben ausserhalb des Projektmodells.
- Workspace und Feature-Graph wissen nicht, dass ein Feature fachlich abgeschlossen wurde, wenn diese Information nur in einer Markdown-Datei vorliegt.
- Ein Product Owner sieht dadurch zwar Event-Verlauf, aber keinen verdichteten Abschluss-Snapshot pro Feature.

## Ziel

- Ein Coding Agent kann eine `*-done.md` Datei ueber die bestehende MCP-Schnittstelle an PSA senden.
- PSA analysiert den Markdown-Inhalt mit einem internen Agenten und erzeugt daraus ein stabiles JSON-Ergebnis.
- Das Ergebnis aktualisiert genau ein vorhandenes Projekt-Feature anhand der expliziten `featureId`.
- Workspace, Cockpit und Feature-Graph zeigen dadurch den realen Umsetzungsstand des Features.
- Living Sync behaelt den Import als auditierbaren Verlaufseintrag.

## Kontext7-Erkenntnisse

- **Spring Boot / Spring MVC:** Multipart-Uploads koennen in bestehenden REST-Controllern schlank ueber `@RequestParam("file") MultipartFile` abgebildet werden. Das passt zur bestehenden Upload-Struktur im Backend und erlaubt eine REST-Spiegelung des neuen MCP-Flows fuer interne Tests.
- **Content Negotiation:** `text/markdown` kann bei Bedarf explizit als Media Type registriert werden. Fuer V1 reicht es, wenn der Agent den Markdown-Inhalt ueber das MCP-Tool als String liefert.
- **Markdown-Parsing-Library bewusst nicht verwenden:** Obwohl Bibliotheken wie `commonmark-java` Markdown in einen AST parsen koennen, ist das hier ausdruecklich nicht der gewaehlte Weg. Die fachliche Strukturierung soll vom PSA-Agenten kommen, nicht von parserbasierter Code-Logik.

## Architektur

### MCP-Tool

Neues Tool im bestehenden Living-Sync-MCP:

- `import_feature_done_markdown`

Eingaben:

- `projectId`
- `featureId`
- `fileName`
- `markdown`
- optional `agentName`

Verhalten:

- `featureId` ist die einzige fachliche Zuordnung zum Projekt-Feature.
- Die Markdown-Ueberschrift wird nur als Plausibilitaetscheck verwendet.
- Das Tool liefert ein strukturiertes Ergebnis mit Importstatus, Warnungen und abgeleitetem Feature-Status zurueck.

### Agentische Analyse

Neuer PSA-Agent, z. B. `FeatureDoneImportAgent`:

- erhaelt Projektkontext, Ziel-Feature und den hochgeladenen Markdown-Inhalt
- antwortet ausschliesslich mit JSON
- extrahiert daraus:
  - `derivedStatus`
  - `summary`
  - `implementedItems`
  - `deviations`
  - `tests`
  - `openPoints`
  - `technicalDebt`
  - `warnings`
  - `headerCheck`

Wichtig:

- keine Regex- oder AST-basierte Fachlogik im Anwendungscode
- Backend validiert nur Schema, Pflichtfelder und Projektbezug

### Persistenz

Der Import soll drei Spuren hinterlassen:

1. Roh-Markdown als Artefakt des Importlaufs
2. Living-Sync-Event fuer Audit und Verlauf
3. Separaten Feature-Completion-Snapshot als JSON fuer das betroffene Projekt-Feature

Empfohlene Trennung:

- `WizardFeatureGraph` bleibt das Planungsmodell
- `FeatureCompletionSnapshot` wird das Umsetzungsmodell

Beispiel fuer den Snapshot:

```kotlin
@Serializable
data class FeatureCompletionTestEvidence(
    val name: String,
    val status: String,
)

@Serializable
data class FeatureCompletionSnapshot(
    val projectId: String,
    val featureId: String,
    val derivedStatus: LivingSyncFeatureStatus,
    val summary: String,
    val implementedItems: List<String> = emptyList(),
    val deviations: List<String> = emptyList(),
    val openPoints: List<String> = emptyList(),
    val technicalDebt: List<String> = emptyList(),
    val tests: List<FeatureCompletionTestEvidence> = emptyList(),
    val warnings: List<String> = emptyList(),
    val sourceEventId: String,
    val sourceFileName: String,
    val updatedAt: String,
)
```

### Projektion in Workspace und Feature-Graph

Der Import soll das bestehende Planungsmodell nicht umschreiben, sondern um eine abgeleitete Sicht erweitern:

- Feature-Status im Workspace folgt dem letzten `FeatureCompletionSnapshot`
- Cockpit und Feature-Graph zeigen `derivedStatus` als sichtbaren Umsetzungsstand
- Offene Punkte und Abweichungen werden am aktiven Feature angezeigt
- Test-Evidenz aus dem Snapshot wird am Feature sichtbar
- Living Sync listet den Importlauf als separates Ereignis mit Warnungen und Verweis auf das Ziel-Feature

## JSON-Zielformat des Agents

```json
{
  "featureId": "uuid",
  "headerCheck": {
    "matchesExpectedFeature": true,
    "reportedFeatureLabel": "Feature 45: Living-Sync via MCP",
    "warnings": []
  },
  "derivedStatus": "DONE",
  "summary": "Kurze fachliche Zusammenfassung des umgesetzten Stands.",
  "implementedItems": [
    "Neue Domain-Modelle fuer Living-Sync-Events."
  ],
  "deviations": [
    "Spring-WebMVC-JSON-RPC statt Spring-AI-MCP-Starter."
  ],
  "tests": [
    {
      "name": "LivingSyncServiceTest",
      "status": "PRESENT"
    }
  ],
  "openPoints": [
    "Auth-Hardening fuer externe Coding Agents festlegen."
  ],
  "technicalDebt": [
    "Spring AI MCP Transport spaeter produktiv absichern."
  ],
  "warnings": []
}
```

## Akzeptanzkriterien

1. Das bestehende MCP-Interface bietet ein neues Tool `import_feature_done_markdown` an.
2. Das Tool akzeptiert genau ein Ziel-Feature ueber `featureId` und einen Markdown-Inhalt ueber `markdown`.
3. PSA verwendet fuer die fachliche Analyse keinen parserbasierten Anwendungscode, sondern einen dedizierten internen Agenten.
4. Der Agent antwortet ausschliesslich mit einem validierbaren JSON-Ergebnis.
5. PSA speichert den Roh-Markdown des Imports separat als Artefakt.
6. PSA speichert das normalisierte Agent-Ergebnis als strukturierten Feature-Completion-Snapshot.
7. PSA schreibt fuer jeden Import ein nachvollziehbares Living-Sync-Ereignis.
8. Ein erfolgreicher Import aktualisiert den sichtbaren Status des betroffenen Projekt-Features im Workspace.
9. Offene Punkte und Abweichungen aus der Done-Datei werden am betroffenen Feature sichtbar.
10. Test-Evidenz aus der Done-Datei wird am betroffenen Feature sichtbar.
11. Die Markdown-Ueberschrift wird nur als Plausibilitaetscheck genutzt; ein Mismatch erzeugt Warnungen, aber keinen harten Fehler, solange `featureId` gueltig ist.
12. Backend-Tests decken Tool-Validierung, Agent-JSON-Validierung, Snapshot-Persistenz und Projektion auf das Projekt-Feature ab.

## Abhaengigkeiten

- Feature 22: Intelligenter FEATURES-Step mit DAG
- Feature 45: Living-Sync via MCP
- Feature 50: Project Cockpit Feature Workbench

## Nicht-Ziele

- Kein manueller UI-Upload ausserhalb der MCP-Schnittstelle.
- Kein regelbasiertes Markdown-Parsing mit Regex, AST oder Section-Mapping im Anwendungscode.
- Kein Import fuer mehrere Projekt-Features in einem Lauf.
- Keine automatische Feature-Erkennung nur anhand des Markdown-Headers.
- Keine Rueckschreiblogik in das eigentliche `WizardFeature`-Planungsmodell.

## Betroffene Dateien

- `backend/src/main/kotlin/com/agentwork/productspecagent/api/LivingSyncController.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/api/LivingSyncMcpController.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureDoneImportAgent.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FeatureCompletionSnapshot.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/LivingSyncModels.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/LivingSyncService.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`
- `backend/src/main/resources/prompts/feature-done-import-system.md`
- `backend/src/test/kotlin/com/agentwork/productspecagent/agent/FeatureDoneImportAgentTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/service/LivingSyncServiceTest.kt`
- `frontend/src/lib/api.ts`
- `frontend/src/components/living-sync/LivingSyncPanel.tsx`
- `frontend/src/components/cockpit/ProjectCockpitPrototype.tsx`
- `docs/superpowers/specs/2026-05-12-feature-done-markdown-import-design.md`
