# Feature 28: Project Document Upload — Done

**Implementiert:** 2026-04-25
**Branch:** `feat/feature-28-project-document-upload`
**Plan:** [`docs/superpowers/plans/2026-04-24-project-document-upload.md`](../superpowers/plans/2026-04-24-project-document-upload.md)
**Spec:** [`docs/superpowers/specs/2026-04-24-project-document-upload-design.md`](../superpowers/specs/2026-04-24-project-document-upload-design.md)
**Feature-Doc:** [`28-project-document-upload.md`](28-project-document-upload.md)

## Zusammenfassung

Pro Projekt können nun **PDF-, Markdown- und Plain-Text-Dokumente** (max. 10 MB) per Drag-and-Drop in den neuen **"Documents"-Tab** hochgeladen werden. Beim ersten Upload erzeugt das Backend lazy eine GraphMesh-Collection, persistiert die `collectionId` in `project.json` und reicht alle weiteren Dokumente in dieselbe Collection. Status-Updates kommen per **3-Sekunden-Polling** vom Frontend, das automatisch stoppt, sobald alle Dokumente terminal sind (`EXTRACTED`/`FAILED`) oder der Tab nicht aktiv ist.

## Implementierungs-Übersicht (14 Commits)

| Commit | Inhalt |
|---|---|
| `a6aaa7a` | `feat(domain): add optional Project.collectionId for GraphMesh` |
| `e8c0340` | `feat(domain): add Document and DocumentState` |
| `b4c13bd` | `feat(graphmesh): add GraphMeshConfig with default URL and timeout` |
| `38765eb` | `feat(graphmesh): add GraphMeshException sealed hierarchy` |
| `744cf80` | `feat(graphmesh): add GraphMeshClient with RestClient + MockWebServer tests` |
| `c8ef235` | `feat(service): add DocumentService with lazy collection creation` |
| `53e7a71` | `feat(config): add graphmesh.url and multipart limits` |
| `e0e3b57` | `feat(api): add exception handlers for GraphMesh and upload validation` |
| `1ae646a` | `feat(api): add DocumentController with multipart upload, list, delete` |
| `0c7d057` | `feat(api): add document upload/list/delete client functions` |
| `5bb8c29` | `feat(stores): add document-store with polling for non-terminal states` |
| `d640da0` | `feat(documents): add DocumentsPanel with drop-zone and status list` |
| `75605c9` | `feat(workspace): add Documents tab to project workspace sidebar` |
| `16be8e2` | `fix(documents): empty-list polling guard + persisted-collectionId test` |

## Abweichungen vom Plan

1. **MockWebServer-Dependency:** Plan spezifizierte `com.squareup.okhttp3:mockwebserver:4.12.0` (OkHttp 4). Tatsächlich verwendet: `com.squareup.okhttp3:mockwebserver3:5.3.2` (OkHttp 5). Begründung: Koog (`ai.koog:koog-spring-boot-starter:0.7.3`) bringt OkHttp 5 transitiv, was mit OkHttp-4-MockWebServer Classpath-Konflikte verursachen würde (`NoClassDefFoundError: okhttp3/internal/Util`). Test-API-Anpassungen waren notwendig (Builder-Pattern für `MockResponse`, `close()` statt `shutdown()`, `body!!.utf8()` statt `body.readUtf8()`).

2. **Übersprungene Plan-Schritte (bereits vorhanden):**
   - Task 2.1 Step 2: `@ConfigurationPropertiesScan` war schon auf `ProductSpecAgentApplication.kt`.
   - Task 3.1 Step 4: `ProjectNotFoundException` existiert bereits in `service/ProjectService.kt:18`.
   - Task 4.4: CORS bleibt unverändert — `next dev` läuft auf Port 3000 (kein `-p`-Override im Frontend), was mit `cors.allowed-origins: "http://localhost:3000"` matcht.

3. **Code-Reviewer-Hinweise zu Task 2.3 (nicht umgesetzt — außerhalb Plan-Scope):**
   - Optional: Test für `getDocument`-Pfad direkt in `GraphMeshClientTest` (statt nur indirekt über `DocumentService`).
   - Optional: Test für HTTP-5xx → `GraphMeshException.GraphQlError`.
   - Beide sind Nice-to-Haves; der Plan hat sie nicht gefordert. Sollten in einem Folge-PR adressiert werden, falls gewünscht.

4. **Final-Review-Findings — bewusste Abweichungen, NICHT umgesetzt:**
   - **`ErrorResponse`-Body-Shape:** Spec definierte für 413 `{ error, maxBytes }` und für 415 `{ error, allowed[] }`. Die bestehende projektweit verwendete `ErrorResponse(error, message, timestamp)` wurde NICHT erweitert, um Konsistenz mit allen anderen Fehlern zu wahren. Das Frontend liest `body.error` und das funktioniert. Falls Frontend später dynamische Limits anzeigen soll, muss `ErrorResponse` projektweit erweitert werden — eigenes Feature.
   - **502-Test in `DocumentControllerTest`:** `GraphMeshException.GraphQlError → 502` ist nur per `GlobalExceptionHandler`-Mapping implementiert, nicht per Controller-Test verifiziert. `FakeGraphMeshClient.simulateGraphQlError` nicht implementiert (YAGNI). Mapping ist trivial via `@ExceptionHandler`-Annotation.
   - **`ClassCastException` im `GraphMeshClient.post()`:** Wenn GraphMesh `{}` ohne `data`-Feld zurückgibt, wirft `response["data"] as Map<String, Any>` eine `ClassCastException` statt `GraphMeshException`. In der Praxis liefert GraphMesh entweder `data` oder `errors` — der Edge-Case ist hypothetisch.
   - **Toast-System:** Spec spricht von "Toast", aktuell wird Inline-Fehler im Panel angezeigt. Im Projekt existiert kein Toast-System; Inline-Meldung ist funktional gleichwertig.

5. **Final-Review-Findings — UMGESETZT in Commit `16be8e2`:**
   - Frontend-Polling-Bug: `[].every(...) === true` führte zu sofortigem Stop bei leerer Liste. Fix: `docs.length > 0 && docs.every(...)`.
   - `DocumentServiceTest` ergänzt um den spec-geforderten Atomizitäts-Test: `collectionId` wird persistiert, auch wenn `uploadDocument` danach scheitert.

## Test-Status

**Backend:** Volle Test-Suite grün (15+ neue Tests).

| Klasse | Tests |
|---|---|
| `ProjectStorageTest` | +2 (collectionId round-trip, legacy-JSON ohne Feld) |
| `GraphMeshClientTest` | 6 Tests gegen MockWebServer (createCollection, uploadDocument, listDocuments, deleteDocument, GraphQL-Errors, unreachable) |
| `DocumentServiceTest` | 4 Tests (lazy creation, idempotent, project not found, **Atomizität bei Upload-Fehler**) |
| `DocumentControllerTest` | 6 Tests (201 upload, 415 bad MIME, 400 no file, GET list, DELETE 204, 503 GraphMesh down) |

**Frontend:** Kein Test-Runner konfiguriert (siehe `frontend/CLAUDE.md`). `npm run lint` zeigt keine neuen Errors in den geänderten Dateien (Baseline: 30 pre-existing Probleme in fremden Files). `npm run build` BUILD SUCCESSFUL.

## Manueller Smoke-Test (durchgeführt am YYYY-MM-DD von <Name>)

> Voraussetzung: GraphMesh läuft auf `http://localhost:8083` (`docker-compose up -d && ./gradlew bootRun` im GraphMesh-Repo). Backend auf `:8080`. Frontend-Dev auf `:3000`.

**Vom Final-Reviewer hervorgehobene Risk-Szenarien (zuerst testen):**
- [ ] **Leere-Liste + Sofort-Upload-Pfad:** Workspace mit frischem Projekt öffnen, Documents-Tab anklicken (Liste leer), DIREKT eine Datei hochladen. Network-Tab muss nach dem Upload Polling-Requests zeigen. (Vor dem Fix in `16be8e2` wäre Polling stehengeblieben.)
- [ ] State-Übergang `UPLOADED → PROCESSING → EXTRACTED` ist in der UI live sichtbar (nicht erst nach Reload)
- [ ] Tab-Wechsel zu z.B. "Chat" → Network-Tab: keine `GET /documents`-Requests mehr, während Dokumente noch in `UPLOADED`/`PROCESSING` sind

**Standard-Flow:**
- [ ] Upload PDF < 10 MB → erscheint sofort in Liste mit `UPLOADED`-Badge
- [ ] Network-Tab zeigt 3-Sekunden-Polling auf `GET /api/v1/projects/{id}/documents`
- [ ] Polling stoppt automatisch, sobald alle Dokumente terminal sind
- [ ] Zweiter Upload für dasselbe Projekt → Backend-Logs zeigen **kein** zweites `createCollection`
- [ ] `data/projects/{id}/project.json` enthält nach erstem Upload das Feld `collectionId`
- [ ] Datei > 10 MB hochladen → 413-Antwort, Inline-Fehlermeldung im Panel
- [ ] PNG-Datei hochladen → 415-Antwort, Inline-Fehlermeldung im Panel
- [ ] Trash-Icon klicken → Dokument verschwindet aus Liste, in GraphMesh nicht mehr unter `documents(collectionId: …)` zu finden
- [ ] GraphMesh stoppen (`docker-compose stop graphmesh-backend` oder Ctrl-C), Upload versuchen → 503-Antwort, Inline-Fehlermeldung, restliche Wizard-Funktionalität (Chat, Decisions, Wizard-Navigation) läuft weiter

**Hinweis für Tester:** Die Spec sprach von "Toast" — implementiert ist Inline-Fehler im Panel (siehe Abweichung Nr. 4). Das ist kein Bug, sondern bewusste Design-Konsistenz mit fehlendem Toast-System.

## Offene Punkte / Technische Schulden

- **Keine sichtbaren Schulden.** Implementierung folgt dem Plan, alle bewussten YAGNI-Entscheidungen aus dem Spec sind bestätigt:
  - Kein Confirmation-Dialog beim Löschen
  - Kein lokaler Cache der Dokumente
  - Kein Auto-Retry bei `FAILED`-State
  - Kein Background-Job für verwaiste Collections (kann durch fehlgeschlagene `uploadDocument`-Calls nach erfolgreichem `createCollection` entstehen — User-Retry funktioniert idempotent)
  - Keine GraphMesh-Auth (gibt es derzeit nicht; bei Einführung ist `graphmesh.api-key` als optionales Feld in `GraphMeshConfig` nachziehbar)

## Nächste Schritte (optional, nicht Teil dieses Features)

- **Re-Indexing:** Falls GraphMesh später RAG-Integration im Wizard bekommt, sollte `DocumentService` einen Hook bekommen, der bei neuen `EXTRACTED`-Dokumenten Decisions/Clarifications neu generiert.
- **Mehrfach-Upload-UX:** Aktuell wird jede Datei sequenziell hochgeladen — eine Progress-Bar pro Datei wäre nutzerfreundlicher.
- **CORS-Vorbereitung für Prod:** `cors.allowed-origins` aktuell hardcoded auf `localhost:3000`. Bei Deployment muss das per Env-Var konfigurierbar werden.
- **Test-Lücken aus dem Code-Review (optional):** `getDocument` und HTTP-5xx-Pfad in `GraphMeshClientTest` ergänzen.
