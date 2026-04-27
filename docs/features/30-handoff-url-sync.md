# Feature 30: Statische CLAUDE.md mit Sync-URL im Handoff

## Zusammenfassung

Die Handoff-`CLAUDE.md` wird vom dynamisch generierten Spec-Auszug auf einen statischen Inhalt umgestellt: oben eine markante "How to Sync This Project"-Sektion mit einer URL, über die der Agent jederzeit den aktuellen Projekt-Snapshot per `curl` neu ziehen kann; darunter feste Behavioral Guidelines (Think Before Coding / Simplicity First / Surgical Changes / Goal-Driven Execution). Ein neuer GET-Endpoint `/api/v1/projects/{id}/handoff/handoff.zip` macht das Re-Synchronisieren ohne Body und Auth möglich. `AGENTS.md` und `implementation-order.md` bleiben unverändert.

## User Stories

1. Als PO möchte ich, dass der AI-Agent in der Handoff-CLAUDE.md sofort sieht, wo er den aktuellen Projekt-Snapshot herunterladen kann.
2. Als AI-Agent möchte ich mit `curl -L -o handoff.zip "<url>"` den aktuellen Stand ziehen — kein Body, kein Auth-Setup.
3. Als PO möchte ich, dass die Verhaltensrichtlinien immer im Handoff stehen, unabhängig vom Projekt.
4. Als PO möchte ich, dass mein UI-Flow (HandoffDialog mit editierbaren Feldern) weiterhin funktioniert.

## Acceptance Criteria

- [ ] `GET /api/v1/projects/{id}/handoff/handoff.zip` liefert eine ZIP wie der bestehende `POST /handoff/export` (200, `application/zip`, korrekter `Content-Disposition`-Header).
- [ ] Aufrufbar mit `curl -L -o file.zip "<url>"` ohne weitere Parameter.
- [ ] Die `CLAUDE.md` im ZIP beginnt mit `# {projectName}`, gefolgt vom AI-Agent-Blockquote und der `## How to Sync This Project`-Sektion (URL, `curl`-Befehl, Method, Response-Übersicht, 3-Schritte-Vorgehen).
- [ ] Darunter folgen die statischen Behavioral Guidelines (4 Sektionen, wörtlich).
- [ ] Sync-URL wird aus dem aktuellen Request abgeleitet (`ServletUriComponentsBuilder`).
- [ ] `HandoffPreview` enthält neues Feld `syncUrl: String`; `HandoffExportRequest` enthält optionales `syncUrl: String?`.
- [ ] User-Edits an `claudeMd` im Dialog werden weiterhin honoriert (bestehende Override-Logik).
- [ ] `AGENTS.md` und `implementation-order.md` unverändert.
- [ ] Backend-Tests: `HandoffServiceTest` (Template-Rendering) + `HandoffControllerTest` (GET-Endpoint via `@WebMvcTest`).
- [ ] Manuell verifiziert: Dialog → CLAUDE.md-Tab zeigt das neue Template; ZIP-Export funktioniert; `curl` mit der eingebetteten URL liefert eine valide ZIP.

## Technische Details

### Backend

**Geänderte Dateien**

- `api/HandoffController.kt` — neuer `@GetMapping("/handoff.zip")`-Handler, baut Sync-URL aus dem aktuellen Request via `ServletUriComponentsBuilder.fromRequest(request)`.
- `export/HandoffService.kt` — `generateClaudeMd()` lädt das neue Mustache-Template und rendert `projectName` + `syncUrl`. `generatePreview()` und `exportHandoff()` reichen `syncUrl` durch.
- `domain/HandoffModels.kt` — `HandoffPreview` bekommt `syncUrl: String`; `HandoffExportRequest` bekommt `syncUrl: String? = null`.

**Neue Dateien**

- `src/main/resources/templates/handoff/claude.md.mustache` — statisches Template mit `{{projectName}}` und `{{syncUrl}}`.
- `src/test/kotlin/.../export/HandoffServiceTest.kt` und `.../api/HandoffControllerTest.kt` (oder Erweiterungen, falls vorhanden).

### Frontend

**Geänderte Dateien**

- `src/lib/api.ts` — `HandoffPreview`-Type um `syncUrl: string` erweitern. Keine neue Funktion nötig (GET-Endpoint wird vom Agent extern aufgerufen, nicht vom Frontend).

`HandoffDialog.tsx` bleibt strukturell unverändert.

### REST-API

| Methode | Pfad | Body | Response |
|---|---|---|---|
| `GET` | `/api/v1/projects/{id}/handoff/handoff.zip` | — | `200`, `application/zip`, ZIP-Body |
| `POST` | `/api/v1/projects/{id}/handoff/preview` | — | `200`, `HandoffPreview` (jetzt mit `syncUrl`) |
| `POST` | `/api/v1/projects/{id}/handoff/export` | `HandoffExportRequest` | `200`, ZIP wie heute |

## Tests

### Backend

- `HandoffServiceTest`: Template enthält Projektname (genau einmal als H1) und Sync-URL (mindestens einmal); alle vier Behavioral-Guideline-Sektionen sind enthalten.
- `HandoffControllerTest` (`@WebMvcTest`): `GET /handoff.zip` → 200, korrekte Header, ZIP-Body parsebar, eingebettete URL stimmt.

### Frontend

Kein Test-Runner. Manuelle Browser-Verifikation:

1. HandoffDialog öffnen → CLAUDE.md-Tab zeigt das neue statische Template.
2. ZIP exportieren → entpacken → `CLAUDE.md` enthält die richtige URL und alle Behavioral Guidelines.
3. `curl -L -o test.zip "<url-aus-claude.md>"` → ZIP wird heruntergeladen.

## Abhängigkeiten

- Feature 8 (Agent-ready Handoff) — implementiert; dieses Feature ist eine Evolution.
- Feature 5 (Git-Repository Output) und Feature 0 (Project Setup) — Voraussetzungen für den bestehenden Export-Pfad.

## Aufwand

S–M (Small to Medium): ein neuer Endpoint, ein Template, ein Service-Refactor, zwei Tests, ein Frontend-Type-Update.

## Out of Scope

- Inkrementeller / Delta-Sync.
- Bidirektionaler Sync.
- Auth/Token für die Sync-URL.
- "Copy URL"-Button im Dialog.
- Format-Parameter am GET-Endpoint.
- Konfigurierbare Public-Base-URL via Property.
