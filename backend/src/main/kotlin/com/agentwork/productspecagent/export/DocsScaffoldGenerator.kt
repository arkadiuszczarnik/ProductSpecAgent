package com.agentwork.productspecagent.export

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import org.springframework.stereotype.Service
import java.io.StringWriter

data class ScaffoldContext(
    val projectName: String,
    val features: List<FeatureContext>,
    val decisions: List<DecisionContext>,
    val scopeContent: String?,
    val mvpContent: String?,
    val techStack: String,
    val problemContent: String?,
    val targetAudienceContent: String?,
    val architectureContent: String?,
    val backendContent: String?,
    val frontendContent: String?
)

data class FeatureContext(
    val number: Int,
    val title: String,
    val slug: String,
    val filename: String,
    val description: String,
    val estimate: String,
    val dependencies: String,
    val stories: List<StoryContext>,
    val acceptanceCriteria: List<TaskContext>,
    val tasks: List<TaskContext>,
    /**
     * Feature 22: Human-readable scope label for this feature — one of "Frontend",
     * "Backend", "Frontend + Backend", "Core", or null if the wizard didn't produce a
     * matching entry. Null suppresses the `**Scope:**` line in the feature template.
     */
    val scope: String? = null,
    /**
     * Feature 22: Key-value map carrying scope-specific content (e.g. `apiEndpoints`,
     * `uiComponents`, `screens`, `dataModel`). Empty / missing entries suppress their
     * corresponding section in the feature template.
     */
    val scopeFields: Map<String, String> = emptyMap(),
    /**
     * Feature 22: Explicit truthy flags per scope-field. Kept separately from
     * [scopeFields] because Mustache.java's section evaluation of a Map entry whose
     * value is an empty string still renders as truthy in some reflection paths; using
     * a boolean removes the ambiguity.
     */
    val hasUiComponents: Boolean = false,
    val hasScreens: Boolean = false,
    val hasUserInteractions: Boolean = false,
    val hasApiEndpoints: Boolean = false,
    val hasDataModel: Boolean = false,
    val hasSideEffects: Boolean = false,
    val hasPublicApi: Boolean = false,
    val hasTypesExposed: Boolean = false,
    val hasExamples: Boolean = false,
)

data class StoryContext(val index: Int, val title: String, val description: String)
data class TaskContext(val title: String, val description: String)
data class DecisionContext(val title: String, val chosen: String, val rationale: String)

@Service
class DocsScaffoldGenerator {

    private val mf: MustacheFactory = DefaultMustacheFactory("templates/scaffold")

    fun generate(context: ScaffoldContext): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // 00-feature-set-overview.md
        result["docs/features/00-feature-set-overview.md"] = render(
            "docs/features/00-feature-set-overview.md.mustache", context
        )

        // Per-feature docs
        for (feature in context.features) {
            result["docs/features/${feature.filename}"] = render(
                "docs/features/feature.md.mustache", feature
            )
        }

        // Architecture overview
        result["docs/architecture/overview.md"] = render(
            "docs/architecture/overview.md.mustache", context
        )

        // Backend API
        result["docs/backend/api.md"] = render(
            "docs/backend/api.md.mustache", context
        )

        // Frontend design
        result["docs/frontend/design.md"] = render(
            "docs/frontend/design.md.mustache", context
        )

        return result
    }

    private fun render(templatePath: String, scope: Any): String {
        val mustache = mf.compile(templatePath)
        val writer = StringWriter()
        mustache.execute(writer, scope).flush()
        return writer.toString()
    }
}
