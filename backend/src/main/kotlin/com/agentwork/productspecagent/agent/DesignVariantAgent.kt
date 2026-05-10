package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.DesignImageInput
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
data class DesignGenerationInput(
    val projectId: String,
    val description: String?,
    val image: DesignImageInput?,
)

@Serializable
data class DesignGenerationResult(
    val analysis: DesignAnalysis,
    val title: String,
    val html: String,
    val rationale: String,
)

@Deprecated("Temporary compatibility for legacy design workbench tests until Simple Design Generator V1 controller migration removes callers.")
@Serializable
data class GeneratedDesignVariant(
    val title: String,
    val html: String,
    val rationale: String,
)

@Component
open class DesignVariantAgent(private val koogRunner: KoogAgentRunner? = null) {
    companion object {
        const val AGENT_ID = "design-variant"
    }

    open fun generate(input: DesignGenerationInput): DesignGenerationResult {
        val subject = input.description?.take(120)
            ?: input.image?.originalName?.let { "Reference image $it" }
            ?: "Generated design"
        val escapedSubject = escapeHtml(subject)
        val imageLine = input.image?.originalName
            ?.let { "<p>Reference image: ${escapeHtml(it)}</p>" }
            .orEmpty()
        val source = when {
            input.description != null && input.image != null -> "description and image"
            input.image != null -> "image"
            else -> "description"
        }

        return DesignGenerationResult(
            analysis = DesignAnalysis(
                summary = "Generated layout from $source.",
                visualDirection = "Clean product interface with clear hierarchy.",
                rationale = "Fallback generation keeps local development deterministic.",
            ),
            title = "Generated Design",
            html = """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                      body{margin:0;font-family:system-ui;background:#f7f7f4;color:#171717}
                      main{min-height:100vh;display:grid;place-items:center;padding:48px}
                      section{max-width:920px;width:100%;background:white;border:1px solid #ddd;border-radius:8px;padding:40px}
                      h1{font-size:40px;line-height:1.05;margin:0 0 16px}
                      p{font-size:16px;line-height:1.6;color:#4b5563}
                    </style>
                  </head>
                  <body>
                    <main><section><h1>$escapedSubject</h1><p>Generated HTML layout preview.</p>$imageLine</section></main>
                  </body>
                </html>
            """.trimIndent(),
            rationale = "Fallback generated from the current V1 input.",
        )
    }

    @Deprecated("Temporary compatibility for legacy design workbench tests until Simple Design Generator V1 controller migration removes callers.")
    open fun generate(projectId: String, screenId: String, prompt: String?): GeneratedDesignVariant {
        val result = generate(
            DesignGenerationInput(
                projectId = projectId,
                description = prompt ?: screenId,
                image = null,
            ),
        )
        return GeneratedDesignVariant(
            title = result.title,
            html = result.html,
            rationale = result.rationale,
        )
    }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
