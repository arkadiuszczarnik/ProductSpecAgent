# Feature 51: Feature-Done-Markdown Import via MCP — Done

Implementiert am 2026-05-12.

## Zusammenfassung

Product-Spec-Agent kann jetzt ueber die bestehende MCP-Schnittstelle eine `*-done.md` Datei fuer genau ein Projekt-Feature entgegennehmen. Ein interner PSA-Agent analysiert den Markdown-Inhalt und liefert eine strukturierte JSON-Normalform zurueck. PSA speichert den Roh-Markdown, schreibt ein eigenes Living-Sync-Import-Event und persistiert einen `FeatureCompletionSnapshot`, der im Workspace und im Living-Sync-Panel als sichtbarer Feature-Status projiziert wird.

## Implementiert

- Neuer Domain-Typ `FeatureCompletionSnapshot` inklusive `FeatureCompletionTestEvidence`.
- Neue Storage-Pfade fuer:
  - importierten Roh-Markdown unter `projects/{projectId}/sync/imports/{featureId}/{eventId}.md`
  - Feature-Snapshots unter `projects/{projectId}/sync/feature-snapshots/{featureId}.json`
- Neuer `FeatureDoneImportAgent` mit Prompt `feature-done-import-system`.
- Neues Living-Sync-MCP-Tool `import_feature_done_markdown`.
- Neuer REST-Spiegel unter `/api/v1/projects/{projectId}/living-sync/mcp/import-feature-done-markdown`.
- Neuer Living-Sync-Event-Typ `FEATURE_DONE_IMPORT`.
- `LivingSyncSummary` enthaelt jetzt `featureCompletions`.
- `LivingSyncService.getSummary()` merged:
  - Wizard-Features
  - Living-Sync-Progress-Events
  - alle gespeicherten Feature-Completion-Snapshots
- `LivingSyncPanel` zeigt Snapshot-basierten Status, Zusammenfassung, Open Points und Warnungen.
- Frontend-API-Typen wurden auf den Snapshot-Payload erweitert.
- `ProjectCockpitPrototype` wurde in seiner Mock-Sprache auf Completion-Snapshots aktualisiert.

## Abweichungen vom Feature-Dokument

- Das Frontend-Cockpit bleibt ein Prototyp mit aktualisierter Mock-Sprache; es gibt noch keine echte API-Anbindung der Cockpit-Ansicht an `featureCompletions`.
- Die Feature-Projektion im Workspace zeigt Feature-IDs weiter direkt an; eine Aufloesung auf Wizard-Titel ist nicht Teil dieser Lieferung.
- Fuer die Frontend-Verifikation wurde zusaetzlich gezieltes ESLint auf die geaenderten Dateien verwendet, weil `npm run lint` repo-weit bereits durch bestehende Fremdartefakte und Alt-Warnungen fehlschlaegt.

## Tests

- `LivingSyncStorageTest`
- `FeatureDoneImportAgentTest`
- `PromptRegistryTest`
- `LivingSyncServiceTest`
- `LivingSyncMcpControllerTest`
- `LivingSyncControllerTest`
- `npx eslint src/lib/api.ts src/components/living-sync/LivingSyncPanel.tsx src/components/cockpit/ProjectCockpitPrototype.tsx`

## Offene Punkte

- `cd frontend && npm run lint` ist weiterhin nicht gruen, aber die verbleibenden Fehler liegen ausserhalb dieses Features, vor allem in generierten Dateien unter `frontend/playwright-report/trace/assets/*` sowie in bereits bestehenden Frontend-Warnungen.
- Das Cockpit verwendet weiterhin Mock-Daten und keine echte `featureCompletions`-Abfrage.
- `FeatureCompletionTestEvidence.status` ist im Frontend aktuell noch als freier String typisiert; falls sich der Backend-Wertebereich stabilisiert, kann daraus spaeter ein engerer Union-Typ werden.
