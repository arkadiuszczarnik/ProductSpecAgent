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
    val tasks: List<TaskContext>
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
