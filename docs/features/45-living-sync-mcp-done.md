# Feature 45: Living-Sync via MCP — Done

Implementiert am 2026-05-07.

## Zusammenfassung

Product-Spec-Agent kann jetzt Living-Sync-Daten aus der Ziel-Applikation entgegennehmen. Coding Agents melden Fortschritt, Testlaeufe, Tokenverbrauch, Code-Aenderungen und Sync-Notes ueber projektbezogene Reporting-Endpunkte. Product-Spec-Agent speichert diese Events append-only und berechnet daraus eine Summary fuer das Projekt-Workspace-Panel.

## Implementiert

- Neue Domain-Modelle fuer Living-Sync-Events, Request-DTOs und Summary-DTOs.
- Neue `LivingSyncStorage`-Schicht unter `projects/{projectId}/sync/events/{eventId}.json`.
- Neuer `LivingSyncService` mit Report-Methoden fuer:
  - Feature-Fortschritt
  - Testlaeufe
  - Tokenverbrauch
  - Code-Aenderungen
  - Sync-Notes
- Neue REST/MCP-kompatible Endpunkte unter `/api/v1/projects/{projectId}/living-sync/mcp/*`.
- Neuer stateless JSON-RPC MCP-Endpunkt `/mcp` mit `tools/list` und `tools/call`.
- Neue Summary-API `GET /api/v1/projects/{projectId}/living-sync`.
- Neues Frontend-Panel `Living Sync` im Projekt-Workspace.
- Handoff-Dateien enthalten Living-Sync-Reporting-Anweisungen und projektspezifische Reporting-URLs.
- `GET /api/v1/projects/{projectId}/handoff/handoff.zip` und `POST /mcp` sind fuer Coding Agents ohne Login erreichbar.
- REST-Doku wurde um die neuen Endpunkte erweitert.

## Abweichungen vom Feature-Dokument

- V1 nutzt einen kleinen stateless JSON-RPC-MCP-Endpunkt im bestehenden Spring-WebMVC-Backend statt einer zusaetzlichen Spring-AI-MCP-Starter-Dependency. Ein Spring-AI-MCP-Transport mit Starter-Autokonfiguration bleibt als technische Haertung offen.
- Automatischer Git-Import bleibt wie geplant nur eine spaetere Option.
- Das Frontend ist read-only und erlaubt keine manuelle Korrektur von Sync-Events.

## Tests

- `LivingSyncStorageTest`
- `LivingSyncServiceTest`
- `LivingSyncControllerTest`
- `LivingSyncMcpControllerTest`
- `HandoffControllerTest.POST preview includes Living Sync reporting instructions`

## Offene Punkte

- Echten MCP-Server-Transport mit Spring AI MCP einbauen und gegen die produktive Deployment-Umgebung testen.
- Optionales Auth-Hardening fuer externe Coding Agents festlegen: Bearer Token oder spezielles Projekt-Sync-Token.
- Optionaler automatischer Git-Import als V2: Remote-Repo anbinden, Commits/Diffs lesen und daraus Events ableiten.
- Optional Feature-Status aus Living-Sync-Events in Wizard-Features oder Tasks spiegeln.
