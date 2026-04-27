# Design: Statische CLAUDE.md mit Sync-URL im Handoff

## Zusammenfassung

Beim Handoff zu Claude/Codex/Custom enthält die generierte `CLAUDE.md` heute eine dynamische Spec-Zusammenfassung, die aus den Projekt-Dokumenten gebaut wird. Das ist Doppelarbeit (`SPEC.md` enthält dieselben Inhalte) und wenig nützlich für den Agent. Stattdessen wird `CLAUDE.md` zu einem statischen Template mit zwei dynamischen Stellen: dem Projektnamen und einer **Sync-URL**, über die der Agent jederzeit den aktuellen Snapshot des Projekts per `GET` abholen kann. Der statische Inhalt besteht aus einer markanten "How to Sync"-Sektion am Anfang plus den vorgegebenen Behavioral Guidelines. Ein neuer GET-Endpoint `/api/v1/projects/{id}/handoff/handoff.zip` macht das Re-Synchronisieren mit `curl`/`wget` trivial möglich. Der bestehende UI-Flow (`HandoffDialog` mit Edit-Funktion + `POST /handoff/export`) bleibt funktional unverändert.

## User Stories

1. Als PO möchte ich, dass der AI-Agent in der Handoff-CLAUDE.md sofort sieht, wo er den aktuellen Projekt-Snapshot herunterladen kann, damit er seine lokale Kopie zwischen Sessions auffrischen kann.
2. Als AI-Agent (Claude/Codex/Custom) möchte ich mit einem einzigen `curl`-Befehl den aktuellen Stand ziehen, ohne Auth-Setup oder Body-Konstruktion.
3. Als PO möchte ich, dass die Verhaltensrichtlinien (Think Before Coding, Simplicity, Surgical Changes, Goal-Driven Execution) **immer** im Handoff stehen — unabhängig vom Projekt — damit jeder Agent dieselben Disziplin-Defaults bekommt.
4. Als PO möchte ich, dass mein UI-Flow (Handoff-Dialog mit editierbaren Feldern) weiterhin funktioniert.

## Acceptance Criteria

- [ ] Neuer Endpoint `GET /api/v1/projects/{id}/handoff/handoff.zip` liefert eine ZIP wie der bestehende `POST /handoff/export` (Status `200`, `Content-Disposition: attachment; filename="<slug>-handoff.zip"`, MIME `application/zip`).
- [ ] Der GET-Endpoint braucht weder Body noch Query-Parameter; mit `curl -L -o file.zip "<url>"` funktioniert es.
- [ ] Die im ZIP enthaltene `CLAUDE.md` enthält oben:
  - `# {projectName}`
  - Blockquote `> **AI Agent — read this entire file before doing anything else.**`
  - Sektion `## How to Sync This Project` mit URL, `curl`-Befehl, Method-Description, Response-Übersicht und 3-Schritte-Vorgehen.
- [ ] Darunter folgen die statischen **Behavioral Guidelines** (4 Sektionen: Think Before Coding / Simplicity First / Surgical Changes / Goal-Driven Execution) **wörtlich** wie vom User vorgegeben.
- [ ] Die Sync-URL in `CLAUDE.md` zeigt auf den GET-Endpoint mit dem aktuellen Projekt-ID-Pfad und basiert auf der Request-URI (Spring `ServletUriComponentsBuilder`).
- [ ] Der bestehende `POST /handoff/preview`-Response enthält ein neues Feld `syncUrl: String`, das der UI angezeigt wird (Anzeige selbst optional, siehe Out of Scope).
- [ ] Wenn der User `claudeMd` im Dialog editiert und über `POST /handoff/export` schickt, wird der editierte Inhalt verwendet (bestehende Override-Logik in `HandoffService.exportHandoff()`).
- [ ] `AGENTS.md` und `implementation-order.md` sind unverändert (bleiben dynamisch).
- [ ] Backend-Tests: `HandoffServiceTest` für Template-Rendering (Projektname und Sync-URL korrekt eingesetzt) + `HandoffControllerTest` (`@WebMvcTest`) für den neuen GET-Endpoint (200, Header, Body ist gültiges ZIP).
- [ ] Manuell verifiziert: HandoffDialog öffnet, CLAUDE.md-Tab zeigt das neue statische Template mit korrekter URL, ZIP-Download funktioniert, der `curl`-Befehl aus der CLAUDE.md liefert eine valide ZIP.

## Technische Details

### Geänderte Dateien

- `backend/src/main/kotlin/com/agentwork/productspecagent/api/HandoffController.kt` — neuer `@GetMapping("/handoff.zip")`-Handler. Liefert ZIP, baut `syncUrl` aus dem aktuellen Request via `ServletUriComponentsBuilder.fromRequest(request).build().toUriString()` und reicht ihn an `HandoffService.exportHandoff()` durch.
- `backend/src/main/kotlin/com/agentwork/productspecagent/export/HandoffService.kt`
  - `generateClaudeMd()` neu: lädt Mustache-Template von Classpath, rendert `projectName` + `syncUrl`. Alte dynamische Spec/Decisions/Tasks-Sektionen entfallen.
  - `generatePreview(projectId, format, syncUrl)` bekommt einen optionalen `syncUrl`-Parameter. Falls `null`, wird er aus dem aktuellen Request abgeleitet (für UI-Aufrufe).
  - `exportHandoff(projectId, request)`: nutzt `request.syncUrl` falls gesetzt, sonst den intern generierten.
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/HandoffModels.kt`
  - `HandoffPreview` bekommt Feld `val syncUrl: String`.
  - `HandoffExportRequest` bekommt optionales Feld `val syncUrl: String? = null`.
- `frontend/src/lib/api.ts` — `HandoffPreview`-Type um `syncUrl: string` erweitern. Keine neue Funktion nötig (der GET-Endpoint wird vom Agent extern aufgerufen, nicht vom Frontend).

### Neue Dateien

- `backend/src/main/resources/templates/handoff/claude.md.mustache` — statisches Template mit zwei Mustache-Variablen `{{projectName}}` und `{{syncUrl}}`.
- `backend/src/test/kotlin/com/agentwork/productspecagent/export/HandoffServiceTest.kt` (oder Erweiterung eines bestehenden Tests, falls vorhanden) — verifiziert Template-Rendering.
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/HandoffControllerTest.kt` (oder Erweiterung, falls vorhanden) — verifiziert GET-Endpoint via `@WebMvcTest` + MockMvc.

### Template-Inhalt (`claude.md.mustache`)

```markdown
# {{projectName}}

> **AI Agent — read this entire file before doing anything else.**

---

## How to Sync This Project

Dieses Projekt wird vom Product-Spec-Agent verwaltet. Die hier vorliegende Spec
ist eine **Momentaufnahme**. Wenn du Updates brauchst, hol dir die aktuelle
Version vom Service:

```bash
curl -L -o handoff.zip "{{syncUrl}}"
unzip -o handoff.zip
```

- **Sync-URL:** `{{syncUrl}}`
- **Method:** `GET` (kein Auth, kein Body)
- **Response:** ZIP mit `CLAUDE.md`, `AGENTS.md`, `implementation-order.md`,
  `SPEC.md`, `decisions/`, `clarifications/`, `tasks/`, `documents/`.

**Empfohlenes Vorgehen vor jeder grösseren Änderung:**

1. Sync ziehen (`curl ...` wie oben).
2. `git diff` auf den entpackten Files prüfen — gibt es Änderungen am Spec?
3. Falls ja: Plan anpassen, mit dem User abstimmen, dann erst implementieren.

---

## Behavioral Guidelines

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.
```

(Beim Rendering ersetzt Mustache `{{projectName}}` und `{{syncUrl}}` — der Rest ist statisch. Die innenliegenden ` ```bash ` und ` ``` ` Triple-Backticks müssen Mustache-sicher entkommen werden, falls Mustache sie als Section-Tags interpretiert; Mustache reagiert nur auf `{{...}}`, also kein Problem.)

### REST-API

| Methode | Pfad | Body | Response |
|---|---|---|---|
| `GET` | `/api/v1/projects/{id}/handoff/handoff.zip` | — | `200`, `application/zip`, `Content-Disposition: attachment; filename="<slug>-handoff.zip"`, ZIP-Body |
| `POST` | `/api/v1/projects/{id}/handoff/preview` | — | `200`, `HandoffPreview` (jetzt mit `syncUrl`) |
| `POST` | `/api/v1/projects/{id}/handoff/export` | `HandoffExportRequest` (optional `syncUrl`) | `200`, ZIP wie heute |

### URL-Berechnung

`ServletUriComponentsBuilder.fromRequest(request).build().toUriString()`:
- GET-Endpoint: liefert seinen eigenen Pfad zurück → die URL, die der Agent gerade aufgerufen hat → ideal.
- POST-Preview-Endpoint: liefert `…/handoff/preview` → das wollen wir **nicht**. Lösung: Im Preview-Endpoint die URL über `ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/projects/{id}/handoff/handoff.zip").buildAndExpand(projectId).toUriString()` konstruieren.

### Was nicht geändert wird

- `ExportService` (Basis-ZIP).
- `generateAgentsMd()` und `generateImplementationOrder()` bleiben dynamisch.
- `HandoffDialog.tsx`-UI-Struktur (Tabs, Edit-Felder). Der CLAUDE.md-Tab zeigt automatisch den neuen statischen Inhalt.
- Spring Security / Auth.
- Frontend-Typen ausser `HandoffPreview.syncUrl`.

## Tests

### Backend

- **`HandoffServiceTest`**:
  - `generateClaudeMd renders project name and sync URL`: ruft Service mit fixem Projekt + URL auf, asserted dass Output `# <name>` enthält und die URL exakt zweimal vorkommt (im `curl`-Block und im "Sync-URL:"-Listenpunkt).
  - `generateClaudeMd contains all four behavioral guideline sections`: prüft dass die H3-Header `### 1. Think Before Coding` … `### 4. Goal-Driven Execution` enthalten sind.
- **`HandoffControllerTest`** (`@WebMvcTest(HandoffController::class)`):
  - `GET /handoff.zip returns 200 with ZIP body and attachment header`.
  - `GET /handoff.zip embeds request URL into the CLAUDE.md inside the ZIP` (entpackt das ZIP im Test, liest `<slug>/CLAUDE.md`, asserted dass die im Test verwendete URL eingebettet ist).

### Frontend

Kein Test-Runner. Manuelle Browser-Verifikation:
1. HandoffDialog öffnen → CLAUDE.md-Tab → neues statisches Template sichtbar mit Projektname und URL.
2. ZIP exportieren → entpacken → `CLAUDE.md` öffnen → URL stimmt.
3. URL aus der CLAUDE.md per `curl -L -o test.zip "<url>"` aufrufen → ZIP wird heruntergeladen → Inhalt entspricht dem UI-Export.
4. URL ohne `?format=...` → liefert default `claude-code` Format.

## Out of Scope

- Inkrementeller Sync / Delta-Updates (Hash/ETag-basiert).
- Bidirektionaler Sync (Agent schreibt Spec/Tasks zurück).
- Auth / Token für die Sync-URL — der Service ist bewusst lokal/intern.
- Anzeige der Sync-URL als separates Element im HandoffDialog (URL steht ohnehin im sichtbaren CLAUDE.md-Inhalt).
- Konfigurierbare Public-Base-URL via Property — Request-Header reicht; falls später ein Reverse-Proxy ohne `X-Forwarded-Host` kommt, kann eine Property nachgereicht werden.
- "Copy URL"-Button im Dialog.
- Format-Switch im GET-Endpoint (`?format=codex`) — YAGNI bis es jemand braucht.
- Migration / Backwards-Compatibility — `HandoffPreview.syncUrl` ist neu, aber Frontend wird im selben Atemzug aktualisiert.
