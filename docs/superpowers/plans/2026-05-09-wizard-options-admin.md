# Wizard Options Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to execute this plan.

**Goal:** Build a maintainable admin page for wizard selection options so operators can add, rename, disable, reorder, and reset options for architecture, backend, frontend, database, deployment, API style, authentication, UI library, styling, and theme choices without changing code.

**Architecture:** The backend owns one global `WizardOptionCatalog`, stored in the existing object store at `config/wizard-options/catalog.json`. A default catalog mirrors the current hard-coded frontend configuration in `frontend/src/lib/category-step-config.ts`. The frontend loads the public catalog for the wizard and asset-bundle coverage, while an admin route edits the same catalog through admin API endpoints. Existing frontend helper functions remain as fallbacks so the wizard still works if the catalog request fails.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, kotlinx.serialization, existing `ObjectStore`, JUnit 5/MockMvc, Next.js App Router, React 19, Zustand, TypeScript, Tailwind CSS 4, existing UI components under `frontend/src/components/ui`.

## Desired Catalog Shape

Persist this JSON shape:

```json
{
  "version": 1,
  "categories": [
    {
      "id": "SaaS",
      "label": "SaaS",
      "visibleSteps": ["IDEA", "PROBLEM", "FEATURES", "MVP", "ARCHITECTURE", "BACKEND", "FRONTEND"],
      "allowedScopes": ["BACKEND", "FRONTEND"],
      "fields": [
        {
          "step": "ARCHITECTURE",
          "key": "architecture",
          "label": "System Architektur",
          "options": [
            { "id": "monolith", "label": "Monolith", "enabled": true },
            { "id": "microservices", "label": "Microservices", "enabled": true },
            { "id": "serverless", "label": "Serverless", "enabled": true }
          ]
        }
      ]
    }
  ],
  "updatedAt": "2026-05-09T00:00:00Z"
}
```

Rules:

- `version` starts at `1`.
- Category `id` values stay equal to the existing user-facing category names: `SaaS`, `Mobile App`, `CLI Tool`, `Library`, `Desktop App`, `API`.
- `visibleSteps` and `allowedScopes` use `FlowStepType` values.
- Field `step` uses `FlowStepType`.
- Option `id` is generated from the label with a stable slug when the admin creates a new option.
- Disabled options stay in the catalog but are hidden in wizard dropdowns and asset-bundle missing-triple calculations.
- Reset replaces the persisted catalog with the current default catalog.

## Default Values To Preserve

Copy the existing `CATEGORY_STEP_CONFIG` values exactly from `frontend/src/lib/category-step-config.ts` into the backend default catalog:

- `SaaS`: visible `BASE_STEPS + ARCHITECTURE + BACKEND + FRONTEND`, allowed `BACKEND`, `FRONTEND`.
- `Mobile App`: visible `BASE_STEPS + ARCHITECTURE + BACKEND + FRONTEND`, allowed `BACKEND`, `FRONTEND`.
- `CLI Tool`: visible `BASE_STEPS + ARCHITECTURE`, allowed `BACKEND`.
- `Library`: visible `BASE_STEPS`, allowed none, no fields.
- `Desktop App`: visible `BASE_STEPS + ARCHITECTURE + BACKEND + FRONTEND`, allowed `BACKEND`, `FRONTEND`.
- `API`: visible `BASE_STEPS + ARCHITECTURE + BACKEND`, allowed `BACKEND`.

Field labels and option labels must match the current frontend file:

- Architecture fields: `architecture`, `database`, `deployment`.
- Backend fields: `framework`, `apiStyle`, `auth`.
- Frontend fields: `framework`, `uiLibrary`, `styling`, `theme`.

## Task 1: Backend Domain, Defaults, Storage, And Validation

**Files**

- Create `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardOptionCatalog.kt`
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogDefaults.kt`
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/storage/WizardOptionCatalogStorage.kt`
- Create `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogService.kt`
- Create `backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogServiceTest.kt`

**Test first**

Add `WizardOptionCatalogServiceTest` with tests:

1. `returns default catalog when no catalog is persisted`
   - Use an in-memory `ObjectStore` test double.
   - Assert the first category is `SaaS`.
   - Assert `SaaS` contains `ARCHITECTURE`, `BACKEND`, `FRONTEND`.
   - Assert `SaaS` backend `framework` contains `Kotlin + Spring`.

2. `persists and reloads updated catalog`
   - Load default catalog.
   - Add option `{ id = "elixir-phoenix", label = "Elixir + Phoenix", enabled = true }` to `SaaS/BACKEND/framework`.
   - Save catalog.
   - Create a new service instance over the same storage.
   - Assert the new option is returned.

3. `reset replaces persisted catalog with defaults`
   - Save a catalog containing `Elixir + Phoenix`.
   - Call reset.
   - Assert `Elixir + Phoenix` is gone.
   - Assert defaults are present.

4. `rejects duplicate category ids`
   - Save a catalog with two `SaaS` categories.
   - Assert service throws `WizardOptionCatalogValidationException`.

5. `rejects duplicate field keys inside a category step`
   - Save a catalog where `SaaS` has two `BACKEND/framework` fields.
   - Assert validation exception.

6. `rejects blank option labels`
   - Save a catalog containing an option label `"   "`.
   - Assert validation exception.

Run:

```bash
cd backend && ./gradlew test --tests 'com.agentwork.productspecagent.service.WizardOptionCatalogServiceTest'
```

**Implement**

Domain:

```kotlin
@Serializable
data class WizardOptionCatalog(
    val version: Int = 1,
    val categories: List<WizardOptionCategory>,
    val updatedAt: String
)

@Serializable
data class WizardOptionCategory(
    val id: String,
    val label: String,
    val visibleSteps: List<FlowStepType>,
    val allowedScopes: List<FlowStepType>,
    val fields: List<WizardOptionField> = emptyList()
)

@Serializable
data class WizardOptionField(
    val step: FlowStepType,
    val key: String,
    val label: String,
    val options: List<WizardOption> = emptyList()
)

@Serializable
data class WizardOption(
    val id: String,
    val label: String,
    val enabled: Boolean = true
)
```

Storage:

- Inject the existing `ObjectStore`.
- Use key `config/wizard-options/catalog.json`.
- Use `Json { ignoreUnknownKeys = true; prettyPrint = true }`.
- Methods:
  - `load(): WizardOptionCatalog?`
  - `save(catalog: WizardOptionCatalog): WizardOptionCatalog`
  - `delete()`

Service:

- Methods:
  - `getCatalog(): WizardOptionCatalog`
  - `saveCatalog(catalog: WizardOptionCatalog): WizardOptionCatalog`
  - `resetCatalog(): WizardOptionCatalog`
- `getCatalog` returns defaults if no persisted catalog exists.
- `saveCatalog` validates, updates `updatedAt` to `Clock` time, and persists.
- Inject `Clock` for deterministic tests.
- Validation:
  - `version >= 1`
  - category ids and labels are not blank.
  - category ids are unique.
  - `visibleSteps` contains all base steps `IDEA`, `PROBLEM`, `FEATURES`, `MVP`.
  - fields have nonblank `key` and `label`.
  - field identity `(step, key)` is unique inside each category.
  - options have nonblank `id` and `label`.
  - option ids are unique inside each field.

Commit checkpoint after task 1:

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardOptionCatalog.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogDefaults.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/storage/WizardOptionCatalogStorage.kt \
  backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogService.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogServiceTest.kt
git commit -m "feat(wizard-options): add catalog service"
```

## Task 2: Backend API

**Files**

- Create `backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardOptionCatalogController.kt`
- Create `backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardOptionCatalogControllerTest.kt`

**Test first**

Add controller tests with MockMvc:

1. `GET /api/v1/wizard-options returns catalog`
   - Assert status 200.
   - Assert `$.categories[0].id` exists.

2. `GET /api/v1/admin/wizard-options returns catalog`
   - Assert status 200.

3. `PUT /api/v1/admin/wizard-options persists catalog`
   - Send current catalog with one added option.
   - Assert status 200.
   - GET public catalog.
   - Assert option exists.

4. `POST /api/v1/admin/wizard-options/reset restores defaults`
   - Persist a modified catalog.
   - Reset.
   - Assert modified option is gone.

5. `PUT /api/v1/admin/wizard-options returns 400 for invalid catalog`
   - Send duplicate category ids.
   - Assert status 400.
   - Assert response contains a useful validation message.

Run:

```bash
cd backend && ./gradlew test --tests 'com.agentwork.productspecagent.api.WizardOptionCatalogControllerTest'
```

**Implement**

Routes:

- `GET /api/v1/wizard-options` -> public catalog.
- `GET /api/v1/admin/wizard-options` -> admin catalog.
- `PUT /api/v1/admin/wizard-options` -> validate and persist request body.
- `POST /api/v1/admin/wizard-options/reset` -> reset to defaults.

Controller behavior:

- Return JSON body directly.
- Map `WizardOptionCatalogValidationException` to `400 Bad Request` with a small error payload:

```kotlin
@Serializable
data class WizardOptionCatalogErrorResponse(val message: String)
```

Commit checkpoint after task 2:

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/WizardOptionCatalogController.kt \
  backend/src/test/kotlin/com/agentwork/productspecagent/api/WizardOptionCatalogControllerTest.kt
git commit -m "feat(wizard-options): expose catalog api"
```

## Task 3: Frontend API Types And Client

**Files**

- Edit `frontend/src/lib/api.ts`

**Implement**

Add TypeScript types that match the backend JSON:

```ts
export interface WizardOption {
  id: string
  label: string
  enabled: boolean
}

export interface WizardOptionField {
  step: StepType
  key: string
  label: string
  options: WizardOption[]
}

export interface WizardOptionCategory {
  id: Category
  label: string
  visibleSteps: StepType[]
  allowedScopes: StepType[]
  fields: WizardOptionField[]
}

export interface WizardOptionCatalog {
  version: number
  categories: WizardOptionCategory[]
  updatedAt: string
}
```

Add API functions:

```ts
getWizardOptions: () => apiFetch<WizardOptionCatalog>("/api/v1/wizard-options"),
getAdminWizardOptions: () => apiFetch<WizardOptionCatalog>("/api/v1/admin/wizard-options"),
saveAdminWizardOptions: (catalog: WizardOptionCatalog) =>
  apiFetch<WizardOptionCatalog>("/api/v1/admin/wizard-options", {
    method: "PUT",
    body: JSON.stringify(catalog),
  }),
resetAdminWizardOptions: () =>
  apiFetch<WizardOptionCatalog>("/api/v1/admin/wizard-options/reset", { method: "POST" }),
```

Verify changed file compiles with later frontend tasks.

## Task 4: Catalog Adapter And Default Frontend Fallback

**Files**

- Edit `frontend/src/lib/category-step-config.ts`

**Implement**

Keep the existing exports for compatibility:

- `Category`
- `ALL_STEP_KEYS`
- `BASE_STEPS`
- `CATEGORY_STEP_CONFIG`
- `getVisibleSteps`
- `getFieldOptions`
- `getAllowedScopes`

Add these exports:

```ts
export const DEFAULT_CATEGORY_STEP_CONFIG = CATEGORY_STEP_CONFIG

export function catalogToCategoryStepConfig(catalog: WizardOptionCatalog): Record<Category, CategoryStepConfig> {
  // Convert enabled catalog options to the existing config shape.
}

export function getVisibleStepsFromCatalog(
  catalog: WizardOptionCatalog | null,
  category: Category,
): StepType[] {
  // Return catalog category visibleSteps, fallback to getVisibleSteps(category).
}

export function getFieldOptionsFromCatalog(
  catalog: WizardOptionCatalog | null,
  category: Category,
  step: StepType,
): Record<string, string[]> {
  // Return enabled options only, fallback to getFieldOptions(category, step).
}

export function getAllowedScopesFromCatalog(
  catalog: WizardOptionCatalog | null,
  category: Category,
): StepType[] {
  // Return catalog category allowedScopes, fallback to getAllowedScopes(category).
}
```

Implementation details:

- Import `WizardOptionCatalog` from `frontend/src/lib/api.ts`.
- Filter options with `option.enabled !== false`.
- If a category or field is missing in the catalog, use the current hard-coded fallback for that category and step.
- Do not mutate `CATEGORY_STEP_CONFIG`.

## Task 5: Wizard Options Store

**Files**

- Create `frontend/src/lib/stores/wizard-options-store.ts`

**Implement**

Create a Zustand store:

```ts
interface WizardOptionsState {
  catalog: WizardOptionCatalog | null
  adminCatalog: WizardOptionCatalog | null
  loading: boolean
  saving: boolean
  error: string | null
  loadCatalog: () => Promise<void>
  loadAdminCatalog: () => Promise<void>
  saveAdminCatalog: (catalog: WizardOptionCatalog) => Promise<void>
  resetAdminCatalog: () => Promise<void>
  setAdminCatalog: (catalog: WizardOptionCatalog) => void
}
```

Behavior:

- `loadCatalog` calls `api.getWizardOptions`.
- `loadAdminCatalog` calls `api.getAdminWizardOptions`.
- `saveAdminCatalog` persists via `api.saveAdminWizardOptions`, then updates both `adminCatalog` and `catalog`.
- `resetAdminCatalog` calls reset, then updates both `adminCatalog` and `catalog`.
- On API failure, keep previous catalog and set `error`.
- The store does not own editor-specific draft mutations; the admin page can edit a local draft and save it.

## Task 6: Wizard Runtime Integration

**Files**

- Edit `frontend/src/lib/stores/wizard-store.ts`
- Edit `frontend/src/components/wizard/steps/ArchitectureForm.tsx`
- Edit `frontend/src/components/wizard/steps/BackendForm.tsx`
- Edit `frontend/src/components/wizard/steps/FrontendForm.tsx`

**Implement**

Wizard store:

- Import `useWizardOptionsStore` and `getVisibleStepsFromCatalog`.
- In `loadWizard`, load the public catalog before deriving visible steps:

```ts
const optionsStore = useWizardOptionsStore.getState()
if (!optionsStore.catalog && !optionsStore.loading) {
  await optionsStore.loadCatalog()
}
const catalog = useWizardOptionsStore.getState().catalog
const visibleSteps = getVisibleStepsFromCatalog(catalog, wizard.category)
```

- In `setCategory`, derive visible steps from the current catalog.

Forms:

- Replace direct `getFieldOptions(category, step)` reads with catalog-aware helper calls using `useWizardOptionsStore((state) => state.catalog)`.
- Preserve `DEFAULT_OPTIONS` fallback behavior.
- Hide disabled options because `getFieldOptionsFromCatalog` filters them.

Verify:

- Existing wizard dropdowns still show the same options before any admin changes.
- After saving a new backend framework, the backend form shows it without code changes.

## Task 7: Asset Bundle Coverage Uses The Catalog

**Files**

- Edit `frontend/src/lib/asset-bundles/possible-triples.ts`
- Edit `frontend/src/lib/stores/asset-bundle-store.ts`
- Edit `frontend/src/components/asset-bundles/AssetBundlesPage.tsx`

**Implement**

`possible-triples.ts`:

- Change `getAllPossibleTriples()` to accept an optional catalog:

```ts
export function getAllPossibleTriples(catalog?: WizardOptionCatalog | null): BundleTriple[] {
  const config = catalog ? catalogToCategoryStepConfig(catalog) : CATEGORY_STEP_CONFIG
  // existing traversal over config
}
```

- Only enabled options from the catalog participate because the adapter filters them.

Asset bundle store:

- Change `getMissingTriples` to accept an optional catalog:

```ts
getMissingTriples: (catalog?: WizardOptionCatalog | null) => BundleTriple[]
```

Asset bundles page:

- Load the public wizard options catalog on mount when missing tab data is shown.
- Pass `catalog` into `getMissingTriples(catalog)`.

Verify:

- Adding a new backend framework option creates new missing asset-bundle triples.
- Disabling an option removes related missing triples.

## Task 8: Admin Page UI

**Files**

- Create `frontend/src/app/admin/wizard-options/page.tsx`
- Create `frontend/src/components/wizard-options/WizardOptionsAdminPage.tsx`
- Create `frontend/src/components/wizard-options/WizardOptionFieldEditor.tsx`
- Edit `frontend/src/components/layout/AppShell.tsx`

**Implement**

Route:

- `/admin/wizard-options`
- Page component imports and renders `WizardOptionsAdminPage`.

Navigation:

- Add a nav item near Asset Bundles:
  - label: `Wizard Optionen`
  - href: `/admin/wizard-options`
  - icon: `SlidersHorizontal` or `ListChecks` from `lucide-react`

Admin page behavior:

- On mount, call `loadAdminCatalog`.
- Keep a local `draft` catalog.
- Show category tabs or a compact category list.
- For the selected category, show fields grouped by step.
- Each field editor supports:
  - add option
  - rename option label
  - enable/disable option
  - move option up/down
  - remove option from draft before save
- Top actions:
  - `Speichern`
  - `Zuruecksetzen`
  - reload/discard draft if dirty
- Disable save while saving or while no draft exists.
- Show API errors in a small inline error block.

Slug generation:

```ts
function slugifyOptionLabel(label: string): string {
  return label
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
}
```

When adding a duplicate slug in the same field, append `-2`, `-3`, etc.

UI constraints:

- Use existing `Button`, `Input`, and layout primitives if present.
- Use a checkbox or existing switch for enabled state.
- Do not use a marketing-style page; this is a dense admin tool.
- Keep labels short and German-facing.
- Avoid nested cards; use a simple two-column admin layout or full-width sections.

## Task 9: Focused Frontend Verification

Run the narrowest frontend validation after Tasks 3-8:

```bash
cd frontend && npx eslint \
  src/lib/api.ts \
  src/lib/category-step-config.ts \
  src/lib/stores/wizard-options-store.ts \
  src/lib/stores/wizard-store.ts \
  src/lib/stores/asset-bundle-store.ts \
  src/lib/asset-bundles/possible-triples.ts \
  src/components/wizard/steps/ArchitectureForm.tsx \
  src/components/wizard/steps/BackendForm.tsx \
  src/components/wizard/steps/FrontendForm.tsx \
  src/components/wizard-options/WizardOptionsAdminPage.tsx \
  src/components/wizard-options/WizardOptionFieldEditor.tsx \
  src/components/asset-bundles/AssetBundlesPage.tsx \
  src/components/layout/AppShell.tsx \
  src/app/admin/wizard-options/page.tsx
```

Then run:

```bash
cd frontend && npm run build
```

If the full frontend build fails because of unrelated pre-existing issues, capture the failing files and still fix all errors in files touched for this feature.

Commit checkpoint after Tasks 3-9:

```bash
git add frontend/src/lib/api.ts \
  frontend/src/lib/category-step-config.ts \
  frontend/src/lib/stores/wizard-options-store.ts \
  frontend/src/lib/stores/wizard-store.ts \
  frontend/src/lib/stores/asset-bundle-store.ts \
  frontend/src/lib/asset-bundles/possible-triples.ts \
  frontend/src/components/wizard/steps/ArchitectureForm.tsx \
  frontend/src/components/wizard/steps/BackendForm.tsx \
  frontend/src/components/wizard/steps/FrontendForm.tsx \
  frontend/src/components/wizard-options/WizardOptionsAdminPage.tsx \
  frontend/src/components/wizard-options/WizardOptionFieldEditor.tsx \
  frontend/src/components/asset-bundles/AssetBundlesPage.tsx \
  frontend/src/components/layout/AppShell.tsx \
  frontend/src/app/admin/wizard-options/page.tsx
git commit -m "feat(wizard-options): add admin catalog ui"
```

## Task 10: End-To-End Backend Verification And Feature Done Doc

**Files**

- Create `docs/features/46-wizard-options-admin-done.md`

**Verify**

Run backend tests:

```bash
cd backend && ./gradlew test
```

Run frontend verification again if code changed after Task 9:

```bash
cd frontend && npx eslint \
  src/lib/api.ts \
  src/lib/category-step-config.ts \
  src/lib/stores/wizard-options-store.ts \
  src/lib/stores/wizard-store.ts \
  src/lib/stores/asset-bundle-store.ts \
  src/lib/asset-bundles/possible-triples.ts \
  src/components/wizard/steps/ArchitectureForm.tsx \
  src/components/wizard/steps/BackendForm.tsx \
  src/components/wizard/steps/FrontendForm.tsx \
  src/components/wizard-options/WizardOptionsAdminPage.tsx \
  src/components/wizard-options/WizardOptionFieldEditor.tsx \
  src/components/asset-bundles/AssetBundlesPage.tsx \
  src/components/layout/AppShell.tsx \
  src/app/admin/wizard-options/page.tsx
cd frontend && npm run build
```

Done doc content:

```md
# Feature 46 Done: Wizard Options Admin

## Implemented

- Backend catalog domain, default catalog, object-store persistence, validation, and reset.
- Public and admin API endpoints for wizard options.
- Frontend catalog client and catalog-aware wizard option helpers.
- Admin page at `/admin/wizard-options`.
- Wizard dropdowns and asset-bundle missing coverage now read from the catalog.

## Verification

- `cd backend && ./gradlew test`
- `cd frontend && npx eslint ...`
- `cd frontend && npm run build`

## Notes

- Defaults mirror the previous hard-coded frontend options.
- Disabled options remain stored but are hidden from wizard dropdowns and missing asset-bundle calculations.
```

Commit final docs:

```bash
git add docs/features/46-wizard-options-admin-done.md
git commit -m "docs(feature): mark wizard options admin done"
```

## Final Acceptance Criteria

- `/admin/wizard-options` exists in the app shell navigation.
- Admin can add a backend language/framework option and save it.
- Reloading the page preserves the saved option.
- Wizard backend form shows the saved option.
- Disabling the option hides it from the wizard without deleting it from the admin catalog.
- Reset restores the original default options.
- Public wizard API and admin wizard API return the same catalog after save/reset.
- Asset-bundle missing coverage includes enabled catalog options and excludes disabled ones.
- Backend tests for catalog service and API pass.
- Frontend changed files pass ESLint.
- Frontend production build succeeds or only fails on clearly unrelated pre-existing issues, which are documented in the final response.
