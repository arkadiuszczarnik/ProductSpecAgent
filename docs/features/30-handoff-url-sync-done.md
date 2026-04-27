# Feature 30 — Done: Statische CLAUDE.md mit Sync-URL im Handoff

Implementierung abgeschlossen am 2026-04-27 auf `main`. Alle Acceptance Criteria erfüllt, alle Backend-Tests grün, Browser- und `curl`-Verifikation durch User bestanden.

## Zusammenfassung der Implementierung

Die `CLAUDE.md` im Handoff wird nicht mehr dynamisch aus Specs/Decisions/Tasks zusammengebaut, sondern aus einem statischen Mustache-Template gerendert. Das Template enthält oben eine markante "How to Sync This Project"-Sektion mit URL und `curl`-Beispiel und darunter die festen Behavioral Guidelines (Think Before Coding / Simplicity First / Surgical Changes / Goal-Driven Execution).

Ein neuer `GET /api/v1/projects/{id}/handoff/handoff.zip`-Endpoint liefert eine **flache** ZIP (keinen `<slug>/`-Wrapper-Ordner), sodass der Agent vom Projekt-Root aus mit drei Befehlen synchronisieren kann:

```bash
curl -L -o handoff.zip "<url>"
unzip -o handoff.zip
rm handoff.zip
```

Der bestehende `POST /export`-Endpoint (UI-Dialog mit Edit-Funktion) behält den Wrapper-Ordner — das passt zum Use-Case "Projekt-Archiv herunterladen".

## Geänderte / neue Dateien

**Backend**
- `domain/HandoffModels.kt` — `HandoffPreview.syncUrl: String` (non-null), `HandoffExportRequest.syncUrl: String? = null`
- `export/HandoffService.kt` — Mustache-basierte `generateClaudeMd()`, `syncUrl`-Propagation, neuer `flat: Boolean = false` Parameter, `decisionService` aus Konstruktor entfernt
- `api/HandoffController.kt` — neuer `GET /handoff.zip`-Endpoint, `buildSyncUrl()`-Helper, GET nutzt `flat = true`
- `resources/templates/handoff/claude.md.mustache` (neu) — statisches Template mit `{{{projectName}}}` und `{{{syncUrl}}}`

**Backend-Tests**
- `api/HandoffControllerTest.kt` — zwei neue Tests: Template-Inhalt im POST-Export, GET-Endpoint mit Request-URL, plus Test für flache ZIP-Struktur

**Frontend**
- `lib/api.ts` — `HandoffPreview.syncUrl: string`, `HandoffExportRequest.syncUrl?: string`

## Commit-Range

`1e3001b` (vor Implementation) bis `f879034` (final). 11 Commits insgesamt:

| SHA | Beschreibung |
|---|---|
| `64f6e1f` | feat(handoff): add syncUrl field to HandoffPreview and HandoffExportRequest |
| `69ec0ad` | feat(handoff): add static claude.md.mustache template with sync URL placeholder |
| `d729a8a` | fix(handoff): use proper ß in claude.md.mustache to match codebase German variant |
| `197c448` | refactor(handoff): render CLAUDE.md from static Mustache template with syncUrl |
| `be81981` | feat(handoff): add GET /handoff.zip endpoint and propagate sync URL |
| `780c941` | feat(frontend): add syncUrl to HandoffPreview type |
| `8fc3111` | feat(frontend): mirror syncUrl?: string on HandoffExportRequest type |
| `617dae4` | feat(handoff): serve flat ZIP from GET /handoff.zip for in-place agent sync |
| `f879034` | docs(handoff): clean up handoff.zip after extraction in sync snippet |

(Plus zwei Spec/Plan-Doc-Commits am Anfang.)

## Abweichungen vom ursprünglichen Plan / Spec

1. **`flat: Boolean`-Parameter und flache GET-ZIP** waren in der ursprünglichen Spec nicht enthalten. Nach Browser-Verifikation hat der User bemerkt, dass der `<slug>/`-Wrapper-Ordner beim `unzip` im Agent-Workflow stört. Der Parameter wurde nachträglich hinzugefügt (Commit `617dae4`), POST-Endpoints bleiben unverändert.
2. **`rm handoff.zip` im Snippet** wurde ebenfalls als Refinement nachgereicht (Commit `f879034`), damit der Agent nach Sync keinen ZIP-Cruft im Projekt-Root hinterlässt.
3. **`HandoffExportRequest.syncUrl?: string` im Frontend** wurde nach dem Final-Code-Review als Symmetrie-Fix nachgereicht (`8fc3111`). Funktional irrelevant (Frontend nutzt das Feld nicht), aber Type-Symmetrie zur Backend-Domäne hergestellt.
4. **ß-Typo-Fix:** `grösseren` → `größeren` im Template (`d729a8a`), entdeckt vom Code-Quality-Reviewer.
5. **Bewusst nicht-kompilierender Zwischen-Commit:** Der Plan hatte das vorgesehen (Task 3 ändert Service-Signaturen, Task 4 fixt den Controller). Implementiert wie geplant, kein Hook hat dagegen blockiert.

## Offene Fragen / Tech-Debt

1. **Slug-Berechnung dreifach dupliziert:** `HandoffController.kt` (×2) und `HandoffService.kt` (×1) berechnen den Slug jeweils mit derselben Regex-Logik. Folge-Refactoring-Kandidat: Extension-Property `Project.slug` oder `ProjectService.slugFor(projectId)`.
2. **`format`-Query-Parameter am GET-Endpoint** wurde bewusst weggelassen (YAGNI). Falls jemand Codex-spezifische `AGENTS.md` per `curl` syncen will, wäre `?format=codex` ein Mini-Add.
3. **Reverse-Proxy ohne `X-Forwarded-Host`/`X-Forwarded-Proto`** würde die interne Server-URL in die `CLAUDE.md` schreiben statt die öffentliche. Out-of-Scope für lokales Tool, kann später per `app.public-base-url` Property nachgereicht werden.
4. **Keine `Cache-Control: no-store`-Header** am GET-Endpoint. Aktuell unkritisch (Localhost-Setup), bei produktivem Reverse-Proxy mit Caching wäre es relevant.
5. **`HandoffPreview.syncUrl` ist `String` ohne Default** — kein Issue, solange die Klasse nur serialisiert und nicht deserialisiert wird. Bei zukünftiger Inter-Service-Roundtrip-Nutzung (Caching, Replay) müsste ein Default ergänzt werden.

## Verifikation

- Alle 8 Tests in `HandoffControllerTest` grün (`./gradlew test --quiet` BUILD SUCCESSFUL).
- TypeScript: `npx tsc --noEmit` ohne Fehler.
- ESLint: keine neuen Fehler durch das Feature.
- Browser-/`curl`-Verifikation durch den User bestanden: HandoffDialog zeigt das neue Template, ZIP-Export funktioniert, `curl`-aufgerufene Sync-URL liefert flache ZIP, der Snippet `unzip -o handoff.zip && rm handoff.zip` läuft sauber im Projekt-Root durch.
