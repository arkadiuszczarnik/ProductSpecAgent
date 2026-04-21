# Feature 23: Simplify Project Create — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/projects/new` verlangt nur noch einen Project Name. `ProjectService.createProject(name)` initialisiert `wizard.json` mit `IDEA.productName = name` und legt kein `idea.md` mehr an.

**Architecture:** Backend-API-Signatur wird in einem atomaren Task geändert (DTO + Service + Controller + alle Test-Call-Sites gleichzeitig, damit es kompiliert). Danach werden die Verhaltens-Änderungen (Wizard-Init, kein idea.md) klassisch per TDD draufgesetzt. Frontend folgt nach, ist rein UI/API-Client.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5 + AssertJ, Next.js 16, React 19.

---

## File Structure

### Backend — geändert
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ProjectModels.kt` (oder wo `CreateProjectRequest` liegt — siehe Task 1 Schritt 1)
- `backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`

### Backend — Tests angepasst (Task 1)
- `service/ProjectServiceTest.kt`, `service/ConsistencyCheckServiceTest.kt`
- `api/ProjectControllerTest.kt`, `api/WizardControllerTest.kt`, `api/WizardChatControllerTest.kt`, `api/ChatControllerTest.kt`, `api/TaskControllerTest.kt`, `api/CheckControllerTest.kt`, `api/HandoffControllerTest.kt`, `api/DecisionControllerTest.kt`, `api/ClarificationControllerTest.kt`, `api/ExportControllerTest.kt`, `api/FileControllerTest.kt`, `api/FeatureProposalControllerTest.kt`
- `agent/IdeaToSpecAgentTest.kt`, `agent/DecisionAgentTest.kt`, `agent/PlanGeneratorAgentTest.kt`, `agent/SpecContextBuilderTest.kt`
- `export/ScaffoldContextBuilderTest.kt`

### Backend — Tests neu (Task 2, 3)
- `service/ProjectServiceTest.kt` (zwei neue `@Test`-Methoden)

### Frontend — geändert
- `frontend/src/lib/api.ts`
- `frontend/src/app/projects/new/page.tsx`

---

## Task 1: Backend — API-Signatur ändern (atomic)

**Warum atomic?** Die Signatur-Änderung an `ProjectService.createProject` und `CreateProjectRequest` zieht Compile-Fehler in allen Aufrufstellen nach sich. Alles in einen Commit, danach sind die Tests wieder grün — aber das **Verhalten** ist noch unverändert (Wizard-Init und idea.md kommen in Task 2/3).

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ProjectModels.kt` (Datei enthält `CreateProjectRequest`)
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`
- Modify: ~20 Test-Dateien (siehe File Structure oben)

- [ ] **Step 1: `CreateProjectRequest` suchen und DTO bereinigen**

Suche: `grep -n "data class CreateProjectRequest" backend/src/main/kotlin -r`

Ziel-Datei modifizieren — Feld `idea` entfernen:

```kotlin
@Serializable
data class CreateProjectRequest(val name: String)
```

- [ ] **Step 2: `ProjectController.createProject` anpassen**

Datei: `backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt`

```kotlin
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
fun createProject(@RequestBody request: CreateProjectRequest): ProjectResponse {
    return projectService.createProject(request.name)
}
```

- [ ] **Step 3: `ProjectService.createProject` Signatur ändern — Verhalten noch nicht**

Datei: `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`

Ersetze den aktuellen Body komplett (Zeilen 24–44):

```kotlin
fun createProject(name: String): ProjectResponse {
    val now = Instant.now().toString()
    val project = Project(
        id = UUID.randomUUID().toString(),
        name = name,
        ownerId = "anonymous",
        status = ProjectStatus.DRAFT,
        createdAt = now,
        updatedAt = now
    )
    val flowState = createInitialFlowState(project.id)

    storage.saveProject(project)
    storage.saveFlowState(flowState)
    storage.saveSpecStep(project.id, "idea.md", "# Idea\n\n")

    generateDocsScaffold(project.id, name)

    return ProjectResponse(project = project, flowState = flowState)
}
```
(Nur der `idea`-Parameter und die String-Interpolation `"# Idea\n\n$idea"` ändern sich. Alles andere bleibt für Task 2/3.)

- [ ] **Step 4: Alle direkten `projectService.createProject(...)`-Aufrufe in Tests anpassen**

Betroffene Dateien und Zeilen:

`service/ProjectServiceTest.kt`:
- Zeile 27: `service.createProject("My Project", "A great idea")` → `service.createProject("My Project")`
- Zeile 42: `service.createProject("Test", "Idea")` → `service.createProject("Test")`
- Zeile 58: `service.createProject("Delete Me", "Idea")` → `service.createProject("Delete Me")`
- Zeile 75: `service.createProject("P1", "Idea 1")` → `service.createProject("P1")`
- Zeile 76: `service.createProject("P2", "Idea 2")` → `service.createProject("P2")`
- Zeile 89: `service.createProject("Flow", "Idea")` → `service.createProject("Flow")`

`service/ConsistencyCheckServiceTest.kt` (Zeilen 50, 59, 76, 92, 101): je das zweite Argument entfernen.

`agent/DecisionAgentTest.kt` (Zeilen 34, 55): `createProject("Test", "An idea")` → `createProject("Test")`.

`agent/PlanGeneratorAgentTest.kt` Zeile 24: `createProject("Test", "An idea")` → `createProject("Test")`.

`agent/SpecContextBuilderTest.kt` (Zeilen 30, 39): zweites Argument entfernen.

`agent/IdeaToSpecAgentTest.kt` (Zeilen 118, 130, 158, 178, 190, 208, 226, 258, 278 — alle `createProject("Test", ...)` Aufrufe): zweites Argument entfernen.

`export/ScaffoldContextBuilderTest.kt` Zeile 60: `createProject("Test Project", "An idea")` → `createProject("Test Project")`.

- [ ] **Step 5: Alle HTTP-Body-JSON mit `"idea":"..."` in Controller-Tests anpassen**

Betroffene Dateien (das `"idea":"…"`-Segment im JSON-String entfernen — inkl. vorangehendem Komma):

- `api/ProjectControllerTest.kt` Zeilen 24, 39, 61
- `api/WizardControllerTest.kt` Zeile 21
- `api/WizardChatControllerTest.kt` Zeile 50
- `api/ChatControllerTest.kt` Zeilen 59, 85, 109
- `api/CheckControllerTest.kt` Zeile 21
- `api/TaskControllerTest.kt` Zeile 35
- `api/HandoffControllerTest.kt` Zeile 24
- `api/DecisionControllerTest.kt` Zeile 40
- `api/ClarificationControllerTest.kt` Zeile 25
- `api/ExportControllerTest.kt` Zeile 24
- `api/FileControllerTest.kt` Zeile 21
- `api/FeatureProposalControllerTest.kt` Zeile 71

Beispiel-Transformation:
```kotlin
.content("""{"name":"Wizard Test","idea":"An idea"}""")
// →
.content("""{"name":"Wizard Test"}""")
```

- [ ] **Step 6: Tests ausführen**

```bash
cd backend && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`, alle 150 Tests grün. Kein Verhaltenstest hat sich geändert — nur die API-Signatur.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/ \
        backend/src/main/kotlin/com/agentwork/productspecagent/api/ProjectController.kt \
        backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt \
        backend/src/test/
git commit -m "refactor(backend): drop idea param from CreateProjectRequest and createProject

API signature change — idea is now captured in the wizard IDEA step instead
of at project creation. Behavioural changes (wizard init, no idea.md) follow
in separate commits."
```

---

## Task 2: Backend — `createProject` initialisiert `wizard.json` mit `IDEA.productName`

TDD — zuerst der failing Test, dann die Implementation.

**Files:**
- Modify (test): `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`
- Modify (prod): `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`

- [ ] **Step 1: Failing-Test hinzufügen**

Am Ende von `ProjectServiceTest.kt` (innerhalb der Klasse) einfügen. Falls `WizardService` nicht schon als Field existiert: hinzufügen wie bei `ProjectService` (via `@TempDir` und dem vorhandenen Setup-Pattern).

Imports ergänzen:
```kotlin
import kotlinx.serialization.json.JsonPrimitive
import com.agentwork.productspecagent.service.WizardService
```

Test:
```kotlin
@Test
fun `createProject initializes wizard IDEA productName with project name`() {
    val response = service.createProject("TaskFlow Pro")
    val wizard = wizardService.getWizardData(response.project.id)
    assertEquals(
        JsonPrimitive("TaskFlow Pro"),
        wizard.steps["IDEA"]?.fields?.get("productName")
    )
}
```

Falls `wizardService` nicht im Test verfügbar ist, im Setup-Block ergänzen:
```kotlin
private lateinit var wizardService: WizardService
// in @BeforeEach:
wizardService = WizardService(storage)
```
(An den konkreten Konstruktor von `WizardService` anpassen — `grep -n "class WizardService" backend/src/main/kotlin -r`.)

- [ ] **Step 2: Test ausführen und scheitern sehen**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.service.ProjectServiceTest.createProject initializes wizard IDEA productName with project name"
```

Expected: FAIL — `wizard.steps["IDEA"]` ist `null`, weil `createProject` keine WizardData anlegt.

- [ ] **Step 3: Implementation**

Datei: `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`

Am Anfang der Datei Import ergänzen:
```kotlin
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import kotlinx.serialization.json.JsonPrimitive
```

Im `createProject(name)`-Body **nach** `storage.saveFlowState(flowState)` und **vor** `storage.saveSpecStep(...)` einfügen:

```kotlin
// Prefill wizard IDEA.productName so the user doesn't re-type the name.
val initialWizard = WizardData(
    projectId = project.id,
    steps = mapOf(
        "IDEA" to WizardStepData(
            fields = mapOf("productName" to JsonPrimitive(name))
        )
    )
)
storage.saveWizardData(project.id, initialWizard)
```

- [ ] **Step 4: Test ausführen und grün sehen**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.service.ProjectServiceTest"
```

Expected: alle `ProjectServiceTest`-Tests grün.

- [ ] **Step 5: Gesamte Suite grün halten**

```bash
cd backend && ./gradlew test
```

Expected: alle 151 Tests grün (150 alte + 1 neuer).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt
git commit -m "feat(backend): createProject prefills wizard IDEA.productName

Saves the project name into wizard.json as IDEA.productName so the user
doesn't have to retype it in the wizard IDEA step."
```

---

## Task 3: Backend — `createProject` legt kein `idea.md` mehr an

TDD — failing Test, dann remove.

**Files:**
- Modify (test): `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`
- Modify (prod): `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`
- Modify (test fallout): `backend/src/test/kotlin/com/agentwork/productspecagent/api/FileControllerTest.kt`

- [ ] **Step 1: Failing-Test hinzufügen**

Datei: `backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt`

Am Ende der Klasse einfügen:
```kotlin
@Test
fun `createProject does not create spec idea md`() {
    val response = service.createProject("TaskFlow Pro")
    assertNull(service.readSpecFile(response.project.id, "idea.md"))
}
```

Imports falls nötig:
```kotlin
import org.junit.jupiter.api.Assertions.assertNull
```

- [ ] **Step 2: Test ausführen und scheitern sehen**

```bash
cd backend && ./gradlew test --tests "com.agentwork.productspecagent.service.ProjectServiceTest.createProject does not create spec idea md"
```

Expected: FAIL — `readSpecFile` gibt `"# Idea\n\n"` zurück (nicht null), weil aktuell `createProject` idea.md leer schreibt.

- [ ] **Step 3: `saveSpecStep("idea.md", ...)` aus `createProject` entfernen**

Datei: `backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt`

Zeile entfernen:
```kotlin
storage.saveSpecStep(project.id, "idea.md", "# Idea\n\n")
```

- [ ] **Step 4: Fallout — `FileControllerTest.GET file content for spec file`**

Datei: `backend/src/test/kotlin/com/agentwork/productspecagent/api/FileControllerTest.kt` (Test Zeilen 57–64)

Der Test liest `spec/idea.md` direkt nach `createProject`. Das File existiert jetzt nicht mehr. Test anpassen — idea.md explizit via neue HTTP-POST-Route **nicht** vorhanden; stattdessen direkt einen anderen Spec-File-Pfad anlegen, der ohnehin existiert, ODER den Test-Body überflüssig machen.

Einfachster Fix — den Test so umschreiben, dass er ein spec file anlegt, bevor es gelesen wird. Wir haben aber keinen direkten Endpoint dafür — `saveSpecFile` ist Service-intern. Alternative: den Test auf eine Datei testen, die beim Create angelegt wird und Markdown ist (`docs/features/00-feature-set-overview.md`).

Test ersetzen:

```kotlin
@Test
fun `GET file content for spec file`() {
    val pid = createProject()
    mockMvc.perform(get("/api/v1/projects/$pid/files/docs/features/00-feature-set-overview.md"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("markdown"))
        .andExpect(jsonPath("$.content").isNotEmpty())
}
```

(`docs/features/00-feature-set-overview.md` wird vom Docs-Scaffold-Generator beim Create immer angelegt — siehe `generateDocsScaffold` in `ProjectService`.)

- [ ] **Step 5: Tests ausführen und alle grün sehen**

```bash
cd backend && ./gradlew test
```

Expected: alle 152 Tests grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/ProjectService.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/service/ProjectServiceTest.kt \
        backend/src/test/kotlin/com/agentwork/productspecagent/api/FileControllerTest.kt
git commit -m "feat(backend): createProject no longer writes empty idea.md

idea.md is now created by the agent on IDEA step completion, matching the
pattern of other spec files (problem.md, target-audience.md, …). Fixes
FileControllerTest to read a scaffold file that actually exists after create."
```

---

## Task 4: Frontend — `createProject` API-Wrapper anpassen

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: `createProject`-Signatur finden**

```bash
grep -n "createProject" frontend/src/lib/api.ts
```

Expected: eine Wrapper-Funktion, die `{ name, idea }` akzeptiert und an `POST /api/v1/projects` schickt.

- [ ] **Step 2: Signatur auf `{ name }` reduzieren**

Ersetze den Wrapper durch:
```typescript
export async function createProject(payload: { name: string }): Promise<ProjectResponse> {
  return apiFetch<ProjectResponse>("/api/v1/projects", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
```

Falls ein separates TypeScript-Interface `CreateProjectRequest` existiert (auch via `grep -n "CreateProjectRequest" frontend/src`): `idea`-Feld entfernen.

- [ ] **Step 3: Lint**

```bash
cd frontend && npm run lint
```

Expected: keine neuen Errors (es gibt pre-existing Lint-Warnings in anderen Dateien — ignorieren).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "refactor(frontend): createProject API wrapper takes only { name }"
```

---

## Task 5: Frontend — `/projects/new` Seite vereinfachen

**Files:**
- Modify: `frontend/src/app/projects/new/page.tsx`

- [ ] **Step 1: `idea`-State entfernen**

Entferne:
```typescript
const [idea, setIdea] = useState("");
```

- [ ] **Step 2: `canSubmit` anpassen**

Alt: `const canSubmit = name.trim().length > 0 && idea.trim().length > 0 && !loading;`
Neu: `const canSubmit = name.trim().length > 0 && !loading;`

- [ ] **Step 3: `handleSubmit`-Call anpassen**

Alt: `createProject({ name: name.trim(), idea: idea.trim() })`
Neu: `createProject({ name: name.trim() })`

- [ ] **Step 4: `<textarea id="project-idea">`-Block entfernen**

Entferne den kompletten `<div>`-Block, der Label + Textarea für „Product Idea" enthält (ca. Zeilen 80–97).

- [ ] **Step 5: `CardDescription` anpassen**

Alt:
```tsx
<CardDescription>
  Give your project a name and describe your product idea. The agent will guide you through the spec process.
</CardDescription>
```

Neu:
```tsx
<CardDescription>
  Gib deinem Projekt einen Namen. Die Idee beschreibst du anschließend im Wizard.
</CardDescription>
```

- [ ] **Step 6: Lint**

```bash
cd frontend && npm run lint
```

Expected: keine neuen Errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/projects/new/page.tsx
git commit -m "feat(frontend): /projects/new only asks for Project Name

The idea is captured later in the wizard IDEA step; productName there is
prefilled with the project name by the backend."
```

---

## Task 6: Manual Browser Test

Frontend hat keinen Unit-Test-Runner (siehe `frontend/CLAUDE.md`) — UI im Browser verifizieren.

- [ ] **Step 1: Backend + Frontend starten**

```bash
./start.sh
```

(Backend auf `:8080`, Frontend auf `:3001`.)

- [ ] **Step 2: Neues Projekt im Browser anlegen**

Gehe zu `http://localhost:3001/projects/new`. Verifiziere:
- Nur **ein** Eingabefeld sichtbar: Project Name
- Keine Textarea für „Product Idea"
- CardDescription ist auf Deutsch: „Gib deinem Projekt einen Namen. Die Idee beschreibst du anschließend im Wizard."
- Create-Button aktiv, sobald Name eingetippt wird
- Nach Klick: Weiterleitung ins Workspace

- [ ] **Step 3: IDEA-Step im Wizard prüfen**

Im Workspace beim Wizard-Step IDEA:
- `Produktname`-Feld ist mit dem Create-Screen-Namen vorbefüllt
- `Produktidee / Vision` ist leer
- `Kategorie` ist nicht gesetzt

- [ ] **Step 4: Spec-File-Explorer prüfen**

Im File-Explorer (rechtes Panel, „Files"-Tab falls vorhanden):
- `spec/idea.md` ist **nicht** vorhanden (wird erst nach IDEA-Step-Complete angelegt)
- `docs/features/00-feature-set-overview.md` ist vorhanden (Scaffold)

- [ ] **Step 5: Full IDEA-Step durchlaufen**

Fülle `Produktidee` + `Kategorie` aus, klicke **Weiter**. Erwartung:
- Agent antwortet
- Flow-State zeigt IDEA = COMPLETED
- `spec/idea.md` erscheint jetzt im Explorer

- [ ] **Step 6: Kein Commit** (nur manuelle Verifikation)

---

## Task 7: Final Verify

- [ ] **Step 1: Backend full test run**

```bash
cd backend && ./gradlew test
```

Expected: alle 152 Tests grün.

- [ ] **Step 2: Frontend lint**

```bash
cd frontend && npm run lint
```

Expected: keine neuen Errors durch Feature 23.

- [ ] **Step 3: Git log review**

```bash
git log --oneline -8
```

Erwartete Commits (in Reihenfolge):
1. `refactor(backend): drop idea param from CreateProjectRequest and createProject`
2. `feat(backend): createProject prefills wizard IDEA.productName`
3. `feat(backend): createProject no longer writes empty idea.md`
4. `refactor(frontend): createProject API wrapper takes only { name }`
5. `feat(frontend): /projects/new only asks for Project Name`

- [ ] **Step 4: Done-Doc schreiben**

Erstelle `docs/features/23-simplify-project-create-done.md` (Muster: frühere Done-Docs wie `18-step-blocker-gate-done.md`, `21-wizard-features-to-tasks-done.md`). Kurzfassung:
- Was wurde gebaut
- Acceptance Criteria Status (alle checked?)
- Abweichungen vom Spec (falls welche)

```bash
git add docs/features/23-simplify-project-create-done.md
git commit -m "docs: Feature 23 done-doc"
```
