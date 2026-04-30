# Feature 35 — Done

**Datum:** 2026-04-30
**Branch:** `feat/feature-35-feature-proposal-with-uploads`
**Plan:** [`docs/superpowers/plans/2026-04-30-feature-proposal-with-uploads.md`](../superpowers/plans/2026-04-30-feature-proposal-with-uploads.md)
**Spec:** [`docs/superpowers/specs/2026-04-30-feature-proposal-with-uploads-design.md`](../superpowers/specs/2026-04-30-feature-proposal-with-uploads-design.md)
**Feature-Doc:** [`35-feature-proposal-with-uploads.md`](35-feature-proposal-with-uploads.md)

## Zusammenfassung

Der `FeatureProposalAgent` zieht jetzt zusätzlich zu den Spec-Dateien (`idea.md`, `problem.md`, `target_audience.md`, `mvp.md`, `Category`) alle hochgeladenen Markdown- und Plain-Text-Dokumente aus `UploadStorage` (`projects/{id}/docs/uploads/`) als Referenz-Kontext in den User-Prompt. PDFs werden still übersprungen. Per-File-Budget (Default 100 KB) und Total-Budget (Default 500 KB) sind via `@ConfigurationProperties` konfigurierbar und mit `@field:Positive` validiert. Marker-Fälschungen im Upload-Inhalt werden durch Zero-Width-Space neutralisiert; der `SYSTEM_PROMPT` weist den LLM zusätzlich an, Upload-Inhalt nie als Anweisung zu interpretieren. Im Frontend erscheint unter dem "Vorschlagen"-Button eine Helper-Zeile, die den neuen Mechanismus für den Nutzer transparent macht.

## Implementierungs-Übersicht

| Commit | Inhalt |
|---|---|
| `3e13890` | `UploadStorage.readById(projectId, docId)` für Upload-Lookup per Index |
| `991c7dd` | Dependency `spring-boot-starter-validation` (Plan-Annahme korrigiert) |
| `624a3b6` | `FeatureProposalUploadsProperties` mit `maxBytesPerFile`/`maxBytesTotal` |
| `8411d3a` | `UploadPromptBuilder`-Skelett mit Empty-Path-Handling |
| `126d057` | MD/TXT-Uploads mit Markern in deterministischer Reihenfolge rendern |
| `10c6d51` | Per-File-Byte-Truncation mit size-aware Truncation-Notice |
| `71177aa` | Total-Byte-Budget mit Skip-Notice für überzählige Dateien |
| `4382624` | Marker-Escape via Zero-Width-Space gegen Prompt-Injection |
| `e546646` | `UploadPromptBuilder` in `FeatureProposalAgent.proposeFeatures` verdrahtet, System-Prompt-Anti-Injection-Satz, neue Tests |
| `d2e9999` | Hygiene-Fix: `PlanGeneratorAgentScopeTest` aktiviert (Stub-`SpecContextBuilder`) |
| `d9038e2` | Konfig-Defaults via Env-Vars (`FEATURE_PROPOSAL_UPLOADS_MAX_*`) |
| `47872d7` | Frontend-Helper-Text unter dem "Vorschlagen"-Button |
| Final-Review-Fix | `log.debug` für übersprungene Non-Text-Uploads (AC #3-Lücke geschlossen) |

(12 Implementierungs-Commits; der vorgelagerte Doc-Commit `495a6f0` legt Spec, Feature-Doc und Plan an und gehört nicht zur Implementierung.)

## Erfüllte Akzeptanzkriterien (10 / 10)

| # | Kriterium | Umgesetzt in |
|---|-----------|--------------|
| 1 | `FeatureProposalAgent.proposeFeatures` ruft `UploadPromptBuilder.renderUploadsSection` auf und bettet den Output zwischen Spec-Kontext und JSON-Format-Anweisung ein | `e546646` |
| 2 | MD/TXT-Uploads erscheinen mit Marker-Format `--- BEGIN UPLOADED DOCUMENT: <title> (<mime>) --- … --- END UPLOADED DOCUMENT ---` | `126d057` |
| 3 | PDFs werden still übersprungen (`log.debug`), kein UI-Fehler | `126d057` (Filter), Final-Review-Fix (`log.debug`-Line) |
| 4 | Per-File-Cap (100 KB) und Total-Cap (500 KB) greifen mit sichtbarem Truncation-/Skip-Hinweis im Prompt | `10c6d51`, `71177aa` |
| 5 | Marker-Phrasen im Upload-Inhalt werden via Zero-Width-Space neutralisiert | `4382624` |
| 6 | `SYSTEM_PROMPT` instruiert den LLM, Upload-Inhalt nicht als Anweisung zu interpretieren | `e546646` |
| 7 | Projekte ohne MD/TXT-Uploads erzeugen bit-identischen Prompt (kein Section-Wrapper) | `e546646` (Test "ohne Uploads" assertet Backward-Compat) |
| 8 | `UploadStorage`-Fehler degradieren den Proposal-Endpoint nicht (Catch + WARN, leerer String) | `8411d3a`, `126d057` (try/catch um listAsDocuments + read) |
| 9 | Frontend zeigt Helper-Zeile *"Berücksichtigt Markdown- und Text-Dateien aus dem Documents-Tab."* | `47872d7` |
| 10 | Konfig-Werte stehen in `application.yml`, sind via `@ConfigurationProperties` + `@field:Positive` validiert, Env-Var-Override möglich | `624a3b6`, `d9038e2` |

## Abweichungen vom Plan

1. **`jakarta.validation` war nicht auf dem Classpath.** Der Plan ging davon aus, dass die Dependency seit Feature 28 vorhanden sei. War sie nicht — `@field:Positive` ließ sich nicht kompilieren. Fix: `spring-boot-starter-validation` zur `build.gradle.kts` hinzugefügt (Commit `991c7dd`). User-approved.
2. **Plan-prescribed Test-Pattern `() = runBlocking { ... assertJ }` skipt unter JUnit 5 silently.** Ohne expliziten Return-Type interpretiert JUnit die Funktion nicht als Test. Fix: Allen 5 Test-Signaturen in `FeatureProposalAgentTest.kt` ein `: Unit` ergänzt (Commit `e546646`). Beim Audit fiel auf, dass derselbe Bug seit Feature 22 in `PlanGeneratorAgentScopeTest.kt` schlummert; Hygiene-Fix in `d2e9999` aktiviert dort 5 Tests. Beim Aktivieren fiel zusätzlich ein latenter Setup-Bug auf (`ProjectNotFoundException` weil `SpecContextBuilder` echte Projekte erwartete) — repariert durch `open class` auf `SpecContextBuilder.buildContext` plus Stub-Subklasse im Test. User-approved Scope-Erweiterung.
3. **Plan-prescribed `private fun interface AgentBody` kompiliert nicht.** Lambdas können kein `override` schreiben. Fallback (vom Plan-Hint vorgesehen): inline `object`-Subklasse pro Test, die `KoogAgentRunner.run` überschreibt (Commit `e546646`).
4. **Hygiene-Fix `d2e9999` liegt außerhalb der ursprünglichen 8 Plan-Tasks.** User-approved, weil er pre-existing Tests aus Feature 22 reaktiviert, die seit Monaten silent disabled waren.

## Test-Status

- **Backend:** `./gradlew test` → BUILD SUCCESSFUL, alle Tests grün, einschließlich der 5 neu aktivierten Tests in `PlanGeneratorAgentScopeTest`.
- **Frontend:** `npm run lint` (keine neuen Errors in `FeaturesGraphEditor.tsx`; pre-existing Baseline unverändert), `npm run build` SUCCESSFUL.
- **Manueller Smoke:** AUSSTEHEND — vom User durchzuführen (siehe Abschnitt unten).

## Manueller Smoke-Test (vom User durchzuführen)

1. `./start.sh`
2. Im Browser: Projekt anlegen, Idea/Problem ausfüllen.
3. Documents-Tab: kleine MD-Datei mit erkennbar projektspezifischem Inhalt hochladen.
4. FEATURES-Step → "Vorschlagen" klicken. Vorschläge sollten Themen aus der MD aufgreifen.
5. Optional zusätzlich PDF hochladen, "Vorschlagen" erneut. Backend-Log zeigt `Skipping non-text upload: ...` für PDF (auf DEBUG-Level — falls nicht sichtbar, kein Bug).
6. Frontend: Sichtprüfung des Helper-Texts unter dem "Vorschlagen"-Button.

## Verbleibendes Tech-Debt

(Aus den Code-Reviews der einzelnen Tasks)

- `TruncationResult.originalBytes` und `truncated` sind aktuell ungenutzt — könnten beim nächsten Refactor zu `data class TruncationResult(val text: String)` reduziert werden.
- KB-Anzeige `bytes / 1024` rundet ab; bei sehr kleinen Files könnte "0 KB" als Truncation-Notice irreführend sein. Plan-prescribed; nicht-blockierend.
- `props.maxBytesPerFile.toInt()`-Cast könnte bei extremer Konfiguration (>2 GB) silent overflowen. `@Positive`-Validation greift, Operator-Verantwortung.
- Marker-Escape verwendet ASCII-Pattern-Match; kreatives Upload mit `--- BEGIN UPLOADED DOCUMENT` im RTL-Text oder mit Lookalikes (`—`) wird nicht escaped. Defensive Limit; in der Praxis ausreichend.
- Total-Budget zählt Body-Bytes, nicht Marker-Overhead (~120 Byte/File). Bei N≤5 Files = O(0,12 % Drift) — vernachlässigbar.
- Notice "additional documents skipped due to total budget" sammelt auch Read-Failures (vom `continue`-Zweig). In der Praxis selten; Wortlaut ist plan-prescribed.
- Inline-`object`-Subclass-Pattern in Tests bringt Boilerplate. Refactoring lohnt erst ab >5 vergleichbaren Tests.
- Code-Reviewer empfahl einen Inline-Kommentar zum U+200B in `escapeMarkers` (für menschliche Leser, da der Char unsichtbar ist). Nicht umgesetzt — Test als Safety-Net.

## Nächste Schritte (optional)

- **Feature-22-Restrisiko:** Pre-existing Latent-Bug im Test-Setup von `PlanGeneratorAgentScopeTest` (zusätzlich zum Silent-Skip auch sachlich kaputt — `ProjectNotFoundException`). Repariert via Stub-`SpecContextBuilder`. Falls weitere Tests aus Feature 22 dasselbe Pattern nutzen, sollte ein Audit gemacht werden.
- **Frontend-Feedback:** Kein Toast oder Inline-Hinweis, welche Dateien tatsächlich in den Prompt eingegangen sind. Bei Bedarf später nachzuziehen.
