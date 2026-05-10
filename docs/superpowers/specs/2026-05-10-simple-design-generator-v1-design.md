# Simple Design Generator V1

## Context

The current Agentic Design Workbench is too broad for the intended first usable version. It exposes screens, variants, snippets, input classification, and suggestions. The product direction for V1 is smaller: the DESIGN step should let the user provide a description, an image, or both, then generate one HTML layout on a canvas.

This spec replaces the visible Workbench flow with a simpler generator while keeping the secure preview and wizard handoff behavior.

## Goals

- Let the user enter a design description, upload one reference image, or provide both.
- Generate one active HTML layout from the current inputs.
- Show the generated layout in the canvas immediately after generation.
- Let the user regenerate from the current inputs; the newest valid generation replaces the active canvas design.
- Let the user complete the DESIGN step only when a valid generated design exists.
- Keep generated HTML preview validation and CSP restrictions.

## Non-Goals

- No visible screen management.
- No visible variant history or variant picker.
- No HTML/CSS snippet upload in V1.
- No editable classification UI.
- No suggestion list or apply-suggestion workflow.
- No multi-screen export from the simplified V1.

## User Flow

1. User opens the DESIGN step.
2. The left side shows a description field and image upload.
3. User may provide description only, image only, or both.
4. User clicks `Design generieren`.
5. Backend stores the current input, asks the design agent to analyze it, validates the returned HTML, stores the valid result, and returns the updated workbench.
6. Frontend renders the active result in the canvas iframe.
7. User can click `Neu generieren`; the backend runs the same process again and replaces the active result.
8. User clicks `Design übernehmen`; the wizard completes DESIGN and uses the active generated design in export/handoff.

## Data Model

Use a simplified workbench contract:

```kotlin
data class DesignWorkbench(
    val projectId: String,
    val description: String? = null,
    val imageInput: DesignImageInput? = null,
    val analysis: DesignAnalysis? = null,
    val currentDesign: GeneratedDesign? = null,
    val updatedAt: String,
)

data class DesignImageInput(
    val originalName: String,
    val contentRef: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: String,
)

data class DesignAnalysis(
    val summary: String,
    val visualDirection: String,
    val rationale: String,
)

data class GeneratedDesign(
    val id: String,
    val title: String,
    val htmlPath: String,
    val rationale: String,
    val createdAt: String,
)
```

The implementation may keep migration tolerance for the existing broad workbench JSON, but the public V1 contract should be the simplified shape above.

## Backend API

Keep endpoints under `/api/v1/projects/{projectId}/design`.

- `GET /workbench`
  - Returns the simplified workbench.

- `PUT /input`
  - Multipart form request.
  - Fields:
    - `description`: optional string.
    - `file`: optional image file.
  - Rejects the request when both are missing or blank.
  - Replaces the current input state.
  - Clears `currentDesign` when the input changes, because the previous design no longer matches the input.

- `POST /generate`
  - Requires existing description or image.
  - Calls the design generation agent with all current input.
  - Stores `analysis` and `currentDesign`.
  - Validates generated HTML before storing it as the active preview.

- `GET /preview`
  - Returns the active generated HTML.
  - Returns `404` if no current design exists.
  - Uses the same secure preview headers and CSP as the existing workbench preview.

- `POST /complete`
  - Requires `currentDesign`.
  - Completes the DESIGN wizard step through the existing progression boundary.

Existing legacy ZIP endpoints remain outside the primary DESIGN UI. Broad workbench endpoints for screens, variants, suggestions, snippets, and classifications should be removed from the primary frontend path and can be deleted or left internal only if required for migration.

## Agent Behavior

Add or reshape a design generation agent with this interface:

```kotlin
data class DesignGenerationInput(
    val projectId: String,
    val description: String?,
    val image: DesignImageInput?,
)

data class DesignGenerationResult(
    val analysis: DesignAnalysis,
    val title: String,
    val html: String,
    val rationale: String,
)
```

The production path should follow existing Koog runner patterns where available. The fallback/test path can generate deterministic HTML from the description and image metadata so tests are stable.

The prompt should ask for a complete standalone HTML document suitable for iframe preview. The output parser should require structured JSON containing `analysis`, `title`, `html`, and `rationale`.

## Frontend Design

Replace the current three-column Workbench with two clear zones:

- Left panel:
  - Description textarea.
  - Image upload control.
  - `Design generieren` button.
  - `Neu generieren` button after a design exists.
  - `Design übernehmen` button, disabled until `currentDesign` exists.
  - Compact analysis summary after generation.

- Right canvas:
  - Empty state before generation.
  - Loading state while generating.
  - Iframe preview from `GET /design/preview` after generation.

The frontend should remove visible controls for screens, variants, snippets, classification edits, and suggestion application.

## Error Handling

- Empty input: inline error, no backend generation.
- Non-image upload: backend returns `400`; frontend shows the message.
- Image over 5 MB: backend returns `400`; frontend shows the message.
- Agent returns invalid or unsafe HTML: backend rejects generation and preserves the previous valid `currentDesign` if one exists.
- Complete without generated design: backend returns `400`; frontend keeps the step incomplete.

## Security

Generated HTML remains untrusted. Continue using the existing `DesignPreviewValidator` and iframe preview restrictions:

- No external URLs or protocol-relative URLs.
- No storage, cookie, parent/window opener, network APIs, or unsafe form actions.
- CSP blocks network access and embedding outside the allowed frame ancestor.
- Data images and inline styles/scripts remain allowed only to the extent currently supported by the validator.

## Testing

Backend tests:

- `PUT /input` rejects empty description plus missing image.
- Description-only input can be saved.
- Image-only input can be saved.
- Combined description plus image can be saved.
- `POST /generate` creates `analysis` and `currentDesign`.
- Regenerate replaces `currentDesign`.
- Unsafe generated HTML is rejected and previous valid design is preserved.
- `POST /complete` rejects missing `currentDesign`.
- `POST /complete` succeeds when `currentDesign` exists.

Frontend tests/checks:

- Store exposes only simplified V1 actions needed by the UI.
- DESIGN UI renders description, image upload, generate, regenerate, preview, and complete controls.
- Complete is disabled before generation and enabled after `currentDesign`.
- Changed frontend files pass targeted lint.

Verification:

- `cd backend && ./gradlew test`
- `cd frontend && npm run lint -- <changed frontend files>`
- `cd frontend && npm run build`

## Implementation Notes

Context7 references checked:

- Next.js App Router client components support client-side form handling with `FormData` and `fetch`; the simplified UI should remain a client component because it manages local input state, file selection, and async backend calls.
- Spring Boot supports multipart uploads through Spring MVC multipart handling; the simplified `PUT /input` should use multipart form data because the image is optional but binary.

## Open Decisions

No open product decisions remain for V1. The approved behavior is: description, image, or both; generate one active canvas design; regenerate replaces the active design.
