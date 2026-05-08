# RFC: Deepen Export and Handoff Package Building

## Problem

Project export and agent handoff are one packaging workflow with two delivery modes, but the current implementation spreads the package contract across several shallow modules:

- `ExportController` loads the project only to derive the download filename and calls `ExportService.exportProject`.
- `ExportService` builds the normal project ZIP, renders export templates, writes `CLAUDE.md` and `AGENTS.md` for normal project exports, includes docs/spec/tasks/decisions/clarifications, matches asset bundles, and writes Claude/Codex tool symlinks.
- `HandoffService` generates preview markdown, calls `ExportService.exportProject`, unzips the result, filters some symlinks, overlays handoff files, embeds Living Sync and Product Spec Sync asset bundles, and patches symlinks again.
- `AssetBundleExporter` owns bundle matching, asset-bundle ZIP layout, README rendering, path safety checks, and tool-link symlink paths.
- `ZipSymlinkSupport` is called from multiple places, so symlink entry tracking leaks into service-level code.

This creates integration risk around ZIP entry names, root prefixing versus flat handoff layout, duplicate symlinks, template selection, static asset resources, and generated filenames. A reader must understand both normal export assembly and handoff post-processing to reason about one handoff ZIP.

## Proposed Interface

Introduce a deep package boundary optimized for current callers:

```kotlin
interface ExportPackageBuilder {
    fun exportProject(
        projectId: String,
        options: ProjectExportOptions = ProjectExportOptions(),
    ): ZipPackage

    fun previewHandoff(
        projectId: String,
        syncUrl: String,
        format: HandoffFormat = HandoffFormat.ClaudeCode,
    ): HandoffPreview

    fun exportHandoff(
        projectId: String,
        syncUrl: String,
        options: HandoffPackageOptions = HandoffPackageOptions(),
    ): ZipPackage
}

data class ZipPackage(
    val filename: String,
    val bytes: ByteArray,
    val mediaType: String = "application/zip",
)

data class ProjectExportOptions(
    val includeDecisions: Boolean = true,
    val includeClarifications: Boolean = true,
    val includeTasks: Boolean = true,
)

data class HandoffPackageOptions(
    val format: HandoffFormat = HandoffFormat.ClaudeCode,
    val overrides: HandoffOverrides = HandoffOverrides(),
)

data class HandoffOverrides(
    val claudeMd: String? = null,
    val agentsMd: String? = null,
    val implementationOrder: String? = null,
)

@JvmInline
value class HandoffFormat private constructor(val value: String) {
    companion object {
        val ClaudeCode = HandoffFormat("claude-code")
        val Codex = HandoffFormat("codex")
        fun custom(value: String): HandoffFormat = HandoffFormat(value)
        fun fromWire(value: String?): HandoffFormat =
            when (value) {
                null, "", "claude-code" -> ClaudeCode
                "codex" -> Codex
                else -> custom(value)
            }
    }
}
```

Controller usage should become:

```kotlin
val zip = packageBuilder.exportProject(
    projectId = projectId,
    options = (request ?: ExportRequest()).toProjectExportOptions(),
)

return zip.toResponseEntity()
```

```kotlin
val zip = packageBuilder.exportHandoff(
    projectId = projectId,
    syncUrl = syncUrl,
    options = (request ?: HandoffExportRequest()).toHandoffPackageOptions(),
)

return zip.toResponseEntity()
```

The public API intentionally does not expose:

- `flat`
- `includeAgentTemplateFiles`
- `ZipInputStream`
- `ZipOutputStream`
- symlink patching
- slug generation
- asset-bundle root paths

Those are packaging implementation details.

## Internal Design

Use a caller-friendly facade with smaller internal collaborators:

```kotlin
class DefaultExportPackageBuilder(
    private val projectAssembler: ProjectPackageAssembler,
    private val handoffContentFactory: HandoffContentFactory,
    private val handoffOverlayWriter: HandoffOverlayWriter,
) : ExportPackageBuilder
```

### `ProjectPackageAssembler`

Owns the shared base package:

- `README.md`
- `docs/SPEC.md`
- raw spec files
- `.gitignore`
- docs scaffold files and uploads
- decisions
- clarifications
- tasks and `docs/PLAN.md`
- matched asset bundles
- optional normal-export `CLAUDE.md` and `AGENTS.md`
- optional Claude/Codex tool symlinks

It should write to an internal archive writer, not return a ZIP that other services unzip.

### `HandoffContentFactory`

Owns handoff-specific markdown and JSON content:

- preview `CLAUDE.md`
- preview `AGENTS.md`
- `implementation-order.md`
- `syncUrl`
- Living Sync base URL
- MCP URL
- `.claude/settings.json`
- `.claude/living-sync.json`

Preview and export must use the same content factory so the frontend preview cannot drift from the exported ZIP.

### `HandoffOverlayWriter`

Owns the handoff overlay:

- writes `CLAUDE.md`, `AGENTS.md`, and `implementation-order.md`
- applies `HandoffOverrides`
- embeds Living Sync reporter bundle from classpath resources
- embeds Product Spec Sync bundle from classpath resources
- writes `.claude/*` and `.agents/*` symlinks to the neutral `.asset-bundles`

### `ZipArchiveWriter`

Owns ZIP mechanics:

- text and binary entries
- symlink entries
- duplicate entry policy
- final `ZipSymlinkSupport.patchSymlinks`
- root handling through an internal `ArchiveRoot`

```kotlin
sealed interface ArchiveRoot {
    data class Slugged(val slug: String) : ArchiveRoot
    data object Flat : ArchiveRoot
}
```

Normal project export uses `ArchiveRoot.Slugged(projectSlug)`.
Handoff export uses `ArchiveRoot.Flat`.

The handoff path must not call `exportProject()` and then rewrite ZIP bytes. It should assemble the same base project content through `ProjectPackageAssembler` with handoff-specific internal options:

```kotlin
projectAssembler.writeBasePackage(
    writer = writer,
    projectId = projectId,
    root = ArchiveRoot.Flat,
    options = ProjectExportOptions(),
    agentTemplateFiles = AgentTemplateFiles.Skip,
    toolLinks = ToolLinks.Skip,
)

handoffOverlayWriter.write(...)
```

## Dependency Strategy

Dependency category: **local-substitutable**.

The module can be tested using the existing in-memory storage stack:

- `ProjectStorage(InMemoryObjectStore())`
- `DecisionStorage(InMemoryObjectStore())`
- `ClarificationStorage(InMemoryObjectStore())`
- `TaskStorage(InMemoryObjectStore())`
- `AssetBundleStorage(InMemoryObjectStore())`

The packaging module depends on local resources and services:

- `ProjectService`
- `WizardService`
- `TaskService`
- `DecisionService`
- `ClarificationService`
- `AssetBundleStorage` or `AssetBundleExporter`
- Mustache templates under `templates/export` and `templates/handoff`
- static asset-bundle classpath resources

No true external dependency is required. URLs embedded into handoff content are input values, not remote calls.

## Testing Strategy

New boundary tests should cover:

- Normal project export returns a `ZipPackage` with slug filename, prefixed ZIP root, `README.md`, `docs/SPEC.md`, raw spec files, `CLAUDE.md`, `AGENTS.md`, no `implementation-order.md`, and neutral `.asset-bundles` plus `.claude`/`.agents` symlinks.
- Handoff preview uses the same handoff markdown content as handoff export `CLAUDE.md`.
- Handoff export returns a flat ZIP with root `CLAUDE.md`, `AGENTS.md`, `implementation-order.md`, base project files, `.claude/settings.json`, `.claude/living-sync.json`, Living Sync reporter bundle, Product Spec Sync bundle, and tool symlinks.
- Handoff overrides replace only the requested markdown entries.
- Handoff export does not duplicate tool symlink entries from the normal export path.
- Matched project asset bundles and global asset bundles are included under the neutral `.asset-bundles` layout.
- `syncUrl` from GET handoff download is embedded into `CLAUDE.md` and the derived Living Sync/MCP config.

Old tests to reduce or migrate:

- `ExportControllerTest` assertions that inspect package internals should move to `ExportPackageBuilderTest`.
- `HandoffControllerTest` ZIP-content assertions should move to `ExportPackageBuilderTest`, leaving controller tests for HTTP status, headers, and sync URL source.
- `AssetBundleExporterZipTest` symlink-layout tests can be reduced once `ExportPackageBuilderTest` covers final package output.
- `ExportServiceDesignBundleTest` should remain only if it checks docs/scaffold behavior outside package assembly; otherwise migrate to package boundary tests.

Keep thin controller tests for:

- normal export returns `application/zip` and filename from `ZipPackage`
- handoff preview maps query `format` to `HandoffFormat`
- POST handoff export uses generated sync URL
- GET handoff ZIP uses the request URL as sync URL

## Implementation Recommendations

Implement incrementally:

1. Add the `ExportPackageBuilder` API and a default implementation that delegates to the existing `ExportService` and `HandoffService`. This lets controllers migrate first without changing ZIP bytes.
2. Move filename slugging into the builder and simplify controllers.
3. Introduce `ZipArchiveWriter` and route normal export through it while preserving `ExportService` output.
4. Extract shared project package assembly into `ProjectPackageAssembler`.
5. Extract handoff markdown/config generation into `HandoffContentFactory`.
6. Replace handoff ZIP reprocessing with direct base package assembly plus `HandoffOverlayWriter`.
7. Move ZIP-content assertions from controller tests to builder boundary tests.
8. Remove obsolete public methods or turn `ExportService` and `HandoffService` into private implementation collaborators.

Avoid the fully generic contributor engine for now. It is a good future shape if the product needs multiple package formats or plugin-defined sections, but the current system has two concrete package products. A purpose-built facade keeps the interface small and prevents internal packaging switches from leaking into callers.
