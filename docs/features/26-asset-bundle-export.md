# Feature 26: Asset-Bundle-Export

## Zusammenfassung
Beim Project-Export werden automatisch alle Asset-Bundles aus S3, deren Triple `(step, field, value)` zu einer Wizard-Wahl des Projekts passt, in das Export-ZIP unter `.claude/{skills,commands,agents}/<bundle-id>/...` gemerged. Damit erhält der User ein „Claude-Code-ready" Projekt — die im Wizard angezeigten Tech-Entscheidungen kommen mit den passenden kuratierten Skills, Commands und Agents geliefert.

Der Mechanismus ist silent: keine UI-Interaktion, kein Opt-out. Transparenz wird durch eine generierte „Included Asset Bundles"-Sektion in der Export-`README.md` hergestellt.

## User Stories
1. Als PO möchte ich, dass ein exportiertes Projekt sofort Claude-Code-kompatible Skills für meinen Tech-Stack enthält, ohne dass ich sie manuell zusammensuchen muss.
2. Als PO möchte ich in der README transparent sehen, welche Bundles eingeschlossen wurden und auf Basis welcher Wizard-Wahl.
3. Als Bundle-Curator möchte ich, dass ein hochgeladener Bundle automatisch in alle passenden Project-Exports eingebunden wird, ohne pro Projekt etwas zu konfigurieren.

## Acceptance Criteria
- [ ] Beim Export eines Projekts werden alle Asset-Bundles aus S3 geladen, deren Triple `(step, field, value)` zu einer Wizard-Wahl passt (slugify-tolerant — `"Spring Boot"` matcht `"spring-boot"`).
- [ ] Bundle-Files werden im ZIP unter `<prefix>/.claude/<type>/<bundle-id>/<rest-of-relativePath>` abgelegt (`<type>` ∈ {`skills`, `commands`, `agents`}).
- [ ] Die Export-`README.md` erhält bei mindestens einem Match eine Sektion `## Included Asset Bundles` mit Liste pro Bundle (Title, ID, Version, Trigger-Triple, Description).
- [ ] JsonArray-Wizard-Werte (Multi-Selects) matchen pro String-Element separat — ein Wizard-Wert kann mehrere Bundles auslösen.
- [ ] Number-Wizard-Werte werden via `toString()` gegen Triple-Values gematcht.
- [ ] `JsonNull`, fehlende Steps oder Felder führen zu „kein Match", nicht zu Fehlern.
- [ ] Storage-Outage, korrupte Manifests oder fehlende Files führen zum Skippen des betroffenen Bundles (mit `WARN`-Log), niemals zum Abbruch des Project-Exports.
- [ ] Bundle-Files ausserhalb der Whitelist-Top-Dirs (`skills`, `commands`, `agents`) oder mit auffälliger Pfad-Normalisierung werden übersprungen.
- [ ] Existierende Project-Export-Tests bleiben grün (Default-Mock liefert 0 Bundles).
- [ ] Drei neue Test-Klassen (`AssetBundleExporterMatchTest`, `AssetBundleExporterZipTest`, `AssetBundleExporterReadmeTest`) plus zwei zusätzliche Cases in `ExportServiceTest`.

## Technische Details
- **Backend:** Neue Service-Klasse `AssetBundleExporter` in `backend/src/main/kotlin/com/agentwork/productspecagent/export/`. Drei Methoden: `matchedBundles(wizardData)`, `writeToZip(zip, prefix, bundles)`, `renderReadmeSection(bundles)`.
- **Backend:** Erweiterung von `ExportService.exportProject()` um Dependency-Injection des neuen Service plus drei Hooks (Wizard-Load, ZIP-Write, README-Section).
- **Frontend:** Keine Änderungen.
- **API:** Bestehender `POST /api/v1/projects/{id}/export`-Endpoint bleibt unverändert (silent always-merge).
- **Match-Algorithmus:** Bundle-driven — `storage.listAll()` als Quelle, für jeden Manifest gegen Wizard-Daten checken. Slugify-toleranter Vergleich über `assetBundleSlug()`.
- **Pfad-Mapping:** Namespaced unter `<bundle-id>/` — Konflikte zwischen Bundles physisch unmöglich.
- **Defensive Fehlerbehandlung:** Bundle-Probleme dürfen den Project-Export nie brechen; alle Fehler führen zum Skippen einzelner Bundles bzw. Files mit `WARN`-Log.

Vollständiges Design-Dokument: [`docs/superpowers/specs/2026-04-30-asset-bundle-export-design.md`](../superpowers/specs/2026-04-30-asset-bundle-export-design.md)

## Abhängigkeiten
- Sub-Feature A — Asset-Bundle-Storage-Foundation (auf `main` bei `19a853f`)
- Sub-Feature B — Asset-Bundle-Admin-UI (auf `main` bei `797d70e`)

## Aufwand
S (Small) — eine neue Klasse, drei Test-Klassen, drei kleine Touch-Points in `ExportService`.
