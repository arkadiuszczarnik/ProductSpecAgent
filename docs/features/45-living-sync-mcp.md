# Feature 45: Living-Sync via MCP

## Zusammenfassung

Product-Spec-Agent stellt einen MCP-Server bereit, den externe Coding Agents in der erzeugten Ziel-Applikation registrieren koennen. Der Coding Agent meldet waehrend der Umsetzung laufend strukturierte Rueckinformationen an Product-Spec-Agent zurueck: Feature-Fortschritt, Testlaeufe, Tokenverbrauch, geaenderte Dateien, Commits, Blocker, offene Fragen und technische Schulden.

Das Feature ergaenzt den bestehenden Handoff-Sync aus Feature 30. Feature 30 gibt dem Agent einen aktuellen Projekt-Snapshot. Feature 45 fuegt die Gegenrichtung hinzu: Der Agent schreibt Umsetzungsstatus in Product-Spec-Agent zurueck.

## Problem

Nach dem Handoff kann Product-Spec-Agent heute nicht verlaesslich erkennen, was in der Ziel-Applikation passiert:

- Ein Feature kann umgesetzt sein, ohne dass Product-Spec-Agent davon weiss.
- Tests koennen hinzugefuegt, geaendert oder fehlschlagen, ohne dass Task Coverage oder Feature-Status reagieren.
- Tokenverbrauch und Agent-Laeufe sind nicht nachvollziehbar.
- Abweichungen vom urspruenglichen Plan, technische Schulden oder offene Fragen bleiben im Coding-Agent-Kontext haengen.
- Product Owner sehen nicht, ob Spezifikation, Tasks und reale Umsetzung noch zusammenpassen.

## Ziel

Als Product Owner moechte ich sehen, wie weit die Ziel-Applikation mit der Umsetzung meiner Product-Spec ist, damit Product-Spec-Agent nicht nur den Start der Entwicklung begleitet, sondern auch laufend Status, Tests, Kosten und Abweichungen aus der Umsetzung kennt.

Als Coding Agent moechte ich Product-Spec-Agent ueber ein MCP-Tool aktualisieren koennen, damit ich nach jedem relevanten Implementierungsschritt Fortschritt und Evidenz strukturiert zurueckmelde.

## Goals

- Coding Agents koennen Product-Spec-Agent als MCP-Server nutzen.
- Product-Spec-Agent speichert strukturierte Living-Sync-Events pro Projekt.
- Product-Spec-Agent berechnet daraus einen aktuellen Sync-Status.
- Product Owner sehen Feature-Fortschritt, Teststatus, Tokenverbrauch und letzte Agent-Meldungen im Projekt.
- Handoff-Dateien erklaeren dem Coding Agent, wann und wie die MCP-Tools aufzurufen sind.

## Outcomes

- Ein externer Coding Agent kann per MCP melden, dass ein Feature `in_progress`, `blocked` oder `done` ist.
- Ein externer Coding Agent kann Testlaeufe mit Status, Zahlen und kurzer Zusammenfassung melden.
- Ein externer Coding Agent kann Tokenverbrauch pro Agent, Modell und Task melden.
- Ein externer Coding Agent kann geaenderte Dateien und optionale Commit-Hashes melden.
- Product-Spec-Agent zeigt einen Living-Sync-Status pro Projekt an.
- Handoff enthaelt MCP-Verbindungsdaten und Tool-Regeln fuer Rueckmeldungen.

## V1 Scope

### MCP-Tools

| Tool | Zweck |
|---|---|
| `get_project_sync_context` | Liefert Projekt-ID, Features, Tasks, Handoff-Kontext und letzte Sync-Zusammenfassung. |
| `report_feature_progress` | Meldet Feature-Status mit Zusammenfassung und Evidenz. |
| `report_test_run` | Meldet Testkommando, Ergebnis, Anzahl bestandener/fehlgeschlagener Tests und kurze Ausgabe. |
| `report_token_usage` | Meldet Tokenverbrauch pro Agent, Modell, Task und Zeitraum. |
| `report_code_changes` | Meldet geaenderte Dateien, optionale Commits und Zusammenfassung. |
| `report_sync_note` | Meldet Blocker, Abweichungen, offene Fragen oder technische Schulden. |

### Datenmodell

```kotlin
enum class LivingSyncEventType {
    FEATURE_PROGRESS,
    TEST_RUN,
    TOKEN_USAGE,
    CODE_CHANGES,
    SYNC_NOTE
}

enum class LivingSyncFeatureStatus {
    PLANNED,
    IN_PROGRESS,
    BLOCKED,
    DONE
}

data class LivingSyncEvent(
    val id: String,
    val projectId: String,
    val type: LivingSyncEventType,
    val featureId: String? = null,
    val taskId: String? = null,
    val agentName: String? = null,
    val model: String? = null,
    val status: String? = null,
    val summary: String,
    val evidence: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val commits: List<String> = emptyList(),
    val testCommand: String? = null,
    val testsPassed: Int? = null,
    val testsFailed: Int? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val createdAt: String
)
```

### Backend

- Neuer MCP-Endpoint im Backend, z. B. `/mcp`, fuer `tools/list` und `tools/call`.
- Spring AI MCP bleibt eine moegliche Transport-Haertung, wenn die Dependency gegen Spring Boot 4 validiert ist.
- Neue Domain-Modelle fuer Living-Sync-Events und Summary.
- Neuer Storage unter `projects/{projectId}/sync/events/*.json`.
- Neuer `LivingSyncService` zum Speichern von Events und Berechnen der Summary.
- Neue REST-Lese-API fuer das Frontend, z. B. `GET /api/v1/projects/{projectId}/living-sync`.

### Frontend

- Neuer Workspace-Tab oder Panel `Living Sync`.
- Anzeige:
  - letzter Sync-Zeitpunkt
  - Feature-Status
  - letzte Testlaeufe
  - Tokenverbrauch
  - geaenderte Dateien/Commits
  - Blocker und Sync-Notes
- V1 ist read-only. Manuelle Korrekturen bleiben out of scope.

### Handoff

`CLAUDE.md` und `AGENTS.md` erhalten eine Living-Sync-Sektion:

- MCP-Server-URL
- Projekt-ID
- verfuegbare Tools
- Regel: Nach jedem relevanten Implementierungsschritt ein passendes MCP-Tool aufrufen.
- Regel: Testlaeufe und Tokenverbrauch melden, wenn sie bekannt sind.
- Regel: Blocker und Planabweichungen sofort als `report_sync_note` melden.

## Option: Automatischer Git-Import

Automatischer Git-Import ist eine ausdrueckliche spaetere Option, aber nicht Teil von V1.

In einer spaeteren Version koennte Product-Spec-Agent ein Ziel-Repository selbst anbinden:

- Remote-Repository-URL und Branch pro Projekt speichern.
- Repository periodisch oder manuell aktualisieren.
- Neue Commits und Diffs auslesen.
- Geaenderte Dateien Features oder Tasks zuordnen.
- Testdateien und Testaenderungen erkennen.
- Aus Commit-Metadaten und Diffs automatisch Living-Sync-Events ableiten.

Diese Option ist hilfreich, wenn Coding Agents unvollstaendig reporten oder wenn Product-Spec-Agent unabhaengig vom Agenten den realen Codezustand pruefen soll. Fuer V1 bleibt MCP-Reporting fuehrend, weil der Coding Agent den Kontext seiner Arbeit direkt kennt und strukturierter berichten kann als ein nachtraeglicher Diff-Parser.

## Out of Scope fuer V1

- Automatischer Git-Import.
- Automatische Codeanalyse oder Feature-Erkennung aus Diffs.
- Automatisches Setzen von `done` ohne Agent-Evidenz.
- OAuth-Server-Flow fuer MCP.
- Bidirektionale Konfliktloesung zwischen PSA-Status und Ziel-App-Status.
- Schreibende UI-Korrekturen an Sync-Events.

## Acceptance Criteria

- [ ] Backend startet einen MCP-Server mit den Living-Sync-Tools.
- [ ] `get_project_sync_context` liefert Projektkontext und aktuelle Sync-Summary.
- [ ] `report_feature_progress` speichert ein Event und aktualisiert die Feature-Summary.
- [ ] `report_test_run` speichert Teststatus, Kommando, Zaehler und Kurzfassung.
- [ ] `report_token_usage` speichert Tokenmetriken und aggregiert Gesamtverbrauch.
- [ ] `report_code_changes` speichert Dateien, optionale Commits und Zusammenfassung.
- [ ] `report_sync_note` speichert Blocker, Abweichungen oder technische Schulden.
- [ ] `GET /api/v1/projects/{projectId}/living-sync` liefert eine Summary fuer die UI.
- [ ] Frontend zeigt Living-Sync-Daten in einem Projekt-Panel an.
- [ ] Handoff-Dateien enthalten MCP-Setup und Reporting-Regeln.
- [ ] Backend-Tests decken Event-Speicherung, Summary-Berechnung und Tool-Validierung ab.

## Technische Hinweise

- MCP kann in V1 stateless ueber JSON-RPC `tools/list` und `tools/call` angeboten werden; Spring AI MCP bietet spaeter einen WebMVC/Streamable-HTTP-Server mit `@McpTool`.
- MCP-Requests sollten in deployten Umgebungen ueber ein Bearer-Token oder bestehende Auth abgesichert werden.
- Events sollten append-only gespeichert werden. Die Summary wird aus Events berechnet oder gecacht.
- Tool-Parameter sollten stabil und klein bleiben, damit Coding Agents sie verlaesslich ausfuellen koennen.
- Tokenverbrauch ist optional, weil nicht jeder Agent oder jedes Modell alle Metriken liefert.

## Abhaengigkeiten

- Feature 8: Agent-ready Handoff.
- Feature 30: Statische Handoff-Sync-URL.
- Feature 31: Project Storage auf S3.
- Feature 44: Akzeptanzkriterien im Feature-Edit-Modal, optional fuer spaetere Statuszuordnung.

## Aufwand

L (Large): neuer MCP-Server, neue Domain-/Storage-Schicht, neue Summary-API, Handoff-Erweiterung, Frontend-Panel und Tests.
