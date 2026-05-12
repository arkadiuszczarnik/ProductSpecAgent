# Feature-Done-Markdown Import via MCP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Coding Agent kann ueber die bestehende MCP-Schnittstelle eine `*-done.md` Datei fuer genau ein Projekt-Feature hochladen; PSA analysiert den Markdown-Inhalt mit einem internen Agenten, speichert Rohtext plus JSON-Snapshot und spiegelt den abgeleiteten Status im Living Sync und im Workspace.

**Architecture:** Die Implementierung erweitert den bestehenden Living-Sync-Stack statt einen neuen Import-Stack einzufuehren. Roh-Markdown, Living-Sync-Import-Event und `FeatureCompletionSnapshot` bleiben getrennt, damit `WizardFeatureGraph` das Planungsmodell bleibt und der Snapshot das Umsetzungsmodell. Die fachliche Analyse erfolgt ausschliesslich ueber einen neuen Koog-Agenten mit festem JSON-Schema.

**Tech Stack:** Kotlin, Spring Boot 4, kotlinx.serialization, bestehender Koog-Agent-Runner, ObjectStore-basierte Persistenz, React/Next.js TypeScript.

---

### Task 1: Domain- und Storage-Fundament fuer Import-Snapshots

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FeatureCompletionSnapshot.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/LivingSyncModels.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/LivingSyncStorage.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/LivingSyncStorageTest.kt`

- [ ] **Step 1: Write the failing storage test for raw markdown + snapshot persistence**

```kotlin
@Test
fun `stores raw done markdown and latest feature completion snapshot`() {
    val objectStore = InMemoryObjectStore()
    val storage = LivingSyncStorage(objectStore)

    val snapshot = FeatureCompletionSnapshot(
        projectId = "project-1",
        featureId = "feature-1",
        derivedStatus = LivingSyncFeatureStatus.DONE,
        summary = "Implemented and verified.",
        implementedItems = listOf("New MCP tool"),
        sourceEventId = "event-1",
        sourceFileName = "45-living-sync-mcp-done.md",
        updatedAt = "2026-05-12T10:00:00Z",
    )

    storage.saveImportedDoneMarkdown("project-1", "feature-1", "event-1", "# Feature 45")
    storage.saveFeatureCompletionSnapshot(snapshot)

    assertEquals("# Feature 45", storage.loadImportedDoneMarkdown("project-1", "feature-1", "event-1"))
    assertEquals(snapshot, storage.loadFeatureCompletionSnapshot("project-1", "feature-1"))
}
```

- [ ] **Step 2: Run the storage test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.storage.LivingSyncStorageTest`

Expected: FAIL with unresolved `FeatureCompletionSnapshot` / missing `saveImportedDoneMarkdown` / missing `loadFeatureCompletionSnapshot`.

- [ ] **Step 3: Add the snapshot model and extend Living Sync storage**

```kotlin
@Serializable
data class FeatureCompletionTestEvidence(
    val name: String,
    val status: String,
)

@Serializable
data class FeatureCompletionSnapshot(
    val projectId: String,
    val featureId: String,
    val derivedStatus: LivingSyncFeatureStatus,
    val summary: String,
    val implementedItems: List<String> = emptyList(),
    val deviations: List<String> = emptyList(),
    val openPoints: List<String> = emptyList(),
    val technicalDebt: List<String> = emptyList(),
    val tests: List<FeatureCompletionTestEvidence> = emptyList(),
    val warnings: List<String> = emptyList(),
    val sourceEventId: String,
    val sourceFileName: String,
    val updatedAt: String,
)
```

```kotlin
private fun importedMarkdownKey(projectId: String, featureId: String, eventId: String) =
    "projects/$projectId/sync/imports/$featureId/$eventId.md"

private fun snapshotKey(projectId: String, featureId: String) =
    "projects/$projectId/sync/feature-snapshots/$featureId.json"

fun saveImportedDoneMarkdown(projectId: String, featureId: String, eventId: String, markdown: String) {
    objectStore.put(importedMarkdownKey(projectId, featureId, eventId), markdown.toByteArray(), "text/markdown")
}

fun saveFeatureCompletionSnapshot(snapshot: FeatureCompletionSnapshot) {
    objectStore.put(snapshotKey(snapshot.projectId, snapshot.featureId), json.encodeToString(snapshot).toByteArray(), "application/json")
}
```

- [ ] **Step 4: Re-run the storage test**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.storage.LivingSyncStorageTest`

Expected: PASS with the new persistence paths under `projects/{projectId}/sync/imports/` and `projects/{projectId}/sync/feature-snapshots/`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/FeatureCompletionSnapshot.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/domain/LivingSyncModels.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/storage/LivingSyncStorage.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/storage/LivingSyncStorageTest.kt
git commit -m "feat(living-sync): add feature completion snapshot storage"
```

### Task 2: PSA-Agent fuer Markdown-zu-JSON-Analyse einfuehren

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureDoneImportAgent.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt`
- Create: `backend/src/main/resources/prompts/feature-done-import-system.md`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/agent/FeatureDoneImportAgentTest.kt`

- [ ] **Step 1: Write the failing agent test for strict JSON parsing**

```kotlin
@Test
fun `analyze parses completion snapshot response`() = runTest {
    val agent = object : FeatureDoneImportAgent(promptService = fakePromptService()) {
        override suspend fun runAgent(prompt: String): String = """
            {
              "featureId": "feature-1",
              "headerCheck": {
                "matchesExpectedFeature": true,
                "reportedFeatureLabel": "Feature 45: Living-Sync via MCP",
                "warnings": []
              },
              "derivedStatus": "DONE",
              "summary": "Implemented and tested.",
              "implementedItems": ["New MCP tool"],
              "deviations": [],
              "tests": [{"name":"LivingSyncServiceTest","status":"PRESENT"}],
              "openPoints": ["Auth hardening remains open."],
              "technicalDebt": [],
              "warnings": []
            }
        """.trimIndent()
    }

    val result = agent.analyze(
        projectId = "project-1",
        featureId = "feature-1",
        fileName = "45-living-sync-mcp-done.md",
        markdown = "# Feature 45: Living-Sync via MCP — Done"
    )

    assertEquals(LivingSyncFeatureStatus.DONE, result.derivedStatus)
    assertEquals("feature-1", result.featureId)
    assertEquals("LivingSyncServiceTest", result.tests.single().name)
}
```

- [ ] **Step 2: Run the agent test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.FeatureDoneImportAgentTest`

Expected: FAIL because `FeatureDoneImportAgent` and the new prompt are missing.

- [ ] **Step 3: Implement the agent and prompt registration**

```kotlin
@Service
open class FeatureDoneImportAgent(
    private val contextBuilder: SpecContextBuilder,
    private val wizardService: WizardService,
    private val promptService: PromptService,
    private val koogRunner: KoogAgentRunner? = null,
) {
    companion object { const val AGENT_ID = "feature-done-import" }

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun analyze(projectId: String, featureId: String, fileName: String, markdown: String): FeatureDoneImportResult {
        val feature = loadFeature(projectId, featureId) ?: throw IllegalArgumentException("Feature $featureId not found")
        val prompt = buildPrompt(projectId, feature, fileName, markdown)
        val raw = runAgent(prompt)
        return json.decodeFromString(raw.cleanJsonFence())
    }
}
```

```kotlin
PromptDefinition(
    id = "feature-done-import-system",
    title = "Feature-Done-Import — System-Prompt",
    description = "Analysiert eine Feature-Done-Markdown-Datei und antwortet nur mit JSON.",
    agent = "FeatureDoneImport",
    resourcePath = "/prompts/feature-done-import-system.md",
    validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
)
```

- [ ] **Step 4: Re-run the agent test**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.agent.FeatureDoneImportAgentTest`

Expected: PASS and parse failures raise the existing `ProposalParseException`-style exception path.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureDoneImportAgent.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/PromptRegistry.kt \
  backend/src/main/resources/prompts/feature-done-import-system.md \
  backend/src/test/kotlin/com/agentwork/productspecagent/agent/FeatureDoneImportAgentTest.kt
git commit -m "feat(agent): add feature done import analyzer"
```

### Task 3: Service- und MCP-Integration fuer Importlaeufe

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/LivingSyncModels.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/LivingSyncService.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/LivingSyncMcpController.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/LivingSyncController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/LivingSyncServiceTest.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/LivingSyncMcpControllerTest.kt`

- [ ] **Step 1: Write the failing service test for import projection**

```kotlin
@Test
fun `import feature done markdown stores event and projects snapshot into summary`() = runTest {
    val (projectId, service) = fixtureWithAgent(
        analysis = FeatureDoneImportResult(
            featureId = "feature-1",
            derivedStatus = LivingSyncFeatureStatus.DONE,
            summary = "Implemented and verified.",
            openPoints = listOf("Auth hardening remains open."),
            deviations = listOf("Used JSON-RPC instead of Spring AI MCP."),
            tests = listOf(FeatureCompletionTestEvidence("LivingSyncServiceTest", "PRESENT")),
        )
    )

    service.importFeatureDoneMarkdown(
        projectId = projectId,
        request = LivingSyncFeatureDoneImportRequest(
            featureId = "feature-1",
            fileName = "45-living-sync-mcp-done.md",
            markdown = "# Feature 45",
            agentName = "codex",
        ),
    )

    val summary = service.getSummary(projectId)
    assertEquals(LivingSyncFeatureStatus.DONE, summary.features.single().status)
    assertEquals("Auth hardening remains open.", summary.featureCompletions.single().openPoints.single())
}
```

- [ ] **Step 2: Write the failing MCP controller test for the new tool**

```kotlin
@Test
fun `tools call import feature done markdown updates project feature summary`() {
    val projectId = createProject()

    mockMvc.perform(
        post("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "jsonrpc": "2.0",
                  "id": "call-import-1",
                  "method": "tools/call",
                  "params": {
                    "name": "import_feature_done_markdown",
                    "arguments": {
                      "projectId": "$projectId",
                      "featureId": "feature-1",
                      "fileName": "45-living-sync-mcp-done.md",
                      "markdown": "# Feature 45: Living-Sync via MCP — Done"
                    }
                  }
                }
                """.trimIndent()
            )
    )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("DONE")))
}
```

- [ ] **Step 3: Run the focused backend tests and confirm failure**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.LivingSyncServiceTest --tests com.agentwork.productspecagent.api.LivingSyncMcpControllerTest`

Expected: FAIL with missing request type, missing service method, missing tool registration, and missing summary projection field.

- [ ] **Step 4: Implement request/response types, service orchestration, and MCP dispatch**

```kotlin
@Serializable
data class LivingSyncFeatureDoneImportRequest(
    val featureId: String,
    val fileName: String,
    val markdown: String,
    val agentName: String? = null,
)

fun importFeatureDoneMarkdown(projectId: String, request: LivingSyncFeatureDoneImportRequest): LivingSyncEvent {
    projectService.getProject(projectId)
    val analysis = featureDoneImportAgent.analyze(projectId, request.featureId, request.fileName, request.markdown)
    val event = save(projectId, LivingSyncEvent(
        id = newId(),
        projectId = projectId,
        type = LivingSyncEventType.SYNC_NOTE,
        featureId = request.featureId,
        agentName = request.agentName,
        status = analysis.derivedStatus.name,
        summary = "Imported ${request.fileName}: ${analysis.summary}",
        createdAt = now(),
    ))
    storage.saveImportedDoneMarkdown(projectId, request.featureId, event.id, request.markdown)
    storage.saveFeatureCompletionSnapshot(analysis.toSnapshot(projectId, event.id, request.fileName, now()))
    return event
}
```

```kotlin
"import_feature_done_markdown" -> livingSyncService.importFeatureDoneMarkdown(
    projectId,
    LivingSyncFeatureDoneImportRequest(
        featureId = arguments.string("featureId") ?: return toolText("Missing featureId."),
        fileName = arguments.string("fileName") ?: return toolText("Missing fileName."),
        markdown = arguments.string("markdown") ?: return toolText("Missing markdown."),
        agentName = arguments.string("agentName"),
    ),
).summary
```

- [ ] **Step 5: Re-run the focused backend tests**

Run: `cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.LivingSyncServiceTest --tests com.agentwork.productspecagent.api.LivingSyncMcpControllerTest --tests com.agentwork.productspecagent.api.LivingSyncControllerTest`

Expected: PASS with the new tool visible in `tools/list`, import calls accepted, and summary projection populated from stored snapshots.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/LivingSyncModels.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/LivingSyncService.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/api/LivingSyncMcpController.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/api/LivingSyncController.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/LivingSyncServiceTest.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/api/LivingSyncMcpControllerTest.kt
git commit -m "feat(living-sync): import feature done markdown over mcp"
```

### Task 4: Frontend-Typen und Living-Sync-Workspace auf Snapshot-Projektion umstellen

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/components/living-sync/LivingSyncPanel.tsx`
- Modify: `frontend/src/components/cockpit/ProjectCockpitPrototype.tsx`

- [ ] **Step 1: Add frontend types for feature completion snapshots**

```ts
export interface FeatureCompletionTestEvidence {
  name: string;
  status: string;
}

export interface FeatureCompletionSnapshot {
  projectId: string;
  featureId: string;
  derivedStatus: LivingSyncFeatureStatus;
  summary: string;
  implementedItems: string[];
  deviations: string[];
  openPoints: string[];
  technicalDebt: string[];
  tests: FeatureCompletionTestEvidence[];
  warnings: string[];
  sourceEventId: string;
  sourceFileName: string;
  updatedAt: string;
}

export interface LivingSyncSummary {
  projectId: string;
  features: LivingSyncFeatureSummary[];
  featureCompletions: FeatureCompletionSnapshot[];
  // existing fields stay unchanged
}
```

- [ ] **Step 2: Run lint once to verify the type additions fail on missing UI usage**

Run: `cd frontend && npm run lint`

Expected: FAIL or report unused / incomplete rendering paths until the panel consumes `featureCompletions`.

- [ ] **Step 3: Update the Living Sync panel to show imported completion details**

```tsx
function FeatureStatuses({ summary }: { summary: LivingSyncSummary }) {
  const completionsByFeature = new Map(summary.featureCompletions.map((item) => [item.featureId, item]));

  return (
    <Section title="Feature Status">
      <div className="space-y-2">
        {summary.features.map((feature) => {
          const completion = completionsByFeature.get(feature.featureId);
          return (
            <div key={feature.featureId} className="rounded-md border bg-card p-2">
              <div className="flex items-center justify-between gap-2">
                <span className="truncate text-sm font-medium">{feature.featureId}</span>
                <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-medium", STATUS_STYLE[feature.status])}>
                  {completion?.derivedStatus ?? feature.status}
                </span>
              </div>
              <p className="mt-1 text-xs text-muted-foreground">{completion?.summary ?? feature.summary}</p>
              {completion?.openPoints?.length ? (
                <ul className="mt-2 list-disc space-y-1 pl-4 text-xs text-muted-foreground">
                  {completion.openPoints.slice(0, 3).map((point) => <li key={point}>{point}</li>)}
                </ul>
              ) : null}
            </div>
          );
        })}
      </div>
    </Section>
  );
}
```

- [ ] **Step 4: Re-run frontend lint**

Run: `cd frontend && npm run lint`

Expected: PASS with the panel compiling against the expanded `LivingSyncSummary` payload.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/api.ts \
  frontend/src/components/living-sync/LivingSyncPanel.tsx \
  frontend/src/components/cockpit/ProjectCockpitPrototype.tsx
git commit -m "feat(frontend): surface imported feature completion snapshots"
```

### Task 5: End-to-end verification and docs alignment

**Files:**
- Modify: `docs/features/51-feature-done-markdown-import-via-mcp.md` (only if implementation diverges)
- Modify: `docs/features/51-feature-done-markdown-import-via-mcp-done.md`

- [ ] **Step 1: Run the focused backend and frontend verification suite**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.storage.LivingSyncStorageTest --tests com.agentwork.productspecagent.agent.FeatureDoneImportAgentTest --tests com.agentwork.productspecagent.service.LivingSyncServiceTest --tests com.agentwork.productspecagent.api.LivingSyncMcpControllerTest --tests com.agentwork.productspecagent.api.LivingSyncControllerTest
cd ../frontend && npm run lint
```

Expected: PASS for all targeted backend tests and frontend lint.

- [ ] **Step 2: Perform an MCP smoke test through the controller test**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.LivingSyncMcpControllerTest
```

Expected: PASS with a real JSON-RPC request against `POST /mcp` that imports markdown and then verifies the projected `featureCompletions` payload through `GET /api/v1/projects/{projectId}/living-sync`.

- [ ] **Step 3: Write the done doc**

```md
# Feature 51: Feature-Done-Markdown Import via MCP — Done

Implementiert am 2026-05-12.

## Zusammenfassung

...
```

- [ ] **Step 4: Commit**

```bash
git add docs/features/51-feature-done-markdown-import-via-mcp-done.md \
  docs/features/51-feature-done-markdown-import-via-mcp.md
git commit -m "docs(feature): mark feature done markdown import complete"
```
