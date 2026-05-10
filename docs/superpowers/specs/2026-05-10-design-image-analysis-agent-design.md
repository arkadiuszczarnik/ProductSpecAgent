# Design Image Analysis Agent

## Context

Feature 47 is currently implemented as Simple Design Generator V1: the user provides a description, an image, or both, and the system generates one active HTML design on the canvas. The current backend only passes image metadata to `DesignVariantAgent`. It does not inspect the uploaded image content, so colors, typography, visible components, and layout structure are not translated into the design prompt.

This feature adds a dedicated image analysis step before HTML generation. The image analysis produces structured design context that is visible in the UI and reused by the design generation agent.

## Goals

- Analyze an uploaded design/reference image with a dedicated agent.
- Translate the image into a detailed structured design model.
- Store the image analysis on the `DesignWorkbench`.
- Show a compact, non-editable analysis summary in the DESIGN step.
- Allow retry only when image analysis failed.
- Ensure `POST /design/generate` automatically runs image analysis when an image exists but no analysis has been stored.
- Feed the stored image analysis into `DesignVariantAgent` so generated HTML reflects image elements, colors, typography, layout, components, mood, and brand signals.

## Non-Goals

- No editable image-analysis UI in this iteration.
- No raw Debug JSON in the V1 UI.
- No async background job infrastructure.
- No multi-image analysis.
- No variant history or multi-screen workflow.
- No revival of legacy workbench compatibility models.

## User Flow

1. User opens the DESIGN step.
2. User enters a description, uploads an image, or both.
3. User clicks `Design generieren`.
4. Frontend saves the input through `PUT /design/input`.
5. If a new image was selected, frontend calls `POST /design/image/analyze`.
6. UI shows `Bild wird analysiert` while the analysis request is in flight.
7. On success, UI shows a compact summary:
   - design direction,
   - palette,
   - layout.
8. On failure, UI shows the error and an `Analyse erneut versuchen` retry action.
9. Frontend continues with `POST /design/generate` after successful analysis.
10. If the frontend skips image analysis or loads an older workbench with image but no analysis, `POST /design/generate` runs analysis before generation.
11. `DesignVariantAgent` generates HTML from user description, image metadata, and image analysis.

## Design Decisions

- Analysis is a separate agent: `DesignImageAnalysisAgent`.
- Analysis is triggered by an explicit endpoint: `POST /design/image/analyze`.
- Upload stays simple and only stores input.
- Generate is robust and fills missing image analysis automatically.
- Backend stores detailed JSON, but frontend only renders compact cards in V1.
- Retry button appears only after analysis failure.

## Data Model

Extend `DesignWorkbench`:

```kotlin
@Serializable
data class DesignWorkbench(
    val projectId: String,
    val description: String? = null,
    val imageInput: DesignImageInput? = null,
    val imageAnalysis: DesignImageAnalysis? = null,
    val imageAnalysisError: String? = null,
    val analysis: DesignAnalysis? = null,
    val currentDesign: GeneratedDesign? = null,
    val updatedAt: String,
)
```

Add detailed image analysis types:

```kotlin
@Serializable
data class DesignImageAnalysis(
    val summary: String,
    val palette: List<DesignColor>,
    val typography: List<DesignTypographySignal>,
    val layoutHierarchy: List<DesignLayoutRegion>,
    val components: List<DesignComponentSignal>,
    val moodTags: List<String>,
    val brandSignals: List<String>,
    val designBrief: String,
)

@Serializable
data class DesignColor(
    val hex: String,
    val role: String,
    val weight: String,
    val notes: String,
)

@Serializable
data class DesignTypographySignal(
    val category: String,
    val role: String,
    val weight: String,
    val notes: String,
)

@Serializable
data class DesignLayoutRegion(
    val name: String,
    val order: Int,
    val priority: Int,
    val description: String,
)

@Serializable
data class DesignComponentSignal(
    val name: String,
    val role: String,
    val description: String,
)
```

Field rules:

- `summary`: short human-readable analysis.
- `palette`: 3-8 colors. `hex` must be normalized as `#RRGGBB` when possible.
- `typography`: categories like `ui-sans`, `serif-display`, `mono-labels`, not exact font guesses unless visually obvious.
- `layoutHierarchy`: ordered top-level regions with stable priorities.
- `components`: concrete detected UI/content elements.
- `moodTags`: short lowercase tags.
- `brandSignals`: visual brand cues and repeating patterns.
- `designBrief`: compact prompt-ready text for HTML generation.

## Backend API

Existing endpoints stay:

- `GET /api/v1/projects/{projectId}/design/workbench`
- `PUT /api/v1/projects/{projectId}/design/input`
- `POST /api/v1/projects/{projectId}/design/generate`
- `GET /api/v1/projects/{projectId}/design/preview`
- `POST /api/v1/projects/{projectId}/design/complete`

Add:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/projects/{projectId}/design/image/analyze` | Analyze the currently stored image and persist `imageAnalysis`. |

`POST /design/image/analyze` behavior:

- Requires DESIGN access through the same wizard visibility/current-step checks as input and generate.
- Returns `400` if no image exists.
- Reads image bytes from `imageInput.contentRef`.
- Calls `DesignImageAnalysisAgent`.
- On success:
  - stores `imageAnalysis`,
  - clears `imageAnalysisError`,
  - clears `currentDesign` and `analysis` only if the underlying input changed before analysis.
- On agent or parser failure:
  - stores `imageAnalysisError`,
  - keeps existing `imageAnalysis` if it belongs to the same image,
  - returns `400` with the error message.

`POST /design/generate` behavior:

- Requires description or image.
- If image exists and `imageAnalysis == null`, calls the image analysis service first.
- If image analysis fails during generate, returns `400` and does not replace the current valid design.
- Passes description, image metadata, and `imageAnalysis` to `DesignVariantAgent`.

## Storage

`DesignWorkbenchStorage.saveInput` and `saveImageInput` must invalidate image analysis consistently:

- Description-only change:
  - clears generated design and `analysis`,
  - keeps existing `imageInput`,
  - keeps `imageAnalysis` if the image did not change.
- New image upload:
  - stores the new image,
  - clears `imageAnalysis`,
  - clears `imageAnalysisError`,
  - clears generated design and `analysis`.
- Image-only existing state with no new file:
  - does not clear existing image analysis.

Add storage helpers:

```kotlin
fun readImageInput(projectId: String): ByteArray
fun saveImageAnalysis(projectId: String, analysis: DesignImageAnalysis): DesignWorkbench
fun saveImageAnalysisError(projectId: String, message: String): DesignWorkbench
```

## Agents

### DesignImageAnalysisAgent

Create a new agent with ID `design-image-analysis`.

Input:

```kotlin
data class DesignImageAnalysisInput(
    val projectId: String,
    val image: DesignImageInput,
    val bytes: ByteArray,
)
```

Output:

```kotlin
data class DesignImageAnalysisResult(
    val analysis: DesignImageAnalysis,
)
```

The agent uses:

- prompt ID `design-image-analysis-system`,
- model tier `MEDIUM` by default,
- Koog multimodal prompt support for image attachments.

Context7 note: Koog documents multimodal prompts with `user { text(...); image(...) }` and local attachments. The current `KoogAgentRunner` only supports text, so implementation needs a small multimodal runner method or a dedicated vision runner path.

Fallback behavior:

- If no Koog runner is available in tests/local fallback, return deterministic analysis based on image metadata.
- Fallback must be obviously generic and should not pretend to inspect pixels.

### DesignVariantAgent

Extend generation input:

```kotlin
data class DesignGenerationInput(
    val projectId: String,
    val description: String?,
    val image: DesignImageInput?,
    val imageAnalysis: DesignImageAnalysis?,
)
```

The prompt must include the full image analysis JSON plus a short prose summary. The generated HTML should use `designBrief` as the primary visual guidance when present.

## Prompt Registry And Models

Add prompt definition:

- ID: `design-image-analysis-system`
- Agent: `DesignImageAnalysis`
- File: `backend/src/main/resources/prompts/design-image-analysis-system.md`
- Validators: not blank, max length 50,000

Add model default:

```yaml
agent:
  models:
    defaults:
      design-image-analysis: MEDIUM
```

The prompt must require valid JSON only and include the exact response shape. It must instruct the model not to identify people, infer sensitive traits, or describe private personal attributes from the image.

## Frontend

Extend API helpers:

```ts
export async function analyzeDesignImage(projectId: string): Promise<DesignWorkbench>
```

Extend Zustand store:

- `analyzingImage: boolean`
- `analyzeImage(projectId): Promise<void>`

UI behavior in `DesignInputPanel`:

- If a new image was selected and input save succeeds, call `analyzeImage`.
- Show `Bild wird analysiert` while analyzing.
- Show compact analysis after success:
  - `Designrichtung`: `summary` plus up to 4 `moodTags`.
  - `Palette`: first 4-6 `palette` colors as swatches.
  - `Layout`: first 3 `layoutHierarchy` regions.
- If `imageAnalysisError` is present, show a compact error and `Analyse erneut versuchen`.
- No raw JSON panel in V1.

Generate flow:

- `Design generieren` should save input.
- If image exists and no analysis exists, run `analyzeImage`.
- If analysis fails, stop before `generate`.
- If analysis succeeds or is already present, run `generate`.

## Error Handling

- No image: `POST /image/analyze` returns `400`.
- Empty image bytes: existing image upload validation rejects it before analysis.
- Unsupported image type: existing image upload validation rejects non-`image/*`.
- Oversized image: existing 5 MB service limit rejects it before analysis.
- Invalid agent JSON:
  - set `imageAnalysisError`,
  - return `400`,
  - do not replace current design.
- Vision provider failure:
  - set `imageAnalysisError`,
  - return `400`,
  - show retry in UI.

## Security And Privacy

- The image is only sent to the configured AI provider during explicit analysis or generate fallback.
- Analysis prompt must focus on visual design attributes: colors, layout, typography, components, mood, and brand signals.
- The agent must not identify people or infer sensitive personal traits.
- Stored analysis must not include raw image bytes.
- Generated HTML remains subject to `DesignPreviewValidator`.

## Testing

Backend tests:

- `DesignWorkbenchStorageTest`
  - new image upload clears `imageAnalysis` and `imageAnalysisError`;
  - description-only update preserves existing image analysis;
  - `saveImageAnalysis` persists detailed JSON;
  - `saveImageAnalysisError` persists retryable error state.
- `DesignImageAnalysisAgentTest`
  - parses valid JSON;
  - fallback is deterministic;
  - invalid JSON throws `InvalidDesignImageAnalysisException` so the service can persist `imageAnalysisError`.
- `DesignWorkbenchServiceTest`
  - `analyzeImage` rejects missing image;
  - `analyzeImage` stores analysis;
  - `analyzeImage` stores error on failure;
  - `generate` runs missing image analysis before design generation;
  - `generate` passes image analysis to `DesignVariantAgent`;
  - `generate` preserves existing valid design when analysis fails.
- `DesignWorkbenchControllerTest`
  - `POST /design/image/analyze` returns workbench with `imageAnalysis`;
  - missing image returns `400`;
  - analysis failure returns `400` and exposes `imageAnalysisError`.
- Registry tests:
  - `AgentModelRegistryTest` includes `design-image-analysis`;
  - `PromptRegistryTest` includes `design-image-analysis-system`;
  - prompt controller tests expose the new prompt.

Frontend tests/checks:

- API helper calls `POST /design/image/analyze`.
- Store exposes `analyzeImage` and `analyzingImage`.
- `DesignInputPanel` shows compact analysis cards.
- Retry appears only when `imageAnalysisError` exists.
- Generate flow stops when analysis fails.

Verification:

```bash
cd backend
./gradlew test
```

```bash
cd frontend
npm run lint
npm run build
```

## Acceptance Criteria

1. Uploading an image can trigger a dedicated image analysis call.
2. Image analysis produces detailed structured JSON with palette, typography, layout hierarchy, components, mood tags, brand signals, and design brief.
3. The UI shows a compact non-editable analysis summary.
4. Retry is visible only after image analysis failure.
5. `POST /design/generate` automatically analyzes an image when analysis is missing.
6. `DesignVariantAgent` receives and uses image analysis.
7. New image upload clears stale image analysis.
8. Description-only changes do not delete image analysis for the same stored image.
9. Agent/model/prompt registries expose `design-image-analysis`.
10. Generated HTML remains validated by existing preview security.
