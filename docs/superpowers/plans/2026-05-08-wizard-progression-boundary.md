# Wizard Progression Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the backend the canonical owner of wizard progression, final-step detection, and completion actions so category-specific wizards finish at the correct visible step.

**Architecture:** Add a pure `WizardProgressionPolicy` for category-specific visible steps, then introduce a `WizardProgression` boundary that wraps existing completion behavior and returns an explicit progression/action model. Migrate backend responses and frontend wizard flow to consume the server-owned progression instead of duplicating completion heuristics.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, MockMvc, Kotlin coroutines, TypeScript, React 19, Zustand.

---

## File Structure

- Create `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ProductCategory.kt`: backend product-category enum with current frontend wire values.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgression.kt`: public boundary interface, progression DTOs, client-action DTOs, and policy types.
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`: pure category-to-visible-step mapping and next/final calculations.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`: move completion implementation behind `WizardProgression`, use `WizardProgressionPolicy` for terminal-step/next-step decisions, return progression/action artifacts.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardChatModels.kt`: extend API response with `progression`, `action`, and `artifacts` while keeping old fields for compatibility.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardChatController.kt`: adapt result from `WizardProgression`.
- Modify `backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardController.kt`: add `GET /api/v1/projects/{projectId}/wizard/progression`.
- Modify `frontend/src/lib/api.ts`: add progression/action types and return fields to `completeWizardStep`.
- Modify `frontend/src/lib/stores/wizard-store.ts`: store backend progression and follow `action` instead of computing finality locally.
- Modify `frontend/src/components/wizard/WizardForm.tsx`: render primary CTA from backend progression.
- Test `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicyTest.kt`: pure category plan tests.
- Test `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`: category-specific terminal-step boundary tests.
- Test `backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardChatControllerTest.kt`: response contract includes new progression/action while preserving old fields.

### Task 1: Add Category Progression Policy

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ProductCategory.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgression.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicyTest.kt`

- [ ] **Step 1: Write the failing policy tests**

Create `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicyTest.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.WizardData
import com.agentwork.productspecagent.domain.WizardStepData
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WizardProgressionPolicyTest {

    private val policy = WizardProgressionPolicy()

    @Test
    fun `library ends at MVP`() {
        val plan = policy.planFor(wizardData("Library"))

        assertThat(plan.visibleSteps).containsExactly(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
        )
        assertThat(plan.isTerminal(FlowStepType.MVP)).isTrue()
        assertThat(plan.nextAfter(FlowStepType.MVP)).isNull()
    }

    @Test
    fun `cli tool ends at architecture`() {
        val plan = policy.planFor(wizardData("CLI Tool"))

        assertThat(plan.visibleSteps).containsExactly(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.ARCHITECTURE,
        )
        assertThat(plan.isTerminal(FlowStepType.ARCHITECTURE)).isTrue()
        assertThat(plan.nextAfter(FlowStepType.MVP)).isEqualTo(FlowStepType.ARCHITECTURE)
    }

    @Test
    fun `api ends at backend`() {
        val plan = policy.planFor(wizardData("API"))

        assertThat(plan.visibleSteps).containsExactly(
            FlowStepType.IDEA,
            FlowStepType.PROBLEM,
            FlowStepType.FEATURES,
            FlowStepType.MVP,
            FlowStepType.ARCHITECTURE,
            FlowStepType.BACKEND,
        )
        assertThat(plan.isTerminal(FlowStepType.BACKEND)).isTrue()
        assertThat(plan.nextAfter(FlowStepType.ARCHITECTURE)).isEqualTo(FlowStepType.BACKEND)
    }

    @Test
    fun `saas uses all visible steps and ends at frontend`() {
        val plan = policy.planFor(wizardData("SaaS"))

        assertThat(plan.visibleSteps).containsExactlyElementsOf(FlowStepType.entries)
        assertThat(plan.isTerminal(FlowStepType.FRONTEND)).isTrue()
    }

    private fun wizardData(category: String): WizardData =
        WizardData(
            projectId = "p1",
            steps = mapOf(
                "IDEA" to WizardStepData(
                    fields = mapOf("category" to JsonPrimitive(category)),
                )
            ),
        )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardProgressionPolicyTest
```

Expected: compilation fails because `WizardProgressionPolicy` and `ProductCategory` do not exist.

- [ ] **Step 3: Add backend `ProductCategory`**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/domain/ProductCategory.kt`:

```kotlin
package com.agentwork.productspecagent.domain

enum class ProductCategory(val wireValue: String) {
    SAAS("SaaS"),
    MOBILE_APP("Mobile App"),
    CLI_TOOL("CLI Tool"),
    LIBRARY("Library"),
    DESKTOP_APP("Desktop App"),
    API("API");

    companion object {
        fun fromWire(value: String?): ProductCategory? =
            entries.firstOrNull { it.wireValue == value }
    }
}
```

- [ ] **Step 4: Add progression boundary types**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgression.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepStatus
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.ProductCategory
import kotlinx.serialization.Serializable

interface WizardProgression {
    fun snapshot(projectId: String): WizardProgressionView
    suspend fun complete(command: CompleteWizardStep): WizardCompletionResult
}

data class WizardProgressionPlan(
    val category: ProductCategory?,
    val visibleSteps: List<FlowStepType>,
) {
    fun isTerminal(step: FlowStepType): Boolean = visibleSteps.lastOrNull() == step

    fun nextAfter(step: FlowStepType): FlowStepType? =
        visibleSteps.dropWhile { it != step }.drop(1).firstOrNull()
}

@Serializable
data class WizardProgressionView(
    val category: String?,
    val steps: List<WizardStepView>,
    val currentStep: String?,
    val status: String,
    val primaryAction: WizardPrimaryActionDto,
)

@Serializable
data class WizardStepView(
    val step: String,
    val status: String,
    val visible: Boolean = true,
    val finalVisibleStep: Boolean = false,
)

@Serializable
data class WizardPrimaryActionDto(
    val type: String,
    val step: String? = null,
)

@Serializable
data class WizardClientActionDto(
    val type: String,
    val step: String? = null,
)

data class WizardCompletionResult(
    val message: String,
    val progression: WizardProgressionView,
    val action: WizardClientActionDto,
    val artifacts: WizardCreatedArtifacts = WizardCreatedArtifacts(),
)

@Serializable
data class WizardCreatedArtifacts(
    val decisionIds: List<String> = emptyList(),
    val clarificationIds: List<String> = emptyList(),
)

fun completeStepAction(step: FlowStepType): WizardPrimaryActionDto =
    WizardPrimaryActionDto(type = "COMPLETE_STEP", step = step.name)

fun showStepAction(step: FlowStepType): WizardClientActionDto =
    WizardClientActionDto(type = "SHOW_STEP", step = step.name)

val openExportPrimaryAction = WizardPrimaryActionDto(type = "OPEN_EXPORT")
val openExportClientAction = WizardClientActionDto(type = "OPEN_EXPORT")
val stayClientAction = WizardClientActionDto(type = "STAY")
val nonePrimaryAction = WizardPrimaryActionDto(type = "NONE")
```

- [ ] **Step 5: Add pure policy implementation**

Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`:

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.ProductCategory
import com.agentwork.productspecagent.domain.WizardData
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
class WizardProgressionPolicy {

    fun planFor(wizardData: WizardData): WizardProgressionPlan {
        val category = ProductCategory.fromWire(
            wizardData.steps["IDEA"]?.fields?.get("category")
                ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() },
        )
        return WizardProgressionPlan(
            category = category,
            visibleSteps = visibleSteps(category),
        )
    }

    private fun visibleSteps(category: ProductCategory?): List<FlowStepType> =
        when (category) {
            ProductCategory.LIBRARY -> listOf(
                FlowStepType.IDEA,
                FlowStepType.PROBLEM,
                FlowStepType.FEATURES,
                FlowStepType.MVP,
            )
            ProductCategory.CLI_TOOL -> listOf(
                FlowStepType.IDEA,
                FlowStepType.PROBLEM,
                FlowStepType.FEATURES,
                FlowStepType.MVP,
                FlowStepType.ARCHITECTURE,
            )
            ProductCategory.API -> listOf(
                FlowStepType.IDEA,
                FlowStepType.PROBLEM,
                FlowStepType.FEATURES,
                FlowStepType.MVP,
                FlowStepType.ARCHITECTURE,
                FlowStepType.BACKEND,
            )
            ProductCategory.SAAS,
            ProductCategory.MOBILE_APP,
            ProductCategory.DESKTOP_APP,
            null -> FlowStepType.entries.toList()
        }
}
```

- [ ] **Step 6: Run policy tests to verify they pass**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardProgressionPolicyTest
```

Expected: all four policy tests pass.

### Task 2: Make Backend Completion Use Category Progression

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardStepCompletionServiceTest.kt`

- [ ] **Step 1: Write failing category-terminal completion tests**

Add these tests to `WizardStepCompletionServiceTest`:

```kotlin
@Test
fun `library completion opens export at MVP`() = runBlocking {
    val project = projectService.createProject("Library Project")
    wizardService.saveStepData(
        project.project.id,
        "IDEA",
        com.agentwork.productspecagent.domain.WizardStepData(
            fields = mapOf("category" to kotlinx.serialization.json.JsonPrimitive("Library")),
            completedAt = java.time.Instant.now().toString(),
        ),
    )
    val completion = createCompletion(SequenceWizardAgent(listOf("Done.", "# Library Spec\n\nReady.")))

    val result = completion.complete(
        CompleteWizardStep(
            projectId = project.project.id,
            step = FlowStepType.MVP,
            fields = mapOf("mvp" to "Small public API"),
        )
    )

    assertThat(result.nextStep).isNull()
    assertThat(result.exportTriggered).isTrue()
    assertThat(projectService.getFlowState(project.project.id).currentStep).isEqualTo(FlowStepType.MVP)
    assertThat(projectService.readSpecFile(project.project.id, "spec.md")).contains("Library Spec")
    Unit
}

@Test
fun `api completion opens export at BACKEND`() = runBlocking {
    val project = projectService.createProject("API Project")
    wizardService.saveStepData(
        project.project.id,
        "IDEA",
        com.agentwork.productspecagent.domain.WizardStepData(
            fields = mapOf("category" to kotlinx.serialization.json.JsonPrimitive("API")),
            completedAt = java.time.Instant.now().toString(),
        ),
    )
    val completion = createCompletion(SequenceWizardAgent(listOf("Done.", "# API Spec\n\nReady.")))

    val result = completion.complete(
        CompleteWizardStep(
            projectId = project.project.id,
            step = FlowStepType.BACKEND,
            fields = mapOf("framework" to "Kotlin+Spring"),
        )
    )

    assertThat(result.nextStep).isNull()
    assertThat(result.exportTriggered).isTrue()
    assertThat(projectService.getFlowState(project.project.id).currentStep).isEqualTo(FlowStepType.BACKEND)
    Unit
}
```

- [ ] **Step 2: Run tests to verify they fail against enum-order finality**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest
```

Expected: new tests fail because `MVP` and `BACKEND` are not treated as terminal steps.

- [ ] **Step 3: Inject `WizardProgressionPolicy` and use visible plan**

In `WizardStepCompletionService`, add constructor dependency:

```kotlin
private val progressionPolicy: WizardProgressionPolicy = WizardProgressionPolicy(),
```

Replace current `isLastStep` and `nextStep` calculation with:

```kotlin
val wizardData = wizardService.getWizardData(command.projectId)
val plan = progressionPolicy.planFor(wizardData)
require(command.step in plan.visibleSteps) {
    "Wizard step ${command.step.name} is not visible for category ${plan.category?.wireValue ?: "default"}"
}
val isLastStep = plan.isTerminal(command.step)
val nextStep = if (!isLastStep) plan.nextAfter(command.step) else null
```

Remove the old local `stepOrder` usage for next/final calculation.

- [ ] **Step 4: Keep flow-state updates scoped to visible next step**

In the existing flow-state update block, keep all steps but only mark `nextStep` in progress:

```kotlin
val updatedSteps = flowState.steps.map { step ->
    when (step.stepType) {
        command.step -> step.copy(status = FlowStepStatus.COMPLETED, updatedAt = now)
        nextStep -> step.copy(status = FlowStepStatus.IN_PROGRESS, updatedAt = now)
        else -> step
    }
}
```

This preserves storage compatibility while ensuring `currentStep` stays on terminal visible step when the category is complete.

- [ ] **Step 5: Update `createCompletion` test helper**

If the constructor requires the new dependency explicitly, update `createCompletion`:

```kotlin
private fun createCompletion(agent: WizardCompletionAgent): WizardStepCompletion =
    WizardStepCompletionService(
        contextBuilder = contextBuilder,
        projectService = projectService,
        promptService = promptService,
        decisionService = decisionService,
        clarificationService = clarificationService,
        wizardService = wizardService,
        completionAgent = agent,
        taskService = taskService,
        progressionPolicy = WizardProgressionPolicy(),
    )
```

- [ ] **Step 6: Run completion service tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest
```

Expected: existing tests and new category-terminal tests pass.

### Task 3: Return Progression and Explicit Actions from Completion

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardChatModels.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardChatController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardChatControllerTest.kt`

- [ ] **Step 1: Write failing controller contract test**

Add a MockMvc assertion to the existing successful wizard completion test in `WizardChatControllerTest`:

```kotlin
.andExpect(jsonPath("$.progression.steps").isArray)
.andExpect(jsonPath("$.progression.currentStep").value("PROBLEM"))
.andExpect(jsonPath("$.progression.primaryAction.type").value("COMPLETE_STEP"))
.andExpect(jsonPath("$.progression.primaryAction.step").value("PROBLEM"))
.andExpect(jsonPath("$.action.type").value("SHOW_STEP"))
.andExpect(jsonPath("$.action.step").value("PROBLEM"))
.andExpect(jsonPath("$.artifacts.decisionIds").isArray)
.andExpect(jsonPath("$.artifacts.clarificationIds").isArray)
```

- [ ] **Step 2: Run controller test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.WizardChatControllerTest
```

Expected: JSON path assertions fail because the response does not include `progression`, `action`, or `artifacts`.

- [ ] **Step 3: Extend API response model**

In `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardChatModels.kt`, extend `WizardStepCompleteResponse`:

```kotlin
@Serializable
data class WizardStepCompleteResponse(
    val message: String,
    val nextStep: String?,
    val exportTriggered: Boolean,
    val decisionId: String? = null,
    val clarificationId: String? = null,
    val progression: com.agentwork.productspecagent.service.WizardProgressionView? = null,
    val action: com.agentwork.productspecagent.service.WizardClientActionDto? = null,
    val artifacts: com.agentwork.productspecagent.service.WizardCreatedArtifacts? = null,
)
```

Keep old fields to avoid breaking the frontend during migration.

- [ ] **Step 4: Add progression fields to service result**

Extend `WizardStepCompletionResult` in `WizardStepCompletion.kt`:

```kotlin
data class WizardStepCompletionResult(
    val message: String,
    val nextStep: FlowStepType?,
    val exportTriggered: Boolean,
    val decisionId: String? = null,
    val clarificationId: String? = null,
    val progression: WizardProgressionView? = null,
    val action: WizardClientActionDto? = null,
    val artifacts: WizardCreatedArtifacts = WizardCreatedArtifacts(),
)
```

Before returning, build progression/action:

```kotlin
val progression = snapshotFor(command.projectId)
val action = when {
    isLastStep -> openExportClientAction
    nextStep != null -> showStepAction(nextStep)
    else -> stayClientAction
}
```

Add a private `snapshotFor(projectId: String): WizardProgressionView` helper in the service that:

```kotlin
val wizardData = wizardService.getWizardData(projectId)
val plan = progressionPolicy.planFor(wizardData)
val flowState = projectService.getFlowState(projectId)
val current = flowState.currentStep.takeIf { it in plan.visibleSteps }
val completedTerminal = plan.visibleSteps.lastOrNull()?.let { terminal ->
    flowState.steps.firstOrNull { it.stepType == terminal }?.status == FlowStepStatus.COMPLETED
} == true
return WizardProgressionView(
    category = plan.category?.wireValue,
    steps = plan.visibleSteps.map { step ->
        WizardStepView(
            step = step.name,
            status = flowState.steps.first { it.stepType == step }.status.name,
            visible = true,
            finalVisibleStep = plan.isTerminal(step),
        )
    },
    currentStep = current?.name,
    status = if (completedTerminal) "READY_FOR_EXPORT" else "IN_PROGRESS",
    primaryAction = if (completedTerminal) openExportPrimaryAction else current?.let(::completeStepAction) ?: nonePrimaryAction,
)
```

- [ ] **Step 5: Map new result fields in controller**

In `WizardChatController`, add these fields to `WizardStepCompleteResponse`:

```kotlin
progression = result.progression,
action = result.action,
artifacts = result.artifacts,
```

- [ ] **Step 6: Run controller and service tests**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.WizardChatControllerTest --tests com.agentwork.productspecagent.service.WizardStepCompletionServiceTest
```

Expected: both test classes pass.

### Task 4: Add Snapshot Endpoint

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardController.kt`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardControllerTest.kt`

- [ ] **Step 1: Write failing snapshot endpoint test**

Add to `WizardControllerTest`:

```kotlin
@Test
fun `GET wizard progression returns backend visible steps for library`() {
    val pid = createProject()
    mockMvc.perform(
        put("/api/v1/projects/$pid/wizard/IDEA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"fields":{"category":"Library"},"completedAt":null}""")
    ).andExpect(status().isOk())

    mockMvc.perform(get("/api/v1/projects/$pid/wizard/progression"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("Library"))
        .andExpect(jsonPath("$.steps.length()").value(4))
        .andExpect(jsonPath("$.steps[3].step").value("MVP"))
        .andExpect(jsonPath("$.steps[3].finalVisibleStep").value(true))
        .andExpect(jsonPath("$.primaryAction.type").value("COMPLETE_STEP"))
}
```

- [ ] **Step 2: Run endpoint test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.WizardControllerTest
```

Expected: request fails with 404 because `/wizard/progression` does not exist.

- [ ] **Step 3: Make `WizardStepCompletionService` implement `WizardProgression`**

Change the class declaration:

```kotlin
class WizardStepCompletionService(...) : WizardStepCompletion, WizardProgression {
```

Add:

```kotlin
override fun snapshot(projectId: String): WizardProgressionView =
    snapshotFor(projectId)
```

Keep `complete(command: CompleteWizardStep)` as the existing completion implementation.

- [ ] **Step 4: Add endpoint to `WizardController`**

Inject `WizardProgression`:

```kotlin
class WizardController(
    private val wizardService: WizardService,
    private val wizardProgression: WizardProgression,
)
```

Add:

```kotlin
@GetMapping("/progression")
fun progression(@PathVariable projectId: String): WizardProgressionView =
    wizardProgression.snapshot(projectId)
```

- [ ] **Step 5: Run endpoint test**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.WizardControllerTest
```

Expected: endpoint test passes.

### Task 5: Migrate Frontend API and Store to Server Progression

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/stores/wizard-store.ts`

- [ ] **Step 1: Add TypeScript progression types**

In `frontend/src/lib/api.ts`, add near the wizard response types:

```ts
export interface WizardStepView {
  step: StepType;
  status: StepStatus;
  visible: boolean;
  finalVisibleStep: boolean;
}

export interface WizardPrimaryAction {
  type: "COMPLETE_STEP" | "OPEN_EXPORT" | "NONE";
  step?: StepType | null;
}

export interface WizardClientAction {
  type: "SHOW_STEP" | "OPEN_EXPORT" | "STAY";
  step?: StepType | null;
}

export interface WizardCreatedArtifacts {
  decisionIds: string[];
  clarificationIds: string[];
}

export interface WizardProgressionView {
  category: string | null;
  steps: WizardStepView[];
  currentStep: StepType | null;
  status: "IN_PROGRESS" | "READY_FOR_EXPORT";
  primaryAction: WizardPrimaryAction;
}
```

Extend `WizardStepCompleteResponse`:

```ts
export interface WizardStepCompleteResponse {
  message: string;
  nextStep?: StepType | null;
  exportTriggered?: boolean;
  decisionId?: string | null;
  clarificationId?: string | null;
  progression?: WizardProgressionView | null;
  action?: WizardClientAction | null;
  artifacts?: WizardCreatedArtifacts | null;
}
```

Add API helper:

```ts
export async function getWizardProgression(projectId: string): Promise<WizardProgressionView> {
  return apiFetch<WizardProgressionView>(`/api/v1/projects/${projectId}/wizard/progression`);
}
```

- [ ] **Step 2: Add progression to Zustand state**

In `frontend/src/lib/stores/wizard-store.ts`, import the new types/helper:

```ts
import type { WizardProgressionView } from "@/lib/api";
import { getWizardData, saveWizardStep, completeWizardStep, getWizardProgression } from "@/lib/api";
```

Extend `WizardState`:

```ts
progression: WizardProgressionView | null;
```

Initialize and reset:

```ts
progression: null,
```

```ts
reset: () => set({ data: null, progression: null, activeStep: "IDEA", loading: false, saving: false, chatPending: false }),
```

- [ ] **Step 3: Load progression with wizard data**

In `loadWizard`, after loading wizard data, fetch progression:

```ts
const data = await getWizardData(projectId);
const progression = await getWizardProgression(projectId);
set({
  data,
  progression,
  activeStep: progression.currentStep ?? progression.steps[0]?.step ?? "IDEA",
  loading: false,
});
```

In the catch path:

```ts
set({ data: { projectId, steps: {} }, progression: null, loading: false });
```

- [ ] **Step 4: Make `visibleSteps` use server progression when available**

Replace `visibleSteps` body:

```ts
visibleSteps: () => {
  const progression = get().progression;
  if (progression) {
    return WIZARD_STEPS.filter((s) => progression.steps.some((p) => p.step === s.key));
  }
  const category = get().getCategory();
  const visible = getVisibleSteps(category);
  return WIZARD_STEPS.filter((s) => visible.includes(s.key));
},
```

- [ ] **Step 5: Follow server action after completion**

In the generic `completeStep` response handling, after adding agent message, add:

```ts
if (response.progression) {
  set({ progression: response.progression });
}

if (response.action?.type === "SHOW_STEP" && response.action.step) {
  set({ activeStep: response.action.step, chatPending: false });
} else {
  set({ chatPending: false });
}
```

Replace the existing `if (response.nextStep) { ... }` navigation block with this action handling.

Keep this compatibility fallback after the action block:

```ts
if (!response.action && response.nextStep) {
  const steps = visibleSteps();
  const nextVisible = steps.find((s) => s.key === response.nextStep);
  if (nextVisible) set({ activeStep: response.nextStep });
}
```

Return:

```ts
return { exportTriggered: response.action?.type === "OPEN_EXPORT" || !!response.exportTriggered };
```

- [ ] **Step 6: Run frontend lint**

Run:

```bash
cd frontend && npm run lint
```

Expected: lint passes.

### Task 6: Render Wizard CTA from Server Progression

**Files:**
- Modify: `frontend/src/components/wizard/WizardForm.tsx`

- [ ] **Step 1: Read progression in `WizardForm`**

Change store selection:

```ts
const { activeStep, saving, chatPending, completeStep, goPrev, visibleSteps, progression } = useWizardStore();
```

- [ ] **Step 2: Replace local wizard-done heuristic**

Remove:

```ts
const lastStepKey = steps[steps.length - 1]?.key;
const lastStepCompleted =
  !!lastStepKey &&
  flowState?.steps.find((s) => s.stepType === lastStepKey)?.status === "COMPLETED";
const wizardDone = isLast && lastStepCompleted;
```

Replace with:

```ts
const primaryAction = progression?.primaryAction;
const wizardDone = primaryAction?.type === "OPEN_EXPORT";
```

If `flowState` is now unused, remove:

```ts
const flowState = useProjectStore((s) => s.flowState);
```

- [ ] **Step 3: Keep button labels compatible**

Replace the button label branch with:

```tsx
{wizardDone ? (
  <><Download size={14} /> Exportieren</>
) : isLast ? (
  <><Save size={14} /> Abschliessen</>
) : (
  <>Weiter <ArrowRight size={14} /></>
)}
```

This preserves current text but uses backend readiness for the export state.

- [ ] **Step 4: Run frontend lint**

Run:

```bash
cd frontend && npm run lint
```

Expected: lint passes.

### Task 7: Route Design Completion Through Progression Result

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/DesignBundleController.kt`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/stores/wizard-store.ts`
- Test: `backend/src/test/kotlin/com/agentwork/productspecagent/api/DesignBundleControllerTest.kt`

- [ ] **Step 1: Add response fields to design complete response**

In `DesignBundleController.CompleteResponse`, add:

```kotlin
val progression: WizardProgressionView? = null,
val action: WizardClientActionDto? = null,
```

- [ ] **Step 2: Inject `WizardProgression` into `DesignBundleController`**

Change constructor:

```kotlin
class DesignBundleController(
    private val storage: DesignBundleStorage,
    private val props: DesignBundleProperties,
    private val designSummaryAgent: DesignSummaryAgent,
    private val projectService: ProjectService,
    private val wizardProgression: WizardProgression,
    @Value("\${app.frontend-origin:http://localhost:3001}") private val frontendOrigin: String,
)
```

- [ ] **Step 3: Use progression snapshot after design advance**

After calling `projectService.advanceStep(projectId, FlowStepType.DESIGN)`, build:

```kotlin
val progression = wizardProgression.snapshot(projectId)
val action = progression.currentStep
    ?.takeIf { it != FlowStepType.DESIGN.name }
    ?.let { WizardClientActionDto(type = "SHOW_STEP", step = it) }
    ?: if (progression.status == "READY_FOR_EXPORT") WizardClientActionDto(type = "OPEN_EXPORT") else WizardClientActionDto(type = "STAY")
return ResponseEntity.ok(CompleteResponse(message, nextStep?.name, progression, action))
```

Keep the existing `nextStep` field for compatibility.

- [ ] **Step 4: Extend frontend design response type**

In `frontend/src/lib/api.ts`, extend `DesignCompleteResponse`:

```ts
export interface DesignCompleteResponse {
  message: string;
  nextStep?: StepType | null;
  progression?: WizardProgressionView | null;
  action?: WizardClientAction | null;
}
```

- [ ] **Step 5: Update design branch in wizard store**

In the `step === "DESIGN"` branch of `completeStep`, after `completeDesignStep` returns:

```ts
if (response.progression) {
  set({ progression: response.progression });
}
if (response.action?.type === "SHOW_STEP" && response.action.step) {
  set({ activeStep: response.action.step });
} else if (response.nextStep) {
  const visible = visibleSteps();
  const nextVisible = visible.find((v) => v.key === response.nextStep);
  if (nextVisible) set({ activeStep: response.nextStep });
}
```

- [ ] **Step 6: Run backend and frontend checks**

Run:

```bash
cd backend && ./gradlew test --tests com.agentwork.productspecagent.api.DesignBundleControllerTest
cd frontend && npm run lint
```

Expected: backend design controller tests pass and frontend lint passes.

### Task 8: Full Verification and Cleanup

**Files:**
- Review: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- Review: `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`
- Review: `frontend/src/lib/stores/wizard-store.ts`
- Review: `frontend/src/components/wizard/WizardForm.tsx`

- [ ] **Step 1: Search for enum-order progression still used in completion paths**

Run:

```bash
rg "FlowStepType\\.entries|advanceStep\\(|exportTriggered|lastStepCompleted|wizardDone" backend/src/main/kotlin frontend/src -n
```

Expected:

- `FlowStepType.entries` may remain in `FlowState.kt` and fallback/default policy only.
- `advanceStep(` may remain in `ProjectService` and compatibility design code if Task 7 has not fully replaced it.
- `exportTriggered` may remain only for compatibility response mapping.
- `lastStepCompleted` should not remain in `WizardForm.tsx`.

- [ ] **Step 2: Run backend tests**

Run:

```bash
cd backend && ./gradlew test
```

Expected: build successful.

- [ ] **Step 3: Run frontend lint**

Run:

```bash
cd frontend && npm run lint
```

Expected: lint passes.

- [ ] **Step 4: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

## Self-Review

- Spec coverage: category-owned progression is covered by Tasks 1-4; frontend heuristic removal by Tasks 5-6; design compatibility by Task 7; verification by Task 8.
- Placeholder scan: this plan intentionally contains exact paths, commands, and code snippets for each change step.
- Type consistency: backend response names are `progression`, `action`, `artifacts`; frontend mirrors those names.
