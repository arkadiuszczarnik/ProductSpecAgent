# Feature 23 — Simplify Project Create (Done)

## Status
Umgesetzt am 2026-04-21 auf Branch `feat/feature-23-simplify-project-create` in 5 Commits (plus 1 Cleanup), Subagent-Driven-Development-Workflow mit Spec- und Code-Quality-Reviews nach jedem Haupt-Commit.

## Zusammenfassung der Implementierung

### Backend
- **`CreateProjectRequest`** (`domain/ApiModels.kt`) — Feld `idea: String` entfernt. Nur noch `val name: String`.
- **`ProjectService.createProject(name)`** — Signatur auf einen Parameter reduziert. Zwei Verhaltensaenderungen:
  1. Initialisiert `wizard.json` mit `steps.IDEA.fields.productName = JsonPrimitive(name)`, sodass der Wizard-Step IDEA beim ersten Oeffnen den Namen bereits vorbefuellt zeigt.
  2. Legt kein `spec/idea.md` mehr beim Create an — die Datei wird erst beim IDEA-Step-Complete vom Agent erzeugt (konsistent mit `problem.md`, `target-audience.md`, etc.).
- **`ProjectController`** — uebergibt nur noch `request.name` an den Service.

### Frontend
- **`src/lib/api.ts`** — `CreateProjectRequest`-Interface hat kein `idea`-Feld mehr. Wrapper-Typ ist jetzt `{ name: string }`.
- **`src/app/projects/new/page.tsx`** — `idea`-State, die Validation-Klausel `idea.trim().length > 0`, der `<textarea id="project-idea">`-Block und der `idea`-Parameter im `createProject`-Call sind entfernt. `CardDescription` auf Deutsch: _"Gib deinem Projekt einen Namen. Die Idee beschreibst du anschließend im Wizard."_

### Tests
- 29 Backend-Testaufrufe `projectService.createProject("…", "…")` auf einen Parameter reduziert.
- 16 HTTP-Body-JSONs `{"name":"…","idea":"…"}` auf `{"name":"…"}` reduziert.
- **Neu:** `ProjectServiceTest."createProject initializes wizard IDEA productName with project name"` — assertet `wizard.IDEA.productName == JsonPrimitive("TaskFlow Pro")`.
- **Neu:** `ProjectServiceTest."createProject does not create spec idea md"` — assertet `readSpecFile(id, "idea.md") == null`.
- **Angepasst:** Zwei Tests in `FileControllerTest`, die sich auf `spec/idea.md` bzw. das `spec/`-Verzeichnis nach Create verliessen, zeigen jetzt auf `docs/features/00-feature-set-overview.md` bzw. pruefen auf die `docs/`-Directory.
- **Cleanup:** Stale Testname `createProject saves project, flowState, and idea file` → `createProject saves project and flowState`. Obsoleter Kommentar `// The idea.md is already saved by createProject` in `SpecContextBuilderTest` entfernt.

## Geaenderte Dateien

### Backend (main)
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ApiModels.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`

### Backend (tests — 22 Dateien)
- `backend/src/test/kotlin/com/agentwork/productspecagent/service/` — `ProjectServiceTest.kt`, `ConsistencyCheckServiceTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/api/` — `ProjectControllerTest.kt`, `WizardControllerTest.kt`, `WizardChatControllerTest.kt`, `ChatControllerTest.kt`, `CheckControllerTest.kt`, `TaskControllerTest.kt`, `HandoffControllerTest.kt`, `DecisionControllerTest.kt`, `ClarificationControllerTest.kt`, `ExportControllerTest.kt`, `FileControllerTest.kt`, `FeatureProposalControllerTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/agent/` — `IdeaToSpecAgentTest.kt`, `DecisionAgentTest.kt`, `PlanGeneratorAgentTest.kt`, `SpecContextBuilderTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/export/ScaffoldContextBuilderTest.kt`

### Frontend
- `frontend/src/lib/api.ts`
- `frontend/src/app/projects/new/page.tsx`

## Commit-Sequenz auf Branch

```
27f9389e  feat(frontend): /projects/new only asks for Project Name
59a746cf  refactor(frontend): createProject API wrapper takes only { name }
a5ce94f   test(backend): cleanup stale idea-file references after Task 3
ad1634b   feat(backend): createProject no longer writes empty idea.md
10bb5cb   feat(backend): createProject prefills wizard IDEA.productName
651174fe  refactor(backend): drop idea param from CreateProjectRequest and createProject
```

## Acceptance Criteria — Abdeckung
- [x] `/projects/new` zeigt nur noch **ein** Eingabefeld: Project Name
- [x] Create-Button ist aktiv, sobald `name.trim().length > 0`
- [x] Frontend `createProject`-API akzeptiert nur noch `{ name: string }`
- [x] Backend-DTO `CreateProjectRequest` hat kein `idea`-Feld mehr
- [x] `ProjectService.createProject(name)` legt `project.json`, `flow-state.json` und `docs/`-Scaffold an
- [x] `ProjectService.createProject(name)` legt **kein** `spec/idea.md` mehr an
- [x] `ProjectService.createProject(name)` initialisiert `wizard.json` mit `steps.IDEA.fields.productName = name`
- [x] Workspace-Page laedt `wizard.json`, User sieht im IDEA-Step den `Produktname` vorausgefuellt
- [x] User kann `productName` im IDEA-Step ueberschreiben — keine automatische Sync mit `project.name`
- [x] Alle bestehenden Backend-Tests, die `createProject("x", "y")` oder JSON mit `"idea"` senden, angepasst
- [x] Neuer Test: `createProject` initialisiert `wizard.json` mit `IDEA.productName = name`
- [x] Neuer Test: `createProject` legt kein `idea.md` an

## Abweichungen vom Plan
- Der Plan listete fuer Task 1 ca. 26 Test-Aufrufstellen; tatsaechlich waren es 29 (`IdeaToSpecAgentTest.kt` hatte drei weitere Aufrufe nach den im Plan genannten Zeilen — Plan-Luecke, keine inhaltliche Abweichung).
- Der Plan nannte fuer Task 3 nur **einen** FileControllerTest-Fallout (`GET file content for spec file`). Beim Testlauf trat ein zweiter Fallout zutage: `GET files includes spec directory` verlaesst sich ebenfalls auf die `spec/`-Directory nach Create, die nach Feature 23 erst beim IDEA-Step-Complete entsteht. Der Test wurde im gleichen Task umbenannt zu `GET files includes docs directory` (aequivalente Assertion-Intention: _file tree enthaelt ein erwartetes Scaffold-Verzeichnis_).
- Zwei redundante explizite Imports in `ProjectService.kt` (`WizardData`, `WizardStepData`) sind neben dem existierenden Wildcard-Import `domain.*` harmlose kosmetische Duplikate. Absichtlich nicht geaendert, weil der Plan sie explizit vorgab. Fuer eine Codebase-Cleanup-Session spaeter.

## Verifikation
- `./gradlew test` — **152 / 152 Tests gruen** (150 bestehende + 2 neue Verhaltens-Tests)
- `npm run lint` (frontend) — Lint-Baseline unveraendert (18 pre-existing errors / 17 warnings, keine neuen)
- Pro-Commit Spec-Compliance- + Code-Quality-Reviews (Subagent-Driven-Development) fuer Tasks 1–3 dokumentiert approved

## Manueller Smoke-Test
Erforderlich vor Merge auf main:
1. Backend + Frontend starten (`./start.sh` oder `docker-compose up`)
2. `http://localhost:3001/projects/new` oeffnen — nur ein Eingabefeld sichtbar, deutsche CardDescription
3. Project Name eingeben, **Create Project** klicken — Weiterleitung ins Workspace
4. Im Wizard-Step IDEA: `Produktname` ist vorausgefuellt, `Produktidee / Vision` ist leer, `Kategorie` nicht gesetzt
5. File-Explorer: `spec/idea.md` nicht vorhanden; `docs/features/00-feature-set-overview.md` vorhanden
6. IDEA-Step ausfuellen + abschliessen → Agent erzeugt `idea.md`, Flow-State advanct auf PROBLEM
