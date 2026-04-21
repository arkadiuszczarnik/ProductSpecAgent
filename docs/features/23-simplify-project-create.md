# Feature 23: Project Create vereinfachen — nur Project Name

## Zusammenfassung
Die Seite `/projects/new` soll beim Anlegen eines neuen Projekts nur noch den Project Name abfragen. Das bisherige Feld "Product Idea" entfällt, weil der Wizard-Step `IDEA` ohnehin die Felder `productName`, `vision` und `category` erfasst — der User würde die Idee sonst doppelt eingeben. Zusätzlich wird `wizard.IDEA.productName` beim Project-Create automatisch mit dem eingegebenen `project.name` vorbefüllt, sodass der User den Namen nicht noch einmal tippen muss.

## User Stories
1. Als PO möchte ich ein Projekt mit nur einem Klick und einem einzigen Eingabefeld anlegen, damit ich schnell starten kann, ohne bereits über die Idee nachgedacht zu haben.
2. Als PO möchte ich den Projektnamen nicht zweimal eingeben, weil die Wizard-IDEA-Stufe den Namen automatisch vom Create-Screen übernimmt.
3. Als PO möchte ich die detaillierte Produktidee bewusst erst im Wizard-Schritt IDEA formulieren, damit Kontext, Vision und Kategorie zusammen an einer Stelle erfasst werden.

## Acceptance Criteria
- [ ] `/projects/new` zeigt nur noch **ein** Eingabefeld: `Project Name` (plus Cancel / Create-Buttons)
- [ ] Create-Button ist aktiv, sobald `name.trim().length > 0`
- [ ] `createProject`-API im Frontend (`lib/api.ts`) akzeptiert nur noch `{ name: string }` — kein `idea`-Parameter mehr
- [ ] Backend-DTO `CreateProjectRequest` hat kein `idea`-Feld mehr
- [ ] `ProjectService.createProject(name)` legt weiterhin `project.json`, `flow-state.json` und `docs/`-Scaffold an
- [ ] `ProjectService.createProject(name)` legt **kein** `spec/idea.md` mehr an (wird erst beim IDEA-Step-Complete vom Agent erzeugt — konsistent mit `problem.md`, `target-audience.md`, etc.)
- [ ] `ProjectService.createProject(name)` initialisiert `wizard.json` mit `steps.IDEA.fields.productName = name` (sonst leere `WizardData`)
- [ ] Workspace-Page lädt `wizard.json`, der User sieht im IDEA-Step den `Produktname`-Wert vorausgefüllt; `vision` und `category` sind weiterhin leer
- [ ] User kann `productName` im IDEA-Step überschreiben — `project.name` bleibt davon unberührt (keine automatische Sync)
- [ ] Alle bestehenden Backend-Tests, die `createProject("x", "y")` aufrufen oder `POST /projects` mit `"idea":"..."` senden, werden auf die neue Signatur aktualisiert
- [ ] Neuer Backend-Test: `createProject` initialisiert `wizard.json` mit `IDEA.productName = name`

## Technische Details

### Backend

**Datei: `domain/ProjectModels.kt`** (oder wo `CreateProjectRequest` liegt)
```kotlin
data class CreateProjectRequest(val name: String)
```
(Das `idea: String`-Feld fällt weg. Breaking change für die REST-API — einziger Client ist unser Frontend.)

**Datei: `service/ProjectService.kt`**
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

    // Prefill wizard IDEA.productName with project name so the user doesn't
    // have to retype it. Other IDEA fields (vision, category) stay empty.
    val initialWizard = WizardData(
        projectId = project.id,
        steps = mapOf(
            "IDEA" to WizardStepData(
                fields = mapOf("productName" to JsonPrimitive(name))
            )
        )
    )
    storage.saveWizardData(project.id, initialWizard)

    generateDocsScaffold(project.id, name)
    return ProjectResponse(project = project, flowState = flowState)
}
```
- `saveSpecStep(projectId, "idea.md", ...)` entfernt — `idea.md` wird beim IDEA-Step-Complete vom Agent erzeugt.
- `WizardData` und `WizardStepData` werden mit dem vorbefüllten `productName` angelegt. Der konkrete Value-Typ (`JsonPrimitive`) folgt dem bestehenden Muster in `WizardModels.kt`.

**Datei: `api/ProjectController.kt`**
```kotlin
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
fun createProject(@RequestBody request: CreateProjectRequest): ProjectResponse {
    return projectService.createProject(request.name)
}
```

### Frontend

**Datei: `src/lib/api.ts`**
```typescript
export async function createProject(payload: { name: string }): Promise<ProjectResponse> {
  return apiFetch<ProjectResponse>("/api/v1/projects", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
```
Die Typ-Definition / Signatur wird angepasst. Falls ein separates `CreateProjectRequest`-Type existiert, wird es ebenfalls bereinigt.

**Datei: `src/app/projects/new/page.tsx`**
- `idea`-State, `setIdea`-Calls, `<textarea id="project-idea">`-Block und `idea.trim().length > 0`-Check entfernen
- `canSubmit = name.trim().length > 0 && !loading`
- `createProject({ name: name.trim() })` (ohne `idea`)
- `CardDescription` anpassen: „Gib deinem Projekt einen Namen. Die Idee beschreibst du anschließend im Wizard."

### Tests

**Backend — mechanische Anpassung** aller Stellen mit zwei-Parameter-Aufrufen:
- `ProjectServiceTest`, `ConsistencyCheckServiceTest`
- `WizardControllerTest`, `WizardChatControllerTest`, `ProjectControllerTest`, `TaskControllerTest`, `FileControllerTest`, `HandoffControllerTest`, `DecisionControllerTest`, `ClarificationControllerTest`, `CheckControllerTest`, `ExportControllerTest`, `FeatureProposalControllerTest`
- `IdeaToSpecAgentTest`, `PlanGeneratorAgentTest`, `DecisionAgentTest`, `SpecContextBuilderTest`, `ScaffoldContextBuilderTest`

Ersetzungsmuster:
- `createProject("Name", "Idea")` → `createProject("Name")`
- `"""{"name":"...","idea":"..."}"""` → `"""{"name":"..."}"""`

**Backend — neuer Test in `ProjectServiceTest`:**
```kotlin
@Test
fun `createProject initializes wizard IDEA productName with project name`() {
    val resp = projectService.createProject("TaskFlow Pro")
    val wizard = wizardService.getWizardData(resp.project.id)
    assertThat(wizard.steps["IDEA"]?.fields?.get("productName"))
        .isEqualTo(JsonPrimitive("TaskFlow Pro"))
}
```

**Backend — neuer Test in `ProjectServiceTest`:**
```kotlin
@Test
fun `createProject does not create idea md`() {
    val resp = projectService.createProject("TaskFlow Pro")
    assertThat(projectService.readSpecFile(resp.project.id, "idea.md")).isNull()
}
```

**Frontend** — manuelles Browser-Testing laut `frontend/CLAUDE.md` (kein Unit-Test-Runner konfiguriert).

## Datenfluss nach der Änderung
1. User öffnet `/projects/new`, gibt „TaskFlow Pro" ein, klickt **Create Project**
2. Frontend `POST /api/v1/projects` mit Body `{ "name": "TaskFlow Pro" }`
3. Backend legt an:
   - `data/projects/{id}/project.json` — `name: "TaskFlow Pro"`
   - `data/projects/{id}/flow-state.json` — initial, IDEA = OPEN
   - `data/projects/{id}/wizard.json` — `steps.IDEA.fields.productName = "TaskFlow Pro"`
   - `data/projects/{id}/docs/…` (Scaffold)
   - **kein** `spec/idea.md`
4. Frontend navigiert zu `/projects/{id}` → Workspace → IDEA-Step aktiv
5. IdeaForm zeigt: `Produktname = "TaskFlow Pro"` (vorausgefüllt), `Produktidee = ""`, `Kategorie = nicht gesetzt`
6. User ergänzt Vision + Kategorie, klickt **Weiter** → Agent generiert `idea.md`, `[STEP_COMPLETE]` → nächster Step

## Abhängigkeiten
- Feature 01 (Idea to Spec Flow) — Wizard-Step IDEA existiert bereits
- Feature 11 (Guided Wizard Forms) — `IdeaForm` existiert und liest `steps.IDEA.fields`
- Feature 12 (Dynamic Wizard Steps) — `category` steuert die sichtbaren späteren Steps

## Nicht im Scope
- Automatische Sync zwischen `project.name` und `wizard.IDEA.productName` nach dem Create
- UX-Hinweis, wenn der User den Wizard-Namen abweichend vom Project-Namen setzt
- Migration bestehender Projekte (vorhandene `idea.md`-Dateien bleiben unberührt; bestehende Projekte ohne vorbefüllten `productName` zeigen einfach ein leeres Feld wie bisher)

## Aufwand
S
